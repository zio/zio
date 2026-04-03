---
id: global-shared-state
title: "Global Shared State Using Ref"
sidebar_label: "Global Shared State"
---

One of the common use cases for `Ref` is to manage the state of applications, especially in concurrent environments. We can use the `Ref` data type, which is a purely functional description of a mutable reference.

> **Note:**
>
> In this section, we will only cover the basic usage of the `Ref` data type. To learn more details about the `Ref`, especially its usage in concurrent programming, please refer to the [`Ref`](../concurrency/ref.md) page on the concurrency section.

In the previous page, we have learned how to use recursive functions to manage the state of our application. However, this approach has the following drawbacks:
- We cannot share the state between multiple fibers.
- Sometime, writing the application logic is a bit tedious. It is somehow awkward to pass the state using function parameters.

Thanks to the `Ref` data type, we can easily use the `Ref` data type to manage the state of our application, whether we need concurrency or not.

In the previous section, we learned that we can have state management, even for effectful operations. Here is the last example we tried:

```scala mdoc:compile-only
import zio._

def inputNames: ZIO[Any, String, List[String]] = {
  def loop(names: List[String]): ZIO[Any, String, List[String]] = {
    Console.readLine("Please enter a name or `q` to exit: ").orDie.flatMap {
      case "q" =>
        ZIO.succeed(names)
      case name =>
        loop(names appended name)
    }
  }

  loop(List.empty[String])
}
```

This code can be rewritten using the `Ref` type, which is simpler than the previous one:

```scala mdoc:compile-only
import zio._

def getNames: ZIO[Any, String, List[String]] =
  Ref.make(List.empty[String])
    .flatMap { ref =>
      Console
        .readLine("Please enter a name or 'q' to exit: ")
        .orDie
        .repeatWhileZIO {
          case "q" => ZIO.succeed(false)
          case name => ref.update(_ appended name).as(true)
        } *> ref.get
    }
```

First, we created a mutable reference to the initial state value, which is an empty list. Then, we read from the console repeatedly until the user enters the "q" command. Finally, we got the value of the reference and returned it.

> **Note:**
>
> All the operations on the `Ref` data type are effectful. So when we are reading from or writing to a `Ref`, we are performing an effectful operation.

Now that we have learned how to use the `Ref` data type, we can use it to manage the state concurrently. For example, assume while we are reading from the console, we have another fiber that is trying to update the state from a different source:

```scala mdoc:compile-only
import zio._

def getNames: ZIO[Any, String, List[String]] =
  for {
    ref <- Ref.make(List.empty[String])
    f1 <- Console
      .readLine("Please enter a name or 'q' to exit: ")
      .orDie
      .repeatWhileZIO {
        case "q"  => ZIO.succeed(false)
        case name => ref.update(_ appended name).as(true)
      }.fork 
      f2 <- ZIO.foreachDiscard(Seq("John", "Jane", "Joe", "Tom")) { name =>
        ref.update(_ appended name) *> ZIO.sleep(1.second)
      }
      .fork
    _ <- f1.join
    _ <- f2.join
    v <- ref.get
  } yield v
```

## Counter Example

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
      c <- Counter.make
      _ <- c.inc
      _ <- c.inc
      _ <- c.dec
      _ <- c.inc
      v <- c.get
      _ <- ZIO.debug(s"This counter has a value of $v.")
    } yield ()
}
```

We can use this counter in a concurrent environment, e.g. in a RESTful API to count the number of requests. But for just an example, let's concurrently update the counter:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    for {
      c <- Counter.make
      _ <- c.inc <&> c.inc <&> c.dec <&> c.inc
      v <- c.get
      _ <- ZIO.debug(s"This counter has a value of $v.")
    } yield ()
}
```
