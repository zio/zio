---
id: index
title: "Introduction"
---

## ZIO Environment
The `ZIO[-R, +E, +A]` data type describes an effect that requires an input type of `R`, as an environment, may fail with an error of type `E` or succeed and produces a value of type `A`.

The input type is also known as _environment type_. This type-parameter indicates that to run an effect we need one or some services as an environment of that effect.

For example, when we have `ZIO[Console, Nothing, Unit]`, this shows that to run this effect we need to provide an implementation of the `Console` service:

```scala mdoc:silent
import zio.ZIO
import zio.console._
val effect: ZIO[Console, Nothing, Unit] = putStrLn("Hello, World!")
```

So finally when we provide a live version of `Console` service to our `effect`, it will be converted to an effect that doesn't require any environmental service:

```scala mdoc:silent
val mainApp: ZIO[Any, Nothing, Unit] = effect.provideLayer(Console.live)
```

Finally, to run our application we can put our `mainApp` inside the `run` method:

```scala mdoc:silent:nest
import zio.{ExitCode, ZEnv, ZIO}
import zio.console._

object MainApp extends zio.App {
  val effect: ZIO[Console, Nothing, Unit] = putStrLn("Hello, World!")
  val mainApp: ZIO[Any, Nothing, Unit] = effect.provideLayer(Console.live)

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = 
    mainApp.exitCode
}
```

Sometimes an effect needs more than one environmental service, it doesn't matter, in these cases, we compose all dependencies by `++` operator:

```scala mdoc:silent:nest
import zio.console._
import zio.random._

val effect: ZIO[Console with Random, Nothing, Unit] = for {
  r <- nextInt
  _ <- putStrLn(s"random number: $r")
} yield ()

val mainApp: ZIO[Any, Nothing, Unit] = effect.provideLayer(Console.live ++ Random.live)
```

We don't need to provide live layers for built-in services (don't worry, we will discuss layers later in this page). ZIO has a `ZEnv` type alias for the composition of all ZIO built-in services (Clock, Console, System, Random, and Blocking). So we can run the above `effect` as follows:

```scala mdoc:silent:nest
import zio.console._
import zio.random._
import zio.{ExitCode, ZEnv, ZIO}

object MainApp extends zio.App {
  val effect: ZIO[Console with Random, Nothing, Unit] = for {
    r <- nextInt
    _ <- putStrLn(s"random number: $r")
  } yield ()

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    effect.exitCode
}
```

ZIO environment facility enables us to:

1. **Code to Interface** — like object-oriented paradigm, in ZIO we encouraged to code to interfaces and defer the implementation. It is the best practice, but ZIO does not enforce us to do that.

2. **Write a Testable Code** — By coding to an interface, whenever we want to test our effects, we can easily mock any external services, by providing a _test_ version of those instead of the `live` version.

## Defining Services in OOP

Before diving into writing services in ZIO style, let's review how we define them in object-oriented fashion:

1. **Service Definition** — In object-oriented programming, we define services with traits. A service is a bundle of related functionality which are defined in a trait:

```scala mdoc:silent:nest
trait FooService {

}
```

2. **Service Implementation** — We implement these services by using classes:

```scala mdoc:silent:nest
class FooServiceImpl extends FooService {
    
}
```

3. **Defining Dependencies** — If the creation of a service depends on other services, we can define these dependencies by using constructors:

```scala mdoc:silent:nest
trait ServiceA {

}

trait ServiceB {

}

class FooServiceImpl(a: ServiceA, b: ServiceB) {

}
```

In object-oriented programming, the best practice is to _program to an interface, not an implementation_. So in the previous example, `ServiceA` and `ServiceB` are interfaces, not concrete classes. 

4. **Injecting Dependencies** — Now, the client of `FooServiceImpl` service can provide its own implementation of `ServiceA` and `ServiceB`, and inject them to the `FooServiceImpl` constructor:

```scala mdoc:silent:nest
class ServiceAImpl extends ServiceA
class ServiceBImpl extends ServiceB
val fooService = new FooServiceImpl(new ServiceAImpl, new ServiceBImpl)
```

Sometimes, as the number of dependent services grows and the dependency graph of our application becomes complicated, we need an automatic way of wiring and providing dependencies into the services of our application. In these situations, we might use a dependency injection framework to do all its magic machinery for us.

## Contextual Data Types

Defining service in ZIO is not very different from object-oriented style, it has the same principle; coding to an interface, not an implementation. But the way ZIO encourages us to implement this principle differs somewhat from the object-oriented style. 

