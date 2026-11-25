# Configure Credentials and Region

> ZIO S3 builds its S3 client from a region and an AWS credentials source. The `live` and `liveZIO` functions take the region as a `software.amazon.awssdk.regions.Region`, and the credentials either as a value or as a provider. This page shows how to choose each source, and the [settings reference](./settings.md) lists every parameter.

## Use Static Credentials

To pass an access key and secret key directly, use `live` with `AwsBasicCredentials`:

```scala
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._

val s3Layer: Layer[S3Exception, S3] =
  live(Region.AF_SOUTH_1, AwsBasicCredentials.create("key", "secret"))
```

For temporary credentials, such as those issued by AWS STS, use `AwsSessionCredentials` in place of `AwsBasicCredentials`, or the `providers.session` provider.

## Use a Credentials Provider

To resolve credentials lazily, pass a provider from `zio.s3.providers` to `liveZIO`. Each provider is an effect that builds an AWS `AwsCredentialsProvider`:

```scala
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._
import zio.s3.providers._

// environment variables
val fromEnv: ZLayer[Any, S3Exception, S3] =
  liveZIO(Region.AF_SOUTH_1, env)

// Java system properties
val fromSystem: ZLayer[Any, S3Exception, S3] =
  liveZIO(Region.AF_SOUTH_1, system)

// the EC2 instance profile
val fromInstance: ZLayer[Scope, S3Exception, S3] =
  liveZIO(Region.AF_SOUTH_1, instanceProfile)

// web identity token, used by workloads such as EKS service accounts
val fromWebIdentity: ZLayer[Scope, S3Exception, S3] =
  liveZIO(Region.AF_SOUTH_1, webIdentity)

// the AWS SDK default chain, which tries several sources in order
val fromDefaultChain: ZLayer[Scope, S3Exception, S3] =
  liveZIO(Region.AF_SOUTH_1, default)
```

The providers `env` and `system` need no scope, so their layers have type `ZLayer[Any, S3Exception, S3]`. The providers `instanceProfile`, `webIdentity`, `default`, `profile`, and `container` need a `Scope`, because they hold resources that the scope closes.

To pass a fixed key pair through a provider, use `providers.basic`, or `providers.session` for temporary credentials. These need no scope:

```scala
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._
import zio.s3.providers._

val fromKeys: ZLayer[Any, S3Exception, S3] =
  liveZIO(Region.AF_SOUTH_1, basic("key", "secret"))

val fromTemporaryKeys: ZLayer[Any, S3Exception, S3] =
  liveZIO(Region.AF_SOUTH_1, session("key", "secret", "token"))
```

To read the credentials that Amazon ECS or EKS inject into a container, use `container`. It needs a `Scope`:

```scala
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._
import zio.s3.providers._

val fromContainer: ZLayer[Scope, S3Exception, S3] =
  liveZIO(Region.AF_SOUTH_1, container)
```

To read a named profile from `~/.aws/credentials`, use `profile` with `Some(name)`:

```scala
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._
import zio.s3.providers._

val fromProfile: ZLayer[Scope, S3Exception, S3] =
  liveZIO(Region.AF_SOUTH_1, profile(Some("dev")))
```

## Use Custom Credential Logic

To fetch credentials from your own source, such as a secret store, build an effect that returns `AwsCredentials`, pass it to `settings`, and feed the result into the `live` layer that takes `S3Settings`:

```scala
import software.amazon.awssdk.auth.credentials.{ AwsBasicCredentials, AwsCredentials }
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._

// replace with a call to your secret store
val fetchCredentials: ZIO[Any, S3Exception, AwsCredentials] =
  ZIO.succeed(AwsBasicCredentials.create("key", "secret"))

val s3Layer: ZLayer[Any, S3Exception, S3] =
  settings(Region.AF_SOUTH_1, fetchCredentials) >>> live
```

## Handle Credential Failures

When a provider cannot resolve credentials, building the layer fails. The provider in `zio.s3.providers` reports `InvalidCredentials`, but `Live.connect` wraps that failure in `ConnectionError` when it builds the client, so the layer's error is `ConnectionError`, which extends `S3Exception`. Handle it where you build the layer:

```scala
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.S3Exception
import zio._
import zio.s3._
import zio.s3.errors.ConnectionError
import zio.s3.providers._

val s3Layer: ZLayer[Scope, S3Exception, S3] =
  liveZIO(Region.AF_SOUTH_1, instanceProfile)

val checked: ZIO[Scope, S3Exception, Boolean] =
  s3Layer.build.as(true).catchSome { case _: ConnectionError => ZIO.succeed(false) }
```

See the [errors reference](./errors.md) for the full error hierarchy.

## Choose the Region

Every layer takes a `Region` from `software.amazon.awssdk.regions.Region`, such as `Region.CA_CENTRAL_1`. For S3-compatible storage that uses a region name the AWS SDK does not know, see [S3-compatible endpoints](./connect-to-an-s3-compatible-store.md).
