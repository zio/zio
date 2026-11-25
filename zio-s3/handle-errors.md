# Handle Errors

> Most ZIO S3 operations fail with `S3Exception`, so one handler can catch every failure. The [errors reference](./errors.md) lists the subtypes that the library raises. This page shows how to recover from the common failures.

## Handle a Missing Object

To handle a missing object, match an `S3Exception` with status code 404 in `ZIO#catchSome`. `getObject` rewraps the AWS SDK's `NoSuchKeyException` into a plain `S3Exception` that keeps the status code, so matching `NoSuchKeyException` never succeeds. Any other `S3Exception` continues to fail:

```scala
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._
import zio.stream.ZPipeline

val readOrEmpty: ZIO[S3, Exception, String] =
  getObject("docs", "missing.txt")
    .via(ZPipeline.utf8Decode)
    .runCollect
    .map(_.mkString)
    .catchSome { case e: S3Exception if e.statusCode == 404 => ZIO.succeed("") }
```

## Handle Connection Failures

To handle a failure while building the layer, match `ConnectionError`. The error message comes from the AWS SDK, so log it or show it to the user rather than matching its text:

```scala
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._
import zio.s3.errors.ConnectionError

import java.net.URI

val connected: ZIO[Any, S3Exception, Unit] =
  listBuckets
    .provideLayer(
      live(
        Region.US_EAST_1,
        AwsBasicCredentials.create("TESTKEY", "TESTSECRET"),
        Some(URI.create("http://localhost:9000")),
        forcePathStyle = Some(true)
      )
    )
    .unit
    .catchSome { case e: ConnectionError => Console.printLine(s"cannot connect: ${e.getMessage}").orDie }
```

The `ConnectionError` type appears in the signature of the `live` value that takes `S3Settings`, and it appears in the error of the layer for a failed provider. Use `ZIO#catchSome` on the layer, as [configure credentials](./credentials.md#handle-credential-failures) shows, to recover from it.

## Know Which Failures Are Defects

Some failures stop the effect with a defect, not a typed `S3Exception`, so `ZIO#catchSome` on `S3Exception` does not see them. A defect is an unrecoverable error that ZIO reports through `Cause.Die`. Two cases produce one: `multipartUpload` with a part size below `PartSize.Min`, or a parallelism of 0 or less, so validate these values where you build them; and the stub, for several cases that a real server reports as errors, as the [testing how-to](./testing.md) lists.