ZIO encourages us to write service with _Module Pattern_. Before diving into introducing this technique, let's get to know more about ZIO contextual types. ZIO have two data type that plays a key role in writing ZIO services using Module Pattern: 

1. ZLayer
2. Has

Let's review each of them.

### ZLayer

A `ZLayer[-RIn, +E, +ROut]` data type describes how to construct `ROut` services by using `RIn` services. 

We can think of `ZLayer` as a more powerful version of a constructor, it is an alternative way to represent a constructor. Like a constructor, it allows us to build the `ROut` service in terms of its dependencies (`RIn`).

For example, a `ZLayer[Blocking with Logging, Throwable, Database]` can be thought of as a function that map `Blocking` and `Logging` services into `Database` service: 

```scala
(Blocking, Logging) => Database
```

So we can say that the `Database` service has two dependencies: `Blocking` and `Logging` services.

### Has
A `Has[A]` data type is a wrapper that we usually used for wrapping services. (e.g. Has[UserRepo] or Has[Logging]).

ZIO wrap services with `Has` data type to:

1. **Wire/bind** services into their implementations. This data type has an internal map to maintain this binding. 

2. **Combine** multiple services together. ZIO uses `++` operator to combine services and mix them to create a bigger dependency object.

Let's combine `Has[Database]` and `Has[Logging]` services with `++` operator:

```scala mdoc:invisible
import zio._
```

```scala mdoc:silent
trait Database
trait Logging

val hasDatabase: Has[Database] = Has(new Database {})
val hasLogging: Has[Logging]   = Has(new Logging {})

val combined: Has[Database] with Has[Logging] = hasDatabase ++ hasLogging
```

ZIO internally ask `combined` using `get` method to determine wiring configurations:

```scala mdoc:silent
val database: Database = combined.get[Database]
val logging: Logging   = combined.get[Logging]
```

These are implementation details, and we don't care about them. We usually don't create directly any `Has` data type.

Whenever we lift a service value into `ZLayer` with the `ZLayer.succeed` constructor, ZIO will wrap our service with `Has` data type:

```scala mdoc:silent:nest
trait Logging {
  def log(line: String): UIO[Unit]
}

val logging: ULayer[Has[Logging]] = ZLayer.succeed(new Logging {
  override def log(line: String): UIO[Unit] = ZIO.effectTotal(println(line))
})
```

Let's write a layer for `Database` service:

```scala mdoc:silent:nest
trait Database {
   def putInt(key: String): UIO[Unit]
   def getInt(key: String): UIO[Int]
}

val database: ULayer[Has[Database]] = ZLayer.succeed(new Database {
  override def putInt(key: String): UIO[Unit] = ???
  override def getInt(key: String): UIO[Int] = ???
})
```

Now, when we combine multiple layer together, these services will combined via `with` intersection type:

```scala mdoc:silent:nest
val myLayer: ZLayer[Any, Nothing, Has[Logging] with Has[Database]] = logging ++ database
```

Finally, when we provide our layer into the ZIO effect, ZIO can access the binding configuration and extract each service. ZIO does internally these pieces of wiring machinery, we don't care about them:

```scala mdoc:invisible
val effect: ZIO[Has[Logging] with Has[Database], Throwable, Unit] = ZIO.effect(???)
```

```scala mdoc:silent
effect.provideLayer(myLayer) 
```

## Defining ZIO Services
ZIO has two patterns to write services. The first version of Module Pattern has some boilerplate, but the second version is very concise and straightforward. ZIO doesn't mandate any of them, you can use whichever you like.

In object-oriented programming:

- **Service Definition** is done by using _interfaces_ (Scala trait or Java Interface).
- **Service Implementation** is done by implementing interfaces using _classes_ or creating _new object_ of the interface.
- **Defining Dependencies** is done by using _constructors_. They allow us to build classes, give their dependencies. This is called constructor-based dependency injection.

We have a similar analogy in Module Pattern, except instead of using _constructors_ we use **`ZLayer`** to define dependencies. So in ZIO fashion, we can think of `ZLayer` as a service constructor.

### Module Pattern 1.0

Let's see how the `Console` service is defined and implemented in ZIO:

1. **Wrapping Service Definition with Has** — At the first step, we create a package object of `console`, and inside that we define the `Console` module as a type alias for `Has[Console.Service]`.

2. **Service Definition** — Then we create the `Console` companion object. Inside the companion object, we define the service definition with a trait named `Service`. Traits are how we define services. A service could be all the stuff that is related to one concept with singular responsibility.

