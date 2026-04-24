# Design: Subagent-Based Documentation Review

> **Date:** 2026-03-19
**Status:** Draft
**Scope:** Add a maker-critic workflow to all documentation creation skills

# Design: Subagent-Based Documentation Review

**Date:** 2026-03-19
**Status:** Draft
**Scope:** Add a maker-critic workflow to all documentation creation skills

## Problem

The existing documentation pipeline has layered mechanical checks (writing style, mdoc conventions, compilation gates) but lacks a content-level reviewer. No system verifies that documentation is clear, technically accurate against source code, complete in coverage, or consistent with related pages. These gaps are caught only by human review — if at all.

## Decisions

| Decision | Choice |
|----------|--------|
| Critique level | Full spectrum — content quality, technical accuracy, completeness, consistency |
| Trigger scope | All 6 creation skills automatically |
| Iteration model | Severity-gated — iterate for HIGH/MEDIUM, single pass for LOW |
| Architecture | Maker-critic agent pair — maker produces and fixes, critic reviews, orchestrator coordinates |
| Output format | Structured report + commit-per-fix |
| Critic context | Doc file + source code + related docs |
| Who fixes | The maker agent — it receives critique and fixes its own work |
| Agent lifecycles | Maker stays alive across rounds; critic freshly spawned each round |

## Architecture: Maker Agent + Critic Agent + Orchestrator

Three components:

1. **`.claude/agents/docs-critic.md`** — Reusable agent definition with persona, review dimensions, severity rubric, and report format. Read-only tools (`Read`, `Glob`, `Grep`).
2. **`.claude/skills/docs-critique/SKILL.md`** — Pure coordinator that spawns the maker, spawns the critic, passes messages between them, and manages the iteration loop. Never edits files itself.
3. **Maker agent** — A general-purpose agent spawned by the orchestrator to run the doc creation skill. Stays alive via `SendMessage` to receive critique and fix its own work.

## Agent Definition: `docs-critic`

**Persona:** Senior technical writer and Scala developer. Skeptical by default — assumes the doc has problems and looks for them.

**Tools:** `Read`, `Glob`, `Grep` only. No write access.

**Review Dimensions:**

| Dimension | What it checks |
|-----------|---------------|
| Content Quality | Clarity, narrative flow, example realism, audience fit, motivation before code |
| Technical Accuracy | API signatures match source (static text comparison — critic cannot compile), examples correct beyond compilation, described behavior matches implementation |
| Completeness | Required sections present (detect doc type from path/frontmatter, compare against appropriate section list: `docs-data-type-ref` for reference pages, `docs-how-to-guide` for guides, `docs-tutorial` for tutorials; if doc type cannot be determined, skip required-sections check and note in report), edge cases mentioned, error scenarios covered, cross-references adequate |
| Consistency | Matches terminology/tone of related docs, no contradictions with other pages |

**Severity Rubric:**

| Severity | Definition | Iteration? |
|----------|-----------|------------|
| HIGH | Factually wrong, misleading, or missing critical content. Reader would be confused or write buggy code. | Yes — must iterate until fixed |
| MEDIUM | Incomplete, unclear, or inconsistent. Reader could figure it out but shouldn't have to. | Yes — iterate once |
| LOW | Stylistic nit, minor improvement. Reader wouldn't notice. | No — single pass, fix if easy |

**Report Format:**

```
## Docs Critic Report: <filename>

### Summary
<1-2 sentence overall assessment>

### Findings

#### [HIGH/accuracy] <title>
**Location:** <section name or line range>
**Issue:** <what's wrong>
**Evidence:** <quote from source code or related doc that proves it>
**Suggested fix:** <concrete suggestion>

#### [MEDIUM/completeness] <title>
...

### Verdict
<APPROVED | ITERATE — N high, M medium issues remain>
```

## Orchestrating Skill: `docs-critique`

**Invocation:** `/docs-critique <skill-name> <skill-args>`

Example: `/docs-critique docs-data-type-ref Schema`

The orchestrator is a pure coordinator. It never reads, writes, or edits documentation files. It only spawns agents and passes messages between them.

### Phase 1: Spawn Maker Agent

Spawn a general-purpose agent via the `Agent` tool:

```
"Run /docs-data-type-ref for Schema. Complete all steps of the skill.
 Report the path of the generated documentation file when done."
```

The maker agent runs the full creation skill (research, write, verify, format, integrate) and returns the doc file path.

### Phase 2: Gather Critic Context

Using the doc path returned by the maker, the orchestrator prepares context for the critic:

