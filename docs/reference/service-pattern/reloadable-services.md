---
id: reloadable-services
title: "Introduction to Reloadable Services"
sidebar_label: "Reloadable Services"

---

Reloadable services are a feature in ZIO that allow us to reload services when necessary. With ZIO When we reload a service, it will automatically deallocate any resources that the service was using. This includes any open files, network connections, or database connections. ZIO will then reallocate new resources for the new service. This process of deallocation and reallocation is handled automatically by ZIO, so you don't need to worry about it. 

Here are some examples of how we might use reloadable services:

- **Config changes**: If we make a change to the configuration of a service, we might want to reload the service so that it can pick up the new configuration.
- **Scheduled reloads**: We might want to reload a service on a regular interval, such as every n minutes. This can be useful for services that need to be refreshed on a regular basis.
- **Reload on change on Schema**: Assume we have a service that reads data from a database. If we make a change to the database schema, we might want to reload the service so that it can pick up the new schema. 

In this article we will discuss two approaches to implement reloadable services in ZIO:
- **Using `Reloadable` service** 
- **Using `ServiceReloader` service (the macro approach)**

## The `Reloadable` Service

Like ordinary ZIO services, reloadable services are designed to work with ZIO environment easily. The `Reloadable[Service]` data type, is a wrapper data type around any services that is going to be reloadable. This data type has two primary methods, `get` and `reload`, one for getting the underlying service managed by `ScopedRef` and the other is for reloading the service:

```scala
case class Reloadable[Service](scopedRef: ScopedRef[Service], reload: IO[Any, Unit]) {
  def get: UIO[Service] = scopedRef.get
  def reloadFork: UIO[Unit] = reload.ignoreLogged.forkDaemon.unit
}

object Reloadable {
  def manual[In, E, Out](
      layer: ZLayer[In, E, Out]
    ): ZLayer[In, E, Reloadable[Out]] = ???
    
  def auto[In, E, Out](
      layer: ZLayer[In, E, Out],
      schedule: Schedule[In, Any, Any]
    ): ZLayer[In, E, Reloadable[Out]] = ???
}
```

### Reloadable Operations

The basic two operation of `Reloadable`:

1. **`Reloadable#get`**— By calling `get` we can access the underlying service, and then we can directly work with. Assume we have got the `Reloadable[Counter]` service from the ZIO environment using `ZIO.service[Reloadable[Counter]]` accessor, then we can call `get` to access the `Counter` and working with that directly:

```scala mdoc:compile-only
import zio._

val app = ZIO[Reloadable[Counter], Nothing, Unit]
  for {
    reloadable <- ZIO.service[Reloadable[Counter]]
    counter    <- reloadable.get 
  } yield ()
```

2. **`Reloadable#reload`**— This will acquire a new service and release the old one, so the service will be reloaded.

```scala mdoc:compile-only
import zio._

val app: ZIO[Reloadable[Counter], Any, Unit] =
  for {
    reloadable <- ZIO.service[Reloadable[Counter]]
    counter    <- reloadable.get
    _          <- counter.increment
    _          <- counter.increment
    _          <- counter.increment
    _          <- counter.get.debug("Counter value is")
    _          <- reloadable.reload
    counter    <- reloadable.get
    _          <- counter.increment
    _          <- counter.increment
    _          <- counter.get.debug("Counter value is")
  } yield ()
```


### Creating Reloadable Services

So far we learned how to obtain reloadable service from ZIO environment and play with them. But, these workflows are not executable until we provide their requirements. For example, in the previous example, the type our workflow is `ZIO[Reloadable[Counter], Any, Unit]`. This means, we need to provide a layer of type `Reloadable[Counter]`. We have to create reloadable service and provide it as ZLayer. In this section we are going to learn on how to create such services.

We have two basic way to create reloadable services, manual and automatic way.

1. **`Reloadable.manual`**— With manual constructor, we can create a reloadable service and then anytime we need to reload the service, we can manually call `Reloadable#reload` method. This method takes a layer of type `ZLayer[In, E, Out]` and return a layer of type `ZLayer[In, E, Reloadable[Out]]`.

