# Download an Object

> `getObject` returns the bytes of an object as a `Stream`, so a large object is never loaded into memory at once. This page shows how to write an object to a local file, how to read it as text, and how to read it line by line. The [operations reference](./s3-operations.md#object-operations) lists the signatures.

## Save an Object to a File

To write an object to a local file, run the byte stream into a file sink. The sink creates the file and closes it when the stream ends:

```scala
import zio._
import zio.s3._
import zio.stream.ZSink

import java.nio.file.Paths

val save: ZIO[S3, Throwable, Long] =
  getObject("bucket-1", "upload/myfile.zip")
    .run(ZSink.fromPath(Paths.get("/my/path/to/download.zip")))
```

The effect returns the number of bytes written. When the key does not exist, `getObject` fails with an `S3Exception`, and the [handle errors how-to](./handle-errors.md) shows how to recover.

## Read an Object as Text

To read a small text object into a `String`, decode the bytes as UTF-8 and collect the chunks:

```scala
import zio._
import zio.s3._
import zio.stream.ZPipeline

val text: ZIO[S3, Throwable, String] =
  getObject("bucket-1", "notes.txt")
    .via(ZPipeline.utf8Decode)
    .runCollect
    .map(_.mkString)
```

## Read an Object Line by Line

To process a large text object one line at a time, use `streamLines`. It decodes the bytes as UTF-8 and splits them into lines, so only the current line is held in memory:

```scala
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._

val firstTen: ZIO[S3, S3Exception, Chunk[String]] =
  streamLines("bucket-1", "logs/app.log").take(10).runCollect
```

When the object is not valid UTF-8, `streamLines` fails with `DecodingException`. For binary objects, use `getObject` and process the bytes directly.
