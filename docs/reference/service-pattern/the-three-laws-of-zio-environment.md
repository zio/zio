---
id: the-three-laws-of-zio-environment
title: "The Three Laws of ZIO Environment"
sidebar_label: "Three Laws of ZIO Environment"
---

```scala mdoc:invisible
import zio._

case class Doc(
    title: String,
    description: String,
    language: String,
    format: String,
    content: Array[Byte]
)
case class Metadata(
    title: String,
    description: String,
    language: String,
    format: String
)

trait BlobStorage {
  def get(id: String): ZIO[Any, Throwable, Array[Byte]]

  def put(content: Array[Byte]): ZIO[Any, Throwable, String]

  def delete(id: String): ZIO[Any, Throwable, Unit]
}

trait MetadataRepo {
  def get(id: String): ZIO[Any, Throwable, Metadata]

  def put(id: String, metadata: Metadata): ZIO[Any, Throwable, Unit]

  def delete(id: String): ZIO[Any, Throwable, Unit]

  def findByTitle(title: String): ZIO[Any, Throwable, Map[String, Metadata]]
}

case class DocRepoImpl(
    metadataRepo: MetadataRepo,
    blobStorage: BlobStorage
) extends DocRepo {
  override def get(id: String): ZIO[Any, Throwable, Doc] =
    for {
      metadata <- metadataRepo.get(id)
      content <- blobStorage.get(id)
    } yield Doc(
      metadata.title,
      metadata.description,
      metadata.language,
      metadata.format,
      content
    )

  override def save(document: Doc): ZIO[Any, Throwable, String] =
    for {
      id <- blobStorage.put(document.content)
      _ <- metadataRepo.put(
        id,
        Metadata(
          document.title,
          document.description,
          document.language,
          document.format
        )
      )
    } yield id

  override def delete(id: String): ZIO[Any, Throwable, Unit] =
    for {
      _ <- blobStorage.delete(id)
      _ <- metadataRepo.delete(id)
    } yield ()

  override def findByTitle(title: String): ZIO[Any, Throwable, List[Doc]] =
    for {
      map <- metadataRepo.findByTitle(title)
      content <- ZIO.foreach(map)((id, metadata) =>
        for {
          content <- blobStorage.get(id)
        } yield id -> Doc(
          metadata.title,
          metadata.description,
          metadata.language,
          metadata.format,
          content
        )
      )
    } yield content.values.toList
}

object DocRepoImpl {
  val layer: ZLayer[BlobStorage with MetadataRepo, Nothing, DocRepo] =
    ZLayer {
      for {
        metadataRepo <- ZIO.service[MetadataRepo]
        blobStorage  <- ZIO.service[BlobStorage]
      } yield DocRepoImpl(metadataRepo, blobStorage)
    }
}


trait DocRepo {
  def get(id: String): ZIO[Any, Throwable, Doc]

  def save(document: Doc): ZIO[Any, Throwable, String]

  def delete(id: String): ZIO[Any, Throwable, Unit]

  def findByTitle(title: String): ZIO[Any, Throwable, List[Doc]]
}

object DocRepo {
  def get(id: String): ZIO[DocRepo, Throwable, Doc] =
    ZIO.serviceWithZIO[DocRepo](_.get(id))

  def save(document: Doc): ZIO[DocRepo, Throwable, String] =
    ZIO.serviceWithZIO[DocRepo](_.save(document))

  def delete(id: String): ZIO[DocRepo, Throwable, Unit] =
    ZIO.serviceWithZIO[DocRepo](_.delete(id))

  def findByTitle(title: String): ZIO[DocRepo, Throwable, List[Doc]] =
    ZIO.serviceWithZIO[DocRepo](_.findByTitle(title))
}


object InmemoryBlobStorage {
  val layer: ZLayer[Any, Nothing, BlobStorage] = 
    ZLayer {
      ???
    } 
}


object InmemoryMetadataRepo {
  val layer: ZLayer[Any, Nothing, MetadataRepo] = 
    ZLayer {
      ???
    }
}
```

When we are working with the ZIO environment, one question might arise: "When should we use environment and when do we need to use constructors?".

Using ZIO environment follows three laws:

## 1. Service Interface (Trait)

**When we are defining service interfaces we should _never_ use the environment for dependencies of the service itself.**

For example, if the implementation of service `X` depends on service `Y` and `Z` then these should never be reflected in the trait that defines service `X`. It's leaking implementation details.

