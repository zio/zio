---
id: modules
title: "Summary of ZIO Modules"
sidebar_label: "Summary of Modules"
slug: "modules"
description: "An overview of all published ZIO artifacts (zio, zio-streams, zio-test, and more) and when to add each one to your build."
keywords:
  - "ZIO Modules"
  - "ZIO Artifacts"
  - "ZIO Dependencies"
  - "sbt libraryDependencies"
---

[Getting Started](getting-started.md) covers the `zio` core artifact, which is all most applications need. ZIO also publishes several additional artifacts for streaming, testing, and other specialized use cases. Add only the ones your project actually uses.

## Core

```scala mdoc:passthrough
println(s"""```""")
println(s"""libraryDependencies += "dev.zio" %% "zio" % "${zio.BuildInfo.version.split('+').head}"""")
println(s"""```""")
```

| Artifact | Description |
| --- | --- |
| `zio` | The ZIO effect system itself: `ZIO`, fibers, `Ref`, `Promise`, `Queue`, `Hub`, `Schedule`, `ZLayer`, and Software Transactional Memory (`STM`/`TRef`). |

## Streaming

```scala mdoc:passthrough
println(s"""```""")
println(s"""libraryDependencies += "dev.zio" %% "zio-streams" % "${zio.BuildInfo.version.split('+').head}"""")
println(s"""```""")
```

| Artifact | Description |
| --- | --- |
| `zio-streams` | `ZStream`, `ZSink`, and `ZPipeline` for pull-based, effectful, backpressured streaming. |

## Testing

```scala mdoc:passthrough
println(s"""```""")
println(s"""libraryDependencies += "dev.zio" %% "zio-test"     % "${zio.BuildInfo.version.split('+').head}" % Test""")
println(s"""libraryDependencies += "dev.zio" %% "zio-test-sbt" % "${zio.BuildInfo.version.split('+').head}" % Test""")
println(s"""testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")""")
println(s"""```""")
```

| Artifact | Description |
| --- | --- |
| `zio-test` | The core ZIO Test framework: `ZIOSpecDefault`, assertions, generators (`Gen`), and test aspects. |
| `zio-test-sbt` | sbt test framework integration, so `sbt test` runs ZIO Test specs. |
| `zio-test-magnolia` | Derives `Gen` and `Diff` instances automatically for case classes and sealed traits via Magnolia. |
| `zio-test-refined` | Generators for [refined](https://github.com/fthomas/refined) refinement types. |
| `zio-test-scalacheck` | Adapters for reusing existing ScalaCheck `Gen` and `Arbitrary` instances as ZIO Test generators. |
| `zio-test-junit` | Runs ZIO Test specs as JUnit tests, for build tools and IDEs that only understand JUnit. |
| `zio-test-junit-engine` | JUnit Platform `TestEngine` for ZIO Test, for IDEs/tools that integrate via the JUnit Platform instead of the legacy JUnit 4 runner. |

## Concurrency

| Artifact | Description |
| --- | --- |
| `zio-concurrent` | Thread-safe concurrent data structures — `ConcurrentMap` and `ConcurrentSet` — built on Java's `java.util.concurrent`. |

## Other

| Artifact | Description |
| --- | --- |
| `zio-managed` | The legacy `ZManaged` resource-management API, kept for migration. New code should use `Scope`, which is built into `zio`. |
| `zio-macros` | Macro-generated helpers (e.g. `@mockable`) used internally and by libraries built on ZIO. |
| `zio-stacktracer` | Lightweight fiber-tracing support used internally by `zio` for readable async stack traces. |

`zio-internal-macros` is also published but is an internal implementation detail with no stable public API — do not depend on it directly.

## Platform Support

| Artifact | JVM | Scala.js | Scala Native |
| --- | :---: | :---: | :---: |
| `zio` | ✅ | ✅ | ✅ |
| `zio-streams` | ✅ | ✅ | ✅ |
| `zio-test` | ✅ | ✅ | ✅ |
| `zio-test-sbt` | ✅ | ✅ | ✅ |
| `zio-test-magnolia` | ✅ | ✅ | — |
| `zio-test-refined` | ✅ | ✅ | — |
| `zio-test-scalacheck` | ✅ | ✅ | ✅ |
| `zio-test-junit` | ✅ | — | — |
| `zio-test-junit-engine` | ✅ | — | — |
| `zio-concurrent` | ✅ | ✅ | ✅ |
| `zio-managed` | ✅ | ✅ | ✅ |
| `zio-macros` | ✅ | ✅ | ✅ |
| `zio-stacktracer` | ✅ | ✅ | ✅ |
