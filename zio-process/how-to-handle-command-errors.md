# Handle Command Errors

> This guide shows how to react to a program that fails to start or exits with a non-zero code. Every failure in the error channel is a `CommandError`, so you match on the case you care about and handle the rest as usual. [Errors and Exit Codes](./concepts/errors-and-exit-codes.md) explains the model, and the [CommandError](./reference/command-error.md) reference lists every case. The examples need these imports:

```scala
import zio._
import zio.process._
```

## Branch on the Exit Code

`Command#exitCode` returns the code in the success channel, whether it is zero or not, so a program can branch on it without catching an error:

```scala
val code: ZIO[Any, CommandError, ExitCode] = Command("ls", "--non-existent-flag").exitCode
```

## Fail on a Non-Zero Exit Code

`Command#successfulExitCode` treats any non-zero code as a failure with `CommandError.NonZeroErrorCode`, which carries the code:

```scala
val checked: ZIO[Any, CommandError, ExitCode] =
  Command("ls", "--non-existent-flag").successfulExitCode.catchSome {
    case CommandError.NonZeroErrorCode(code) => ZIO.succeed(code)
  }
```

## Check the Exit Code After Reading the Output

The output shortcuts, such as `Command#string`, never look at the exit code. To read the output and fail on a non-zero code, run the command with `Command#run` and call `Process#successfulExitCode` after reading:

```scala
val checkedOutput: ZIO[Any, CommandError, String] =
  for {
    process <- Command("ls", "-l").run
    output  <- process.stdout.string
    _       <- process.successfulExitCode
  } yield output
```

## Run a Fallback When a Program Is Missing

To run another program when the first one is not installed, match `CommandError.ProgramNotFound`:

```scala
val output: ZIO[Any, CommandError, String] =
  Command("some-program-that-may-not-exist").string.catchSome {
    case CommandError.ProgramNotFound(_) => Command("fallback-program").string
  }
```

`CommandError.WorkingDirectoryMissing` is the matching case for a working directory that does not exist. `CommandError.ProgramNotFound` and `CommandError.PermissionDenied` are not reliably raised on Scala Native, so check the exit code or the output as well on that platform.

## Handle Every Case

To handle each case differently, match on the sealed type. Because `CommandError.NotStarted` is the base class of three cases, listing those three covers the programs that never started:

```scala
def describe(error: CommandError): String =
  error match {
    case CommandError.ProgramNotFound(_)            => "The program is not installed."
    case CommandError.PermissionDenied(_)           => "The program cannot be run by this user."
    case CommandError.WorkingDirectoryMissing(dir)  => s"The directory $dir does not exist."
    case CommandError.NonZeroErrorCode(code)        => s"The program failed with code ${code.code}."
    case CommandError.IOError(cause)                => s"Reading or writing a stream failed: ${cause.getMessage}"
    case other                                      => s"Unexpected failure: ${other.getMessage}"
  }
```
