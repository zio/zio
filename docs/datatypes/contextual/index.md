---
id: index
title: "Introduction to the ZIO's Contextual Data Types"
sidebar_label: "Introduction"
---

ZIO provides a contextual abstraction that encodes the environment of the running effect. This means, every effect can work within a specific context, called an environment.

So when we have a `ZIO[R, E, A]` effect, we can say "given `R` as the environment of the effect, the effect may fail with an error type of `E`, or may succeed with a value of type `A`".

For example, when we have an effect of type `ZIO[DatabaseConnection, IOException, String]`, we can say that our effect works within the context of `DatabaseConnection`. In other words, we can say that our effect requires the `DatabaseConnection` service as a context to run.

We will see how layers can be used to eliminate the environment of an effect:

```scala mdoc:compile-only
import zio._

import java.io.IOException

trait DatabaseConnection

// An effect which requires DatabaseConnection to run
val effect: ZIO[DatabaseConnection, IOException, String] = ???

// A layer that produces DatabaseConnection service
val dbConnection: ZLayer[Any, IOException, DatabaseConnection] = ???

// After applying dbConnection to our environmental effect the reurned
// effect has no dependency on the DatabaseConnection
val eliminated: ZIO[Any, IOException, String] = 
  dbConnection { // Provides DatabaseConnection context
    effect       // An effect running within `DatabaseConnection` context
  }
```

ZIO provide this facility through the following concept and data types:
1. [ZIO Environment](#zio-environment) — The `R` type parameter of `ZIO[R, E, A]` data type.
2. [ZEnvironment](./zenvironment.md) — Built-in type-level map for maintaining the environment of a `ZIO` data type. 
3. [ZLayer](./zlayer.md) — Describes how to build one or more services in our application.

Next, we will discuss _ZIO Environment_ and _ZLayer_ and finally how to write ZIO services using _Module Pattern_.

## ZIO Environment

The `ZIO[-R, +E, +A]` data type describes an effect that requires an input type of `R`, as an environment, may fail with an error of type `E` or succeed and produces a value of type `A`.

The input type is also known as _environment type_. This type-parameter indicates that to run an effect we need one or some services as an environment of that effect. In other word, `R` represents the _requirement_ for the effect to run, meaning we need to fulfill the requirement in order to make the effect _runnable_.

So we can think of `ZIO[R, E, A]` as a mental model of a function from a value of type `R` to the `Either[E, A]`:

```scala
type ZIO[R, E, A] = R => Either[E, A]
```

`R` represents dependencies; whatever services, config, or wiring a part of a ZIO program depends upon to work. We will explore what we can do with `R`, as it plays a crucial role in `ZIO`.

For example, when we have `ZIO[Console, Nothing, Unit]`, this shows that to run this effect we need to provide an implementation of the `Console` service:

```scala mdoc:silent
import zio._

import java.io.IOException

val effect: ZIO[Console, IOException, Unit] = 
  Console.printLine("Hello, World!")
```

So when we provide a live version of `Console` service to our `effect`, it will be converted to an effect that doesn't require any environmental service:

```scala mdoc:compile-only
val mainApp: ZIO[Any, IOException, Unit] = effect.provide(Console.live)
```

```scala mdoc:invisible:reset

```

Finally, to run our application we can put our `mainApp` inside the `run` method:

```scala mdoc:compile-only
import zio._
import zio.Console._
import java.io.IOException

object MainApp extends ZIOAppDefault {
  val effect: ZIO[Console, IOException, Unit] = printLine("Hello, World!")
  val mainApp: ZIO[Any, IOException, Unit] = effect.provide(Console.live)

  def run = mainApp
}
```

Sometimes an effect needs more than one environmental service, it doesn't matter, in these cases, we can provide all dependencies all together:

```scala mdoc:compile-only
import zio._

import java.io.IOException

val effect: ZIO[Console & Random, IOException, Unit] = for {
  r <- Random.nextInt
  _ <- Console.printLine(s"random number: $r")
} yield ()

val mainApp: ZIO[Any, IOException, Unit] = effect.provide(Console.live, Random.live)
```

We don't need to provide live layers for built-in services (Layers will be discussed later on this page). ZIO has a `ZEnv` type alias for the composition of all ZIO built-in services (`Clock`, `Console`, `System`, `Random`, and `Blocking`). So we can run the above `effect` as follows:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = effect
  
  val effect: ZIO[Console & Random, Nothing, Unit] = for {
    r <- Random.nextInt
    _ <- Console.printLine(s"random number: $r").orDie
  } yield ()
}
```

```scala mdoc:invisible:reset

