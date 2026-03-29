# Fix Notes: Handling errors allows recovering from defects (#9874)

This file documents the fix approach. The actual fix is in FiberRuntime.scala.

## Problem
When a Cause contains both Fail and Die (e.g., `Cause.die(ex) && Cause.fail("e")`),
catchAll / catchSome / mapError and similar "failure-only" handlers would:
1. Extract the failure value via `cause.failureOption`
2. Invoke the handler with it
3. Completely lose the defect portion of the cause

## Fix
In FiberRuntime, when evaluating OnFailure continuations, we must check whether the
cause contains defects/interruptions AFTER stripping the failure. If so, the remaining
defect/interrupt cause must be re-raised rather than silently dropped.

The invariant: defects and interruptions always take priority over failures.
