---
id: creating-zio-streams
title: "Creating ZIO Streams"
---

There are several ways to create ZIO Stream. In this section, we are going to enumerate some of the important ways of creating `ZStream`.

```scala mdoc:invisible
import zio._
import zio.stream._
import zio.Cause.Die
import zio.Console._
import zio.stm.{STM, TQueue}
import java.io.{BufferedReader, FileReader, FileInputStream, IOException}
import java.nio.file.{Files, Path, Paths}
import java.nio.file.Path._
import java.net.URL
import java.lang.IllegalArgumentException
import scala.concurrent.TimeoutException
```

## Common Constructors

**ZStream.apply** — Creates a pure stream from a variable list of values:

```scala mdoc:silent:nest
val stream: ZStream[Any, Nothing, Int] = ZStream(1, 2, 3)
```

**ZStream.unit** — A stream that contains a single `Unit` value:

```scala mdoc:silent:nest
val unit: ZStream[Any, Nothing, Unit] = ZStream.unit
```

**ZStream.never** — A stream that produces no value or fails with an error:

```scala mdoc:silent:nest
val never: ZStream[Any, Nothing, Nothing] = ZStream.never
```

**ZStream.repeat** — A stream that repeats using the specified schedule:
```scala mdoc:silent:nest
val repeat: ZStream[Any, Nothing, Int] = 
  ZStream(1).repeat(Schedule.forever)
```

**ZStream.iterate** — Takes an initial value and applies the given function to the initial value iteratively. The initial value is the first value produced by the stream, followed by f(init), f(f(init)), ...

```scala mdoc:silent:nest
val nats: ZStream[Any, Nothing, Int] = 
  ZStream.iterate(1)(_ + 1) // 1, 2, 3, ...
```

**ZStream.range** — A stream from a range of integers `[min, max)`:

```scala mdoc:silent:nest
val range: ZStream[Any, Nothing, Int] = ZStream.range(1, 5) // 1, 2, 3, 4
```

**ZStream.service[R]** — Create a stream that extract the requested service from the environment:

```scala mdoc:compile-only
trait Foo

val fooStream: ZStream[Foo, Nothing, Foo] = ZStream.service[Foo]
```

**ZStream.scoped** — Creates a single-valued stream from a scoped resource:

```scala mdoc:silent:nest
val scopedStream: ZStream[Any, Throwable, BufferedReader] =
  ZStream.scoped(
    ZIO.fromAutoCloseable(
      ZIO.attemptBlocking(
        Files.newBufferedReader(java.nio.file.Paths.get("file.txt"))
      )
    )
  )
```

## From Success and Failure

Similar to `ZIO` data type, we can create a `ZStream` using `fail` and `succeed` methods:

```scala mdoc:silent:nest
val s1: ZStream[Any, String, Nothing] = ZStream.fail("Uh oh!")
val s2: ZStream[Any, Nothing, Int]    = ZStream.succeed(5)
```

## From Chunks

We can create a stream from a `Chunk`:

```scala mdoc:nest
val s1 = ZStream.fromChunk(Chunk(1, 2, 3))
```

Or from multiple `Chunks`:

```scala mdoc:nest
val s2 = ZStream.fromChunks(Chunk(1, 2, 3), Chunk(4, 5, 6))
```

## From ZIO

**ZStream.fromZIO** — We can create a stream from a ZIO workflow by using `ZStream.fromZIO` constructor. For example, the following stream is a stream that reads a line from a user:

```scala mdoc:silent:nest
val readline: ZStream[Any, IOException, String] = 
  ZStream.fromZIO(Console.readLine)
```

A stream that produces one random number:

```scala mdoc:silent:nest
val randomInt: ZStream[Any, Nothing, Int] = 
  ZStream.fromZIO(Random.nextInt)
```

**ZStream.fromZIOOption** — In some cases, depending on the result of the ZIO workflow, we should decide to emit an element or return an empty stream. In these cases, we can use `fromZIOOption` constructor:

```scala 
object ZStream {
  def fromZIOOption[R, E, A](fa: ZIO[R, Option[E], A]): ZStream[R, E, A] = ???
}
```

Let's see an example of using this constructor. In this example, we read a string from user input, and then decide to emit that or not; If the user enters an `EOF` string, we emit an empty stream, otherwise we emit the user input:

