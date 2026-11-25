# Introduction to ZIO AWS

> [ZIO AWS](https://zio.dev/zio-aws) is an AWS wrapper for [ZIO](https://zio.dev) for _all_ AWS services, built on the AWS Java SDK v2.

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-aws/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-aws-core_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-aws-core_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-aws-core_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-aws-core_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-aws-core_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-aws-core_2.13) [![ZIO AWS](https://img.shields.io/github/stars/zio/zio-aws?style=social)](https://github.com/zio/zio-aws)

## Introduction

ZIO AWS gives a ZIO application direct access to every AWS service. Use it on its own when a program only needs to call a few operations, or as a building block for higher-level libraries around one service.

Key features of ZIO AWS:

- A common configuration layer, `AwsConfig`, shared by every service client
- A ZIO service module per AWS service, with a wrapper for every operation
- HTTP client implementations (Netty, http4s, Akka HTTP and AWS CRT), provided as ZIO layers
- `ZStream` wrappers around paginated, byte-streaming and event-streaming operations
- Scala case classes, enums and newtypes in place of the Java builders and `null` values
- [Aspects](aspects.md) that add logging, metrics or retries to every call of a service

## Installation

ZIO AWS is published for Scala 2.13 and Scala 3, on the JVM. Every module of one release has the same version. A program needs the core module, one HTTP client module and one module for each AWS service it calls. This example adds EC2:

```scala
libraryDependencies ++= Seq(
  // Core: AwsConfig, AwsError, aspects and streaming support. JVM.
  "dev.zio" %% "zio-aws-core" % "7.59.5.2",

  // HTTP client that sends the requests, built on Netty. JVM.
  "dev.zio" %% "zio-aws-netty" % "7.59.5.2",

  // The EC2 service module; add one module per AWS service you call. JVM.
  "dev.zio" %% "zio-aws-ec2" % "7.59.5.2"
)
```

## Modules

Besides the core module, ZIO AWS has two kinds of modules: HTTP clients and AWS service modules.

### HTTP Clients

Pick one HTTP client module. Netty is the default choice; the others fit an application that already runs on their stack:

```scala
libraryDependencies ++= Seq(
  // Netty client, the one the AWS SDK uses by default. Supports HTTP/2. JVM.
  "dev.zio" %% "zio-aws-netty" % "7.59.5.2",

  // http4s Blaze client, for applications already on http4s. JVM.
  "dev.zio" %% "zio-aws-http4s" % "7.59.5.2",

  // Akka HTTP client; needs an ActorSystem layer. JVM.
  "dev.zio" %% "zio-aws-akka-http" % "7.59.5.2",

  // AWS Common Runtime (CRT) client. JVM.
  "dev.zio" %% "zio-aws-crt-http" % "7.59.5.2"
)
```

[Choose an HTTP Client](guide-choose-http-client.md) helps you pick one and links to the reference of each.

### AWS Service Modules

There is one generated module per AWS service, named `zio-aws-<service>`, such as `zio-aws-s3`, `zio-aws-dynamodb` or `zio-aws-sqs`:

```scala
libraryDependencies ++= Seq(
  // Amazon S3. JVM.
  "dev.zio" %% "zio-aws-s3" % "7.59.5.2",

  // Amazon DynamoDB. JVM.
  "dev.zio" %% "zio-aws-dynamodb" % "7.59.5.2"
)
```

The [Artifacts](artifacts.md) page lists every service module.

## Example

The following program prints the ID of every EC2 instance in the account. `describeInstances` is a paginated operation, so ZIO AWS returns it as a stream that fetches the next page when it is needed:

```scala
import zio._
import zio.stream.ZStream
import zio.aws.core.config.AwsConfig
import zio.aws.ec2.Ec2
import zio.aws.ec2.model.DescribeInstancesRequest
import zio.aws.netty.NettyHttpClient

object ListInstances extends ZIOAppDefault {
  val program =
    Ec2
      .describeInstances(DescribeInstancesRequest())
      .mapZIO(_.getInstances)
      .flatMap(ZStream.fromIterable(_))
      .mapZIO(_.getInstanceId)
      .foreach(id => Console.printLine(id).orDie)

  def run =
    program.provide(NettyHttpClient.default, AwsConfig.default, Ec2.live)
}
```

The [Getting Started](getting-started.md) page explains each part of a program like this one.

## Design

The library has a core module and one generated module for _each_ AWS service. A code generator reads the service models of the AWS Java SDK and produces a thin ZIO wrapper around each service's async client. Because the wrapper delegates every call to the Java SDK, the generator does not have to know how each AWS protocol works, and a new AWS SDK release becomes a new ZIO AWS release automatically.

Three layers build a service client. The HTTP client layer provides an `HttpClient`, `AwsConfig` applies the region, credentials and timeouts, and the service layer, such as `Ec2.live`, builds the client:

```
NettyHttpClient.default   provides HttpClient
        |
        v
AwsConfig.default         provides AwsConfig, needs HttpClient
        |
        v
Ec2.live                  provides Ec2, needs AwsConfig
```

The [Wrappers](wrappers.md) reference describes what the generated modules contain.

## Where to Start

If you are new to ZIO AWS, start with [Getting Started](getting-started.md), then build a complete program in the [tutorial](tutorial-first-program.md). The how-to guides cover single tasks, such as [configuring credentials and region](guide-configure-credentials-and-region.md), [using a local endpoint](guide-use-a-local-endpoint.md), [streaming S3 objects](guide-stream-s3-objects.md) and [adding retries](guide-add-retries.md). Related libraries, articles and talks are listed under [Resources](resources.md).