3. **Service Implementation** — After that, we implement our service by creating a new Service and then lifting that entire implementation into the `ZLayer` data type by using the `succeed` constructor.

4. **Defining Dependencies** — If our service has a dependency on other services, we should use constructors like `ZLayer.fromService` and `ZLayer.fromServices`.

5. **Accessor Helper** — Finally, to create the API more ergonomic, it's better to write accessor methods for all of our service methods. 

Accessor methods allow us to utilize all the features inside the service through the ZIO Environment. That means, if we call `putStrLn`, we don't need to pull out the `putStrLn` from the ZIO Environment. The `accessM` method helps us to access the environment of effect and reduce the redundant operation, every time.

```scala mdoc:invisible
import zio.{Has, UIO, Layer, ZLayer, ZIO, URIO}
```

```scala mdoc:silent
object console {
  type Console = Has[Console.Service]

  // Companion object exists to hold service definition and also the live implementation.
  object Console {
    trait Service {
      def putStr(line: String): UIO[Unit]

      def putStrLn(line: String): UIO[Unit]
    }

    val live: Layer[Nothing, Console] = ZLayer.succeed {
      new Service {
        override def putStr(line: String): UIO[Unit] =
          ZIO.effectTotal(print(line))

        override def putStrLn(line: String): UIO[Unit] =
          ZIO.effectTotal(println(line))
      }
    }
  }

  // Accessor Methods
  def putStr(line: => String): URIO[Console, Unit] =
    ZIO.accessM(_.get.putStr(line))

  def putStrLn(line: => String): URIO[Console, Unit] =
    ZIO.accessM(_.get.putStrLn(line))
}
```

This is how ZIO services are created. Let's use the `Console` service in our application:

```scala mdoc:silent
object ConsoleExample extends zio.App {
  import zio.RIO
  import console._
 
  private val application: RIO[Console, Unit] = putStrLn("Hello, World!") 

  override def run(args: List[String]) = 
    application.provideLayer(Console.live).exitCode
}
```

During writing an application we don't care which implementation version of the `Console` service will be injected into our `application`, later at the end of the day, it will be provided by methods like `provideLayer`.

### Module Pattern 2.0

Writing services with Module Pattern 2.0 is much easier than the previous one. It removes some level of indirection from the previous version, and much more similar to the object-oriented approach in writing services.

Module Pattern 2.0 has more similarity with object-oriented way of defining services. We use classes to implement services, and we use constructors to define service dependencies. But at the end of the day, we lift class constructor into the ZLayer.

1. **Service Definition** — Defining service in this version has changed slightly compared to the previous version. We would take the service definition and pull it out into the top-level:

```scala mdoc:invisible:reset
import zio._
```

```scala mdoc:silent
trait Logging {
  def log(line: String): UIO[Unit]
}
```

2. **Service Implementation** — It is the same as what we did in object-oriented fashion. We implement the service with Scala class. By convention, we name the live version of its implementation as `LoggingLive`:

```scala mdoc:silent:nest
case class LoggingLive() extends Logging {
  override def log(line: String): UIO[Unit] = 
    ZIO.effectTotal(print(line))
}
```

3. **Define Service Dependencies** — We might need `Console` and `Clock` services to implement the `Logging` service. In this case, we put its dependencies into its constructor. All the dependencies are just interfaces, not implementation. Just like what we did in object-oriented style:

```scala mdoc:invisible:reset
import java.time._
import zio._

trait Logging {
  def log(line: String): UIO[Unit]
}

trait Clock {
  def currentDateTime: IO[DateTimeException, OffsetDateTime]
}

trait Console {
  def putStrLn(line: String): UIO[Unit] 
}
```

```scala mdoc:silent
case class LoggingLive(console: Console, clock: Clock) extends Logging {
  override def log(line: String): UIO[Unit] = 
    for {
      current <- clock.currentDateTime.orDie
      _       <- console.putStrLn(current.toString + "--" + line)
    } yield ()
}
```

4. **Defining ZLayer** — Now, we create a companion object for `LoggingLive` data type and lift the service implementation into the `ZLayer`:

```scala mdoc
object LoggingLive {
  val live: URLayer[Has[Console] with Has[Clock], Has[LoggingLive]] = 
    (LoggingLive(_, _)).toLayer
}
```

That's it! Very simple! ZIO encourages us to follow some of the best practices in object-oriented programming. So it doesn't require us to throw away all our object-oriented knowledge. 
