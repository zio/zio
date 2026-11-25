# Choose an HTTP Client

> Pick the Netty, http4s, Akka HTTP or AWS CRT HTTP client for ZIO AWS and set its protocol and connection options.

In this guide, we will choose an HTTP client for ZIO AWS, build it as a layer and tune its connection settings. By the end, you will know which module to add, which layer to provide and how to set the protocol.

Every AWS client sends its requests through an `HttpClient` layer, so the choice affects connection pooling, HTTP/2 support and the other libraries already in your application.

## The Problem

The AWS Java SDK bundles Netty and uses it when nothing else is configured. That default works, but it is a poor fit when your application already runs on another HTTP stack. A second client adds a second connection pool, a second set of threads and a second set of settings to tune.

ZIO AWS separates the choice from the rest of the library. Each HTTP client is its own module that provides an `HttpClient` layer, and `AwsConfig` takes that layer as a dependency. Changing the client means changing one layer, not every service.

## Prerequisites

The core module is always needed. Add one of these HTTP client modules next to it:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-aws-core"      % "7.59.5.2",
  "dev.zio" %% "zio-aws-netty"     % "7.59.5.2",
  "dev.zio" %% "zio-aws-http4s"    % "7.59.5.2",
  "dev.zio" %% "zio-aws-akka-http" % "7.59.5.2",
  "dev.zio" %% "zio-aws-crt-http"  % "7.59.5.2"
)
```

The examples below use these imports:

```scala
import zio._
import zio.aws.core.config.AwsConfig
import zio.aws.core.httpclient.{HttpClient, Protocol}
import zio.aws.ec2.Ec2
import zio.aws.netty.NettyHttpClient
import zio.aws.http4s.Http4sClient
import zio.aws.crt.AwsCrtHttpClient
```

## Choosing a Client

Use the table below as a starting point. The Netty client is the default choice, because it is the one the AWS SDK uses itself.

| Client    | Module              | Layer to provide           | Choose it when                                                                         |
| --------- | ------------------- | -------------------------- | -------------------------------------------------------------------------------------- |
| Netty     | `zio-aws-netty`     | `NettyHttpClient.default`  | You have no other HTTP stack (recommended)                                             |
| http4s    | `zio-aws-http4s`    | `Http4sClient.default`     | Your application already uses http4s and Blaze                                         |
| Akka HTTP | `zio-aws-akka-http` | `AkkaHttpClient.client()`  | Your application already runs an Akka actor system (also needs an `ActorSystem` layer) |
| AWS CRT   | `zio-aws-crt-http`  | `AwsCrtHttpClient.default` | You want the AWS Common Runtime implementation                                         |

Each layer has the same shape. It provides an `HttpClient`, and `AwsConfig` consumes it:

```scala
val withNetty: ZLayer[Any, Throwable, Ec2] =
  NettyHttpClient.default >>> AwsConfig.default >>> Ec2.live

val withHttp4s: ZLayer[Any, Throwable, Ec2] =
  Http4sClient.default >>> AwsConfig.default >>> Ec2.live
```

Changing the HTTP client leaves the service layers untouched, because they only depend on `AwsConfig`.

## Setting the Protocol

The Netty client can speak HTTP/1.1, HTTP/2 or both. `NettyHttpClient.default` uses HTTP/1.1. `NettyHttpClient.dual` uses HTTP/2 for the services that support it and HTTP/1.1 for the rest:

```scala
val dualNetty: ZLayer[Any, Throwable, HttpClient] = NettyHttpClient.dual
```

To choose the protocol and adjust the builder, call `NettyHttpClient.customized`. Its first argument is a `Protocol`, and its second is a function that receives the Netty builder:

```scala
val tunedNetty: ZLayer[Any, Throwable, HttpClient] =
  NettyHttpClient.customized(
    Protocol.Dual,
    _.maxConcurrency(50)
  )
