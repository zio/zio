---
id: index
title: "Introduction to ZIO's Interruption Model"
sidebar_label: Interruption Model
---


## Interruption

While developing concurrent applications, there are several cases that we need to _interrupt_ the execution of other fibers, for example:

1. A parent fiber might start some child fibers to perform a task, and later the parent might decide that, it doesn't need the result of some or all of the child fibers.
2. Two or more fibers start race with each other. The fiber whose result is computed first wins, and all other fibers are no longer needed, and should be interrupted.
3. In interactive applications, a user may want to stop some already running tasks, such as clicking on the "stop" button to prevent downloading more files.
4. Computations that run longer than expected should be aborted by using timeout operations.
5. When we have an application that perform compute-intensive tasks based on the user inputs, if the user changes the input we should cancel the current task and perform another one.

A simple and naive way to implement fiber interruption is to provide a mechanism that one fiber can _kill/terminate_ another fiber. This is not a correct solution, because if the target fiber is in the middle of changing a shared state, it leads to an inconsistent state. So this solution doesn't guarantee to leave the shared mutable state in an internally consistent state.

Other than the very simple kill solution, there are two popular valid solutions to this problem:

1. **Semi-asynchronous Interruption (Polling for Interruption)**— Imperative languages often use polling to implement a semi-asynchronous signaling mechanism, such as Java. In this model, a fiber sends a request for interruption of other fiber. The target fiber keep polling the interrupt status, and based on the interrupt status will find out that weather there is an interruption request from other fibers received or not. Then it should terminate itself as soon as possible.

Using this solution, the fiber itself takes care of critical sections. So while a fiber is in the middle of a critical section, if it receives an interruption request, it should postpone the termination until leaving the critical section.

The drawback of this solution is that, if the programmer forget to poll regularly enough, then the target fiber become unresponsive and cause deadlocks. Another problem with this solution is that polling a global flag is not a functional operation, that doesn't fit with ZIO's paradigm.

3. **Asynchronous Interruption**— In asynchronous interruption a fiber allows to terminate another fiber. So the target fiber is not responsible for polling the status, instead in critical sections the target fiber postpone the interruptibility these regions. This is a purely-functional solution and doesn't require to poll a global state. ZIO uses this solution for its interruption model. It is a fully asynchronous signalling mechanism.

This mechanism doesn't have the drawback of forgetting to poll regularly. And also its fully compatible with functional paradigm because in a purely-functional computation, at any point we can abort the computation, (except for critical sections that should be postponed).

### When Does a Fiber Get Interrupted?

There are several ways and situations that fibers can be interrupted. In this section we will introduce each one with an example of how to reproduce these situations:

1. A fiber can be interrupted by calling `Fiber#interrupt` on that fiber.

Let's try to make a fiber and then interrupt it:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def task = {
    for {
      fn <- ZIO.fiberId.map(_.threadName)
      _ <- ZIO.debug(s"$fn starts a long running task")
      _ <- ZIO.sleep(1.minute)
      _ <- ZIO.debug("done!")
    } yield ()
  }

  def run =
    for {
      f <-
        task.onInterrupt(
          ZIO.debug(s"Task interrupted while running")
        ).fork
      _ <- f.interrupt
    } yield ()
}
```

Here is the output of running this peace of code, which denotes that the task was interrupted:

```
Task interrupted while running
```

2. When composing multiple parallel effects, when one of them interrupted, other fibers will be interrupted. So if we have two parallel tasks, if one of them failed or interrupted, another will be interrupted:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  def debugInterruption(taskName: String) = (fibers: Set[FiberId]) =>
    for {
      fn <- ZIO.fiberId.map(_.threadName)
      _ <- ZIO.debug(
        s"The $fn fiber which is the underlying fiber of the $taskName task " +
          s"interrupted by ${fibers.map(_.threadName).mkString(", ")}"
      )
    } yield ()

  def task[R, E, A](name: String)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    zio.onInterrupt(debugInterruption(name))

  def debugMainFiber =
    for {
      fn <- ZIO.fiberId.map(_.threadName)
      _ <- ZIO.debug(s"Main fiber ($fn) starts executing the whole application.")
    } yield ()

  def run = {
    // self interrupting fiber 
    val first = task("first")(ZIO.interrupt)

    // never ending fiber
    val second = task("second")(ZIO.never)

    debugMainFiber *> {
      // uncomment each line and run the code to see the result

      // first fiber will be interrupted 
      first *> second

      // never ending application
      // second *> first

      // first fiber will be interrupted
      // first <*> second

      // never ending application
      // second <*> first

      // first and second will be interrupted
      // first <&> second

      // first and second will be interrupted 
      // second <&> first
    }
  }

}
```