```scala mdoc:silent:nest
val userInput: ZStream[Any, IOException, String] = 
  ZStream.fromZIOOption(
    Console.readLine.mapError(Option(_)).flatMap {
      case "EOF" => ZIO.fail[Option[IOException]](None)
      case o     => ZIO.succeed(o)
    }
  ) 
```

## From Asynchronous Callback

Assume we have an asynchronous function that is based on callbacks. We would like to register a callbacks on that function and get back a stream of the results emitted by those callbacks. We have `ZStream.async` which can adapt functions that call their callbacks multiple times and emit the results over a stream:

```scala mdoc:silent:nest
// Asynchronous Callback-based API
def registerCallback(
    name: String,
    onEvent: Int => Unit,
    onError: Throwable => Unit
): Unit = ???

// Lifting an Asynchronous API to ZStream
val stream = ZStream.async[Any, Throwable, Int] { cb =>
  registerCallback(
    "foo",
    event => cb(ZIO.succeed(Chunk(event))),
    error => cb(ZIO.fail(error).mapError(Some(_)))
  )
}
```

The error type of the `register` function is optional, so by setting the error to the `None` we can use it to signal the end of the stream.

## From Iterators

Iterators are data structures that allow us to iterate over a sequence of elements. Similarly, we can think of ZIO Streams as effectual Iterators; every `ZStream` represents a collection of one or more, but effectful values.

**ZStream.fromIteratorSucceed** — We can convert an iterator that does not throw exception to `ZStream` by using `ZStream.fromIteratorSucceed`:

```scala mdoc:silent:nest
val s1: ZStream[Any, Throwable, Int] = ZStream.fromIterator(Iterator(1, 2, 3))
val s2: ZStream[Any, Throwable, Int] = ZStream.fromIterator(Iterator.range(1, 4))
val s3: ZStream[Any, Throwable, Int] = ZStream.fromIterator(Iterator.continually(0))
```

Also, there is another constructor called **`ZStream.fromIterator`** that creates a stream from an iterator which may throw an exception.

**ZStream.fromIteratorZIO** — If we have an effectful Iterator that may throw Exception, we can use `fromIteratorZIO` to convert that to the ZIO Stream:

```scala mdoc:silent:nest
import scala.io.Source
val lines: ZStream[Any, Throwable, String] = 
  ZStream.fromIteratorZIO(ZIO.attempt(Source.fromFile("file.txt").getLines()))
```

Using this method is not good for resourceful effects like above, so it's better to rewrite that using `ZStream.fromIteratorScoped` function.

**ZStream.fromIteratorScoped** — Using this constructor we can convert a scoped iterator to ZIO Stream:

```scala mdoc:silent:nest
val lines: ZStream[Any, Throwable, String] = 
  ZStream.fromIteratorScoped(
    ZIO.fromAutoCloseable(
      ZIO.attempt(scala.io.Source.fromFile("file.txt"))
    ).map(_.getLines())
  )
```

**ZStream.fromJavaIterator** — It is the Java version of these constructors which create a stream from Java iterator that may throw an exception. We can convert any Java collection to an iterator and then lift them to the ZIO Stream.

For example, to convert the Java Stream to the ZIO Stream, `ZStream` has a `fromJavaStream` constructor which convert the Java Stream to the Java Iterator and then convert that to the ZIO Stream using `ZStream.fromJavaIterator` constructor:

```scala mdoc:silent:nest
def fromJavaStream[A](stream: => java.util.stream.Stream[A]): ZStream[Any, Throwable, A] =
  ZStream.fromJavaIterator(stream.iterator())
```

Similarly, `ZStream` has `ZStream.fromJavaIteratorSucceed`, `ZStream.fromJavaIteratorZIO` and `ZStream.fromJavaIteratorScoped` constructors.

## From Iterables

**ZStream.fromIterable** — We can create a stream from `Iterable` collection of values:

```scala mdoc:silent
val list = ZStream.fromIterable(List(1, 2, 3))
```

**ZStream.fromIterableZIO** — If we have an effect producing a value of type `Iterable` we can use `fromIterableZIO` constructor to create a stream of that effect.

Assume we have a database that returns a list of users using `Task`:

```scala mdoc:invisible
import zio._
case class User(name: String)
```

```scala mdoc:silent:nest
trait Database {
  def getUsers: Task[List[User]]
}

object Database {
  def getUsers: ZIO[Database, Throwable, List[User]] = 
    ZIO.serviceWithZIO[Database](_.getUsers)
}
```