```

### Motivation

One might ask "What is the motivation behind encoding the dependency in the type parameter of `ZIO` data type"? What is the benefit of doing so?

Let's see how writing an application which requires reading from or writing to the console. As part of making the application _modular_ and _testable_ we define a separate service called `Console` which is responsible for reading from and writing to the console. We do that simply by writing an interface:

```scala mdoc:silent
import zio._

trait Console {
  def print(line: Any): Task[Unit]

  def printLine(line: Any): Task[Unit]

  def readLine: Task[String]
}
```

Now we can write our application that accepts the `Console` interface as a parameter:

```scala mdoc:silent
import zio._

def myApp(c: Console): Task[Unit] =
  for {
    _    <- c.print("Please enter your name: ")
    name <- c.readLine
    _    <- c.printLine(s"Hello, $name!")
  } yield ()
```

Similar to the object-oriented paradigm we code to interface not implementation. In order to run the application, we need to implement a production version of the `Console`:

```scala mdoc:silent
import zio._

object ConsoleLive extends Console {
  override def print(line: Any): Task[Unit] =
    Task.attemptBlocking(scala.Predef.print(line))

  override def printLine(line: Any): Task[Unit] =
    Task.attemptBlocking(scala.Predef.println(line))

  override def readLine: Task[String] =
    Task.attemptBlocking(scala.io.StdIn.readLine())
}
```

Finally, we can provide the `ConsoleLive` to our application and run the whole:

```mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def myApp(c: Console): Task[Unit] =
    for {
      _    <- c.print("Please enter your name: ")
      name <- c.readLine
      _    <- c.printLine(s"Hello, $name!")
    } yield ()

  def run = myApp(ConsoleLive)
}
```

```scala mdoc:invisible:reset

```

In the above example, we discard the fact that we can use the ZIO environment and utilize the `R` parameter of the `ZIO` data type. So instead we tried to write the application with the `Task` data type which ignore the ZIO environment. To create our application testable, we gathered all console functionalities into the same interface called, `Console` and implement that in another object called, `ConsoleLive`. Finally, at the end of the day, we provide the implementation of the `Console` service, i.e. `ConsoleLive`, to our application.

**While this technique works for small programs, it doesn't scale.** Assume we have multiple services, and we use them in our application logic like bellow:

```scala
def foo(
   s1: Service1,
   s2: Service2,
   s3: Service3
)(arg1: String, arg2: String, arg3: Int): Task[Int] = ???

def bar(
  s1: Service1,
  s12: Service12,
  s18: Service18, 
  sn: ServiceN
)(arg1: Int, arg2: String, arg3: Double, arg4: Int): Task[Unit]

def myApp(s1: Service1, s2: Service2, ..., sn: ServiceN): Task[Unit] = 
  for {
    a <- foo(s1, s2, s3)("arg1", "arg2", 4) 
    _ <- bar(s1, s12, s18, sn)(7, "arg2", 1.2, a)
      ...
  } yield ()
```

Writing real applications using this technique is tedious and cumbersome because all dependencies have to be passed across all methods. We can simplify the process of writing our application by using the ZIO environment and [Module Pattern](#module-pattern):

```scala
def foo(arg1: String, arg2: String, arg3: Int): ZIO[Service1 & Service2 & Service3, Throwable, Int] = 
  for {
    s1 <- ZIO.service[Service1]
    s2 <- ZIO.service[Service2] 
      ...
  } yield ()

def bar(arg1: Int, arg2: String, arg3: Double, arg4: Int): ZIO[Service1 & Service12 & Service18 & ServiceN, Throwable, Unit] =
  for {
    s1  <- ZIO.service[Service1] 
    s12 <- ZIO.service[Service12]
      ...
  } yield ()
```

### Advantage of Using ZIO Environment

ZIO environment facility enables us to:

1. **Code to Interface** — like object-oriented paradigm, in ZIO we encouraged to code to interface and defer the implementation. It is the best practice, but ZIO does not enforce us to do that.

2. **Write a Testable Code** — By coding to an interface, whenever we want to test our effects, we can easily mock any external services, by providing a _test_ version of those instead of the _live_ version.

3. **Compose Services with Strong Type Inference Facility** — We can compose multiple effects that require various services, so the final effect requires the intersection of all those services:

```scala mdoc:compile-only
import zio._

trait ServiceA
trait ServiceB
trait ServiceC

// Requires ServiceA and produces a value of type Int
def foo: ZIO[ServiceA, Nothing, Int] = ???

// Requires ServiceB and ServiceC and produces a value of type String
def bar: ZIO[ServiceB & ServiceC, Throwable, String] = ???

// Requires ServicB and produces a value of type Double
def baz(a: Int, b: String): ZIO[ServiceB, Nothing, Double] = ???

// Requires ServiceB and ServiceB and ServiceC and produces a value of type Double
val myApp: ZIO[ServiceA & ServiceB & ServiceC, Throwable, Double] =
  for {
    a <- foo
    b <- bar
    c <- baz(a, b)
  } yield c
