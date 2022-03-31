---
id: console 
title: "Console"
---

The Console service contains simple I/O operations for reading/writing strings from/to the standard input, output, and error console.

| Function        | Input Type        | Output Type                         |
|-----------------|-------------------|-------------------------------------|
| `print`         | `line: => String` | `ZIO[Any, IOException, Unit]`       |
| `printError`    | `line: => String` | `ZIO[Any, IOException, Unit]`       |
| `printLine`     | `line: => String` | `ZIO[Any, IOException, Unit]`       |
| `printLineError`| `line: => String` | `ZIO[Any, IOException, Unit]`       |
| `readLine`      |                   | `ZIO[Any, IOException, String]`     |

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
