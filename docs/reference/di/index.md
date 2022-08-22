---
id: index
title: "Introduction To Dependency Injection"
---

## What is Dependency?

When we implement a service, we might need to use other services. So a dependency is just another service that is required to fulfill its functionality:

```scala
class Editor {
 val formatter = new Formatter
 val compiler = new Compiler
 
 def formatAndCompile(code: String): UIO[String] = 
 formatter.format(code).flatMap(compiler.compile)
}
```

## What is Dependency Injection?

Dependency injection is a pattern for decoupling the usage of dependencies from their actual creation process. In other words, it is a process of injecting dependencies of service from the outside world. The service itself doesn't know how to create its dependencies.

The following example shows an `Editor` service that depends on `Formatter` and `Compiler` services. It doesn't use dependency injection:

```scala mdoc:invisible
class Formatter
class Compiler
```

```scala mdoc:compile-only
import zio._ 

class Editor {
 private val formatter = new Formatter
 private val compiler = new Compiler
 
 def formatAndCompile(code: String): UIO[String] =
 formatter.format(code).flatMap(compiler.compile)
}
```

The `Editor` class in the above example is responsible for creating the `Formatter` and `Compiler` services. The client of the `Editor` class doesn't have any control over these services. The client can't use a different implementation for the `Formatter` and `Compiler` services. So it makes it hard to test the `Editor` class.

Let's try to change the above example to use the constructor-based dependency injection pattern:

```scala mdoc:invisible

```scala mdoc:silent:nest
import zio._

class Editor(formatter: Formatter, compiler: Compiler) {
 def formatAndCompile(code: String): UIO[String] = ???
}
```

In this example, the `Editor` service is not responsible for creating its dependencies. Instead, they are expected to be injected from the caller site. The `Editor` service does not know how its dependencies are created, they are just injected into its constructor.

So dependency injection is a very simple concept and can be implemented with simple constructs. In a lot of situations, we are not required to use any tools or frameworks.

In the [motivation page](motivation.md) we explain why applications should use the dependency injection pattern in more detail.

## ZIO's Built-in Dependency Injection

ZIO has a full solution to the dependency injection problem. It provides a built-in approach to dependency injection using the following tools in combination together:

1. **ZIO Environment**
  - We use the `ZIO.serviceXYZ` to access services inside the ZIO environment, without having any knowledge of how the services are created or implemented. Using `ZIO.serviceXYZ` helps us to decouple our usage of services from the implementation of the services. Consequently, all dependencies will be encoded inside the `R` type parameter of our ZIO application. This specifies which services are required to fulfill the application's functionality.
  - We use the `ZIO.provideXYZ` to provide services to the ZIO environment. This is the opposite operation of `ZIO.serviceXYZ`. It allows us to inject all dependencies into the ZIO environment.
2. **ZLayer**— We use layers to create the dependency graph that our application depends on.

We can achieve dependency injection through these three simple steps:

1. **Accessing The ZIO Environment**— We use `ZIO.service` to access services from the environment.

2. **[Building Dependency Graph](building-dependency-graph.md)**— We use `ZLayer` to build the dependency graph. Assume we have several services with their dependencies, and we need a way to compose and wire up these dependencies to create the application's dependency graph. `ZLayer` is a ZIO solution for this problem. It allows us to build up the whole application dependency graph by composing layers horizontally and vertically.

3. **[Dependency Propagation](dependency-propagation.md)**— The `ZIO#provide` method allows us to propagate the dependencies from bottom to top.

## ZIO's Dependency Injection Features

Dependency injection in ZIO is very powerful which increases the developer productivity. Let's recap some important features of dependency injection in ZIO:

1. **Composable**
  - **Composable Environment**— Because of the very composable nature of the `ZIO` data type, its environment type parameter is also composable. So when we compose multiple `ZIO` effects, where each one requires a specific service, we finally get a `ZIO` effect that requires all the required services that each of the composed effects requires. 
  
  For example, if we `zip` two effects of type `ZIO[A, Nothing, Int]` and `ZIO[B, Throwable, String]`, the result of this operation will become `ZIO[A with B, Throwable, (Int, String)]`. The result operation requires both `A` and `B` services.

  - **Composable Dependencies**— The `ZLayer` is also composable, As well as the ZIO's environment type parameter. So we can compose multiple layers to [create a complex dependency graph](building-dependency-graph.md).

2. **Type-Safe**— All the required dependencies should be provided at compile time. If we forget to provide the required services at compile time, we will get a compile error. So if our program compiles successfully, we are sure that we haven't runtime errors due to missing dependencies.

3. **Effectful**— We build dependency graphs using `ZLayer. Since `ZLayer` is effectful, we can create a dependency graph in an effectful way.

4. **Resourceful**— It also helps us to have resourceful dependencies, where we can manage the creation and release phases of the dependencies.

5. **Parallelism**— All dependencies are created in parallel, and will be provided to our application.

## Other Frameworks

Using `ZLayer` along with the ZIO environment to use dependency injection is optional. While we encourage users to use ZIO's idiomatic dependency injection, it is not mandatory.

We can still use other DI solutions. Here are some other options:
- [Guice](https://github.com/google/guice)
- [izumi distage](https://izumi.7mind.io/distage/index.html)
- [MacWire](https://github.com/softwaremill/macwire)
