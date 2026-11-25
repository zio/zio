# How to choose the exit code for each error

> Scripts and CI jobs read a program's exit code to decide what happened. A program built with `ZIOCliDefault` uses only two codes, `0` and `1`. This guide shows how to give each kind of failure its own exit code, such as `2` for invalid input and `3` for a missing file.

## What ZIOCliDefault Does

`ZIOCliDefault` passes the program arguments to `CliApp#run`. The program exits with `0` when `CliApp#run` succeeds, including after `--help`, and with `1` for every `CliError`. [Validation and Errors](validation-and-errors.md#exit-codes) lists each case.

Because every failure ends with code `1`, a caller cannot tell a typing mistake from a failed command. To change that, we run the `CliApp` ourselves from a `ZIOAppDefault` and map each error to an exit code.

## Define the Errors of Your Command

Start with an error type for the failures your command can report. Here, a `show` command fails when the file does not exist or cannot be read:

```scala
import zio._
import zio.cli._
import zio.cli.HelpDoc.Span.text
import java.nio.file.{Files, Path}

sealed trait ShowError
object ShowError {
  case class NotFound(path: Path)   extends ShowError
  case class Unreadable(path: Path) extends ShowError
}

val show: Command[Path] = Command("show", Args.file("file"))

val showApp: CliApp[Any, ShowError, Unit] =
  CliApp.make(
    name = "show",
    version = "1.0.0",
    summary = text("Prints the size of a file"),
    command = show
  ) { path =>
    for {
      _    <- ZIO.fail(ShowError.NotFound(path)).unless(Files.exists(path))
      size <- ZIO.attempt(Files.size(path)).orElseFail(ShowError.Unreadable(path))
      _    <- Console.printLine(s"$path: $size bytes").orDie
    } yield ()
  }
```

## Map Each Error to an Exit Code

Next, write a function from `CliError[ShowError]` to `ExitCode`. Matching on `CliError.Execution` gives access to your own error. zio-cli prints nothing for your errors, so print a message for them here:

```scala
def exitCodeFor(error: CliError[ShowError]): UIO[ExitCode] =
  error match {
    case CliError.Parsing(_) =>
      ZIO.succeed(ExitCode(2))
    case CliError.Execution(ShowError.NotFound(path)) =>
      Console.printLineError(s"No such file: $path").orDie.as(ExitCode(3))
    case CliError.Execution(ShowError.Unreadable(path)) =>
      Console.printLineError(s"Cannot read: $path").orDie.as(ExitCode(4))
    case CliError.IO(_) | CliError.BuiltIn(_) =>
      ZIO.succeed(ExitCode(70))
  }
```

`CliError.Parsing` needs no message, because `CliApp#run` has already printed the validation error.

## Run the App From ZIOAppDefault

Finally, extend `ZIOAppDefault` instead of `ZIOCliDefault`. Read the arguments with `ZIOApp#getArgs`, run the app, and on failure call `ZIOApp#exit` with the chosen code:

```scala
object ShowMain extends ZIOAppDefault {
  def run =
    for {
      args <- getArgs
      _    <- showApp.run(args.toList).catchAll(error => exitCodeFor(error).flatMap(exit(_)))
    } yield ()
}
```

`ZIOApp#exit` stops the program at once with the given code. Returning an `ExitCode` value from `ZIOAppDefault#run` is not enough: the program exits with `0` whenever `ZIOAppDefault#run` succeeds, whatever value it returns.

With this program, the exit codes are:

| Command line       | Exit code | Reason                               |
| ------------------ | --------- | ------------------------------------ |
| `show build.sbt`   | `0`       | The file exists and its size prints. |
| `show --help`      | `0`       | Help is a successful run.            |
| `show`             | `2`       | The `<file>` argument is missing.    |
| `show missing.txt` | `3`       | `ShowError.NotFound`                 |

## Test the Mapping

`exitCodeFor` is an ordinary function, so tests can call it with each `CliError` case without starting the program:

```scala
Unsafe.unsafe { implicit unsafe =>
  Runtime.default.unsafe.run(exitCodeFor(CliError.Parsing(ValidationError(ValidationErrorType.InvalidArgument, HelpDoc.empty)))).getOrThrowFiberFailure()
}
// res1: ExitCode = ExitCode(code = 2)
```

## Related Pages

- [Validation and Errors](validation-and-errors.md) describes every `CliError` case and the messages users see.
- [CliApp and Running a CLI](cli-app.md) describes `CliApp#run` and `ZIOCliDefault`.
