---
id: blocking 
title: "Blocking"
---

The **Blocking** service provides access to a thread pool that can be used for performing
blocking operations, such as thread sleeps, synchronous socket/file reads, and so forth. 

By default, ZIO is asynchronous and all effects will be executed on a default primary thread pool which is optimized for asynchronous operations. As ZIO uses a fiber-based concurrency model, if we run **Blocking I/O** or **CPU Work** workloads on a primary thread pool, they are going to monopolize all threads of **primary thread pool**.

In the following example, we create 20 blocking tasks to run parallel on the primary async thread pool. Assume we have a machine with an 8 CPU core, so the ZIO creates a thread pool of size 16 (2 * 8). If we run this program, all of our threads got stuck, and the remaining 4 blocking tasks (20 - 16) haven't any chance to run on our thread pool:

```scala mdoc:silent
import zio.{ZIO, URIO}
import zio.console._
def blockingTask(n: Int): URIO[Console, Unit] =
  putStrLn(s"running blocking task number $n").orDie *>
    ZIO.effectTotal(Thread.sleep(3000)) *>
    blockingTask(n)

val program = ZIO.foreachPar((1 to 100).toArray)(blockingTask)
```

ZIO has a separate **blocking thread pool** specially designed for **Blocking I/O** and, also **CPU Work** workloads. We should run blocking workloads on this thread pool to prevent interfering with the primary thread pool.

The contract is that the thread pool will accept unlimited tasks (up to the available memory)
and continuously create new threads as necessary.

The `blocking` operator takes a ZIO effect and return another effect that is going to run on a blocking thread pool:

```scala mdoc:invisible:nest
import zio.blocking._
val program = ZIO.foreachPar((1 to 100).toArray)(t => blocking(blockingTask(t)))
```

Also, we can directly imports a synchronous effect that does blocking IO into ZIO effect by using `effectBlocking`:

```scala mdoc:silent:nest
def blockingTask(n: Int) = effectBlocking {
  do {
    println(s"running blocking task number $n")
    Thread.sleep(3000) 
  } while (true)
}
```
