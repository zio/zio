# Migrate from scala.sys.process

> This guide shows how the common `scala.sys.process` idioms look in ZIO Process. The main difference is that a ZIO Process command is a value that starts nothing until its effect runs, and that failures are values in the error channel. There is no implicit conversion from strings, so each command is created with `Command.apply`.

## Run a Command and Get Its Output

In `scala.sys.process`, `!!` runs a command and returns its output, throwing an exception when the exit code is not zero. The ZIO Process shortcut `Command#string` returns the output as an effect, and never looks at the exit code:

```scala
import scala.sys.process._

val before: String = "ls -l /tmp".!!
```

The same command in ZIO Process:

```scala
import zio._
import zio.process._

val after: ZIO[Any, CommandError, String] = Command("ls", "-l", "/tmp").string
```

To also fail on a non-zero exit code, see [Handle Command Errors](./how-to-handle-command-errors.md).

## Get the Exit Code

In `scala.sys.process`, `!` runs a command and returns its exit code. In ZIO Process, use `Command#exitCode`:

```scala
import zio._
import zio.process._

val code: ZIO[Any, CommandError, ExitCode] = Command("ls", "-l", "/tmp").exitCode
```

## Pipe and Redirect

The `#|`, `#>` and `#>>` operators of `scala.sys.process` become `Command#|`, `Command#>` and `Command#>>`. The `#<` operator that reads a file into standard input becomes `Command#stdin` with `ProcessInput.fromFile`:

```scala
import zio._
import zio.process._

import java.io.File

val pipeline: ZIO[Any, CommandError, ExitCode] =
  (Command("ps", "-ef") | Command("grep", "java") > new File("java.txt")).exitCode

val fromFile: ZIO[Any, CommandError, String] =
  Command("wc", "-l").stdin(ProcessInput.fromFile(new File("input.txt"))).string
```

## Set the Directory and Environment

`Process(command, cwd, extraEnv: _*)` in `scala.sys.process` becomes `Command#workingDirectory` and `Command#env`. See [Set the Environment and Working Directory](./how-to-set-the-environment-and-working-directory.md) for how `Command#env` differs by platform.

## Read Output as a Stream

The lazy line streams of `scala.sys.process` become `Command#linesStream`, a `ZStream` that emits each line as it arrives and stops the process when the stream is interrupted. See [Run a Command and Read Its Output](./how-to-run-a-command-and-read-its-output.md).
