# Errors

> Most ZIO S3 operations fail with `S3Exception`, the error type of the AWS S3 Java client. ZIO S3 adds its own subtypes of `S3Exception` for the failures that it detects itself, and it passes through the subtypes that the AWS SDK raises for S3 responses, such as `NoSuchKeyException`. This page lists each type and when the library raises it.

## Error Types

Each type below extends `S3Exception`, so a handler for `S3Exception` catches all of them:

- `InvalidCredentials(message: String)` is raised by the providers in `zio.s3.providers` when the AWS SDK cannot resolve credentials from that source.
- `InvalidSettings(message: String)` is raised when `S3Region.from` or `S3Settings.from` receives a region that the AWS SDK does not know.
- `ConnectionError(message: String, cause: Throwable)` is raised when the client cannot be built. Building the client wraps a failed credentials provider in this error, so a layer built from a provider fails with `ConnectionError`.
- `DecodingException(cause: CharacterCodingException)` is raised by `streamLines` when the object is not valid UTF-8.
- `SdkError(error: SdkException)` is raised by `execute` when the AWS SDK fails without an S3 response, such as a network error.
- `InvalidPartSize(message: String, size: Int)` is defined in the library, but no code path raises it today. An invalid part size or parallelism stops the effect with a defect instead, as the [multipart upload how-to](./upload-a-large-file-in-parts.md) describes.

The AWS SDK raises further subtypes of `S3Exception` for S3 responses, such as `NoSuchKeyException` when an object does not exist and `NoSuchBucketException` when a bucket does not exist. `execute` and the operations that call it directly, such as `getObjectMetadata` and `isBucketExists`, pass these through unchanged. `getObject`, `streamLines` and the part uploads of `multipartUpload` rewrap them into a plain `S3Exception` that keeps the status code, message and cause, so match `statusCode` there.

## Handle Errors

The [handle errors how-to](./handle-errors.md) shows how to recover from a missing object, from a failed connection, and from the failures that are defects rather than `S3Exception`.
