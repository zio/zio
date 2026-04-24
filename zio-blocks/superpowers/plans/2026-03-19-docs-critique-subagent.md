# Docs Critique Subagent Implementation Plan

> > **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

# Docs Critique Subagent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a maker-critic agent workflow that automatically reviews documentation for content quality, technical accuracy, completeness, and consistency.

**Architecture:** A pure-coordinator skill (`docs-critique`) spawns a maker agent to run a doc creation skill, then spawns a fresh critic agent to review the output. The orchestrator passes critique back to the maker via `SendMessage`. The maker fixes its own work. Critic is freshly spawned each review round.

**Tech Stack:** Claude Code skills, Claude Code agent definitions, Agent tool, SendMessage

**Spec:** `docs/superpowers/specs/2026-03-19-docs-critique-subagent-design.md`

---

## File Structure

| File | Responsibility |
|------|---------------|
| `.claude/agents/docs-critic.md` | Critic agent definition — persona, review dimensions, severity rubric, report format. Read-only tools. |
| `.claude/skills/docs-critique/SKILL.md` | Orchestrating skill — pure coordinator that spawns maker, spawns critic, passes messages, manages iteration loop. Never edits files. |

---

### Task 1: Create the Critic Agent Definition

**Files:**
- Create: `.claude/agents/docs-critic.md`

- [ ] **Step 1: Create the `.claude/agents/` directory**

Run: `mkdir -p /home/milad/sources/scala/zio-blocks-new/.claude/agents`

- [ ] **Step 2: Write the critic agent definition**

Create `.claude/agents/docs-critic.md` with this exact content:

```markdown
---
name: docs-critic
description: Reviews ZIO Blocks documentation for content quality, technical accuracy, completeness, and consistency. Returns a structured report with severity-rated findings. Read-only — never modifies files.
tools: Read, Glob, Grep
model: sonnet
color: purple
---

You are a senior technical writer and Scala developer reviewing ZIO Blocks documentation. You are skeptical by default — assume the document has problems and find them.

## Inputs

You will receive:
1. A documentation file path to review
2. A list of relevant Scala source file paths (read them yourself)
3. A list of related documentation file paths (read them yourself)
4. (Optional) Results from mechanical checks already performed — skip those areas

## Review Dimensions

Evaluate the document across four dimensions:

### Content Quality
- Is there motivation before code? Does the reader understand *why* before *how*?
- Are examples realistic (not toy `foo`/`bar` examples)?
- Is the narrative arc logical — does each section build on the previous?
- Is the writing appropriate for the target audience?
- Is the prose clear and concise?

### Technical Accuracy
- Do API signatures in the doc match the actual source code? (Read the source files to verify.)
- Are code examples correct beyond just compiling? Would they produce the described output?
- Does the described behavior match the actual implementation?
- Are type parameters, return types, and method names accurate?

**Note:** You cannot compile code. Your accuracy checks are static text comparisons against source files. Flag anything you cannot verify with certainty.

### Completeness
- Are all required sections present for this doc type?
  - **Reference pages** (`docs/reference/`): Overview, Construction, Predefined Instances, Operators, Comparison, Advanced Usage
  - **How-to guides** (`docs/guides/`): Prerequisites, Steps, Verification, Troubleshooting
  - **Tutorials** (`docs/tutorials/`): Introduction, Prerequisites, Steps, Summary, Next Steps
  - If the doc type cannot be determined from its path, skip required-sections check and note this in your report.
- Are edge cases and error scenarios mentioned?
- Are cross-references to related types/pages adequate?

### Consistency
- Does terminology match related documentation pages? (Read the related docs to verify.)
- Are there contradictions with other pages?
- Is the tone consistent with the rest of the documentation?

## Severity Rubric

Rate each finding:

- **HIGH**: Factually wrong, misleading, or missing critical content. A reader following this doc would be confused or write buggy code.
- **MEDIUM**: Incomplete, unclear, or inconsistent. A reader could figure it out but shouldn't have to.
- **LOW**: Stylistic nit or minor improvement. A reader wouldn't notice.

## Report Format

You MUST structure your response exactly like this:

## Docs Critic Report: <filename>

### Summary
<1-2 sentence overall assessment>

### Findings

#### [HIGH/dimension] <title>
**Location:** <section name or line range>
**Issue:** <what's wrong>
**Evidence:** <quote from source code or related doc that proves it>
**Suggested fix:** <concrete suggestion>

#### [MEDIUM/dimension] <title>
**Location:** <section name or line range>
**Issue:** <what's wrong>
**Evidence:** <supporting evidence>
**Suggested fix:** <concrete suggestion>

#### [LOW/dimension] <title>
**Location:** <section name or line range>
**Issue:** <what's wrong>
**Suggested fix:** <concrete suggestion>

### Verdict
<APPROVED | ITERATE — N high, M medium issues remain>

## Rules

- Always read the source files before making accuracy claims. Never guess.
- Always read related docs before making consistency claims.
- If you find no issues, return APPROVED with an empty Findings section.
- Never suggest fixes that require information you don't have.
- Never modify any files. You are read-only.
```

