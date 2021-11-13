---
id: hasmany
title: "HasMany"
---

The `HasMany[K, A]` data type is used with the ZIO environment to express an effect's dependency on multiple services of type `A` which are keyed by type `A`.

The `HasMany[K, A]` is a type alias for `Has[Map[K, A]]` data type:

```scala
type HasMany[K, A] = Has[Map[K, A]]
```

## Providing Multiple Instance of the Same Interface

In ordinary `Has[A]` data type, when we want to express dependencies of type `Console`, `Database` and `Logging`, we use
the `Has[Console] with Has[Database] with Has[Logging]` data type. This is convenient, where each type uniquely
identifies one service.

What about those cases where we need to express multiple services of the same type, e.g. `Database`? To deal with such
cases we can use the `HasMany` data type. So in these cases the environment type would be something
like `Has[Console] with HasMany[String, Database] with Has[Logging]`.

To access the specified service correspond to a key, we can use the `ZIO.serviceAt[Service](key)` constructor. For
example, to access a `Database` service which is specified by the "inmemory" key, we can write:

```scala mdoc:invisible
import zio._
trait Database
```

```scala mdoc:silent:nest
val database: URIO[HasMany[String, Database], Option[Database]] =
  ZIO.serviceAt[Database]("persistent")
```

A service can be updated at the specified key with the `ZIO#updateServiceAt` operator.

## Example

Here is an example of multiple instances of the `Database` interface that are provided to the ZIO environment:

```scala mdoc:compile-only
trait Database {
  def add(key: String, value: Array[Byte]): ZIO[Any, Throwable, Unit]

  def get(key: String): ZIO[Any, Throwable, Array[Byte]]
}

object Database {
  val serviceBuilder: URServiceBuilder[Has[Logging], HasMany[String, Database]] = { (logger: Logging) =>
    Map(
      "persistent" -> PersistentDatabase(logger),
      "inmemory" -> InmemoryDatabase(logger)
    )
  }.toServiceBuilder
}

trait Logging {
  def log(line: Any): ZIO[Any, Throwable, Unit]
}

case class ConsoleLogger(console: Console) extends Logging {
  override def log(line: Any): ZIO[Any, Throwable, Unit] =
    console.printLine(s"ConsoleLogger -- $line")
}

object ConsoleLogger {
  val serviceBuilder: URServiceBuilder[Has[Console], Has[Logging]] =
    (ConsoleLogger.apply _).toServiceBuilder[Logging]
}

case class InmemoryDatabase(logger: Logging) extends Database {
  override def add(key: String, value: Array[Byte]): ZIO[Any, Throwable, Unit] =
    ZIO.unit <* logger.log(s"new $key added to the inmemory database")

  override def get(key: String): ZIO[Any, Throwable, Array[Byte]] =
    ZIO(Array.empty[Byte]) <* logger.log(s"retrieving value of $key key from inmemory database")
}

object InmemoryDatabase {
  val serviceBuilder: URServiceBuilder[Has[Logging], Has[Database]] =
    (InmemoryDatabase.apply _).toServiceBuilder[Database]
}

case class PersistentDatabase(logger: Logging) extends Database {
  override def add(key: String, value: Array[Byte]): ZIO[Any, Throwable, Unit] =
    ZIO.unit <* logger.log(s"new $key added to the persistent database")

  override def get(key: String): ZIO[Any, Throwable, Array[Byte]] =
    ZIO.succeed(Array.empty[Byte]) <* logger.log(s"retrieving value of $key key from persistent database")
}

object PersistentDatabase {
  def serviceBuilder: URServiceBuilder[Has[Logging], Has[Database]] = (PersistentDatabase.apply _).toServiceBuilder[Database]
}

object HasManyExample extends ZIOAppDefault {
  val myApp = for {
    inmemory <- ZIO.serviceAt[Database]("inmemory").flatMap(x => ZIO.fromOption[Database](x))
    persistent <- ZIO.serviceAt[Database]("persistent").flatMap(x => ZIO.fromOption[Database](x))
    _ <- inmemory.add("key1", "value1".getBytes)
    _ <- persistent.add("key2", "value2".getBytes)
  } yield ()

  def run =
    myApp.injectCustom(
      Database.serviceBuilder,
      ConsoleLogger.serviceBuilder
    )
}
```

