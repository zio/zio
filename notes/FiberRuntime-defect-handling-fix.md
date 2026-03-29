# Patch Notes: Handling errors allows recovering from defects (Issue #9874)

## Problem

"Failure-only" error handlers such as `catchAll`, `catchSome`, `foldZIO`, `mapError`, etc., can incorrectly swallow (i.e., suppress or lose) defects and interruptions when a `Cause` contains both a typed failure (`Cause.Fail`) **and** a defect (`Cause.Die`) or interruption (`Cause.Interrupt`).

### Root Cause

ZIO's `Cause` is a data structure that can represent parallel and sequential combinations of failures. For example:

```
Cause.Both(Cause.Fail("typed error"), Cause.Die(new RuntimeException("defect")))
```

When a failure-only handler (one that is only supposed to handle typed `E` failures, not defects or interruptions) processes such a `Cause`, it should:

1. Extract the typed failure(s) and pass them to the handler.
2. **Preserve** any defects/interruptions that were combined with that failure.
3. Re-raise the defects/interruptions if they were present.

Previously, some paths would extract the typed `E` failure, run the handler, and discard the accompanying defect — effectively swallowing it.

## Fix Strategy

The fix is applied at the `FiberRuntime` level (and/or the ZIO combinator level) in the handling of `EvaluationStep.Continuation`-style error continuations.

### Invariant

> If a `Cause` contains both a typed failure **and** a defect/interruption, a failure-only handler MAY handle the typed failure portion, but MUST re-raise (combine back in) the defect/interruption portion.

### Implementation

The key utility is `Cause#failureOrCause`:

```scala
// Returns either the typed failure E, or the full Cause if it cannot be reduced
// to a pure typed failure (i.e., it contains defects/interruptions).
def failureOrCause: Either[E, Cause[E]]
```

For handlers like `catchAll`, the runtime should:

1. Call `cause.failureOrCause`.
2. If `Left(e)` — pure typed failure, no defects: pass `e` to the handler normally.
3. If `Right(cause)` — cause contains defects/interruptions (possibly alongside failures):
   - Optionally still handle the typed failure portion via `cause.failures`
   - Re-raise the non-failure portion via `cause.stripFailures` **after** running the handler.

This ensures defects propagate to the nearest `catchAllCause`/`sandbox`/`resurrect` handler, which is the correct ZIO semantics.

## Affected Operators

- `ZIO#catchAll`
- `ZIO#catchSome`
- `ZIO#foldZIO` / `ZIO#fold`
- `ZIO#mapError`
- `ZIO#orElse`
- `ZIO#orElseEither`

## Test Cases

See the accompanying test additions in `ZIOSpec` for regression tests covering:

- `catchAll` on `Cause.Both(Fail, Die)` preserves the defect
- `foldZIO` failure branch on mixed cause re-raises defect
- `mapError` does not swallow defects
