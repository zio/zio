# Introduction to ZIO S3

> [ZIO S3](https://github.com/zio/zio-s3) is a thin wrapper over the AWS S3 async client for ZIO.

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-s3/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-s3_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-s3_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-s3_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-s3_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-s3_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-s3_2.13) [![ZIO S3](https://img.shields.io/github/stars/zio/zio-s3?style=social)](https://github.com/zio/zio-s3)

## Introduction

ZIO S3 exposes the main operations of the AWS S3 Java client as ZIO effects and ZIO Streams. Each operation needs an `S3` service, which we provide with a layer built from a region and credentials:

```scala
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._
import zio.stream.ZStream

val s3Layer: Layer[S3Exception, S3] =
  live(Region.US_EAST_1, AwsBasicCredentials.create("accessKeyId", "secretAccessKey"))

// list all buckets available
val buckets: ZIO[S3, S3Exception, Unit] =
  listBuckets.unit

// list all objects of all buckets
val allObjects: ZStream[S3, S3Exception, String] =
  ZStream
    .fromIterableZIO(listBuckets)
    .flatMap(bucket => listAllObjects(bucket.name).map(obj => obj.bucketName + "/" + obj.key))
```

All operations live in the package object `zio.s3`, so a single `import zio.s3._` brings them into scope. To run a first program end to end, follow [Getting Started](./getting-started.md). For a guided walk-through, see the [tutorial](./tutorial.md).

## Installation

Add ZIO S3 to your `build.sbt`. The library is published for the JVM only, for Scala 2.12, 2.13 and 3:

```sbt
libraryDependencies ++= Seq(
  // ZIO S3 client for the JVM, built on the AWS SDK v2 async client
  "dev.zio" %% "zio-s3" % "0.4.5"
)
```
