---
id: resourceful-streams
title: "Resourceful Streams"
---

Most of the constructors of `ZStream` have a special variant to lift a scoped resource to a Stream (e.g. `ZStream.fromReaderScoped`). By using these constructors, we are creating streams that are resource-safe. Before creating a stream, they acquire the resource, and after usage; they close the stream.

ZIO Stream also has `acquireRelease` and `finalizer` constructors which are similar to `ZIO.acquireRelease`. They allow us to clean up or finalizing before the stream ends:

## Acquire Release

We can provide `acquire` and `release` actions to `ZStream.acquireReleaseWith` to create a resourceful stream:

```scala
object ZStream {
  def acquireReleaseWith[R, E, A](
    acquire: ZIO[R, E, A]
  )(
    release: A => URIO[R, Any]
  ): ZStream[R, E, A] = ???
```

Let's see an example of using an acquire release when reading a file. In this example, by providing `acquire` and `release` actions to `ZStream.acquireReleaseWith`, it gives us a scoped stream of `BufferedSource`. As this stream is scoped, we can convert that `BufferedSource` to a stream of its lines and then run it, without worrying about resource leakage:

```scala mdoc:invisible
import zio._
import zio.stream._
import zio.Cause.Die
import zio.Console._
import scala.io.Source
import zio.stm.{STM, TQueue}
import java.io.{BufferedReader, FileReader, FileInputStream, IOException}
import java.nio.file.{Files, Path, Paths}
import java.nio.file.Path._
import java.net.URL
import java.lang.IllegalArgumentException
import scala.concurrent.TimeoutException
```

```scala mdoc:silent:nest
val lines: ZStream[Any, Throwable, String] =
  ZStream
    .acquireReleaseWith(
      ZIO.attempt(Source.fromFile("file.txt")) <* printLine("The file was opened.")
    )(x => ZIO.succeed(x.close()) <* printLine("The file was closed.").orDie)
    .flatMap { is =>
      ZStream.fromIterator(is.getLines())
    }
```

## Finalization

We can also create a stream that never fails and define a finalizer for it, so that finalizer will be executed before that stream ends.

```scala
object ZStream {
  def finalizer[R](
    finalizer: URIO[R, Any]
  ): ZStream[R, Nothing, Any] = ???
}
```

It is useful when need to add a finalizer to an existing stream. Assume we need to clean up the temporary directory after our streaming application ends:

```scala mdoc:silent:nest
import zio.Console._

def application: ZStream[Any, IOException, Unit] = ZStream.fromZIO(printLine("Application Logic."))
def deleteDir(dir: Path): ZIO[Any, IOException, Unit] = printLine("Deleting file.")

val myApp: ZStream[Any, IOException, Any] =
  application ++ ZStream.finalizer(
    (deleteDir(Paths.get("tmp")) *>
      printLine("Temporary directory was deleted.")).orDie
  )
```

## Ensuring

We might want to run some code after the execution of the stream's finalization. To do so, we can use the `ZStream#ensuring` operator:

```scala mdoc:silent:nest
ZStream
  .finalizer(Console.printLine("Finalizing the stream").orDie)
  .ensuring(
    printLine("Doing some other works after stream's finalization").orDie
  )
  
// Output:
// Finalizing the stream
// Doing some other works after stream's finalization
```
