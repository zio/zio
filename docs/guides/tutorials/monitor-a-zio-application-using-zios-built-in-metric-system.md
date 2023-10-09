---
id: monitor-a-zio-application-using-zio-built-in-metric-system
title: "Tutorial: How to Monitor a ZIO Application Using ZIO's Built-in Metric System?"
sidebar_label: "Monitoring a ZIO Application Using ZIO's Built-in Metric System"
---

## Introduction

ZIO has a built-in metric system that allows us to monitor the performance of our application. This is very useful for debugging and tuning our application. In this tutorial, we are going to learn how to add metrics to our application and then how to connect our application to one of the metric backends, e.g. Prometheus.

## Running the Example

To access the code examples, you can clone the [ZIO Quickstarts](http://github.com/zio/zio-quickstarts) project:

```bash
$ git clone git@github.com:zio/zio-quickstarts.git 
$ cd zio-quickstarts/zio-quickstart-restful-webservice-metrics
```

And finally, run the application using sbt:

```bash
$ sbt run
```

## Trying a Simple Example

Before going to apply the metrics in our application, let's try a simple example:

```scala mdoc:compile-only
import zio._
import zio.metrics.Metric

object MainApp extends ZIOAppDefault {
  private val count = Metric.counterInt("fib_call_total").fromConst(1)

  def fib(n: Int): ZIO[Any, Nothing, Int] =
    if (n <= 1) ZIO.succeed(1)
    else for {
      a <- fib(n - 1) @@ count
      b <- fib(n - 2) @@ count
    } yield a + b

  def run =
    for {
      i <- Console.readLine("Please enter a number to calculate fibonacci: ").mapAttempt(_.toInt)
      n <- fib(i) @@ count
      _ <- Console.printLine(s"fib($i) = $n")
      c <- count.value
      _ <- ZIO.debug(s"number of fib calls to calculate fib($i): ${c.count}")
    } yield ()
}
```

In this example, we are going to calculate the fibonacci number for a given number. We also count the number of times we call the `fib` function using the `count` metric. Finally, we will print the value of the metric as a debug message.

This is a pedagogical example of how to use metrics. But in real life, we will probably want to poll the metrics using a web API and feed them to a monitoring system, e.g. Prometheus. In the following sections, we will learn how to do that by applying the metrics to our RESTful web service.

## Adding Metrics to Our Restful Web Service

In this section, we are going to define a new metric called `count_all_requests` that counts the number of requests to our web service:

```scala mdoc:silent
import zio._
import zio.metrics._

def countAllRequests(method: String, handler: String) =
  Metric.counterInt("count_all_requests").fromConst(1)
    .tagged(
      MetricLabel("method", method),
      MetricLabel("handler", handler)
    )
```

This metric also has two tags, `method` and `handler`, which are used to tag the metric which enables us to group the metrics by HTTP method and the URL handler.

All metrics are defined as a `ZIOAspect` that helps us to add metrics to our application in an aspect-oriented fashion.

## Applying the Metrics to Our Restful Web Service

After defining the metric, we need to apply it to our web service. We can do this by using the `@@` syntax:

```scala mdoc:invisible
import zio._
import zio.json._

case class User(name: String, age: Int)

object User {
  implicit val encoder: JsonEncoder[User] =
    DeriveJsonEncoder.gen[User]
  implicit val decoder: JsonDecoder[User] =
    DeriveJsonDecoder.gen[User]
}

trait UserRepo {
  def register(user: User): Task[String]

  def lookup(id: String): Task[Option[User]]
  
  def users: Task[List[User]]
}

object UserRepo {
  def register(user: User): ZIO[UserRepo, Throwable, String] =
    ZIO.serviceWithZIO[UserRepo](_.register(user))

  def lookup(id: String): ZIO[UserRepo, Throwable, Option[User]] =
    ZIO.serviceWithZIO[UserRepo](_.lookup(id))

  def users: ZIO[UserRepo, Throwable, List[User]] =
    ZIO.serviceWithZIO[UserRepo](_.users)
}
```

```scala mdoc:silent
import zhttp.http._
import zio.json._


object UserApp {
  def apply(): Http[UserRepo, Throwable, Request, Response] =
    Http.collectZIO[Request] {
      // GET /users
      case Method.GET -> !! / "users" =>
        UserRepo.users
          .map(response => Response.json(response.toJson)) @@
          countAllRequests("GET", "/users")
    }
}
```

We can do the same for the rest of the HTTP request handlers in our web service. After applying all the metrics, it is time to prove the metrics as a RESTful API. Before that, let's add the required dependencies to our project.

## Adding Dependencies to the Project

In the following sections, we are going to utilize the `zio-metrics-connector` module from the ZIO ZMX project and also provide metrics as a REST API. So let's add the following dependency to our project:

```scala
libraryDependencies += "dev.zio" %% "zio-metrics-connectors" % "2.2.0"
libraryDependencies += "dev.zio" %% "zio-metrics-connectors-prometheus" % "2.2.0"
```

This module provides various connectors for metrics backend, e.g. Prometheus.

## Providing Metrics as a REST API For Prometheus

The following snippet shows how to provide an HTTP endpoint that exposes the metrics as a REST API for Prometheus:

```scala mdoc:invisible:reset

```

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

```scala mdoc:silent
import zhttp.http._
import zio._
import zio.metrics.connectors.prometheus.PrometheusPublisher

object PrometheusPublisherApp {
  def apply(): Http[PrometheusPublisher, Nothing, Request, Response] = {
    Http.collectZIO[Request] { case Method.GET -> !! / "metrics" =>
      ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
    }
  }
}
```

Next, we need to add the `PrometheusPublisherApp` HTTP App to our application:

```scala mdoc:silent
import zio._
import zhttp.service.Server
import zio.metrics.connectors.{MetricsConfig, prometheus}

object MainApp extends ZIOAppDefault {
  private val metricsConfig = ZLayer.succeed(MetricsConfig(1.seconds))

  def run =
    Server.start(
      port = 8080,
      http = GreetingApp() ++ DownloadApp() ++ CounterApp() ++ UserApp() ++ PrometheusPublisherApp()
    ).provide(
      // An layer responsible for storing the state of the `counterApp`
      ZLayer.fromZIO(Ref.make(0)),
      
      // To use the persistence layer, provide the `PersistentUserRepo.layer` layer instead
      InmemoryUserRepo.layer,

      // configs for metric backends
      metricsConfig,

      // The prometheus reporting layer
      prometheus.publisherLayer,
      prometheus.prometheusLayer,
    )
}
```

## Testing the Metrics

Now that we have the metrics as a REST API, we can test them. Let's run the application and then send some requests to the application as below:

```bash
$ curl -i http://localhost:8080/users -d '{"name": "John", "age": 42}'
$ curl -i http://localhost:8080/users -d '{"name": "Jane", "age": 43}'
$ curl -i http://localhost:8080/users
```

If we fetch the metrics from the "/metrics" endpoint, we will see the metrics in the Prometheus format, like below:

```bash
$ curl -i http://localhost:8080/metrics
HTTP/1.1 200 OK
content-type: text/plain
content-length: 278

# TYPE count_all_requests counter
# HELP count_all_requests Some help
count_all_requests{method="POST",handler="/users"}  2.0 1655210796102
# TYPE count_all_requests counter
# HELP count_all_requests Some help
count_all_requests{method="GET",handler="/users"}  1.0 1655210796102⏎
```

Now that we have the metrics as a REST API, we can add this endpoint to our Prometheus server to fetch the metrics periodically.

## Conclusion

In this tutorial, we have learned how to define metrics and apply them to our application. We have also learned how to provide the metrics as a REST API which then can be polled by a Prometheus server.

All the source code associated with this article is available on the [ZIO Quickstart](http://github.com/zio/zio-quickstarts) on Github.
