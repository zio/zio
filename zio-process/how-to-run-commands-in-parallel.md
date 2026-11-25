# Run Commands in Parallel

> This guide shows how to run several commands at the same time and collect their results. A `Command` is an ordinary ZIO effect when it is read with a method such as `Command#string`, so the parallel operators of ZIO apply to it directly.

## Run a Fixed Set of Commands

To run two commands together and keep both results, use `ZIO#zipPar`:

```scala
import zio._
import zio.process._

val both: ZIO[Any, CommandError, (String, String)] =
  Command("echo", "first").string.zipPar(Command("echo", "second").string)
```

If one command fails, the other is interrupted. Interrupting `Command#string` closes its output stream but does not destroy the process, so a long-running program can keep running. Use `Command#exitCode`, or hold the `Process` handle and call `Process#kill`, when the program must stop.

## Run a List of Commands

To run one command per item and collect the results in order, use `ZIO.foreachPar`:

```scala
import zio._
import zio.process._

val hosts: List[String] = List("localhost", "127.0.0.1")

val results: ZIO[Any, CommandError, List[String]] =
  ZIO.foreachPar(hosts)(host => Command("echo", host).string)
```

## Limit the Number of Running Processes

Starting many programs at once can exhaust the machine. To cap how many run together, wrap the effect in `ZIO.withParallelism`:

```scala
import zio._
import zio.process._

val limited: ZIO[Any, CommandError, List[String]] =
  ZIO.withParallelism(4) {
    ZIO.foreachPar((1 to 20).toList)(n => Command("echo", n.toString).string)
  }
```

At most four of the twenty commands run at any moment.
