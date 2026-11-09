# AWS Parameter Store

> AWS Systems Manager Parameter Store keeps configuration values under hierarchical names such as `/myapp/prod/db/url`. zio-config can read every parameter under one base path and expose them as a `ConfigProvider`, so the same `Config` descriptions you use for files and strings work with Parameter Store too.

## Choosing a Module

zio-config offers two modules for Parameter Store. They expose the same entry point, `ConfigProvider.fromParameterStore`, and differ only in the AWS client library underneath:

| Module               | AWS client library             |
| -------------------- | ------------------------------ |
| `zio-config-aws`     | AWS SDK for Java v1            |
| `zio-config-zio-aws` | `zio-aws`, a ZIO-native client |

Both modules define classes in the same package, `zio.config.aws.parameterstore`, so add only one of them to a project. The examples below use `zio-config-aws`. To use the ZIO-native client instead, replace the module name in the dependency and follow the ZIO-native example further down.

To add the SDK-based module to an sbt build:

```scala
libraryDependencies += "dev.zio" %% "zio-config-aws" % "4.1.2"
```

## Reading a Base Path

`ConfigProvider.fromParameterStore` loads all parameters under the base path in one call, including parameters in nested paths. The base path prefix is removed from each name, and the remaining path segments become config keys separated by `/`. A parameter named `/myapp/prod/db/url` is therefore read as the key `db/url`. Pass the base path without a trailing slash, because the prefix removal expects exactly one `/` after the base path.

To read the `url` and `port` values under `/myapp/prod/db`:

```scala
import zio._
import zio.config._
import zio.config.aws.parameterstore._

final case class Database(url: String, port: Int)

val databaseConfig: Config[Database] =
  (Config.string("url") zip Config.int("port")).nested("db").map(pair => Database(pair._1, pair._2))
```

Next, build the provider from the base path and load the config from it:

```scala
val loadDatabase: ZIO[Any, Config.Error, Database] =
  for {
    provider <- ConfigProvider.fromParameterStore("/myapp/prod")
    database <- provider.load(databaseConfig)
  } yield database
```

The provider reads the parameters once, when `ConfigProvider.fromParameterStore` runs. Later changes in Parameter Store are not seen until you build a new provider.

## Credentials and Region

When you call `ConfigProvider.fromParameterStore` with only a base path, zio-config creates the client with `AWSSimpleSystemsManagementClientBuilder.defaultClient()`. That client follows the AWS default credential chain for credentials and region, so environment variables, a shared profile, and an instance role all work without code changes.

To control the client yourself, pass a `Task` that builds an `AWSSimpleSystemsManagement` client as the second argument:

```scala
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder

val withCustomClient: ZIO[Any, Config.Error, ConfigProvider] =
  ConfigProvider.fromParameterStore(
    "/myapp/prod",
    ZIO.attempt(AWSSimpleSystemsManagementClientBuilder.standard().withRegion("us-east-1").build())
  )
```

With the `zio-config-zio-aws` module, the caller provides an `Ssm` service instead. The following sketch builds the `Ssm` layer from the ZIO-native client and loads the same `databaseConfig`:

```scala
import zio._
import zio.aws.core.config.AwsConfig
import zio.aws.netty.NettyHttpClient
import zio.aws.ssm.Ssm
import zio.config._
import zio.config.aws.parameterstore._

val loadWithZioAws: ZIO[Any, Config.Error, Database] =
  ZIO
    .serviceWithZIO[Ssm] { ssm =>
      ConfigProvider.fromParameterStore("/myapp/prod", ssm).flatMap(_.load(databaseConfig))
    }
    .provide(NettyHttpClient.default, AwsConfig.default, Ssm.live)
```

## Required Permissions

The identity that runs the application needs `ssm:GetParametersByPath` on the base path, because the provider reads parameters recursively. The provider also requests decrypted values, so the identity needs `kms:Decrypt` on the KMS key that encrypts any `SecureString` parameters under that path.

## Errors

If an AWS call fails, `ConfigProvider.fromParameterStore` returns a `Config.Error` whose message contains the text of the original exception. Handle it like any other `Config.Error`, for example with `catchAll` or by logging it at startup.