```

The `Protocol` values are `Protocol.Http11`, `Protocol.Http2` and `Protocol.Dual`. The `NettyHttpClient.customized` function returns a layer that you provide in the same way as `NettyHttpClient.default`.

:::tip
For a Netty client configured from a ZIO Config source, use `NettyHttpClient.configured`. It needs a `NettyClientConfig` layer, which you can load with `ZIO.config(nettyClientConfig)` from `zio.aws.netty.descriptors`. The [HTTP Clients](./http.md) reference lists every key.
:::

## Using http4s

The http4s client is built on the Blaze client. `Http4sClient.default` uses the default settings, and `Http4sClient.customized` receives a function that changes the `BlazeClientBuilder`. Pass `identity` to keep the defaults, and change the builder when you need other settings:

```scala
val tunedHttp4s: ZLayer[Any, Throwable, HttpClient] =
  Http4sClient.customized(identity)
```

The http4s client needs no extra layers, so it can replace Netty in a project that already has http4s on its classpath.

## Using AWS CRT

The AWS Common Runtime client is a native implementation. `AwsCrtHttpClient.default` uses the defaults, and `AwsCrtHttpClient.customized` changes its builder:

```scala
val crt: ZLayer[Any, Throwable, HttpClient] = AwsCrtHttpClient.default
```

## Using Akka HTTP

The Akka HTTP client needs an `ActorSystem` in its environment. If your application already has one, provide its layer; otherwise create one as a scoped layer that terminates the actor system when the program ends:

```scala
import akka.actor.ActorSystem
import zio.aws.akkahttp.AkkaHttpClient

val actorSystem: ZLayer[Any, Throwable, ActorSystem] =
  ZLayer.scoped(
    ZIO.acquireRelease(ZIO.attempt(ActorSystem("zio-aws")))(system =>
      ZIO.fromFuture(_ => system.terminate()).orDie
    )
  )

val withAkka: ZLayer[Any, Throwable, Ec2] =
  actorSystem >>> AkkaHttpClient.client() >>> AwsConfig.default >>> Ec2.live
```

`AkkaHttpClient.client()` reads its connection settings from the actor system's configuration. Pass `connectionPoolSettings` to override them in code.

## Calling Services That Need HTTP/2

Most AWS services use HTTP/1.1, but the event-streaming operations of Kinesis (`subscribeToShard`) and Transcribe Streaming (`startStreamTranscription`) need HTTP/2. ZIO AWS marks the `zio-aws-kinesis` and `zio-aws-transcribestreaming` clients as HTTP/2 services, and the HTTP client layer decides which protocol each client gets:

| Netty layer                                   | HTTP/2 services | Other services | Use it when                            |
| --------------------------------------------- | --------------- | -------------- | -------------------------------------- |
| `NettyHttpClient.default` (`Protocol.Http11`) | HTTP/1.1        | HTTP/1.1       | You call no HTTP/2 operation           |
| `NettyHttpClient.dual` (`Protocol.Dual`)      | HTTP/2          | HTTP/1.1       | You call Kinesis or Transcribe streams |
| `customized(Protocol.Http2)`                  | HTTP/2          | fails          | Every service you use supports HTTP/2  |

With `Protocol.Http2`, building a client for a service that does not support HTTP/2 fails with `UnsupportedOperationException`. The http4s, Akka HTTP and AWS CRT layers have no protocol setting: they give every service the same underlying client, and the http4s Blaze client speaks HTTP/1.1 only. Use Netty with `NettyHttpClient.dual` for the HTTP/2 operations.

To share one Netty layer between Kinesis and other services, provide `NettyHttpClient.dual`:

```scala
val sharedDual: ZLayer[Any, Throwable, AwsConfig] =
  NettyHttpClient.dual >>> AwsConfig.default
```

## Going Further

- The [HTTP Clients](./http.md) reference lists every factory and its settings.
- The [Configure Credentials and Region](./guide-configure-credentials-and-region.md) guide shows how the `AwsConfig` layer uses the client you chose.
