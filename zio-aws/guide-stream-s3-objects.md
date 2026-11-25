# Stream S3 Objects

> Upload and download Amazon S3 objects as ZIO streams, read object metadata, and list the objects in a bucket.

In this guide, we will upload and download Amazon S3 objects as ZIO streams, so that a file of any size passes through the program without being loaded into memory. By the end, you will know how to upload from memory and from a file, how to download to a file, and how to list the objects of a bucket.

We'll take an incremental approach: first we upload, then we download, and finally we list a bucket.

## The Problem

An S3 object can be larger than the memory of the program that reads it. The AWS Java SDK streams object bodies through reactive-streams publishers and response transformers, which do not fit naturally into a ZIO program, and the bytes then have to be connected to a file or another stream by hand.

ZIO AWS generates S3 operations that take and return `ZStream[Any, AwsError, Byte]`. An upload consumes a stream, and a download returns one, so the bytes flow between S3 and any ZIO stream source or sink.

## Prerequisites

Add the core module, an HTTP client and the S3 module:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-aws-core"  % "7.59.5.2",
  "dev.zio" %% "zio-aws-netty" % "7.59.5.2",
  "dev.zio" %% "zio-aws-s3"    % "7.59.5.2"
)
```

The examples use these imports:

```scala
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import zio._
import zio.stream.{ZSink, ZStream}
import zio.aws.core.AwsError
import zio.aws.s3.S3
import zio.aws.s3.model.{GetObjectRequest, ListObjectsV2Request, PutObjectRequest, S3Object}
import zio.aws.s3.model.primitives.{BucketName, ContentLength, ObjectKey}
```

## Uploading from Memory

`S3.putObject` takes the request and the body as a second argument. The body is a stream of bytes, and S3 needs to know its length before the upload starts, so we set `contentLength` in the request:

```scala
def uploadText(bucket: String, key: String, text: String): ZIO[S3, AwsError, Unit] = {
  val bytes = Chunk.fromArray(text.getBytes(StandardCharsets.UTF_8))
  S3.putObject(
    PutObjectRequest(
      bucket = BucketName(bucket),
      key = ObjectKey(key),
      contentLength = ContentLength(bytes.length.toLong)
    ),
    ZStream.fromChunk(bytes)
  ).unit
}
```

The code above:

- `BucketName`, `ObjectKey` and `ContentLength` are the S3 newtypes for these fields; the request case class does not accept plain strings or numbers.
- Optional request fields such as `contentLength` take the value directly; ZIO AWS wraps it in `Optional` for you.
- `ZStream.fromChunk(bytes)` is the body. It must produce exactly `contentLength` bytes.
- `S3.putObject` returns a `PutObjectResponse.ReadOnly`, which holds the object's ETag; `ZIO#unit` discards it here.

## Uploading a File

A file upload is the same call with a file stream as the body. `ZStream.fromPath` reads the file in chunks, and its failures are `Throwable` values, so we turn them into `AwsError` with `AwsError.fromThrowable`:

```scala
def uploadFile(bucket: String, key: String, path: Path): ZIO[S3, AwsError, Unit] =
  for {
    size <- ZIO.attempt(Files.size(path)).mapError(AwsError.fromThrowable)
    _ <- S3.putObject(
           PutObjectRequest(
             bucket = BucketName(bucket),
             key = ObjectKey(key),
             contentLength = ContentLength(size)
           ),
           ZStream.fromPath(path).mapError(AwsError.fromThrowable)
         )
  } yield ()
```

The file is never loaded into memory as a whole: the HTTP client pulls chunks from the stream as it sends them.

## Downloading to a File

`S3.getObject` returns a `StreamingOutputResult`. It pairs the response, which holds the object's metadata, with an `output` stream of the object's bytes:

```scala
def downloadFile(bucket: String, key: String, path: Path): ZIO[S3, AwsError, Long] =
  for {
    result <- S3.getObject(GetObjectRequest(bucket = BucketName(bucket), key = ObjectKey(key)))
    _ <- Console.printLine(s"Content type: ${result.response.contentType.getOrElse("unknown")}").orDie
    written <- result.output
                 .mapError(_.toThrowable)
                 .run(ZSink.fromPath(path))
                 .mapError(AwsError.fromThrowable)
  } yield written
```

The code above:

- `result.response` is a `GetObjectResponse.ReadOnly`, available before any byte of the body is read. Its `contentType` field is an `Optional`.
- `result.output` is a `ZStream[Any, AwsError, Byte]` that reads the HTTP response body as it arrives. Run it once.
- `ZSink.fromPath(path)` writes the bytes to a file and returns the number of bytes written. The sink fails with `Throwable`, so we map the stream's `AwsError` to a `Throwable` with `AwsError#toThrowable` before running it, and map the result back.

## Reading Part of an Object

`StreamingOutputResult` has two helpers that change one half of the result and keep the other. `mapOutput` changes the byte stream, and `mapResponse` changes the response. To read only the first kilobyte of an object:

```scala
def firstKilobyte(bucket: String, key: String): ZIO[S3, AwsError, Chunk[Byte]] =
  S3.getObject(GetObjectRequest(bucket = BucketName(bucket), key = ObjectKey(key)))
    .map(_.mapOutput(_.take(1024)))
    .flatMap(_.output.runCollect)
```

`take(1024)` stops reading after 1024 bytes. To ask S3 to send only a byte range instead, set the `range` field of `GetObjectRequest` to an HTTP range such as `bytes=0-1023`.

## Listing the Objects in a Bucket

`S3.listObjectsV2` is a paginated operation, so it returns a stream of `S3Object.ReadOnly` items and fetches the next page when the stream needs it:

```scala
def objectKeys(bucket: String): ZStream[S3, AwsError, String] =
  S3.listObjectsV2(ListObjectsV2Request(bucket = BucketName(bucket)))
    .mapZIO(_.getKey)
    .map(key => ObjectKey.unwrap(key))
```

Combined with the download function, this copies every object of a bucket to a local directory, one object at a time:

```scala
def downloadAll(bucket: String, directory: Path): ZIO[S3, AwsError, Unit] =
  objectKeys(bucket)
    .filterNot(_.endsWith("/"))
    .mapZIO { key =>
      val target = directory.resolve(key)
      ZIO.attempt(Files.createDirectories(target.getParent)).mapError(AwsError.fromThrowable) *>
        downloadFile(bucket, key, target)
    }
    .runDrain
```

## Going Further

- The [Wrappers](wrappers.md) reference describes the streaming operation signatures and `StreamingOutputResult`.
- The [Use a Local Endpoint](guide-use-a-local-endpoint.md) guide runs the same code against LocalStack or MinIO.
- The [Paginate and Stream Results](guide-paginate-and-stream.md) guide covers paginated operations in more depth.
