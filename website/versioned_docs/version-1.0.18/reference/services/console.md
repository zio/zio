---
id: console
title: "Console"
---

Console service contains simple I/O operations for reading/writing strings from/to the standard input, output, and error console.

| Function      | Input Type        | Output Type                         |
|---------------|-------------------|-------------------------------------|
| `putStr`      | `line: => String` | `URIO[Console, Unit]`               |
| `putStrErr`   | `line: => String` | `URIO[Console, Unit]`               |
| `putStrLn`    | `line: => String` | `URIO[Console, Unit]`               |
| `putStrLnErr` | `line: => String` | `URIO[Console, Unit]`               |
| `getStrLn`    |                   | `ZIO[Console, IOException, String]` |

All functions of console service are effectful, this means they are just descriptions of reading/writing from/to the console. 

As ZIO data type support monadic operations, we can compose these functions with for-comprehension which helps us to write our program pretty much like an imperative program:

```scala
import java.io.IOException

import zio.ZIO
import zio.console._

object MyHelloApp extends zio.App {
  val program: ZIO[Console, IOException, Unit] = for {
    _ <- putStrLn("Hello, what is you name?")
    name <- getStrLn
    _ <- putStrLn(s"Hello $name, welcome to ZIO!")
  } yield ()

  override def run(args: List[String]) = program.exitCode
}
```

Note again, every line of our `program` are descriptions, not statements. As we can see the type of our `program` is `ZIO[Console, IOException, Unit]`, it means to run `program` we need the `Console` service as an environment, it may fail due to failure of `getStrLn` and it will produce `Unit` value.
