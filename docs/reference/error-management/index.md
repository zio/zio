---
id: "index"
title: "Introduction to Error Management in ZIO"
description: "ZIO's comprehensive approach to handling typed errors with facilities for catching, propagating, and transforming errors type-safely."
keywords:
  - "Typed Errors"
  - "Error Management"
  - "Error Propagation"
  - "Error Recovery"
  - "Error Transformation"
---

ZIO's error management model is built around three interlocking types: `Cause[E]`, `Exit[E, A]`, and the error channel of `ZIO[R, E, A]`. Together they give every ZIO program a precise, type-safe account of every failure, unhandled exception, and fiber interruption that can occur — and a rich set of operators for recovering from each. Most readers encounter `Cause[E]` through recovery operators such as `ZIO#catchAllCause` and `ZIO#sandbox` rather than by constructing it directly; it is the runtime representation that the ZIO fiber runtime manages internally. Their structural shapes are:

```scala
sealed abstract class Cause[+E]     // runtime error graph: failures, defects, interruptions
sealed trait Exit[+E, +A]           // completed fiber result: Success or Failure(cause)
sealed trait ZIO[-R, +E, +A]        // effect with a typed error channel E
```

## Overview

The error management area is organized into six topics, each covering a distinct concern:

- **[Three Types of Errors](types/index.md)** — the taxonomy of typed failures (`Failure`), untyped runtime exceptions (`Defect`), and catastrophic JVM errors (`Fatal`) that ZIO distinguishes at both the type-system and runtime levels.
- **[Core Concepts](expected-and-unexpected-errors.md)** — the typed-error guarantee, what it means for an effect to be "unexceptional", and how sequential versus parallel composition affects the error structure.
- **[Error Channel Operations](operations/map-operations.md)** — mapping, filtering, refining, flipping, and otherwise transforming the error channel without necessarily recovering from it.
- **[Recovering From Errors](recovering/catching.md)** — catching typed failures, folding over results, retrying with a policy, sandboxing defects, timing out, and falling back to alternative effects.
- **[Error Accumulation](error-accumulation.md)** — collecting all failures from a collection of effects without short-circuiting, using `ZIO.validate` and related combinators.
- **[Best Practices](best-practices/algebraic-data-types.md)** — modelling domain errors as sealed ADTs, keeping defects out of the typed channel, avoiding reflexive logging, and using union types (Scala 3) for lightweight error composition.

## How They Work Together

The three core types interact through a single data flow: constructors create `Cause` leaf nodes, composition combines them into a `Cause` graph, the fiber runtime records the graph in an `Exit` value, and recovery operators intercept the graph at different granularities. Here is the step-by-step workflow:

1. `ZIO.fail(e)` produces a `Cause.Fail(e, trace)` node; `ZIO.die(t)` produces `Cause.Die(t, trace)`; `ZIO.interrupt` produces `Cause.Interrupt(fiberId, trace)`.
2. When two sequential effects both fail, their causes are joined with `++`, producing `Cause.Then(left, right)` — a node that preserves temporal ordering.
3. When two parallel effects both fail, their causes are joined with `&&`, producing `Cause.Both(left, right)` — a node that records concurrent failures without imposing an order.
4. When a fiber finishes, the runtime wraps the outcome in an `Exit`: either `Exit.Success(value)` for a successful result or `Exit.Failure(cause)` carrying the full `Cause[E]` graph.
5. Recovery operators such as `ZIO#catchAll` and `ZIO#catchSome` intercept only the typed `E` channel — they match `Cause.Fail` nodes but leave `Cause.Die` and `Cause.Interrupt` untouched.
6. `sandbox` promotes the full `Cause[E]` into the typed error channel, producing `ZIO[R, Cause[E], A]`, so any `catch*` operator can then pattern-match against `Cause.Die`, `Cause.Interrupt`, `Cause.Then`, or `Cause.Both`.
7. `ZIO#unsandbox` (or the companion-object form `ZIO.unsandbox(v)`) reverts the sandbox, moving `Cause[E]` back out of the typed channel and restoring the effect's original `E` type.

The diagram below maps this data flow from construction through composition, fiber exit, and recovery:

```
  ┌────────────┐   ┌────────────┐   ┌────────────────┐
  │  ZIO.fail  │   │  ZIO.die   │   │ ZIO.interrupt  │
  └─────┬──────┘   └─────┬──────┘   └───────┬────────┘
        │                │                  │
        ▼                ▼                  ▼
  ┌────────────┐   ┌────────────┐   ┌────────────────┐
  │ Cause.Fail │   │ Cause.Die  │   │Cause.Interrupt │
  └─────┬──────┘   └─────┬──────┘   └───────┬────────┘
        │ (++)           │ (&&)             │
        └────────────────┘                  │
                 │        ┌─────────────────┘
                 ▼        ▼
         ┌──────────────────────────────┐
         │  Cause.Then  /  Cause.Both   │
         │  (sequential/parallel graph) │
         └──────────────┬───────────────┘
                        │
         ┌──────────────┴───────────────┐
         ▼                              ▼
  ┌─────────────────────┐  ┌─────────────────────┐
  │ Exit.Failure(cause) │  │  Exit.Success(value) │
  └──────────┬──────────┘  └──────────────────────┘
             │
             ▼
  ┌──────────────────────────────────────────────────┐
  │  catchAll      (typed E only)                    │
  │  catchAllCause (full Cause[E] graph)             │
  │  sandbox       → promotes Cause[E] to E channel  │
  └──────────────────────────────────────────────────┘
             │
             ▼
  ┌──────────────────────────────────────────────────┐
  │       recovered ZIO[R, E2, A]  effect            │
  └──────────────────────────────────────────────────┘
```

`foldCauseZIO` is the primitive from which all `catch*` and `fold*` operators are derived — every recovery method in ZIO is ultimately expressed in terms of it.

**Type Relationships:**

- `Cause[E]` is the runtime representation of all three error channels; the type parameter `E` covers only the typed-failure dimension, while `Cause.Die` and `Cause.Interrupt` nodes are always untyped.
- `Exit[E, A]` is a sealed subtype of `ZIO[Any, E, A]` — an `Exit` value can be used anywhere a `ZIO` is expected without explicit lifting.
- `Exit.Failure(cause)` wraps the full `Cause[E]` graph; `Exit.Success(a)` carries the result value of a successfully completed fiber.
- `Cause.Then` preserves temporal ordering (sequential failures); `Cause.Both` preserves concurrency (parallel failures); `Cause.Stackless` suppresses stack-trace rendering for its sub-cause.
- `foldCauseZIO` is the primitive from which all other `catch*` and `fold*` operators are derived; every other recovery combinator calls it internally.

To see these types working together, consider the sandbox/unsandbox pattern — the standard approach when you need to recover from a defect or inspect the full `Cause` structure:

```scala mdoc:compile-only
import zio._

val effect: ZIO[Any, String, String] =
  ZIO.succeed("primary result") *> ZIO.fail("Oh uh!")

val recovered: ZIO[Any, String, String] =
  effect
    .sandbox                                              // ZIO[Any, Cause[String], String]
    .catchSome { case Cause.Fail(_, _) =>
      ZIO.succeed("fallback result")                      // matches typed failure only
    }
    .unsandbox                                            // ZIO[Any, String, String]
```

After `sandbox` promotes the entire `Cause[String]` into the error channel, `catchSome` can pattern-match on `Cause.Fail` specifically — leaving any `Cause.Die` or `Cause.Interrupt` nodes untouched. Calling `unsandbox` at the end restores the effect's `String` error type.
