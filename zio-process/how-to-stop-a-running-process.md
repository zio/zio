# Stop a Running Process

> This guide shows how to stop a program that is still running, and how to check on it first. How you stop it depends on whether you hold a `Process` handle. The examples need these imports:

```scala
import zio._
import zio.process._

import zio.durationInt
```

## Stop It by Interrupting the Fiber

When you run a command with `Command#exitCode` or `Command#successfulExitCode`, the process belongs to the fiber that runs it. Interrupting that fiber destroys the process, so you do not need to track it:

```scala
val interrupted: ZIO[Any, Nothing, Unit] =
  for {
    fiber <- Command("sleep", "20").exitCode.forkDaemon
    _     <- ZIO.sleep(5.seconds)
    _     <- fiber.interrupt
  } yield ()
```

## Stop It Through the Handle

`Command#run` returns a `Process` handle as soon as the program starts. The handle is not tied to the fiber that started it, so interrupting that fiber does not stop the program, unless the fiber is waiting in `Process#exitCode` or `Process#successfulExitCode`, which destroy the program when interrupted. Call `Process#kill` when you decide to stop it. It sends the terminate signal (SIGTERM on Unix) and waits until the program exits:

```scala
val killed: ZIO[Any, CommandError, Unit] =
  for {
    process <- Command("sleep", "20").run
    _       <- ZIO.sleep(5.seconds)
    _       <- process.kill
  } yield ()
```

## Force It to Stop

`Process#killForcibly` is the forceful version, SIGKILL on Unix, and it also waits. Call `Process#kill` first, which lets the program clean up, and fall back to `Process#killForcibly` only when a program does not stop:

```scala
val forced: ZIO[Any, CommandError, Unit] =
  for {
    process <- Command("sleep", "20").run
    _       <- process.killForcibly
  } yield ()
```

On Scala.js, `Process#killForcibly` sends the same signal as `Process#kill`.

## Stop the Program and Its Children

A program often starts child processes, and stopping only the parent can leave them running. On the JVM, `Process#killTree` stops the parent and all its descendants, and `Process#killTreeForcibly` does the same with SIGKILL. Both need JDK 9 or later and do not exist on Scala.js or Scala Native:

```scala
val treeStopped: ZIO[Any, CommandError, Unit] =
  for {
    process <- Command("sleep", "20").run
    _       <- process.killTree
  } yield ()
```

## Check Whether It Is Still Running

`Process#isAlive` returns `true` while the program has not exited, and `Process#pid` returns the operating system's process ID, which is useful to log or to connect from another tool:

```scala
val status: ZIO[Any, CommandError, (Boolean, Long)] =
  for {
    process <- Command("sleep", "20").run
    alive   <- process.isAlive
    pid     <- process.pid
    _       <- process.kill
  } yield (alive, pid)
```

On Scala.js, `Process#isAlive` returns `true` even after the program has exited, so it cannot detect that a program finished. To stop a program after a deadline, see [Set a Timeout on a Command](./how-to-set-a-timeout-on-a-command.md).
