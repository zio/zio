# Tutorial: Build an EC2 Inventory Program

> Build a ZIO AWS program step by step that lists every EC2 instance in every region, with streams, per-region clients and error handling.

Welcome to ZIO AWS! In this tutorial we build one small but complete program: an inventory that lists every EC2 instance in every region of an AWS account. This tutorial is for developers who know basic Scala and ZIO effects and layers, and who have run the program on the [Getting Started](getting-started.md) page. You don't need any prior experience with EC2.

## Learning Objectives

By the end of this tutorial, you will understand:

- How a generated ZIO AWS service turns AWS responses into ZIO effects and streams
- How to turn the read-only AWS wrappers into your own data types
- How to build a service client for a different region with `Ec2.scoped`
- How to keep a program running when one AWS call fails

We'll learn these concepts through:

1. Setting up the project
2. Listing the regions
3. Turning instances into our own data
4. Building one client per region
5. Surviving a failing region
6. Putting it together

We recommend reading from top to bottom, because each section builds on the previous one.

## Background

EC2 is regional: each region has its own instances, and an EC2 client talks to one region. To list every instance in the account, the program first asks one region for the list of regions, then asks each region for its instances.

ZIO AWS gives us three tools for this. Each AWS operation is a ZIO effect, so we can sequence calls and run them in parallel. Operations that return many pages are streams, so we never deal with next-page tokens. And every service has constructors that let us change the client's settings, such as its region, while it is built.

## 1. Setting Up the Project

The program uses the core module, the Netty HTTP client and the EC2 module:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-aws-core"  % "7.59.5.2",
  "dev.zio" %% "zio-aws-netty" % "7.59.5.2",
  "dev.zio" %% "zio-aws-ec2"   % "7.59.5.2"
)
```

Every later section uses these imports:

```scala
import zio._
import zio.stream.ZStream
import zio.aws.core.AwsError
import zio.aws.core.config.AwsConfig
import zio.aws.ec2.Ec2
import zio.aws.ec2.model.{DescribeInstancesRequest, DescribeRegionsRequest}
import zio.aws.netty.NettyHttpClient
import software.amazon.awssdk.regions.{Region => AwsRegion}
```

The `zio.aws.ec2.model` package holds the request and response types. The AWS SDK's own `Region` type is renamed to `AwsRegion`, because EC2's model also has a type named `Region`.

## 2. Listing the Regions

Let's start with one call. `Ec2.describeRegions` returns the regions that are enabled for the account:

```scala
val regionNames: ZIO[Ec2, AwsError, List[String]] =
  for {
    response <- Ec2.describeRegions(DescribeRegionsRequest())
    regions  <- response.getRegions
    names    <- ZIO.foreach(regions.toList)(_.getRegionName)
  } yield names
```

The code above:

- `Ec2.describeRegions(...)` performs the call. Its type is `ZIO[Ec2, AwsError, DescribeRegionsResponse.ReadOnly]`, so it needs the `Ec2` service and can fail with an `AwsError`.
- `response.getRegions` reads the `regions` field. The field is optional in the AWS model, so the getter is an effect that fails with `AwsError` when the field is absent.
- `ZIO.foreach(...)(_.getRegionName)` reads the name of each region the same way. In EC2's model the region name is a plain `String`; services that define their own value types use newtypes instead, as the [Wrappers](wrappers.md) reference describes.

Notice that nothing has run yet. `regionNames` is a description of the calls; it runs when we provide an `Ec2` service and execute it.

## 3. Turning Instances into Our Own Data

The program should not pass AWS wrapper types around, so let's define our own record for one instance:

```scala
case class InstanceInfo(
  region: String,
  id: String,
  instanceType: String,
  state: String
)
```

`Ec2.describeInstances` is a paginated operation, so it returns a stream of reservations instead of a single response. Each reservation holds a list of instances. We flatten the reservations into instances and convert each one:

```scala
def listInstances(region: String): ZIO[Ec2, AwsError, Chunk[InstanceInfo]] =
  Ec2
    .describeInstances(DescribeInstancesRequest())
    .mapZIO(_.getInstances)
    .flatMap(instances => ZStream.fromIterable(instances))
    .mapZIO { instance =>
      instance.getInstanceId.map { id =>
        InstanceInfo(
          region = region,
          id = id,
          instanceType = instance.instanceType.map(_.toString).getOrElse("unknown"),
          state = instance.state.flatMap(_.name).map(_.toString).getOrElse("unknown")
        )
      }
    }
    .runCollect
