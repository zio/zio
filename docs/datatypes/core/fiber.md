---
id: fiber
title: "Fiber"
---

To perform an effect without blocking the current process, we can use fibers, which are a lightweight concurrency mechanism.

We can `fork` any `IO[E, A]` to immediately yield an `UIO[Fiber[E, A]]`. The provided `Fiber` can be used to `join` the fiber, which will resume on production of the fiber's value, or to `interrupt` the fiber, which immediately terminates the fiber and safely releases all resources acquired by the fiber.

```scala mdoc:silent
import zio._
```
```scala mdoc:invisible
sealed trait Analysis
case object Analyzed extends Analysis

val data: String = "tut"

def analyzeData[A](data: A): UIO[Analysis] = IO.succeed(Analyzed)
def validateData[A](data: A): UIO[Boolean] = IO.succeed(true)
```

```scala mdoc:silent
val analyzed =
  for {
    fiber1   <- analyzeData(data).fork  // IO[E, Analysis]
    fiber2   <- validateData(data).fork // IO[E, Boolean]
    // Do other stuff
    valid    <- fiber2.join
    _        <- if (!valid) fiber1.interrupt
                else IO.unit
    analyzed <- fiber1.join
  } yield analyzed
```

A Fiber can be thought of as a virtual thread. A Fiber is the analog of a Java thread (java.lang.thread), but it performs much better. Fibers, on the other hand, are implemented in such a fashion, that a single JVM thread will end up executing many fibers. We can think of fibers as unbounded JVM threads.

> _**Warning**, if you are not an advanced programmer:_
>
>You should avoid fibers. If you can avoid fibers, then do it. ZIO gives you many concurrent primitives like `raceWith`, `zipPar`, `foreachPar`, and so forth, which allows you to avoid using fibers directly.
>
> Fibers just like threads are low-level constructs. It's not generally recommended for an average programmer to manually use fibers. It is very easy to make lots of mistakes or to introduce performance problems by manually using them.

## Why Fibers?
There are some limitations with JVM threads. 
- **They are Scarce** Threads on the JVM map to the operating system level threads which imposes an upper bound on the number of threads that we can have inside our application.
- **Expensive on Creation** Their creation is expensive in terms of time and memory complexity.
- **Much Overhead on Context Switching** Switching between the execution of one thread to another thread is not cheap, and it takes a lot of time.
- **Lack of Composability** Threads are not typed. They don't have a meaningful return type, due to this limitation, we cannot compose threads. Also, it has no type parameters for error and it is assumed if that thread started it might throw some exception of throwable type. In Java when we create a thread, we should provide a `run` function that returns void. It's a void returning method. So threads cannot finish with any specific value.
- **Synchronous**

In the following sections, we are going to discuss the main features of fibers, and how fiber overcame Java thread drawbacks.

### Unbounded Size
So whereas the mapping from JVM threads to operating system threads is one-to-one, the mapping of fiber to a thread is many-to-one. That is to say, any JVM thread will end up executing anywhere from hundreds to thousands even tens of thousands of threads concurrently, by hopping back and forth between them as necessary. They give us virtual threads in which it has the benefits of threads but the scalability way beyond threads. In other words, fibers provide us massive concurrently with lightweight green threading on the JVM.

As a rough rule of thumb, we can have an application with a thousand real threads. No problem, modern servers can support applications with a thousand threads. However, we cannot have an application with a hundred thousand threads, that application will die. That just won't make any progress. The JVM nor our operating system can physically support a hundred thousand threads. However, it is no problem to have a Scala application with a hundred thousand fibers that application can perform in a very high-performance fashion, and the miracle that enables that to happen is fiber.

### Lightweight
JVM threads are expensive to create in order of time and memory complexity, also it takes a lot of time to switch between one thread of execution to another. Fibers are virtual and, and as they use green threading, they are considered to be lightweight cooperative threads, this means that fibers always yield their executions to each other without the overhead of preemptive scheduling.

### Asynchronous
Fiber is asynchronous and, a thread is always synchronous. That is why fibers have higher scalability because they are asynchronous. Threads are not, that is why they don't scale as well.

### Typed and Composable
Fibers have typed error and success values. So actually fiber has two type parameters `E` and `A`:
- The `E` corresponds to the error channel. It indicates the error type with which the fiber can fail. 
- The `A` corresponds to the success value of the computation. That is the type with which the fiber can succeed. Whereas fibers can finish with the value of type `A`.

The fact, that fibers are typed allows us to write more type-safe programs. Also, it increases the compositional properties of our programs. Because we can say, we are going to wait on that fiber to finish and when it's done, we are going to get its value of type `A`.

