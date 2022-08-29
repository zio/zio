---
id: dependency-injection-in-zio
title: "Getting Started With Dependency Injection in ZIO"
sidebar_label: "Getting Started"
---

:::caution
In this page, we will focus on essential parts of dependency injection in ZIO. So we are not going to cover all the best practices for writing ZIO services.

In real world applications, we encourage to use [service pattern](../service-pattern/service-pattern.md) to write ZIO services.
:::

We can achieve dependency injection through these three simple steps:
1. Accessing services from the ZIO environment through the `ZIO.serviceXYZ` operations.
2. Writing application logic using services and composing them together.
3. Building the dependency graph using manual or automatic layer construction (optional).
4. Providing dependencies to the ZIO environment through the `ZIO.provideXYZ` operations.

## Step 1: Accessing Services From The ZIO Environment

To write application logic, we need to access services from the ZIO environment. We can do this by using the `ZIO.serviceXYZ` operation.

For example, assume we have the following services:

```scala mdoc:silent
import zio._

final class A {
  def foo: UIO[String] = ZIO.succeed("Hello!")
}

final class B {
  def bar: UIO[Int] = ZIO.succeed(42)
}
```

When we call `ZIO.service[A]`, we are asking the ZIO environment for the `A` service. So then we can access all the functionality of the `A` service:

```scala mdoc:compile-only
val effect: ZIO[A, Nothing, String] =
  for {
    a <- ZIO.service[A] 
    r <- a.foo
  } yield r
```

The signature of the above effect, says that in order to produce a value of type `String`, I need the `A` service from the ZIO environment.

We can also use `ZIO.serviceWith`/`ZIO.srviceWithZIO` to directly access one of the service functionalities:

```scala mdoc:silent
object A {
  def foo: ZIO[A, Nothing, String] = ZIO.serviceWithZIO[A](_.foo) 
}

object B {
  def bar: ZIO[B, Nothing, Int] = ZIO.serviceWithZIO[B](_.bar)
}
```

## Step 2: Writing Application Logic Using Services

ZIO is a composable data type on its environment type parameter. So when we have an effect that requires the `A` service, and also we have another effect that requires the `B` service; when we compose these two services together, the resulting effect requires both `A` and `B` services:

```scala mdoc:silent
// Sequential Composition Example
val myApp: ZIO[A with B, Nothing, (String, Int)] =
  for {
    a <- A.foo
    b <- B.bar
  } yield (a, b)
```

```scala mdoc:silent:nest
// Parallel Composition Example
val myApp: ZIO[A with B, Nothing, (String, Int)] = A.foo <&> B.bar
```

Now the `myApp` effect requires `A` and `B` services to fulfill its functionality. We can see that we are writing application logic, we are not concerned about how services will be created! We are focused on using services to write the application logic.

In the next step, we are going to build a dependency graph that holds two `A` and `B` services.

## Step 2: Building The Dependency Graph (Optional)

To be able to run our application, we need to build the dependency graph that it needs. This can be done using the `ZLayer` data type. It allows us to build up the whole application's dependency graph by composing layers manually or automatically.

Assume each of these services has its own layer like the below:

```scala mdoc:silent:nest
object A {
  def foo: ZIO[A, Nothing, String] = 
    ZIO.serviceWithZIO[A](_.foo) 
  
  val layer: ZLayer[Any, Nothing, A] = 
    ZLayer.succeed(new A) 
}

object B {
  def bar: ZIO[B, Nothing, Int] = 
    ZIO.serviceWithZIO[B](_.bar)
  
  val layer: ZLayer[Any, Nothing, B] = 
    ZLayer.succeed(new B)
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
val result: ZIO[Any, Nothing, (String, Int)] = myApp.provideLayer(appLayer)
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
