# Test Code That Uses AWS

> Keep AWS calls out of your unit tests by putting them behind your own ZIO service and swapping in a stub layer in tests.

In this guide, we will put the AWS calls behind a small service of our own, implement that service once with ZIO AWS and once as a stub for tests. By the end, your business logic will run in tests without network access or credentials.

We'll take an incremental approach: first we define the service, then we write the live implementation, and finally we write a stub and use it in a test.

## The Problem

A function that calls AWS directly can only be tested against AWS. The test needs credentials, a network connection and a real account, and it becomes slow and flaky. Even a test that only checks the logic around the call has to set all of that up.

ZIO AWS does not generate mock objects for the service modules. Instead, you separate the part of your code that talks to AWS from the part that uses the result. The business logic depends on a small service of your own, and only the live implementation depends on the AWS client.

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
import zio.aws.core.AwsError
import zio.aws.ec2.Ec2
import zio.aws.ec2.model._
```

## Defining the Service

We define a trait for the one question our code needs answered. The trait has no AWS types in its public methods, so code that uses it never needs to know about the AWS client:

```scala
trait RegionCatalog {
  def regionNames: ZIO[Any, AwsError, Iterable[String]]
}

object RegionCatalog {
  def regionNames: ZIO[RegionCatalog, AwsError, Iterable[String]] =
    ZIO.serviceWithZIO[RegionCatalog](_.regionNames)
}
```

The companion object provides an accessor, so a program can write `RegionCatalog.regionNames` and declare `RegionCatalog` in its environment.

## Implementing the Live Service

The live implementation calls EC2 through the `Ec2` service. It takes the `Ec2` service from the layer, and then it provides that service to the AWS calls:

```scala
val live: ZLayer[Ec2, Nothing, RegionCatalog] =
  ZLayer.fromZIO(
    ZIO.service[Ec2].map { ec2 =>
      new RegionCatalog {
        override def regionNames: ZIO[Any, AwsError, Iterable[String]] =
          Ec2
            .describeRegions(DescribeRegionsRequest())
            .flatMap(_.getRegions)
            .flatMap(regions => ZIO.foreach(regions)(_.getRegionName))
            .provideEnvironment(ZEnvironment(ec2))
      }
    }
  )
```

The code above:

- `ZIO.service[Ec2]` reads the EC2 service from the environment, which the caller provides.
- `provideEnvironment(ZEnvironment(ec2))` gives the AWS calls the `Ec2` service they need. The returned effect requires nothing, so callers do not have to provide `Ec2` again.
- `getRegionName` returns the region name as a plain `String`, so the public method has no AWS types.

## Writing the Stub

The stub implements the same trait without calling AWS. Its effect requires nothing, so a test can provide it directly:

```scala
val stub: ULayer[RegionCatalog] =
  ZLayer.succeed(
    new RegionCatalog {
      override def regionNames: ZIO[Any, AwsError, Iterable[String]] =
        ZIO.succeed(List("eu-west-1", "us-east-1"))
    }
  )
```

The stub returns a fixed list. You can make it return any value or fail with an `AwsError`, which lets a test check how the code reacts to failures.

## Using the Stub in a Test

Business logic depends only on `RegionCatalog`. The function below counts the regions and reports the count:

```scala
val regionReport: ZIO[RegionCatalog, AwsError, String] =
  RegionCatalog.regionNames.map(names => s"${names.size} regions available")
```

A test provides the stub in place of the live layer, and no network call happens:

```scala
val reportWithStub: ZIO[Any, AwsError, String] =
  regionReport.provideLayer(stub)
```

In a test suite built with ZIO Test, the same `provideLayer(stub)` call goes inside the test body, and the assertion checks the result.

:::tip
Keep the service trait small. Every operation you add to it needs a stub, so one method per question the business logic asks is a good size.
:::

## Going Further

- The [Configure Credentials and Region](./guide-configure-credentials-and-region.md) guide shows how the live layer receives its configuration.
- The [Handle AWS Errors](./guide-handle-errors.md) guide shows how a stub can fail with an `AwsError` to test error handling.
