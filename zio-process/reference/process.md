# Process

> `Process` is the handle that `Command#run` returns once the program has started. It gives access to the program's output streams and controls the program. The idea behind it, and who owns a running program, is explained in [Commands and Processes](../concepts/commands-and-processes.md).

## Streams

The output streams are values on the handle. Both are [ProcessStream](./process-input-output.md#processstream) values.

| Member                 | Returns         | Description                                       |
| ---------------------- | --------------- | ------------------------------------------------- |
| `Process#stdout`       | `ProcessStream` | Standard output of the program.                   |
| `Process#stderr`       | `ProcessStream` | Standard error of the program.                    |
| `Process#stdoutJava`   | `InputStream`   | Standard output as a Java `InputStream`.          |

## Waiting and Inspecting

| Member                        | Returns                            | Description                                                                         |
| ----------------------------- | ---------------------------------- | ----------------------------------------------------------------------------------- |
| `Process#exitCode`            | `ZIO[Any, CommandError, ExitCode]` | Waits for the program and returns its exit code, zero or not.                       |
| `Process#successfulExitCode`  | `ZIO[Any, CommandError, ExitCode]` | Waits for the program and fails with `CommandError.NonZeroErrorCode` unless zero.   |
| `Process#isAlive`             | `UIO[Boolean]`                     | Whether the program is still running. Always `true` on Scala.js.                    |
| `Process#pid`                 | `ZIO[Any, CommandError, Long]`     | The operating system's process ID. Needs JDK 9 or later on the JVM.                 |

Interrupting a fiber that waits in `Process#exitCode` or `Process#successfulExitCode` destroys the program.

## Stopping

Each of these waits until the program has exited. [Stop a Running Process](../how-to-stop-a-running-process.md) shows when to use which.

| Member                       | Description                                                                          |
| ---------------------------- | ------------------------------------------------------------------------------------ |
| `Process#kill`               | Sends the terminate signal (SIGTERM on Unix).                                        |
| `Process#killForcibly`       | Forcefully stops the program (SIGKILL on Unix). On Scala.js, it sends the same signal as `Process#kill`. |
| `Process#killTree`           | Stops the program and its descendants. JVM only, JDK 9 or later.                     |
| `Process#killTreeForcibly`   | Forcefully stops the program and its descendants. JVM only, JDK 9 or later.          |

## Platform Access

`Process#execute` runs a function on the platform's own process object and returns the result as an effect. That object is `java.lang.Process` on the JVM and Scala Native, and a Node.js child process on Scala.js, so code that uses it is not portable:

```scala
import zio._
import zio.process._

val javaPid: ZIO[Any, CommandError, Long] =
  for {
    process <- Command("sleep", "1").run
    pid     <- process.execute(_.pid)
  } yield pid
```

`Process#getOutputStream` returns the program's standard input as a Java `OutputStream`. On Scala.js, it wraps the Node.js standard input stream. It is meant for programs that write to a process by hand.
