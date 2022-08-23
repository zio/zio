---
id: dependency-injection-in-zio
title: "Getting Started With Dependency Injection in ZIO"
sidebar_label: "Getting Started"
---

:::caution
In this page, we will focus on essential parts of dependency injection in ZIO. So we are not going to cover all the best practices for writing ZIO services.

In real world applications, we encourage to use [service pattern](service-pattern/service-pattern.md) to write ZIO services.
:::

We can achieve dependency injection through these three simple steps:
1. Accessing services from the ZIO environment through the `ZIO.service` operation.
2. Building the dependency graph using manual or automatic layer construction.
3. Providing dependencies to the ZIO environment through the `ZIO.provideXYZ` operations.

## Step 1: Accessing Services From The ZIO Environment

To write application logic, we need to access services from the ZIO environment. We can do this by using the `ZIO.service` operation.

- When we call `ZIO.service[A]`, we are asking the ZIO environment for the `A` service. So then we can access all the functionality of the `A` service.
- Accordingly, we can access all services that we need and use them in our application. Because the ZIO environment is composable, finally the type of our application will denote all the services that it needs.

For example, assume we have the following services:

```scala mdoc:silent
import zio._

trait A {
  def foo(): UIO[String]
}

trait B {
  def bar(i: String): UIO[Int]
}
```

We can write the following application logic:

```scala mdoc:silent
import zio._

// myApp requires A and B services to fulfill its functionality
val myApp: ZIO[A with B, Nothing, Int] =
  for {
    a <- ZIO.service[A] 
    r <- a.foo()
    b <- ZIO.service[B]
    o <- b.bar(r)
  } yield o
```

## Step 2: Building The Dependency Graph

To be able to run our application, we need to build the dependency graph that it needs. This can be done using the `ZLayer` data type. It allows us to build up the whole application's dependency graph by composing layers manually or automatically.

Assume each of these services has its own layer like the below:

```scala mdoc:silent
object A {
  val layer: ZLayer[Any, Nothing, A] = 
    ZLayer.succeed(???) 
}

object B {
  val layer: ZLayer[Any, Nothing, B] = 
    ZLayer.succeed(???)
}
```

In the previous example, the `myApp` application requires the `A` and `B` services. We can build that manually by composing two `A` and `B` layers horizontally:

```scala mdoc:silent
val appLayer: ZLayer[Any, Nothing, A with B] = 
  A.layer ++ B.layer
```

Or we can use automatic layer construction:

```scala mdoc:compile-only
val appLayer: ZLayer[Any, Nothing, A with B] =
  ZLayer.make[A with B](A.layer, B.layer) 
```

:::note
Automatic layer construction is useful when the dependency graph is large and complex. So in simple cases, it doesn't demonstrate the power of automatic layer construction.
:::

## Step 3: Providing Dependencies to the ZIO Environment

To run our application, we need to provide (inject) all dependencies to the ZIO environment. This can be done by using one of the `ZIO.provideXYZ` operations. This allows us to propagate dependencies from button to top:

Let's provide our application with the `appLayer`:

```scala mdoc:silent
val result: ZIO[Any, Nothing, Int] = myApp.provideLayer(appLayer)
```

Here the `ZLayer` data types act as a dependency/environment eliminator. By providing required dependencies to our ZIO application, `ZLayer` eliminates all dependencies from the environment of our application.

That's it! Now we can run our application:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = result
}
```

Usually, when we use automatic layer construction, we skip the second step and instead provide all dependencies directly to the `ZIO.provide` operation. It takes care of building the dependency graph and providing the dependency graph to our ZIO application:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = myApp.provide(A.layer, B.layer)
}
```
