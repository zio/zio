---
id: ref 
title: "Ref"
---

We can store the state of our application using the `Ref` data type.

> **Note:**
> 
> In this section, we will only cover the basic usage of the `Ref` data type. To learn more details about the `Ref`, especially its usage in concurrent programming, please refer to the [`Ref`](../concurrency/ref.md) page on the concurrency section.

Let's write a counter using the `Ref` data type:

```scala mdoc:silent
import zio._

case class Counter(value: Ref[Int]) {
  def inc: UIO[Unit] = value.update(_ + 1)
  def dec: UIO[Unit] = value.update(_ - 1)
  def get: UIO[Int] = value.get
}

object Counter {
  def make: UIO[Counter] = Ref.make(0).map(Counter(_))
}
```

Here is the usage example of the `Counter`:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    for {
      counter <- Counter.make
      _ <- counter.inc
      _ <- counter.inc
      _ <- counter.dec
      _ <- counter.inc
      value <- counter.get
      _ <- ZIO.debug(s"This counter has a value of $value.")
    } yield ()
}
```