In the above code the `first <&> second` is a parallel composition of two `first` and `second` tasks. so when we run them together, the `zipwithpar`/`<&>` operator will run these two tasks in two parallel fibers. if either side of this operator fails or is interrupted the other side will be interrupted.

3. Child fibers are scoped to their parents:

    - If a child fiber does not complete its job or does not join its parent before the parent has completed its job, the child fiber will be interrupted:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def debugInterruption(taskName: String) = (fibers: Set[FiberId]) =>
    for {
      fn <- ZIO.fiberId.map(_.threadName)
      _ <- ZIO.debug(
        s"the $fn fiber which is the underlying fiber of the $taskName task " +
          s"interrupted by ${fibers.map(_.threadName).mkString(", ")}"
      )
    } yield ()

  def run =
    for {
      fn <- ZIO.fiberId.map(_.threadName)
      _ <- ZIO.debug(s"$fn starts working.")
      child =
        for {
          cfn <- ZIO.fiberId.map(_.threadName)
          _ <- ZIO.debug(s"$cfn starts working by forking from its parent ($fn)")
          _ <- ZIO.never
        } yield ()
      _ <- child.onInterrupt(debugInterruption("child")).fork
      _ <- ZIO.sleep(1.second)
      _ <- ZIO.debug(s"$fn finishes its job and is going go exit.")
    } yield ()
    
}
```

Here is the result of one of the executions of this sample code:

```
zio-fiber-2 starts working.
zio-fiber-7 starts working by forking from its parent (zio-fiber-2)
zio-fiber-2 finishes its job and is going to exit.
the zio-fiber-7 fiber which is the underlying fiber of the child task interrupted by zio-fiber-2
```

- If a parent fiber is interrupted, all its children will be interrupted:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  def debugInterruption(taskName: String) = (fibers: Set[FiberId]) =>
    for {
      fn <- ZIO.fiberId.map(_.threadName)
      _ <- ZIO.debug(
        s"The $fn fiber which is the underlying fiber of the $taskName task " +
          s"interrupted by ${fibers.map(_.threadName).mkString(", ")}"
      )
    } yield ()

  def task =
    for {
      fn <- ZIO.fiberId.map(_.threadName)
      _ <- ZIO.debug(s"$fn starts running that will print random numbers and booleans")
      f1 <- Random.nextIntBounded(100)
        .debug("random number ")
        .schedule(Schedule.spaced(1.second).forever)
        .onInterrupt(debugInterruption("random number"))
        .fork
      f2 <- Random.nextBoolean
        .debug("random boolean ")
        .schedule(Schedule.spaced(2.second).forever)
        .onInterrupt(debugInterruption("random boolean"))
        .fork
        _ <- f1.join
        _ <- f2.join
    } yield ()

  def run =
    for {
      f <- task.fork
      _ <- ZIO.sleep(5.second)
      _ <- f.interrupt
    } yield ()
}
```

Here is one sample output for this program:

```
zio-fiber-7 starts running that will print random numbers and booleans
random number : 65
random boolean : true
random number : 51
random number : 46
random boolean : true
random number : 30
The zio-fiber-9 fiber which is the underlying fiber of the random boolean task interrupted by zio-fiber-7
The zio-fiber-8 fiber which is the underlying fiber of the random number task interrupted by zio-fiber-7
```

### Postponing Interruption of Fibers

As we discussed earlier, it is dangerous for fibers to interrupt others. The danger with such an interruption is that:
- If the interruption occurs during the execution of an operation that must be _finalized_, the finalization will not be executed.
- If this interruption occurs in the middle of a _critical section_, it will cause an application state to become inconsistent.
- It is also a threat to _resource safety_. If the fiber is in the middle of acquiring a resource and is interrupted, the application will leak resources.

