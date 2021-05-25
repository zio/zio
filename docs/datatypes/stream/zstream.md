---
id: zstream
title: "ZStream"
---

## Introduction 

A `ZStream[R, E, O]` is a description of a program that, when evaluated, may emit zero or more values of type `O`, may fail with errors of type `E`, and uses an environment of type `R`. 

One way to think of `ZStream` is as a `ZIO` program that could emit multiple values. As we know, a `ZIO[R, E, A]` data type, is a functional effect which is a description of a program that needs an environment of type `R`, it may end with an error of type `E`, and in case of success, it returns a value of type `A`. The important note about `ZIO` effects is that in the case of success they always end with exactly one value. There is no optionality here, no multiple infinite values, we always get exact value:

```scala mdoc:invisible
import zio.{ZIO, Task, ZManaged, Chunk}
import zio.blocking.Blocking
import zio.random.Random
import zio.clock.Clock
import java.io.{FileReader, FileInputStream, IOException}
import zio.console.Console
import java.net.URL
```

```scala mdoc:silent:nest
val failedEffect: ZIO[Any, String, Nothing]       = ZIO.fail("fail!")
val oneIntValue : ZIO[Any, Nothing, Int]          = ZIO.succeed(3)
val oneListValue: ZIO[Any, Nothing, List[Int]]    = ZIO.succeed(List(1, 2, 3))
val oneOption   : ZIO[Any, Nothing , Option[Int]] = ZIO.succeed(None)
```

A functional stream is pretty similar, it is a description of a program that requires an environment of type `R` and it may signal with errors of type `E` and it yields `O`, but the difference is that it will yield zero or more values. 

So a `ZStream` represents one of the following cases in terms of its elements:
- **An Empty Stream** — It might end up empty; which represent an empty stream, e.g. `ZStream.empty`.
- **One Element Stream** — It can represent a stream with just one value, e.g. `ZStream.succeed(3)`.
- **Multiple Finite Element Stream** — It can represent a stream of finite values, e.g. `ZStream.range(1, 10)`
- **Multiple Infinite Element Stream** — It can even represent a stream that _never ends_ as an infinite stream, e.g. `ZStream.iterate(1)(_ + 1)`.

```scala mdoc:silent:nest
import zio.stream.ZStream
val emptyStream         : ZStream[Any, Nothing, Nothing]   = ZStream.empty
val oneIntValueStream   : ZStream[Any, Nothing, Int]       = ZStream.succeed(4)
val oneListValueStream  : ZStream[Any, Nothing, List[Int]] = ZStream.succeed(List(1, 2, 3))
val finiteIntStream     : ZStream[Any, Nothing, Int]       = ZStream.range(1, 10)
val infiniteIntStream   : ZStream[Any, Nothing, Int]       = ZStream.iterate(1)(_ + 1)
```

Another example of a stream is when we're pulling a Kafka topic or reading from a socket. There is no inherent definition of an end there. Stream elements arrive at some point, or even they might never arrive at any point.

## Stream Types
Based on type parameters of `ZStream`, there are 4 types of streams:

1. `ZStream[Any, Nothing, O]` — A stream that emits `O` values and cannot fail.
2. `ZStream[Any, Throwable, O]` — A stream that emits `O` values and can fail with `Throwable`.
3. `ZStream[Any, Nothing, Nothing]` — A stream that emits no elements.
4. `ZStream[R, E, O]` — A stream that requires access to the `R` service, can fail with error of type `E` and emits `O` values.

## Creating a Stream

There are several ways to create ZIO Stream. In this section, we are going to enumerate some of the important ways of creating `ZStream`. 

### Common Constructors

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

**ZStream.repeat** — Takes an initial value and applies the given function to the initial value iteratively. The initial value is the first value produced by the stream, followed by f(init), f(f(init)), ...

```scala mdoc:silent:nest
val nats: ZStream[Any, Nothing, Int] = 
  ZStream.iterate(1)(_ + 1) // 1, 2, 3, ...
```

**ZStream.range** — A stream from a range of integers `[min, max)`:

```scala mdoc:silent:nest
val range: ZStream[Any, Nothing, Int] = ZStream.range(1, 5) // 1, 2, 3, 4
```

**ZStream.environment[R]** — Create a stream that extract the request service from the environment:

```scala mdoc:silent:nest
val clockStream: ZStream[Clock, Nothing, Clock] = ZStream.environment[Clock]
```

### From Success and Failure

Similar to `ZIO` data type, we can create a `ZStream` using `fail` and `succeed` methods:

```scala mdoc:silent:nest
val s1: ZStream[Any, String, Nothing] = ZStream.fail("Uh oh!")
val s2: ZStream[Any, Nothing, Int]    = ZStream.succeed(5)
```

### From Chunks

We can create a stream from a `Chunk`:

```scala mdoc:nest
val s1 = ZStream.fromChunk(Chunk(1, 2, 3))
```

Or from multiple `Chunks`:

```scala mdoc:nest
val s2 = ZStream.fromChunks(Chunk(1, 2, 3), Chunk(4, 5, 6))
```

### From Effect

**ZStream.fromEffect** — We can create a stream from an effect by using `ZStream.fromEffect` constructor. For example, the following stream is a stream that reads a line from a user:

```scala mdoc:silent:nest
val readline: ZStream[Console, IOException, String] = 
  ZStream.fromEffect(zio.console.getStrLn)
```

A stream that produces one random number:

```scala mdoc:silent:nest
val randomInt: ZStream[Random, Nothing, Int] = 
  ZStream.fromEffect(zio.random.nextInt)
```

**ZStream.fromEffectOption** — In some cases, depending on the result of the effect, we should decide to emit an element or return an empty stream. In these cases, we can use `fromEffectOption` constructor:

```scala 
object ZStream {
  def fromEffectOption[R, E, A](fa: ZIO[R, Option[E], A]): ZStream[R, E, A] = ???
}
```

Let's see an example of using this constructor. In this example, we read a string from user input, and then decide to emit that or not; If the user enters an `EOF` string, we emit an empty stream, otherwise we emit the user input:  

```scala mdoc:silent:nest
val userInput: ZStream[Console, IOException, String] = 
  ZStream.fromEffectOption(
    zio.console.getStrLn.mapError(Option(_)).flatMap {
      case "EOF" => ZIO.fail[Option[IOException]](None)
      case o     => ZIO.succeed(o)
    }
  ) 
```

### From Iterators

Iterators are data structures that allow us to iterate over a sequence of elements. Similarly, we can think of ZIO Streams as effectual Iterators; every `ZStream` represents a collection of one or more, but effectful values. 

**ZStream.fromIteratorTotal** — We can convert an iterator that does not throw exception to `ZStream` by using `ZStream.fromIteratorTotal`:

```scala mdoc:silent:nest
val s1: ZStream[Any, Throwable, Int] = ZStream.fromIterator(Iterator(1, 2, 3))
val s2: ZStream[Any, Throwable, Int] = ZStream.fromIterator(Iterator.range(1, 4))
val s3: ZStream[Any, Throwable, Int] = ZStream.fromIterator(Iterator.continually(0))
```

Also, there is another constructor called **`ZStream.fromIterator`** that creates a stream from an iterator which may throw an exception.

**ZStream.fromIteratorEffect** — If we have an effectful Iterator that may throw Exception, we can use `fromIteratorEffect` to convert that to the ZIO Stream:

```scala mdoc:silent:nest
import scala.io.Source
val lines: ZStream[Any, Throwable, String] = 
  ZStream.fromIteratorEffect(Task(Source.fromFile("file.txt").getLines()))
```

Using this method is not good for resourceful effects like above, so it's better to rewrite that using `ZStream.fromIteratorManaged` function.

**ZStream.fromIteratorManaged** — Using this constructor we can convert a managed iterator to ZIO Stream:

```scala mdoc:silent:nest
val lines: ZStream[Any, Throwable, String] = 
  ZStream.fromIteratorManaged(
    ZManaged.fromAutoCloseable(
      Task(scala.io.Source.fromFile("file.txt"))
    ).map(_.getLines())
  )
```

**ZStream.fromJavaIterator** — It is the Java version of these constructors which create a stream from Java iterator that may throw an exception. We can convert any Java collection to an iterator and then lift them to the ZIO Stream.

For example, to convert the Java Stream to the ZIO Stream, `ZStream` has a `fromJavaStream` constructor which convert the Java Stream to the Java Iterator and then convert that to the ZIO Stream using `ZStream.fromJavaIterator` constructor:

```scala mdoc:silent:nest
def fromJavaStream[R, A](stream: => java.util.stream.Stream[A]): ZStream[R, Throwable, A] =
  ZStream.fromJavaIterator(stream.iterator())
```

Similarly, `ZStream` has `ZStream.fromJavaIteratorTotal`, `ZStream.fromJavaIteratorEffect` and `ZStream.fromJavaIteratorManaged` constructors.

### From Iterables

**ZStream.fromIterable** — We can create a stream from `Iterable` collection of values:

```scala mdoc:silent
val list = ZStream.fromIterable(List(1, 2, 3))
```

**ZStream.fromIterableM** — If we have an effect producing a value of type `Iterable` we can use `fromIterableM` constructor to create a stream of that effect.

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
  def getUsers: ZIO[Has[Database], Throwable, List[User]] = 
    ZIO.serviceWith[Database](_.getUsers)
}
```

As this operation is effectful, we can use `ZStream.fromIterableM` to convert the result to the `ZStream`:

```scala mdoc:silent:nest
val users: ZStream[Has[Database], Throwable, User] = 
  ZStream.fromIterableM(Database.getUsers)