```

Another important note about the ZIO environment is that the type inference works well on effect composition. After we composed all the application logic together, the compiler and also IDE can infer the proper type for the environment of the final effect.

In the example above, the compiler can infer the environment type of the `myApp` effect which is `ServiceA & ServiceB & ServiceC`.

### Accessing ZIO Environment

We have two types of accessors for the ZIO environment:
1. **Service Accessor (`ZIO.service`)** is used to access a specific service from the environment.
2. **Service Members Accessors (`ZIO.serviceWith` and `ZIO.serviceWithZIO`)** are used to access capabilities of a specific service from the environment.

> **Note**:
>
> To access the entire ZIO environment we can use `ZIO.environment*`, but we do not use these methods regularly to access ZIO services. Instead, we use service accessors and service member accessors.

#### Service Accessor

To access a service from the ZIO environment, we can use the `ZIO.service` constructor. For example, in the following program we are going to access the `AppConfig` from the environment:

```scala mdoc:silent
import zio._

case class AppConfig(host: String, port: Int)

val myApp: ZIO[AppConfig, Nothing, Unit] =
  for {
    config <- ZIO.service[AppConfig]
    _      <- ZIO.logInfo(s"Application started with config: $config")
  } yield ()
```

To run the `myApp` effect, we should provide the `AppConfig` layer (we will talk about `ZLayer` on the next section):

```scala mdoc:compile-only
object MainApp extends ZIOAppDefault {
  def run = myApp.provide(ZLayer.succeed(AppConfig("localhost", 8080)))
}
```

```scala mdoc:invisible:reset

```

To access multiple services from the ZIO environment, we can do the same:

```scala mdoc:compile-only
import zio._

trait Foo
trait Bar
trait Baz

for {
  foo <- ZIO.service[Foo]  
  bar <- ZIO.service[Bar]
  bax <- ZIO.service[Baz]
} yield ()
```

When creating ZIO layers that have multiple dependencies, this can be helpful. We will discuss this pattern in the [Module Pattern](#module-pattern) section.

#### Service Members Accessors

Sometimes instead of accessing a service, we need to access the capabilities (members) of a service. Based on the return type of each capability, we can use one of these accessors:
- **ZIO.serviceWith**
- **ZIO.serviceWithZIO**

In [Module Pattern](#module-pattern), we use these accessors to write "accessor methods" for ZIO services.

Let's look at each one in more detail:

1. **ZIO.serviceWith** — When we are accessing service members whose return type is an ordinary value, we should use the `ZIO.serviceWith`.

In the following example, we need to use the `ZIO.serviceWith` to write accessor methods for all of the `AppConfig` members:

```scala mdoc:compile-only
import zio._

case class AppConfig(host: String, port: Int, poolSize: Int)

object AppConfig {
  // Accessor Methods
  def host: ZIO[AppConfig, Nothing, String]  = ZIO.serviceWith(_.host) 
  def port: ZIO[AppConfig, Nothing, Int]     = ZIO.serviceWith(_.port)
  def poolSize: ZIO[AppConfig, Nothing, Int] = ZIO.serviceWith(_.poolSize)
}

val myApp: ZIO[AppConfig, Nothing, Unit] =
  for {
    host     <- AppConfig.host
    port     <- AppConfig.port
    _        <- ZIO.logInfo(s"The service will be service at $host:$port")
    poolSize <- AppConfig.poolSize
    _        <- ZIO.logInfo(s"Application started with $poolSize pool size")
  } yield ()
```

2. **ZIO.serviceWithZIO** — When we are accessing service members whose return type is a ZIO effect, we should use the `ZIO.serviceWithZIO`.

For example, in order to write the accessor method for the `log` member of the `Logging` service, we need to use the `ZIO.serviceWithZIO` function:

```scala mdoc:compile-only
import zio._

trait Logging {
  def log(line: String): Task[Unit]
}

object Logging {
  // Accessor Methods:
  def log(line: String): ZIO[Logging, Throwable, Unit] =
    ZIO.serviceWithZIO(_.log(line))
}

val myApp: ZIO[Logging & Console, Throwable, Unit] =
  for {
    _    <- Logging.log("Application Started!")
    _    <- Console.print("Please enter your name: ")
    name <- Console.readLine
    _    <- Console.printLine(s"Hello, $name!")
    _    <- Logging.log("Application exited!")
  } yield ()
