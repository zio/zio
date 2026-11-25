# Introduction to ZIO Lambda

> ZIO Lambda is a ZIO-based AWS Custom Runtime compatible with GraalVM Native Image. It lets you write an AWS Lambda function as a ZIO program, with typed JSON events in and typed JSON responses out, and deploy it as a GraalVM native binary.

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-lambda/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-lambda_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-lambda_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-lambda_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-lambda_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-lambda_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-lambda_2.13) [![ZIO Lambda](https://img.shields.io/github/stars/zio/zio-lambda?style=social)](https://github.com/zio/zio-lambda)

## Installation

ZIO Lambda is published for Scala 2.12, 2.13 and 3, and runs on the JVM, including as a GraalVM native image. Add the core module to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  // The runtime: ZLambdaRunner, Context. JVM only. It brings zio-json with it
  "dev.zio" %% "zio-lambda" % "1.0.7"
)
```

## Modules

The event and response modules are optional. Add them when you want ready-made Scala types for AWS events and responses:

```scala
libraryDependencies ++= Seq(
  // Scala types for the events that AWS services send to Lambda. JVM only
  "dev.zio" %% "zio-lambda-event" % "1.0.7",

  // Scala types for the responses that some AWS integrations expect. JVM only
  "dev.zio" %% "zio-lambda-response" % "1.0.7"
)
```

See [Event and Response Types](./event-and-response-types.md) for what the two modules contain.

## Where to Go Next

1. [Getting Started](./getting-started.md) shows how to write a handler with custom event and response types.
2. The Reference section describes [`ZLambdaRunner`](./reference/zlambda-runner.md), [`Context`](./reference/context.md) and every event and response type.
3. [Build an HTTP API](./tutorials/http-api-on-api-gateway.md) walks through an HTTP API on API Gateway step by step.
4. The how-to guides cover [testing a handler locally](./testing-locally.md) and [deploying it to AWS Lambda](./deployment.md).
