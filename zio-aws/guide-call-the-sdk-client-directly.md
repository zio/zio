# Customize Clients and Call the AWS SDK Directly

> Change AWS SDK client settings that CommonAwsConfig does not cover, for one service or for all of them, and call SDK features such as waiters through the raw client.

In this guide, we will reach past the generated wrappers to the AWS Java SDK when a program needs something the wrappers do not offer. By the end, you will know how to change the SDK client builder of one service or of every service, how to call the raw SDK client, and how to convert between SDK and ZIO AWS model types.

We'll take an incremental approach: first we customize one service, then every service, and finally we call the SDK client itself.

## The Problem

ZIO AWS wraps every operation of every service, and `CommonAwsConfig` covers the settings most programs need: region, credentials, endpoint, timeouts and headers. The AWS SDK has more. Each client builder has service-specific settings, the SDK has features that are not operations, such as waiters that poll until a resource reaches a state, and some programs already have code written against the SDK types.

None of this needs a fork of the library. Every generated service exposes the SDK client it wraps, and every layer constructor accepts a function that changes the SDK builder.

## Prerequisites

Add the core module, an HTTP client and the service module:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-aws-core"  % "7.59.5.2",
  "dev.zio" %% "zio-aws-netty" % "7.59.5.2",
  "dev.zio" %% "zio-aws-ec2"   % "7.59.5.2"
)
```

The examples use these imports:

```scala
import zio._
import zio.aws.core.AwsError
import zio.aws.core.config.{AwsConfig, ClientCustomization}
import zio.aws.ec2.Ec2
import zio.aws.ec2.model.{DescribeRegionsRequest, Region}
import zio.aws.netty.NettyHttpClient
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder
import software.amazon.awssdk.core.client.config.{ClientOverrideConfiguration, SdkAdvancedClientOption}
import software.amazon.awssdk.services.ec2.{model => sdk}
```

The last import gives the SDK's EC2 model package the short name `sdk`, so that `sdk.DescribeInstancesRequest` is the Java type and `DescribeRegionsRequest` is the ZIO AWS case class.

## Customizing One Service Client

Every service has a `customized` constructor next to `live`, such as `Ec2.customized`. It takes a function from the SDK builder to the SDK builder, and applies it after `AwsConfig` has applied the common settings, so the function has the last word. This EC2 client gets its own API call timeout:

```scala
val ec2WithTimeout: ZLayer[AwsConfig, Throwable, Ec2] =
  Ec2.customized(
    _.overrideConfiguration(
      ClientOverrideConfiguration.builder().apiCallTimeout(java.time.Duration.ofSeconds(5)).build()
    )
  )
```

The function receives an `Ec2AsyncClientBuilder`, so every EC2-specific setting is available. Other services work the same way, for example `S3.customized(_.forcePathStyle(true))` for S3.

:::caution
`overrideConfiguration` replaces the whole override configuration that `AwsConfig.configured()` built from `CommonClientConfig`. Set every value you need in it, or customize through `AwsConfig` instead.
:::

`Ec2.scoped` takes the same function and returns the client as a scoped `ZIO` instead of a layer, which is useful to build several clients with different settings, such as one per region.

## Customizing Every Service Client

To change the builder of every service in the program, implement `ClientCustomization` and pass it to `AwsConfig.customized`. Its `customize` method is generic, because the same customization applies to the builders of all services:

```scala
val userAgentSuffix: ClientCustomization =
  new ClientCustomization {
    override def customize[Client, Builder <: AwsClientBuilder[Builder, Client]](
        builder: Builder
    ): Builder =
      builder.overrideConfiguration(
        ClientOverrideConfiguration
          .builder()
          .putAdvancedOption(SdkAdvancedClientOption.USER_AGENT_SUFFIX, "inventory-app")
          .build()
      )
  }

val customizedAwsConfig: ZLayer[Any, Throwable, AwsConfig] =
  NettyHttpClient.default >>> AwsConfig.customized(userAgentSuffix)
```

The code above adds `inventory-app` to the user agent of every request, which makes the program's calls easy to find in AWS CloudTrail. `ClientCustomization.None` is the customization that changes nothing; `AwsConfig.default` uses it.

`AwsConfig.customized` does not read a `CommonAwsConfig`, so region and credentials come from the SDK's defaults unless the customization sets them, for example with `builder.region(...)`.

## Calling the SDK Client

Every generated service has an `api` field that holds the SDK's async client, such as `Ec2AsyncClient`. Use it for SDK features that are not operations. EC2's waiter polls `DescribeInstances` until an instance is running:

```scala
def waitUntilRunning(instanceId: String): ZIO[Ec2, AwsError, Unit] =
  ZIO.serviceWithZIO[Ec2] { ec2 =>
    ZIO
      .fromCompletionStage(
        ec2.api
          .waiter()
          .waitUntilInstanceRunning(sdk.DescribeInstancesRequest.builder().instanceIds(instanceId).build())
      )
      .mapError(AwsError.fromThrowable)
      .unit
  }
```

The code above:

- `ZIO.serviceWithZIO[Ec2]` reads the `Ec2` service, and `ec2.api` is its `Ec2AsyncClient`.
- The SDK method returns a `CompletableFuture`, which `ZIO.fromCompletionStage` turns into an effect.
- `AwsError.fromThrowable` turns the SDK exception into the same `AwsError` that generated operations fail with, so callers handle both the same way.

:::note
Calls through `api` bypass the aspects attached to the service with `@@@`, because aspects wrap the generated operations only.
:::

## Converting Between SDK and ZIO AWS Models

When code mixes both APIs, convert the models at the boundary. Every request case class has `buildAwsValue()`, which builds the SDK object:

```scala
DescribeRegionsRequest(allRegions = true).buildAwsValue()
// res0: software.amazon.awssdk.services.ec2.model.DescribeRegionsRequest = DescribeRegionsRequest(AllRegions=true)
```

In the other direction, the companion's `wrap` method, here `Region.wrap`, turns an SDK object into the read-only wrapper, and `ReadOnly#asEditable` turns that into the case class:

```scala
val wrapped: Region.ReadOnly =
  Region.wrap(sdk.Region.builder().regionName("eu-west-1").build())
// wrapped: Region.ReadOnly = zio.aws.ec2.model.Region$Wrapper@6245db57

wrapped.regionName
// res1: prelude.data.Optional[String] = Present(get = "eu-west-1")
wrapped.asEditable
// res2: Region = Region(
//   optInStatus = Absent,
//   geography = Present(get = List()),
//   regionName = Present(get = "eu-west-1"),
//   endpoint = Absent
// )
```

## Going Further

- The [Wrappers](wrappers.md) reference describes the generated service and model types.
- The [Configuration](configuration.md) reference describes `AwsConfig` and its constructors.
- The [Configure Credentials and Region](guide-configure-credentials-and-region.md) guide covers the settings `CommonAwsConfig` provides.
