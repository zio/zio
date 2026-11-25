# Configuration

> Reference for AwsConfig and its three constructors, CommonAwsConfig, CommonClientConfig, ClientCustomization and the ZIO Config keys that describe them.

Each service layer depends on an `AwsConfig` layer. `AwsConfig` sets up the AWS Java SDK's async client of every service: it applies the common settings to the client builder and plugs in the [HTTP client](http.md). One `AwsConfig` layer is shared by every service in a program.

## AwsConfig

`AwsConfig` is a trait in `zio.aws.core.config` with two methods, which every generated service calls while it builds its client:

```scala
trait AwsConfig {
  def configure[Client, Builder <: AwsClientBuilder[Builder, Client]](builder: Builder): Task[Builder]

  def configureHttpClient[Client, Builder <: AwsAsyncClientBuilder[Builder, Client]](
      builder: Builder,
      serviceCaps: ServiceHttpCapabilities
  ): Task[Builder]
}
```

You rarely implement it yourself; the companion object provides three layers, described below. All three need an `HttpClient`.

### AwsConfig.default

`AwsConfig.default: ZLayer[HttpClient, Nothing, AwsConfig]` sets only the HTTP client. The region, credentials, endpoint, timeouts and retries are left to the AWS SDK, which reads them from its standard sources, such as the `AWS_REGION` environment variable and the `~/.aws` profile files.

### AwsConfig.configured

`AwsConfig.configured(): ZLayer[HttpClient & CommonAwsConfig, Nothing, AwsConfig]` is the recommended layer. It also needs a `CommonAwsConfig`, which you can build in code with `ZLayer.succeed(CommonAwsConfig(...))` or load from any [ZIO Config](https://zio.dev/zio-config/) source with `ZLayer.fromZIO(ZIO.config(commonAwsConfig))`, using the descriptor from `zio.aws.core.config.descriptors`. The [Configure Credentials and Region](guide-configure-credentials-and-region.md) guide shows both.

The layer applies the fields of `CommonAwsConfig` to every client builder:

- `region`, when set, becomes the client's region.
- `credentialsProvider` is always applied.
- `endpointOverride`, when set, replaces the AWS endpoint, for example to reach [LocalStack](guide-use-a-local-endpoint.md).
- `commonClientConfig`, when set, becomes the client's override configuration with the extra headers, timeouts and profile name, and **turns off the AWS SDK's own retries**. Add retries with an [aspect](guide-add-retries.md) instead. When it is `None`, the SDK keeps its default retry behaviour.

### AwsConfig.customized

`AwsConfig.customized(customization: ClientCustomization): ZLayer[HttpClient, Nothing, AwsConfig]` passes every client builder to a `ClientCustomization`, and does not read a `CommonAwsConfig`. `ClientCustomization` has one generic method, so that the same code works for the builders of all services:

```scala
trait ClientCustomization {
  def customize[Client, Builder <: AwsClientBuilder[Builder, Client]](builder: Builder): Builder
}
```

`ClientCustomization.None` returns the builder unchanged; `AwsConfig.default` is `AwsConfig.customized(ClientCustomization.None)`. The [Customize Clients](guide-call-the-sdk-client-directly.md) guide implements one. To change the builder of one service only, use the service's `customized` constructor, described in [Wrappers](wrappers.md).

## CommonAwsConfig

`CommonAwsConfig` holds the settings that `AwsConfig.configured()` applies:

```scala
case class CommonAwsConfig(
    region: Option[Region],
    credentialsProvider: AwsCredentialsProvider,
    endpointOverride: Option[URI],
    commonClientConfig: Option[CommonClientConfig]
)

case class CommonClientConfig(
    extraHeaders: Map[String, List[String]],
    apiCallTimeout: Option[Duration],
    apiCallAttemptTimeout: Option[Duration],
    defaultProfileName: Option[String]
)
```

`Region`, `AwsCredentialsProvider` and `URI` are the AWS SDK and Java types, and `Duration` is ZIO's. `apiCallTimeout` limits a whole call, including retries, and `apiCallAttemptTimeout` limits one HTTP attempt.

## Configuration Keys

The `commonAwsConfig` descriptor in `zio.aws.core.config.descriptors` reads a `CommonAwsConfig` from these keys:

- `region` is an AWS region name, such as `eu-west-1`. It is optional.
- `credentials.type` selects the credentials provider: `default` (the SDK's default chain, also used when the `credentials` section is missing), `anonymous`, `instance-profile` or `static`. The `static` type also reads `credentials.accessKeyId` and `credentials.secretAccessKey`.
- `endpointOverride` is an optional URI. It must start with `http://` or `https://`; a value such as `localhost:9000` fails when the configuration is loaded.
- `client` is the optional `CommonClientConfig` section, with `extraHeaders` (a list of `name` and `value` entries), `apiCallTimeout`, `apiCallAttemptTimeout` and `defaultProfileName`.

The following table is generated from the descriptor. The HTTP clients have their own keys, listed on the [HTTP Clients](http.md) page:

## Configuration Details

|FieldName|Format                       |Description|Sources|
|---      |---                          |---        |---    |
|         |all-of|           |       |

#### Field Descriptions

|FieldName        |Format                             |Description                                                                                  |Sources|
|---              |---                                |---                                                                                          |---    |
|region           |primitive                          |a text property, AWS region to connect to                                                    |       |
|                 |any-one-of|                                                                                             |       |
|endpointOverride |primitive                          |a text property, Overrides the AWS service endpoint (must include http:// or https:// scheme)|       |
|client|all-of                  |Common settings for AWS service clients                                                      |       |

#### Field Descriptions

|FieldName|Format   |Description                                  |Sources|
|---      |---      |---                                          |---    |
|type     |map      |a text property, AWS credentials provider    |       |
|         |primitive|a constant property, AWS credentials provider|       |

### client

|FieldName                    |Format               |Description                                                                                     |Sources|
|---                          |---                  |---                                                                                             |---    |
|extraHeaders|list|Extra headers to be sent with each request                                                      |       |
|apiCallTimeout               |primitive            |a duration property, Amount of time to allow the client to complete the execution of an API call|       |
|apiCallAttemptTimeout        |primitive            |a duration property, Amount of time to wait for the HTTP request to complete before giving up   |       |
|defaultProfileName           |primitive            |a text property, Default profile name                                                           |       |

### extraHeaders

|FieldName|Format   |Description                  |Sources|
|---      |---      |---                          |---    |
|name     |primitive|a text property, Header name |       |
|value    |list     |a text property, Header value|       |

The descriptor's building blocks, `region`, `httpUri`, `credentialsProvider`, `awsCredentials` and `commonClientConfig`, are public too, so you can reuse them in your own configuration types.

## Sharing One AwsConfig

Every generated service layer depends on `AwsConfig`, so one `AwsConfig` layer serves several services, which then share the HTTP client and the common settings:

```scala
import zio._
import zio.aws.core.config.AwsConfig
import zio.aws.ec2.Ec2
import zio.aws.elasticbeanstalk.ElasticBeanstalk
import zio.aws.netty.NettyHttpClient

val awsConfig: ZLayer[Any, Throwable, AwsConfig] =
  NettyHttpClient.default >>> AwsConfig.default

val services: ZLayer[Any, Throwable, Ec2 & ElasticBeanstalk] =
  awsConfig >>> (Ec2.live ++ ElasticBeanstalk.live)
```
