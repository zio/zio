---
id: zio-slick-interop
title: "ZIO Slick Interop"
---

[ZIO Slick Interop](https://github.com/ScalaConsultants/zio-slick-interop) is a small library, that provides interop between Slick and ZIO.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "io.scalac" %% "zio-slick-interop" % "0.4.0"
```

## Example

To run this example we should also add the _HikariCP integration for Slick_ in our `build.sbt` file:

```scala
libraryDependencies += "com.typesafe.slick" %% "slick-hikaricp" % "3.3.3"
```

Here is a full working example of creating database-agnostic Slick repository:

```scala
import com.typesafe.config.ConfigFactory
import slick.interop.zio.DatabaseProvider
import slick.interop.zio.syntax._
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcProfile
import zio.console.Console
import zio.interop.console.cats.putStrLn
import zio.{ExitCode, IO, URIO, ZEnvironment, ZIO, ZLayer}

import scala.jdk.CollectionConverters._

case class Item(id: Long, name: String)

trait ItemRepository {
  def add(name: String): IO[Throwable, Long]

  def getById(id: Long): IO[Throwable, Option[Item]]

  def upsert(name: String): IO[Throwable, Long]
}

object ItemsTable {
  class Items(tag: Tag) extends Table[Item](
    _tableTag = tag,
    _tableName = "ITEMS"
  ) {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)

    def name = column[String]("NAME")

    def * = (id, name) <> ((Item.apply _).tupled, Item.unapply _)
  }

  val table = TableQuery[ItemsTable.Items]
}

object SlickItemRepository {
  val live: ZLayer[DatabaseProvider, Throwable, ItemRepository] =
    ZLayer.fromServiceM { db =>
      db.profile.flatMap { profile =>
        import profile.api._

        val initialize = ZIO.fromDBIO(ItemsTable.table.schema.createIfNotExists)

        val repository = new ItemRepository {
          private val items = ItemsTable.table

          def add(name: String): IO[Throwable, Long] =
            ZIO
              .fromDBIO((items returning items.map(_.id)) += Item(0L, name))
              .provideEnvironment(ZEnvironment(db))

          def getById(id: Long): IO[Throwable, Option[Item]] = {
            val query = items.filter(_.id === id).result

            ZIO.fromDBIO(query).map(_.headOption).provideEnvironment(ZEnvironment(db))
          }

          def upsert(name: String): IO[Throwable, Long] =
            ZIO
              .fromDBIO { implicit ec =>
                (for {
                  itemOpt <- items.filter(_.name === name).result.headOption
                  id <- itemOpt.fold[DBIOAction[Long, NoStream, Effect.Write]](
                    (items returning items.map(_.id)) += Item(0L, name)
                  )(item => (items.map(_.name) update name).map(_ => item.id))
                } yield id).transactionally
              }
              .provideEnvironment(Environment(db))
        }

        initialize.as(repository).provideEnvironment(Environment(db))
      }
    }
}


object Main extends zio.App {

  private val config = ConfigFactory.parseMap(
    Map(
      "url" -> "jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1",
      "driver" -> "org.h2.Driver",
      "connectionPool" -> "disabled"
    ).asJava
  )

  private val env: ZLayer[Any, Throwable, ItemRepository] =
    (ZLayer.succeed(config) ++ ZLayer.succeed[JdbcProfile](
      slick.jdbc.H2Profile
    )) >>> DatabaseProvider.live >>> SlickItemRepository.live

  val myApp: ZIO[Console with Has[ItemRepository], Throwable, Unit] =
    for {
      repo <- ZIO.service[ItemRepository]
      aId1 <- repo.add("A")
      _ <- repo.add("B")
      a <- repo.getById(1L)
      b <- repo.getById(2L)
      aId2 <- repo.upsert("A")
      _ <- putStrLn(s"$aId1 == $aId2")
      _ <- putStrLn(s"A item: $a")
      _ <- putStrLn(s"B item: $b")
    } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.provideCustom(env).exitCode
}
```
