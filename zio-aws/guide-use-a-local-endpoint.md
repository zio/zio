# Use a Local Endpoint such as LocalStack or MinIO

> Point ZIO AWS clients at LocalStack, MinIO or another AWS-compatible endpoint, in code or from configuration, including S3 path-style access.

In this guide, we will send ZIO AWS requests to a local AWS-compatible server, such as [LocalStack](https://www.localstack.cloud/) or [MinIO](https://min.io/), instead of AWS. By the end, you will have a configuration for local development and tests that you can set in code or load from a configuration source.

We'll take an incremental approach: first we set the endpoint in code, then we fix S3 addressing, and finally we load the same settings from configuration.

## The Problem

Integration tests and local development should not need an AWS account. Local servers such as LocalStack emulate the AWS APIs on a port of your machine, and MinIO does the same for S3. A client only reaches them when three settings change together: the endpoint URL, credentials the local server accepts, and a region.

S3 needs a fourth change. The AWS SDK puts the bucket name in the host name (`my-bucket.s3.amazonaws.com`), which a server on `localhost` cannot answer, so the client must put the bucket in the path instead.

ZIO AWS keeps the endpoint, credentials and region in one `CommonAwsConfig` value, so every service client in the program uses the local server once that value changes.

## Prerequisites

Add the core module, an HTTP client and the service modules you call. The examples use S3:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-aws-core"  % "7.59.5.2",
  "dev.zio" %% "zio-aws-netty" % "7.59.5.2",
  "dev.zio" %% "zio-aws-s3"    % "7.59.5.2"
)
```

The examples use these imports:

```scala
import java.net.URI
import zio._
import zio.aws.core.config.{AwsConfig, CommonAwsConfig}
import zio.aws.core.config.descriptors.commonAwsConfig
import zio.aws.netty.NettyHttpClient
import zio.aws.s3.S3
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
```

## Setting the Endpoint in Code

`CommonAwsConfig` has an `endpointOverride` field. We set it to the local server's URL, together with fixed credentials and a region. LocalStack listens on port 4566 and accepts any credentials:

```scala
val localConfig: CommonAwsConfig =
  CommonAwsConfig(
    region = Some(Region.US_EAST_1),
    credentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")),
    endpointOverride = Some(URI.create("http://localhost:4566")),
    commonClientConfig = None
  )

val localAwsConfig: ZLayer[Any, Throwable, AwsConfig] =
  (NettyHttpClient.default ++ ZLayer.succeed(localConfig)) >>> AwsConfig.configured()
```

The code above:

- `region` must be set, because the AWS SDK signs every request for a region, even a local one.
- `StaticCredentialsProvider` returns the same keys on every request. Use it only for a local server; for AWS, keep the `DefaultCredentialsProvider` chain.
- `endpointOverride` replaces the AWS endpoint of every client that uses this `AwsConfig`.
- `commonClientConfig = None` keeps the AWS SDK's default timeouts and retries.

Any service layer built on `localAwsConfig` now talks to the local server, for example `localAwsConfig >>> S3.live`.

## Using Path-Style Addressing for S3

For S3, we also turn on path-style addressing, so that requests go to `http://localhost:4566/my-bucket/key`. The setting belongs to the S3 client builder, so we pass it to `S3.customized`, which applies a function to the builder after `AwsConfig` has configured it:

```scala
val localS3: ZLayer[Any, Throwable, S3] =
  localAwsConfig >>> S3.customized(_.forcePathStyle(true))
```

Other services do not need this step.

## Loading the Settings from Configuration

To switch between AWS and the local server without changing code, load `CommonAwsConfig` with the `commonAwsConfig` descriptor. It reads `region`, `endpointOverride` and a `credentials` section whose `type` is `static`, with `accessKeyId` and `secretAccessKey`. To see the result, we load it from an in-memory `ConfigProvider`; in a real application the same keys come from environment variables or a HOCON file:

```scala
val localProvider: ConfigProvider =
  ConfigProvider.fromMap(
    Map(
      "region"                      -> "us-east-1",
      "endpointOverride"            -> "http://localhost:4566",
      "credentials.type"            -> "static",
      "credentials.accessKeyId"     -> "test",
      "credentials.secretAccessKey" -> "test"
    )
  )
// localProvider: ConfigProvider = zio.ConfigProvider$$anon$9@4edc5245

val loaded: CommonAwsConfig =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(localProvider.load(commonAwsConfig)).getOrThrowFiberFailure()
  }
// loaded: CommonAwsConfig = CommonAwsConfig(
//   region = Some(value = us-east-1),
//   credentialsProvider = e(credentials=AwsCredentials(accessKeyId=test, providerName=e)),
//   endpointOverride = Some(value = http://localhost:4566),
//   commonClientConfig = None
// )

loaded.endpointOverride
// res0: Option[URI] = Some(value = http://localhost:4566)
loaded.region
// res1: Option[Region] = Some(value = us-east-1)
```

In an application, the provider is installed once, and the layer reads it with `ZIO.config`:

```scala
val awsConfigFromSource: ZLayer[Any, Throwable, AwsConfig] =
  (NettyHttpClient.default ++ ZLayer.fromZIO(ZIO.config(commonAwsConfig))) >>> AwsConfig.configured()
```

## Writing the Endpoint with Its Scheme

The `endpointOverride` value must start with `http://` or `https://`. A value without a scheme, such as `localhost:4566`, is not a valid endpoint for the AWS SDK, so the descriptor rejects it when the configuration is loaded instead of failing on the first request:

```scala
Unsafe.unsafe { implicit unsafe =>
  Runtime.default.unsafe
    .run(
      ConfigProvider
        .fromMap(Map("endpointOverride" -> "localhost:4566"))
        .load(commonAwsConfig)
        .either
    )
    .getOrThrowFiberFailure()
    .left
    .map(_.getMessage)
}
// res2: Either[String, CommonAwsConfig] = Left(
//   value = "(Invalid data at endpointOverride: Invalid endpoint URI 'localhost:4566': scheme must be 'http' or 'https' (e.g., 'http://localhost:9000'))"
// )
```

## Going Further

- The [Configure Credentials and Region](guide-configure-credentials-and-region.md) guide covers the other `CommonAwsConfig` settings.
- The [Configuration](configuration.md) reference lists every configuration key.
- The [Stream S3 Objects](guide-stream-s3-objects.md) guide uploads and downloads objects, which works the same against a local server.
