---
id: index
title: "Error Channel Operations"
description: "Transform ZIO's error channel without recovering: map errors, chain on failures, filter successes, expose errors and causes in the success channel, convert defects to failures, refine error types, and more."
keywords:
  - "Error Channel"
  - "mapError"
  - "error transformation"
  - "error refinement"
  - "channel operations"
---

ZIO's error channel supports a rich set of transformations beyond simple recovery. These operators let us reshape, inspect, and restructure errors and successes without necessarily resolving the failure — useful when threading error values through a larger computation.

- **[Map Operations](map-operations.md)** — transform error values with `mapError`, `mapErrorCause`, and `mapAttempt` without recovering from the failure.
- **[Chaining Effects Based on Errors](chaining-effects-based-on-errors.md)** — sequence a second effect that depends on the first effect's typed error using `flatMapError`.
- **[Filtering the Success Channel](filtering-the-success-channel.md)** — convert success values that fail a predicate into typed errors using `filterOrFail`, `filterOrDie`, and `filterOrElse`.
- **[Tapping Errors](tapping-errors.md)** — inspect failure values, defects, and `Cause` graphs as a side effect without altering the error channel, using `tapError`, `tapErrorCause`, and `tapDefect`.
- **[Exposing Errors in the Success Channel](exposing-errors-in-the-success-channel.md)** — move typed failures into the success channel as `Either` values using `ZIO#either`, then submerge them back with `ZIO#absolve`.
- **[Exposing the Cause in the Success Channel](exposing-the-cause-in-the-success-channel.md)** — surface the full `Cause[E]` graph in the success channel using `ZIO#cause`, then submerge it with `ZIO#uncause`.
- **[Converting Defects to Failures](converting-defects-to-failures.md)** — turn defects back into typed failures using `absorb` and `resurrect`.
- **[Error Refinement](error-refinement.md)** — narrow or widen the typed error channel using `refineOrDie`, `refineToOrDie`, `unrefine`, and `unrefineTo`.
- **[Flattening Optional Error Types](flattening-optional-error-types.md)** — collapse `Option[E]` error types into plain `E` using `ZIO#flattenErrorOption`, providing a default error for the `None` case.
- **[Merging the Error Channel into the Success Channel](merging-the-error-channel-into-the-success-channel.md)** — collapse the error channel into the success channel using `ZIO#merge` when both types are compatible, producing an infallible effect.
- **[Flipping Error and Success Channels](flipping-the-error-and-success-channel.md)** — swap the error and success channels using `flip` and `flipWith` to apply success-channel operators to errors.
- **[Rejecting Some Success Values](rejecting-some-success-values.md)** — convert select success values into typed failures using `ZIO#reject` and `ZIO#rejectZIO` with a partial function.
- **[Zooming In on Nested Values](zooming-in-on-nested-values.md)** — navigate `Option` and `Either` values nested inside ZIO effects using `some`, `unsome`, `left`, `right`, and related operators.
