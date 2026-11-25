# Introduction to ZIO Process

> [ZIO Process](https://zio.dev/zio-process) is a simple ZIO library for interacting with external processes and command-line programs.

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-process/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-process_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-process_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-process_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-process_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-process_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-process_2.13) [![ZIO Process](https://img.shields.io/github/stars/zio/zio-process?style=social)](https://github.com/zio/zio-process)

## Introduction

ZIO Process is backed by ZIO Streams, enabling you to work with processes that output gigabytes of data without worrying about exceeding memory constraints.

ZIO Process provides a principled way to call out to external programs from within a ZIO application while leveraging ZIO's capabilities like interruptions and offloading blocking operations to a separate thread pool. We do not need to worry about avoiding these common pitfalls, as we do when using Java's `ProcessBuilder` or the `scala.sys.process` API, because the library already takes care of them.

Key features of ZIO Process:
- **Deep ZIO Integration** — Leverages ZIO to handle interruption and offload blocking operations.
- **ZIO Streams** — Command output is available as streams of bytes or lines, so you can work with processes that output gigabytes of data without exceeding memory constraints.
- **Descriptive Errors** — Failures are reported as `CommandError` cases, such as a missing program or a non-zero exit code.
- **Piping** — A simple DSL pipes the output of one command into the input of another.
- **Interactive Processes** — Send requests to a running process and read its responses as they arrive.

## Installation

ZIO Process is published for Scala 2.12, 2.13 and 3. To use it on the JVM, add the following to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  // ZIO Process: runs external programs as ZIO effects (JVM)
  "dev.zio" %% "zio-process" % "0.8.2"
)
```

For a cross-built project that also targets Scala.js, use `%%%` so that sbt picks the artifact for each platform:

```scala
libraryDependencies ++= Seq(
  // ZIO Process: runs external programs as ZIO effects (JVM and Scala.js)
  "dev.zio" %%% "zio-process" % "0.8.2"
)
```

The code supports Scala Native, but no Scala Native artifact is published yet. [Platform Support](./platform-support.md) explains the differences between the platforms.

## Example

Here is a simple example of using ZIO Process:

```scala
import zio._
import zio.process.Command

import java.io.File

object ZIOProcessExample extends ZIOAppDefault {
  val myApp = for {
    fiber <- Command("dmesg", "--follow").linesStream
      .foreach(Console.printLine(_))
      .fork
    cpuModel <- (Command("cat", "/proc/cpuinfo") |
      Command("grep", "model name") |
      Command("head", "-n", "1") |
      Command("cut", "-d", ":", "-f", "2")).string
    _ <- Console.printLine(s"CPU Model: $cpuModel")
    _ <- (Command("pg_dump", "my_database") > new File("dump.sql")).exitCode
    _ <- fiber.join
  } yield ()

  override def run = myApp
}
```

This example runs on Linux, because it uses `dmesg` and `/proc/cpuinfo`.

## Where to Go Next

[Getting Started](./getting-started.md) runs a first program. The [Concepts](./concepts/index.md) pages explain how commands, processes, streams and errors fit together, the [Reference](./reference/command.md) pages list every public type, and the [Tutorial](./tutorial-disk-usage-report.md) builds a small command-line tool step by step. The how-to guides, starting with [Run a Command and Read Its Output](./how-to-run-a-command-and-read-its-output.md), solve one task each.