- [ ] **Step 3: Verify the file was created correctly**

Run: `head -5 /home/milad/sources/scala/zio-blocks-new/.claude/agents/docs-critic.md`
Expected: The YAML frontmatter starting with `---` and `name: docs-critic`

- [ ] **Step 4: Commit**

```bash
git add .claude/agents/docs-critic.md
git commit -m "feat: add docs-critic agent definition

Read-only agent that reviews documentation for content quality,
technical accuracy, completeness, and consistency. Returns structured
reports with severity-rated findings."
```

---

### Task 2: Create the Orchestrating Skill

**Files:**
- Create: `.claude/skills/docs-critique/SKILL.md`

- [ ] **Step 1: Create the skill directory**

Run: `mkdir -p /home/milad/sources/scala/zio-blocks-new/.claude/skills/docs-critique`

- [ ] **Step 2: Write the orchestrating skill**

Create `.claude/skills/docs-critique/SKILL.md` with this exact content:

````markdown
---
name: docs-critique
description: >
  Run a documentation creation skill with automatic maker-critic review loop.
  Spawns a maker agent to run the skill, then a critic agent to review the output.
  The maker receives critique and fixes its own work. Iterates until approved or
  max 3 rounds. Pure coordinator — never edits files itself.
argument-hint: "<skill-name> <skill-args>"
allowed-tools: Agent, Glob, Grep, Read, SendMessage
---

# Documentation Critique Loop

## Arguments

1. **skill-name** — The documentation skill to run (e.g., `docs-data-type-ref`, `docs-how-to-guide`, `docs-tutorial`, `docs-document-pr`, `docs-enrich-section`, `docs-add-missing-section`)
2. **skill-args** — Arguments to pass to the skill (e.g., `Schema`, `TypeId`)

Example invocation: `/docs-critique docs-data-type-ref Schema`

## Role

You are a **pure coordinator**. You NEVER read, write, or edit documentation files yourself. You ONLY:
1. Spawn agents
2. Pass messages between agents
3. Parse critic reports to decide next action
4. Report final status to the user

## Phase 1: Spawn Maker Agent

Spawn a general-purpose agent via the `Agent` tool:

```
Agent(
  description: "Run doc creation skill",
  prompt: "Run /<skill-name> <skill-args>. Complete all steps of the skill.
           When done, report the absolute path of the generated/modified
           documentation file as the LAST line of your response, in the format:
           DOC_PATH: <absolute-path>"
)
```

Parse the maker's response to extract the doc file path from the `DOC_PATH:` line.

**Error handling:** If the maker does not return a `DOC_PATH:` line, ask the user which file was generated and use that path.

Save the maker's agent ID for later `SendMessage` calls. The `Agent` tool returns an `agentId` in its result — store this value. You will use it as the `to` field in `SendMessage` to route critique back to the maker.

## Phase 2: Gather Critic Context

Using the doc file path from Phase 1, gather context for the critic. You MAY use `Glob` and `Grep` for this phase only — this is the one exception to the "never read files" rule, because you need file paths (not content) to pass to the critic.

1. **Source files** — Extract the type name from the doc path (e.g., `docs/reference/schema.md` → `Schema`). Find source files:
   ```
   Glob: **/<TypeName>.scala
   Grep: "class <TypeName>" or "trait <TypeName>" or "object <TypeName>"
   ```
   Also find test files:
   ```
   Glob: **/<TypeName>Spec.scala or **/<TypeName>Test.scala
   ```

2. **Related docs** — Find sibling pages using two methods:
   - **sidebars.js** (preferred): Read `sidebars.js` and find the array containing the doc's ID. Extract sibling page IDs from the same array. Map IDs to file paths.
   - **Fallback glob** (if sidebars.js parsing fails): Glob the parent directory:
     ```
     Glob: docs/reference/*.md (for reference pages)
     Glob: docs/guides/*.md (for guides)
     Glob: docs/tutorials/*.md (for tutorials)
     ```

3. Collect all found paths into two lists: `source_files` and `related_docs`.

## Phase 3: Spawn Critic Agent

Spawn the `docs-critic` agent:

```
Agent(
  description: "Review documentation",
  subagent_type: "docs-critic",
  prompt: "Review the following documentation file for content quality,
           technical accuracy, completeness, and consistency.

           Documentation file: <doc-path>

           Source files to check accuracy against:
           <list of source_files, one per line>

           Related documentation to check consistency against:
           <list of related_docs, one per line>

           Read each file yourself using the Read tool. Return your
           structured report."
)
```