```

## ZLayer

Defining service in ZIO is not very different from object-oriented style, it has the same principle; coding to an interface, not an implementation. Therefore, ZIO encourages us to implement this principle by using the _Module Pattern_, which is quite similar to the object-oriented style.

**The `ZLayer` is a data type that plays a key role in writing ZIO services using the _Module Pattern_**. So, before diving into the _Module Pattern_, we need to learn more about it. 

`ZLayer[-RIn, +E, +ROut]` is a recipe to build an environment of type `ROut`, starting from a value `RIn`, and possibly producing an error `E` during creation.

We can compose `layerA` and `layerB` _horizontally_ to build a layer that has the requirements of both, to provide the capabilities of both, through `layerA ++ layerB`

We can also compose layers _vertically_, meaning the output of one layer is used as input for the subsequent layer, resulting in one layer with the requirement of the first, and the output of the second: `layerA >>> layerB`. When doing this, the first layer must output all the services required by the second layer, but we can defer creating some of these services and require them as part of the input of the final layer using `ZLayer.identity`.


### Dependency Injection in ZIO

`ZLayer` combined with the _ZIO Environment_, allow us to use ZIO for dependency injection. There are two parts for dependency injection:
1. **Building Dependency Graph**
2. **Dependency Propagation**

ZIO has a full solution to the dependency injection problem. It solves the first problem by using compositional properties of `ZLayer`, and solves the second by using ZIO Environment facilities like `ZIO#provide`.

The way ZIO manages dependencies between application components gives us extreme power in terms of compositionality and offering the capability to easily change different implementations. This is particularly useful during _testing_ and _mocking_.

By using ZLayer and ZIO Environment we can solve the propagation and wire-up problems in dependency injection. But it doesn't necessary to use it, we can still use things like [Guice](https://github.com/google/guice) with ZIO, or we might like to use [izumi distage](https://izumi.7mind.io/distage/index.html) solution for dependency injection.

### Building Dependency Graph

Assume we have several services with their dependencies, and we need a way to compose and wiring up these dependencies and create the dependency graph of our application. `ZLayer` is a ZIO solution for this problem, it allows us to build up the whole application dependency graph by composing layers horizontally and vertically. More information about how to compose layers is on the [ZLayer](zlayer.md) page.

### Dependency Propagation

When we write an application, our application has a lot of dependencies. We need a way to provide implementations and feeding and propagating all dependencies throughout the whole application. We can solve the propagation problem by using _ZIO environment_.

During the development of an application, we don't care about implementations. Incrementally, when we use various effects with different requirements on their environment, all part of our application composed together, and at the end of the day we have a ZIO effect which requires some services as an environment. Before running this effect by `unsafeRun` we should provide an implementation of these services into the ZIO Environment of that effect.

ZIO has some facilities for doing this. `ZIO#provide` is the core function that allows us to _feed_ an `R` to an effect that requires an `R`.

Notice that the act of `provide`ing an effect with its environment, eliminates the environment dependency in the resulting effect type, represented by type `Any` of the resulting environment.

#### Using `ZIO#provideEnvironment` Method

The `ZIO#provideEnvironment` takes an instance of `ZEnvironment[R]` and provides it to the `ZIO` effect which eliminates its dependency on `R`:

```scala
trait ZIO[-R, +E, +A] {
  def provideEnvironment(r: => ZEnvironment[R]): IO[E, A]
}
```

This is similar to dependency injection, and the `provide*` function can be thought of as _inject_.

```scala mdoc:invisible:reset
import zio._
```

Assume we have the following services:

```scala mdoc:silent:nest
trait Logging {
  def log(str: String): UIO[Unit]
}

object Logging {
  def log(line: String) = ZIO.serviceWithZIO[Logging](_.log(line))
}
```

Let's write a simple program using `Logging` service:

```scala mdoc:silent:nest
val app: ZIO[Logging, Nothing, Unit] = Logging.log("Application Started!")
```

We can `provide` implementation of `Logging` service into the `app` effect:

```scala mdoc:silent:nest
val loggingImpl = new Logging {
  override def log(line: String): UIO[Unit] =
    UIO.succeed(println(line))
}

val effect = app.provideEnvironment(ZEnvironment(loggingImpl))
```

Most of the time, we don't use `ZIO#provideEnvironment` directly to provide our services, instead; we use `ZLayer` to construct the dependency graph of our application, then we use methods like `ZIO#provide`, `ZIO#provideSome` and `ZIO#provideCustom` to propagate dependencies into the environment of our ZIO effect.

#### Using `ZIO#provide` Method

Unlike the `ZIO#provideEnvironment` which takes a `ZEnvironment[R]`, the `ZIO#provide` takes a `ZLayer` to the ZIO effect and translates it to another level.

Assume we have written this piece of program that requires Clock and Console services:

```scala mdoc:silent:nest
import zio.Clock._
import zio.Console._
import zio.Random._

val myApp: ZIO[Random & Console & Clock, Nothing, Unit] = for {
  random  <- nextInt 
  _       <- printLine(s"A random number: $random").orDie
  current <- currentDateTime
  _       <- printLine(s"Current Data Time: $current").orDie
} yield ()
```