### Interrupt Safe
With threads in Java, it is not a safe operation to terminate them, by using the stop method. The stop operation has been [deprecated](https://docs.oracle.com/javase/1.5.0/docs/guide/misc/threadPrimitiveDeprecation.html). So this is not a safe operation to force kill a thread. Instead, we should try to request an interruption to the thread, but in this case, the thread may not respond to our request, and it may just go forever.

Fiber has a safe version of this functionality that works very well. Just like we can interrupt a thread, we can interrupt a fiber too, but interruption of fibers is much more reliable. It will always work, and it probably works very fast. We don't need to wait around, we can just try to interrupt them, and they will be gone very soon.

### Structured Concurrency
Until now, we find that ZIO Fiber solves a lot of drawbacks of using Java threads. With fibers, we can have hundreds of thousands and even thousands of thousands of fibers are started and working together. We reached a very massive concurrently with fibers. Now how can we manage them? Some of them are top-level fibers and some others are forked and become children of their parents. How can we manage their scopes, how to keep track of all fibers, and prevent them to leak? What happens during the execution of a child fiber, the parent execution interrupted? The child fibers should be scoped to their parent fibers. We need a way to manage these scopes automatically. This is where structured concurrency shines.

> _**Important**:_
>
> It's worth mentioning that in the ZIO model, all codes run on the fiber. There is no such thing as code that is executing outside of the fiber. When we create a main function in ZIO that returns an effect, even if we don't explicitly fork a fiber when we execute that effect, that effect will execute on what is called the main fiber. It's a top-level fiber.
>
>It's just like if we have a main function in Java then that main function will execute on the main thread. There is no code in Java that does not execute on a thread. All code executes on a thread even if you didn't create a thread. 

ZIO has support for structured concurrency. The way ZIO structured concurrency works is that the child fibers are scoped to their parent fibers which means when the parent effect is done running then its child's effects will be automatically interrupted. So when we fork, and we get back a fiber, the fiber's lifetime is bound to the parent fiber, that forked it. It is very difficult to leak fibers because child fibers are guaranteed to complete before their parents.

The structure concurrency gives us a way to reason about fiber lifespans. We can statically reason about the lifetimes of children fibers just by looking at our code. We don't need to insert complicated logic to keep track of all the child fibers and manually shut them down.


#### Global Lifetime
Sometimes we want a child fiber to outlive the scope of the parent. what do you do in that case? well, we have another operator called `forkDaemon`. The `forkDaemon` forks the fiber as a daemon fiber. Daemon fibers can outlive their parents. They can live forever. They run in the background doing their work until they end with failure or success. This gives us a way to spawn background jobs that should just keep on going regardless of what happens to the parent.

#### Fine-grained Scope
If we need a very flexible fine-grained control over the lifetime of a fiber there is another operator called `forkin`. We can fork a fiber inside a specific scope, when that scope is closed then the fiber will be terminated. 

## Operations

### fork and join
Whenever we need to start a fiber, we have to `fork` an effect, it gives us a fiber. It is similar to the `start` method on Java thread or submitting a new thread to the thread pool in Java, it is the same idea. Also, joining is a way of waiting for that fiber to compute its value. We are going to wait until it's done.

In the following example, we are going to run sleep and printing on a separate fiber and at the end, waiting for that fiber to compute its value:

```scala mdoc:silent
import zio._
import zio.console._
import zio.clock._
import zio.duration._
for {
  fiber <- (sleep(3.seconds) *>
    putStrLn("Hello, after 3 second") *>
    ZIO.succeed(10)).fork
  _ <- putStrLn(s"Hello, World!")
  res <- fiber.join
  _ <- putStrLn(s"Our fiber succeeded with $res")
} yield ()
```

### fork0
A more powerful variant of `fork`, called `fork0`, allows specification of supervisor that will be passed any non-recoverable errors from the forked fiber, including all such errors that occur in finalizers. If this supervisor is not specified, then the supervisor of the parent fiber will be used, recursively, up to the root handler, which can be specified in `Runtime` (the default supervisor merely prints the stack trace).

### interrupt
Whenever we want to get rid of our fiber, we can simply call interrupt on that. The interrupt operation does not resume until the fiber has completed or has been interrupted and all its finalizers have been run. These precise semantics allow construction of programs that do not leak resources.

### await
To inspect whether our fiber succeeded or failed, we can call `await` on fiber. if we call `await` it will wait for that fiber to terminate, and it will give us back the fiber's value as an `Exit`. That exit value could be failure or success. 

```scala mdoc:silent
import zio.console._
import zio.random._
for {
  b <- nextBoolean
  fiber <- (if (b) ZIO.succeed(10) else ZIO.fail("The boolean was not true")).fork
  exitValue <- fiber.await
  _ <- exitValue match {
    case Exit.Success(value) => putStrLn(s"Fiber succeeded with $value")
    case Exit.Failure(cause) => putStrLn(s"Fiber failed")
  }
} yield ()
```

The `await` is similar to join but lower level than join. When we call join if the underlying fiber failed then our attempt to join it will also fail with the same error.  

### Parallelism
To execute actions in parallel, the `zipPar` method can be used:

```scala mdoc:invisible
case class Matrix()
def computeInverse(m: Matrix): UIO[Matrix] = IO.succeed(m)
def applyMatrices(m1: Matrix, m2: Matrix, m3: Matrix): UIO[Matrix] = IO.succeed(m1)
```

```scala mdoc:silent
def bigCompute(m1: Matrix, m2: Matrix, v: Matrix): UIO[Matrix] =
  for {
    t <- computeInverse(m1).zipPar(computeInverse(m2))
    (i1, i2) = t
    r <- applyMatrices(i1, i2, v)
  } yield r
```

The `zipPar` combinator has resource-safe semantics. If one computation fails, the other computation will be interrupted, to prevent wasting resources.

### Racing

Two `IO` actions can be *raced*, which means they will be executed in parallel, and the value of the first action that completes successfully will be returned.

```scala mdoc:silent
fib(100) race fib(200)
```

The `race` combinator is resource-safe, which means that if one of the two actions returns a value, the other one will be interrupted, to prevent wasting resources.

The `race` and even `zipPar` combinators are a specialization of a much-more powerful combinator called `raceWith`, which allows executing user-defined logic when the first of two actions succeeds.

On the JVM, fibers will use threads, but will not consume *unlimited* threads. Instead, fibers yield cooperatively during periods of high-contention.

```scala mdoc:silent
def fib(n: Int): UIO[Int] =
  if (n <= 1) {
    IO.succeed(1)
  } else {
    for {
      fiber1 <- fib(n - 2).fork
      fiber2 <- fib(n - 1).fork
      v2     <- fiber2.join
      v1     <- fiber1.join
    } yield v1 + v2
  }
```

## Error Model
The `IO` error model is simple, consistent, permits both typed errors and termination, and does not violate any laws in the `Functor` hierarchy.

An `IO[E, A]` value may only raise errors of type `E`. These errors are recoverable by using the `either` method.  The resulting effect cannot fail, because the failure case bas been exposed as part of the `Either` success case.  

```scala mdoc:silent
val error: Task[String] = IO.fail(new RuntimeException("Some Error"))
val errorEither: ZIO[Any, Nothing, Either[Throwable, String]] = error.either
```

Separately from errors of type `E`, a fiber may be terminated for the following reasons:

 * The fiber self-terminated or was interrupted by another fiber. The "main" fiber cannot be interrupted because it was not forked from any other fiber.
 * The fiber failed to handle some error of type `E`. This can happen only when an `IO.fail` is not handled. For values of type `UIO[A]`, this type of failure is impossible.
 * The fiber has a defect that leads to a non-recoverable error. There are only two ways this can happen:
     1. A partial function is passed to a higher-order function such as `map` or `flatMap`. For example, `io.map(_ => throw e)`, or `io.flatMap(a => throw e)`. The solution to this problem is to not to pass impure functions to purely functional libraries like ZIO, because doing so leads to violations of laws and destruction of equational reasoning.
     2. Error-throwing code was embedded into some value via `IO.effectTotal`, etc. For importing partial effects into `IO`, the proper solution is to use a method such as `IO.effect`, which safely translates exceptions into values.

When a fiber is terminated, the reason for the termination, expressed as a `Throwable`, is passed to the fiber's supervisor, which may choose to log, print the stack trace, restart the fiber, or perform some other action appropriate to the context.

A fiber cannot stop its own interruption. However, all finalizers will be run during termination, even when some finalizers throw non-recoverable errors. Errors thrown by finalizers are passed to the fiber's supervisor.

There are no circumstances in which any errors will be "lost", which makes the `IO` error model more diagnostic-friendly than the `try`/`catch`/`finally` construct that is baked into both Scala and Java, which can easily lose errors.

## Thread Shifting - JVM
By default, fibers make no guarantees as to which thread they execute on. They may shift between threads, especially as they execute for long periods of time.

Fibers only ever shift onto the thread pool of the runtime system, which means that by default, fibers running for a sufficiently long time will always return to the runtime system's thread pool, even when their (asynchronous) resumptions were initiated from other threads.

For performance reasons, fibers will attempt to execute on the same thread for a (configurable) minimum period, before yielding to other fibers. Fibers that resume from asynchronous callbacks will resume on the initiating thread, and continue for some time before yielding and resuming on the runtime thread pool.

These defaults help guarantee stack safety and cooperative multitasking. They can be changed in `Runtime` if automatic thread shifting is not desired.
