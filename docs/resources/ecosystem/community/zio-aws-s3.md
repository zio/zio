---
id: zio-aws-s3
title: "ZIO AWS S3"
---

[ZIO AWS S3](https://github.com/zio-mesh/zio-aws-s3) is a ZIO integration with AWS S3 SDK.

## Introduction

This project aims to ease ZIO integration with AWS S3, providing a clean, simple and efficient API.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "io.github.neurodyne" %% "zio-aws-s3" % "0.4.13"
```

## Example

```scala
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import zio.{ExitCode, URIO, _}
import zio_aws_s3.AwsApp.AwsLink
import zio_aws_s3.{AwsAgent, AwsApp}

import scala.jdk.CollectionConverters._

object ZIOAWSS3Example extends zio.App {
  val BUCKET = "<bucket name>"

  val awsEnv: ZLayer[S3AsyncClient, Throwable, AwsLink] =
    AwsApp.ExtLayer.live >>> AwsApp.AwsLink.live

  val app: ZIO[Any, Throwable, Unit] = for {
    s3 <- AwsAgent.createClient(Region.US_WEST_2, "<endpoint>")
    response <- AwsApp.listBuckets().provide(awsEnv).provideEnvironment(s3)
    buckets <- Task(response.buckets.asScala.toList.map(_.name))
    _ = buckets.foreach(println)
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    app.exitCode
}
```
