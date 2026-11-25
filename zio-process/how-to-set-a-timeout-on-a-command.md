# Set a Timeout on a Command

> This guide shows how to give a program a deadline and stop it when the deadline passes. Use `Command#run` to get the `Process` handle, wait for the exit code with `ZIO#timeout`, and call `Process#kill` yourself when the wait times out.

## Kill the Process After a Deadline

The following effect returns `Some(code)` if the program finishes in time, and `None` after it has stopped a program that took too long:

```scala
import zio._
import zio.process._

import zio.durationInt

val limited: ZIO[Any, CommandError, Option[ExitCode]] =
  for {
    process <- Command("sleep", "20").run
    result  <- process.exitCode.timeout(5.seconds)
    _       <- ZIO.when(result.isEmpty)(process.kill)
  } yield result
```

`Process#exitCode` destroys the program when the waiting fiber is interrupted, so `ZIO#timeout` already stops it. The explicit `Process#kill` also waits until the program has exited. Wrapping `Command#exitCode` in `ZIO#timeout` is not covered by the project's tests: the test is disabled (see zio/zio#3840), so prefer the handle for a hard deadline.

## Fail Instead of Returning None

To turn the timeout into a failure that the caller handles, fail with `ZIO.fail` after stopping the program:

```scala
import zio._
import zio.process._

import zio.durationInt

val strict: ZIO[Any, Throwable, ExitCode] =
  for {
    process <- Command("sleep", "20").run
    result  <- process.exitCode.timeout(5.seconds)
    code    <- result match {
                 case Some(code) => ZIO.succeed(code)
                 case None       => process.kill *> ZIO.fail(new RuntimeException("sleep timed out"))
               }
  } yield code
```

For other ways to stop a program, see [Stop a Running Process](./how-to-stop-a-running-process.md).
