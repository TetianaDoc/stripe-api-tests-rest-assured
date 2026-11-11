Run an agentic review→fix loop on `$ARGUMENTS` (or the most recently edited Java test file under `src/test/java` if no arguments).

You are the orchestrator running in the main conversation. You are the **only** place that can spawn subagents in parallel — domain reviewers cannot spawn each other. Follow this loop exactly.

## Argument parsing

Parse `$ARGUMENTS` before doing anything else:

- If `--iterations N` is present, extract `N` as `MAX_ITERATIONS` and remove `--iterations N` from the string. Otherwise `MAX_ITERATIONS = 1`.
- If `--continue` is present, set `CONTINUE_MODE = true` and remove `--continue` from the string. Otherwise `CONTINUE_MODE = false`.
- The remaining string after removing all flags is `target`:
  - If empty, default to the most recently modified `.java` file under `src/test/java/` (resolve via `git status --porcelain src/test/java` or `find src/test/java -name "*.java" -exec stat -f "%m %N" {} + | sort -rn | head -1 | cut -d' ' -f2-`).
  - `target` may be a single Java file (e.g. `src/test/java/com/automation/stripe/customers/CustomerEdgeCasesTest.java`), a package directory (e.g. `src/test/java/com/automation/stripe/customers`), or `src/test/java/com/automation/stripe/base/StripeSpecFactory.java`.

`MAX_ITERATIONS` controls how many times `@code-implementator` may run. A reviewer pass always runs at least once (at the start) and once more at the end (regression check). It does not count toward `MAX_ITERATIONS`.

Examples:

- `/review src/test/java/com/automation/stripe/customers/CustomerEdgeCasesTest.java` → 1 initial reviewer pass, up to 1 implementator pass, 1 final regression pass
- `/review src/test/java/com/automation/stripe/products/ProductCrudIndependentTest.java --iterations 2` → 1 initial reviewer pass, up to 2 implementator passes, 1 final regression pass
- `/review src/test/java/com/automation/stripe/base/StripeSpecFactory.java` → reviews spec factory across Java, REST Assured, and Security (Coverage auto-skips non-`*Test.java` files)
- `/review src/test/java/com/automation/stripe/customers/CustomerCrudIndependentTest.java --continue` → resume from last `.claude/.review-cache` state

## Setup

**If `CONTINUE_MODE = false`**, run:

```bash
rm -rf .claude/.review-cache && mkdir -p .claude/.review-cache
```

Then set `IMPL_ITER = 0`.

**If `CONTINUE_MODE = true`**, run:

```bash
mkdir -p .claude/.review-cache
ls .claude/.review-cache/iter-*.md 2>/dev/null | sort | tail -1
```

Determine `IMPL_ITER` (number of implementator passes already completed) from the output:

- If no files exist: warn the user ("no previous cache found, starting fresh") and set `IMPL_ITER = 0`.
- If the last file is `iter-N.md`, count unchecked findings:
  ```bash
  grep -c '^### \[ \]' .claude/.review-cache/iter-N.md || echo 0
  ```
  - If count > 0: implementator did not finish on this file — resume it first (Step 2a below), then set `IMPL_ITER = N`.
  - If count == 0: implementator finished — set `IMPL_ITER = N`.
- If `IMPL_ITER >= MAX_ITERATIONS`: report "nothing left to do" and emit the Final Report from existing files.

Compute the absolute path to `.claude/.review-cache` (call it `<CACHE_DIR>`) now and use it for all paths below.

## Reviewer pass sub-procedure

Whenever this loop says **"run a reviewer pass"** with `feedback_path=<CACHE_DIR>/<basename>.md` and a given `iteration`, execute the following procedure exactly:

**RP-1.** Derive four temp paths in `<CACHE_DIR>`:

- `java_path = <CACHE_DIR>/java-<basename>.md`
- `restassured_path = <CACHE_DIR>/restassured-<basename>.md`
- `security_path = <CACHE_DIR>/security-<basename>.md`
- `coverage_path = <CACHE_DIR>/coverage-<basename>.md` *(only if `iteration != final`)*

**RP-2.** Spawn the domain reviewers **in parallel**. Emit **a single assistant message containing all the subagent tool calls below as separate tool-use blocks**. Do not call them sequentially.

Calls to issue in that one message:

- `@review-java` with `target=<target>`, `output_path=<java_path>`, `iteration=<iteration>`
- `@review-restassured` with `target=<target>`, `output_path=<restassured_path>`, `iteration=<iteration>`
- `@review-security` with `target=<target>`, `output_path=<security_path>`, `iteration=<iteration>`
- `@review-coverage` with `target=<target>`, `output_path=<coverage_path>` *(skip on `iteration=final`)*

