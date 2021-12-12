---
id: zio-k8s
title: "ZIO K8s"
---

[ZIO K8S](https://github.com/coralogix/zio-k8s) is an idiomatic ZIO client for the Kubernetes API.

## Introduction

This library provides a client for the full Kubernetes API as well as providing code generator support for custom resources and higher-level concepts such as operators, taking full advantage of the ZIO library.

Using ZIO K8S we can talk to the Kubernetes API that helps us to:
- Write an operator for our custom resource types
- Schedule some jobs in our cluster
- Query the cluster for monitoring purposes
- Write some cluster management tools

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "com.coralogix" %% "zio-k8s-client" % "1.3.3"
```

And then we need to choose the proper sttp backend:

```scala
"com.softwaremill.sttp.client3" %% "httpclient-backend-zio" % "3.1.1",
"com.softwaremill.sttp.client3" %% "slf4j-backend"          % "3.1.1"
```

Or the asynchronous version:

```scala
"com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.1.1"
"com.softwaremill.sttp.client3" %% "slf4j-backend"                 % "3.1.1"
```

## Example

This is an example of printing the tail logs of a container:

```scala
import com.coralogix.zio.k8s.client.K8sFailure
import com.coralogix.zio.k8s.client.config.httpclient._
import com.coralogix.zio.k8s.client.model.K8sNamespace
import com.coralogix.zio.k8s.client.v1.pods
import com.coralogix.zio.k8s.client.v1.pods.Pods
import zio._
import zio.console.Console

import scala.languageFeature.implicitConversions

object ZIOK8sLogsExample extends zio.App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = (args match {
    case List(podName) => tailLogs(podName, None)
    case List(podName, containerName) => tailLogs(podName, Some(containerName))
    case _ => console.putStrLnErr("Usage: <podname> [containername]")
  })
    .provideCustom(k8sDefault >>> Pods.live)
    .exitCode

  def tailLogs(podName: String,
               containerName: Option[String]
              ): ZIO[Pods with Console, K8sFailure, Unit] =
    pods
      .getLog(
        name = podName,
        namespace = K8sNamespace.default,
        container = containerName,
        follow = Some(true)
      )
      .tap { line: String =>
        console.putStrLn(line).ignore
      }
      .runDrain
}
```

## Resources

- [ZIO World - ZIO Kubernetes (ZIO K8S 1.0)](https://www.youtube.com/watch?v=BUMe2hGKjXA&t=31s) by Daniel Vigovszky (March 2020) — ZIO K8S 1.0, a new library by Daniel Vigovszky and Coralogix for working with Kubernetes, which includes support for the whole API, including event processors and compositional aspects for metrics and logging.
