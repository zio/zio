# Connect to MinIO or Another S3-Compatible Store

> ZIO S3 works with any server that speaks the S3 API, such as MinIO for local development, or a hosted provider that is not AWS. To connect to such a server, pass its address as the endpoint when you build the layer. The endpoint overrides the AWS address that the client would otherwise use.

## Connect to MinIO

To connect to a local MinIO server, pass the server address as `uriEndpoint` and set `forcePathStyle` to `Some(true)`. MinIO expects the bucket name in the path, not in the host name:

```scala
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._

import java.net.URI

val minio: Layer[S3Exception, S3] =
  live(
    Region.US_EAST_1,
    AwsBasicCredentials.create("TESTKEY", "TESTSECRET"),
    Some(URI.create("http://localhost:9000")),
    forcePathStyle = Some(true)
  )
```

The repository's `docker-compose.yml` starts a MinIO server on port 9000 with the credentials `TESTKEY` and `TESTSECRET`, and it creates a bucket named `bucket-1`. Any region works with MinIO, so `Region.US_EAST_1` is a valid choice.

## Connect to Another Provider

To connect to a hosted S3-compatible service, pass its HTTPS address as `uriEndpoint`. Use a region that the provider documents for signing requests, and set `forcePathStyle` only if the provider requires path-style addressing:

```scala
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._

import java.net.URI

val provider: Layer[S3Exception, S3] =
  live(
    Region.EU_CENTRAL_1,
    AwsBasicCredentials.create("access-key", "secret-key"),
    Some(URI.create("https://s3.provider.example.com")),
    forcePathStyle = None
  )
```

The address `s3.provider.example.com` is a placeholder. Use the endpoint from your provider's documentation.

## Know the Region Limits

The `live` and `liveZIO` functions accept only regions from the AWS SDK's list, such as `Region.US_EAST_1`. A region name that the SDK does not know makes the layer fail with `InvalidSettings`, so pick the closest SDK region for the provider's signing name. The `live` layer built from `S3Settings` has no endpoint parameter, so it cannot connect to a custom endpoint. Use `live` or `liveZIO` with an endpoint instead, or the next section when the region name is not in the SDK's list.

## Use a Region the AWS SDK Does Not Know

To connect to a provider whose region name is not in the AWS SDK's list, build the region with `S3Region.unsafeFromString`, which skips the check, and connect with `Live.connect`, which takes the region, a credentials provider and the endpoint:

```scala
import zio._
import zio.s3._

import java.net.URI

val custom: ZLayer[Any, Throwable, S3] =
  ZLayer.scoped(
    Live.connect[Any](
      S3Region.unsafeFromString("custom-region-1"),
      providers.basic("access-key", "secret-key"),
      Some(URI.create("https://s3.provider.example.com")),
      forcePathStyle = Some(true)
    )
  )
```

The name is not checked, so a wrong region name fails later, when the client makes its first request.

## Verify the Connection

To check that the endpoint answers, list the buckets with the layer you built. A wrong address fails the effect with `SdkError`, wrong credentials fail it with an `S3Exception` from the server, and a credentials provider or client that cannot be built fails it with `ConnectionError`, as described in the [errors reference](./errors.md):

```scala
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._

import java.net.URI

val check: ZIO[Any, S3Exception, Unit] =
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
```
