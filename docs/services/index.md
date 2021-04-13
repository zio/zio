---
id: index
title: "Introduction"
---

In the functional Scala as well as in object-oriented programming the best practice is to _Program to an interface, not an implementation_. This is the most important design principle in software development and helps us to write maintainable code by:

* Allowing the client to hold an interface as a contract and don't worry about the implementation. The interface signature determines all operations that should be done. 

* Enabling a developer to write more testable programs. When we write a test for our business logic we don't have to run and interact with real services like databases which makes our test run very slow. If our code is correct our test code should always pass, there should be no hidden variables or depend on outside sources. We can't know that the database is always running correctly. We don't want to fail our tests because of the failure of external service.

* Providing the ability to write more modular applications. So we can plug in different implementations for different purposes without a major modification.

It is not mandatory but ZIO encourages us to follow this principle by bundling related functionality as an interface by using module pattern. 

Let's see how the `Console` service is defined and implemented in ZIO. To prevent naming collision with the original implementation we use the `Terminal` name to introduce the `Console` service.

1. At the first step, we need to define our module `Terminal` as a type alias for `Has[Terminal.Service]`.

2. Then inside the `Terminal` companion object, we should define the service definition in a trait named `Service`. Traits are how we define services. A service could be all the stuff that is related to one concept with singular responsibility.

3. After that we need to implement our Service by creating a new Service and then lifting that entire implementation into the ZLayer data type by using the `succeed` operator.

4. Finally, to create our API more ergonomic it's better to implement accessor methods for all of our service methods.

```scala mdoc:invisible
import zio.{Has, UIO, Layer, ZLayer, ZIO, URIO}
```

```scala mdoc:silent
object terminal {
  type Terminal = Has[Terminal.Service]

  object Terminal {
    trait Service {
      def print(line: String): UIO[Unit]

      def printLine(line: String): UIO[Unit]
    }

    val live: Layer[Nothing, Terminal] = ZLayer.succeed {
      new Service {
        override def print(line: String): UIO[Unit] =
          ZIO.effectTotal(print(line))

        override def printLine(line: String): UIO[Unit] =
          ZIO.effectTotal(println(line))
      }
    }
  }

  def print(line: => String): URIO[Terminal, Unit] =
    ZIO.accessM(_.get.print(line))

  def printLine(line: => String): URIO[Terminal, Unit] =
    ZIO.accessM(_.get.printLine(line))
}
```

This is how the ZIO services are created. Let's use the `Terminal` service in our application:

```scala mdoc:silent
object TerminalExample extends zio.App {
  import zio.RIO
  import terminal._
 
  private val application: RIO[Terminal, Unit] = printLine("Hello, World!") 

  override def run(args: List[String]) = 
    application.provideLayer(Terminal.live).exitCode
}
```

When we write our entire `application` we don't care which implementation version of Terminal service will be injected into our `application`, later at the end of the day, it will be provided by methods like `provideLayer`.

ZIO already provided 5 build-in services, when we use these services we don't need to provide their corresponding environment explicitly. The `ZEnv` environment is a type alias for all of these services and will be provided by ZIO to our effects:

- **[Console](console.md)** — Operations for reading/writing strings from/to the standard input, output, and error console.
- **[Clock](clock.md)** — Contains some functionality related to time and scheduling. 
- **[Random](random.md)** — Provides utilities to generate random numbers.
- **[Blocking](blocking.md)** — Provides access to a thread pool that can be used for performing blocking operations.
- **[System](system.md)** — Contains several useful functions related to system environments and properties.
