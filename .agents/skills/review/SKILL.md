---
name: review
description: >-
  Run a multi-agent review-and-fix loop (/review) on a Java REST Assured test file
  or package. Spawns four parallel domain reviewers (review-java, review-restassured,
  review-security, review-coverage), merges findings via synthesis-reviewer, applies
  fixes and runs Maven verification via code-implementator, and finishes with a
  regression pass.
---

# `/review` — Multi-Agent Review & Fix Loop (REST Assured + JUnit 5)

Run an iterative `review → synthesize → implement → verify → final regression` loop on a target test file, base class, or package in `stripe-api-tests-rest-assured`.

## Arguments

Parse any arguments passed after `/review`:

- `--iterations <N>`: Maximum number of `code-implementator` passes (`MAX_ITERATIONS`, default `1`).
- `--continue`: Resume from `.claude/.review-cache` (`CONTINUE_MODE = true`, default `false`).
- `<target>`: Target file or package directory. If omitted, default to the most recently modified `.java` file under `src/test/java/`.

Examples:
- `/review src/test/java/com/automation/stripe/customers/CustomerEdgeCasesTest.java`
- `/review src/test/java/com/automation/stripe/products/ProductCrudIndependentTest.java --iterations 2`
- `/review src/test/java/com/automation/stripe/base/StripeSpecFactory.java`
- `/review --continue`

---

## Step 0 — Setup Cache & Subagent Definitions

1. Compute `<CACHE_DIR>` as `<workspace_root>/.claude/.review-cache`.
   - If `CONTINUE_MODE = false`: run `rm -rf .claude/.review-cache && mkdir -p .claude/.review-cache` and set `IMPL_ITER = 0`.
   - If `CONTINUE_MODE = true`: run `mkdir -p .claude/.review-cache` and inspect existing `iter-*.md` files to determine `IMPL_ITER` and whether unchecked `### [ ]` findings remain.

2. Define the subagent types (once per conversation) using `define_subagent`, loading their system prompts from `.claude/agents/*.md` (all with `EnableWriteTools: true` so reviewers can write their output markdown files to `<CACHE_DIR>` and `code-implementator` can edit code and run `mvn`):
   - `review-java` (`.claude/agents/review-java.md`)
   - `review-restassured` (`.claude/agents/review-restassured.md`)
   - `review-security` (`.claude/agents/review-security.md`)
   - `review-coverage` (`.claude/agents/review-coverage.md`)
   - `synthesis-reviewer` (`.claude/agents/synthesis-reviewer.md`)
   - `code-implementator` (`.claude/agents/code-implementator.md`)

---

## Reviewer Pass Sub-Procedure

Whenever the loop says **"run a reviewer pass"** with `feedback_path=<CACHE_DIR>/<basename>.md` and `iteration=<1|2|...|final>`:

1. **Derive temp output paths:**
   - `java_path = <CACHE_DIR>/java-<basename>.md`
   - `restassured_path = <CACHE_DIR>/restassured-<basename>.md`
   - `security_path = <CACHE_DIR>/security-<basename>.md`
   - `coverage_path = <CACHE_DIR>/coverage-<basename>.md` *(skip when `iteration=final`)*

2. **Fan out domain reviewers in parallel:**
   In a single turn, call `invoke_subagent` for all 4 (or 3 on `final`) domain reviewers:
   - `TypeName: "review-java"`, `Role: "Java & JUnit 5 Reviewer"`, `Prompt: "target=<target>\noutput_path=<java_path>\niteration=<iteration>"`
   - `TypeName: "review-restassured"`, `Role: "REST Assured Reviewer"`, `Prompt: "target=<target>\noutput_path=<restassured_path>\niteration=<iteration>"`
   - `TypeName: "review-security"`, `Role: "Security & Secret Auditor"`, `Prompt: "target=<target>\noutput_path=<security_path>\niteration=<iteration>"`
   - *(If `iteration != final`)* `TypeName: "review-coverage"`, `Role: "API Scenario Coverage Analyst"`, `Prompt: "target=<target>\noutput_path=<coverage_path>"`

   End your turn after launching them and wait for reactive notifications when all domain reviewers complete. Each returns `DOMAIN=<d> FILE=<path> COUNT=<N>`.

3. **Synthesize findings:**
   Once all parallel domain reviewers have reported back, call `invoke_subagent` for `synthesis-reviewer`:
   - `TypeName: "synthesis-reviewer"`
   - `Role: "Synthesis Reviewer"`
   - `Prompt`:
     ```text
     target=<target>
     feedback_path=<CACHE_DIR>/<basename>.md
     iteration=<iteration>
     java_path=<java_path>
     restassured_path=<restassured_path>
     security_path=<security_path>
     coverage_path=<coverage_path>
     ```
     *(Omit `coverage_path` when `iteration=final`.)*

   Wait for `synthesis-reviewer` to return:
   `FEEDBACK_FILE=<feedback_path> TOTAL=<N> CRITICAL=<X> HIGH=<Y> MEDIUM=<Z> LOW=<W>`

---

## Orchestration Loop

### Step 1 — Initial Review
Run a reviewer pass with `feedback_path=<CACHE_DIR>/iter-0.md` and `iteration=1`.
- If `TOTAL == 0` → skip to Final Report with `Outcome: clean`.

### Step 2 — Fix Loop (up to `MAX_ITERATIONS`)
While `IMPL_ITER < MAX_ITERATIONS`:
1. Invoke `code-implementator` via `invoke_subagent`:
   - `TypeName: "code-implementator"`
   - `Role: "Code Implementator (Pass <IMPL_ITER + 1>)"`
   - `Prompt: "feedback_path=<CACHE_DIR>/iter-<IMPL_ITER>.md"`
2. Wait for `code-implementator` to finish applying fixes and running `mvn test-compile` (and `mvn test` when `STRIPE_SECRET_KEY` is set). Parse:
   `FEEDBACK_FILE=<path> FIXED=<N> BLOCKED=<M> REMAINING=<K>`
3. Increment `IMPL_ITER`.
   - If `REMAINING > 0` and `FIXED == 0` → stop loop (`Outcome: stuck`).
   - If `REMAINING == 0` → proceed to Step 3 (Final Regression Review).
   - If `IMPL_ITER < MAX_ITERATIONS` and `REMAINING > 0` → run a reviewer pass with `feedback_path=<CACHE_DIR>/iter-<IMPL_ITER>.md` and `iteration=<IMPL_ITER + 1>`. If `TOTAL == 0`, stop (`Outcome: clean`).
   - If `IMPL_ITER == MAX_ITERATIONS` and `REMAINING > 0` → stop loop (`Outcome: residual-blocked`).

### Step 3 — Final Regression Review
If the loop did not stop with `stuck`, run a final reviewer pass with `feedback_path=<CACHE_DIR>/final.md` and `iteration=final` (`review-coverage` skipped).
- If `TOTAL == 0` → `Outcome: clean`
- Else → `Outcome: residual-blocked`

### Step 4 — Final Report
Output the summary table showing each pass (`Initial`, `1..N`, `Final`), counts by severity (`Total`, `Critical`, `High`, `Medium`, `Low`, `Fixed`, `Blocked`), and links to the generated feedback files in `.claude/.review-cache/`.
