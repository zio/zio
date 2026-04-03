---
id: examples
title: "Examples"
---

## An Example of a ZIO Application with Multiple Config Layers

In the following example, we have an application that requires `AppConfig` layer, which itself requires `DBConfig` and `ServerConfig` layers:

```scala mdoc:compile-only
import zio._

case class ServerConfig(host: String, port: Int)
object ServerConfig {
  val layer: ULayer[ServerConfig] =
    ZLayer.succeed(ServerConfig("localhost", 8080))
}

case class DBConfig(name: String)
object DBConfig {
  val layer: ULayer[DBConfig] =
    ZLayer.succeed(DBConfig("my-test-db"))
}

case class AppConfig(db: DBConfig, serverConfig: ServerConfig)
object AppConfig {
  val layer: ZLayer[DBConfig with ServerConfig, Nothing, AppConfig] =
    ZLayer {
      for {
        db     <- ZIO.service[DBConfig]
        server <- ZIO.service[ServerConfig]
      } yield AppConfig(db, server)
    }
}

object MainApp extends ZIOAppDefault {
  val myApp =
    for {
      c <- ZIO.service[AppConfig]
      _ <- ZIO.debug(s"Application started with config: ${c}")
    } yield ()

  def run = myApp.provide(AppConfig.layer, DBConfig.layer, ServerConfig.layer)
}
```

## An Example of Manually Generating a Dependency Graph

Suppose we have defined the `UserRepo`, `DocumentRepo`, `Database`, `BlobStorage`, and `Cache` services and their respective implementations as follows:

```scala mdoc:silent
import zio._

case class User(email: String, name: String)

trait UserRepo {
  def save(user: User): Task[Unit]

  def get(email: String): Task[User]
}

object UserRepo {
  def save(user: User): ZIO[UserRepo, Throwable, Unit] =
    ZIO.serviceWithZIO(_.save(user))

  def get(email: String): ZIO[UserRepo, Throwable, User] =
    ZIO.serviceWithZIO(_.get(email))
}

case class UserRepoLive(cache: Cache, database: Database) extends UserRepo {
  override def save(user: User): Task[Unit] = ???

  override def get(email: String): Task[User] = ???
}

object UserRepoLive {
  val layer: URLayer[Cache & Database, UserRepo] =
    ZLayer {
      for {
        cache    <- ZIO.service[Cache]
        database <- ZIO.service[Database]
      } yield UserRepoLive(cache, database)
    }
}

trait Database

case class DatabaseLive() extends Database

object DatabaseLive {
  val layer: ZLayer[Any, Nothing, Database] =
    ZLayer.succeed(DatabaseLive())
}

trait Cache {
  def save(key: String, value: Array[Byte]): Task[Unit]

  def get(key: String): Task[Array[Byte]]

  def remove(key: String): Task[Unit]
}

class InmemeoryCache() extends Cache {
  override def save(key: String, value: Array[Byte]): Task[Unit] = ???

  override def get(key: String): Task[Array[Byte]] = ???

  override def remove(key: String): Task[Unit] = ???
}

object InmemoryCache {
  val layer: ZLayer[Any, Throwable, Cache] =
    ZLayer(ZIO.attempt(new InmemeoryCache).debug("initialized"))
}

class PersistentCache() extends Cache {
  override def save(key: String, value: Array[Byte]): Task[Unit] = ???

  override def get(key: String): Task[Array[Byte]] = ???

  override def remove(key: String): Task[Unit] = ???
}

object PersistentCache {
  val layer: ZLayer[Any, Throwable, Cache] =
    ZLayer(ZIO.attempt(new PersistentCache).debug("initialized"))
}

case class Document(title: String, author: String, body: String)

trait DocumentRepo {
  def save(document: Document): Task[Unit]

  def get(id: String): Task[Document]
}

object DocumentRepo {
  def save(document: Document): ZIO[DocumentRepo, Throwable, Unit] =
    ZIO.serviceWithZIO(_.save(document))

  def get(id: String): ZIO[DocumentRepo, Throwable, Document] =
    ZIO.serviceWithZIO(_.get(id))
}

case class DocumentRepoLive(cache: Cache, blobStorage: BlobStorage) extends DocumentRepo {
  override def save(document: Document): Task[Unit] = ???

  override def get(id: String): Task[Document] = ???
}

object DocumentRepoLive {
  val layer: ZLayer[Cache & BlobStorage, Nothing, DocumentRepo] =
    ZLayer {
      for {
        cache       <- ZIO.service[Cache]
        blobStorage <- ZIO.service[BlobStorage]
      } yield DocumentRepoLive(cache, blobStorage)
    }
}

trait BlobStorage {
  def store(key: String, value: Array[Byte]): Task[Unit]
}

case class BlobStorageLive() extends BlobStorage {
  override def store(key: String, value: Array[Byte]): Task[Unit] = ???
}

object BlobStorageLive {
  val layer: URLayer[Any, BlobStorage] =
    ZLayer.succeed(BlobStorageLive())
}
```

And then assume we have the following ZIO application:

```scala mdoc:silent
import zio._

def myApp: ZIO[DocumentRepo & UserRepo, Throwable, Unit] =
  for {
    _ <- UserRepo.save(User("john@doe", "john"))
    _ <- DocumentRepo.save(Document("introduction to zio", "john", ""))
    _ <- UserRepo.get("john@doe").debug("retrieved john@doe user")
    _ <- DocumentRepo.get("introduction to zio").debug("retrieved article about zio")
  } yield ()
```

