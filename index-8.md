# Introduction to ZIO AMQP

> ZIO AMQP is a ZIO-based wrapper around the [RabbitMQ Java client](https://www.rabbitmq.com/client-libraries/java-api-guide). It exposes queues as `ZStream`s of deliveries and serializes access to the underlying RabbitMQ channel, so concurrent fibers cannot corrupt it. Connections and channels are scoped resources: they are closed automatically when the `Scope` that owns them ends, even if the program fails or is interrupted.

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-amqp/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-amqp_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-amqp_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-amqp_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-amqp_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-amqp_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-amqp_2.13) [![ZIO AMQP](https://img.shields.io/github/stars/zio/zio-amqp?style=social)](https://github.com/zio/zio-amqp)

## Installation

Add the following to your `build.sbt` file. ZIO AMQP is published for Scala 2.12, 2.13 and 3, on the JVM only:

```scala
libraryDependencies ++= Seq(
  // ZIO AMQP: ZIO wrapper around the RabbitMQ Java client (JVM)
  "dev.zio" %% "zio-amqp" % "1.0.3"
)
```

ZIO AMQP depends on ZIO 2, ZIO Prelude and the RabbitMQ Java client, which sbt resolves automatically.

## Where to Go Next

Start with [Getting Started](./getting-started.md), which connects to a broker, publishes a message and consumes it back. To understand the ideas behind the API, read [AMQP Basics](./explanation/amqp-basics.md). To look up a type or method, use the [reference](./reference/amqp.md).