We provide implementation of `Random`, `Console` and `Clock` services to the `myApp` effect by using `ZIO#provide` method:

```scala mdoc:silent:nest
val mainEffect: ZIO[Any, Nothing, Unit] = 
  myApp.provide(Random.live, Console.live, Clock.live)
```

As we see, the type of our effect converted from `ZIO[Random & Console & Clock, Nothing, Unit]` which requires two services to `ZIO[Any, Nothing, Unit]` effect which doesn't require any services.

#### Using `ZIO#provideSome` Method

Sometimes we have written a program, and we don't want to provide all its requirements. In these cases, we can use `ZIO#provideSome` to partially apply some layers to the `ZIO` effect.

In the previous example, if we just want to provide the `Console`, we should use `ZIO#provideSome`:

```scala
val mainEffectSome: ZIO[Random & Clock, Nothing, Unit] = 
  myApp.provideSome[Random & Clock](Console.live)
```

> **Note:**
>
> When using `ZIO#provideSome[R0]`, we should provide the remaining type as `R0` type parameter. This workaround helps the compiler to infer the proper types.

#### Using `ZIO#provideCustom` Method

`ZEnv` is a convenient type alias that provides several built-in ZIO services that are useful in most applications. Sometimes we have written a program that contains ZIO built-in services and some other services that are not part of `ZEnv`.

As `ZEnv` provides us the implementation of built-in services, we just need to provide layers for those services that are not part of the `ZEnv`. The `ZIO#provideCustom` method helps us to do so. It returns an effect that only depends on `ZEnv`.

Let's write an effect that has some built-in services and also has a `Logging` service:

```scala mdoc:invisible:reset
import zio._
import zio.Console._
import zio.Clock._
```

```scala mdoc:silent
trait Logging {
  def log(str: String): UIO[Unit]
}

object Logging {
  def log(line: String) = ZIO.serviceWithZIO[Logging](_.log(line))
}

object LoggingLive {
  val layer: ULayer[Logging] = ZLayer.succeed {
    new Logging {
      override def log(str: String): UIO[Unit] = ???
    }
  }
}

val myApp: ZIO[Logging & Console & Clock, Nothing, Unit] = for {
  _       <- Logging.log("Application Started!")
  current <- Clock.currentDateTime
  _       <- Console.printLine(s"Current Data Time: $current").orDie
} yield ()
```

This program uses two ZIO built-in services, `Console` and `Clock`. We don't need to provide `Console` and `Clock` manually, to reduce some boilerplate, we use `ZEnv` to satisfy some common base requirements.

By using `ZIO#provideCustom` we only provide the `Logging` layer, and it returns a `ZIO` effect which only requires `ZEnv`:

```scala mdoc:silent
val mainEffect: ZIO[ZEnv, Nothing, Unit] = myApp.provideCustom(LoggingLive.layer)
```

## Defining ZIO Service

### Defining Services in OOP

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

### Defining Services in ZIO

A service is a group of functions that deals with only one concern. Keeping the scope of each service limited to a single responsibility improves our ability to understand code, in that we need to focus only on one topic at a time without juggling too many concepts together in our head.

`ZIO` itself provides the basic capabilities through modules, e.g. see how `ZEnv` is defined.

In the functional Scala as well as in object-oriented programming the best practice is to _Program to an Interface, Not an Implementation_. This is the most important design principle in software development and helps us to write maintainable code by:

* Allowing the client to hold an interface as a contract and don't worry about the implementation. The interface signature determines all operations that should be done.

* Enabling a developer to write more testable programs. When we write a test for our business logic we don't have to run and interact with real services like databases which makes our test run very slow. If our code is correct our test code should always pass, there should be no hidden variables or depend on outside sources. We can't know that the database is always running correctly. We don't want to fail our tests because of the failure of external service.

* Providing the ability to write more modular applications. So we can plug in different implementations for different purposes without a major modification.

It is not mandatory but ZIO encourages us to follow this principle by bundling related functionality as an interface by using _Module Pattern_.

The core idea is that a layer depends upon the interfaces exposed by the layers immediately below itself, but is completely unaware of its dependencies' internal implementations.

In object-oriented programming:

- **Service Definition** is done by using _interfaces_ (Scala trait or Java Interface).
- **Service Implementation** is done by implementing interfaces using _classes_ or creating _new object_ of the interface.
- **Defining Dependencies** is done by using _constructors_. They allow us to build classes, give their dependencies. This is called constructor-based dependency injection.

We have a similar analogy in Module Pattern, except instead of using _constructors_ we use **`ZLayer`** to define dependencies. So in ZIO fashion, we can think of `ZLayer` as a service constructor.