Each agent returns a one-line result: `DOMAIN=<d> FILE=<path> COUNT=<N>`. Read those.

**RP-3.** If any domain agent failed to return a parseable line, re-invoke that single agent once as a fallback. If it still fails, set its `COUNT=0` and proceed — synthesis will treat the temp file as empty.

**RP-4.** Invoke `@synthesis-reviewer` with:

```
target=<target>
feedback_path=<CACHE_DIR>/<basename>.md
iteration=<iteration>
java_path=<java_path>
restassured_path=<restassured_path>
security_path=<security_path>
coverage_path=<coverage_path>     # omit on iteration=final
```

`synthesis-reviewer` reads only the four (or three) small temp files — not source code — dedupes, prioritizes, renumbers, writes the merged feedback file, cleans up temps, and returns:

```
FEEDBACK_FILE=<feedback_path> TOTAL=<N> CRITICAL=<X> HIGH=<Y> MEDIUM=<Z> LOW=<W>
```

Parse that line. This is the result of the reviewer pass.

## Loop

### Step 1 — Initial review (always runs, unless continuing with unfinished implementator work)

> Skip this step only if `CONTINUE_MODE = true` AND the last cache file had unchecked findings (resume implementator instead — see Setup above).

Run a reviewer pass with `feedback_path=<CACHE_DIR>/iter-0.md` and `iteration=1`.

- If `TOTAL == 0` → skip to Final Report, `outcome = clean` (nothing to fix)

### Step 2 — Fix loop (runs up to `MAX_ITERATIONS` times)

Repeat the following block, incrementing `IMPL_ITER` each time, while `IMPL_ITER < MAX_ITERATIONS`:

**2a.** Invoke `@code-implementator` with:

```
feedback_path=<CACHE_DIR>/iter-<IMPL_ITER>.md
```

(On the first pass use `iter-0.md` from the initial review. On subsequent passes use the file from the previous reviewer pass in Step 2b.)

Parse the returned line: `FEEDBACK_FILE=<path> FIXED=<N> BLOCKED=<M> REMAINING=<K>`

- If `REMAINING > 0` and `FIXED == 0` → stop loop, `outcome = stuck`
- If `REMAINING == 0` → all findings fixed, proceed to Final Review (Step 3)

Increment `IMPL_ITER`.

**2b.** If `IMPL_ITER < MAX_ITERATIONS` and `REMAINING > 0`, run a reviewer pass with `feedback_path=<CACHE_DIR>/iter-<IMPL_ITER>.md` and `iteration=<IMPL_ITER + 1>`.

- If `TOTAL == 0` → stop loop, `outcome = clean`

(Continue loop with the new feedback file.)

If `IMPL_ITER == MAX_ITERATIONS` and `REMAINING > 0` → stop loop, `outcome = residual-blocked`

### Step 3 — Final regression review (always runs if loop completed without `stuck`)

Run a reviewer pass with `feedback_path=<CACHE_DIR>/final.md` and `iteration=final`.
*(`review-coverage` is skipped on this pass — `final` only checks for regressions across `java` / `restassured` / `security`.)*

- If `TOTAL == 0` → `outcome = clean`
- Else → `outcome = residual-blocked` (new findings appeared after fixing)

## Fallback parsing

If `synthesis-reviewer` does not return the expected line, count directly from the merged file:

- `TOTAL` / `REMAINING`: `grep -c '^### \[ \]' <feedback_path>` (or 0 if file missing)
- `FIXED`: previous `TOTAL` minus current `REMAINING`

## Final Report

Include rows for all passes that ran (including ones from a previous session when using `--continue`):

```markdown
# Review Loop Result

**Target:** <target>
**Implementator passes:** <IMPL_ITER> / <MAX_ITERATIONS>
**Mode:** <fresh | continued from iter-N>
**Outcome:** <clean | residual-blocked | stuck>

| Pass         | Role            | Total | Critical | High | Medium | Low | Fixed | Blocked |
|:-------------|:----------------|------:|---------:|-----:|-------:|----:|------:|--------:|
| Initial      | reviewer        | ...   |          |      |        |     | —     | —       |
| 1            | implementator   | —     | —        | —    | —      | —   | ...   | ...     |
| Final        | reviewer        | ...   |          |      |        |     | —     | —       |

**Feedback files:**

- .claude/.review-cache/iter-0.md
- .claude/.review-cache/final.md
```
