---
id: zmanaged
title: "ZManaged"
---

A `ZManaged[R, E, A]` is a managed resource, that requires an `R`, and may fail with an `E` value, or succeed with an `A`.

 `ZManaged` is a data structure that encapsulates the acquisition and the release of a resource, which may be used by invoking the `use` method of the resource. The resource will be automatically acquired before the resource is used and automatically released after the resource is used.

Resources do not survive the scope of `use`, meaning that if we attempt to capture the resource, leak it from `use`, and then use it after the resource has been consumed, the resource will not be valid anymore and may fail with some checked error, as per the type of the functions provided by the resource.

## Creation

In this section, we explore some common ways to create managed resources.

### Making

`ZManaged` has a `make` constructor which requires `acquire` and `release` actions:


```scala
val managed = ZManaged.make(acquire)(release)
```

In the following example, we have a managed resource which requires `Console` as an environment to print the first line of a given file. The `BufferedReader` will be acquired before printing the first line and automatically will be released after using `BufferedReader`:

```scala
import zio.console._
def printFirstLine(file: String): ZIO[Console, Throwable, Unit] = {
  def acquire(file: String) = ZIO.effect(new BufferedReader(new FileReader(file)))
  def release(reader: BufferedReader) = ZIO.effectTotal(reader.close())

  ZManaged.make(acquire(file))(release).use { reader =>
    putStrLn(reader.readLine()) 
  }
}
```

If we need to have different logic in `release` action based on exit status of `acquire` action, we can use `ZManaged.makeExit` constructor:

```scala
trait ZManaged[-R, +E, +A] {
  def makeExit[R, R1 <: R, E, A](
    acquire: ZIO[R, E, A]
  )(release: (A, Exit[Any, Any]) => ZIO[R1, Nothing, Any]): ZManaged[R1, E, A]
}
```

Not that like `ZManaged.make`, both of `acquire` and `release` actions are uninterruptible in `ZManaged.makeExit`.

### Lifting a pure value

We can lift pure values to `ZManaged` with `ZIO.succeed` method:

```scala
val managedString = ZManaged.succeed("Hello, World!")
val managedBoolean = ZManaged.succeed(true)
```

### Lifting a ZIO effect

Every `ZIO` effect can be lifted to `ZManaged` with `ZManaged.fromEffect` or `ZIO#toZManaged_` operations:

```scala
val managedHello = ZManaged.fromEffect(putStrLn("Hello, World!"))
val managedHello_ = putStrLn("Hello, World!").toManaged_
```

This is useful when we want to combine `ZManaged` effects with `ZIO` effects. Assume during creation of managed resource, we need to log some information, we can lift a `ZIO` effect to `ZManaged` world:


```scala
def userRepository: ZManaged[Blocking with Console, Throwable, UserRepository] = for {
  cfg <- dbConfig.toManaged_
  _ <- putStrLn("Read database config").toManaged_
  _ <- initializeDb(cfg).toManaged_
  _ <- putStrLn("Database initialized").toManaged_
  xa <- makeTransactor(cfg)
  _ <- putStrLn("Created new blocking transactor").toManaged_
} yield new UserRepository(xa)
```

### Making from AutoClosable Resources

If the resource implemented the `AutoClosable` interface, we can easily make a `ZManaged` from it by using `ZManaged.fromAutoClosable` constructor:

```scala
ZManaged.fromAutoCloseable(ZIO.effect(new FileInputStream("file.txt")))
// res1: ZManaged[Any, Throwable, FileInputStream] = zio.ZManaged$$anon$2@19dd8c22
ZManaged.fromAutoCloseable(ZIO.effect(fromResource("file.txt")))
// res2: ZManaged[Any, Throwable, scala.io.BufferedSource] = zio.ZManaged$$anon$2@60c09e2e
ZManaged.fromAutoCloseable(ZIO.effect(fromFile("file.txt")))
// res3: ZManaged[Any, Throwable, scala.io.BufferedSource] = zio.ZManaged$$anon$2@210bd8dd
```

### Making Interruptible Acquires

By default, when we create a `ZManaged` via `ZManaged.make` constructor, the `acquire` and `release` actions are _uninterruptible_. But what if we want to make the `acquire` action interruptible? The `makeInterruptible` constructor does that for us:

```scala
trait ZManaged[-R, +E, +A] {
  def makeInterruptible[R, E, A](
    acquire: ZIO[R, E, A]
  )(release: A => URIO[R, Any]): ZManaged[R, E, A]
}
```

