# Getting Started

> This page runs a first program with ZIO Process. To add the library to a project, copy the dependency block from the [Installation](./index.md#installation) section of the introduction.

## A First Program

To run an external program from a ZIO application, create a `Command`, run it, and print the result. The following app runs `echo` and prints its output:

```scala
import zio._
import zio.process._

object HelloProcess extends ZIOAppDefault {
  override def run: ZIO[Any, Any, Unit] =
    for {
      greeting <- Command("echo", "Hello from ZIO Process").string
      _        <- Console.print(greeting)
    } yield ()
}
```

`Command#string` runs the program and returns everything it wrote to standard output. Because the result is an ordinary ZIO effect, it can be combined with other effects, retried, or interrupted like any other.

## Where to Go Next

To understand what a `Command` is and when the program starts, read [Commands and Processes](./concepts/commands-and-processes.md). To learn by building something, follow the [Tutorial](./tutorial-disk-usage-report.md). To solve a specific task, start with [Run a Command and Read Its Output](./how-to-run-a-command-and-read-its-output.md). Every public type is listed in the [Command](./reference/command.md) reference.