So the following service definition is wrong because the `BlobStorage` and `MetadataRepo` services are dependencies of the  `DocRepo` service's implementation, not the `DocRepo` interface itself:


```scala mdoc:compile-only
import zio._

trait DocRepo {
  def save(document: Doc): ZIO[BlobStorage & MetadataRepo, Throwable, String]
}
```

## 2. Service Implementation (Class)

**When implementing service interfaces, we should accept all dependencies in the class constructor.**

Again, let's see how `DocRepoImpl` accepts `BlobStorage` and `MetadataRepo` dependencies from the class constructor:

```scala mdoc:compile-only
case class DocRepoImpl(
    metadataRepo: MetadataRepo,
    blobStorage: BlobStorage
) extends DocRepo {
  override def delete(id: String): ZIO[Any, Throwable, Unit] =
    for {
      _ <- blobStorage.delete(id)
      _ <- metadataRepo.delete(id)
    } yield ()

  override def get(id: String): ZIO[Any, Throwable, Doc] = ???

  override def save(document: Doc): ZIO[Any, Throwable, String] = ???

  override def findByTitle(title: String): ZIO[Any, Throwable, List[Doc]] = ???
}

object DocRepoImpl {
  val layer: ZLayer[BlobStorage with MetadataRepo, Nothing, DocRepo] =
    ZLayer {
      for {
        metadataRepo <- ZIO.service[MetadataRepo]
        blobStorage  <- ZIO.service[BlobStorage]
      } yield DocRepoImpl(metadataRepo, blobStorage)
    }
}
```

So keep in mind, we can't do something like this:

```scala mdoc:fail:silent
case class DocRepoImpl() extends DocRepo {
  override def delete(id: String): ZIO[Any, Throwable, Unit] =
    for {
      blobStorage  <- ZIO.service[BlobStorage]
      metadataRepo <- ZIO.service[MetadataRepo]
      _            <- blobStorage.delete(id)
      _            <- metadataRepo.delete(id)
    } yield ()

  override def get(id: String): ZIO[Any, Throwable, Doc] = ???

  override def save(document: Doc): ZIO[Any, Throwable, String] = ???

  override def findByTitle(title: String): ZIO[Any, Throwable, List[Doc]] = ???
}
```

## 3. Business Logic

**Finally, in the business logic we should use the ZIO environment to consume services.**

Therefore, in the last example, if we inline all accessor methods whenever we are using services, we are using the ZIO environment:

```scala mdoc:compile-only
import zio._
import java.io.IOException

object MainApp extends ZIOAppDefault {
  val app =
    for {
      id <-
        ZIO.serviceWithZIO[DocRepo](_.save(
          Doc(
              "How to write a ZIO application?",
              "In this tutorial we will learn how to write a ZIO application.",
              "en",
              "text/plain",
              "content".getBytes()
            )
          )
        )
      doc <- ZIO.serviceWithZIO[DocRepo](_.get(id))
      _ <- Console.printLine(
        s"""
          |Downloaded the document with $id id:
          |  title: ${doc.title}
          |  description: ${doc.description}
          |  language: ${doc.language}
          |  format: ${doc.format}
          |""".stripMargin
      )
      _ <- ZIO.serviceWithZIO[DocRepo](_.delete(id))
      _ <- Console.printLine(s"Deleted the document with $id id")
    } yield ()

  def run =
    app.provide(
      DocRepoImpl.layer,
      InmemoryBlobStorage.layer,
      InmemoryMetadataRepo.layer
    )
}
```

That's it! These are the most important rules we need to know about the ZIO environment.

----------------

:::info
The remaining part of this section can be skipped if you are not an advanced ZIO user.
:::

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

  def error(msg: String): HttpResponse = HttpResponse(500, msg)
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
  val layer: URLayer[Any, HttpServer] = ZLayer.succeed(HttpServerLive())
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
        onTrue = ZIO.attempt(HttpResponse.ok("pong")),
        onFalse = ZIO.attempt(HttpResponse.error("bad request"))
      )
    }

    map = Map(
      "/healthcheck" -> healthcheck,
      "/pingpong" -> pingpong
    )
    _ <- HttpServer.serve(map, "localhost", 8080)
  } yield ()

  def run = myApp.provideLayer(HttpServerLive.layer)

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
  val layer = ZLayer.succeed(DatabaseLive())
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

  def run = myApp.provideLayer(DatabaseLive.layer)

}
```

So while it's better to err on the side of "don't put things into the environment of service interface", there are cases where it's acceptable.

