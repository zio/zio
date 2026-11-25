# Handle AWS Errors

> Recognise and recover from the two AwsError cases, read the AWS SDK exception behind a failed call and turn errors into Throwables.

In this guide, we will handle the failures of an AWS call with pattern matching on `AwsError`, read the AWS error code behind a failed request and recover from a missing field. By the end, you will know how to decide which failures to retry, which to report and which to ignore.

We'll take an incremental approach: first we catch every error, then we match the two error cases, and finally we read the error code of a service failure.

## The Problem

The AWS Java SDK reports failures by throwing exceptions, and it reports missing response fields by returning `null`. A program that calls the SDK directly has to catch both, and it must know which exception types a given service can throw.

ZIO AWS turns both into values of one type, `AwsError`, in the error channel of every effect. The compiler then requires you to handle the failure, and a missing field can no longer slip through as `null`.

## Prerequisites

Add the core module and a service module, such as EC2:

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
import zio.aws.core.{AwsError, FieldIsNone, GenericAwsError}
import zio.aws.ec2.Ec2
import zio.aws.ec2.model._
import software.amazon.awssdk.services.ec2.model.Ec2Exception
```

## Catching Every Error

Each AWS call has the error type `AwsError`. The simplest way to recover is to provide a fallback for every failure. Here a failed `DescribeRegions` call returns an empty list of regions:

```scala
val regions: ZIO[Ec2, Nothing, Iterable[Region.ReadOnly]] =
  Ec2
    .describeRegions(DescribeRegionsRequest())
    .flatMap(_.getRegions)
    .catchAll(_ => ZIO.succeed(Iterable.empty))
```

The `catchAll` handler receives the `AwsError` and returns a fallback effect. The result has the error type `Nothing`, so the caller no longer has to handle failures.

:::warning
Catching every error hides the difference between a network problem and a rejected request. Use it only when a fallback is always acceptable.
:::

## Matching the Two Error Cases

`AwsError` is a sealed trait with two cases, so a pattern match with both cases is exhaustive:

- `GenericAwsError(reason)` wraps the exception that the AWS SDK threw. The `reason` is a `Throwable`.
- `FieldIsNone(field)` is produced when a field that the response must contain is absent. The `field` is the name of the field.

To handle each case differently:

```scala
val described: ZIO[Ec2, Nothing, Unit] =
  Ec2
    .describeRegions(DescribeRegionsRequest())
    .flatMap(_.getRegions)
    .flatMap(regions => ZIO.foreachDiscard(regions)(_.getRegionName))
    .catchAll {
      case GenericAwsError(reason) =>
        Console.printLine(s"The AWS call failed: ${reason.getMessage}").ignore
      case FieldIsNone(field) =>
        Console.printLine(s"The response has no $field field").ignore
    }
```

The code above handles the two failures with different messages. The `GenericAwsError` branch can use the exception message, and the `FieldIsNone` branch can name the field that was missing.

## Reading the AWS Error Code

Failures from the service itself carry the AWS error code, such as `UnauthorizedOperation` or `Throttling`. The `reason` of a `GenericAwsError` is the SDK exception, which is specific to the service. For EC2 it is `Ec2Exception`, and that exception gives access to the error code:

```scala
val errorCode: AwsError => Option[String] = {
  case GenericAwsError(ec2: Ec2Exception) =>
    Option(ec2.awsErrorDetails()).map(_.errorCode())
  case _ =>
    None
}
```

The code above returns the AWS error code when the failure comes from EC2, and `None` for any other failure. Each service module has its own exception class, named after the service.

## Converting to a Throwable

Code that still uses exceptions can convert an `AwsError` with `toThrowable`. Both cases have a `Throwable` form. The effect below fails with a `Throwable` instead of an `AwsError`:

```scala
val asThrowable: ZIO[Ec2, Throwable, Iterable[Region.ReadOnly]] =
  Ec2
    .describeRegions(DescribeRegionsRequest())
    .flatMap(_.getRegions)
    .mapError(_.toThrowable)
```

`toThrowable` returns the wrapped exception for a `GenericAwsError`, and a `NoSuchElementException` for a `FieldIsNone`.

## Retrying Failures

Retries are not part of the error model. ZIO AWS recommends adding retries with an aspect on the service layer, so that every call gets the same policy. See [Add Aspects](./guide-add-aspects.md) for an example. The [AWS Errors](./aws-error.md) reference describes every case of `AwsError`.

## Going Further

- The [AWS Errors](./aws-error.md) reference lists the full definition of `AwsError`.
- The [Paginate and Stream Results](./guide-paginate-and-stream.md) guide shows how errors appear inside a stream.
