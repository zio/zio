# Tutorial: Build a Disk Usage Report Tool

> Welcome! In this tutorial, we build a small command-line tool that reports the biggest files and folders under a directory. Along the way we run a command, pipe three commands together, turn the output into Scala values, handle a failure, and write the result to a file. By the end, you will have used the main parts of ZIO Process in one program.

The tool relies on the Unix programs `du`, `sort` and `head`, so it runs on Linux and macOS. We assume that you know basic ZIO, such as `for` comprehensions and `ZIOAppDefault`, and that ZIO Process is on your classpath as described in [Installation](./index.md#installation).

## Step 1: Run a Command

We start with the simplest possible use: run `du` for a directory and read its output as lines. `du -ak` prints the size in kilobytes of every file and folder, one per line, as the size, a tab, and the path:

```scala
import zio._
import zio.process._

import java.io.File

def allEntries(dir: File): ZIO[Any, CommandError, Chunk[String]] =
  Command("du", "-ak", ".").workingDirectory(dir).lines
```

`Command("du", "-ak", ".")` only describes the program. `Command#workingDirectory` returns a new `Command` that starts in `dir`, and `Command#lines` runs it and collects standard output as a `Chunk[String]`. Nothing runs until the effect itself runs.

## Step 2: Pipe Commands Together

The output has one line for every file, which is far too much. In a shell, we write `du -ak . | sort -rn | head -n 5`. ZIO Process has the same idea with the `Command#|` operator, which connects the output of one command to the input of the next:

```scala
def biggestLines(dir: File, count: Int): ZIO[Any, CommandError, Chunk[String]] =
  (Command("du", "-ak", ".").workingDirectory(dir) |
    Command("sort", "-rn") |
    Command("head", "-n", count.toString)).lines
```

The pipeline is also a `Command`, so `Command#lines` reads the output of its last command. The pipe works without a shell, so each command is a program with its own arguments. `Command#workingDirectory` on a pipeline applies to every command in it.

## Step 3: Turn Lines into Values

Lines of text are awkward to work with, so we parse each one into an `Entry` with a size and a path. A line that does not match the format is skipped:

```scala
final class Entry(val sizeKb: Long, val path: String)

def parse(line: String): Option[Entry] =
  line.split("\t", 2) match {
    case Array(size, path) => scala.util.Try(size.toLong).toOption.map(new Entry(_, path))
    case _                 => None
  }

def biggest(dir: File, count: Int): ZIO[Any, CommandError, Chunk[Entry]] =
  biggestLines(dir, count).map(_.map(parse).collect { case Some(entry) => entry })
```

The result of `biggest` is an ordinary ZIO effect that returns typed values, so it composes with the rest of a program.

## Step 4: Handle a Failure

What happens when the directory does not exist? `Command#workingDirectory` makes the effect fail with `CommandError.WorkingDirectoryMissing` before any program starts. Every failure of ZIO Process is a `CommandError`, so we can match on the case that we want to handle and let the rest through:

```scala
def biggestOrEmpty(dir: File, count: Int): ZIO[Any, CommandError, Chunk[Entry]] =
  biggest(dir, count).catchSome { case CommandError.WorkingDirectoryMissing(_) => ZIO.succeed(Chunk.empty) }
```

Returning an empty chunk hides the problem, so a real tool prints a message instead. We keep the example short. [Handle Command Errors](./how-to-handle-command-errors.md) covers the other cases.

## Step 5: Write a Report to a File

To save the report, we send a string to the standard input of `cat` and redirect its output to a file. `Command#<<` feeds the string to standard input, and `Command#>` writes standard output to the file:

```scala
def report(entries: Chunk[Entry]): String =
  entries.map(entry => f"${entry.sizeKb}%10d KB  ${entry.path}").mkString("", "\n", "\n")

def save(entries: Chunk[Entry], file: File): ZIO[Any, CommandError, ExitCode] =
  ((Command("cat") << report(entries)) > file).exitCode
```

`Command#exitCode` runs the command and returns its exit code. Because the output goes to a file, there is nothing for the program to read.

## Putting It Together

We now have all the pieces. The complete tool takes a directory from the command line, defaulting to the current one, prints the report, and saves it to `report.txt`:

```scala
object DiskUsage extends ZIOAppDefault {
  override def run: ZIO[ZIOAppArgs, Any, Unit] =
    for {
      args    <- getArgs
      dir      = new File(args.headOption.getOrElse("."))
      entries <- biggest(dir, 10)
      _       <- Console.print(report(entries))
      _       <- save(entries, new File("report.txt"))
    } yield ()
}
```

That's it! We built a tool from three commands and a few lines of parsing.

## Next Steps

To go further, read how to [Run a Command and Read Its Output](./how-to-run-a-command-and-read-its-output.md) with streams for very large output, or how to [Stop a Running Process](./how-to-stop-a-running-process.md) that takes too long.
