# Redirect Output to a File

> This guide shows how to send standard output or standard error to a file, to the terminal, or to both streams at once. The destinations are `ProcessOutput` values, set with `Command#stdout` and `Command#stderr`. The examples need these imports:

```scala
import zio._
import zio.process._

import java.io.File
```

## Write Standard Output to a File

`Command#>` overwrites the file, and `Command#>>` appends to it:

```scala
val dump: ZIO[Any, CommandError, ExitCode] =
  (Command("ls", "-l") > new File("listing.txt")).exitCode

val append: ZIO[Any, CommandError, ExitCode] =
  (Command("ls", "-l") >> new File("listing.txt")).exitCode
```

These are shorthand for `Command#stdout` with `ProcessOutput.FileRedirect` or `ProcessOutput.FileAppendRedirect`. Write the full form to redirect another stream:

```scala
val explicit: ZIO[Any, CommandError, ExitCode] =
  Command("ls", "-l").stdout(ProcessOutput.FileRedirect(new File("listing.txt"))).exitCode
```

With a file redirect, the output is not captured, so `Command#string` and the other reading methods have nothing to read. Read the file after the command finishes instead.

## Write Standard Error to a File

`Command#stderr` accepts the same `ProcessOutput` values:

```scala
val errorsToFile: ZIO[Any, CommandError, ExitCode] =
  Command("ls", "--non-existent-flag").stderr(ProcessOutput.FileRedirect(new File("errors.txt"))).exitCode
```

## Show Output on the Terminal

Use `ProcessOutput.Inherit` to print a stream to the current terminal, which is useful for diagnostics that the user should see. To connect all three streams to the terminal, so that the command behaves as if typed in a shell, use `Command#inheritIO`:

```scala
val repl: ZIO[Any, CommandError, ExitCode] = Command("scala").inheritIO.exitCode
```

## Redirect a Pipeline

On a pipeline, `Command#stdout` and `Command#stderr` apply to the rightmost command. See [Pipe One Command into Another](./piping.md).