### Module Pattern

Writing services in ZIO using _Module Pattern_ is much similar to the object-oriented way of defining services. We use scala traits to define services, classes to implement services, and constructors to define service dependencies. Finally, we lift the class constructor into the `ZLayer`.

Let's start learning this module pattern by writing a `Logging` service:

1. **Service Definition** — Traits are how we define services. A service could be all the stuff that is related to one concept with singular responsibility. We define the service definition with a trait named `Logging`:

```scala mdoc:invisible:reset
import zio._
```

```scala mdoc:silent
trait Logging {
  def log(line: String): UIO[Unit]
}
```

2. **Service Implementation** — It is the same as what we did in an object-oriented fashion. We implement the service with the Scala class. By convention, we name the live version of its implementation as `LoggingLive`:

```scala mdoc:compile-only
case class LoggingLiveee() extends Logging {
  override def log(line: String): UIO[Unit] = 
    ZIO.succeed(print(line))
}
```

3. **Define Service Dependencies** — We might need `Console` and `Clock` services to implement the `Logging` service. Here, we put its dependencies into its constructor. All the dependencies are just interfaces, not implementation. Just like what we did in object-oriented style:

```scala mdoc:invisible:reset
import zio._

trait Logging {
  def log(line: String): UIO[Unit]
}
```

```scala mdoc:silent
case class LoggingLive(console: Console, clock: Clock) extends Logging {
  override def log(line: String): UIO[Unit] = 
    for {
      current <- clock.currentDateTime
      _       <- console.printLine(s"$current--$line").orDie
    } yield ()
}
```

4. **Defining ZLayer** — Now, we create a companion object for `LoggingLive` data type and lift the service implementation into the `ZLayer`:

```scala mdoc:silent
object LoggingLive {
  val layer: URLayer[Console & Clock, Logging] =
    (LoggingLive(_, _)).toLayer[Logging]
}
```

Note that the previous step is syntactic sugar of writing the layer directly in combination with for-comprehension style of accessing the ZIO environment:

```scala
object LoggingLive {
  val layer: ZLayer[Clock & Console, Nothing, Logging] =
    ZLayer {
      for {
        console <- ZIO.service[Console]
        clock   <- ZIO.service[Clock]
      } yield LoggingLive(console, clock)
    }
}
```

5. **Accessor Methods** — Finally, to create the API more ergonomic, it's better to write accessor methods for all of our service methods using `ZIO.serviceWithZIO` constructor inside the companion object:

```scala mdoc:silent
object Logging {
  def log(line: String): URIO[Logging, Unit] = ZIO.serviceWithZIO[Logging](_.log(line))
}
```

Accessor methods allow us to utilize all the features inside the service through the ZIO Environment. That means, if we call `Logging.log`, we don't need to pull out the `log` function from the ZIO Environment. The `ZIO.serviceWithZIO` constructor helps us to access the environment and reduce the redundant operations, every time.

This is how ZIO services are created. Let's use the `Logging` service in our application. We should provide the live layer of `Logging` service to be able to run the application:

```scala mdoc:compile-only
import zio._
import java.io.IOException

object MainApp extends ZIOAppDefault {
  val app: ZIO[Logging & Console, IOException, Unit] =
    for {
      _    <- Logging.log("Application Started!")
      _    <- Console.print("Enter your name:")
      name <- Console.readLine
      _    <- Console.printLine(s"Hello, $name!")
      _    <- Logging.log("Application Exited!")
    } yield ()

  def run = app.provideCustom(LoggingLive.layer)
}
```

During writing the application, we don't care which implementation version of the `Logging` service will be injected into our `app`, later at the end of the day, it will be provided by one of `ZIO#provide*` methods.

That's it! Very simple! ZIO encourages us to follow some of the best practices in object-oriented programming. So it doesn't require us to throw away all our object-oriented knowledge.

### Macros for Generating Accessor Methods

Writing accessor methods is a repetitive task and would be cumbersome in services with many methods. We can automate the generation of accessor methods using `zio-macro` module. 

To install the `zio-macro` we should add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-macros" % "<zio-version>"
```

Also, to enable macro expansion we need to setup our project:

  - for Scala `>= 2.13` add compiler option:

  ```scala
  scalacOptions += "-Ymacro-annotations"
  ```

  - for Scala `< 2.13` add macro paradise compiler plugin:

  ```scala
  compilerPlugin(("org.scalamacros" % "paradise"  % "2.1.1") cross CrossVersion.full)
  ```

> **Note:**
> 
> At the moment these are only available for Scala versions `2.x`, however their equivalents for Scala 3 are on our roadmap.

Now we can use the `@accessible` macro to generate _capability accessors_:

```scala
import zio._
import zio.macros.accessible

