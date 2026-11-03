# ZIO Spark

> [ZIO Spark](https://univalence.github.io/zio-spark/) is a functional wrapper around Spark that makes it work with ZIO, improves error management, and increases performance.

## Introduction

ZIO Spark rewrites the existing Spark API using better functional programming principles. Each Spark type has a corresponding type in `zio.spark`, such as `Dataset` and `SparkSession`, with a different API:

- **Effectful Actions** — Actions return ZIO effects. For example, `count` returns a `Task[Long]` instead of a `Long`.
- **Explicit Errors** — Operations that can fail return a `TryAnalysis`, which lets us handle, for example, a column that doesn't exist.
- **No Implicit SparkSession** — The session is provided as a ZIO layer, so there is no need to pass an implicit `SparkSession` around.
- **ZIO Features** — Spark jobs can use features like retrying and running in parallel.
- **Existing Code** — Existing Spark code can be used without modifications, and lifted into ZIO Spark with `zio.spark.sql.fromSpark`.

The library supports Scala 2.11, 2.12, 2.13, and 3, and it works with [ZIO Direct](https://zio.dev/zio-direct). The authors describe it as not yet as battle-tested as they would like it to be.

## Installation

In order to use this library, we need to add the following lines to our `build.sbt` file. Spark is provided, so we add the Spark version we use, as we usually would:

```scala
libraryDependencies ++= Seq(
  "io.univalence"    %% "zio-spark"  % "0.13.0",
  "org.apache.spark" %% "spark-core" % "3.3.1" % Provided,
  "org.apache.spark" %% "spark-sql"  % "3.3.1" % Provided
)
```

To create an example application with all the dependencies, we can use the Giter8 template:

```bash
sbt new univalence/zio-spark.g8
```

## Example

In the following example, we read a text file, keep the lines that contain `zio`, and print them. We use ZIO Direct to write the program in a direct style:

```scala
import zio.*
import zio.direct.*
import zio.spark.sql.*

// import for syntax + spark encoders
import zio.spark.sql.implicits.*
import scala3encoders.given

// throw AnalysisException directly
import zio.spark.sql.TryAnalysis.syntax.throwAnalysisException

object Main extends ZIOAppDefault {
  val sparkSession = SparkSession.builder.master("local").asLayer

  override def run = {
    defer {
      val readBuild: RIO[SparkSession, DataFrame] = SparkSession.read.text("./build.sbt")
      val text: Dataset[String] = readBuild.run.as[String]

      text.filter(_.contains("zio")).show(truncate = false).run

      Console.printLine("what a time to be alive!").run
    }.provideLayer(sparkSession)
  }
}
```

## Using Existing Spark Code

Existing Spark code can be lifted into ZIO Spark. Then we can keep using it, or switch to the new API with `.zioSpark`:

```scala
def existingCode(implicit ss: org.apache.spark.sql.SparkSession): org.apache.spark.sql.Dataset[String] = {
  import ss.implicits._
  ss.read.parquet("toto.parquet").as[String]
}

val out =
  zio.spark.sql.fromSpark(existingCode).flatMap(_.zioSpark.count)
```

For the full documentation, see the [ZIO Spark documentation](https://univalence.github.io/zio-spark/).