As this operation is effectful, we can use `ZStream.fromIterableZIO` to convert the result to the `ZStream`:

```scala mdoc:silent:nest
val users: ZStream[Database, Throwable, User] = 
  ZStream.fromIterableZIO(Database.getUsers)
```

## From Repetition

**ZStream.repeat** — Repeats the provided value infinitely:

```scala mdoc:silent:nest
val repeatZero: ZStream[Any, Nothing, Int] = ZStream.repeat(0)
```

**ZStream.repeatWith** — This is another variant of `repeat`, which repeats according to the provided schedule. For example, the following stream produce zero value every second:

```scala mdoc:silent:nest
import zio._
import zio.Clock._
import zio.Duration._
import zio.Random._
import zio.Schedule
val repeatZeroEverySecond: ZStream[Any, Nothing, Int] = 
  ZStream.repeatWithSchedule(0, Schedule.spaced(1.seconds))
```

**ZStream.repeatZIO** — Assume we have an effectful API, and we need to call that API and create a stream from the result of that. We can create a stream from that effect that repeats forever.

Let's see an example of creating a stream of random numbers:

```scala mdoc:silent:nest
val randomInts: ZStream[Any, Nothing, Int] =
  ZStream.repeatZIO(Random.nextInt)
```

**ZStream.repeatZIOOption** — We can repeatedly evaluate the given effect and terminate the stream based on some conditions.

Let's create a stream repeatedly from user inputs until user enter "EOF" string:

```scala mdoc:silent:nest
val userInputs: ZStream[Any, IOException, String] = 
  ZStream.repeatZIOOption(
    Console.readLine.mapError(Option(_)).flatMap {
      case "EOF" => ZIO.fail[Option[IOException]](None)
      case o     => ZIO.succeed(o)
    }
  )
```

Here is another interesting example of using `repeatZIOOption`; In this example, we are draining an `Iterator` to create a stream of that iterator:

```scala mdoc:silent:nest
def drainIterator[A](it: Iterator[A]): ZStream[Any, Throwable, A] =
  ZStream.repeatZIOOption {
    ZIO.attempt(it.hasNext).mapError(Some(_)).flatMap { hasNext =>
      if (hasNext) ZIO.attempt(it.next()).mapError(Some(_))
      else ZIO.fail(None)
    }
  }
```

**ZStream.tick** —  A stream that emits Unit values spaced by the specified duration:

```scala mdoc:silent:nest
val stream: ZStream[Any, Nothing, Unit] = 
  ZStream.tick(1.seconds)
```

There are some other variant of repetition API like `repeatZIOWith`, `repeatZIOOption`, `repeatZIOChunk` and `repeatZIOChunkOption`.

## From Unfolding/Pagination

In functional programming, `unfold` is dual to `fold`.

With `fold` we can process a data structure and build a return value. For example, we can process a `List[Int]` and return the sum of all its elements.

The `unfold` represents an operation that takes an initial value and generates a recursive data structure, one-piece element at a time by using a given state function. For example, we can create a natural number by using `one` as the initial element and the `inc` function as the state function.

### Unfold

**ZStream.unfold** — `ZStream` has `unfold` function, which is defined as follows:

```scala
object ZStream {
  def unfold[S, A](s: S)(f: S => Option[(A, S)]): ZStream[Any, Nothing, A] = ???
}
```

- **s** — An initial state value
- **f** — A state function `f` that will be applied to the initial state `s`. If the result of this application is `None` the stream will end, otherwise the result is `Some`, so the next element in the stream would be `A` and the current state of transformation changed to the new `S`, this new state is the basis of the next unfold process.

For example, we can a stream of natural numbers using `ZStream.unfold`:

```scala mdoc:silent
val nats: ZStream[Any, Nothing, Int] = ZStream.unfold(1)(n => Some((n, n + 1)))
```

We can write `countdown` function using `unfold`:

```scala mdoc:silent
def countdown(n: Int) = ZStream.unfold(n) {
  case 0 => None
  case s => Some((s, s - 1))
}
```

Running this function with an input value of 3 returns a `ZStream` which contains 3, 2, 1 values.

**ZStream.unfoldZIO** — `unfoldZIO` is an effectful version of `unfold`. It helps us to perform _effectful state transformation_ when doing unfold operation.

Let's write a stream of lines of input from a user until the user enters the `exit` command:

```scala mdoc:silent
val inputs: ZStream[Any, IOException, String] = ZStream.unfoldZIO(()) { _ =>
  Console.readLine.map {
    case "exit"  => None
    case i => Some((i, ()))
  } 
}   
```

`ZStream.unfoldChunk`, and `ZStream.unfoldChunkZIO` are other variants of `unfold` operations but for `Chunk` data type.

### Pagination

**ZStream.paginate** — This is similar to `unfold`, but allows the emission of values to end one step further. For example the following stream emits `0, 1, 2, 3` elements:

```scala mdoc:silent:nest
val stream = ZStream.paginate(0) { s =>
  s -> (if (s < 3) Some(s + 1) else None)
}
```

Similar to `unfold` API, `ZStream` has various other forms as well as `ZStream.paginateZIO`, `ZStream.paginateChunk` and `ZStream.paginateChunkZIO`.

### Unfolding vs. Pagination

One might ask what is the difference between `unfold` and `paginate` combinators? When we should prefer one over another? So, let's find the answer to this question by doing another example.

Assume we have a paginated API that returns an enormous amount of data in a paginated fashion. When we call that API, it returns a data type `ResultPage` which contains the first-page result and, it also contains a flag indicating whether that result is the last one, or we have more data on the next page:

```scala mdoc:invisible
case class RowData()
```

```scala mdoc:silent:nest
case class PageResult(results: Chunk[RowData], isLast: Boolean)

def listPaginated(pageNumber: Int): ZIO[Any, Throwable, PageResult] = ZIO.fail(???)
```

We want to convert this API to a stream of `RowData` events. For the first attempt, we might think we can do it by using `unfold` operation as below:

```scala mdoc:silent:nest
val firstAttempt: ZStream[Any, Throwable, RowData] = 
  ZStream.unfoldChunkZIO(0) { pageNumber =>
    for {
      page <- listPaginated(pageNumber)
    } yield
      if (page.isLast) None
      else Some((page.results, pageNumber + 1))
  }
```

But it doesn't work properly; it doesn't include the last page result. So let's do a trick and to perform another API call to include the last page results:

```scala mdoc:silent:nest
val secondAttempt: ZStream[Any, Throwable, RowData] = 
  ZStream.unfoldChunkZIO(Option[Int](0)) {
    case None => ZIO.none // We already hit the last page
    case Some(pageNumber) => // We did not hit the last page yet
     for {
        page <- listPaginated(pageNumber)
      } yield Some(page.results, if (page.isLast) None else Some(pageNumber + 1))
  }
```

This works and contains all the results of returned pages. It works but as we saw, `unfold` is not friendliness to retrieve data from paginated APIs.

We need to do some hacks and extra works to include results from the last page. This is where `ZStream.paginate` operation comes to play, it helps us to convert a paginated API to ZIO stream in a more ergonomic way. Let's rewrite this solution by using `paginate`:

```scala mdoc:silent:nest
val finalAttempt: ZStream[Any, Throwable, RowData] = 
  ZStream.paginateChunkZIO(0) { pageNumber =>
    for {
      page <- listPaginated(pageNumber)
    } yield page.results -> (if (!page.isLast) Some(pageNumber + 1) else None)
  }
```

## From Wrapped Streams

Sometimes we have an effect that contains a `ZStream`, we can unwrap the embedded stream and produce a stream from those effects. If the stream is wrapped with the `ZIO` effect, we use `unwrap`, and if it is wrapped with scoped `ZIO` we use `unwrapScoped`:

```scala mdoc:silent:nest
val wrappedWithZIO: UIO[ZStream[Any, Nothing, Int]] = 
  ZIO.succeed(ZStream(1, 2, 3))
val s1: ZStream[Any, Nothing, Int] = 
  ZStream.unwrap(wrappedWithZIO)

val wrappedWithZIOScoped = ZIO.succeed(ZStream(1, 2, 3))
val s2: ZStream[Any, Nothing, Int] = 
  ZStream.unwrapScoped(wrappedWithZIOScoped)
```

## From Java IO

**ZStream.fromPath** — Create ZIO Stream from a file:

```scala mdoc:silent:nest
import java.nio.file.Paths
val file: ZStream[Any, Throwable, Byte] = 
  ZStream.fromPath(Paths.get("file.txt"))
```