@accessible
trait ServiceA {
  def method(input: Something): UIO[Unit]
}

// below will be autogenerated
object ServiceA {
  def method(input: Something) =
    ZIO.serviceWithZIO[ServiceA](_.method(event))
}
```

For normal values, a `ZIO` with `Nothing` on error channel is generated:

```scala
import zio._
import zio.macros.accessible

@accessible
trait ServiceB {
  def pureMethod(input: Something): SomethingElse
}

// below will be autogenerated
object ServiceB {
  def pureMethod(input: Something): ZIO[ServiceB, Nothing, SomethingElse] =
    ZIO.serviceWith[ServiceB](_.pureMethod(input))
}
```

The `@throwing` annotation will mark impure methods. Using this annotation will request ZIO to push the error on the error channel:

```scala
import zio._
import zio.macros.accessible
import zio.macros.throwing

@accessible
trait ServiceC {
  @throwing
  def impureMethod(input: Something): SomethingElse
}

// below will be autogenerated
object ServiceC {
  def impureMethod(input: Something): ZIO[ServiceC, Throwable, SomethingElse] =
    ZIO.serviceWithZIO[ServiceC](s => ZIO(s.impureMethod(input)))
}
```

As we’ve already mentioned, currently we have no macro support for Scala 3, instead we provide the `Accessible` trait that is a macro-less means of creating accessors from services. We can simply extend the companion object with `Accessible[ServiceName]` and then call `Companion(_.someMethod)` to return a ZIO effect that requires the service in its environment:

```scala mdoc:compile-only
import zio._

trait ServiceD {
  def method(input: Int): Task[String]
  def anotherMethod: UIO[Int]
}

object ServiceD extends Accessible[ServiceD]

val myApp: ZIO[ServiceD, Throwable, (String, Int)] =
  for {
    s <- ServiceD(_.method(3))
    i <- ServiceD(_.anotherMethod)
  } yield (s, i)
```

### The Three Laws of ZIO Environment

When we are working with the ZIO environment, one question might arise: "When should we use environment and when do we need to use constructors?".

Using ZIO environment follows three laws:

1. **Service Interface (Trait)** — When we are defining service interfaces we should _never_ use the environment for dependencies of the service itself.

For example, if the implementation of service `X` depends on service `Y` and `Z` then these should never be reflected in the trait that defines service `X`. It's leaking implementation details.

So the following service definition is wrong because the `Console` and `Clock` service are dependencies of the  `Logging` service's implementation, not the `Logging` interface itself:

```scala mdoc:compile-only
import zio._
trait Logging {
  def log(line: String): ZIO[Console & Clock, Nothing, Unit]
}
```

2. **Service Implementation (Class)** — When implementing service interfaces, we should accept all dependencies in the class constructor.

Again, let's see how `LoggingLive` accepts `Console` and `Clock` dependencies from the class constructor:

```scala mdoc:compile-only
case class LoggingLive(console: Console, clock: Clock) extends Logging {
  override def log(line: String): UIO[Unit] =
    for {
      current <- clock.currentDateTime
      _       <- console.printLine(s"$current--$line").orDie
    } yield ()
}
```

So keep in mind, we can't do something like this:

```scala mdoc:fail
case class LoggingLive() extends Logging {
  override def log(line: String) =
    for {
      clock   <- ZIO.service[Clock]
      console <- ZIO.service[Console]
      current <- clock.currentDateTime
      _       <- console.printLine(s"$current--$line").orDie
    } yield ()
}
```

3. **Business Logic** — Finally, in the business logic we should use the ZIO environment to consume services.

Therefore, in the last example, if we inline all accessor methods whenever we are using services, we are using the ZIO environment:

```scala mdoc:compile-only
import zio._
import java.io.IOException

object MainApp extends ZIOAppDefault {
  val app: ZIO[Logging & Console, IOException, Unit] =
    for {
      _    <- ZIO.serviceWithZIO[Logging](_.log("Application Started!"))
      _    <- ZIO.serviceWithZIO[Console](_.print("Enter your name: "))
      name <- ZIO.serviceWithZIO[Console](_.readLine)
      _    <- ZIO.serviceWithZIO[Console](_.printLine(s"Hello, $name!"))
      _    <- ZIO.serviceWithZIO[Logging](_.log("Application Exited!"))
    } yield ()

  def run = app.provideCustom(LoggingLive.layer)
}
```

That's it! These are the most important rules we need to know about the ZIO environment.

> **Note**:
> 
> The remaining part of this section can be skipped if you are not an advanced ZIO user.

Now let's elaborate more on the first rule. On rare occasions, all of which involve local context that is independent of implementation, it's _acceptable_ to use the environment in the definition of a service.

Here are two examples:

1. In a web application, a service may be defined only to operate in the context of an HTTP request. In such a case, the request itself could be stored in the environment: `ZIO[HttpRequest, ...]`. This is acceptable because this use of the environment is part of the semantics of the trait itself, rather than leaking an implementation detail of some particular class that implements the service trait:

```scala mdoc:compile-only
import zio._
import zio.stream._
import java.net.URI
import java.nio.charset.StandardCharsets

