---
id: has
title: "Has"
---

The trait `Has[A]` is used with the ZIO environment to express an effect's dependency on a service of type `A`. 

For example,`RIO[Has[Console.Service], Unit]` is an effect that requires a `Console.Service` service.

## Overview
ZIO Wrap services with `Has` data type to:

1. **Combine** multiple services together. 
2. **Bind** services into their implementations. 

### Combining Services
Two or more `Has[_]` elements can be combined _horizontally_ using their `++` operator:


```scala
val logger: Has[Logging]   = Has(new Logging{})
val random: Has[RandomInt] = Has(new RandomInt{})

// Note the use of the infix `++` operator on `Has` to combine two `Has` elements:
val combined: Has[Logging] with Has[RandomInt] = logger ++ random 
```

### Binding Services

The extra power that is given by `Has` is that the resulting data structure is backed by an _heterogeneous map_. `Has` can be thought of as a `Map[K, V]` which keys are _service types_ and values are _service implementations_. from service type to service implementation, that collects each instance that is mixed in so that the instances can be accessed/extracted/modified individually, all while still guaranteeing supreme type safety.

ZIO internally can ask `combined` using `get` method to determine binding configurations:

```scala
// get back the Logging and RandomInt services from the combined values:
val logger: Logging   = combined.get[Logging]
val random: RandomInt = combined.get[RandomInt]
```

These are implementation details. Usually, we don't create a `Has` directly. Instead, we create a `Has` using `ZLayer`.

## Motivation
Some components in an application might depend upon more than one service, so we might need to combine multiple services and feed them to the ZIO Environment. Services cannot directly be combined, they can be combined if they first wrapped in the `Has` data type. 

Let's get into this problem and how the `Has` data type, solves this problem:

### Problem
ZIO environment has a `ZIO#provide` which takes an `R` and returns a `ZIO` effect which doesn't require `R` and ready to be run by the `unsafeRun` operation of `Runtime`.

Assume we have two `Logging` and `RandomInt` services:


```scala
trait Logging {
  def log(line: String): UIO[Unit]
}

trait RandomInt {
  def random: UIO[Int]
}
```

We also provided their accessors to their companion object. We just used `ZIO.accessM` to access environment of each service:

```scala
object Logging {
  def log(line: String): ZIO[Logging, Nothing, Unit] = ZIO.accessM[Logging](_.log(line))
}

object RandomInt {
  val random: ZIO[RandomInt, Nothing, Int] = ZIO.accessM[RandomInt](_.random)
}
```

Now, we are ready to write our application using these interfaces. We are going to write a simple program which generates a new random number and feed it into the logger:

```scala
val myApp: ZIO[Logging with RandomInt, Nothing, Unit] = 
  for {
    _       <- Logging.log("Application Started!")
    nextInt <- RandomInt.random
    -       <- Logging.log(s"Random number generated: ${nextInt.toString}")
  } yield ()
```

To run this program, we need to implement a live version of `Logging` and `RandomInt` services. So let's implement each of them:

```scala
val LoggingLive: Logging = new Logging {
  override def log(line: String): UIO[Unit] =
    ZIO.effectTotal(println(line))
}

val RandomIntLive: RandomInt = new RandomInt {
  override def random: UIO[Int] =
    ZIO.effectTotal(scala.util.Random.nextInt())
}
```

Great! Now, we are ready to inject these two dependencies into our application `myApp` through `ZIO.provide` function.  

```scala
lazy val mainApp = myApp.provide(???) //What to provide?
```

As the type of `myApp` effect is `ZIO[Logging with RandomInt, Nothing, Unit]`, we should provide an object with a type of `Logging with RandomInt`. Oh! How can we combine `LoggingLive` and `RandomIntLive` objects together? Unfortunately, we don't have a way to combine these two objects to create a required service (`Logging with RandomInt`).

But, there is a workaround, we can throw away these implementations and write a new implementation for an intersection of these two services:

```scala
val LoggingWithRandomIntLive = new Logging with RandomInt {
  override def log(line: String): UIO[Unit] =
    ZIO.effectTotal(println(line))

  override def random: UIO[Int] =
    ZIO.effectTotal(scala.util.Random.nextInt())
}
```

Now, we can provide this implementation into our application:

```scala
val mainApp: IO[Nothing, Unit] = myApp.provide(LoggingWithRandomIntLive)
```

The `mainApp` doesn't need any environmental services and can be run by using the ZIO Runtime system:

```scala
Runtime.default.unsafeRun(mainApp)
```

But this workaround is not perfect, because every time we are writing an application, we need to provide a specific implementation for its requirement. This is overwhelming.

We need to implement each of each service separately and at the end of the day, combine them and provide that to our application. This is where the `Has[_]` wrapper data type comes into play.

### Solution

`Has[_]` data type enables us to combine different services and provide them to the ZIO Environment.  Let's solve the previous problem by using the `Has` wrapper.

First, we should change the accessor methods to return us an effect which requires services wrapped into the `Has` data type:

```scala
object Logging {
  def log(line: String): ZIO[Has[Logging], Nothing, Unit] =
    ZIO.serviceWith[Logging](_.log(line))
}

object RandomInt {
  val random: ZIO[Has[RandomInt], Nothing, Int] =
    ZIO.serviceWith[RandomInt](_.random)
}
```

`ZIO.serviceWith` is accessor method like `ZIO.accessM`, it accesses the specified service in the environment of effect, but it returns a ZIO effect which requires a service wrapped in `Has[_]` data type.

We should refactor our application to represent the correct types.

```scala
val myApp: ZIO[Has[Logging] with Has[RandomInt], Nothing, Unit] =
  for {
    _ <- Logging.log("Application Started!")
    nextInt <- RandomInt.random
    - <- Logging.log(s"Random number generated: ${nextInt.toString}")
  } yield ()
```

Now, our application is a ZIO effect which requires `Has[Logging] with Has[RandomInt]` services. Let's combine implementation of these two services using Has data type:

```scala
val combined: Has[Logging] with Has[RandomInt] = Has(LoggingLive) ++ Has(RandomIntLive)
```

Let's feed the combined services into our application:

```scala
val effect: IO[Nothing, Unit] = myApp.provide(combined)
zio.Runtime.default.unsafeRun(effect)
```

That is how the `Has` data type helps us to combine services. The previous example was just for demonstrating purposes, and we rarely create `Has` data type directly. Instead, we create a `Has` via `ZLayer`.

Whenever we lift a service value into `ZLayer` with the `ZLayer.succeed` constructor or  `toLayer`, ZIO will wrap our service with `Has` data type.

Let's implement `Logging` and `RandomInt` services:

```scala
case class LoggingLive() extends Logging {
  override def log(line: String): UIO[Unit] =
    ZIO.effectTotal(println(line))
}

case class RandomIntLive() extends RandomInt {
  override def random: UIO[Int] =
    ZIO.effectTotal(scala.util.Random.nextInt())
}
```

Now, we can lift these two implementations into the `ZLayer`. The `ZLayer` will wrap our services into the `Has[_]` data type:


```scala
object LoggingLive {
  val layer: URLayer[Any, Has[Logging]] =
    (LoggingLive.apply _).toLayer
}

object RandomIntLive {
  val layer: URLayer[Any, Has[RandomInt]] =
    (RandomIntLive.apply _).toLayer
}
```

Now, when we combine multiple layers together, these services will combined via `with` intersection type:

```scala
val myLayer: ZLayer[Any, Nothing, Has[Logging] with Has[RandomInt]] = 
    LoggingLive.layer ++ RandomIntLive.layer
```

Finally, when we provide our layer into the ZIO effect, ZIO can access the binding configuration and extract each service. ZIO does internally these pieces of wiring machinery, we don't care about the implementation detail:

```scala
val mainApp: ZIO[Any, Nothing, Unit] = myApp.provideLayer(myLayer) 
// mainApp: ZIO[Any, Nothing, Unit] = zio.ZIO$CheckInterrupt@4682e5d9
```

