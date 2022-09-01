---
id: zio-aws
title: "ZIO AWS"
---

[ZIO AWS](https://github.com/zio/zio-aws) is a low-level AWS wrapper for ZIO for all the AWS services using the AWS Java SDK v2.

## Introduction

The goal is to have access to all AWS functionality for cases when only simple, direct access is needed from a ZIO application, or to be used as a building block for higher-level wrappers around specific services.

Key features of ZIO AWS:

- Common configuration layer
- ZIO module layer per AWS service
- Wrapper for all operations on all services
- HTTP service implementations for functional Scala HTTP libraries, injected through ZIO’s module system
- ZStream wrapper around paginated operations
- Service-specific extra configuration
- More idiomatic Scala request and response types wrapping the Java classes

## Installation

There are tones of artifacts [published](https://zio.github.io/zio-aws/docs/artifacts.html) for each AWS service. We can pick whichever services we need.

## Example

The following example uses the _ElasticBeanstalk_ and _EC2_ APIs:

```scala
libraryDependencies += "dev.zio" %% "zio-aws-core"             % "5.17.102.7",
libraryDependencies += "dev.zio" %% "zio-aws-ec2"              % "5.17.102.7",
libraryDependencies += "dev.zio" %% "zio-aws-elasticbeanstalk" % "5.17.102.7",
libraryDependencies += "dev.zio" %% "zio-aws-netty"            % "5.17.102.7"
```

And here is the example code:

```scala
import zio.aws.core.AwsError
import zio.aws.core.config.AwsConfig
import zio.aws.ec2.Ec2
import zio.aws.ec2.model._
import zio.aws.ec2.model.primitives._
import zio.aws.elasticbeanstalk.ElasticBeanstalk
import zio.aws.elasticbeanstalk.model._
import zio.aws.elasticbeanstalk.model.primitives._
import zio.aws.netty.NettyHttpClient
import zio._
import zio.stream._

object ZIOAWSExample extends ZIOAppDefault {
  val program: ZIO[Console & Ec2 & ElasticBeanstalk, AwsError, Unit] =
    for {
      appsResult <- ElasticBeanstalk.describeApplications(
        DescribeApplicationsRequest(applicationNames = Some(List(ApplicationName("my-service"))))
      )
      app <- appsResult.getApplications.map(_.headOption)
      _ <- app match {
        case Some(appDescription) =>
          for {
            applicationName <- appDescription.getApplicationName
            _ <- Console
              .printLine(
                s"Got application description for $applicationName"
              )
              .ignore

            envStream = ElasticBeanstalk.describeEnvironments(
              DescribeEnvironmentsRequest(applicationName =
                Some(applicationName)
              )
            )

            _ <- envStream.run(Sink.foreach { env =>
              env.getEnvironmentName.flatMap { environmentName =>
                (for {
                  environmentId <- env.getEnvironmentId
                  _ <- Console
                    .printLine(
                      s"Getting the EB resources of $environmentName"
                    )
                    .ignore

                  resourcesResult <-
                    ElasticBeanstalk.describeEnvironmentResources(
                      DescribeEnvironmentResourcesRequest(environmentId =
                        Some(environmentId)
                      )
                    )
                  resources <- resourcesResult.getEnvironmentResources
                  _ <- Console
                    .printLine(
                      s"Getting the EC2 instances in $environmentName"
                    )
                    .ignore
                  instances <- resources.getInstances
                  instanceIds <- ZIO.foreach(instances)(_.getId)
                  _ <- Console
                    .printLine(
                      s"Instance IDs are ${instanceIds.mkString(", ")}"
                    )
                    .ignore

                  reservationsStream = Ec2.describeInstances(
                    DescribeInstancesRequest(instanceIds = Some(instanceIds.map(id => zio.aws.ec2.model.primitives.InstanceId(ResourceId.unwrap(id)))))
                  )
                  _ <- reservationsStream.run(Sink.foreach { reservation =>
                    reservation.getInstances
                      .flatMap { instances =>
                        ZIO.foreach(instances) { instance =>
                          for {
                            id <- instance.getInstanceId
                            typ <- instance.getInstanceType
                            launchTime <- instance.getLaunchTime
                            _ <- Console.printLine(s"  instance $id:").ignore
                            _ <- Console.printLine(s"    type: $typ").ignore
                            _ <- Console
                              .printLine(
                                s"    launched at: $launchTime"
                              )
                              .ignore
                          } yield ()
                        }
                      }
                  })
                } yield ()).catchAll { error =>
                  Console
                    .printLineError(
                      s"Failed to get info for $environmentName: $error"
                    )
                    .ignore
                }
              }
            })
          } yield ()
        case None =>
          ZIO.unit
      }
    } yield ()

  override def run: URIO[ZEnv with ZIOAppArgs, ExitCode] = {
    val httpClient = NettyHttpClient.default
    val awsConfig = httpClient >>> AwsConfig.default
    val aws = awsConfig >>> (Ec2.live ++ ElasticBeanstalk.live)

    program
      .provideCustomLayer(aws)
      .either
      .flatMap {
        case Left(error) =>
          Console.printLineError(s"AWS error: $error").ignore.as(ExitCode.failure)
        case Right(_) =>
          ZIO.unit.as(ExitCode.success)
      }
  }
}
```
