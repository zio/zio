---
id: zio-test-akka-http
title: "ZIO Test Akka HTTP"
---

[ZIO Test Akka HTTP](https://github.com/senia-psm/zio-test-akka-http) is an Akka-HTTP Route TestKit for zio-test.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "info.senia" %% "zio-test-akka-http" % "2.0.0-RC5"
```

## Example

An example of writing Akka HTTP Route test spec:

```scala
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Directives.complete
import zio.test.Assertion._
import zio.test._
import zio.test.akkahttp.AkkaZIOSpecDefault

object MySpec extends AkkaZIOSpecDefault {
  def spec =
    suite("MySpec")(
      test("my test") {
        assertZIO(Get() ~> complete(HttpResponse()))(
          handled(
            response(equalTo(HttpResponse()))
          )
        )
      }
    )
}
```
