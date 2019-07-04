![ZIO Logo](./ZIO.png)

| CI | Coverage | Snapshot | Release | Issues | Users |
| --- | --- | --- | --- | --- | --- |
| [![Build Status][Badge-Circle]][Link-Circle] | [![Coverage Status][Badge-Codecov]][Link-Codecov] | [![Snapshot Artifacts][Badge-SonatypeSnapshots]][Link-SonatypeSnapshots] | [![Release Artifacts][Badge-SonatypeReleases]][Link-SonatypeReleases] | [![Average time to resolve an issue][Badge-IsItMaintained]][Link-IsItMaintained] | [![Scaladex dependencies badge][Badge-Scaladex]][Link-Scaladex] |

# Welcome to ZIO

ZIO is a zero-dependency Scala library for asynchronous and concurrent programming.

Powered by highly-scalable, non-blocking fibers that never waste or leak resources, ZIO lets you build scalable, resilient, and reactive applications that meet the needs of your business.

 - **High-performance**. Build scalable applications with 100x the performance of Scala's `Future`.
 - **Type-safe**. Use the full power of the Scala compiler to catch bugs at compile time.
 - **Concurrent**. Easily build concurrent apps without deadlocks, race conditions, or complexity.
 - **Asynchronous**. Write sequential code that looks the same whether it's asynchronous or synchronous.
 - **Resource-safe**. Build apps that never leak resources (including threads!), even when they fail.
 - **Testable**. Inject test services into your app for fast, deterministic, and type-safe testing.
 - **Resilient**. Build apps that never lose errors, and which respond to failure locally and flexibly.
 - **Functional**. Rapidly compose solutions to complex problems from simple building blocks.

To learn more about ZIO, see the following references:

 - [Homepage](https://zio.dev/)
 - [Contributor's Guide](CONTRIBUTING.md)
 - [License](LICENSE)
 - [Issues](https://github.com/zio/zio/issues)
 - [Pull Requests](https://github.com/zio/zio/pulls)

---

# Sponsors

[![Septimal Mind][Image-SeptimalMind]][Link-SeptimalMind]

[Septimal Mind][Link-SeptimalMind] sponsors work on ZIO Tracing and continuous maintenance.

[![SoftwareMill][Image-SoftwareMill]][Link-SoftwareMill]

[SoftwareMill][Link-SoftwareMill] generously provides ZIO with paid-for CircleCI build infrastructure.

---

# [Learn More on the ZIO Homepage](https://zio.dev/)

---

### Legal

Copyright 2017 - 2019 John A. De Goes and the ZIO Contributors. All rights reserved.


[Link-Codecov]: https://codecov.io/gh/zio/zio?branch=master "Codecov"
[Link-IsItMaintained]: http://isitmaintained.com/project/zio/zio "Average time to resolve an issue"
[Link-Scaladex]: https://index.scala-lang.org/search?q=dependencies:zio/zio "Scaladex"
[Link-SonatypeReleases]: https://oss.sonatype.org/content/repositories/releases/dev/zio/zio_2.12/ "Sonatype Releases"
[Link-SonatypeSnapshots]: https://oss.sonatype.org/content/repositories/staging/dev/zio/zio_2.12/ "Sonatype Snapshots"
[Link-Circle]: https://circleci.com/gh/zio/zio "circleci"
[Link-SoftwareMill]: https://softwaremill.com "SoftwareMill"
[Link-SeptimalMind]: https://7mind.io "Septimal Mind"

[Badge-Codecov]: https://codecov.io/gh/zio/zio/coverage.svg?branch=master "Codecov"
[Badge-IsItMaintained]: http://isitmaintained.com/badge/resolution/zio/zio.svg "Average time to resolve an issue"
[Badge-Scaladex]: https://index.scala-lang.org/count.svg?q=dependencies:zio/zio&subject=scaladex "Scaladex"
[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio_2.12.svg "Sonatype Releases"
[Badge-SonatypeSnapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio_2.12.svg "Sonatype Snapshots"
[Badge-Circle]: https://circleci.com/gh/zio/zio.svg?style=svg "circleci"

[Image-SoftwareMill]: ./website/static/img/softwaremill.svg "SoftwareMill"
[Image-SeptimalMind]: ./website/static/img/septimal_mind.svg "Septimal Mind"
