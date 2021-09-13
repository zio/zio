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
