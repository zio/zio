# Set and Read Object Metadata

> Each S3 object carries a content type, a size, an ETag, and a set of user-defined metadata entries. ZIO S3 lets you set these when you upload, and read them without downloading the object. The [models reference](./models.md#objectmetadata) describes the `ObjectMetadata` type, and the [options reference](./options.md#uploadoptions) describes the upload settings.

## Set Metadata and Content Type on Upload

To store user metadata and a content type with an object, pass an `UploadOptions` value to `putObject`. The metadata map holds your own keys, and ZIO S3 adds the `x-amz-meta-` prefix for you:

```scala
import software.amazon.awssdk.services.s3.model.{ ObjectCannedACL, S3Exception }
import zio._
import zio.s3._
import zio.stream.ZStream

val bytes: Chunk[Byte] = Chunk.fromArray("name,age\nJohn,42".getBytes)

val upload: ZIO[S3, S3Exception, Unit] =
  putObject(
    bucketName = "docs",
    key = "people.csv",
    contentLength = bytes.length.toLong,
    content = ZStream.fromChunk(bytes),
    options = UploadOptions(
      metadata = Map("source" -> "crm-export"),
      cannedAcl = ObjectCannedACL.PRIVATE,
      contentType = Some("text/csv")
    )
  )
```

`UploadOptions.default` uses a private canned ACL and no content type, and `UploadOptions.from` sets both the metadata and the content type in one call.

## Read Metadata Without Downloading

To read an object's metadata, call `getObjectMetadata`. S3 answers with the headers only, so the object's bytes are not transferred:

```scala
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._

val info: ZIO[S3, S3Exception, String] =
  getObjectMetadata("docs", "people.csv").map { meta =>
    s"type=${meta.contentType} length=${meta.contentLength} etag=${meta.eTag} source=${meta.metadata.get("source").getOrElse("unknown")}"
  }
```

The `eTag` field has no surrounding quotes, because ZIO S3 removes them when it reads the response. S3 can return metadata keys in a different letter case from the one you wrote, so compare keys in lowercase, as the test suite does.

## Check the Content MD5

S3 can verify an upload against an MD5 checksum that you send with the request. Pass the checksum, encoded as Base64, in the `contentMD5` argument of `putObject`. Object locking on a bucket requires this checksum, and S3 rejects the upload without it:

```scala
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.utils.Md5Utils
import zio._
import zio.s3._
import zio.stream.ZStream

val data: Chunk[Byte] = Chunk.fromArray("name,age\nJohn,42".getBytes)
val checksum: String = Md5Utils.md5AsBase64(data.toArray)

val verified: ZIO[S3, S3Exception, Unit] =
  putObject(
    bucketName = "docs",
    key = "people.csv",
    contentLength = data.length.toLong,
    content = ZStream.fromChunk(data),
    options = UploadOptions.default,
    contentMD5 = Some(checksum)
  )
```

S3 compares the checksum with the bytes it receives and rejects the request when they differ.

## Delete an Object

To remove an object, call `deleteObject`. S3 treats a missing key as already deleted, so the call succeeds when the key does not exist:

```scala
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._

val remove: ZIO[S3, S3Exception, Unit] = deleteObject("docs", "people.csv")
```

## Know the Stub's Metadata Limits

The stub keeps content type and user metadata in memory, as described in the [testing how-to](./testing.md). Files that were not written through `putObject` report an empty content type and no metadata.