In the following code, we have a _stateful_ application. The state is a set of names. We have a function that gets names from the user and adds them to the state. Finally, we have a function that persists all names in the state to the database:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  type State = Set[String]

  def run =
    for {
      state <- Ref.make[State](Set.empty)
      _ <- getNames(state)
      _ <- persistNames(state)
    } yield ()

  def getNames(state: Ref[State]) =
    for {
      _ <- ZIO.debug(s"stateful app started with $state")
      _ <- Console.printLine("please enter 5 names:")
      task = (i: Int) => for {
        name <- Console.readLine(s"$i. ")
        _ <- state.update(_ + name)
      } yield ()
      _ <- ZIO.foreach(1 to 5)(task)
    } yield ()

  def persistNames(state: Ref[State]) =
    for {
      _ <- ZIO.debug(s"persist started with $state")
      names <- state.get
      _ <- ZIO.foreach(names)(name => ZIO.debug(s"persist $name"))
    } yield ()

}
```

So what happens if the `getNames` function is interrupted after the user entered a name and before it is added to the state? We will miss the name that the user entered. So we might need to retry the input operation and ask the user to enter the name again. But this is not user-friendly.

Instead, we can fix this by postponing the interruption of the `getNames` function by using the `uninterruptible` combinator:

```scala mdoc:compile-only
import zio._

type State = Set[String]

def getNames(state: Ref[State]) =
  for {
    _ <- ZIO.debug(s"stateful app started with $state")
    _ <- Console.printLine("please enter 5 names:")
    task = (i: Int) => for {
      _ <- ZIO.uninterruptible(
        for {
          name <- Console.readLine(s"$i. ") 
          _ <- state.update(_ + name)
        } yield ()
      )
    } yield ()
    _ <- ZIO.foreach(1 to 5)(task)
  } yield ()
```

Using this approach, we can avoid the problem of missing a name that the user entered. In this way, if in between the line `Console.readLine` and the line `state.update(_ + name)` the interruption occurs, the interruption will be postponed until the line `state.update(_ + name)` is executed. So we don't miss the name that the user entered.

But this approach causes another problem. The `Console.readLine` is a blocking operation (semantic blocking). So if the interruption occurs while the `Console.readLine` is executing, the interruption gets stuck and the application will block indefinitely.

To reproduce this scenario, we can use the `timeout` combinator to limit the time of the `getNames` function:

```scala
for {
  state <- Ref.make[State](Set.empty)
  _ <- getNames(state).timeout(10.seconds)
  _ <- persistNames(state)
} yield ()
```

By timing out the `getNames`, if the user doesn't enter all 5 names in 10 seconds the `Console.readLine` cannot be interrupted and the application will block indefinitely.

So we when using `uninterruptible` we should care about blocking operations, turning any blocking operation to uninterruptible is dangerous and makes our application unresponsive.

So, let's try a better approach. We can make sure that the blocking operation can be interrupted by using the `interruptible` combinator. By using this combinator, we can turn an uninterruptible operation into an interruptible operation inside an uninterruptible block:

```scala
// effect1 is inside uninterruptible region
// effect2 is inside interruptible region
// effect3 is inside uninterruptible region
ZIO.uninterruptible(
  effect1 *>
   ZIO.interruptible(effect2) *> 
   effect3
)
```

So we can say that `ZIO.interruptible` combinator, makes a hole inside an uninterruptible region. So if an interruption occurs inside the hole, that interruption will be executed immediately.

```scala mdoc:compile-only
import zio._

type State = Set[String]

def getNames(state: Ref[State]) =
  for {
    _ <- ZIO.debug(s"stateful app started with an initial state")
    _ <- Console.printLine("please enter 5 names:")
    task = (i: Int) => for {
      _ <- Console.print(s"$i. ")
      _ <- ZIO.uninterruptible(
        for {
          name <- ZIO.interruptible(Console.readLine)
          _ <- state.update(_ + name)
        } yield ()
      )
    } yield ()
    _ <- ZIO.foreach(1 to 5)(task)
  } yield ()
```
