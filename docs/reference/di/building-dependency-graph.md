---
id: building-dependency-graph
title: "Building Dependency Graph"
---

We have two options to build a dependency graph:

1. [Manual layer construction](manual-layer-construction.md)— This method uses ZIO's composition operators such as horizontal (`++`) and vertical (`>>>`) compositions.
2. [Automatic layer construction](automatic-layer-construction.md)— It uses metaprogramming to automatically create the dependency graph at compile time.

Assume we have the following dependency graph with two top-level dependencies:

```
           DocRepo                ++          UserRepo
      ____/   |   \____                       /     \
     /        |        \                     /       \
 Logging  Database  BlobStorage          Logging   Database
                         |                  
                      Logging            
```

```scala mdoc:invisible:nest
import zio._

case class Logging() {
  // ...
}

object Logging {
  val live: URLayer[Any, Logging] =
    ZLayer.succeed(Logging())
}

case class Database() {
  // ...
}

object Database {
  val live: URLayer[Any, Database] =
    ZLayer.succeed(Database())
}

case class BlobStorage(logging: Logging) {
  // ...
}

object BlobStorage {
  val live: URLayer[Logging, BlobStorage] =
    ZLayer {
      for {
        logging <- ZIO.service[Logging]
      } yield BlobStorage(logging)
    }
}

case class UserRepo(logging: Logging, database: Database) {
  // ...
}

object UserRepo {
  val live: URLayer[Logging with Database, UserRepo] =
    ZLayer {
      for {
        logging  <- ZIO.service[Logging]
        database <- ZIO.service[Database]
      } yield UserRepo(logging, database)
    }
}

case class DocRepo(logging: Logging, database: Database, blobStorage: BlobStorage) {
  // ...
}

object DocRepo {
  val live: URLayer[Logging with Database with BlobStorage, DocRepo] =
    ZLayer {
      for {
        logging     <- ZIO.service[Logging]
        database    <- ZIO.service[Database]
        blobStorage <- ZIO.service[BlobStorage]
      } yield DocRepo(logging, database, blobStorage)
    }
}
```

Now, assume that we have written an application that finally needs two services: `DocRepo` and `UserRepo`:

```scala mdoc:silent
val myApp: ZIO[DocRepo with UserRepo, Throwable, Unit] = ZIO.attempt(???)
```

1. To create the dependency graph for this ZIO application manually, we can use the following code:

```scala mdoc:compile-only
val appLayer: URLayer[Any, DocRepo with UserRepo] =
  ((Logging.live ++ Database.live ++ (Logging.live >>> BlobStorage.live)) >>> DocRepo.live) ++
    ((Logging.live ++ Database.live) >>> UserRepo.live)
    
val res: ZIO[Any, Throwable, Unit] = myApp.provideLayer(appLayer)
```

2. As the development of our application progress, the number of layers will grow, and maintaining the dependency graph manually could be tedious and hard to debug. So, we can automatically construct dependencies with friendly compile-time hints, using `ZIO#provide` operator:

```scala mdoc:silent:nest
val res: ZIO[Any, Throwable, Unit] =
  myApp.provide(
    Logging.live,
    Database.live,
    BlobStorage.live,
    DocRepo.live,
    UserRepo.live
  )
```

The order of dependencies doesn't matter:

```scala mdoc:silent:nest
val res: ZIO[Any, Throwable, Unit] =
  myApp.provide(
    DocRepo.live,
    BlobStorage.live,
    Logging.live,
    Database.live,
    UserRepo.live
  )
```

If we miss some dependencies, it doesn't compile, and the compiler gives us the clue:

```scala mdoc:silent:fail
val app: ZIO[Any, Throwable, Unit] =
  myApp.provide(
    DocRepo.live,
    BlobStorage.live,
//    Logging.live,
    Database.live,
    UserRepo.live
  )
```

```
  ZLayer Wiring Error  

❯ missing Logging
❯     for DocRepo.live

❯ missing Logging
❯     for UserRepo.live
```

:::note
The `ZIO#provide` method, together with its variant `ZIO#provideSome`, is default and easier way of injecting dependencies to the environmental effect. We do not require creating the dependency graph manually, it will be automatically generated.

In contrast, the `ZIO#provideLayer`, and its variant `ZIO#provideSomeLayer`, is useful for low-level and custom cases.
:::