1. Extract type names from the doc path/filename
2. Find corresponding Scala source files and tests (via `Glob`/`Grep`)
3. Find related doc pages (scan `sidebars.js` for siblings, scan doc for cross-reference links)
4. Collect these as a list of file paths

### Phase 3: Spawn Critic Agent

Spawn `docs-critic` agent with a prompt containing:

- The doc file path (agent reads it via `Read` tool for live content)
- List of relevant source file paths (agent reads them itself)
- List of related doc file paths (agent reads them itself)

Passing paths rather than inline content keeps the prompt small and ensures the agent sees live file state.

**Error handling:** If the agent returns a response without a `### Findings` section or without a `### Verdict` line, treat it as an agent failure. Retry once. If the second attempt also fails, report the raw response to the user and skip the fix loop.

### Phase 4: Triage

Parse the critic's report. Sort findings by severity.

- Any HIGH or MEDIUM → enter fix loop (Phase 5)
- Only LOW → send LOWs to maker for single-pass fix, done
- No findings → APPROVED, done

### Phase 5: Fix Loop (severity-gated, max 3 rounds)

```
Round 1:
  Orchestrator → SendMessage to Maker Agent:
    "The critic found these issues. Fix all HIGH and MEDIUM findings.
     One commit per fix (co-located issues may share a commit).
     Commit format: docs(<file-stem>): fix <severity>/<dimension> — <description>"
  Maker fixes and commits
  Orchestrator → Spawn fresh Critic Agent (fresh eyes)
  Critic re-reviews → returns new report
  If new HIGH/MEDIUM → Round 2

Round 2:
  Orchestrator → SendMessage to Maker Agent with new findings
  Maker fixes and commits
  Orchestrator → Spawn fresh Critic Agent
  Critic re-reviews
  If still HIGH/MEDIUM → Round 3 (final)

Round 3 (cap):
  Orchestrator → SendMessage to Maker Agent with remaining findings
  Maker fixes what it can
  Orchestrator reports any remaining issues to user
```

**Why fresh critic each round:** The critic gets true fresh eyes on each re-review — no anchoring to its prior findings or assumptions. This catches regressions that a persistent critic might overlook because it "already checked that."

**Co-located issues:** When multiple findings target the same paragraph or sentence, the maker combines them into a single commit with the highest severity level. Example: `docs(schema): fix HIGH/accuracy+completeness — correct API and add missing context in Construction`

## Data Flow

```
User invokes: /docs-critique docs-data-type-ref Schema
        │
        ▼
  Orchestrator (pure coordinator, never edits files)
        │
        ├──► Phase 1: Spawn Maker Agent
        │      Maker runs /docs-data-type-ref Schema
        │      Produces doc, commits, returns doc path
        │
        ├──► Phase 2: Gather critic context
        │      Find source files, related docs
        │
        ├──► Phase 3: Spawn Critic Agent (fresh)
        │      Critic reads doc + sources + related docs
        │      Returns structured report
        │
        ├──► Phase 4: Triage by severity
        │      HIGH/MEDIUM found?
        │      │
        │  ┌───┴────┐
        │  │ yes    │ no
        │  ▼        ▼
        │  Phase 5  Single-pass LOWs → Done
        │  │
        │  ├──► SendMessage → Maker: "Fix these issues"
        │  │      Maker fixes, commits (one per fix)
        │  │
        │  ├──► Spawn fresh Critic Agent
        │  │      Critic re-reviews
        │  │      APPROVED? → Done
        │  │      ITERATE? → next round (max 3)
        │  │
        │  └──► Round cap reached → report remaining to user
        │
        └──► Final state: APPROVED or remaining issues reported
```

## Integration Into Creation Skills

**No modifications to existing skills.** The orchestrator wraps them — the maker agent runs the skill as-is, then stays alive to receive critique. This is less invasive and keeps existing skills clean.

The user invokes `/docs-critique <skill-name> <args>` instead of invoking the creation skill directly. The orchestrator handles the rest.

## File Inventory

**New files (2):**

- `.claude/agents/docs-critic.md` — critic agent definition
- `.claude/skills/docs-critique/SKILL.md` — orchestrating skill (pure coordinator)

**Modified files (0):**

No existing skills are modified.

## Token Cost Estimate

- Maker agent (creation + alive across rounds): ~30-50K tokens
- Critic agent per spawn: ~15-25K tokens
- Orchestrator overhead: ~2-5K tokens

Typical scenarios:
- APPROVED on first review: ~50-80K tokens (maker + 1 critic)
- 1 round of fixes: ~70-110K tokens (maker + 2 critics)
- 3 rounds (worst case): ~110-150K tokens (maker + 3 critics)
