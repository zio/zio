---
id: overview_handling_resources
title:  "Handling Resources"
---

Ensuring that your applications never leak resources is one of the keys to maximizing application throughput, minimizing latency, and maximizing per-node uptime.

Yet, achieving resource safety in the presence of asynchronous operations, concurrency, and ZIO's interruption model (which will automatically cancel running effects anytime their results will no longer be used) is challenging.

In this section, you will learn a few of the tools that ZIO provides to create safe applications that never leak resources, even in the case of failure, interruption, or defects in your application.

```scala mdoc:invisible
import zio._
```

## Finalizing

In many languages, the `try` / `finally` construct provides a language-level way to guarantee that when the `try` code exits, either normally or abnormally, the _finalizer_ code in the `finally` block will be executed.

ZIO provides a version of this with the `ZIO#ensuring` method, whose guarantees hold across concurrent and async effects. ZIO goes one step further in automatically and losslessly aggregating errors from finalizers.

As with `try` / `finally`, the `ensuring` method guarantees if the effect it is called on begins executing and terminates (either normally or abnormally), then the finalizer will begin execution.

```scala mdoc
val finalizer: UIO[Unit] = 
  ZIO.succeed(println("Finalizing!"))

val finalized: IO[String, Unit] = 
  ZIO.fail("Failed!").ensuring(finalizer)
```

In ZIO, finalizers are not allowed to fail in any recoverable way, which means that you must handle all of the errors that your code can produce.

Like `try` / `finally`, finalizers can be nested, and the failure of any inner finalizer will not affect outer finalizers. Nested finalizers will be executed in reverse order and sequentially, with later finalizers executed only after earlier finalizers.

## Acquire Release 

A common use for `try` is safely acquiring and releasing resources, such as new socket connections or opened files:

```scala 
val handle = openFile(name)

try {
  processFile(handle)
} finally closeFile(handle)
```

ZIO encapsulates this common pattern with `ZIO.acquireReleaseWith`, which allows you to specify an _acquire_ effect, which acquires a resource; a _release function_, which returns an effect to release the resource; and a _use function_, which returns an effect that _uses_ the resource.

So long as the acquire effect succeeds, the release effect is guaranteed to be executed by the runtime system, even in the presence of errors or interruption.

```scala mdoc:invisible
import zio._
import java.io.{ File, IOException }

def openFile(s: String): IO[IOException, File] = ZIO.attempt(???).refineToOrDie[IOException]
def closeFile(f: File): UIO[Unit] = ZIO.unit
def decodeData(f: File): IO[IOException, Unit] = ZIO.unit
def groupData(u: Unit): IO[IOException, Unit] = ZIO.unit
```

```scala mdoc:silent
val groupedFileData: IO[IOException, Unit] = 
  ZIO.acquireReleaseWith(openFile("data.json"))(closeFile(_)) { file =>
    for {
      data    <- decodeData(file)
      grouped <- groupData(data)
    } yield grouped
  }
```

Like `ensuring`, `acquireReleaseWith` has compositional semantics, so if one `acquireReleaseWith` is nested inside another `acquireReleaseWith`, and the outer resource is acquired, then the outer release will always be called, even if, for example, the inner release fails.

For resources which implement the AutoClosable interface, the convenience method `fromAutoClosable` can be used, which can be seen as the ZIO equivalent of try-with-resource.

```scala mdoc:invisible
import zio._
import java.io.FileInputStream
def openFileInputStream(name: String): IO[Throwable, FileInputStream] = ZIO.attemptBlocking(new FileInputStream(name))
```

```scala mdoc:silent
val bytesInFile: IO[Throwable, Int] =
  ZIO.scoped {
    for {
      stream <- ZIO.fromAutoCloseable(openFileInputStream("data.json"))
      data   <- ZIO.attemptBlockingIO(stream.readAllBytes())
    } yield data.length
  }
```

## Next Steps

If you are comfortable with basic resource handling, the next step is to learn about [basic concurrency](basic_concurrency.md).
