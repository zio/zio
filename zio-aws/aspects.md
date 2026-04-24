# Aspects

> It is possible to define _aspects_ of type `AwsCallAspect[R]` that can modify the behavior of the AWS client modules. This can be used for example 
to add logging or metrics to the AWS clients and it's also the recommended way to handle retries or apply rate limiting and other similar patterns.

# Aspects

It is possible to define _aspects_ of type `AwsCallAspect[R]` that can modify the behavior of the AWS client modules. This can be used for example 
to add logging or metrics to the AWS clients and it's also the recommended way to handle retries or apply rate limiting and other similar patterns.

The library contains two default aspects, implementing logging and metrics on top of ZIO 2's core capabilities:

```scala
val callLogging: AwsCallAspect[Any]
def callDuration(prefix: String, boundaries: MetricKeyType.Histogram.Boundaries): AwsCallAspect[Any]
```

To define an aspect, create an instance of the `AwsCallAspect` trait:

```scala
val callLogging: AwsCallAspect[Any] =
    new AwsCallAspect[Any] {
      override final def apply[R, E >: AwsError, A <: Described[_]](
          f: ZIO[R, E, A]
      )(implicit trace: Trace): ZIO[R, E, A] = {
        f.either.timed
          .flatMap {
            case (duration, Right(r)) =>
              ZIO.succeed(r)
            case (duration, Left(error)) =>
              Console
                .printLine(
                  s"AWS call FAILED in $duration with $error"
                )
                .ignore *> ZIO.fail(error)
          }
      }
    }
// callLogging: AwsCallAspect[Any] = repl.MdocSession$MdocApp$$anon$1@57c4462f
```

This aspect can attached to a _client layer_ with the `@@@` operator. Multiple aspects can be composed with `>>>`.

To see a full example, check [example #2](https://github.com/vigoo/zio-aws/blob/master/examples/example2/src/main/scala/Main.scala).
