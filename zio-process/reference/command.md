# Command

> `Command` describes a program, its arguments and how it runs. Creating or configuring a `Command` starts nothing. The type has two shapes, `Command.Standard` for a single program and `Command.Piped` for a pipeline, and most code never needs to tell them apart. For the ideas behind it, see [Commands and Processes](../concepts/commands-and-processes.md).

## Construction

To create a command, call `Command.apply`, which takes the program name and any number of arguments and returns a `Command.Standard`:

```scala
import zio.process._

val ls: Command = Command("ls", "-l", "/tmp")
```

A `Command.Standard` is a case class with the fields `command`, `env`, `workingDirectory`, `stdin`, `stdout`, `stderr` and `redirectErrorStream`. A `Command.Piped(left, right)` joins two commands. The defaults of `Command.apply` are an empty environment, no working directory, inherited standard input, captured standard output and standard error, and no merging of the error stream.

## Settings

Each setting method returns a new `Command`. On a pipeline, each setting reaches only the commands that need it, as described in [Pipe One Command into Another](../piping.md).

| Member                                   | Description                                                                    |
| ---------------------------------------- | ------------------------------------------------------------------------------ |
| `Command#env(env: Map[String, String])`  | Sets the environment variables. A second call replaces the earlier map.        |
| `Command#workingDirectory(workingDirectory: File)` | Sets the directory the program starts in.                                      |
| `Command#redirectErrorStream(redirectErrorStream: Boolean)` | Merges standard error into standard output. Ignored on Scala.js.               |
| `Command#stdin(stdin: ProcessInput)` | Sets where standard input comes from.                                          |
| `Command#stdout(stdout: ProcessOutput)` | Sets where standard output goes.                                               |
| `Command#stderr(stderr: ProcessOutput)` | Sets where standard error goes.                                                |
| `Command#inheritIO`                      | Sends all three standard streams to the current program's terminal.            |
| `Command#>(redirectTo: File)` | Redirects standard output to a file, overwriting it.                           |
| `Command#>>(redirectTo: File)` | Redirects standard output to a file, appending to it.                          |
| `Command#<<(input: String)`              | Sends a UTF-8 string to standard input.                                        |
| `Command#\|(into: Command)`              | Pipes the output of this command into the input of the next command.           |
| `Command#pipe(into: Command)`            | A named alias for `Command#\|`.                                                |

`ProcessInput` and `ProcessOutput` are described in [ProcessInput, ProcessOutput and ProcessStream](./process-input-output.md). On Scala.js, `File` is a `String` path, and the environment behaves differently, as listed in [Platform Support](../platform-support.md).

## Running

These members start the program. Each one runs the command again every time the effect runs.

| Member                                | Returns                                 | Description                                                            |
| ------------------------------------- | --------------------------------------- | ---------------------------------------------------------------------- |
| `Command#run`                         | `ZIO[Any, CommandError, Process]`       | Starts the program and returns its handle.                             |
| `Command#exitCode`                    | `ZIO[Any, CommandError, ExitCode]`      | Runs the program and returns its exit code, zero or not.               |
| `Command#successfulExitCode`          | `ZIO[Any, CommandError, ExitCode]`      | Fails with `CommandError.NonZeroErrorCode` unless the code is zero.    |
| `Command#lines`                       | `ZIO[Any, CommandError, Chunk[String]]` | Collects standard output as lines, decoded as UTF-8.                   |
| `Command#lines(charset)`              | `ZIO[Any, CommandError, Chunk[String]]` | Collects standard output as lines in the given encoding.               |
| `Command#linesStream`                  | `ZStream[Any, CommandError, String]`    | Emits standard output as lines, decoded as UTF-8.                      |
| `Command#string`                      | `ZIO[Any, CommandError, String]`        | Returns standard output as one UTF-8 string. Needs JDK 9 or later.     |
| `Command#string(charset)`             | `ZIO[Any, CommandError, String]`        | Returns standard output as one string in the given encoding.           |
| `Command#stream`                      | `ZStream[Any, CommandError, Byte]`      | Emits standard output as bytes.                                        |

The reading members read only standard output and never look at the exit code, as explained in [Errors and Exit Codes](../concepts/errors-and-exit-codes.md).

## Inspecting a Pipeline

`Command#flatten` returns the standard commands of a pipeline as a `NonEmptyChunk[Command.Standard]`, from left to right. For a single command, it returns a chunk with that one command:

```scala
import zio.process._

val pipeline: Command = Command("ps", "-ef") | Command("grep", "java") | Command("wc", "-l")

val programs: List[String] = pipeline.flatten.map(_.command.head).toList
```
