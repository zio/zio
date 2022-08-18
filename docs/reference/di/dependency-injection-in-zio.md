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
      .provide(         // providing (injecting) all required services that myApp needs
        ZLayer.succeed( // A simple layer that provides implementation of type Int
          5             // Implementation of Int service
        )              
      )
}
```

Here are the steps:
1. We started by writing our application logic. Whenever we wanted to use a service of type `Int` we accessed it from the environment using the `ZIO.service` method. So, we can continue to write our application logic without worrying about what implementation of the service we are using.
2. We created an implementation of Int service, the concrete `5` value.
3. We created a layer for the concrete implementation of `Int` service, `ZLayer.succeed(5)`.
4. Finally, we provided (injected) the layer to our application, `myApp.provide(ZLayer.succeed(5))`. This propagates the layer from bottom to top and provides the concrete implementation of `Int` service to each effect that needs it.
