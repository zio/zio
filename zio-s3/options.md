# Options

> The options types control how ZIO S3 lists objects and uploads them. Each type has a `default` value and helper constructors. The [operations reference](./s3-operations.md) shows which operations take each type.

## ListObjectOptions

`ListObjectOptions` controls a listing of objects in a bucket:

```scala
final case class ListObjectOptions(
  prefix: Option[String],
  maxKeys: Long,
  delimiter: Option[String],
  starAfter: Option[String]
)
```

The fields have these meanings:

- `prefix` keeps only the objects whose keys start with the prefix.
- `maxKeys` sets the number of objects per request. The default is `MaxKeys.Max`, which is 1000.
- `delimiter` is sent to S3 as the listing delimiter. S3 then groups the keys below the delimiter into common prefixes, but `S3ObjectListing` has no field for common prefixes, so those keys are left out of the listing and the prefixes are not returned. Later pages do not reapply it, as [list objects page by page](./list-objects-page-by-page.md#know-the-limits-of-paging) describes.
- `starAfter` starts the listing after the given key.

The companion object provides these constructors:

```scala
object ListObjectOptions {
  val default: ListObjectOptions
  def from(prefix: String, maxKeys: Long): ListObjectOptions
  def fromMaxKeys(maxKeys: Long): ListObjectOptions
  def fromStartAfter(startAfter: String): ListObjectOptions
}
```

`ListObjectOptions.default` has no prefix, no delimiter, no start key, and a page size of 1000.

## UploadOptions

`UploadOptions` controls the metadata and access settings of an uploaded object:

```scala
final case class UploadOptions(
  metadata: Map[String, String],
  cannedAcl: ObjectCannedACL,
  contentType: Option[String]
)
```

The fields have these meanings:

- `metadata` holds your own key-value pairs. ZIO S3 adds the `x-amz-meta-` prefix when it sends them.
- `cannedAcl` sets the access control list for the object. The default is `ObjectCannedACL.PRIVATE`.
- `contentType` sets the `Content-Type` header. When it is `None`, S3 applies its default.

The companion object provides these constructors:

```scala
object UploadOptions {
  val default: UploadOptions
  def from(metadata: Map[String, String], contentType: String): UploadOptions
  def fromContentType(contentType: String): UploadOptions
  def fromMetadata(metadata: Map[String, String]): UploadOptions
}
```

`UploadOptions.default` uses a private ACL, no metadata, and no content type. The stub uses `application/octet-stream` as its content type for `putObject` when none is given, and `binary/octet-stream` for `multipartUpload`, so the two differ in the stub.

## MultipartUploadOptions

`MultipartUploadOptions` adds the part size to the upload settings for `multipartUpload`:

```scala
final case class MultipartUploadOptions(
  uploadOptions: UploadOptions,
  partSize: Int
)
```

The `uploadOptions` field holds the metadata, ACL, and content type. The `partSize` field is the size of each part in bytes, and it must be at least `PartSize.Min`:

```scala
object MultipartUploadOptions {
  val default: MultipartUploadOptions
  def fromUploadOptions(options: UploadOptions): MultipartUploadOptions
  def fromPartSize(partSize: Int): MultipartUploadOptions
}
```

`MultipartUploadOptions.default` uses `UploadOptions.default` and the minimum part size.

## PartSize

`PartSize` holds the byte sizes that help build part sizes:

```scala
object PartSize {
  final val Kilo: Int = 1024
  final val Mega: Int = 1024 * Kilo
  final val Giga: Int = 1024 * Mega
  final val Min: Int = 5 * Mega
}
```

`PartSize.Min` is 5 MB, the smallest part that S3 accepts for every part except the last one. Multiply a constant by a count, as in `10 * PartSize.Mega`, to set a part size. The part size is an `Int`, so it cannot exceed 2 GiB minus one byte, and `2 * PartSize.Giga` overflows. S3 accepts at most 10,000 parts per upload, so the part size times 10,000 is the largest object that `multipartUpload` can store.

## MaxKeys

`MaxKeys` holds the default page size for listings:

```scala
object MaxKeys {
  val Max: Long = 1000L
}
```

`ListObjectOptions.default` uses `MaxKeys.Max`.
