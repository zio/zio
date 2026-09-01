# Scala 2 Compatibility Design Note

> This document explains the design of Scala 2.13 support for `zio.blocks.streams` and the constraints that shaped it.

## Motivation

HTTP data types in `zio-blocks` depend on streams. `zio-http` 4 depends on those HTTP data types. Without Scala 2 stream support, `zio-http` cannot offer Scala 2 support. That dependency chain makes Scala 2.13 support for streams a hard requirement, not an optional nicety.

## Non-negotiable constraint: Scala 3 performance

The streams implementation uses Scala 3 features on hot combinator paths, especially in `Stream` and `Sink` methods that participate in specialization, error-channel elimination, and zero-boxing-friendly code generation. The `inline` keyword on performance-sensitive helpers is not cosmetic; it directly affects what the JVM sees.

A Scala 2 compatibility layer is only acceptable if it leaves the Scala 3 hot path structurally unchanged. Concretely, this rules out:

- Moving key instance methods out of the Scala 3 class body into a shared trait
- Removing or weakening `inline` definitions to satisfy Scala 2's lack of that feature
- Introducing extra trait boundaries that alter the generated Scala 3 bytecode

Adapting surface syntax from Scala 3 `using` to Scala 2 `implicit` is fine where it does not touch the hot path. The risk is not the spelling of contextual parameters; the risk is changing the runtime shape of `Stream` and `Sink` under Scala 3.

## Rejected approach: version-specific trait extraction

An early draft extracted several instance methods into `StreamVersionSpecific` and `SinkVersionSpecific` traits under `scala-2/` and `scala-3/`, with the shared classes extending those traits. The methods moved or routed through these traits included:

- `Stream.++`, `Stream.catchAll`, `Stream.catchDefect`, `Stream.concat`
- `Stream.flatMap`, `Stream.mapError`, `Stream.orElse`, `Stream.&&`
- `Sink.mapError`

This shape localized the syntax differences neatly, but changed the structure of the Scala 3 hot path enough to produce measurable regressions. Benchmarks run with `streams-benchmark` on Scala 3.8.3 and JDK 25:

| Benchmark | Baseline (`main`) | Trait-extraction draft | Change |
|---|---:|---:|---|
| `StreamPipelineBench.zb_flatMap` | 14924.328 ops/s | 1178.997 ops/s | ~12x regression |
| `StreamPipelineBench.zb_concat` | 26003.818 ops/s | 22044.497 ops/s | regression |
| `StreamPipelineBench.zb_filterMap` | 54812.709 ops/s | 50349.471 ops/s | regression |

The `flatMap` result is the clearest signal. Dropping from roughly 14.9k ops/s to 1.18k ops/s is not an acceptable tradeoff for any compatibility layer. The approach was rejected.

## Chosen approach: full per-version split

The implementation uses a full per-version source split. `Stream`, `Sink`, `Reader`, `Writer`, `Pipeline`, and `Interpreter` each have separate implementations under `scala-3/` and `scala-2/` source directories.

Under `scala-3/`, the existing method bodies remain exactly as they were before Scala 2 support was added. `inline` helpers stay in place. No hot path is touched.

Under `scala-2/`, equivalent semantics are provided using `final val` and `final def` where Scala 3 uses `inline val` and `inline def`. The Scala 2 surface is behaviorally equivalent; the Scala 2 compiler cannot honour `inline` in the same way, but the final modifier prevents virtual dispatch and allows the JIT similar opportunities.

## Maintenance notes

Any change to the behavior or public API of `Stream`, `Sink`, `Reader`, `Writer`, `Pipeline`, or `Interpreter` must be applied to both the `scala-2/` and `scala-3/` source trees. The split is intentional and permanent; it is not scaffolding to be collapsed later.

When making changes that touch hot combinators, re-run `streams-benchmark` and verify that `zb_flatMap`, `zb_concat`, and `zb_filterMap` do not regress relative to the `main` baseline.