**ZStream.fromInputStream** — Creates a stream from a `java.io.InputStream`:

```scala mdoc:silent:nest
val stream: ZStream[Any, IOException, Byte] = 
  ZStream.fromInputStream(new FileInputStream("file.txt"))
```

Note that the InputStream will not be explicitly closed after it is exhausted. Use `ZStream.fromInputStreamZIO`, or `ZStream.fromInputStreamScoped` instead.

**ZStream.fromInputStreamZIO** — Creates a stream from a `java.io.InputStream`. Ensures that the InputStream is closed after it is exhausted:

```scala mdoc:silent:nest
val stream: ZStream[Any, IOException, Byte] = 
  ZStream.fromInputStreamZIO(
    ZIO.attempt(new FileInputStream("file.txt"))
      .refineToOrDie[IOException]
  )
```

**ZStream.fromInputStreamScoped** — Creates a stream from a scoped `java.io.InputStream` value:

```scala mdoc:silent:nest
val scoped: ZIO[Scope, IOException, FileInputStream] =
  ZIO.fromAutoCloseable(
    ZIO.attempt(new FileInputStream("file.txt"))
  ).refineToOrDie[IOException]

val stream: ZStream[Any, IOException, Byte] = 
  ZStream.fromInputStreamScoped(scoped)
```

**ZStream.fromResource** — Create a stream from resource file:
```scala mdoc:silent:nest
val stream: ZStream[Any, IOException, Byte] =
  ZStream.fromResource("file.txt")
```

**ZStream.fromReader** — Creates a stream from a `java.io.Reader`:

```scala mdoc:silent:nest
val stream: ZStream[Any, IOException, Char] = 
   ZStream.fromReader(new FileReader("file.txt"))
```

ZIO Stream also has `ZStream.fromReaderZIO` and `ZStream.fromReaderScoped` variants.

## From Java Stream

We can use `ZStream.fromJavaStreamTotal` to convert a Java Stream to ZIO Stream:

```scala mdoc:silent:nest
val stream: ZStream[Any, Throwable, Int] = 
  ZStream.fromJavaStream(java.util.stream.Stream.of(1, 2, 3))
```

ZIO Stream also has `ZStream.fromJavaStream`, `ZStream.fromJavaStreamZIO` and `ZStream.fromJavaStreamScoped` variants.

## From Queue and Hub

`Queue` and `Hub` are two asynchronous messaging data types in ZIO that can be converted into the ZIO Stream:

```scala
object ZStream {
  def fromQueue[O](
    queue: Dequeue[O],
    maxChunkSize: Int = DefaultChunkSize
  ): ZStream[Any, Nothing, O] = ???

  def fromHub[A](
    hub: Hub[A]
  ): ZStream[Any, Nothing, A] = ???
}
```

If they contain `Chunk` of elements, we can use `ZStream.fromChunk...` constructors to create a stream from those elements (e.g. `ZStream.fromChunkQueue`):

```scala mdoc:silent:nest
for {
  promise <- Promise.make[Nothing, Unit]
  hub     <- Hub.unbounded[Chunk[Int]]
  scoped = ZStream.fromChunkHubScoped(hub).tap(_ => promise.succeed(()))
  stream  = ZStream.unwrapScoped(scoped)
  fiber   <- stream.foreach(printLine(_)).fork
  _       <- promise.await
  _       <- hub.publish(Chunk(1, 2, 3))
  _       <- fiber.join
} yield ()
```

Also, If we need to shutdown a `Queue` or `Hub`, once the stream is closed, we should use `ZStream.from..Shutdown` constructors (e.g. `ZStream.fromQueueWithShutdown`).

Also, we can lift a `TQueue` to the ZIO Stream:

```scala mdoc:silent:nest
for {
  q <- STM.atomically(TQueue.unbounded[Int])
  stream = ZStream.fromTQueue(q)
  fiber <- stream.foreach(printLine(_)).fork
  _     <- STM.atomically(q.offer(1))
  _     <- STM.atomically(q.offer(2))
  _     <- fiber.join
} yield ()
```

## From Schedule

We can create a stream from a `Schedule` that does not require any further input. The stream will emit an element for each value output from the schedule, continuing for as long as the schedule continues:

```scala mdoc:silent:nest
val stream: ZStream[Any, Nothing, Long] =
  ZStream.fromSchedule(Schedule.spaced(1.second) >>> Schedule.recurs(10))
```
