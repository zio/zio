---
id: supervisor
title: "Supervisor"
---

A `Supervisor[A]` is allowed to supervise the launching and termination of fibers, producing some visible value of type `A` from the supervision.

## Creation

### track
The `track` creates a new supervisor that tracks children in a set. It takes a boolean `weak` parameter as input, which indicates whether track children in a `Weakset` or not.


```scala mdoc:invisible
import zio.Supervisor
```

```scala mdoc
val supervisor = Supervisor.track(true)
```

We can periodically, report the status of the fibers of our program with the help of the Supervisor.

### fibersIn
The `fibersIn` creates a new supervisor with an initial sorted set of fibers.

In the following example we are creating a new supervisor from an initial set of fibers:

```scala mdoc:invisible
import zio.{Ref, Fiber}
import scala.collection.immutable.SortedSet
def fibers: Seq[Fiber.Runtime[Any, Any]] = ???
```

```scala mdoc
def fiberListSupervisor = for { 
  ref <- Ref.make(SortedSet.from(fibers))
  s <- Supervisor.fibersIn(ref)
} yield (s)
```

## Supervising

Whenever we need to supervise a ZIO effect, we can call `ZIO#supervised` function, `supervised` takes a supervisor and return another effect. The behavior of children fibers is reported to the provided supervisor:

```scala mdoc:invisible
import zio._
import zio.console._
import zio.clock._
import zio.duration._
def fib(n: Int): ZIO[Any, Nothing, Int] = ???
```

```scala mdoc:silent
val supervised = supervisor.flatMap(s => fib(20).supervised(s))
```

Now we can access all information of children fibers through the supervisor.

## Example
In the following example we are going to periodically monitor the number of fibers throughout our application life cycle:

```scala mdoc:silent
object SupervisorExample extends zio.App {
  import zio.duration._

  val program = for {
    supervisor <- Supervisor.track(true)
    fiber <- fib(20).supervised(supervisor).fork
    policy = Schedule
      .spaced(500.milliseconds)
      .whileInputM[Any, Unit](_ => fiber.status.map(x => !x.isDone))
    logger <- monitorFibers(supervisor)
      .repeat(policy).fork
    _ <- logger.join
    result <- fiber.join
    _ <- putStrLn(s"fibonacci result: $result")
  } yield ()

  def monitorFibers(supervisor: Supervisor[Chunk[Fiber.Runtime[Any, Any]]]) = for {
    length <- supervisor.value.map(_.length)
    _ <- putStrLn(s"number of fibers: $length")
  } yield ()

  def fib(n: Int): ZIO[Clock, Nothing, Int] =
    if (n <= 1) {
      ZIO.succeed(1)
    } else {
      for {
        _ <- sleep(500.milliseconds)
        fiber1 <- fib(n - 2).fork
        fiber2 <- fib(n - 1).fork
        v2 <- fiber2.join
        v1 <- fiber1.join
      } yield v1 + v2
    }

  override def run(args: List[String]) = program.exitCode
}
```
