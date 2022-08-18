---
id: dependency-propagation
title: "Dependency Propagation"
---

When we write an application, our application has a lot of dependencies. We need a way to provide implementations and to feed and propagate all dependencies throughout the whole application. We can solve the propagation problem by using _ZIO environment_.

During the development of an application, we don't care about implementations. Incrementally, when we use various effects with different requirements on their environment, all parts of our application compose together, and at the end of the day we have a ZIO effect which requires some services as an environment. Before running this effect by `unsafeRun` we should provide an implementation of these services into the ZIO Environment of that effect.

ZIO has some facilities for doing this. `ZIO#provide` is the core function that allows us to _feed_ an `R` to an effect that requires an `R`.

Notice that the act of `provide`ing an effect with its environment, eliminates the environment dependency in the resulting effect type, represented by type `Any` of the resulting environment.

## Using `ZIO#provideEnvironment` Method

The `ZIO#provideEnvironment` takes an instance of `ZEnvironment[R]` and provides it to the `ZIO` effect which eliminates its dependency on `R`:

```scala
trait ZIO[-R, +E, +A] {
  def provideEnvironment(r: => ZEnvironment[R]): IO[E, A]
}
```

This is similar to dependency injection, and the `provide*` function can be thought of as _inject_.

```scala mdoc:invisible:reset
import zio._
```

Assume we have the following services:

```scala mdoc:silent:nest
trait EmailService {
  def send(email: String, content: String): UIO[Unit]
}

object EmailService {
  def send(email: String, content: String) = ZIO.serviceWithZIO[EmailService](_.send(email, content))
}
```

Let's write a simple program using `EmailService` service:

```scala mdoc:silent:nest
val app: ZIO[EmailService, Nothing, Unit] = EmailService.send("john@doe.com", "Hello John!")
```

We can `provide` implementation of `EmailService` service into the `app` effect:

```scala mdoc:silent:nest
val loggingImpl = new EmailService {
  override def send(email: String, content: String): UIO[Unit] = ???
}

val effect = app.provideEnvironment(ZEnvironment(loggingImpl))
```

Most of the time, we don't use `ZIO#provideEnvironment` directly to provide our services; instead, we use `ZLayer` to construct the dependency graph of our application, then we use methods like `ZIO#provide`, `ZIO#provideSome` and `ZIO#provideCustom` to propagate dependencies into the environment of our ZIO effect.

## Using `ZIO#provide` Method

Unlike the `ZIO#provideEnvironment` which takes a `ZEnvironment[R]`, the `ZIO#provide` takes a `ZLayer` to the ZIO effect and translates it to another level.

Assume we have written this piece of program that requires `Foo` and `Bar` services:

```scala mdoc:silent:nest
trait Foo {
  def foo(): UIO[String]
}

object Foo {
  def foo(): ZIO[Foo, Nothing, String] = ZIO.serviceWithZIO[Foo](_.foo())
}

case class FooLive() extends Foo {
  override def foo(): UIO[String] = ZIO.succeed("foo")
}

object FooLive {
  val layer: ULayer[Foo] = ZLayer.succeed(FooLive())
}

trait Bar {
  def bar(): UIO[Int]
}

object Bar {
  def bar(): ZIO[Bar, Nothing, Int] = ZIO.serviceWithZIO[Bar](_.bar())
}

case class BarLive() extends Bar {
  override def bar(): UIO[Int] = ZIO.succeed(1)
}

object BarLive {
  val layer: ULayer[Bar] = ZLayer.succeed(BarLive())
}

val myApp: ZIO[Foo & Bar, Nothing, Unit] = for {
  foo  <- Foo.foo()
  bar  <- Bar.bar()
  _    <- ZIO.debug(s"foo: $foo, bar: $bar")
} yield ()
```

We provide implementations of `Foo`, `Bar` services to the `myApp` effect by using `ZIO#provide` method:

```scala mdoc:silent:nest
val mainEffect: ZIO[Any, Nothing, Unit] = 
  myApp.provide(FooLive.layer, BarLive.layer)
```

As we see, the type of our effect converted from `ZIO[Foo & Bar, Nothing, Unit]` which requires two services to `ZIO[Any, Nothing, Unit]` effect which doesn't require any services.

## Using `ZIO#provideSome` Method

Sometimes we have written a program, and we don't want to provide all its requirements. In these cases, we can use `ZIO#provideSome` to partially apply some layers to the `ZIO` effect.

In the previous example, if we just want to provide the `Foo`, we should use `ZIO#provideSome`:

```scala mdoc:compile-only
val mainEffectSome: ZIO[Bar, Nothing, Unit] = 
  myApp.provideSome(FooLive.layer)
```

:::caution
When using `ZIO#provideSome[R0]`, we should provide the remaining type as `R0` type parameter. This workaround helps the compiler to infer the proper types.
:::
