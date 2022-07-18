---
id: configurable-zio-application
title: "Tutorial: How to Make a ZIO Application Configurable?"
sidebar_label: "Making a ZIO application configurable"
---

## Introduction

One of the most common requirements for writing an application is to be able to configure it, especially when we are writing cloud-native applications.

In this tutorial, we will start with a simple ZIO application and then try to make it configurable using the ZIO Config library.

## Prerequisites

We will use the [ZIO Quickstart: Restful Web Service](../quickstarts/restful-webservice.md) as our ground project. So make sure you have downloaded and tested it before you start this tutorial.

## Problem

We have a web service that does not allow us to configure the host and port of the service:

```bash
git clone git@github.com:khajavi/zio-quickstart-restful-webservice.git
cd zio-quickstart-restful-webservice
sbt run
```

The output is:

```
Server started on http://localhost:8080
```

We want to be able to configure the host and port of the service so that before running the application, we specify the host and port of the service.

## Solution

When developing a ZIO application, we can use the ZIO environment for accessing two types of contextual information:

- **Accessing Services**: we can access service interfaces from the environment, and they are supposed to be implemented
  and provided to the whole application at the end of the world (Service Pattern).
- **Accessing Configuration**: we can access the configuration that is part of the application.

In this tutorial, we will focus on the second case to configure the host and port of the service. Let's see what steps we need to take to make this happen.

## Step 1: Define the Configuration Data Types (ADTs)

In this example our configuration data type is a case class that contains two fields:

```scala mdoc:silent
case class HttpServerConfig(host: String, port: Int)
```

## Step 2: Accessing The Configuration from the Environment

Now that we have defined our configuration data type, we can start developing our application and access the
configuration from the environment.

We can use the `ZIO.service[HttpServerConfig]` method to access the configuration from the environment:

```scala mdoc:compile-only
import zio._

ZIO.service[HttpServerConfig].flatMap { config =>
  ??? // Do something with the configuration
}
```

The above code is a ZIO workflow that will access the `HttpServerConfig` configuration from the environment and then by using flatMap, we can do something with it, for example, we can print it:

```scala mdoc:compile-only
import zio._

import java.io.IOException

val workflow: ZIO[HttpServerConfig, IOException, Unit] =
  ZIO.service[HttpServerConfig].flatMap { config =>
    Console.printLine(
      "Application started with following configuration:\n" +
        s"\thost: ${config.host}\n" +
        s"\tport: ${config.port}"
    )
  }
```

Let's run the above workflow and see the output:

```scala mdoc:fail
import zio._

import java.io.IOException

case class HttpServerConfig(host: String, port: Int)

object MainApp extends ZIOAppDefault {

  val workflow: ZIO[HttpServerConfig, IOException, Unit] =
    ZIO.service[HttpServerConfig].flatMap { config =>
      Console.printLine(
        "Application started with following configuration:\n" +
          s"\thost: ${config.host}\n" +
          s"\tport: ${config.port}"
      )
    }

  def run = workflow
}
```

When try to compile the above code, you will see the following output:


```
──── ZIO APP ERROR ───────────────────────────────────────────────────

 Your effect requires a service that is not in the environment.
 Please provide a layer for the following type:

   1. HttpServerConfig

 Call your effect's provide method with the layers you need.
 You can read more about layers and providing services here:
 
   https://zio.dev/next/references/contextual/

──────────────────────────────────────────────────────────────────────

  def run = workflow
```

So what happened here? Well, the above error is because we are trying to access the `HttpServerConfig` configuration from the environment, but we have not provided a layer for it.

There are two steps that we need to take to make this happen.
- Defining a layer for `HttpServerConfig` configuration data type.
- Providing the layer to our ZIO workflow.

To provide the configuration layer, we need to define a `ZLayer` of type `HttpServerConfig` and use the `ZIO#provide` method.

Before diving into the next steps, let's define a simple layer and provide it to our workflow, and see what happens:

```scala mdoc:compile-only
import zio._

import java.io.IOException

object MainApp extends ZIOAppDefault {

  val workflow: ZIO[HttpServerConfig, IOException, Unit] =
    ZIO.service[HttpServerConfig].flatMap { config =>
      Console.printLine(
        "Application started with following configuration:\n" +
          s"\thost: ${config.host}\n" +
          s"\tport: ${config.port}"
      )
    }

  def run = workflow.provide(ZLayer.succeed(HttpServerConfig("localhost", 8080)))
}
```

Know we can run the above workflow and see this output:

```
Application started with following configuration:
	host: localhost
	port: 8080
```

Great! Now we have ZIO workflow that can access the configuration layer, and finally we can provide a configuration layer to our application. It works! Now, let's apply the same approach to our RESTful Web Service:

