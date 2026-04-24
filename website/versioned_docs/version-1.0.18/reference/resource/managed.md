---
id: managed
title: "Managed"
---

`Managed[E, A]` is a type alias for `ZManaged[Any, E, A]`, which represents a managed resource that has no requirements, and may fail with an `E`, or succeed with an `A`.


The `Managed` type alias is defined as follows:

```scala
type Managed[+E, +A] = ZManaged[Any, E, A]
```

`Managed` is a data structure that encapsulates the acquisition and the release of a resource, which may be used by invoking the `use` method of the resource. The resource will be automatically acquired before the resource is used, and automatically released after the resource is used.

Resources do not survive the scope of `use`, meaning that if you attempt to capture the resource, leak it from `use`, and then use it after the resource has been consumed, the resource will not be valid anymore and may fail with some checked error, as per the type of the functions provided by the resource.

```scala
import zio._
def doSomething(queue: Queue[Int]): UIO[Unit] = IO.unit

val managedResource = Managed.make(Queue.unbounded[Int])(_.shutdown)
val usedResource: UIO[Unit] = managedResource.use { queue => doSomething(queue) }
```

In this example, the queue will be created when `use` is called, and `shutdown` will be called when `doSomething` completes.

## Creating a Managed

As shown in the previous example, a `Managed` can be created by passing an `acquire` function and a `release` function.

It can also be created from an effect. In this case the release function will do nothing.
```scala
import zio._
def acquire: IO[Throwable, Int] = IO.effect(???)

val managedFromEffect: Managed[Throwable, Int] = Managed.fromEffect(acquire)
```

You can create a `Managed` from a pure value as well.
```scala
import zio._
val managedFromValue: Managed[Nothing, Int] = Managed.succeed(3)
```

## Managed with ZIO environment

`Managed[E, A]` is actually an alias for `ZManaged[Any, E, A]`. If you'd like your `acquire`, `release` or `use` functions to require an environment R, just use `ZManaged` instead of `Managed`.

```scala
import zio._
import zio.console._

val zManagedResource: ZManaged[Console, Nothing, Unit] = ZManaged.make(console.putStrLn("acquiring").orDie)(_ => console.putStrLn("releasing").orDie)
val zUsedResource: URIO[Console, Unit] = zManagedResource.use { _ => console.putStrLn("running").orDie }
```

## Combining Managed

It is possible to combine multiple `Managed` using `flatMap` to obtain a single `Managed` that will acquire and release all the resources.

```scala
import zio._
```


```scala
val managedQueue: Managed[Nothing, Queue[Int]] = Managed.make(Queue.unbounded[Int])(_.shutdown)
val managedFile: Managed[IOException, File] = Managed.make(openFile("data.json"))(closeFile)

val combined: Managed[IOException, (Queue[Int], File)] = for {
    queue <- managedQueue
    file  <- managedFile
} yield (queue, file)

val usedCombinedRes: IO[IOException, Unit] = combined.use { case (queue, file) => doSomething(queue, file) }

```
