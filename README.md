![ZIO Logo](./ZIO.png)

| CI | Release | Snapshot | Issues | Scaladex | Discord | Twitter |
| --- | --- | --- | --- | --- | --- | --- |
| [![Build Status][Badge-Circle]][Link-Circle] | [![Release Artifacts][Badge-SonatypeReleases]][Link-SonatypeReleases] | [![Snapshot Artifacts][Badge-SonatypeSnapshots]][Link-SonatypeSnapshots] | [![Average time to resolve an issue][Badge-IsItMaintained]][Link-IsItMaintained] | [![Badge-Scaladex-page]][Link-Scaladex-page] | [![Badge-Discord]][Link-Discord] | [![Badge-Twitter]][Link-Twitter] |

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
 - [Contributor's Guide](./docs/about/contributing.md)
 - [License](LICENSE)
 - [Issues](https://github.com/zio/zio/issues)
 - [Pull Requests](https://github.com/zio/zio/pulls)

---

# Adopters

Following is a partial list of companies happily using ZIO in
production to craft concurrent applications.

Want to see your company here? [Submit a PR](https://github.com/zio/zio/edit/master/README.md)!

* [adsquare](https://www.adsquare.com/)
* [AutoScout24](https://www.autoscout24.de)
* [Coralogix](https://coralogix.com)
* [DHL Parcel The Netherlands](https://www.werkenbijdhl.nl/it)
* [Hunters.AI](https://hunters.ai)
* [Megogo](https://megogo.net)
* [Optrak](https://optrak.com)
* [Performance Immo](https://www.performance-immo.com/)
* [TomTom](https://tomtom.com)
* [Wehkamp](https://www.wehkamp.nl)

# Sponsors

[![Scalac][Image-Scalac]][Link-Scalac]

[Scalac][Link-Scalac] sponsors ZIO Hackathons and contributes work to multiple projects in ZIO ecosystem.

[![Septimal Mind][Image-SeptimalMind]][Link-SeptimalMind]

[Septimal Mind][Link-SeptimalMind] sponsors work on ZIO Tracing and continuous maintenance.

[![SoftwareMill][Image-SoftwareMill]][Link-SoftwareMill]

[SoftwareMill][Link-SoftwareMill] generously provides ZIO with paid-for CircleCI build infrastructure.


---

# [Learn More on the ZIO Homepage](https://zio.dev/)

---

## Code of Conduct

See the [Code of Conduct](./docs/about/code_of_conduct.md)

---

## Support

Come chat with us on [![Badge-Discord]][Link-Discord].

---

### Legal

Copyright 2017 - 2020 John A. De Goes and the ZIO Contributors. All rights reserved.


[Link-Codecov]: https://codecov.io/gh/zio/zio?branch=master "Codecov"
[Link-IsItMaintained]: http://isitmaintained.com/project/zio/zio "Average time to resolve an issue"
[Link-Scaladex-page]: https://index.scala-lang.org/zio/zio/zio "Scaladex"
[Link-SonatypeReleases]: https://oss.sonatype.org/content/repositories/releases/dev/zio/zio_2.12/ "Sonatype Releases"
[Link-SonatypeSnapshots]: https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio_2.12/ "Sonatype Snapshots"
[Link-Circle]: https://circleci.com/gh/zio/zio "circleci"
[Link-Scalac]: https://scalac.io "Scalac"
[Link-SoftwareMill]: https://softwaremill.com "SoftwareMill"
[Link-SeptimalMind]: https://7mind.io "Septimal Mind"
[Link-Discord]: https://discord.gg/2ccFBr4 "Discord"
[Link-Twitter]: https://twitter.com/zioscala

[Badge-Codecov]: https://codecov.io/gh/zio/zio/coverage.svg?branch=master "Codecov"
[Badge-IsItMaintained]: http://isitmaintained.com/badge/resolution/zio/zio.svg "Average time to resolve an issue"
[Badge-Scaladex-page]: https://index.scala-lang.org/zio/zio/zio/latest.svg "Scaladex"
[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio_2.12.svg "Sonatype Releases"
[Badge-SonatypeSnapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio_2.12.svg "Sonatype Snapshots"
[Badge-Circle]: https://circleci.com/gh/zio/zio.svg?style=svg "circleci"
[Badge-Discord]: https://img.shields.io/discord/629491597070827530?logo=discord "chat on discord"
[Badge-Twitter]: https://img.shields.io/twitter/follow/zioscala.svg?style=plastic&label=follow&logo=twitter

[Image-Scalac]: ./website/static/img/scalac.svg "Scalac"
[Image-SoftwareMill]: ./website/static/img/softwaremill.svg "SoftwareMill"
[Image-SeptimalMind]: ./website/static/img/septimal_mind.svg "Septimal Mind"
