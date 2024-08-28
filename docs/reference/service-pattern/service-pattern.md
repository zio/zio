---
id: service-pattern
title: "The Five Elements of Service Pattern"
sidebar_label: Service Pattern
---

Writing services in ZIO using the _Service Pattern_ is very similar to the object-oriented way of defining services. We use scala traits to define services, classes to implement services, and constructors to define service dependencies. Finally, we lift the class constructor into the `ZLayer`.

Let's start learning this service pattern by writing a `DocRepo` service:

## 1. Service Definition

Traits are how we define services. A service could be all the stuff that is related to one concept with singular responsibility. We define the service definition with a trait named `DocRepo`:

```scala mdoc:silent
import zio._

case class Doc(
    title: String,
    description: String,
    language: String,
    format: String,
    content: Array[Byte]
)

trait DocRepo {
  def get(id: String): ZIO[Any, Throwable, Doc]

  def save(document: Doc): ZIO[Any, Throwable, String]

  def delete(id: String): ZIO[Any, Throwable, Unit]

  def findByTitle(title: String): ZIO[Any, Throwable, List[Doc]]
}
```

## 2. Service Implementation

It is the same as what we did in an object-oriented fashion. We implement the service with the Scala class:

```scala mdoc:compile-only
case class DocRepoImpl() extends DocRepo {
  override def get(id: String): ZIO[Any, Throwable, Doc] = ???

  override def save(document: Doc): ZIO[Any, Throwable, String] = ???

  override def delete(id: String): ZIO[Any, Throwable, Unit] = ???

  override def findByTitle(title: String): ZIO[Any, Throwable, List[Doc]] = ???
}
```

## 3. Service Dependencies

We might need `MetadataRepo` and `BlobStorage` services to implement the `DocRepo` service. Here, we put its dependencies into its constructor. All the dependencies are just interfaces, not implementation. Just like what we did in object-oriented style.

First, we need to define the interfaces for `MetadataRepo` and `BlobStorage` services:

```scala mdoc:silent
case class Metadata(
    title: String,
    description: String,
    language: String,
    format: String
)

trait MetadataRepo {
  def get(id: String): ZIO[Any, Throwable, Metadata]

  def put(id: String, metadata: Metadata): ZIO[Any, Throwable, Unit]

  def delete(id: String): ZIO[Any, Throwable, Unit]

  def findByTitle(title: String): ZIO[Any, Throwable, Map[String, Metadata]]
}

trait BlobStorage {
  def get(id: String): ZIO[Any, Throwable, Array[Byte]]

  def put(content: Array[Byte]): ZIO[Any, Throwable, String]

  def delete(id: String): ZIO[Any, Throwable, Unit]
}
```

Now, we can implement the `DocRepo` service:

```scala mdoc:silent
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
```

## 4. ZLayer (Constructor)

Now, we create a companion object for `DocRepoImpl` data type and lift the service implementation into the `ZLayer`:

```scala mdoc:silent
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

## 5. Accessor Methods

Finally, to create the API more ergonomic, it's better to write accessor methods for all of our service methods using `ZIO.serviceWithZIO` constructor inside the companion object:

```scala mdoc:silent
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
```

Accessor methods allow us to utilize all the features inside the service through the ZIO Environment. That means, if we call `DocRepo.get`, we don't need to pull out the `get` function from the ZIO Environment. The `ZIO.serviceWithZIO` constructor helps us to access the environment and reduce the redundant operations, every time.

Similarly, we need to implement the `BlobStorage` and `MetadataRepo` services:

```scala mdoc:silent
object InmemoryBlobStorage {
  val layer = 
    ZLayer {
      ???
    } 
}

object InmemoryMetadataRepo {
  val layer = 
    ZLayer {
      ???
    }
}
```

This is how ZIO services are created. Let's use the `DocRepo` service in our application. We should provide `DocRepo` layer to be able to run the application:

```scala mdoc:compile-only
import zio._
import java.io.IOException

object MainApp extends ZIOAppDefault {
  val app =
    for {
      id <-
        DocRepo.save(
          Doc(
            "title",
            "description",
            "en",
            "text/plain",
            "content".getBytes()
          )
        )
      doc <- DocRepo.get(id)
      _ <- Console.printLine(
        s"""
          |Downloaded the document with $id id:
          |  title: ${doc.title}
          |  description: ${doc.description}
          |  language: ${doc.language}
          |  format: ${doc.format}
          |""".stripMargin
      )
      _ <- DocRepo.delete(id)
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

During writing the application, we don't care which implementation version of the `BlobStorage` and `MetadataRepo` services will be injected into our `app`. Later at the end of the day, it will be provided by one of `ZIO#provide*` methods.

That's it! Very simple! ZIO encourages us to follow some of the best practices in object-oriented programming. So it doesn't require us to throw away all our object-oriented knowledge.
