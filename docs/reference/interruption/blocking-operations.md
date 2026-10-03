---
id: blocking-operations
title: "Interrupting Blocking Operations"
sidebar_label: "Blocking Operations"
description: "How ZIO interruption relates to blocking code: attemptBlocking, attemptBlockingInterrupt and attemptBlockingCancelable, and how to cancel blocking operations that ignore Thread#interrupt."
keywords:
  - "attemptBlocking"
  - "attemptBlockingInterrupt"
  - "attemptBlockingCancelable"
  - "Blocking Operations"
  - "Thread Interrupt"
---

ZIO's interruption model works by unwinding a fiber at a safe point, but a thread sitting inside a blocking call is not at a safe point and does not reach one on its own. Interrupting the fiber tears down everything ZIO knows about, while the blocking call keeps the thread. Bridging that gap requires telling the blocking code, in its own terms, to stop.

## Interruption of Blocking Operations

By default, when we convert a blocking operation into a ZIO effect using `ZIO.attemptBlocking`, there is no guarantee that if that effect is interrupted the underlying effect will be interrupted.

Let's create a blocking effect from an endless loop:

```scala mdoc:compile-only
import zio._

for {
  _ <- Console.printLine("Starting a blocking operation")
  fiber <- ZIO.attemptBlocking {
    while (true) {
      Thread.sleep(1000)
      println("Doing some blocking operation")
    }
  }.ensuring(
    Console.printLine("End of a blocking operation").orDie
  ).fork
  _ <- fiber.interrupt.schedule(
    Schedule.delayed(
      Schedule.duration(1.seconds)
    )
  )
} yield ()
```

When we interrupt this loop after one second it will still not stop. It will only stop when the entire JVM stops. The `ZIO.attemptBlocking` operator doesn't translate the ZIO interruption into thread interruption (`Thread#interrupt`).

Instead, we should use `ZIO.attemptBlockingInterrupt` to create interruptible blocking effects:

```scala mdoc:compile-only
import zio._

for {
  _ <- Console.printLine("Starting a blocking operation")
  fiber <- ZIO.attemptBlockingInterrupt {
    while(true) {
      Thread.sleep(1000)
      println("Doing some blocking operation")
    }
  }.ensuring(
     Console.printLine("End of the blocking operation").orDie
   ).fork
  _ <- fiber.interrupt.schedule(
    Schedule.delayed(
      Schedule.duration(3.seconds)
    )
  )
} yield ()
```

Two notes on choosing between the blocking constructors:

1. If we are converting a blocking I/O to a ZIO effect, it would be better to use `ZIO.attemptBlockingIO` which refines the error type to `java.io.IOException`.
2. The `ZIO.attemptBlockingInterrupt` method adds significant overhead. So for performance-sensitive applications, it is better to handle interruptions manually using `ZIO.attemptBlockingCancelable`.

## Cancellation of Blocking Operation

Some blocking operations do not respect `Thread#interrupt` by swallowing `InterruptedException`. So they will not be interrupted via `ZIO.attemptBlockingInterrupt`. Instead, they may provide us an API to signal them to _cancel_ their operation.

The following `BlockingService` will not be interrupted in case of a `Thread#interrupt` call, but it checks the `released` flag constantly. If this flag becomes true, the blocking service will finish its job:

```scala mdoc:silent
import zio._
import java.util.concurrent.atomic.AtomicReference

final case class BlockingService() {
  private val released = new AtomicReference(false)

  def start(): Unit = {
    while (!released.get()) {
      println("Doing some blocking operation")
      try Thread.sleep(1000)
      catch {
        case _: InterruptedException => () // Swallowing InterruptedException
      }
    }
    println("Blocking operation closed.")
  }

  def close(): Unit = {
    println("Releasing resources and ready to be closed.")
    released.getAndSet(true)
  }
}
```

So to translate ZIO interruption into cancellation of these types of blocking operations we should use `ZIO.attemptBlockingCancelable`. This method takes a `cancel` effect which is responsible for signalling the blocking code to close itself when ZIO interruption occurs:

```scala mdoc:compile-only
import zio._

val myApp =
  for {
    service <- ZIO.attempt(BlockingService())
    fiber   <- ZIO.attemptBlockingCancelable(
      effect = service.start()
    )(
      cancel = ZIO.succeed(service.close())
    ).fork
    _       <- fiber.interrupt.schedule(
      Schedule.delayed(
        Schedule.duration(3.seconds)
      )
    )
  } yield ()
```

Here is another example of the cancellation of a blocking operation. When we `accept` a server socket, this blocking operation will never be interrupted until we close it using the `ServerSocket#close` method:

```scala mdoc:compile-only
import java.net.{Socket, ServerSocket}
import zio._

def accept(ss: ServerSocket): Task[Socket] =
  ZIO.attemptBlockingCancelable(ss.accept())(ZIO.succeed(ss.close()))
```

The `cancel` effect plays the same role for blocking code that the `Left` canceler of `ZIO.asyncInterrupt` plays for callback-based code, and it is subject to the same guarantee: it runs when the fiber is interrupted, and the fiber's own finalizers run afterwards. See [Interrupting Asynchronous Effects](interruption-and-finalizers.md#interrupting-asynchronous-effects) for the asynchronous counterpart, and the [ZIO data type](../core/zio/zio.md#blocking-synchronous-side-effects) page for the full set of blocking constructors.
