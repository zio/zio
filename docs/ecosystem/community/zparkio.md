---
id: zparkio
title: "ZparkIO"
---

[ZParkIO](https://github.com/leobenkel/ZparkIO) is a boilerplate framework to use _Spark_ and _ZIO_ together.

## Introduction

_ZparkIO_ enables us to:
- Wrap asynchronous and synchronous operations smoothly. So everything is wrapped in ZIO.
- Have ZIO features in our spark jobs, like forking and joining fibers, parallelizing tasks, retrying, and timing-out.
- Make our spark job much easier to debug

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "com.leobenkel" %% "zparkio" % "[SPARK_VERSION]_[VERSION]"
```

## Example

Using _ZparkIO_ we can write jobs like the following example:

```scala
import com.leobenkel.zparkio.Services.SparkModule
import com.leobenkel.zparkio.Services.SparkModule.SparkModule
import com.leobenkel.zparkio.ZparkioApplicationTimeoutException
import org.apache.spark.sql.DataFrame
import zio.clock.Clock
import zio.duration.durationInt
import zio.{Schedule, Task, ZIO}

def readParquetFile[A](parquetPath: String): ZIO[Clock with SparkModule, Throwable, DataFrame] =
  for {
    spark <- SparkModule()
    dataset <- Task(spark.read.parquet(parquetPath))
      .retry(
        Schedule.recurs(3) && Schedule.exponential(2.seconds)
      )
      .timeoutFail(ZparkioApplicationTimeoutException())(5.minutes)
  } yield dataset
```

## Resources

- [Accelerating Spark with ZIO](https://www.youtube.com/watch?v=bWgVGzb5-H8) by Leo Benkel (December 2020) — Apache Spark is a powerful tool for distributed analytics but isn’t built to offer rich primitives for local parallelism. Yet, using local parallelism, it is possible to dramatically improve the performance of ML projects—for example, by fetching data sources in parallel. Historically, Scala projects have used Scala's Future for local parallelism, but increasingly, many projects have begun adopting ZIO as a better Future. Although powerful, ZIO is not easy to set up with Spark. In this talk, Leo will introduce an open-source library that handles all boilerplate so you can easily implement Spark and ZIO in your ML projects, and will then present detailed benchmarks showing how much additional performance can be obtained through local parallelism. Learn how to use ZIO to make Spark better and faster!
