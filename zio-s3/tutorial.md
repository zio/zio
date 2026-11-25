# Tutorial: Upload, List and Download Files

> In this tutorial, we build a small program that creates a bucket, uploads a text file, lists the objects in the bucket, and downloads the file again. We use the local stub from the [testing how-to](./testing.md), so we do not need a running S3 server or AWS account. The same code works against AWS or MinIO once we swap the layer, as [S3-compatible endpoints](./connect-to-an-s3-compatible-store.md) describes.

## Add the Dependency

Add ZIO S3 to your project as the [installation section](./index.md#installation) shows.

## Create an S3 Layer

Every ZIO S3 operation requires an `S3` service. The `stub` function builds one that stores buckets as directories under a root path on the local disk:

```scala
import zio._
import zio.nio.file.{ Path => ZPath }
import zio.s3._

val root: ZPath = ZPath("/tmp/zio-s3-tutorial")

val s3Layer: ZLayer[Any, Nothing, S3] = stub(root)
```

The root directory must exist before we create a bucket, so we create it in the first step of the program.

## Create a Bucket and Upload a File

To create a bucket and upload a text file, we call `createBucket` and `putObject`. The `contentLength` argument is the size of the data in bytes, and `UploadOptions.fromContentType` sets the `Content-Type` of the object:

```scala
import zio._
import zio.s3._
import zio.stream.ZStream
import software.amazon.awssdk.services.s3.model.S3Exception

val content: Chunk[Byte] = Chunk.fromArray("hello from zio-s3".getBytes)

val upload: ZIO[S3, S3Exception, Unit] =
  for {
    _ <- createBucket("tutorial")
    _ <- putObject(
           bucketName = "tutorial",
           key = "greetings/hello.txt",
           contentLength = content.length.toLong,
           content = ZStream.fromChunk(content),
           options = UploadOptions.fromContentType("text/plain")
         )
  } yield ()
```

The key `greetings/hello.txt` contains a slash, so the stub stores the file in a `greetings` subdirectory. Real S3 treats the slash as part of the name, and the listing in the next step shows the full key.

## List the Objects in a Bucket

To list every object in a bucket, we call `listAllObjects`. It returns a stream of `S3ObjectSummary` values, and each summary holds the bucket name, the key, the last-modified time, and the size:

```scala
import zio._
import zio.s3._
import software.amazon.awssdk.services.s3.model.S3Exception

val printKeys: ZIO[S3, S3Exception, Unit] =
  listAllObjects("tutorial")
    .runForeach(summary => Console.printLine(summary.key).orDie)
```

For a bucket with more than one page of results, see [list and paginate objects](./list-objects-page-by-page.md).

## Download a File

To read an object back, we call `getObject`, which returns a stream of bytes. We decode the bytes as UTF-8 text with `ZPipeline.utf8Decode`, then collect the text:

```scala
import zio._
import zio.s3._
import zio.stream.ZPipeline

val download: ZIO[S3, Throwable, String] =
  getObject("tutorial", "greetings/hello.txt")
    .via(ZPipeline.utf8Decode)
    .runCollect
    .map(_.mkString)
```

`getObject` fails with `S3Exception` when the key does not exist. We handle that case in the [errors reference](./errors.md).

## Put It Together

The following program runs all the steps in order and prints the results. We run it with `sbt run`, and it creates files under `/tmp/zio-s3-tutorial`:

```scala
import zio._
import zio.nio.file.{ Files, Path => ZPath }
import zio.s3._
import zio.stream.{ ZPipeline, ZStream }

object S3Tutorial extends ZIOAppDefault {

  val root: ZPath = ZPath("/tmp/zio-s3-tutorial")

  val content: Chunk[Byte] = Chunk.fromArray("hello from zio-s3".getBytes)

  val program: ZIO[S3, Throwable, Unit] =
    for {
      _       <- Files.createDirectories(root)
      _       <- createBucket("tutorial")
      _       <- putObject(
                   bucketName = "tutorial",
                   key = "greetings/hello.txt",
                   contentLength = content.length.toLong,
                   content = ZStream.fromChunk(content),
                   options = UploadOptions.fromContentType("text/plain")
                 )
      objects <- listAllObjects("tutorial").runCollect
      _       <- ZIO.foreachDiscard(objects)(summary => Console.printLine(s"object: ${summary.key}"))
      text    <- getObject("tutorial", "greetings/hello.txt").via(ZPipeline.utf8Decode).runCollect.map(_.mkString)
      _       <- Console.printLine(s"content: $text")
    } yield ()

  def run = program.provide(stub(root))
}
```

When the program runs, it prints the key `greetings/hello.txt` and the text `hello from zio-s3`. To try the same steps against a real server, replace `stub(root)` with a `live` layer, as shown in the [introduction](./index.md).