```

The code above:

- `Ec2.describeInstances(...)` is a `ZStream[Ec2, AwsError, Reservation.ReadOnly]`. The stream requests the next page only after the items of the previous page are consumed.
- `ZStream#mapZIO` with `_.getInstances` reads the instance list of each reservation, and `ZStream#flatMap` with `ZStream.fromIterable` turns the lists into one stream of instances.
- `instance.getInstanceId` fails with `AwsError` if the ID is missing, which never happens for a real instance, so we use the failing getter.
- `instance.instanceType` and `instance.state` are the non-failing accessors. They return `Optional` values, which we turn into text with a default. The `get`-prefixed getters fail; the plain ones return `Optional`.
- `ZStream#runCollect` runs the stream and collects the instances into a `Chunk`.

## 4. Building One Client per Region

`Ec2.live` builds a client for the region in the configuration. To ask another region, we build another client. Every service has a `scoped` constructor, such as `Ec2.scoped`, that takes a function to change the AWS SDK builder, and returns the client as a scoped effect:

```scala
def instancesIn(region: String): ZIO[AwsConfig, AwsError, Chunk[InstanceInfo]] =
  ZIO.scoped {
    Ec2
      .scoped(builder => builder.region(AwsRegion.of(region)))
      .mapError(AwsError.fromThrowable)
      .flatMap(ec2 => listInstances(region).provideEnvironment(ZEnvironment(ec2)))
  }
```

The code above:

- `Ec2.scoped(builder => builder.region(...))` builds an EC2 client whose region is `region`. The function runs after `AwsConfig` has applied the common settings, so its region wins.
- Building a client can fail with a `Throwable`, so `ZIO#mapError` with `AwsError.fromThrowable` turns that failure into an `AwsError`. Now every failure of `instancesIn` has the same type.
- `ZIO#provideEnvironment` with `ZEnvironment(ec2)` gives `listInstances` the client it needs.
- `ZIO.scoped` closes the client when the region is done.

The result only needs `AwsConfig`, which every client in the program shares.

## 5. Surviving a Failing Region

A region can fail even when the others work, for example when an organization policy denies EC2 calls there. We don't want one region to stop the whole inventory, so let's turn each region's result into an `Either` and query the regions in parallel:

```scala
val inventory: ZIO[Ec2 & AwsConfig, AwsError, Chunk[InstanceInfo]] =
  for {
    regions <- regionNames
    results <- ZIO.foreachPar(regions)(region => instancesIn(region).either.map(region -> _))
    _ <- ZIO.foreachDiscard(results) {
           case (region, Left(error)) =>
             Console.printLineError(s"Skipping $region: ${error.toThrowable.getMessage}").orDie
           case _ => ZIO.unit
         }
  } yield Chunk.fromIterable(results).collect { case (_, Right(instances)) => instances }.flatten
```

The code above:

- `ZIO.foreachPar` queries all regions at the same time.
- `ZIO#either` on `instancesIn(region)` turns a failure into a `Left` value, so the effect for that region always succeeds and the others keep running.
- The `ZIO.foreachDiscard` call reports each failed region on standard error. `AwsError#toThrowable` returns the AWS SDK exception, whose message names the problem.
- The `yield` keeps the instances of the regions that succeeded.

Try changing `instancesIn(region).either` to `instancesIn(region)` and the first failing region would fail the whole program.

## 6. Putting It Together

