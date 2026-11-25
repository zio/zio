# Test Without a Real S3

> ZIO S3 includes a stub implementation of the `S3` service for tests. The stub stores each bucket as a directory under a root path on the local disk, so tests run without a network connection or an S3 server. To build the stub layer, call `stub` with the root path:

```scala
import zio._
import zio.nio.file.{ Path => ZPath }
import zio.s3._

val stubS3: ZLayer[Any, Nothing, S3] = stub(ZPath("/tmp/s3-data"))
```

The root directory must exist before the stub creates a bucket. Each bucket is a subdirectory, and each object is a file inside it, so the layout matches what you would see on disk after a sequence of `createBucket` and `putObject` calls.

## Run Effects Against the Stub

To use the stub in a test, provide the layer to the effect under test with `provideLayer`. The stub keeps its buckets on disk under the root path, so give each test suite its own root directory, and delete it when the suite ends, to keep the buckets of one suite out of another:

```scala
import zio._
import zio.nio.file.{ Path => ZPath }
import zio.s3._

val bucketsAfterCreate: ZIO[Any, Throwable, Boolean] =
  (for {
    _       <- createBucket("tests-bucket")
    buckets <- listBuckets
  } yield buckets.map(_.name).contains("tests-bucket"))
    .provideLayer(stub(ZPath("/tmp/s3-data")))
```

The check for the bucket name runs inside the same effect as the create call, so it reads the bucket that the create call made. Pass the result to the assertion library that your test framework provides.

## Give Each Test a Fresh Root Directory

To keep tests apart, create a temporary directory for each test and build the stub on it. The scoped directory is deleted when the scope closes:

```scala
import zio._
import zio.nio.file.Files
import zio.s3._

val isolated: ZIO[Scope, Throwable, Boolean] =
  for {
    root    <- Files.createTempDirectoryScoped(Some("s3-stub"), Seq.empty)
    buckets <- (createBucket("tests-bucket") *> listBuckets).provide(stub(root))
  } yield buckets.exists(_.name == "tests-bucket")
```

The same effect works inside a test of any framework, such as ZIO Test: run it with `ZIO.scoped` or provide the `Scope`, and assert on the result.

## Know Where the Stub Differs from AWS

The stub follows the `S3` API for common operations, but a few behaviors differ from a real server, and tests that depend on them can pass against the stub and fail against S3:

- Errors: `createBucket` on an existing name, `deleteBucket` on a missing or non-empty bucket, and `listObjects` on a missing bucket stop with a defect, not a typed `S3Exception`, so `catchSome` on `S3Exception` does not catch them. Test those cases against a real server.
- Metadata: the content type and user metadata that `putObject` stores live in memory. They are lost when the JVM exits, and files that were written outside `putObject`, such as the files already in the root directory, report an empty content type and no metadata.
- Pagination: the stub does not return correct pages when a listing has a `maxKeys` limit and more objects than that limit. `getNextObjects` ignores the continuation token and lists the bucket again with a page size of 100, and it stops with a defect when the listing has no token, where a real server returns an empty listing. Do not test pagination against the stub. Use a MinIO server as shown in [list and paginate objects](./list-objects-page-by-page.md).
- ETag: the stub computes the ETag from the file text decoded as UTF-8, so for binary content it differs from the MD5 that S3 reports.
- Multipart upload: the stub validates the part size and parallelism with the same defects as the live service, then writes the stream in one piece, so `parallelism` has no effect.
- Raw client: `execute` is not supported and stops with a defect, so code that calls `execute` needs a live layer in tests.

For tests that need the real behavior, run a MinIO server as shown in the [introduction](./index.md) and build a `live` layer with `forcePathStyle = Some(true)`.