Making `ZManaged` via this constructor makes the `acquire` action interruptible, the release action remains uninterruptible.

If we want to decide what to do in the `release` action based on how the `acquire` action is completed, whether by success, failure, or interruption; we can use the `makeReserve` constructor. The type of `release` action is `Exit[Any, Any] => URIO[R, Any]` which provides us the `Exit` status of the `acquire` action, so we can decide what to do based on the exit status of `acquire` action:

```scala
trait ZManaged[-R, +E, +A] {
  def makeReserve[R, E, A](reservation: ZIO[R, E, Reservation[R, E, A]]): ZManaged[R, E, A]
}
```

`Reservation` data type is defined as follows:


```scala
final case class Reservation[-R, +E, +A](acquire: ZIO[R, E, A], release: Exit[Any, Any] => URIO[R, Any])
```

## Usage 

### use

Inside the `use` block, we can use the managed resource and return a new value. The `use` method converts a managed resource from `ZManaged` world to `ZIO` world:

```scala
def firstLine(file: String): ZIO[Console, Throwable, Unit] =
  ZManaged.fromAutoCloseable(ZIO.effect(fromFile(file))).use { reader =>
    putStrLn(reader.bufferedReader().readLine())
  }
```

### useNow

If our managed resource could be valid after releasing resources, we can convert that `ZManaged` to `ZIO` effect by calling `ZManaged#useNow`. 

```scala
val hello: UIO[String] = ZManaged.succeed("Hello, World!").useNow
```

This is useful when we have composed some `ZManaged` with some `ZIO` effects, and the result can be outlived outside the `use` block:

```scala
def is(file: String): Task[FileInputStream]  = Task.effect(???)
def os(file: String): Task[FileOutputStream] = Task.effect(???)

def close(resource: Closeable): UIO[Unit] = Task.effectTotal(???)
def copy(from: FileInputStream, to: FileOutputStream): Task[Unit] = ???

def transfer(from: String, to: String): ZIO[Any, Throwable, Unit] = {
  val resource: ZManaged[Any, Throwable, Unit] = for {
    from <- ZManaged.make(is(from))(close)
    to   <- ZManaged.make(os(to))(close)
    _    <- copy(from, to).toManaged_
  } yield ()
  resource.useNow
}
```

> **Note:**
>
> Be careful, don’t call the `useNow` method on a managed resource that isn’t valid after its release actions. For example, running `useNow` on `ZManaged.fromAutoCloseable(ZIO.effect(fromFile("file.txt")))` doesn’t make sense, because after releasing a file, we haven’t any handle to that file.

### useForever

Assume we are going to make a managed resource long-lived. The `ZManaged#useForever` does that for us, it converts a `ZManaged` effect to a `ZIO` effect which will remain forever running.

## Combinators

`ZManaged` like the `ZIO` effect has almost all combinators that we introduced on the [ZIO](../core/zio.md) page. We can use them to create more complicated `ZManaged` ones.

There is also some combinators which specific for `ZManaged`:

1. **ensuringFirst**— This combinator adds a `ZIO` effect as a finalizer to an existing `ZManaged` effect. This finalizer will be executed before the existing finalizers:

```scala
trait ZManaged[-R, +E, +A] {
  def ensuringFirst[R1 <: R](f: ZIO[R1, Nothing, Any]): ZManaged[R1, E, A]
}
```

2. **onExitFirst**— Like `ensuringFirst`, but it has access to the `ZManaged`’s result:

```scala
trait ZManaged[-R, +E, +A] {
  def onExitFirst[R1 <: R](cleanup: Exit[E, A] => ZIO[R1, Nothing, Any]): ZManaged[R1, E, A]
}
```

3. **withEarlyRelease**— It will produce another `ZManaged` which provides a canceler that can be used to eagerly execute the finalizer of this `ZManaged`:

```scala
trait ZManaged[-R, +E, +A] {
  def withEarlyRelease: ZManaged[R, E, (UIO[Any], A)]
}
```

4. **withEarlyReleaseExit**— Like `withEarlyRelease`, but allows us to specify an exit value in the event of early release:

```scala
trait ZManaged[-R, +E, +A] {
  def withEarlyReleaseExit(e: Exit[Any, Any]): ZManaged[R, E, (UIO[Any], A)]
}
```
