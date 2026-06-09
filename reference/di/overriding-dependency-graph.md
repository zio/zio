# Overriding Dependency Graph

> Overview of overriding ZIO dependency graphs using global and local environments for flexible dependency injection.

We can create a [ZIO](../core/zio/zio.md) application by providing a local or a global environment, or a combination:

## Global Environment

It is usual when writing ZIO applications to provide layers at the end of the world. Then we provide layers to the whole ZIO application all at once. This pattern uses a single global environment for all ZIO applications:

```scala

object MainApp extends ZIOAppDefault {
  val myApp: ZIO[ServiceA & ServiceB & ServiceC & ServiceD, Throwable, Unit] = ???
    
  def run = myApp.provide(a, b, c, d)
}
```

## Local Environment

Occasionally, we may need to provide different environments for different parts of our application, or it may be necessary to provide a single global environment for the entire application except for some inner layers.

Providing a layer locally is analogous to overriding a method in an object-oriented paradigm. So we can think of that as overriding the global environment:

```scala

object MainApp extends ZIOAppDefault {
  def myApp: ZIO[A & B & C, Throwable, Unit] = {
    def innerApp1: ZIO[A & B & C, Throwable, Unit] = ???
    def innerApp2: ZIO[A & C,     Throwable, Unit] = ???

    innerApp1.provideSomeLayer[A & B](localC) *> innerApp2
  }

  def run = myApp.provide(globalA, globalB, globalC)
}
```

ZIO Test's [Live service](../test/services/live.md) uses this pattern to provide real environment to a single part of an effect.

## See Also

- [Providing Different Implementation of a Service](providing-different-implementation-of-a-service.md) — Provide multiple service implementations to ZIO applications without modifying core logic using dependency injection.
- [Building Dependency Graph](building-dependency-graph.md) — Build dependency graphs in ZIO using manual layer composition or automatic dependency injection with compile-time validation.
