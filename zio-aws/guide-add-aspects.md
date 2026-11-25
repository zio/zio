# Add Aspects for Logging and Metrics

> Attach logging, duration metrics and your own failure logging to every call of a ZIO AWS service layer, using aspects and the @@@ operator.

In this guide, we will wrap every call to an AWS service with logging and duration metrics, and we will add an aspect of our own that logs failures. By the end, you will know how to write an aspect and attach it to a service layer.

We'll take an incremental approach: first we attach the built-in logging aspect, then we add the metrics aspect, and finally we write a custom aspect.

## The Problem

A program that calls AWS needs to know how long each call takes and when it fails. Adding a log line before and after each call means editing every call site. When the same change is needed in several places, the copies drift apart.

ZIO AWS lets you attach one aspect to a service layer. Every operation of that service then passes through the aspect, so the change is made once and applies to every call.

## Prerequisites

Add the core module, the service module and an HTTP client:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-aws-core"  % "7.59.5.2",
  "dev.zio" %% "zio-aws-ec2"   % "7.59.5.2",
  "dev.zio" %% "zio-aws-netty" % "7.59.5.2"
)
```

The examples use these imports:

```scala
import zio._
import zio.metrics.MetricKeyType
import zio.aws.core.AwsError
import zio.aws.core.aspects._
import zio.aws.core.config.AwsConfig
import zio.aws.ec2.Ec2
import zio.aws.netty.NettyHttpClient
```

The `zio.aws.core.aspects._` import brings in the `@@@` operator, the built-in aspects and the `AwsCallAspect` type.

## Attaching the Logging Aspect

The library provides `callLogging`, which logs how long each call takes. Attach it to the service layer with the `@@@` operator:

```scala
val ec2WithLogging: ZLayer[AwsConfig, Throwable, Ec2] =
  Ec2.live @@@ callLogging
```

The `@@@` operator returns a new layer. The service it builds is the same `Ec2` service, but every operation now runs through `callLogging` before it returns.

## Recording Call Durations

`callDuration` is a function, because it needs a name for the metric and the boundaries of its histogram buckets. The metric records each call's duration with the `aws_service` and `aws_operation` tags:

```scala
val ec2WithMetrics: ZLayer[AwsConfig, Throwable, Ec2] =
  Ec2.live @@@ callDuration(
    "myapp",
    MetricKeyType.Histogram.Boundaries.fromChunk(Chunk(0.1, 0.5, 1.0, 5.0))
  )
```

The metric name is `myapp_aws_call` in this example. The boundaries are the upper limits of the histogram buckets, in seconds.

## Writing a Custom Aspect

An aspect is an `AwsCallAspect[R]`. To write one, extend the trait and implement `apply`. The aspect receives the call as a ZIO effect and returns a new effect. This one logs every failure and passes the error on unchanged:

```scala
val logFailures: AwsCallAspect[Any] =
  new AwsCallAspect[Any] {
    override final def apply[R, E, A <: Described[_]](
        f: ZIO[R, E, A]
    )(implicit trace: Trace): ZIO[R, E, A] =
      f.tapError(error => ZIO.logError(s"AWS call failed: $error"))
  }
```

The `Described[_]` bound is part of the signature. Every generated call returns its result wrapped in `Described`, which records the service and the operation name, so the aspect can read them.

:::note
The library's `callLogging` also reads the service and operation name from `Described`. A custom aspect can do the same, for example to add the operation to the message.
:::

## Composing the Aspects

Several aspects combine with `>>>`. Both aspects in this example only observe the call and return its result unchanged, so the order does not affect the outcome:

```scala
val ec2Observed: ZLayer[AwsConfig, Throwable, Ec2] =
  Ec2.live @@@ (logFailures >>> callLogging)
```

Each service layer takes its own aspect, so the EC2 layer and a second service layer can use different aspects.

## Retries

Retries are also a good use for an aspect, because they apply to every call of a service in the same way. The [Add Retries](guide-add-retries.md) guide builds a retry aspect step by step.

## Going Further

- The [Aspects](./aspects.md) reference describes the built-in aspects and the `AwsCallAspect` type.
- The [Handle AWS Errors](./guide-handle-errors.md) guide shows how to match the errors that an aspect sees.
