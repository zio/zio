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

This article explores two methods for implementing reloadable services in ZIO:

1. The first method is a non-trivial method that uses the `Reloadable` service, which requires some boilerplate code.
2. The second method is a simpler method introduced by `zio-macros` that uses the `ServiceReloader` service.

Before going into further detail, through this article whenever we use `Counter` class, we refere to this source code:

```scala mdoc:silent
import zio._

import java.util.UUID

trait Counter {
  def increment: UIO[Unit]
  def get: UIO[Int]
}

object Counter {
  val increment: ZIO[Counter, Nothing, Unit] =
    ZIO.serviceWithZIO[Counter](_.increment)

  val get: ZIO[Counter, Nothing, RuntimeFlags] =
    ZIO.serviceWithZIO[Counter](_.get)

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

final case class CounterLive(id: Ref[UUID], ref: Ref[Int]) extends Counter {
  def acquire: UIO[Unit] = {
    Random.nextUUID
      .flatMap(n => id.set(n) *> ZIO.debug(s"Acquired counter $n"))
  }

  def increment: UIO[Unit] =
    ref.update(_ + 1)

  def get: UIO[Int] =
    ref.get

  def release: UIO[Unit] =
    id.get.flatMap(id => ZIO.debug(s"Released counter $id"))
}
```

## 1. The `Reloadable` Service

In line with the principles of typical ZIO services, reloadable services are specifically crafted to operate seamlessly within the ZIO environment. The `Reloadable[Service]` data type serves as a wrapper around any reloadable service. This data type encompasses two fundamental methods: `get` and `reload`. The `get` method facilitates the retrieval of the underlying service managed by the `ScopedRef`, while the `reload` method enables the reloading of the service.

### Reloadable Operations

Before diving into further details, let's begin by examining the definition of the `Reloadable` class:

```scala
case class Reloadable[Service](scopedRef: ScopedRef[Service], reload: IO[Any, Unit]) {
  def get: UIO[Service] = scopedRef.get
  def reloadFork: UIO[Unit] = reload.ignoreLogged.forkDaemon.unit
}
```

The `Reloadable` service encapsulates a [scoped reference](../resource/scopedref.md) to a service and provides methods to retrieve the service value (`get`) and trigger service reloading (`reload` and `reloadFork`).

The two fundamental operations of `Reloadable` are as follows:

1. `Reloadable#get` - By calling the `get` method, we can retrieve the underlying service and interact with it directly.

    For example, let's consider that we have acquired the `Reloadable[Counter]` service from the ZIO environment using the `ZIO.service[Reloadable[Counter]]` accessor. We can then use the `get` method to obtain the Counter instance and directly perform operations on it:

```scala mdoc:compile-only
import zio._

val app: ZIO[Reloadable[Counter], Nothing, Unit] =
  for {
    reloadable <- ZIO.service[Reloadable[Counter]]
    counter    <- reloadable.get 
  } yield ()
```

2. **`Reloadable#reload`**— This operation involves acquiring a new service and releasing the old one, thereby enabling the reloading of the service:

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

Up to this point, we have explored the process of acquiring reloadable services from the ZIO environment and interacting with them. However, these workflows cannot be executed without fulfilling their requirements. For instance, in the previous example, the type of our workflow is `ZIO[Reloadable[Counter], Any, Unit]`. This implies that we need to provide a layer of type `Reloadable[Counter]`. It is necessary to create reloadable services and `provide` them as a `ZLayer`. In this section, we will delve into the creation of such services.

First, let's explore the definition of the two primary constructors for Reloadable:

```scala
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

There are two fundamental approaches to creating reloadable services:

1. **`Reloadable.manual`** - Using the manual constructor, we can create a reloadable service and subsequently reload it whenever needed by explicitly invoking the `Reloadable#reload` method. This method accepts a layer of type `ZLayer[In, E, Out]` and returns a layer of type `ZLayer[In, E, Reloadable[Out]]`.

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

Alternatively, we can directly utilize the `ZLayer#reloadableManual` method:

```scala mdoc:silent:nest
object Counter {
  val live: ZLayer[Any, Nothing, Counter] = ???

  val reloadable: ZLayer[Any, Nothing, Reloadable[Counter]] =
    live.reloadableManual
}
```

Now we can `provide` the `Counter.reloadable` layer to the app workflow and execute the application:

```scala mdoc:compile-only
import zio._

import java.util.UUID

object ReloadableExample extends ZIOAppDefault {
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

This program defines an application that operates on a reloadable counter service (`Reloadable[Counter]`). It obtains the `Counter` service from the reloadable service, performs three `increment` operations, displays its current value, proceeds to `reload` the counter service, performs two additional increment operations, and finally displays its value once again. Therefore, the expected output would be as follows:

```bash
Acquired counter d04519a3-7332-43ca-bc86-f61fbaf2e3d6
Counter value: 3
Released counter d04519a3-7332-43ca-bc86-f61fbaf2e3d6
Acquired counter bc66ba00-0b50-4e6e-9f60-c38b6e140a82
Counter value: 2
Released counter bc66ba00-0b50-4e6e-9f60-c38b6e140a82
```

Observing the behavior, we notice that the service undergoes reloading, causing the counter to reset and begin incrementing from zero once more.

2. **`Reloadable.auto`** - By utilizing this constructor, we can provide a schedule alongside a layer of type `ZLayer[In, E, Out]`, resulting in a layer of reloadable service that will be automatically reloaded based on the specified schedule. Additionally, there is another constructor called `Reloadable.autoFromConfig` which can be used to extract the schedule from the ZIO environment.

Let's change the previous example to reload the Counter service automatically every 5 second. First we need to create auto reloadable service:

```scala mdoc:compile-only
import zio._

