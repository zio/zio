# Scala 2 Compatibility Design Note

> Why streams supports Scala 2.13, the hot-path constraint that shaped the design, and why the sources are shared rather than split per version.

This document explains the design of Scala 2.13 support for `zio.blocks.streams` and the constraints that shaped it.

## Motivation

HTTP data types in `zio-blocks` depend on streams. `zio-http` 4 depends on those HTTP data types. Without Scala 2 stream support, `zio-http` cannot offer Scala 2 support. That dependency chain makes Scala 2.13 support for streams a hard requirement, not an optional nicety.

## Non-negotiable Constraint: Scala 3 Performance

When Scala 2 support was first proposed, the streams implementation used Scala 3 features on hot combinator paths, especially in `Stream` and `Sink` methods that participate in specialization, error-channel elimination, and zero-boxing-friendly code generation. The `inline` keyword on performance-sensitive helpers was not cosmetic; it directly affected what the JVM saw.

A Scala 2 compatibility layer is only acceptable if it leaves the Scala 3 hot path structurally unchanged. Concretely, this rules out:

- Moving key instance methods out of the Scala 3 class body into a shared trait
- Removing or weakening `inline` definitions to satisfy Scala 2's lack of that feature
- Introducing extra trait boundaries that alter the generated Scala 3 bytecode

Adapting surface syntax from Scala 3 `using` to Scala 2 `implicit` is fine where it does not touch the hot path. The risk is not the spelling of contextual parameters; the risk is changing the runtime shape of `Stream` and `Sink` under Scala 3.

## Rejected Approach: Version-specific Trait Extraction

An early draft extracted several instance methods into `StreamVersionSpecific` and `SinkVersionSpecific` traits under `scala-2/` and `scala-3/`, with the shared classes extending those traits. The methods moved or routed through these traits included:

- `Stream.++`, `Stream.catchAll`, `Stream.catchDefect`, `Stream.concat`
- `Stream.flatMap`, `Stream.mapError`, `Stream.orElse`, `Stream.&&`
- `Sink.mapError`

This shape localized the syntax differences neatly, but changed the structure of the Scala 3 hot path enough to produce measurable regressions. Benchmarks run with `streams-benchmark` on JDK 25 (recorded against Scala 3.8.3; the project targets Scala 3.9.0):

| Benchmark | Baseline (`main`) | Trait-extraction draft | Change |
|---|---:|---:|---|
| `StreamPipelineBench.zb_flatMap` | 14924.328 ops/s | 1178.997 ops/s | ~12x regression |
| `StreamPipelineBench.zb_concat` | 26003.818 ops/s | 22044.497 ops/s | regression |
| `StreamPipelineBench.zb_filterMap` | 54812.709 ops/s | 50349.471 ops/s | regression |

The `flatMap` result is the clearest signal. Dropping from roughly 14.9k ops/s to 1.18k ops/s is not an acceptable tradeoff for any compatibility layer. The approach was rejected.

## Current Structure: One Shared Source Set

Streams compiles from a single shared source set. `Stream`, `Sink`, `Reader`, `Writer`, and `Pipeline` each have exactly one implementation, under `streams/shared/src/main/scala/`, and the same bytes compile for Scala 2.13 and Scala 3.

What made that possible is that the hot combinator paths no longer use any Scala 3-only construct. There is no `inline def` or `inline val`, no `using` or `given`, no `extension`, `enum`, or `opaque type` anywhere in the module's main sources. The constraint above was met not by mirroring the hot path into two trees but by writing it in the syntax both compilers accept, which leaves nothing for a Scala 2 tree to fork.

Two files remain version-specific, and each exists under both `scala-2/` and `scala-3/`:

```
streams/shared/src/main/
├── scala/                  the whole public API: Stream, Sink, Reader, Writer, Pipeline
├── scala-2/zio/blocks/streams/
│   ├── LowPriorityJvmTypeInferPlatform.scala
│   └── internal/InternalVersionSpecific.scala
└── scala-3/zio/blocks/streams/
    ├── LowPriorityJvmTypeInferPlatform.scala
    └── internal/InternalVersionSpecific.scala
```

Both declare `private[streams]` traits and neither is reachable from user code. `LowPriorityJvmTypeInferPlatform` supplies the lowest-priority `JvmType.Infer[A]` fallback that sends an unrecognized element type to the boxed lane, mixed into `JvmType.Infer`; see [Zero-Boxing](./zero-boxing.md) for the lane machinery it serves. `InternalVersionSpecific` supplies the `pullInt`, `pullLong`, `pullFloat`, and `pullDouble` helpers that read one element from a `Reader.SyncReader` on its primitive lane.

Each pair is currently byte-identical, so the surviving split is directory-only: it is a place where a per-version difference could be expressed, not a difference that exists today. Nobody maintains two implementations of anything.

There is no public-API difference between Scala 2.13 and Scala 3 in streams, as [Platform Differences](./platform-differences.md#scala-2-versus-scala-3) records. What differs for you is surface syntax you write anyway — a wildcard import is `_` rather than `*`, and a contextual parameter is `implicit` rather than `using`. The axis that actually changes which members exist is JVM versus Scala.js, covered by the [availability matrix](./platform-differences.md#availability-matrix).

## Maintenance Notes

Any change to the behavior or public API of `Stream`, `Sink`, `Reader`, `Writer`, or `Pipeline` goes to the shared source under `streams/shared/src/main/scala/`. There is no second tree to mirror it into.

Keep the shared sources inside the syntax both compilers accept. An `inline def`, a `using` clause, or an `extension` method added there compiles under Scala 3 and breaks the published Scala 2.13 build, and the constraint above rules out recovering by forking the affected method into two trees.

Touch a file under `scala-2/` or `scala-3/` only when you intend a genuine per-version difference, and then change the counterpart in the same commit. Because each pair is byte-identical today, editing one alone is a divergence rather than a fix, and nothing in the build will tell you that you meant it.

When making changes that touch hot combinators, re-run `streams-benchmark` and verify that `zb_flatMap`, `zb_concat`, and `zb_filterMap` do not regress relative to the `main` baseline.

## See Also

- [Platform Differences](./platform-differences.md) — the JVM versus Scala.js availability matrix, and the Scala 2 versus Scala 3 summary
- [Zero-Boxing](./zero-boxing.md) — `JvmType.Infer` and the primitive lanes that `LowPriorityJvmTypeInferPlatform` backs
