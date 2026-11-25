# CommandError

> Every failure in the error channel of a command is a `CommandError`. It is a sealed class that extends `Exception`, so it can also be logged or rethrown like any exception. [Errors and Exit Codes](../concepts/errors-and-exit-codes.md) explains when each failure happens.

## Cases

Match on a case to handle a specific failure.

| Case                                          | Raised when                                                                                  |
| --------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `CommandError.NotStarted`                     | The base class of the three cases below. Match on those instead.                             |
| `CommandError.ProgramNotFound(cause)`         | The program does not exist. Not reliably raised on Scala Native.                             |
| `CommandError.PermissionDenied(cause)`        | The operating system refuses to run the program. Not reliable on Scala Native.               |
| `CommandError.WorkingDirectoryMissing(workingDirectory)` | The directory set with `Command#workingDirectory` does not exist. Raised on every platform. |
| `CommandError.NonZeroErrorCode(exitCode)`     | The program exits with a non-zero code and the code calls `successfulExitCode`.            |
| `CommandError.IOError(cause)`                 | Reading or writing a stream failed. `cause` is a `java.io.IOException`.                      |
| `CommandError.Error(cause)`                   | Any other failure. Treat it as a catch-all.                                                  |

`cause` is the underlying exception. On Scala.js, the `cause` of `ProgramNotFound` and `PermissionDenied` is a `JavaScriptException`, and `WorkingDirectoryMissing` holds the directory as a `String`.

## Matching a Case

To run a fallback when the first program is missing:

```scala
import zio._
import zio.process._

val output: ZIO[Any, CommandError, String] =
  Command("some-program-that-may-not-exist").string.catchSome {
    case CommandError.ProgramNotFound(_) => Command("fallback-program").string
  }
```

[Handle Command Errors](../how-to-handle-command-errors.md) shows more recipes.
