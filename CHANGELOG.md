
## Unreleased

### Fix

- `Cause#failureOrCause` and `Cause#failureTraceOrCause` now correctly prioritize defects (`Die`) and interruptions (`Interrupt`) over failures (`Fail`) when both are present in the same `Cause`. Previously, `catchAll`, `catchSome`, and `orElse` could silently swallow unrecoverable errors in `Both(Die, Fail)` or `Both(Interrupt, Fail)` causes. Fixes [#9874](https://github.com/zio/zio/issues/9874).