```

### From Repetition

**ZStream.repeat** — Repeats the provided value infinitely:

```scala mdoc:silent:nest
val repeatZero: ZStream[Any, Nothing, Int] = ZStream.repeat(0)
```

**ZStream.repeatWith** — This is another variant of `repeat`, which repeats according to the provided schedule. For example, the following stream produce zero value every second:

```scala mdoc:silent:nest
import zio.clock._
import zio.duration._
import zio.random._
import zio.Schedule
val repeatZeroEverySecond: ZStream[Clock, Nothing, Int] = 
  ZStream.repeatWith(0, Schedule.spaced(1.seconds))
```

**ZStream.repeatEffect** — Assume we have an effectful API, and we need to call that API and create a stream from the result of that. We can create a stream from that effect that repeats forever.

Let's see an example of creating a stream of random numbers:

```scala mdoc:silent:nest
val randomInts: ZStream[Random, Nothing, Int] =
  ZStream.repeatEffect(zio.random.nextInt)
```

**ZStream.repeatEffectOption** — We can repeatedly evaluate the given effect and terminate the stream based on some conditions. 

Let's create a stream repeatedly from user inputs until user enter "EOF" string:

```scala mdoc:silent:nest
val userInputs: ZStream[Console, IOException, String] = 
  ZStream.repeatEffectOption(
    zio.console.getStrLn.mapError(Option(_)).flatMap {
      case "EOF" => ZIO.fail[Option[IOException]](None)
      case o     => ZIO.succeed(o)
    }
  )
```

Here is another interesting example of using `repeatEffectOption`; In this example, we are draining an `Iterator` to create a stream of that iterator:

```scala mdoc:silent:nest
def drainIterator[A](it: Iterator[A]): ZStream[Any, Throwable, A] =
  ZStream.repeatEffectOption {
    ZIO(it.hasNext).mapError(Some(_)).flatMap { hasNext =>
      if (hasNext) ZIO(it.next()).mapError(Some(_))
      else ZIO.fail(None)
    }
  }
```

**ZStream.tick** —  A stream that emits Unit values spaced by the specified duration:

```scala mdoc:silent:nest
val stream: ZStream[Clock, Nothing, Unit] = 
  ZStream.tick(1.seconds)
```

There are some other variant of repetition API like `repeatEffectWith`, `repeatEffectOption`, `repeatEffectChunk` and `repeatEffectChunkOption`.

### From Unfolding/Pagination

In functional programming, `unfold` is dual to `fold`. 

With `fold` we can process a data structure and build a return value. For example, we can process a `List[Int]` and return the sum of all its elements. 

The `unfold` represents an operation that takes an initial value and generates a recursive data structure, one-piece element at a time by using a given state function. For example, we can create a natural number by using `one` as the initial element and the `inc` function as the state function.

#### Unfold

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

**ZStream.unfoldM** — `unfoldM` is an effectful version of `unfold`. It helps us to perform _effectful state transformation_ when doing unfold operation.

Let's write a stream of lines of input from a user until the user enters the `exit` command:

```scala mdoc:silent
val inputs: ZStream[Console, IOException, String] = ZStream.unfoldM(()) { _ =>
  zio.console.getStrLn.map {
    case "exit"  => None
    case i => Some((i, ()))
  } 
}   
```

`ZStream.unfoldChunk`, and `ZStream.unfoldChunkM` are other variants of `unfold` operations but for `Chunk` data type.

#### Pagination

**ZStream.paginate** — This is similar to `unfold`, but allows the emission of values to end one step further. For example the following stream emits `0, 1, 2, 3` elements:

```scala mdoc:silent:nest
val stream = ZStream.paginate(0) { s =>
  s -> (if (s < 3) Some(s + 1) else None)
}
```

Similar to `unfold` API, `ZStream` has various other forms as well as `ZStream.paginateM`, `ZStream.paginateChunk` and `ZStream.paginateChunkM`.

#### Unfolding vs. Pagination

One might ask what is the difference between `unfold` and `paginate` combinators? When we should prefer one over another? So, let's find the answer to this question by doing another example.

Assume we have a paginated API that returns an enormous amount of data in a paginated fashion. When we call that API, it returns a data type `ResultPage` which contains the first-page result and, it also contains a flag indicating whether that result is the last one, or we have more data on the next page:

```scala mdoc:invisible
case class RowData()
```

```scala mdoc:silent:nest
case class PageResult(results: Chunk[RowData], isLast: Boolean)

def listPaginated(pageNumber: Int): ZIO[Console, Throwable, PageResult] = ???
```

We want to convert this API to a stream of `RowData` events. For the first attempt, we might think we can do it by using `unfold` operation as below:

```scala mdoc:silent:nest
val firstAttempt: ZStream[Console, Throwable, RowData] = 
  ZStream.unfoldChunkM(0) { pageNumber =>
    for {
      page <- listPaginated(pageNumber)
    } yield
      if (page.isLast) None
      else Some((page.results, pageNumber + 1))
  }
