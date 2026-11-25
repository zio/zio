# Manage Buckets

> A bucket is the container that holds objects. ZIO S3 can create, check, list and delete buckets. The [operations reference](./s3-operations.md#bucket-operations) lists the signatures.

## Create a Bucket If It Does Not Exist

To create a bucket only when it is missing, check it first with `isBucketExists`. `createBucket` on a bucket that already exists fails on a real server, so the check keeps the program safe to run twice:

```scala
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._

def ensureBucket(name: String): ZIO[S3, S3Exception, Unit] =
  isBucketExists(name).flatMap(exists => createBucket(name).unless(exists)).unit
```

## List the Buckets

To list the buckets of the account, call `listBuckets`. It returns a `Chunk` of `S3Bucket` values with a name and a creation date:

```scala
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._

val names: ZIO[S3, S3Exception, Chunk[String]] =
  listBuckets.map(_.map(_.name))
```

## Delete a Bucket

To delete a bucket, call `deleteBucket`. S3 refuses to delete a bucket that still holds objects, so empty it first. This example deletes every object, then the bucket:

```scala
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._

def deleteWithContents(name: String): ZIO[S3, S3Exception, Unit] =
  listAllObjects(name).mapZIOPar(8)(obj => deleteObject(name, obj.key)).runDrain *>
    deleteBucket(name)
```

`deleteObject` succeeds when the key is already gone, so the cleanup is safe to repeat. The stub stops with a defect, not an `S3Exception`, when a bucket is missing or not empty, as the [testing how-to](./testing.md) describes.