object Counter {
  val live: ZLayer[Any, Nothing, Counter] = ???
  
  val autoReloadable: ZLayer[Any, Nothing, Reloadable[Counter]] =
    Reloadable.auto(live, Schedule.fixed(5.seconds))
}
```

Or we can use `ZLayer#reloadableAuto` to convert a layer to auto reloadable service:

```scala mdoc:silent:nest
import zio._

object Counter {
  val live: ZLayer[Any, Nothing, Counter] = ???
  
  val autoReloadable: ZLayer[Any, Nothing, Reloadable[Counter]] =
    live.reloadableAuto(Schedule.fixed(5.seconds))
}
```

Finally, we don't require to manually execute `Reloadable#reload` and the service will be reloaded every 5 second:

```scala mdoc:compile-only
import zio._
import java.util.UUID

object AutoReloadableExample extends ZIOAppDefault {
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

## 2. The `ServiceReloader` Service

Please note that in the previous example, there was no need for manual service reloading. However, we still had to manually retrieve the reloadable service from the environment using `ZIO.service[Reloadable[Counter]]` and then access the `Counter` service from the reloadable counter service. This approach involves some boilerplate code and indirection. We aim to adopt an approach that eliminates the necessity of `Reloadable[Service]` from the environment and instead directly requires the `Service` from the environment.

To address this issue, the `ServiceReloader` has been developed as a solution. It is part of the `zio-macros` package. To utilize it, we need to include the following line in our `build.sbt` file:

```scala
libraryDependencies ++= Seq("dev.zio" %% "zio-macros" % "<version>")
```

The `ServiceReloader` serves as a registry for services, enabling dynamic reloading of services. By applying the `reloadable` operator on `ZLayer`, we can create a reloadable version of our service. Subsequently, we can simply invoke `ServiceLoader.reload` to initiate the reloading of the service. Let's explore the definition of the ServiceLoader trait:

```scala
trait ServiceReloader {
  def register[A: Tag: IsReloadable](serviceLayer: ZLayer[Any, Any, A]): IO[ServiceReloader.Error, A]
  def reload[A: Tag]: IO[ServiceReloader.Error, Unit]
}
```

For example, if we require a reloadable `Counter` service, we can simply invoke `ServiceReloader.reload[Counter]`. The resulting type will be `ZIO[ServiceReloader, ServiceReloader.Error, Unit]`. Consequently, instead of providing a ZLayer of type `Reloadable[Counter]`, we now need to provide a layer of type `ServiceReloader`. 

With this approach, there is no longer a need to retrieve `Reloadable[Counter]` from the ZIO environment, eliminating the requirement to access `Counter` from an instance of the `Reloadable[Counter]` class. Instead, we can work with services in a manner consistent with the idiomatic approach used for regular services. Thus, rather than calling `ZIO.serviceWithZIO[Reloadable[Counter]](_.get)`, we can conveniently use `ZIO.service[Counter]` to obtain the `Counter` service directly from the ZIO environment.

Let's see how we can rewrite the `Reloadable.manual` example with this approach:

```scala mdoc:nest:invisible
import zio._
import zio.macros._

object Counter {
  val increment: ZIO[Counter, Nothing, Unit] =
    ZIO.serviceWithZIO[Counter](_.increment)
    
  val get: ZIO[Counter, Nothing, RuntimeFlags] =
    ZIO.serviceWithZIO[Counter](_.get)

  val live: ZLayer[Any, Nothing, Counter] = ???

  val reloadable: ZLayer[ServiceReloader, ServiceReloader.Error, Counter] =
    live.reloadable
}
```

```scala mdoc:compile-only
import zio._
import zio.macros._

import java.util.UUID

object ServiceReloaderExample extends ZIOAppDefault {

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

To create a reloadable layer, we need to import `zio.macros._`. Subsequently, by invoking the `ZLayer#reloadable` method, we can transform the `live` layer into a layer that depends on `ServiceReloader` and provides `Counter` services:

```scala mdoc:compile-only
import zio._
import zio.macros._

object Counter {

  val live: ZLayer[Any, Nothing, Counter] = ???

  val reloadable: ZLayer[ServiceReloader, ServiceReloader.Error, Counter] =
    live.reloadable
}
```

We can further enhance this application by decoupling the reload process from the application logic. In doing so, each time the service is reloaded, subsequent calls to the service will be served with the freshly reloaded services:

```scala mdoc:compile-only
import zio._

object ServiceReloaderParallelWorkflowExample extends ZIOAppDefault {
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

## Conclusion

Int this article we introduced two methods for implementing reloadable services in ZIO. The first method involves using the `Reloadable` service, which requires some boilerplate code. With this approach, services can be manually reloaded using the reload method. The second method, introduced by `zio-macros`, simplifies the process by utilizing the `ServiceReloader` service. This approach eliminates the need for retrieving the reloadable service from the environment and allows direct access to the service.

Overall, reloadable services in ZIO offer a powerful tool for managing services that require reloading, enabling seamless integration within the ZIO environment and simplifying service management in complex applications.

All the source code associated with this article is available on the [ZIO Quickstart](https://github.com/zio/zio-quickstarts/tree/master/zio-quickstart-reloadable-services) on Github.