The complete program prints one line per instance. It combines every step: the regions come from a client built by `Ec2.live`, each region gets its own client, and failing regions are skipped. Unlike the sections above, it is a single self-contained file that you can copy into `src/main/scala`:

```scala
import zio._
import zio.stream.ZStream
import zio.aws.core.AwsError
import zio.aws.core.config.AwsConfig
import zio.aws.ec2.Ec2
import zio.aws.ec2.model.{DescribeInstancesRequest, DescribeRegionsRequest}
import zio.aws.netty.NettyHttpClient
import software.amazon.awssdk.regions.{Region => AwsRegion}

object Ec2Inventory extends ZIOAppDefault {
  case class InstanceInfo(region: String, id: String, instanceType: String, state: String)

  val regionNames: ZIO[Ec2, AwsError, List[String]] =
    for {
      response <- Ec2.describeRegions(DescribeRegionsRequest())
      regions  <- response.getRegions
      names    <- ZIO.foreach(regions.toList)(_.getRegionName)
    } yield names

  def listInstances(region: String): ZIO[Ec2, AwsError, Chunk[InstanceInfo]] =
    Ec2
      .describeInstances(DescribeInstancesRequest())
      .mapZIO(_.getInstances)
      .flatMap(instances => ZStream.fromIterable(instances))
      .mapZIO { instance =>
        instance.getInstanceId.map { id =>
          InstanceInfo(
            region,
            id,
            instance.instanceType.map(_.toString).getOrElse("unknown"),
            instance.state.flatMap(_.name).map(_.toString).getOrElse("unknown")
          )
        }
      }
      .runCollect

  def instancesIn(region: String): ZIO[AwsConfig, AwsError, Chunk[InstanceInfo]] =
    ZIO.scoped {
      Ec2
        .scoped(builder => builder.region(AwsRegion.of(region)))
        .mapError(AwsError.fromThrowable)
        .flatMap(ec2 => listInstances(region).provideEnvironment(ZEnvironment(ec2)))
    }

  val inventory: ZIO[Ec2 & AwsConfig, AwsError, Chunk[InstanceInfo]] =
    for {
      regions <- regionNames
      results <- ZIO.foreachPar(regions)(region => instancesIn(region).either.map(region -> _))
      _ <- ZIO.foreachDiscard(results) {
             case (region, Left(error)) =>
               Console.printLineError(s"Skipping $region: ${error.toThrowable.getMessage}").orDie
             case _ => ZIO.unit
           }
    } yield Chunk.fromIterable(results).collect { case (_, Right(instances)) => instances }.flatten

  def run =
    inventory
      .flatMap(instances =>
        ZIO.foreachDiscard(instances) { i =>
          Console.printLine(s"${i.region}  ${i.id}  ${i.instanceType}  ${i.state}").orDie
        }
      )
      .provide(NettyHttpClient.default, AwsConfig.default, Ec2.live)
}
```

When you run it with credentials and a default region set (for example `AWS_REGION=eu-west-1`), it prints a line such as `eu-west-1  i-0abc123def4567890  t3.micro  running` for each instance, and a `Skipping ...` line on standard error for each region it could not read.

## What You've Learned

In this tutorial, you learned:

- How a generated service exposes each operation as a ZIO effect, and each paginated operation as a stream
- How to read the read-only wrappers with failing `get` getters and `Optional` accessors, and turn them into your own case class
- How to build a client for another region with `Ec2.scoped` and give it to an effect with `provideEnvironment`
- How to keep a program running when one call fails, with `either` and `ZIO.foreachPar`

You now have the foundation for working with any AWS service that ZIO AWS supports. The next step is to apply it to the tasks of a real application.

## Where to Go Next

- **Ready to configure it for production?** Read [Configure Credentials and Region](guide-configure-credentials-and-region.md) and [Add Retries](guide-add-retries.md).
- **Want to observe the calls?** Read [Add Aspects for Logging and Metrics](guide-add-aspects.md).
- **Want the full API?** Read the [Wrappers](wrappers.md) and [AWS Errors](aws-error.md) reference pages.
