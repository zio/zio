---
id: zio-aws
title: "ZIO AWS"
---

[ZIO AWS](https://github.com/vigoo/zio-aws) is a low-level AWS wrapper for ZIO for all the AWS services using the AWS Java SDK v2.

## Introduction

The goal is to have access to all AWS functionality for cases when only simple, direct access is needed from a ZIO application, or to be used as a building block for higher-level wrappers around specific services.

Key features of ZIO AWS:

- Common configuration service builder
- ZIO module service builder per AWS service
- Wrapper for all operations on all services
- HTTP service implementations for functional Scala HTTP libraries, injected through ZIO’s module system
- ZStream wrapper around paginated operations
- Service-specific extra configuration
- More idiomatic Scala request and response types wrapping the Java classes

## Installation

There are tones of artifacts [published](https://vigoo.github.io/zio-aws/docs/artifacts.html) for each AWS service. We can pick whichever services we need.

## Example

The following example uses the _ElasticBeanstalk_ and _EC2_ APIs:

```scala
libraryDependencies += "io.github.vigoo" %% "zio-aws-core"             % "3.17.8.4",
libraryDependencies += "io.github.vigoo" %% "zio-aws-ec2"              % "3.17.8.4",
libraryDependencies += "io.github.vigoo" %% "zio-aws-elasticbeanstalk" % "3.17.8.4",
libraryDependencies += "io.github.vigoo" %% "zio-aws-netty"            % "3.17.8.4"
```

And here is the example code:

```scala
import io.github.vigoo.zioaws.core.AwsError
import io.github.vigoo.zioaws.ec2.Ec2
import io.github.vigoo.zioaws.ec2.model._
import io.github.vigoo.zioaws.elasticbeanstalk.ElasticBeanstalk
import io.github.vigoo.zioaws.elasticbeanstalk.model._
import io.github.vigoo.zioaws.{core, ec2, elasticbeanstalk, netty}
import zio.console._
import zio.stream._
import zio.{console, _}

object ZIOAWSExample extends zio.App {
  val program: ZIO[Console with Ec2 with ElasticBeanstalk, AwsError, Unit] =
    for {
      appsResult <- elasticbeanstalk.describeApplications(
        DescribeApplicationsRequest(applicationNames = Some(List("my-service")))
      )
      app <- appsResult.applications.map(_.headOption)
      _ <- app match {
        case Some(appDescription) =>
          for {
            applicationName <- appDescription.applicationName
            _ <- console.putStrLn(
              s"Got application description for $applicationName"
            ).ignore

            envStream = elasticbeanstalk.describeEnvironments(
              DescribeEnvironmentsRequest(applicationName =
                Some(applicationName)
              )
            )

            _ <- envStream.run(Sink.foreach { env =>
              env.environmentName.flatMap { environmentName =>
                (for {
                  environmentId <- env.environmentId
                  _ <- console.putStrLn(
                    s"Getting the EB resources of $environmentName"
                  ).ignore

                  resourcesResult <-
                    elasticbeanstalk.describeEnvironmentResources(
                      DescribeEnvironmentResourcesRequest(environmentId =
                        Some(environmentId)
                      )
                    )
                  resources <- resourcesResult.environmentResources
                  _ <- console.putStrLn(
                    s"Getting the EC2 instances in $environmentName"
                  ).ignore
                  instances <- resources.instances
                  instanceIds <- ZIO.foreach(instances)(_.id)
                  _ <- console.putStrLn(
                    s"Instance IDs are ${instanceIds.mkString(", ")}"
                  ).ignore

                  reservationsStream = ec2.describeInstances(
                    DescribeInstancesRequest(instanceIds = Some(instanceIds))
                  )
                  _ <- reservationsStream.run(Sink.foreach { reservation =>
                    reservation.instances
                      .flatMap { instances =>
                        ZIO.foreach(instances) { instance =>
                          for {
                            id <- instance.instanceId
                            typ <- instance.instanceType
                            launchTime <- instance.launchTime
                            _ <- console.putStrLn(s"  instance $id:").ignore
                            _ <- console.putStrLn(s"    type: $typ").ignore
                            _ <- console.putStrLn(
                              s"    launched at: $launchTime"
                            ).ignore
                          } yield ()
                        }
                      }
                  })
                } yield ()).catchAll { error =>
                  console.putStrLnErr(
                    s"Failed to get info for $environmentName: $error"
                  ).ignore
                }
              }
            })
          } yield ()
        case None =>
          ZIO.unit
      }
    } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = { //
    val httpClient = netty.default
    val awsConfig  = httpClient >>> core.config.default
    val aws        = awsConfig >>> (ec2.live ++ elasticbeanstalk.live)

    program
      .provideCustom(aws)
      .either
      .flatMap {
        case Left(error) =>
          console.putStrErr(s"AWS error: $error").ignore.as(ExitCode.failure)
        case Right(_) =>
          ZIO.unit.as(ExitCode.success)
      }
  }
}
```
