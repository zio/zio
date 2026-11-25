# S3 Operations

> The `S3` trait defines every operation that ZIO S3 supports. The package object `zio.s3` exposes each operation as a function that requires `S3` in the environment, so you call `createBucket("docs")` directly, not `s3.createBucket("docs")`. Every operation returns a ZIO effect or a ZIO Stream, and the failure type is `S3Exception`. The [settings reference](./settings.md) describes how to build the `S3` layer.

## Bucket Operations

Bucket operations create, delete, check, and list buckets. A bucket name must follow the S3 naming rules, and the stub maps each bucket to a directory:

```scala
trait S3 {
  def createBucket(bucketName: String): IO[S3Exception, Unit]
  def deleteBucket(bucketName: String): IO[S3Exception, Unit]
  def isBucketExists(bucketName: String): IO[S3Exception, Boolean]
  val listBuckets: IO[S3Exception, S3BucketListing]
}
```

`isBucketExists` returns `false` when the bucket is missing, and fails for other errors. `listBuckets` returns a `Chunk` of [`S3Bucket`](./models.md#s3bucket) values.

## Object Operations

Object operations read, write, inspect, and delete the objects inside a bucket. The key identifies an object, and a slash in the key does not create a folder on real S3:

```scala
trait S3 {
  def getObject(bucketName: String, key: String): Stream[S3Exception, Byte]
  def getObjectMetadata(bucketName: String, key: String): IO[S3Exception, ObjectMetadata]
  def putObject[R](
    bucketName: String,
    key: String,
    contentLength: Long,
    content: ZStream[R, Throwable, Byte],
    options: UploadOptions = UploadOptions.default,
    contentMD5: Option[String] = None
  ): ZIO[R, S3Exception, Unit]
  def deleteObject(bucketName: String, key: String): IO[S3Exception, Unit]
  def streamLines(bucketName: String, key: String): Stream[S3Exception, String]
}
```

`getObject` fails with `S3Exception` when the key does not exist. `putObject` takes the `contentLength` in bytes, which must match the number of bytes that the stream produces. `streamLines` decodes the object as UTF-8 and splits it into lines, and it fails with `DecodingException`, listed in the [errors reference](./errors.md#error-types), when the bytes are not valid UTF-8.

## Multipart Upload

The multipart operation uploads a stream in parts, as described in [stream large files with multipart upload](./upload-a-large-file-in-parts.md):

```scala
trait S3 {
  def multipartUpload[R](
    bucketName: String,
    key: String,
    content: ZStream[R, Throwable, Byte],
    options: MultipartUploadOptions = MultipartUploadOptions.default
  )(parallelism: Int): ZIO[R, S3Exception, Unit]
}
```

The `parallelism` argument must be greater than 0. The `options` argument holds the part size, which must be at least `PartSize.Min`, and a violation stops the effect with a defect.

## Listing Operations

Listing operations return one page of objects, the next page, or a stream of every object. The default page size is 1000 objects, as described in [list and paginate objects](./list-objects-page-by-page.md):

```scala
trait S3 {
  def listObjects(bucketName: String): IO[S3Exception, S3ObjectListing]
  def listObjects(bucketName: String, options: ListObjectOptions): IO[S3Exception, S3ObjectListing]
  def getNextObjects(listing: S3ObjectListing): IO[S3Exception, S3ObjectListing]
  def listAllObjects(bucketName: String): Stream[S3Exception, S3ObjectSummary]
  def listAllObjects(bucketName: String, options: ListObjectOptions): Stream[S3Exception, S3ObjectSummary]
  def paginate(initialListing: S3ObjectListing): Stream[S3Exception, S3ObjectListing]
}
```

`getNextObjects` returns an empty listing when the given listing has no continuation token. The stub differs and stops with a defect, as the [testing how-to](./testing.md) describes. `paginate` emits the initial listing first and then each following page.

## Escape Hatch

The `execute` operation runs a request on the underlying AWS `S3AsyncClient`, for operations that the library does not wrap:

```scala
trait S3 {
  def execute[T](f: S3AsyncClient => CompletableFuture[T]): IO[S3Exception, T]
}
```

The stub does not support `execute`, and the stub stops with a defect when you call it.

## Types Used by These Operations

The operations use the types from the [options reference](./options.md) for their settings, and the [models reference](./models.md) for their results. Every error is a subtype of `S3Exception`, as listed in the [errors reference](./errors.md).