The `myApp` requires `DocumentRepo` and `UserRepo` services to run. So we need to create a `ZLayer` which requires no services and produces `DocumentRepo` and `UserRepo`. We can manually create this layer using [vertical and horizontal layer composition](manual-layer-construction.md#vertical-and-horizontal-composition):

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  val layers: ZLayer[Any, Any, DocumentRepo with UserRepo] =
    (BlobStorageLive.layer ++ InmemoryCache.layer ++ DatabaseLive.layer) >>>
      (DocumentRepoLive.layer >+> UserRepoLive.layer)

  def run = myApp.provideLayer(layers)
}
```

## An Example of Automatically Generating a Dependency Graph

Instead of creating the required layer manually, we can use the `ZIO#provide`. ZIO internally creates the dependency graph automatically based on all dependencies provided:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  def run =
    myApp.provide(
      InmemoryCache.layer,
      DatabaseLive.layer,
      UserRepoLive.layer,
      BlobStorageLive.layer,
      DocumentRepoLive.layer
    )
    
}
```

## An Example of Providing Different Implementations of the Same Service

Let's say we want to provide different versions of the same service to different services. In this example, both `UserRepo` and `DocumentRepo` services require the `Cache` service. However, we want to provide different cache implementations for these two services. Our goal is to provide an `InmemoryCache` layer for `UserRepo` and a `PersistentCache` layer for the `DocumentRepo` service:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  val layers: ZLayer[Any, Throwable, UserRepo with DocumentRepo] =
    ((InmemoryCache.layer ++ DatabaseLive.layer) >>> UserRepoLive.layer) ++
      ((PersistentCache.layer ++ BlobStorageLive.layer) >>> DocumentRepoLive.layer)

  def run = myApp.provideLayer(layers)
}

// Output:
// initialized: zio.examples.PersistentCache@6e899128
// initialized: zio.examples.InmemeoryCache@852e20a
```

## An Example of How to Get Fresh Layers

Having covered the topic of [acquiring fresh layers](../../reference/di/dependency-memoization.md#acquiring-a-fresh-version), let's see an example of using the `ZLayer#fresh` operator.

`DocumentRepo` and `UserRepo` services are dependent on an in-memory cache service. On the other hand, let's assume the cache service is quite simple, and we might be prone to cache conflicts between services. While sharing the cache service may cause some problems for our business logic, we should separate the cache service for both `DocumentRepo` and `UserRepo`:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  val layers: ZLayer[Any, Throwable, UserRepo & DocumentRepo] =
    ((InmemoryCache.layer.fresh ++ DatabaseLive.layer) >>> UserRepoLive.layer) ++
      ((InmemoryCache.layer.fresh ++ BlobStorageLive.layer) >>> DocumentRepoLive.layer)

  def run = myApp.provideLayer(layers)
}

// Output:
// initialized: zio.examples.InmemoryCache@13c9672b
// initialized: zio.examples.InmemoryCache@26d79027
```

## An Example of Pass-through Dependencies

Notice that in the previous examples, both `UserRepo` and `DocuemntRepo` have some [hidden dependencies](../../reference/di/manual-layer-construction.md#hidden-versus-passed-through-dependencies), such as `Cache`, `Database`, and `BlobStorage`.  So these hidden dependencies are no longer expressed in the type signature of the `layers`. From the perspective of a caller, `layers` just outputs a `UserRepo` and `DocuemntRepo` and requires no inputs. The caller does not need to be concerned with the internal implementation details of how the `UserRepo` and `DocumentRepo` are constructed.

An upstream dependency that is used by many other services can be "passed-through" and included in a layer's output. This can be done with the `>+>` operator, which provides the output of one layer to another layer, returning a new layer that outputs the services of _both_.

The following example shows how to passthrough all dependencies to the final layer:

```scala mdoc:compile-only

import zio._

object MainApp extends ZIOAppDefault {

  // passthrough all dependencies
  val layers: ZLayer[Any, Throwable, Database & BlobStorage & Cache & DocumentRepo & UserRepo] =
    DatabaseLive.layer >+>
      BlobStorageLive.layer >+>
      InmemoryCache.layer >+>
      DocumentRepoLive.layer >+>
      UserRepoLive.layer

  // providing all passthrough dependencies to the ZIO application
  def run = myApp.provideLayer(layers)
}
```

## An Example of Updating Hidden Dependencies

One of the use cases of having explicit all dependencies in the final layer is that we can [update](../../reference/di/examples.md#an-example-of-updating-hidden-dependencies) those hidden layers using `ZLayer#update`. In the following example, we are replacing the `InmemoryCache` with another implementation called `PersistentCache`:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  def myApp: ZIO[DocumentRepo & UserRepo, Nothing, Unit] =
    for {
      _ <- ZIO.service[UserRepo]
      _ <- ZIO.service[DocumentRepo]
    } yield ()

  val layers: ZLayer[Any, Throwable, Database & BlobStorage & Cache & DocumentRepo & UserRepo] =
    DatabaseLive.layer >+>
      BlobStorageLive.layer >+>
      InmemoryCache.layer >+>
      DocumentRepoLive.layer >+>
      UserRepoLive.layer

  def run =
    myApp.provideLayer(
      layers.update[Cache](_ => new PersistentCache)
    )
}
```

```scala mdoc:invisible:reset

```
