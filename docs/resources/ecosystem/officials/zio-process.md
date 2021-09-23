---
id: zio-process
title: "ZIO Process"
---

[ZIO Process](https://github.com/zio/zio-process) is a simple ZIO library for interacting with external processes and command-line programs.

## Introduction

ZIO Process provides a principled way to call out to external programs from within a ZIO application while leveraging ZIO's capabilities like interruptions and offloading blocking operations to a separate thread pool. We don't need to worry about avoiding these common pitfalls as we would if we were to use Java's `ProcessBuilder` or the `scala.sys.process` API since it is already taken care of for you.

Key features of the ZIO Process:
- **Deep ZIO Integration** — Leverages ZIO to handle interruption and offload blocking operations.
- **ZIO Streams** — ZIO Process is backed by ZIO Streams, which enables us to obtain the command output as streams of bytes or lines. So we can work with processes that output gigabytes of data without worrying about exceeding memory constraints.
- **Descriptive Errors** — In case of command failure, it has a descriptive category of errors.
- **Piping** — It has a simple DSL for piping the output of one command as the input of another.
- **Blocking Operations** —

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-process" % "0.5.0" 
```

## Example

Here is a simple example of using ZIO Process:

```scala
import zio.console.putStrLn
import zio.process.Command
import zio.{ExitCode, URIO}

import java.io.File

object ZIOProcessExample extends zio.App {

  val myApp = for {
    fiber <- Command("dmesg", "--follow").linesStream
      .foreach(putStrLn(_))
      .fork
    cpuModel <- (Command("cat", "/proc/cpuinfo") |
      Command("grep", "model name") |
      Command("head", "-n", "1") |
      Command("cut", "-d", ":", "-f", "2")).string
    _ <- putStrLn(s"CPU Model: $cpuModel")
    _ <- (Command("pg_dump", "my_database") > new File("dump.sql")).exitCode
    _ <- fiber.join
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.exitCode
}
```
