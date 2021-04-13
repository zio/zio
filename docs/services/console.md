---
id: console 
title: "Console"
---

The Console service contains simple I/O operations for reading/writing strings from/to the standard input, output, and error console.

| Function        | Input Type        | Output Type                         |
|-----------------|-------------------|-------------------------------------|
| `print`         | `line: => String` | `URIO[Console, Unit]`               |
| `printError`    | `line: => String` | `URIO[Console, Unit]`               |
| `printLine`     | `line: => String` | `URIO[Console, Unit]`               |
| `printLineError`| `line: => String` | `URIO[Console, Unit]`               |
| `readLine`      |                   | `ZIO[Console, IOException, String]` |

All functions of the Console service are effectful, this means they are just descriptions of reading/writing from/to the console. 

As ZIO data type support monadic operations, we can compose these functions with for-comprehension which helps us to write our program pretty much like an imperative program:

```scala mdoc:silent
import java.io.IOException

import zio._
import zio.Console._

object MyHelloApp extends zio.App {
  val program: ZIO[Has[Console], IOException, Unit] = for {
    _ <- printLine("Hello, what is you name?")
    name <- readLine
    _ <- printLine(s"Hello $name, welcome to ZIO!")
  } yield ()

  override def run(args: List[String]) = program.exitCode
}
```

Note again, every line of our `program` are descriptions, not statements. As we can see the type of our `program` is `ZIO[Has[Console], IOException, Unit]`, it means to run `program` we need the `Console` service as an environment, it may fail due to failure of `readLine` and it will produce `Unit` value.