```

But it doesn't work properly; it doesn't include the last page result. So let's do a trick and to perform another API call to include the last page results:

```scala mdoc:silent:nest
val secondAttempt: ZStream[Console, Throwable, RowData] = 
  ZStream.unfoldChunkM(Option[Int](0)) {
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
val finalAttempt: ZStream[Console, Throwable, RowData] = 
  ZStream.paginateChunkM(0) { pageNumber =>
    for {
      page <- listPaginated(pageNumber)
    } yield page.results -> (if (!page.isLast) Some(pageNumber + 1) else None)
  }
```

### From Wrapped Streams

Sometimes we have an effect that contains a `ZStream`, we can unwrap the embedded stream and produce a stream from those effects. If the stream is wrapped with the `ZIO` effect, we use `unwrap`, and if it is wrapped with `ZManaged` we use `unwrapManaged`:

```scala mdoc:silent:nest
val wrapedWithZIO = ZIO(ZStream(1, 2, 3))
val unwrapped     = ZStream.unwrap(wrapedWithZIO)

val wrappedWithZManaged = ZManaged.succeed(ZStream(1, 2, 3))
val unwrapped_          = ZStream.unwrapManaged(wrappedWithZManaged)
```

### From Java IO

**ZStream.fromFile** — Create ZIO Stream from a file:

```scala mdoc:silent:nest
import java.nio.file.Paths
val file: ZStream[Blocking, Throwable, Byte] = 
  ZStream.fromFile(Paths.get("file.txt"))
```

**ZStream.fromInputStream** — Creates a stream from a `java.io.InputStream`:

```scala mdoc:silent:nest
val stream: ZStream[Blocking, IOException, Byte] = 
  ZStream.fromInputStream(new FileInputStream("file.txt"))
```

Note that the InputStream will not be explicitly closed after it is exhausted. Use `ZStream.fromInputStreamEffect`, or `ZStream.fromInputStreamManaged` instead.

**ZStream.fromInputStreamEffect** — Creates a stream from a `java.io.InputStream`. Ensures that the InputStream is closed after it is exhausted:

```scala mdoc:silent:nest
val stream: ZStream[Blocking, IOException, Byte] = 
  ZStream.fromInputStreamEffect(
    ZIO.effect(new FileInputStream("file.txt"))
      .refineToOrDie[IOException]
  )
```

**ZStream.fromInputStreamManaged** — Creates a stream from a managed `java.io.InputStream` value:

```scala mdoc:silent:nest
val managed: ZManaged[Any, IOException, FileInputStream] =
  ZManaged.fromAutoCloseable(
    ZIO.effect(new FileInputStream("file.txt"))
  ).refineToOrDie[IOException]

val stream: ZStream[Blocking, IOException, Byte] = 
  ZStream.fromInputStreamManaged(managed)
```

**ZStream.fromResource** — Create a stream from resource file:
```scala mdoc:silent:nest
val stream: ZStream[Blocking, IOException, Byte] =
  ZStream.fromResource("file.txt")
```

**ZStream.fromReader** — Creates a stream from a `java.io.Reader`:

```scala mdoc:silent:nest
val stream: ZStream[Blocking, IOException, Char] = 
   ZStream.fromReader(new FileReader("file.txt"))
```

ZIO Stream also has `ZStream.fromReaderEffect` and `ZStream.fromReaderManaged` variants.

### From Java Stream

We can use `ZStream.fromJavaStreamTotal` to convert a Java Stream to ZIO Stream:

```scala mdoc:silent:nest
val stream: ZStream[Any, Throwable, Int] = 
  ZStream.fromJavaStream(java.util.stream.Stream.of(1, 2, 3))
```

ZIO Stream also has `ZStream.fromJavaStream`, `ZStream.fromJavaStreamEffect` and `ZStream.fromJavaStreamManaged` variants.

### From Queue and Hub

`Queue` and `Hub` are two asynchronous messaging data types in ZIO that can be converted into the ZIO Stream:

```scala
object ZStream {
  def fromQueue[R, E, O](
    queue: ZQueue[Nothing, R, Any, E, Nothing, O],
    maxChunkSize: Int = DefaultChunkSize
  ): ZStream[R, E, O] = ???

  def fromHub[R, E, A](
    hub: ZHub[Nothing, R, Any, E, Nothing, A]
  ): ZStream[R, E, A] = ???
}
```

If they contain `Chunk` of elements, we can use `ZStream.fromChunk...` constructors to create a stream from those elements (e.g. `ZStream.fromChunkQueue`). Also, If we need to shutdown a `Queue` or `Hub`, once the stream is closed, we should use `ZStream.from..Shutdown` constructors (e.g. `ZStream.fromQueueWithShutdown`).

Also, we can lift a `TQueue` to the ZIO Stream:

```scala
object ZStream {
  def fromTQueue[A](queue: TQueue[A]): ZStream[Any, Nothing, A] = ???
}
```

### From Schedule

We can create a stream from a `Schedule` that does not require any further input. The stream will emit an element for each value output from the schedule, continuing for as long as the schedule continues:

```scala mdoc:silent:nest
val stream: ZStream[Clock, Nothing, Long] =
  ZStream.fromSchedule(Schedule.spaced(1.second) >>> Schedule.recurs(10))
```

### Resourceful Streams

Most of the constructors of `ZStream` have a special variant to lift a Managed resource to a Stream (e.g. `ZStream.fromReaderManaged`). By using these constructors, we are creating streams that are resource-safe. Before creating a stream, they acquire the resource, and after usage; they close the stream.

ZIO Stream also has `bracket` and `finalizer` constructors which are similar to `ZManaged`. They allow us to clean up or finalizing before the stream ends:

**ZStream.bracket** — We can provide `acquire` and `release` actions to `ZStream.bracket` to create a resourceful stream:

```scala
object ZStream {
  def bracket[R, E, A](
    acquire: ZIO[R, E, A]
  )(
    release: A => URIO[R, Any]
  ): ZStream[R, E, A] = ???
```

Let's see an example of using a bracket when reading a file. In this example, by providing `acquire` and `release` actions to `ZStream.bracket`, it gives us a managed stream of `BufferedSource`. As this stream is managed, we can convert that `BufferedSource` to a stream of its lines and then run it, without worrying about resource leakage:

```scala mdoc:silent:nest
import zio.console._
val lines: ZStream[Console, Throwable, String] =
  ZStream
    .bracket(
      ZIO.effect(Source.fromFile("file.txt")) <* putStrLn("The file was opened.")
    )(x => URIO.effectTotal(x.close()) <* putStrLn("The file was closed.").orDie)
    .flatMap { is =>
      ZStream.fromIterator(is.getLines())
    }
```

**ZStream.finalizer** — We can also create a stream that never fails and define a finalizer for it, so that finalizer will be executed before that stream ends. 

```
object ZStream {
  def finalizer[R](
    finalizer: URIO[R, Any]
  ): ZStream[R, Nothing, Any] = ???
}
```

It is useful when need to add a finalizer to an existing stream. Assume we need to clean up the temporary directory after our streaming application ends:

```scala mdoc:silent:nest
import java.nio.file.{Path, Paths}
import zio.console._
def application: ZStream[Console, IOException, Unit] = ZStream.fromEffect(putStrLn("Application Logic."))
def deleteDir(dir: Path): ZIO[Console, IOException, Unit] = putStrLn("Deleting file.")

val myApp: ZStream[Console, IOException, Any] =
  application ++ ZStream.finalizer(
    (deleteDir(Paths.get("tmp")) *>
      putStrLn("Temporary directory was deleted.")).orDie
  )
```

## Transforming a Stream

ZIO Stream supports many standard transforming functions like `map`, `partition`, `grouped`, `groupByKey`, `groupedWithin`
and many others. Here are examples of how to use them.   

### map

**map** — Applies a given function to all element of this stream to produce another stream:
```scala mdoc:silent
import zio.stream._

val intStream: UStream[Int] = Stream.fromIterable(0 to 100)
val stringStream: UStream[String] = intStream.map(_.toString)
```

If our transformation is effectful, we can use `ZStream#mapM` instead.

**mapMPar** —  It is similar to `mapM`, but will evaluate effects in parallel. It will emit the results downstream in the original order. The `n` argument specifies the number of concurrent running effects.

Let's write a simple page downloader, which download URLs concurrently:

```scala mdoc:silent:nest
def fetchUrl(url: URL): Task[String] = Task.succeed(???)
def getUrls: Task[List[URL]] = Task.succeed(???)

val pages = ZStream.fromIterableM(getUrls).mapMPar(8)(fetchUrl)  
```
    
**mapChunk** — Each stream is backed by some `Chunk`s. By using `mapChunk` we can batch the underlying stream and map every `Chunk` at once:

```scala mdoc:silent
val chunked = 
  ZStream
    .fromChunks(Chunk(1, 2, 3), Chunk(4, 5), Chunk(6, 7, 8, 9))

val stream = chunked.mapChunks(x => x.tail)

// Input:  1, 2, 3, 4, 5, 6, 7, 8, 9
// Output:    2, 3,    5,    7, 8, 9
```

If our transformation is effectful we can use `mapChunkM` combinator.

**mapAccum** — It is similar to a `map`, but it **transforms elements statefully**. `mapAccum` allows us to _map_ and _accumulate_ in the same operation.

```scala
abstract class ZStream[-R, +E, +O] {
  def mapAccum[S, O1](s: S)(f: (S, O) => (S, O1)): ZStream[R, E, O1]
}
```

Let's write a transformation, which calculate _running total_ of input stream:

```scala mdoc:silent:nest
def runningTotal(stream: UStream[Int]): UStream[Int] =
  stream.mapAccum(0)((acc, next) => (acc + next, acc + next))

// input:  0, 1, 2, 3,  4,  5
// output: 0, 1, 3, 6, 10, 15
```

**mapConcat** — It is similar to `map`, but maps each element to zero or more elements with the type of `Iterable` and then flattens the whole stream:

```scala mdoc:silent:nest
val numbers: UStream[Int] = 
  ZStream("1-2-3", "4-5", "6")
    .mapConcat(_.split("-"))
    .map(_.toInt)

// Input:  "1-2-3", "4-5", "6"
// Output: 1, 2, 3, 4, 5, 6
```

The effectful version of `mapConcat` is `mapConcatM`. 

`ZStream` also has chunked versions of that which are `mapConcatChunk` and `mapConcatChunkM`.

### Partitioning

#### partition
`ZStream#partition` function splits the stream into tuple of streams based on the predicate. The first stream contains all element evaluated to true, and the second one contains all element evaluated to false.

The faster stream may advance by up to `buffer` elements further than the slower one. Two streams are wrapped by `ZManaged` type. 

In the example below, left stream consists of even numbers only:

```scala mdoc:silent:nest
val partitionResult: ZManaged[Any, Nothing, (ZStream[Any, Nothing, Int], ZStream[Any, Nothing, Int])] =
  Stream
    .fromIterable(0 to 100)
    .partition(_ % 2 == 0, buffer = 50)
```

#### partitionEither
If we need to partition a stream using an effectful predicate we can use `ZStream.partitionEither`.

```scala
abstract class ZStream[-R, +E, +O] {
  final def partitionEither[R1 <: R, E1 >: E, O2, O3](
    p: O => ZIO[R1, E1, Either[O2, O3]],
    buffer: Int = 16
  ): ZManaged[R1, E1, (ZStream[Any, E1, O2], ZStream[Any, E1, O3])]
}
```

Here is a simple example of using this function:

```scala mdoc:silent:nest
val partitioned: ZManaged[Any, Nothing, (ZStream[Any, Nothing, Int], ZStream[Any, Nothing, Int])] =
  ZStream
    .fromIterable(1 to 10)
    .partitionEither(x => ZIO.succeed(if (x < 5) Left(x) else Right(x)))
```

### Grouping

#### groupByKey

To partition the stream by function result we can use `groupBy` by providing a function of type `O => K` which determines by which keys the stream should be partitioned.

```scala
abstract class ZStream[-R, +E, +O] {
  final def groupByKey[K](
    f: O => K,
    buffer: Int = 16
  ): ZStream.GroupBy[R, E, K, O]
}
```

In the example below, exam results are grouped into buckets and counted:

```scala mdoc:silent
import zio._
import zio.stream._

  case class Exam(person: String, score: Int)

  val examResults = Seq(
    Exam("Alex", 64),
    Exam("Michael", 97),
    Exam("Bill", 77),
    Exam("John", 78),
    Exam("Bobby", 71)
  )

  val groupByKeyResult: ZStream[Any, Nothing, (Int, Int)] =
    Stream
      .fromIterable(examResults)
      .groupByKey(exam => exam.score / 10 * 10) {
        case (k, s) => ZStream.fromEffect(s.runCollect.map(l => k -> l.size))
      }
```

> **Note**:
>
> `groupByKey` partition the stream by a simple function of type `O => K`; It is not an effectful function. In some cases we need to partition the stream by using an _effectful function_ of type `O => ZIO[R1, E1, (K, V)]`; So we can use `groupBy` which is the powerful version of `groupByKey` function.

#### groupBy
It takes an effectful function of type `O => ZIO[R1, E1, (K, V)]`; ZIO Stream uses this function to partition the stream and gives us a new data type called `ZStream.GroupBy` which represent a grouped stream. `GroupBy` has an `apply` method, that takes a function of type `(K, ZStream[Any, E, V]) => ZStream[R1, E1, A]`; ZIO Runtime runs this function across all groups and then merges them in a non-deterministic fashion as a result.

```scala
abstract class ZStream[-R, +E, +O] {
  final def groupBy[R1 <: R, E1 >: E, K, V](
    f: O => ZIO[R1, E1, (K, V)],
    buffer: Int = 16
  ): ZStream.GroupBy[R1, E1, K, V]
}
```

In the example below, we are going `groupBy` given names by their first character and then count the number of names in each group:

```scala mdoc:silent:nest
val counted: UStream[(Char, Long)] =
  ZStream("Mary", "James", "Robert", "Patricia", "John", "Jennifer", "Rebecca", "Peter")
    .groupBy(x => ZIO.succeed((x.head, x))) { case (char, stream) =>
      ZStream.fromEffect(stream.runCount.map(count => char -> count))
    }
// Input:  Mary, James, Robert, Patricia, John, Jennifer, Rebecca, Peter
// Output: (P, 2), (R, 2), (M, 1), (J, 3)
```

Let's change the above example a bit into an example of classifying students. The teacher assigns the student to a specific class based on the student's talent. Note that the partitioning operation is an effectful:

```scala mdoc:silent:nest
val classifyStudents: ZStream[Console, IOException, (String, Seq[String])] =
  ZStream.fromEffect(
    putStrLn("Please assign each student to one of the A, B, or C classrooms.")
  ) *> ZStream("Mary", "James", "Robert", "Patricia", "John", "Jennifer", "Rebecca", "Peter")
    .groupBy(student =>
      putStr(s"What is the classroom of $student? ") *>
        getStrLn.map(classroom => (classroom, student))
    ) { case (classroom, students) =>
      ZStream.fromEffect(
        students
          .fold(Seq.empty[String])((s, e) => s :+ e)
          .map(students => classroom -> students)
      )
    }

// Input: 
// Please assign each student to one of the A, B, or C classrooms.
// What is the classroom of Mary? A
// What is the classroom of James? B
// What is the classroom of Robert? A
// What is the classroom of Patricia? C
// What is the classroom of John? B
// What is the classroom of Jennifer? A
// What is the classroom of Rebecca? C
// What is the classroom of Peter? A
//
// Output: 
// (B,List(James, John))
// (A,List(Mary, Robert, Jennifer, Peter))
// (C,List(Patricia, Rebecca))
```

#### grouped
To partition the stream results with the specified chunk size, we can use the `grouped` function.

```scala mdoc:silent:nest
val groupedResult: ZStream[Any, Nothing, Chunk[Int]] =
  Stream.fromIterable(0 to 8).grouped(3)

// Input:  0, 1, 2, 3, 4, 5, 6, 7, 8
// Output: Chunk(0, 1, 2), Chunk(3, 4, 5), Chunk(6, 7, 8)
```

#### groupedWithin
It allows grouping events by time or chunk size, whichever is satisfied first. In the example below every chunk consists of 30 elements and is produced every 3 seconds.

```scala mdoc:silent
import zio._
import zio.stream._
import zio.duration._
import zio.clock.Clock

val groupedWithinResult: ZStream[Any with Clock, Nothing, Chunk[Int]] =
  Stream.fromIterable(0 to 10)
    .repeat(Schedule.spaced(1.seconds))
    .groupedWithin(30, 10.seconds)
```

### Concatenation

We can concatenate two streams by using `ZStream#++` or `ZStream#concat` operator which returns a stream that emits the elements from the left-hand stream and then emits the elements from the right stream:

```scala silent:nest
val a = ZStream(1, 2, 3)
val b = ZStream(4, 5)
val c1 = a ++ b
val c2 = a concat b
```

Also, we can use `ZStream.concatAll` constructor to concatenate given streams together:

```scala mdoc:invisible
val a = ZStream(1, 2, 3)
val b = ZStream(4, 5)
```

```scala mdoc:silent:nest
val c3 = ZStream.concatAll(Chunk(a, b))
```

### Merging

Sometimes we need to interleave the emission of two streams and create another stream. In these cases we can't use the `ZStream.concat` operation, we need another mechanism to pick elements from sources. ZIO Stream merge operations, do this for use. Let's discuss some variant of this operation:

#### merge
The `ZSstream#merge` picks elements randomly from specified streams:

```scala mdoc:silent:nest
val s1 = ZStream(1, 2, 3).chunkN(1)
val s2 = ZStream(4, 5, 6).chunkN(1)

val merged = s1 merge s2
// As the merge operation is not deterministic, it may output the following stream of numbers:
// Output: 4, 1, 2, 5, 6, 3
```

Merge operation always try to pull one chunk from each stream, if we chunk our streams equal or over 3 elements in the last example, we encounter a new stream containing one of the `1, 2, 3, 4, 5, 6` or `4, 5, 6, 1, 2, 3` elements.

### Termination Strategy

When we merge two streams, we should think about the _termination strategy_ of this operation. Each stream has a specific lifetime. One stream may emit all its elements and finish its job, another stream may end after one hour of emission, one another may have a long-running lifetime and never end. So when we merge two streams with different lifetimes, what is the termination strategy of the resulting stream?

By default, when we merge two streams using `ZStream#merge` operation, the newly produced stream will terminate when both specified streams terminate. We can also define the _termination strategy_ corresponding to our requirement. ZIO Stream supports four different termination strategies:

- **Left** — The resulting stream will terminate when the left-hand side stream terminates.
- **Right** — The resulting stream will terminate when the right-hand side stream finishes.
- **Both** — The resulting stream will terminate when both streams finish.
- **Either** — The resulting stream will terminate when one of the streams finishes.

Here is an example of specifying termination strategy when merging two streams:

```scala mdoc:silent:nest
import zio.stream.ZStream.TerminationStrategy
val s1 = ZStream.iterate(1)(_+1).take(5).chunkN(1)
val s2 = ZStream.repeat(0).chunkN(1)

val merged = s1.merge(s2, TerminationStrategy.Left)
```

We can also use `ZStream#mergeTerminateLeft`, `ZStream#mergeTerminateRight` or `ZStream#mergeTerminateEither` operations instead of specifying manually the termination strategy.

## Consuming a Stream

```scala mdoc:silent
import zio._
import zio.console._
import zio.stream._

val result: RIO[Console, Unit] = Stream.fromIterable(0 to 100).foreach(i => putStrLn(i.toString))
```

### Using a Sink

A `Sink[E, A0, A, B]` consumes values of type `A`, ultimately producing either an error of type `E`, or a value of type `B` together with a remainder of type `A0`.

We can for example reduce a `Stream` to a `ZIO` value using `Sink.foldLeft`: 

```scala mdoc:silent
import zio._
import zio.stream._

def streamReduce(total: Int, element: Int): Int = total + element
val resultFromSink: UIO[Int] = Stream(1,2,3).run(Sink.foldLeft(0)(streamReduce))
```

## Working on several streams

We can merge several streams using the `merge` method:

```scala mdoc:silent:nest
import zio.stream._

val merged: Stream[Nothing, Int] = Stream(1,2,3).merge(Stream(2,3,4))
```

Or zipping streams: 

```scala mdoc:silent
import zio.stream._

val zippedStream: Stream[Nothing, (Int, Int)] = Stream(1,2,3).zip(Stream(2,3,4))
```

Then we would be able to reduce the stream into to a `ZIO` value: 

```scala mdoc:silent
import zio._

def tupleStreamReduce(total: Int, element: (Int, Int)) = {
  val (a,b) = element
  total + (a + b)
} 

val reducedResult: UIO[Int] = zippedStream.run(Sink.foldLeft(0)(tupleStreamReduce))
```

## Compressed streams

### Decompression

If you read `Content-Encoding: deflate`, `Content-Encoding: gzip` or streams other such streams of compressed data, following transducers can be helpful:
* `inflate` transducer allows to decompress stream of _deflated_ inputs, according to [RFC 1951](https://tools.ietf.org/html/rfc1951).
* `gunzip` transducer can be used to decompress stream of _gzipped_ inputs, according to [RFC 1952](https://tools.ietf.org/html/rfc1952).

Both decompression methods will fail with `CompressionException` when input wasn't properly compressed.

```scala mdoc:silent
import zio.stream.ZStream
import zio.stream.Transducer.{ gunzip, inflate }
import zio.stream.compression.CompressionException

def decompressDeflated(deflated: ZStream[Any, Nothing, Byte]): ZStream[Any, CompressionException, Byte] = {
  val bufferSize: Int = 64 * 1024 // Internal buffer size. Few times bigger than upstream chunks should work well.
  val noWrap: Boolean = false     // For HTTP Content-Encoding should be false.
  deflated.transduce(inflate(bufferSize, noWrap))
}

def decompressGzipped(gzipped: ZStream[Any, Nothing, Byte]): ZStream[Any, CompressionException, Byte] = {
  val bufferSize: Int = 64 * 1024 // Internal buffer size. Few times bigger than upstream chunks should work well.
  gzipped.transduce(gunzip(bufferSize))
}

```

### Compression

The `deflate` transducer compresses a stream of bytes as specified by [RFC 1951](https://tools.ietf.org/html/rfc1951).

```scala mdoc:silent
import zio.stream.ZStream
import zio.stream.Transducer.deflate
import zio.stream.compression.{CompressionLevel, CompressionStrategy, FlushMode}

def compressWithDeflate(clearText: ZStream[Any, Nothing, Byte]): ZStream[Any, Nothing, Byte] = {
  val bufferSize: Int = 64 * 1024 // Internal buffer size. Few times bigger than upstream chunks should work well.
  val noWrap: Boolean = false // For HTTP Content-Encoding should be false.
  val level: CompressionLevel = CompressionLevel.DefaultCompression
  val strategy: CompressionStrategy = CompressionStrategy.DefaultStrategy
  val flushMode: FlushMode = FlushMode.NoFlush
  clearText.transduce(deflate(bufferSize, noWrap, level, strategy, flushMode))
}

def deflateWithDefaultParameters(clearText: ZStream[Any, Nothing, Byte]): ZStream[Any, Nothing, Byte] =
  clearText.transduce(deflate())
```
