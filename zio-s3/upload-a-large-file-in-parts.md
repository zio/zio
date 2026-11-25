# Upload a Large File in Parts

> S3 accepts a single `putObject` request for data of any size, but a large object is safer to send in parts: each part is a separate request, the parts can be sent in parallel, and a failed part can be retried without resending the whole file. ZIO S3 splits a stream into parts for you with `multipartUpload`, so a file never has to fit in memory.

## Upload a File in Parts

To upload a file as a multipart upload, pass a stream of bytes, the upload options, and the number of parts to send in parallel. In this example, each part is 10 MB, and four parts upload at the same time:

```scala
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._
import zio.stream.ZStream
import java.nio.file.Paths

val upload: ZIO[S3, S3Exception, Unit] =
  multipartUpload(
    "backups",
    "2026/archive.tar",
    ZStream.fromPath(Paths.get("/data/archive.tar")),
    MultipartUploadOptions(
      uploadOptions = UploadOptions.fromContentType("application/x-tar"),
      partSize = 10 * PartSize.Mega
    )
  )(4)
```

The second parameter list holds `parallelism`, the number of parts uploaded at the same time. The part size is set in `MultipartUploadOptions.partSize`, and the [options reference](./options.md#multipartuploadoptions) lists the other fields.

## Choose a Part Size

Every part except the last must be at least 5 MB, which is the minimum that S3 accepts. The constant `PartSize.Min` holds that value. Build the options from a part size with `MultipartUploadOptions.fromPartSize`, or from upload options with `MultipartUploadOptions.fromUploadOptions`, which uses the 5 MB minimum:

```scala
import zio.s3._

val defaultParts: MultipartUploadOptions = MultipartUploadOptions.fromUploadOptions(UploadOptions.default)

val tenMegabyteParts: MultipartUploadOptions = MultipartUploadOptions.fromPartSize(10 * PartSize.Mega)
```

A part size below 5 MB, or a parallelism of 0 or less, stops the effect with a defect, which is an unrecoverable error rather than an `S3Exception`. Check both values in the code that builds the options.

## Know What Happens on Failure

When a part fails to upload, `multipartUpload` stops and does not complete the upload. It also does not abort the upload, so the parts that already reached S3 can remain in the bucket and count toward storage. Until the library aborts uploads on failure, remove leftover uploads with the S3 API, through `execute`, or with a lifecycle rule that expires incomplete multipart uploads on the bucket.

## Upload an Empty Stream

When the stream holds no bytes, `multipartUpload` still completes the upload and creates an object of length 0. This is useful when a job must always create a marker object:

```scala
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._
import zio.stream.ZStream

val marker: ZIO[S3, S3Exception, Unit] =
  multipartUpload("backups", "2026/_done", ZStream.empty)(1)
```