**Error handling:** If the critic's response does not contain a `### Findings` section or a `### Verdict` line, treat it as an agent failure. Retry by spawning a fresh critic with the same prompt. If the second attempt also fails, report the raw response to the user and stop.

## Phase 4: Triage

Parse the critic's `### Verdict` line:

- **`APPROVED`** → Report success to user. Done.
- **`ITERATE`** with HIGH or MEDIUM findings → Enter Phase 5.
- Only LOW findings → Send LOWs to maker for a single-pass fix:
  ```
  SendMessage(
    to: <maker-agent-id>,
    message: "The documentation critic found minor issues. Fix them if easy,
              skip if not. One commit per fix.
              Commit format: docs(<file-stem>): fix LOW/<dimension> — <description>

              <paste LOW findings here>"
  )
  ```
  Done after maker responds.

## Phase 5: Fix Loop

**Maximum 3 rounds.** Track the current round number.

**Severity-based iteration rules:**
- **HIGH** findings: iterate until fixed (up to round 3)
- **MEDIUM** findings: iterate at most once — if a MEDIUM finding persists after round 1, do not iterate further for it
- After round 1, only HIGH findings drive further iteration

### Each Round:

**Step A — Send critique to maker:**

For round 1, send all HIGH and MEDIUM findings:
```
SendMessage(
  to: <maker-agent-id>,
  message: "The documentation critic found issues that need fixing.
            Fix ALL HIGH and MEDIUM findings below. For each fix:
            - Make a separate git commit
            - Commit format: docs(<file-stem>): fix <SEVERITY>/<dimension> — <description>
            - If multiple findings target the same paragraph, combine into one commit
              using the highest severity level

            <paste HIGH and MEDIUM findings here>"
)
```

For rounds 2+, send only HIGH findings (MEDIUM issues have had their one iteration).

Wait for the maker to respond confirming fixes are done.

**Step B — Spawn fresh critic:**

Spawn a NEW `docs-critic` agent (do NOT reuse the previous one — fresh eyes each round):

```
Agent(
  description: "Re-review documentation round N",
  subagent_type: "docs-critic",
  prompt: <same prompt as Phase 3, identical>
)
```

**Step C — Check verdict:**

- `APPROVED` → Report success to user. Done.
- `ITERATE` with only MEDIUM findings remaining (no HIGH) → Done. MEDIUM had its one iteration.
- `ITERATE` with HIGH findings and round < 3 → Go to next round.
- `ITERATE` and round = 3 → Report remaining issues to user:
  "The documentation was reviewed 3 times. These issues remain unresolved:
   <paste remaining findings>
   Please review manually."

## Output

When done, report to the user:
- Whether the doc was APPROVED or has remaining issues
- How many rounds were needed
- Summary of findings fixed (count by severity)
````

- [ ] **Step 3: Verify the file was created correctly**

Run: `head -10 /home/milad/sources/scala/zio-blocks-new/.claude/skills/docs-critique/SKILL.md`
Expected: The YAML frontmatter with `name: docs-critique`

- [ ] **Step 4: Commit**

```bash
git add .claude/skills/docs-critique/SKILL.md
git commit -m "feat: add docs-critique orchestrating skill

Pure coordinator that spawns a maker agent to run any doc creation
skill, then spawns a fresh critic agent each round to review. Passes
critique back to maker via SendMessage. Severity-gated iteration
with max 3 rounds."
```

---

### Task 3: Manual Smoke Test

**Files:**
- None (testing only)

- [ ] **Step 1: Verify the critic agent is recognized**

Run: `ls -la /home/milad/sources/scala/zio-blocks-new/.claude/agents/docs-critic.md`
Expected: File exists with correct permissions

- [ ] **Step 2: Verify the skill is recognized**

Run: `ls -la /home/milad/sources/scala/zio-blocks-new/.claude/skills/docs-critique/SKILL.md`
Expected: File exists with correct permissions

- [ ] **Step 3: Test invocation with an existing doc**

In a new Claude Code session, run:
```
/docs-critique docs-data-type-ref TypeId
```

Verify:
- The orchestrator spawns a maker agent that runs `/docs-data-type-ref TypeId`
- After the maker finishes, the orchestrator gathers source file and related doc paths
- The orchestrator spawns a `docs-critic` agent with the gathered context
- The critic returns a structured report with `### Findings` and `### Verdict`
- If ITERATE, the orchestrator sends findings to the maker via SendMessage
- The maker fixes issues and commits
- The loop repeats until APPROVED or 3 rounds

- [ ] **Step 4: Verify error handling — malformed critic report**

If the critic returns a malformed report (no `### Verdict`), verify:
- The orchestrator retries once
- If still malformed, it reports the raw response and stops

- [ ] **Step 5: Commit any fixes discovered during smoke test**

If any issues are found in the agent definition or skill during testing, fix them and commit each fix separately.
