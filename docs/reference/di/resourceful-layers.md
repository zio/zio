---
id: resourceful-layers
title: "Resourceful Services (Scoped Services)"
sidebar_label: "Resourceful Services"
---

Some components of our applications need to be scoped, meaning they undergo a resource acquisition phase before usage, and a resource release phase after usage (e.g. when the application shuts down). As we stated before, the construction of ZIO layers can be effectful and resourceful, this means they can be acquired and safely released when the services are done being utilized.

The `ZLayer` relies on the powerful `Scope` data type and this makes this process extremely simple. We can lift any scoped `ZIO` to `ZLayer` by providing a scoped resource to the `ZLayer.scoped` constructor:

```scala mdoc:silent:nest
import zio._
import scala.io.BufferedSource

val fileLayer: ZLayer[Any, Throwable, BufferedSource] =
  ZLayer.scoped {
    ZIO.fromAutoCloseable(
      ZIO.attempt(scala.io.Source.fromFile("file.txt"))
    )
  }
```

Let's see a real-world example of creating a layer from scoped resources. Assume we have the following `UserRepository` service:

```scala mdoc:silent
import zio._
import scala.io.Source._
import java.io.{FileInputStream, FileOutputStream, Closeable}

trait DBConfig
trait Transactor
trait User

def dbConfig: Task[DBConfig] = ZIO.attempt(???)
def initializeDb(config: DBConfig): Task[Unit] = ZIO.attempt(???)
def makeTransactor(config: DBConfig): ZIO[Scope, Throwable, Transactor] = ZIO.attempt(???)

trait UserRepository {
  def save(user: User): Task[Unit]
}

case class UserRepositoryLive(xa: Transactor) extends UserRepository {
  override def save(user: User): Task[Unit] = ZIO.attempt(???)
}
```

Assume we have written a scoped `UserRepository`:

```scala mdoc:silent:nest
def scoped: ZIO[Scope, Throwable, UserRepository] = 
  for {
    cfg <- dbConfig
    _   <- initializeDb(cfg)
    xa  <- makeTransactor(cfg)
  } yield new UserRepositoryLive(xa)
```

We can convert that to `ZLayer` with `ZLayer.scoped`:

```scala mdoc:nest
val usersLayer : ZLayer[Any, Throwable, UserRepository] =
  ZLayer.scoped(scoped)
```
