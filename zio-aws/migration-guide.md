# Migration guide

> There are some major changes compared to the ZIO 1 version (v3.x.x.x and v4.x.x.x). This section contains detailed information about what changed and how to modify existing code.

# Migration from v3.x.x.x

There are some major changes compared to the ZIO 1 version (v3.x.x.x and v4.x.x.x). This section contains detailed information about what changed and how to modify existing code.

## New package names
Previously the zio-aws packages had the root package `io.github.vigoo.zioaws`. By moving to the ZIO organisation this changed to be simply `zio.aws`. 

So imports for something using the EC2 and ElasticBeanstalk APIs would change from:

```scala

```

to 

```scala

```

Some other changes to the imports may be necessary because of switching to the new service pattern of ZIO, described below.

## New getter names

In previous version of zio-aws the generated models provided to ways to access fields:

- a ZIO effect that failed if the value is `None` with the name of the field (for example `.instanceId`)
- a simple value accessor returning an `Option` value with the `Value` suffix (for example `.instanceIdValue`)

This has been changed to match the convention used in `zio-k8s` to the following:

- the ZIO effect for requiring that a field has value now has the `get` prefix (for example `.getInstanceId`)
- the value with the field name is the simple accessor returning the optional value: (for example `.instanceId`)

So for example the following code that prints information about EC2 instances:

```scala
for {
    id <- instance.instanceId
    typ <- instance.instanceType
    launchTime <- instance.launchTime
    _ <- console.putStrLn(s"  instance $id:").ignore
    _ <- console.putStrLn(s"    type: $typ").ignore
    _ <- console.putStrLn(s"    launched at: $launchTime").ignore
} yield ()
```

would have to be changed to:

```scala
for {
    id <- instance.getInstanceId
    typ <- instance.getInstanceType
    launchTime <- instance.getLaunchTime
    _ <- Console.printLine(s"  instance $id:").ignore
    _ <- Console.printLine(s"    type: $typ").ignore
    _ <- Console.printLine(s"    launched at: $launchTime").ignore
} yield ()
```

## New service pattern
Previous versions were following the _ZIO module pattern 1.0_, so all the generated AWS services consisted of a type alias using `Has`, a trait called `Service` and a package object with the service's name. With ZIO 2 we no longer have `Has` and the recommended way to structure services was changed to the simple new service pattern, where each service is just a `trait` and one or more implementation classes, constructed by layers.

In practice this means that:
- instead of the package object with lower-case name, the accessor functions are now in the service companion object (`ElasticBeanstalk.describeApplications` instead of `elasticbeanstalk.describeApplications`)
- same for the layers, for example `Ec2.live` instead of `ec2.live`
- the service trait has the name of the service (for example `Ec2`) instead of `Service` 

The new service pattern is also applied to the core services like `AwsConfig` and the http implementations.

## Optional parameters
A very large part of the fields of AWS models are optional. These previously had the type `Option`, and although for extracting data from them the library already had the generated getters, constructing these data types still required wrapping most of the parameters in `Some(...)`. To reduce this boilerplate zio-aws now uses the `Optional` type instead of `Option`, which was first used in [zio-k8s](https://coralogix.github.io/zio-k8s/docs/overview/overview_resources#model). Now the two libraries share the same type which was moved to [zio-prelude](https://zio.github.io/zio-prelude/).

The following example:

```scala
kinesis.describeStreamConsumer(
  DescribeStreamConsumerRequest(
    consumerName = Some(consumerName),
    streamARN = Some(streamDescription.streamDescriptionValue.streamARNValue)
  )
)
```

becomes

```scala
Kinesis.describeStreamConsumer(
  DescribeStreamConsumerRequest(
    consumerName = consumerName,
    streamARN = streamDescription.streamDescription.streamARN
  )
)
```

## Newtypes
Previously zio-aws generated simple Scala type aliases for primitive types in the AWS SDKs. For example the `TableName` type in `zio-aws-dynamodb` was just a type alias for `String`:

```scala
package io.github.vigoo.zioaws.dynamodb.model

package object primitives {
  type TableName = String
}
```

The new version uses [zio-prelude's newtype wrappers](https://zio.github.io/zio-prelude/docs/newtypes/) to provide better type safety:

```scala
package zio.aws.dynamodb.model

package object primitives {
  object TableName extends Subtype[String]
  type TableName = TableName.Type
}
```

In practice this means that we have to explicitly wrap these primitive values, for example instead of:

```scala
elasticbeanstalk.describeApplications(
  DescribeApplicationsRequest(applicationNames = Some(List("my-service")))
)
```

now we have to write

```scala
ElasticBeanstalk.describeApplications(
  DescribeApplicationsRequest(applicationNames = List(ApplicationName("my-service")))
)
```

and if we need to convert them to the underlying primitive type we need to call `unwrap`:

```scala
ResourceId.unwrap(id)
```

## Aspects
zio-aws introduced `AwsCallAspect` soon after Adam Fraser's [talk](https://www.youtube.com/watch?v=gcqWdNwNEPg&t=2s) but it was a custom implementation, defined as:

```scala
trait AwsCallAspect[-R] { self =>
  def apply[R1 <: R, A](
    f: ZIO[R1, AwsError, Described[A]]
  ): ZIO[R1, AwsError, Described[A]]

  // ...
}
```

These aspects can be applied to whole zio-aws service layers to add logging, metrics, retries etc for every AWS Java SDK call.

The ZIO 2.0.0 version is now using ZIO's built-in aspect support as a base:

```scala
type AwsCallAspect[-R] =
  ZIOAspect[Nothing, R, AwsError, AwsError, Nothing, Described[_]]
```

### New built-in aspects
With ZIO 2 we have logging and metrics support built-in, so zio-aws now provides ready to use aspects for logging and monitoring AWS calls:

```scala
val callLogging: AwsCallAspect[Any]
def callDuration(prefix: String, boundaries: MetricKeyType.Histogram.Boundaries): AwsCallAspect[Any]
```

### Changes in defining aspects
The following example aspect uses `rezilience` to add circuit breaking for an AWS service:

```scala
def circuitBreaking(cb: CircuitBreaker[AwsError]): AwsCallAspect[Any] =
  new AwsCallAspect[Any] {
    override final def apply[R1 <: Any, A](
      f: ZIO[R1, AwsError, Described[A]]
    ): ZIO[R1, AwsError, Described[A]] =
      cb(f).mapError(policyError =>
        AwsError.fromThrowable(policyError.toException)
      )
  }
```

because of the changed base type for `AwsCallAspect` with the new version the same aspect is defined like this:

```scala
def circuitBreaking(cb: CircuitBreaker[AwsError]): AwsCallAspect[Any] =
  new AwsCallAspect[Any] {
    override final def apply[R, E >: AwsError <: AwsError, A <: Described[_]](
      f: ZIO[R, E, A]
    )(implicit trace: Trace): ZIO[R, E, A] =
      cb(f).mapError(policyError =>
        AwsError.fromThrowable(policyError.toException)
      )
  }
```

The main differences:

- requiring the implicit `Trace` 
- the constraints on the error and result types are now expressed as type bounds because the ZIO aspect is more generic

## New config library

The 3.x series of zio-aws was using `zio-config 1.x`. The recently released 4.x series was using `zio-config 2.x` which is the ZIO 1 version of the config library's new API; The ZIO 2 version of zio-aws uses `zio-config 3.x` which is the same new config API but for ZIO 2.

This means that coming from zio-aws 3.x requires upgrading to the new config API. For more information about the changes in zio-config, [see it's release notes](https://github.com/zio/zio-config/releases/tag/v2.0.0-RC1).