type HttpApp = ZIO[HttpRequest, Throwable, HttpResponse]
type HttpRoute = Map[String, HttpApp]

case class HttpRequest(method: Int,
                       uri: URI,
                       headers: Map[String, String],
                       body: UStream[Byte])

case class HttpResponse(status: Int,
                        headers: Map[String, String],
                        body: UStream[Byte])

object HttpResponse {
  def apply(status: Int, message: String): HttpResponse =
    HttpResponse(
      status = status,
      headers = Map.empty,
      body = ZStream.fromChunk(
        Chunk.fromArray(message.getBytes(StandardCharsets.UTF_8))
      )
    )

  def ok(msg: String): HttpResponse = HttpResponse(200, msg)

  def error(msg: String): HttpResponse = HttpResponse(800, msg)
}

trait HttpServer {
  def serve(map: HttpRoute, host: String, port: Int): ZIO[Any, Throwable, Unit]
}

object HttpServer {
  def serve(map: HttpRoute, host: String, port: Int): ZIO[HttpServer, Throwable, Unit] =
    ZIO.serviceWithZIO(_.serve(map, host, port))
}

case class HttpServerLive() extends HttpServer {
  override def serve(map: HttpRoute, host: String, port: Int): ZIO[Any, Throwable, Unit] = ???
}

object HttpServerLive {
  val layer: URLayer[Any, HttpServer] = (HttpServerLive.apply _).toLayer[HttpServer]
}

object MainWebApp extends ZIOAppDefault {

  val myApp: ZIO[HttpServer, Throwable, Unit] = for {
    _ <- ZIO.unit
    healthcheck: HttpApp = ZIO.service[HttpRequest].map { _ =>
      HttpResponse.ok("up")
    }

    pingpong = ZIO.service[HttpRequest].flatMap { req =>
      ZIO.ifZIO(
        req.body.via(ZPipeline.utf8Decode).runHead.map(_.contains("ping"))
      )(
        onTrue = ZIO(HttpResponse.ok("pong")),
        onFalse = ZIO(HttpResponse.error("bad request"))
      )
    }

    map = Map(
      "/healthcheck" -> healthcheck,
      "/pingpong" -> pingpong
    )
    _ <- HttpServer.serve(map, "localhost", 8080)
  } yield ()

  def run = myApp.provideCustomLayer(HttpServerLive.layer)

}
```

2. In a database application, a service may be defined only to operate in the context of a larger database transaction. In such a case, the transaction could be stored in the environment: `ZIO[DatabaseTransaction, ...]`. As in the previous example, because this is part of the semantics of the trait itself (whose functionality all operates within a transaction), this is not leaking implementation details, and therefore it is valid:

```scala mdoc:compile-only
trait DatabaseTransaction {
  def get(key: String): Task[Int]
  def put(key: String, value: Int): Task[Unit]
}

object DatabaseTransaction {
  def get(key: String): ZIO[DatabaseTransaction, Throwable, Int] =
    ZIO.serviceWithZIO(_.get(key))

  def put(key: String, value: Int): ZIO[DatabaseTransaction, Throwable, Unit] =
    ZIO.serviceWithZIO(_.put(key, value))
}

trait Database {
  def atomically[E, A](zio: ZIO[DatabaseTransaction, E, A]): ZIO[Any, E, A]
}

object Database {
  def atomically[E, A](zio: ZIO[DatabaseTransaction, E, A]): ZIO[Database, E, A] =
    ZIO.serviceWithZIO(_.atomically(zio))
}

case class DatabaseLive() extends Database {
  override def atomically[E, A](zio: ZIO[DatabaseTransaction, E, A]): ZIO[Any, E, A] = ???
}

object DatabaseLive {
  val layer = (DatabaseLive.apply _).toLayer[Database]
}

object MainDatabaseApp extends ZIOAppDefault {
  val myApp: ZIO[Database, Throwable, Unit] =
    for {
      _ <- Database.atomically(DatabaseTransaction.put("counter", 0))
      _ <- ZIO.foreachPar(List(1 to 10)) { _ =>
        Database.atomically(
          for {
            value <- DatabaseTransaction.get("counter")
            _ <- DatabaseTransaction.put("counter", value + 1)
          } yield ()
        )
      }
    } yield ()

  def run = myApp.provideCustomLayer(DatabaseLive.layer)

}
```

So while it's better to err on the side of "don't put things into the environment of service interface", there are cases where it's acceptable.
