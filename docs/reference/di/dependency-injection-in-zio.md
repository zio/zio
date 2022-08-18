---
id: dependency-injection-in-zio
title: "Dependency Injection in ZIO"
---

Here is the minimum effort to get dependency injection working in ZIO:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val myApp: ZIO[Int, Nothing, Long] = // myApp requires a service of type Int
    for {
      a <- ZIO.service[Int] // Accessing a service of type Int
      _ <- ZIO.debug(s"received a value object of Int service from the environment: $a")
    } yield a.toLong * a.toLong

  def run =
    myApp
      .debug("result") // printing the result of the myApp
      .provide(                // providing (injecting) all required services that myApp needs
        ZLayer.succeed(1)      // A simple layer that provides a value of type Int
      )
}
```