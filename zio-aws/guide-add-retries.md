# Add Retries

> Retry throttled and transient AWS failures for every call of a service, with a ZIO Schedule inside an aspect.

In this guide, we will retry failed AWS calls with a ZIO `Schedule`, for every operation of a service at once. By the end, you will have an aspect that retries throttling and network errors with exponential backoff, and leaves other errors alone.

We'll take an incremental approach: first we retry every failure, then we retry only the failures that are worth retrying, and finally we attach the aspect to a service layer.

## The Problem

AWS services reject requests when a caller sends too many of them, and networks drop connections. Both failures go away on their own, so the right reaction is to wait and send the request again.

The AWS SDK has its own retry logic, but ZIO AWS turns it off when you configure clients with `AwsConfig.configured()` and a `client` section, so that retries can be controlled in ZIO instead. Adding `ZIO#retry` to each call is repetitive, and a forgotten call site has no retries at all.

An aspect solves both problems. ZIO AWS runs every AWS request of a service through the aspect attached to the service layer, so one aspect gives every operation the same retry policy.

## Prerequisites

Add the core module, a service module and an HTTP client:

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
import zio.aws.core.{AwsError, GenericAwsError}
import zio.aws.core.aspects._
import zio.aws.core.config.AwsConfig
import zio.aws.ec2.Ec2
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.core.exception.SdkClientException
import java.io.IOException
```

## Retrying Every Failure

An aspect is an `AwsCallAspect[R]`. Its `apply` method receives one AWS request as a ZIO effect and returns a new effect. To retry, we call `ZIO#retry` on that effect with the schedule we are given:

```scala
def retrying(policy: Schedule[Any, AwsError, Any]): AwsCallAspect[Any] =
  new AwsCallAspect[Any] {
    override final def apply[R, E >: AwsError <: AwsError, A <: Described[_]](
        f: ZIO[R, E, A]
    )(implicit trace: Trace): ZIO[R, E, A] =
      f.retry(policy)
  }
```

The error type bounds `E >: AwsError <: AwsError` tell the compiler that the error is exactly `AwsError`, so a `Schedule` that reads `AwsError` values can drive the retries. The `Described[_]` bound limits the aspect to generated AWS calls.

A simple policy retries three times with exponential backoff:

```scala
val threeTimes: Schedule[Any, AwsError, Any] =
  Schedule.exponential(100.millis) && Schedule.recurs(3)
```

`Schedule.exponential(100.millis)` waits 100 ms, then 200 ms, then 400 ms, and `Schedule.recurs(3)` stops after three retries. The `&&` operator continues only while both schedules continue.

## Retrying Only Transient Failures

Retrying every failure is wasteful: a request that AWS rejects for missing permissions fails the same way every time. A `GenericAwsError` carries the exception the AWS SDK threw, and its type and fields tell whether the failure is worth retrying:

```scala
def isTransient(error: AwsError): Boolean =
  error match {
    case GenericAwsError(e: AwsServiceException) => e.isThrottlingException || e.statusCode() >= 500
    case GenericAwsError(e: SdkClientException)  => e.getCause.isInstanceOf[IOException]
    case GenericAwsError(_: IOException)         => true
    case _                                       => false
  }
```

The code above:

- `AwsServiceException#isThrottlingException` is `true` when the service rejected the request because of its rate limit, for example with the `Throttling` or `RequestLimitExceeded` error code.
- `AwsServiceException#statusCode` is the HTTP status of the response; a 5xx status means the service itself failed.
- An `SdkClientException` caused by an `IOException`, or a bare `IOException` from the HTTP client, means the request did not reach the service or the connection broke.
- `FieldIsNone`, the other case of `AwsError`, is a missing field in a successful response, so it is never retried.

We add the check to the policy with `Schedule.recurWhile`, and add jitter so that many fibers do not retry at the same moment:

```scala
val transientOnly: Schedule[Any, AwsError, Any] =
  (Schedule.exponential(100.millis).jittered && Schedule.recurs(5)) &&
    Schedule.recurWhile[AwsError](isTransient)
```

The policy now retries up to five times, but stops at the first failure that is not transient, and the effect fails with that error.

## Attaching the Aspect to a Service

The `@@@` operator, from `zio.aws.core.aspects._`, attaches the aspect to a service layer:

```scala
val ec2WithRetries: ZLayer[AwsConfig, Throwable, Ec2] =
  Ec2.live @@@ retrying(transientOnly)
```

Every operation of the `Ec2` service built by this layer now retries transient failures. For most paginated operations, including those with a `NextToken` field such as `Ec2.describeInstances`, each page request is retried on its own, so a failure on the tenth page does not fetch the first nine again. Operations that use the AWS Java SDK's paginator run the aspect once, when the stream starts.

To log the calls as well, combine the retry aspect with another one using `>>>`:

```scala
val ec2Observed: ZLayer[AwsConfig, Throwable, Ec2] =
  Ec2.live @@@ (retrying(transientOnly) >>> callLogging)
```

:::note
`callLogging` logs successful calls only. With the retry aspect first, it logs one line for the call that finally succeeded, including the time spent on retries.
:::

## Going Further

- The [Add Aspects for Logging and Metrics](guide-add-aspects.md) guide shows the built-in aspects and how to write others.
- The [Aspects](aspects.md) reference describes `AwsCallAspect` and the `@@@` operator.
- The [Handle AWS Errors](guide-handle-errors.md) guide shows how to read the AWS error code of a failure.
