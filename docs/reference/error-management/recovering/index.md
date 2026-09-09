---
id: index
title: "Recovering From Errors"
description: "Recover from ZIO failures using catching, fallback, folding, retrying, timing out, and sandboxing — operators that work on typed errors, defects, and the full Cause graph."
keywords:
  - "Error Recovery"
  - "catchAll"
  - "orElse"
  - "fold"
  - "retry"
  - "timeout"
  - "sandbox"
---

When a ZIO effect fails, we have several strategies for bringing it back to a successful result. Some operators work on the typed error channel; others reach into the full `Cause` graph to handle defects and interruptions as well.

1. **[Catching](catching.md)** recovers from typed failures, defects, interruptions, and the full `Cause` graph using operators such as `catchAll`, `catchSome`, `catchAllCause`, and `catchAllDefect`.

2. **[Fallback](fallback.md)** provides an alternative effect or value when an effect fails, using `orElse`, `orElseFail`, `orElseSucceed`, and related combinators.

3. **[Folding](folding.md)** handles both success and failure in one step with `fold`, `foldZIO`, `foldCause`, and `foldCauseZIO` — the primitive all other ZIO error operators build on.

4. **[Retrying](retrying.md)** re-runs a failing effect according to a `Schedule` policy, with configurable delays, attempt limits, and fallback strategies.

5. **[Timing Out](timing-out.md)** bounds effect execution time with timeout combinators that safely interrupt the effect and optionally convert the result to a typed failure.

6. **[Sandboxing](sandboxing.md)** exposes the full `Cause` graph — including defects and interruptions — using `sandbox`, `unsandbox`, and `sandboxWith`, so any recovery operator can reach them.
