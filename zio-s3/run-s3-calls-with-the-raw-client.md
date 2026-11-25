# Run S3 Calls with the Raw Client

> ZIO S3 wraps the common S3 operations. For an operation it does not wrap, such as copying an object, call `execute`. It takes a function from the AWS `S3AsyncClient` to a `CompletableFuture`, and returns the result as a ZIO effect that fails with `S3Exception`.

## Copy an Object

To copy an object inside S3 without downloading it, build a `CopyObjectRequest` and run it with `execute`:

```scala
import software.amazon.awssdk.services.s3.model.{ CopyObjectRequest, S3Exception }
import zio._
import zio.s3._

val copy: ZIO[S3, S3Exception, Unit] =
  execute(
    _.copyObject(
      CopyObjectRequest
        .builder()
        .sourceBucket("docs")
        .sourceKey("people.csv")
        .destinationBucket("backups")
        .destinationKey("people-copy.csv")
        .build()
    )
  ).unit
```

## Check a Bucket Directly

To call any other operation, pass its request to the matching client method. This example sends a `HeadBucketRequest`:

```scala
import software.amazon.awssdk.services.s3.model.{ HeadBucketRequest, S3Exception }
import zio._
import zio.s3._

val bucketExists: ZIO[S3, S3Exception, Boolean] =
  execute(_.headBucket(HeadBucketRequest.builder().bucket("docs").build())).as(true)
```

## Know How Failures Are Reported

`execute` fails with the `S3Exception` that the AWS SDK raises for an S3 response, such as `NoSuchBucketException`. When the SDK fails without an S3 response, for example on a network error, `execute` fails with `SdkError`. The [errors reference](./errors.md) lists both. The stub does not support `execute` and stops with a defect, so use a `live` layer in tests that call it.
