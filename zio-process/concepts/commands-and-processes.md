# Commands and Processes

> ZIO Process separates two things that most process APIs blur together: the description of a program to run, and the program running. The first is a `Command`, and the second is a `Process`. This page explains how the two relate and who is responsible for a running program.

## A Command Is a Description

A `Command` holds a program name, its arguments, an environment, a working directory and the settings for the three standard streams. Creating one starts nothing, and every setting method returns a new `Command` instead of changing the old one. A single `Command` value can therefore be stored, shared and run many times, and each run starts a new operating system process.

For example, the following value describes `ls -l /tmp`:

```scala
import zio._
import zio.process._

val listTmp: Command = Command("ls", "-l", "/tmp")
```

There is no shell between ZIO Process and the program. `Command.apply` takes the program name and each argument as separate strings, and the program receives exactly those strings, so spaces and quotes need no escaping. A shell feature such as a glob or a pipe character does nothing unless a shell is started on purpose, as shown in [Run a Shell Script](../how-to-run-a-shell-script.md).

## Running a Command

`Command#run` starts the program and returns a `Process`, the handle to the running program. The handle gives access to the output streams and to the methods that wait for, inspect and stop the program. Most code never needs it, because `Command` also has shortcuts such as `Command#string`, `Command#lines` and `Command#exitCode` that start the program and read its result in one call.

The following two values do the same work, one with a shortcut and one with the handle:

```scala
val shortcut: ZIO[Any, CommandError, String] = listTmp.string

val withHandle: ZIO[Any, CommandError, String] =
  for {
    process <- listTmp.run
    output  <- process.stdout.string
  } yield output
```

Both values describe the same work. The shortcut is enough when the whole output is wanted at the end, and the handle is needed when the program has to be controlled while it runs.

## Who Owns a Running Process

The shortcuts tie the program to the fiber that runs them. When that fiber is interrupted while `Command#exitCode` or `Command#successfulExitCode` waits, ZIO Process destroys the program. The handle returned by `Command#run` is not tied to a fiber in the same way, so the program keeps running until it exits or the code calls `Process#kill`. [Stop a Running Process](../how-to-stop-a-running-process.md) shows both approaches.

## Pipelines

Joining two commands with `Command#|` produces another `Command`, so a pipeline is also a description and is also run with `Command#run` or one of the shortcuts. The pipeline's reading methods read the rightmost command. [Pipe One Command into Another](../piping.md) shows how each setting applies to a pipeline.
