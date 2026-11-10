# ZIO JSON

> [ZIO Json](https://github.com/zio/zio-json) is a fast and secure JSON library with tight ZIO integration.

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-json/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-json_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-json_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-json_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-json_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-json_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-json_2.13) [![ZIO JSON](https://img.shields.io/github/stars/zio/zio-json?style=social)](https://github.com/zio/zio-json)

## Introduction

The goal of this project is to create the best all-round JSON library for Scala:

- **Performance** to handle more requests per second than the incumbents, i.e. reduced operational costs.
- **Security** to mitigate against adversarial JSON payloads that threaten the capacity of the server.
- **Fast Compilation** no shapeless, no type astronautics.
- **Future-Proof**, prepared for Scala 3 and next-generation Java.
- **Simple** small codebase, concise documentation that covers everything.
- **Helpful errors** are readable by humans and machines.
- **ZIO Integration** so nothing more is required.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-json" % "1.1.2"
```

For cross-platform projects with Scala.js and Scala Native need to replace `%%` operator by `%%%`, 
and optionally when using `java.time.ZoneId` and `java.time.ZonedDateTime` types need to add 
the dependency on the latest version of Timezone DB:

```scala
libraryDependencies += "io.github.cquiroz" %%% "scala-java-time-tzdb" % "latest.integration"
```

Continue with the [Getting Started](getting-started.mdx) guide for a walkthrough of encoding and decoding JSON with ZIO JSON.
