# Run a Command and Read Its Output

> This guide shows how to run an external program and read what it prints, as lines, as one string, or as raw bytes. Each shortcut runs the command when the effect runs, and decodes standard output as UTF-8 unless a `Charset` is given. For the ideas behind them, see [Commands and Processes](./concepts/commands-and-processes.md).

## Prerequisites

The examples need these imports and a command to run:

```scala
import zio._
import zio.process._
import zio.stream.ZStream

val listFiles = Command("ls", "-1", ".")
```

`Command.apply` takes the program name first, then each argument as a separate string. No shell is involved, so spaces and quotes in an argument need no escaping.

## Collect the Output as Lines

To read every line into memory, use `Command#lines`:

```scala
val lines: ZIO[Any, CommandError, Chunk[String]] = listFiles.lines
```

## Stream the Output as Lines

For long-running commands or large output, use `Command#linesStream`, which emits each line as it arrives and keeps memory use bounded:

```scala
val streamed: ZStream[Any, CommandError, String] = listFiles.linesStream
```

## Read the Output as One String

To get all of standard output as one string, use `Command#string`. To read another encoding, pass a `Charset`:

```scala
import java.nio.charset.StandardCharsets

val text: ZIO[Any, CommandError, String]  = listFiles.string
val utf16: ZIO[Any, CommandError, String] = listFiles.string(StandardCharsets.UTF_16)
```

`Command#string` needs JDK 9 or later on the JVM.

## Read the Output as Bytes

To work with the output without decoding it, use `Command#stream`:

```scala
val bytes: ZStream[Any, CommandError, Byte] = listFiles.stream
```

## Get the Exit Code

`Command#exitCode` runs the command and returns its exit code, whether or not it is zero:

```scala
val code: ZIO[Any, CommandError, ExitCode] = listFiles.exitCode
```

The output shortcuts never look at the exit code. To fail on a non-zero code, see [Handle Command Errors](./how-to-handle-command-errors.md).

## Use the Result in a Program

A command is an ordinary ZIO effect, so it composes with the rest of a program. Because `listFiles` is only a description, the same value can run many times, and each run starts a new process:

```scala
object ListEntries extends ZIOAppDefault {
  override def run: ZIO[Any, Any, Unit] =
    for {
      entries <- listFiles.lines
      _       <- Console.printLine(s"Found ${entries.length} entries")
    } yield ()
}
```

The shortcuts read only standard output. To read standard error too, see [Read Standard Output and Error Separately](./how-to-read-standard-output-and-error-separately.md).