Continuing the previous example, assume we have written the `Counter.live` as follows:

```scala mdoc:compile-only
import zio._

object Counter {
  val live: ZLayer[Any, Nothing, Counter] = ZLayer.scoped {
    for {
      id <- Ref.make(UUID.randomUUID())
      ref <- Ref.make(0)
      service = CounterLive(id, ref)
      _ <- service.acquire
      _ <- ZIO.addFinalizer(service.release)
    } yield service
  }
}
```

We can easily convert that to reloadable layer using `Reloadable.manual`:

```scala mdoc:compile-only
object Counter {
  val live: ZLayer[Any, Nothing, Counter] = ???
  
  val reloadable: ZLayer[Any, Nothing, Reloadable[Counter]] = 
    Reloadable.manual(live)
}
```

Or we can directly call `ZLayer#reloadable`:

```scala mdoc:compile-only
object Counter {
  val live: ZLayer[Any, Nothing, Counter] = ???

  val reloadable: ZLayer[Any, Nothing, Reloadable[Counter]] =
    live.reloadable 
}
```

Now can provide `Counter.reloadable` layer to the `app` workflow and finally run the application:

```scala mdoc:compile-only
import zio._

import java.util.UUID

object ReloadableCounterServiceApp extends ZIOAppDefault {
  val app: ZIO[Reloadable[Counter], Any, Unit] =
    for {
      reloadable <- ZIO.service[Reloadable[Counter]]
      counter <- reloadable.get
      _ <- counter.increment
      _ <- counter.increment
      _ <- counter.increment
      _ <- counter.get.debug("Counter value is")
      _ <- reloadable.reload *> ZIO.sleep(1.second)
      counter <- reloadable.get
      _ <- counter.increment
      _ <- counter.increment
      _ <- counter.get.debug("Counter value is")
    } yield ()

  def run = app.provide(Counter.reloadable)
}
```

This program defines an application that operates on a reloadable counter service. It retrieves the counter service from the reloadable, increments it three times, prints its value, reloads the counter service, then increments it twice, and then prints its value again. So the output should be this:

```bash
Acquired counter d04519a3-7332-43ca-bc86-f61fbaf2e3d6
Counter value: 3
Released counter d04519a3-7332-43ca-bc86-f61fbaf2e3d6
Acquired counter bc66ba00-0b50-4e6e-9f60-c38b6e140a82
Counter value: 2
Released counter bc66ba00-0b50-4e6e-9f60-c38b6e140a82
```

2. **`Reloadable.auto`**- Using this constructor, we can provide a schedule along with a layer of type `ZLayer[In, E, Out]`, it will return a layer of reloadable service which will be automatically reloaded according to the provided schedule. Also, we can use `Reloadable.autoFromConfig` to extract the schedule from the ZIO environment.

Let's change the previous example to reload the Counter service automatically every 5 second. First we need to create auto reloadable service:

```scala mdoc:compile-only
import zio._

object Counter {
  val autoReloadable: ZLayer[Any, Nothing, Reloadable[Counter]] =
    Reloadable.auto(live, Schedule.fixed(5.seconds))
}
```

Or we can use `ZLayer#reloadableAuto` to convert a layer to auto reloadable service:

```scala mdoc:compile-only
import zio._

object Counter {
  val autoReloadable: ZLayer[Any, Nothing, Reloadable[Counter]] =
    live.reloadableAuto(Schedule.fixed(5.seconds))
}
```

Finally, we don't require to manually execute `Reloadable#reload` and the service will be reloaded every 5 second:

