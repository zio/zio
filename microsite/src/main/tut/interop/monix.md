---
layout: docs
section: interop
title:  "Monix"
---

# {{page.title}}

Checkout `interop-monix` module for inter-operation support.

## `Task` conversions

Interop layer provides the following conversions:

- from `IO[Throwable, A]` to `IO[Nothing, Task[A]]`
- from `Task[A]` to `IO[Throwable, A]`

To convert an `IO` value to `Task`, use the following method:

```scala
def toTask: IO[Nothing, eval.Task[A]]
```

To perform conversion in other direction, use the following extension method
available on `IO` companion object:

```scala
def fromTask[A](task: eval.Task[A])(implicit scheduler: Scheduler): IO[Throwable, A]
```

Note that in order to convert the `Task` to an `IO`, an appropriate `Scheduler`
needs to be available.

### Example

```scala
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import scalaz.zio.{ IO, RTS }
import scalaz.zio.interop.monixio._

object UnsafeExample extends RTS {
  def main(args: Array[String]): Unit = {
    val io1 = IO.now(10)
    val t1  = unsafeRun(io1.toTask)

    t1.runToFuture.foreach(r => println(s"IO to task result is $r"))

    val t2  = Task(10)
    val io2 = IO.fromTask(t2).map(r => s"Task to IO result is $r")

    println(unsafeRun(io2))
  }
}
```

## `Coeval` conversions

To convert an `IO` value to `Coeval`, use the following method:

```scala
def toCoeval: IO[Nothing, eval.Coeval[A]]
```

To perform conversion in other direction, use the following extension method
available on `IO` companion object:

```scala
def fromCoeval[A](coeval: eval.Coeval[A]): IO[Throwable, A]
```

### Example

```scala
import monix.eval.Coeval
import scalaz.zio.{ IO, RTS }
import scalaz.zio.interop.monixio._

object UnsafeExample extends RTS {
  def main(args: Array[String]): Unit = {
    val io1 = IO.now(10)
    val c1  = unsafeRun(io1.toCoeval) 

    println(s"IO to coeval result is ${c1.value}")

    val c2  = Coeval(10)
    val io2 = IO.fromCoeval(c2).map(r => s"Coeval to IO result is $r")

    println(unsafeRun(io2))
  }
}
```