```scala mdoc:invisible
import zio._
import zhttp.http._

object GreetingApp {
  def apply() = Http.empty
}

object DownloadApp {
  def apply() = Http.empty
}

object CounterApp {
  def apply() = Http.empty
}

object UserApp {
  def apply() = Http.empty
}

object InmemoryUserRepo {
  val layer = ZLayer.empty
}
```

```scala mdoc:compile-only
import zio._
import zhttp.service.Server

object MainApp extends ZIOAppDefault {
  def run =
    ZIO.service[HttpServerConfig].flatMap { config =>
      Server.start(
        port = config.port,
        http = GreetingApp() ++ DownloadApp() ++ CounterApp() ++ UserApp()
      )
    }.provide(
      // A layer responsible for storing the state of the `counterApp`
      ZLayer.fromZIO(Ref.make(0)),

      // To use the persistence layer, provide the `PersistentUserRepo.layer` layer instead
      InmemoryUserRepo.layer,
     
      // A layer containing the configuration of the http server
      ZLayer.succeed(HttpServerConfig("localhost", 8080))
    )
}
```

Until know, we made our RESTful web service configurable to be able to use its config from the ZIO environment with a simple configuration layer.

Now let's move on to the next step: defining a real layer for our configuration data type.

## Step 3: Defining a Layer for Configuration Data Type

This step involves defining our layer by using the ZIO Config library. This library provides various ways read configuration data from different sources, e.g. from:
- Java properties files
- System environment variables
- HOCON files
- JSON files
- Command-line arguments

In this tutorial, we will use the HOCON files. [HOCON](https://github.com/lightbend/config) is config format which is superset of JSON developed by Lightbend.

### Adding ZIO Config Dependencies

We should add the following dependencies to our `build.sb` file:

```scala
libraryDependencies += "dev.zio" %% "zio-config"          % "3.0.1"
libraryDependencies += "dev.zio" %% "zio-config-typesafe" % "3.0.1"
libraryDependencies += "dev.zio" %% "zio-config-magnolia" % "3.0.1"
```

### Defining the HOCON Configuration File

We can define our configuration inside `application.conf` file in the `resources` directory:

```json
# application.conf

HttpServerConfig {
  # The port to listen on.
  port = 8080
  port = ${?PORT}

  # The hostname to listen on.
  host = "localhost"
  host = ${?HOST}
}
```

HOCON supports substitutions, so in the above configuration, we can use the environment variables `?PORT` and `?HOST` to substitute the values. We also provide a default value for the port and host.

### Defining the Layer

Now let's define configuration layer inside the `HttpServerConfig`'s companion object:

```scala mdoc:silent:nest
import zio._
import zio.config._
import zio.config.magnolia.descriptor
import zio.config.typesafe.TypesafeConfigSource

case class HttpServerConfig(host: String, port: Int)

object HttpServerConfig {
  val layer: ZLayer[Any, ReadError[String], HttpServerConfig] =
    ZLayer {
      read {
        descriptor[HttpServerConfig].from(
          TypesafeConfigSource.fromResourcePath
            .at(PropertyTreePath.$("HttpServerConfig"))
        )
      }
    }
}
```

The ZIO Config has automatic derivation mechanism to parse the HOCON configuration file to our configuration data type `HttpServerConfig`.

## Step 4: Providing the Layer

We are ready to provide the configuration layer to our application:

```scala mdoc:compile-only
import zio._
import zhttp.service.Server

object MainApp extends ZIOAppDefault {
  def run =
    ZIO.service[HttpServerConfig].flatMap { config =>
      Server.start(
        port = config.port,
        http = GreetingApp() ++ DownloadApp() ++ CounterApp() ++ UserApp()
      )
    }.provide(
      // A layer responsible for storing the state of the `counterApp`
      ZLayer.fromZIO(Ref.make(0)),

      // To use the persistence layer, provide the `PersistentUserRepo.layer` layer instead
      InmemoryUserRepo.layer,
     
      // A layer containing the configuration of the http server
      HttpServerConfig.layer
    )
}
```

## Step 5: Running the Application

Now, if we run the application, it will start the server using the configuration defined in the `application.conf` file with its default values:

```
$ sbt run
Server started on port: 8080
```

We set the `HOST` and `PORT` environment variables to override the default values:

```scala
$ HOST=localhost PORT=8081 sbt run
Server started on port: 8081
```

## Conclusion

This tutorial covered how to use ZIO Config to read configuration data from HOCON files and configure our application. We haven't covered all the features of the ZIO Config library. To learn more about this library please visit the [ZIO Config documentation](https://zio.github.io/zio-config/).

The complete working example of this tutorial is available on the `configurable-app` branch of our [ZIO Quickstart: Building RESTful Web Service](https://github.com/zio/zio-quickstart-restful-webservice/tree/configurable-app) quickstart on GitHub.
