# Introduction to ZIO's Built-in Services

> Guide to ZIO's built-in services: Console, Clock, Random, and System with automatic environment management.

ZIO already provides four built-in services:

1. **[Console](console.md)** — Operations for reading/writing strings from/to the standard input, output, and error console.
2. **[Clock](clock.md)** — Contains some functionality related to time and scheduling.
3. **[Random](random.md)** — Provides utilities to generate random numbers.
4. **[System](system.md)** — Contains several useful functions related to system environments and properties.

All four follow the same design: a service trait, a companion object with a `Tag` and accessor methods, a `Live` implementation, and a synchronous `UnsafeAPI`. See [Anatomy of a Built-in Service](anatomy.md) for how that shared design works.

When we use these services we don't need to provide their corresponding environment explicitly. ZIO provides built-in live version of ZIO services to our effects, so we do not need to provide them manually. This works because ZIO seeds a `FiberRef` of default services, `DefaultServices.live`, with `Live` implementations of all four services before a program ever runs, so every accessor reads from that `FiberRef` unless something upstream overrides it — see [Anatomy of a Built-in Service](anatomy.md) for the details.

```scala
import zio._

import java.io.IOException

object MainApp extends ZIOAppDefault {
  val myApp: ZIO[Any, IOException, Unit] = 
    for {
      date <- Clock.currentDateTime
      _    <- ZIO.logInfo(s"Application started at $date")
      _    <- Console.print("Enter your name: ")
      name <- Console.readLine
      _    <- Console.printLine(s"Hello, $name!")
    } yield ()

  def run = myApp
}
```

`myApp` above needs both `Clock` and `Console`, so its environment type composes them with an intersection type — `Clock with Console` in Scala 2, `Clock & Console` in Scala 3. [Anatomy of a Built-in Service](anatomy.md) covers this environment composition in detail.

## Testing These Services

Each of these four services has a deterministic test double — `TestClock`, `TestConsole`, `TestRandom`, and `TestSystem` — that ZIO Test swaps in for `Live` behind the same trait, so code written against `Clock`, `Console`, `Random`, and `System` is testable without any changes. See [Testing These Services](../test/services/index.md) for how each test double works.

## See Also

- [Anatomy of a Built-in Service](anatomy.md) — The shared trait/companion/`Live`/`UnsafeAPI` design behind all four services.
- [Writing ZIO Services](../service-pattern/index.md) — Guide to ZIO Service Pattern: define maintainable services using interfaces and ZLayer for automatic dependency injection.
