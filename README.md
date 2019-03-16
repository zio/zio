![ZIO Logo](./ZIO.png)

| CI | Coverage | Snapshot | Release | Issues | Users |
| --- | --- | --- | --- | --- | --- |
| [![Build Status][Badge-Travis]][Link-Travis] | [![Coverage Status][Badge-Codecov]][Link-Codecov] | [![Snapshot Artifacts][Badge-SonatypeSnapshots]][Link-SonatypeSnapshots] | [![Release Artifacts][Badge-SonatypeReleases]][Link-SonatypeReleases] | [![Average time to resolve an issue][Badge-IsItMaintained]][Link-IsItMaintained] | [![Scaladex dependencies badge][Badge-Scaladex]][Link-Scaladex] |
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

 - [Microsite](https://scalaz.github.io/scalaz-zio/)
 - [Contributor's Guide](CONTRIBUTING.md)
 - [License](LICENSE)
 - [Issues](https://github.com/scalaz/scalaz-zio/issues)
 - [Pull Requests](https://github.com/scalaz/scalaz-zio/pulls)

---

# [Learn More on the ZIO Microsite](https://scalaz.github.io/scalaz-zio/)

---

### Legal

Copyright 2017 - 2019 John A. De Goes and the ZIO Contributors. All rights reserved.


[Link-Codecov]: https://codecov.io/gh/scalaz/scalaz-zio?branch=master "Codecov"
[Link-IsItMaintained]: http://isitmaintained.com/project/scalaz/scalaz-zio "Average time to resolve an issue"
[Link-Scaladex]: https://index.scala-lang.org/search?q=dependencies:scalaz/scalaz-zio "Scaladex"
[Link-SonatypeReleases]: https://oss.sonatype.org/content/repositories/releases/org/scalaz/scalaz-zio_2.12/ "Sonatype Releases"
[Link-SonatypeSnapshots]: https://oss.sonatype.org/content/repositories/staging/org/scalaz/scalaz-zio_2.12/ "Sonatype Snapshots"
[Link-Travis]: https://travis-ci.org/scalaz/scalaz-zio "Travis CI"

[Badge-Codecov]: https://codecov.io/gh/scalaz/scalaz-zio/coverage.svg?branch=master "Codecov"
[Badge-IsItMaintained]: http://isitmaintained.com/badge/resolution/scalaz/scalaz-zio.svg "Average time to resolve an issue"
[Badge-Scaladex]: https://index.scala-lang.org/count.svg?q=dependencies:scalaz/scalaz-zio&subject=scaladex "Scaladex"
[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/org.scalaz/scalaz-zio_2.12.svg "Sonatype Releases"
[Badge-SonatypeSnapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/org.scalaz/scalaz-zio_2.12.svg "Sonatype Snapshots"
[Badge-Travis]: https://travis-ci.org/scalaz/scalaz-zio.svg?branch=master "Travis CI"