```scala mdoc:compile-only
import zio._
import java.util.UUID

object ReloadableServiceExampleAuto extends ZIOAppDefault {
  val app: ZIO[Reloadable[Counter], Any, Unit] =
    for {
      reloadable <- ZIO.service[Reloadable[Counter]]
      counter <- reloadable.get
      _ <- counter.increment
      _ <- counter.increment
      _ <- counter.increment
      _ <- counter.get.debug("Counter value is")
      _ <- ZIO.sleep(6.second)
      counter <- reloadable.get // getting reloadable service from environment
      _ <- counter.increment
      _ <- counter.increment
      _ <- counter.get.debug("Counter value is")
    } yield ()

  def run = app.provide(Counter.autoReloadable)
}
```

## ServiceReloader

Note that in the above example, we do not require to reload service manually, but we still need to manually get the reloadable service from the environment, `ZIO.service[Reloadable[Counter]]` and then we have the ability to get `Counter` service from reloadable counter service. This approach has some boilerplate and indirection. We want an approach that doesn't require `Reloadable[Counter]` from the environment, instead we would like to have an approach that requires `Counter` from the environment.

`ServiceReloader` is developed to solve this problem. Its part of `zio-macros` package and to use that, we need to add the following line to our `build.sbt` file:

```scala
libraryDependencies ++= Seq("dev.zio" %% "zio-macros" % "<version>")
```

A `ServiceReloader` is a registry of services, allowing services to be reloaded dynamically. We can create a reloadable version of our service by using the `reloadable` operator on `ZLayer`. Then just call `ServiceLoader.reload` to reload the service.

```scala
trait ServiceReloader {
  def register[A: Tag: IsReloadable](serviceLayer: ZLayer[Any, Any, A]): IO[ServiceReloader.Error, A]
  def reload[A: Tag]: IO[ServiceReloader.Error, Unit]
}
```

For example, whenever we need reloadable `Counter` service, it is sufficed to call `ServiceReloader.reload[Counter]`. The return type will be `ZIO[ServiceReloader, ServiceReloader.Error, Unit]`. So instead of providing a ZLayer of type `Reloadable[Counter]` we need to provide a layer of type `ServiceReloader`. With this approach we no longer need to obtain `Reloadable[Counter]` from the ZIO environment, thus we no longer need to obtain `Counter` from an instance of `Reloadable[Counter]` class. Instead, we can work with services like the idiomatic approach we commonly use for ordinary services. So instead of calling `ZIO.serviceWithZIO[Reloadable[Counter]](_.get)` we can easily use `ZIO.service[Counter]` to obtain `Counter` service from the ZIO environment.

Let's see how we can rewrite the `Reloadable.manual` example with this approach:

```scala mdoc:compile-only
import zio._
import zio.macros._

import java.util.UUID

object ReloadableServiceExample extends ZIOAppDefault {

  def app: ZIO[Counter with ServiceReloader, ServiceReloader.Error, Unit] =
    for {
      _ <- Counter.increment
      _ <- Counter.increment
      _ <- Counter.increment
      _ <- Counter.get.debug("Counter value")

      _ <- ServiceReloader.reload[Counter]
      _ <- ZIO.sleep(1.seconds)

      _ <- Counter.increment
      _ <- Counter.increment
      _ <- Counter.get.debug("Counter value")
    } yield ()

  def run = app.provide(Counter.reloadable, ServiceReloader.live)
}
```

We can also make this app to the next level by separating the reload process from the app logic. So everytime the service reloaded, the next call to the service will be responded with newly reloaded services:

```scala mdoc:compile-only
import zio._

object ReloadableServiceExampleAnother extends ZIOAppDefault {
  def reloadWorkflow =
    ServiceReloader.reload[Counter].delay(5.seconds)

  def app: ZIO[Counter with ServiceReloader, ServiceReloader.Error, Unit] =
    for {
      _ <- Counter.increment
      _ <- Counter.increment
      _ <- Counter.increment
      _ <- Counter.get.debug("Counter value")

      _ <- ZIO.sleep(6.seconds)

      _ <- Counter.increment
      _ <- Counter.increment
      _ <- Counter.increment
      _ <- Counter.get.debug("Counter value")
    } yield ()

  def run = (app <&> reloadWorkflow).provide(Counter.reloadable, ServiceReloader.live)
}
```
