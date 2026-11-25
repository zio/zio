# Getting Started

> Add ZIO AWS to a project, call one AWS operation and run the program, end to end.

This page takes you from an empty sbt project to a running program that calls AWS. It assumes you know basic Scala and ZIO effects and layers, and that AWS credentials are set up on your machine, for example with `aws configure`.

## Add the Dependencies

A ZIO AWS program needs the core module, one HTTP client module and one module per AWS service. We use EC2 and the Netty client:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-aws-core"  % "7.59.5.2",
  "dev.zio" %% "zio-aws-netty" % "7.59.5.2",
  "dev.zio" %% "zio-aws-ec2"   % "7.59.5.2"
)
```

The [Installation](index.md#installation) section explains each module, and [Artifacts](artifacts.md) lists every AWS service module.

## Call One Operation

Each AWS service is a ZIO service, and each operation is a function on its companion object. We call EC2's `DescribeRegions`, which lists the regions EC2 offers:

```scala
import zio._
import zio.aws.core.AwsError
import zio.aws.core.config.AwsConfig
import zio.aws.ec2.Ec2
import zio.aws.ec2.model.DescribeRegionsRequest
import zio.aws.netty.NettyHttpClient

val listRegions: ZIO[Ec2, AwsError, Unit] =
  for {
    response <- Ec2.describeRegions(DescribeRegionsRequest())
    regions  <- response.getRegions
    _ <- ZIO.foreachDiscard(regions) { region =>
           region.getRegionName.flatMap(name => Console.printLine(name).orDie)
         }
  } yield ()
```

The request is a case class whose fields all have default values, so `DescribeRegionsRequest()` is a valid request. The response is a read-only wrapper around the Java SDK response. Its `getRegions` getter is a ZIO effect, because the field is optional in the AWS model: when the field is absent, the effect fails with an `AwsError` instead of returning `null`.

Building `listRegions` makes no network call. The call happens when the effect runs.

## Provide the Layers and Run

`listRegions` needs an `Ec2` service. Three layers build it: the HTTP client, the common AWS configuration and the EC2 client itself. We provide all three and run the program as a `ZIOAppDefault`:

```scala
object ListRegionsApp extends ZIOAppDefault {
  def run =
    listRegions.provide(
      NettyHttpClient.default,
      AwsConfig.default,
      Ec2.live
    )
}
```

The `ZIO#provide` call builds the layers in dependency order: `NettyHttpClient.default` provides the `HttpClient`, `AwsConfig.default` uses it to provide `AwsConfig`, and `Ec2.live` uses that to build the EC2 client. The client is closed when the program ends.

When you run the program, it prints one line per EC2 region, such as `eu-west-1`.

## Where the Region and Credentials Come From

`AwsConfig.default` sets neither the region nor the credentials, so the AWS SDK looks them up itself. It reads the `AWS_REGION` environment variable, the `aws.region` system property or the profile in `~/.aws/config`, and the same sources for credentials. If the program fails with an error about a missing region, set `AWS_REGION`:

```bash
export AWS_REGION=eu-west-1
```

To set the values in code or load them from a configuration file, see [Configure Credentials and Region](guide-configure-credentials-and-region.md).

## Next Steps

- Build a complete program step by step in the [tutorial](tutorial-first-program.md).
- Learn what a generated service contains in the [Wrappers](wrappers.md) reference.
- Handle failures with [Handle AWS Errors](guide-handle-errors.md).
