# Aspects

> Reference for AwsCallAspect, the built-in logging and duration aspects, the Described result type and the @@@ operator that attaches aspects to service layers.

An aspect of type `AwsCallAspect[R]` changes how the operations of a service behave, without changing the operations themselves. Aspects are the library's recommended way to add logging, metrics, retries or rate limiting to AWS calls. The [Add Aspects for Logging and Metrics](./guide-add-aspects.md) guide shows how to write and attach one.

## AwsCallAspect

`AwsCallAspect` is a type alias in the `zio.aws.core.aspects` package:

```scala
type AwsCallAspect[-R] = ZIOAspect[Nothing, R, AwsError, AwsError, Nothing, Described[_]]
```

An aspect transforms a `ZIO[R, E, A]` where the result `A` is a `Described[_]`. Every generated operation returns its result wrapped in `Described`, so an aspect always knows which service and operation it is looking at.

`AwsCallAspect.identity` is an aspect that changes nothing. It is useful as a starting point when you build aspects in a loop.

## Described

`Described` pairs a result with the name of the call that produced it:

```scala
case class ServiceCallDescription(service: String, operation: String)
case class Described[+A](value: A, description: ServiceCallDescription)
```

The `service` and `operation` fields are the names used by the built-in aspects. A custom aspect reads them from `description`, and it returns the same `Described` value, so the operation's result reaches the caller unchanged.

## Built-in Aspects

The library provides two aspects. Both only observe the call, so they do not change its result. Their signatures are:

```scala
val callLogging: AwsCallAspect[Any]
def callDuration(prefix: String, boundaries: MetricKeyType.Histogram.Boundaries): AwsCallAspect[Any]
```

`callLogging` logs one line for each call in the form `[service/operation] ran for <duration>`, using ZIO's logging. It logs successful calls only, because a failed call never reaches the point where the duration is logged. To log failures, write an aspect of your own, as the [Add Aspects](./guide-add-aspects.md) guide does.

`callDuration` records each call's duration in a histogram named `<prefix>_aws_call`. The histogram has the tags `aws_service` and `aws_operation`, and the `boundaries` are the upper limits of its buckets in seconds. Create the boundaries with `MetricKeyType.Histogram.Boundaries.fromChunk`.

## Attaching an Aspect

The `@@@` operator attaches an aspect to a service layer. It is available once you import `zio.aws.core.aspects._`:

```scala
def @@@[RIn1 <: RIn](aspect: AwsCallAspect[RIn1]): ZLayer[RIn1, E, ROut]
```

The operator works on any `ZLayer` whose output is a service that extends `AspectSupport`, which every generated service does. The result is a layer of the same service, with the aspect applied to each of its operations. Aspects combine with `>>>`:

```scala
val observed = Ec2.live @@@ (logFailures >>> callLogging)
```

## Attaching an Aspect to a Service Value

When you hold a service value instead of a layer, for example one built with `Ec2.scoped`, attach the aspect with `AspectSupport#withAspect`. Every generated service extends `AspectSupport`:

```scala
trait AspectSupport[Self] {
  def withAspect[R](newAspect: AwsCallAspect[R], r: ZEnvironment[R]): Self
}
```

`AspectSupport#withAspect` returns a copy of the service whose operations run through `newAspect`, with the aspect's environment taken from `r`. It replaces the aspect the service had; to keep both, combine them with `>>>` first. `@@@` is built on `AspectSupport#withAspect`. For an aspect that needs nothing, pass `ZEnvironment.empty`:

```scala
import zio._
import zio.aws.core.aspects._
import zio.aws.core.config.AwsConfig
import zio.aws.ec2.Ec2

val loggedEc2: ZIO[AwsConfig & Scope, Throwable, Ec2] =
  Ec2.scoped(identity).map(_.withAspect(callLogging, ZEnvironment.empty))
```

## Writing an Aspect

An aspect is a class that extends `AwsCallAspect[R]` and implements `apply`. The library's own aspects use this signature:

```scala
override final def apply[R, E, A <: Described[_]](f: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]
```

The `Described[_]` bound is part of the signature, so an aspect cannot be applied to a plain `ZIO` that is not a generated AWS call. To use the error, declare the bounds `E >: AwsError <: AwsError`, which tell the compiler that the error is exactly `AwsError`; the [Add Retries](./guide-add-retries.md) guide uses them to retry with a `Schedule`.

An aspect runs once for each AWS request. For an operation that ZIO AWS pages itself, that is once per page; for an operation that uses the Java SDK paginator, it is once for the whole stream, and calls made through a service's `api` field do not run through aspects at all.

## Going Further

- The [Add Aspects for Logging and Metrics](./guide-add-aspects.md) guide walks through attaching the built-in aspects and writing one of your own.
- The [Add Retries](./guide-add-retries.md) guide builds a retry aspect.
