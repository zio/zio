---
id: "console"
title: "Console"
description: "Service providing simple I/O operations for reading/writing strings from/to standard input, output, and error console."
keywords:
  - "Console Service"
  - "Standard Input/Output"
  - "Error Handling"
  - "String I/O"
---

The Console service contains simple I/O operations for reading/writing strings from/to the standard input, output, and error console. It follows the shared [service pattern](./anatomy.md) common to all of ZIO's built-in services: a `Console` trait, a `Live` implementation, and a synchronous `UnsafeAPI` for interop.

| Function         | Input Type          | Output Type                     |
|------------------|----------------------|---------------------------------|
| `print`          | `line: => Any`       | `ZIO[Any, IOException, Unit]`   |
| `printError`     | `line: => Any`       | `ZIO[Any, IOException, Unit]`   |
| `printLine`      | `line: => Any`       | `ZIO[Any, IOException, Unit]`   |
| `printLineError` | `line: => Any`       | `ZIO[Any, IOException, Unit]`   |
| `readLine`       |                       | `ZIO[Any, IOException, String]` |
| `readLine`       | `prompt: String`      | `ZIO[Any, IOException, String]` |

Unlike Clock and Random, whose methods mostly return `UIO` and cannot fail, every Console operation can fail with `IOException`, since reading from or writing to the console is a real I/O operation that can go wrong.

`readLine(prompt)` is a convenience overload defined as a default method on the `Console` trait itself, combining `print` and `readLine`:

```scala
trait Console {
  def readLine(prompt: String): IO[IOException, String] =
    print(prompt) *> readLine
}
```

All functions of the Console service are effectful, this means they are just descriptions of reading/writing from/to the console. 

As ZIO data type supports monadic operations, we can compose these functions with for-comprehension which helps us to write our program pretty much like an imperative program:

```scala mdoc:compile-only
import java.io.IOException

import zio._
import zio.Console._

object MyHelloApp extends ZIOAppDefault {
  val program: ZIO[Any, IOException, Unit] = for {
    _    <- printLine("Hello, what is you name?")
    name <- readLine
    _    <- printLine(s"Hello $name, welcome to ZIO!")
  } yield ()

  def run = program
}
```

Note again, every line of our `program` are descriptions, not statements. As we can see the type of our `program` is `ZIO[Any, IOException, Unit]`, it means to run `program` we do not need any environment, it may fail due to failure of `readLine` and it will produce `Unit` value.

## Blocking I/O

`ConsoleLive` implements `print` and `printLine` with `ZIO.attemptBlockingIO`, and `readLine` with `ZIO.attemptBlockingInterrupt` — both run the underlying, genuinely blocking system call on ZIO's blocking thread pool rather than on the fiber's regular executor. This keeps a slow console write or a read waiting on user input from starving other fibers, but the operation itself is still blocking work, not a lightweight async one — avoid calling these in a tight loop where throughput matters.

`readLine` fails with `EOFException` (a subtype of `IOException`) when standard input is exhausted — for example, when a program's stdin is piped from a file or another process that has finished producing input. Handle it explicitly when a missing line is expected rather than a bug:

```scala mdoc:compile-only
import zio._
import java.io.EOFException

val nextLineOrDefault: ZIO[Any, java.io.IOException, String] =
  Console.readLine.catchSome { case _: EOFException =>
    ZIO.succeed("<no input>")
  }
```

## Synchronous Access (`unsafe`)

`Console` also exposes a synchronous `UnsafeAPI`, following the pattern described in [Anatomy of a Built-in Service](./anatomy.md#synchronous-access-the-unsafeapi). `ConsoleLive`'s `unsafe` implementation calls `scala.io.StdIn.readLine()` directly for input (throwing `EOFException` on a `null` result) and writes through `scala.Console`, without running an effect:

```scala mdoc:compile-only
import zio._

Unsafe.unsafe { implicit unsafe =>
  Console.ConsoleLive.unsafe.printLine("hello")
}
```

Prefer the ordinary `Console.printLine`/`Console.readLine` accessors everywhere else.

## Testing Console I/O

`TestConsole` lets tests feed predetermined input and capture output instead of touching the real console. See [Testing Console](../test/services/console.md) for its `feedLines`, `output`, and `debug`/`silent` API.

## See Also

- [Built-in Services](index.md) — Guide to ZIO's built-in services: Console, Clock, Random, and System with automatic environment management.
