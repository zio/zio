# Configure Credentials and Region

> Set the AWS region, credentials, endpoint and client timeouts for ZIO AWS clients, either in code or from a ZIO Config source.

In this guide, we will configure the region, the credentials and the client timeouts that every ZIO AWS client uses. By the end, you will know how to use the defaults, how to set the values in code and how to load them from a configuration source.

We'll take an incremental approach: first we use the defaults, then we set the values in code, and finally we load them from configuration.

## The Problem

Every AWS call needs a region, credentials and often a timeout. The AWS Java SDK can find these values on its own, but it looks in several places in a fixed order, such as environment variables, system properties and profile files. A program that runs on a laptop, in a container and in a test may need a different value in each place.

If the values are hard-coded in each service client, a change means editing every place that builds a client. If they are read from the environment without a shared place, each client can end up with a different setting.

ZIO AWS solves this with one common configuration layer, `AwsConfig`, that every service module depends on. The settings live in one `CommonAwsConfig` value, which you can build in code or load from configuration.

## Prerequisites

Add the core module, one service module and one HTTP client module to your build:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-aws-core"  % "7.59.5.2",
  "dev.zio" %% "zio-aws-ec2"   % "7.59.5.2",
  "dev.zio" %% "zio-aws-netty" % "7.59.5.2"
)
```

The examples in this guide use these imports:

```scala
import zio._
import zio.aws.core.config.{AwsConfig, CommonAwsConfig, CommonClientConfig}
import zio.aws.core.config.descriptors.commonAwsConfig
import zio.aws.ec2.Ec2
import zio.aws.netty.NettyHttpClient
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
```

## Using the Defaults

`AwsConfig.default` depends only on an `HttpClient`. It does not set the region, the credentials or any timeout, so the AWS SDK decides these on its own. This is the smallest setup, and it works when the environment already has the values:

```scala
val defaultLayers: ZLayer[Any, Throwable, Ec2] =
  NettyHttpClient.default >>> AwsConfig.default >>> Ec2.live
```

The layer chain is read from left to right. `NettyHttpClient.default` provides the `HttpClient`, `AwsConfig.default` provides the `AwsConfig`, and `Ec2.live` provides the `Ec2` service.

## Setting the Values in Code

To set the values explicitly, build a `CommonAwsConfig` and pass it to `AwsConfig.configured()`. This layer depends on an `HttpClient` and on a `CommonAwsConfig`, so we provide both:

```scala
val explicitConfig: CommonAwsConfig = CommonAwsConfig(
  region = Some(Region.EU_WEST_1),
  credentialsProvider = DefaultCredentialsProvider.builder().build(),
  endpointOverride = None,
  commonClientConfig = Some(
    CommonClientConfig(
      extraHeaders = Map.empty,
      apiCallTimeout = Some(Duration.fromSeconds(30)),
      apiCallAttemptTimeout = Some(Duration.fromSeconds(10)),
      defaultProfileName = None
    )
  )
)

val configuredLayers: ZLayer[Any, Throwable, Ec2] =
  (NettyHttpClient.default ++ ZLayer.succeed(explicitConfig)) >>> AwsConfig.configured() >>> Ec2.live
```

The code above:

- `region` sets the region for every client. `Some(Region.EU_WEST_1)` is the same as the `region` key in configuration.
- `credentialsProvider` chooses where the credentials come from. `DefaultCredentialsProvider` uses the same chain as the AWS SDK, so the same environment works. Pass `StaticCredentialsProvider` only for local tests, and never commit literal keys.
- `endpointOverride` sends requests to another URL, for example a local emulator. `None` keeps the regular AWS endpoint.
- `commonClientConfig` holds the timeouts and extra headers. `apiCallTimeout` limits the whole call, including retries, and `apiCallAttemptTimeout` limits one HTTP attempt.

:::note
When `commonClientConfig` is `Some`, `AwsConfig.configured()` also turns off the SDK's own retries. The recommended way to add retries is an aspect, as described in [Add Aspects](./guide-add-aspects.md). When `commonClientConfig` is `None`, the SDK keeps its default retry behaviour.
:::

## Loading the Values from Configuration

To keep the values out of the code, read them with ZIO Config. The `commonAwsConfig` descriptor in `zio.aws.core.config.descriptors` describes all the keys, so the same layer works with any ZIO Config provider:

```scala
val configFromSource: ZLayer[Any, Config.Error, CommonAwsConfig] =
  ZLayer.fromZIO(ZIO.config(commonAwsConfig))

val configuredFromSource: ZLayer[Any, Throwable, Ec2] =
  (NettyHttpClient.default ++ configFromSource.mapError(e => new RuntimeException(e.toString))) >>> AwsConfig.configured() >>> Ec2.live
```

The keys are `region`, `credentials` (with a `type` of `default`, `anonymous`, `instance-profile` or `static`), `endpointOverride` and `client` (with `extraHeaders`, `apiCallTimeout`, `apiCallAttemptTimeout` and `defaultProfileName`). The [Configuration](./configuration.md) page lists every key and its format.

:::warning
Do not put literal access keys in a configuration file that is committed to source control. Use the `default` credentials type and let the AWS SDK read the keys from its own profile or the environment.
:::

## Going Further

- The [Configuration](./configuration.md) reference lists every `CommonAwsConfig` key.
- The [Use a Local Endpoint](./guide-use-a-local-endpoint.md) guide points the clients at LocalStack or MinIO.
- The HTTP client has its own settings. See [HTTP Clients](./http.md), and [Choose an HTTP Client](./guide-choose-http-client.md) for guidance on which one to use.
