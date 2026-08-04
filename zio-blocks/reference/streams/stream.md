# Stream

> `Stream[+E, +A]` is a **lazy, pull-based, typed-error stream** of elements that may fail with an error of type `E`. Nothing executes until a terminal operation is called. When you run a stream synchronously, you get `Either[E, Z]` — typed errors surface as `Left(e)`, and untyped defects propagate as exceptions:

`Stream[+E, +A]` is a **lazy, pull-based, typed-error stream** of elements that may fail with an error of type `E`. Nothing executes until a terminal operation is called. When you run a stream synchronously, you get `Either[E, Z]` — typed errors surface as `Left(e)`, and untyped defects propagate as exceptions:

```scala
abstract class Stream[+E, +A] {
  def run[E2 >: E, Z](sink: Sink[E2, A, Z]): Either[E2, Z]
  def runCollect: Either[E, Chunk[A]]
}
```

`Stream` is purely functional, referentially transparent, and resource-safe:
- **Lazy**: descriptions of pipelines, not eager computations
- **Synchronous**: all terminal operations return `Either[E, Z]` directly (no async effects)
- **Pull-based**: execution is driven from the sink backward through the pipeline
- **Typed errors**: distinguish recoverable errors (`E`) from untyped defects (`Throwable`)
- **Resource-safe**: RAII semantics ensure resources are released in all cases

## Motivation

Traditional eager sequences (like Scala `List`) fall short in **three critical dimensions**. Here's what `Stream[E, A]` solves for each:

**1. Efficiency — Wasteful Computation**

With eager evaluation, the entire dataset is processed upfront, regardless of how many elements you actually need. This example shows how much work is wasted:

```scala
// With Scala List (eager evaluation)
val data = (1 to 1_000_000).toList
val result = data
  .map(_ * 2)          // eagerly: 1M multiplications
  .filter(_ > 10)      // eagerly: 1M comparisons
  .take(10)            // finally: keep only 10
// ❌ Wasted work: computed and discarded 999,990 elements!
```

The problem: `List` eagerly applies `.map` and `.filter` to all 1 million elements, even though only the first 10 passing elements matter. In data processing pipelines (parsing CSV files, filtering logs, transforming sensor streams), this is enormously wasteful.

With `Stream[E, A]`, the architecture is **inverted**: the **sink (consumer) pulls** from the stream. If the sink asks for only 10 elements, only ~20 calculations occur (enough to find 10 valid results after filtering):

```scala
import zio.blocks.streams.*

// With Stream (lazy, pull-based evaluation)
val result: Either[Nothing, zio.blocks.chunk.Chunk[Int]] =
  Stream.fromRange((1 to 100))
    .map(_ * 2)
    .filter(_ > 10)
    .run(Sink.take(10))
// ✓ Computation stops after 10 valid elements are produced
// Only necessary work: ~20 multiplications, ~20 comparisons
```

This **short-circuiting** behavior is automatic and requires no special syntax.

**2. Resource Management — Error-Prone Cleanup**

When you open resources (file handles, network connections, database cursors), you must release them in **all** code paths—success, error, and even mid-stream cancellation. With eager sequences, this burden falls on the caller:

```
// ❌ With traditional Scala (manual resource management, uses var for mutable state)
import java.io.*

var file: BufferedReader = null
try {
  file = new BufferedReader(new FileReader("build.sbt"))
  var count = 0L
  var char = file.read()
  while (char != -1) {
    if (!Character.isWhitespace(char)) {
      count += 1
    }
    char = file.read()
  }
  count
} catch {
  case e: IOException =>
    throw e
} finally {
  if (file != null) file.close()  // ✓ Manual cleanup in finally
}
// ❌ Problem: You must remember the finally block
// ❌ Problem: If an exception occurs in the loop, cleanup must still run (easy to forget!)
// ❌ Problem: Scale to 10 resources? 50 resources? Manually nesting becomes error-prone
```

With `Stream[E, A]`, resource cleanup is **automatic, composable, and guaranteed**—even on error or if the sink cancels early:

```scala
import zio.blocks.streams.*
import java.io.*

// With Stream (resource-safe RAII)
// Open a file and count non-whitespace characters
val charCount: Either[IOException, Long] =
  Stream
    .fromJavaReader(new FileReader("build.sbt"))  // lazily acquires file handle
    .filter(!_.isWhitespace)  // process only non-whitespace
    .count  // count all matching characters
// ✓ File automatically closes in finally block (success or error)
// ✓ If FileReader throws, or filter throws, or count throws—cleanup still runs
// ✓ No manual try/finally needed; no resource leak risk
// ✓ Multiple resources (files, connections, etc.) compose naturally
```

The key difference: `Stream` releases resources via **RAII** (Resource Acquisition Is Initialization) — the resource's lifetime is bound to the compiled stream's `close()` method, which the terminal operation (`run`) always calls in a `finally` block.

**3. Error Handling — Untyped Errors**

Traditional error handling conflates two categories: recoverable **domain errors** (e.g., parsing failed, validation failed) and fatal **defects** (e.g., `OutOfMemoryError`, `NullPointerException`). This makes it hard to write correct error recovery code:

```scala
import scala.util.Try

// With Try/catch (untyped errors)
case class ParseError(msg: String)

def parseLines(lines: List[String]): Try[List[Int]] = Try {
  lines.map { line =>
    line.toInt  // throws NumberFormatException (defect, not domain error!)
  }
}

val result = parseLines(List("1", "abc", "3"))
result match {
  case util.Success(nums) => println(s"Parsed: $nums")
  case util.Failure(e) =>
    // ❌ Can't tell if 'e' is a parse error or a JVM defect
    // ❌ Must handle *all* exceptions the same way
    // ❌ Domain logic mixed with system-level exception handling
    println(s"Error: $e")
}
```

With `Stream[E, A]`, typed errors (`E`) are distinct from untyped defects (`Throwable`), enabling proper error recovery:

```scala
import zio.blocks.streams.*

case class ParseError(msg: String)

// With Stream (typed errors)
val result: Either[ParseError, zio.blocks.chunk.Chunk[Int]] =
  Stream
    .fromIterable(List("1", "abc", "3"))
    .flatMap { line =>
      try {
        Stream.succeed(line.toInt)  // success path
      } catch {
        case _: NumberFormatException =>
          Stream.fail(ParseError(s"Not a number: $line"))  // typed error
      }
    }
    .runCollect

result match {
  case Left(parseError) =>
    // ✓ This branch is *only* for domain errors we chose to surface
    println(s"Parse error: ${parseError.msg}")
  case Right(nums) =>
    // ✓ Untyped defects (OutOfMemoryError, etc.) propagate as exceptions
    // ✓ Clear separation: Either[E, Z] is for recovery, uncaught exceptions are fatal
    println(s"Parsed: ${nums}")
}
```

The key distinction: `Either[ParseError, Z]` means domain errors are *recoverable* via `Left`; any uncaught `Throwable` defect propagates as an exception, which is correct—you cannot recover from running out of memory, only from bad input.

## Construction

Streams can be created from constants, collections, resources, and pull-based sources:

### Constant Streams

The simplest streams are single-element or empty streams.

#### `Stream.empty`

An empty stream that emits no elements and succeeds immediately:

```scala
object Stream {
  val empty: Stream[Nothing, Nothing]
}
```

The empty stream is useful as a base case in recursive stream builders or as a neutral element when concatenating:

```scala
import zio.blocks.streams.*

val emptyStream = Stream.empty
// emptyStream: Stream[Nothing, Nothing] = Stream.empty
val result = emptyStream.runCollect
// result: Either[Nothing, Chunk[Nothing]] = Right(IndexedSeq())
// emptyStream contains no elements
```

#### `Stream.succeed[A]`

Wraps a single value of any type. Specialized overloads avoid boxing for primitives:

```scala
object Stream {
  def succeed[A](a: A): Stream[Nothing, A]
  def succeed(a: Int): Stream[Nothing, Int]
  def succeed(a: Long): Stream[Nothing, Long]
  def succeed(a: Double): Stream[Nothing, Double]
  // ... and Byte, Short, Char, Float, Boolean variants
}
```

When you call `Stream.succeed(value)`, the stream emits exactly one element and completes successfully. This is useful for wrapping a computed value into the stream abstraction:

```scala
import zio.blocks.streams.*

val singleElement = Stream.succeed(42)
// singleElement: Stream[Nothing, Int] = Stream.succeed(...)
val result = singleElement.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(42))
```

#### `Stream.fail[E]`

Creates a stream that fails immediately with a typed error:

```scala
object Stream {
  def fail[E](error: E): Stream[E, Nothing]
}
```

Use `fail` when you need to short-circuit a stream with a known error:

```scala
import zio.blocks.streams.*

sealed trait ApiError
case class NotFound(id: String) extends ApiError

val failedStream = Stream.fail(NotFound("user-123"))
// failedStream: Stream[NotFound, Nothing] = Stream.fail(...)
val result = failedStream.runDrain
// result: Either[NotFound, Unit] = Left(NotFound("user-123"))
// result is Left(NotFound("user-123"))
```

#### `Stream.die`

Throws an untyped defect (exception) immediately:

```scala
object Stream {
  def die(t: Throwable): Stream[Nothing, Nothing]
}
```

Use `die` for truly exceptional, unrecoverable conditions that should not be caught as typed errors:

```scala
import zio.blocks.streams.*

val dieStream = Stream.die(new Exception("System failure"))
// dieStream: Stream[Nothing, Nothing] = Stream.die(...)
```

### From Collections

Streams can be created from existing collections and iterables, making it easy to convert `List`, `Array`, `Chunk`, or custom iterables into lazy streams:

#### `Stream.apply[A]`

Wraps a variable number of arguments into a stream:

```scala
object Stream {
  def apply[A](as: A*): Stream[Nothing, A]
}
```

This is the most natural way to lift a list of values:

```scala
import zio.blocks.streams.*

val numbers = Stream(1, 2, 3, 4, 5)
// numbers: Stream[Nothing, Int] = Stream(1, 2, 3, 4, 5)
val result = numbers.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(1, 2, 3, 4, 5))
```

#### `Stream.fromChunk[A]`

Converts a `Chunk` into a stream. Chunks are immutable, indexed sequences optimized for high-performance operations:

```scala
object Stream {
  def fromChunk[A](chunk: Chunk[A]): Stream[Nothing, A]
}
```

Use this when you already have a `Chunk`:

```scala
import zio.blocks.streams.*
import zio.blocks.chunk.Chunk

val chunk = Chunk(10, 20, 30)
// chunk: Chunk[Int] = IndexedSeq(10, 20, 30)
val stream = Stream.fromChunk(chunk)
// stream: Stream[Nothing, Int] = Stream.fromChunk(...)
val result = stream.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(10, 20, 30))
```

#### `Stream.fromIterable[A]`

Converts any `Iterable[A]` (List, Set, Vector, etc.) into a stream:

```scala
object Stream {
  def fromIterable[A](it: Iterable[A]): Stream[Nothing, A]
}
```

This is useful when integrating with legacy Scala collections:

```scala
import zio.blocks.streams.*

val list = List("a", "b", "c")
// list: List[String] = List("a", "b", "c")
val stream = Stream.fromIterable(list)
// stream: Stream[Nothing, String] = Stream.fromIterable(...)
val result = stream.runCollect
// result: Either[Nothing, Chunk[String]] = Right(IndexedSeq("a", "b", "c"))
```

#### `Stream.fromIterator[A]`

Converts an `Iterator[A]` into a stream. The iterator is consumed lazily:

```scala
object Stream {
  def fromIterator[A](it: => Iterator[A]): Stream[Nothing, A]
}
```

Create a stream from an iterator and collect all elements:

```scala
import zio.blocks.streams.*

val iter = Iterator(10, 20, 30, 40)
// iter: Iterator[Int] = empty iterator
val stream = Stream.fromIterator(iter)
// stream: Stream[Nothing, Int] = Stream.fromIterator(...)
val result = stream.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(10, 20, 30, 40))
```

### From Ranges

Streams can be created from numeric ranges, providing an efficient way to generate sequences of integers without allocating memory upfront:

#### `Stream.range`

Emits integers from `from` (inclusive) to `until` (exclusive):

```scala
object Stream {
  def range(from: Int, until: Int): Stream[Nothing, Int]
}
```

This is memory-efficient (does not allocate intermediate collections):

```scala
import zio.blocks.streams.*

val nums = Stream.range(0, 5)
// nums: Stream[Nothing, Int] = Stream.range(0, 5)
val result = nums.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(0, 1, 2, 3, 4))
```

#### `Stream.fromRange`

Converts a Scala `Range` object:

```scala
object Stream {
  def fromRange(range: Range): Stream[Nothing, Int]
}
```

Create a stream from a `Range` and collect elements:

```scala
import zio.blocks.streams.*

val range = 1 to 10 by 2
// range: Range = Range(1, 3, 5, 7, 9)
val stream = Stream.fromRange(range)
// stream: Stream[Nothing, Int] = Stream.fromRange(...)
val result = stream.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(1, 3, 5, 7, 9))
```

### Generators

These constructors create streams from functions and logic, useful for synthesizing infinite or computed sequences:

#### `Stream.repeat[A]`

Emits the same value infinitely:

```scala
object Stream {
  def repeat[A](a: A): Stream[Nothing, A]
}
```

Infinite streams are safe because streams are lazy; nothing runs until you call a terminal operation with a stopping condition (like `take`):

```scala
import zio.blocks.streams.*

val infinite = Stream.repeat(42)
// infinite: Stream[Nothing, Int] = Stream.repeat(...)
val first5 = infinite.take(5)
// first5: Stream[Nothing, Int] = Stream.repeat(...).take(5)
val result = first5.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(42, 42, 42, 42, 42))
```

#### `Stream.unfold[S, A]`

A stateful generator that emits elements based on a fold-like transition function:

```scala
object Stream {
  def unfold[S, A](s: S)(f: S => Option[(A, S)]): Stream[Nothing, A]
}
```

Each iteration, `f` receives the current state and returns either `None` (stop) or `Some((element, nextState))`. This is useful for generating Fibonacci numbers or other sequences defined by a recurrence relation:

```scala
import zio.blocks.streams.*

val fibonacci = Stream.unfold((0, 1)) {
  case (a, b) => Some((a, (b, a + b)))
}
// fibonacci: Stream[Nothing, Int] = Stream.unfold(...)
val first10 = fibonacci.take(10)
// first10: Stream[Nothing, Int] = Stream.unfold(...).take(10)
val result = first10.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(
//   IndexedSeq(0, 1, 1, 2, 3, 5, 8, 13, 21, 34)
// )
```

### Side Effects

These constructors embed effects and deferred computation into streams, running actions at stream execution time:

#### `Stream.eval[A]`

Runs an arbitrary side effect and emits nothing:

```scala
object Stream {
  def eval(f: => Any): Stream[Nothing, Nothing]
}
```

Use `eval` when you want a side effect in a stream (e.g., logging, metrics) but no element:

```scala
import zio.blocks.streams.*

val sideEffect = Stream.eval(println("Executing side effect"))
// sideEffect: Stream[Nothing, Nothing] = Stream.suspend(...)
val result = sideEffect.runDrain
// Executing side effect
// result: Either[Nothing, Unit] = Right(())
```

#### `Stream.attempt[A]`

Wraps a potentially throwing computation, converting non-fatal `Throwable`s into a typed error. Fatal errors (like `OutOfMemoryError`) are not caught and propagate as exceptions:

```scala
object Stream {
  def attempt[A](f: => A): Stream[Throwable, A]
}
```

Use `attempt` when you have legacy code that throws exceptions:

```scala
import zio.blocks.streams.*

def unsafeJsonParse(s: String): Int = s.toInt

val parsed = Stream.attempt(unsafeJsonParse("42"))
// parsed: Stream[Throwable, Int] = Stream.suspend(...)
val result = parsed.runCollect
// result: Either[Throwable, Chunk[Int]] = Right(IndexedSeq(42))
```

#### `Stream.attemptEval`

Evaluates a side effect and converts any thrown exception into a typed `Throwable` error. Unlike `eval`, this captures exceptions and emits nothing:

```scala
object Stream {
  def attemptEval(f: => Any): Stream[Throwable, Nothing]
}
```

Use `attemptEval` when you need to safely execute an effect that might throw, but you don't need to emit any elements:

```scala
import zio.blocks.streams.*

val effect = Stream.attemptEval {
  val file = new java.io.File("nonexistent.txt")
  if (!file.exists()) throw new java.io.FileNotFoundException("File not found")
}
val result = effect.runDrain
```

#### `Stream.defer[A]`

Defers the execution of a side effect until the stream is run:

```scala
object Stream {
  def defer(f: => Unit): Stream[Nothing, Nothing]
}
```

Defer side effects until the stream executes:

```scala
import zio.blocks.streams.*

val deferred = Stream.defer(println("Effect runs when stream executes"))
// deferred: Stream[Nothing, Nothing] = Stream.defer(...)
val result = deferred.runDrain
// Effect runs when stream executes
// result: Either[Nothing, Unit] = Right(())
```

#### `Stream.suspend[E, A]`

Defers the creation of a stream until run time, useful for recursive stream definitions:

```scala
object Stream {
  def suspend[E, A](stream: => Stream[E, A]): Stream[E, A]
}
```

Define a recursive stream safely:

```scala
import zio.blocks.streams.*

def countDown(n: Int): Stream[Nothing, Int] =
  if (n <= 0) Stream.empty
  else Stream.suspend(Stream.succeed(n) ++ countDown(n - 1))

val result = countDown(5).runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(5, 4, 3, 2, 1))
```

### I/O

Streams can read from external I/O sources like files and readers, automatically managing resource cleanup:

#### `Stream.fromInputStream`

Reads bytes from a Java `InputStream`, managing the resource:

```scala
object Stream {
  def fromInputStream(is: java.io.InputStream): Stream[java.io.IOException, Int]
}
```

The stream automatically closes the input stream when done:

```scala
import zio.blocks.streams.*
import java.io.ByteArrayInputStream

val data = new ByteArrayInputStream("Hello".getBytes)
// data: ByteArrayInputStream = java.io.ByteArrayInputStream@6a864582
val bytes = Stream.fromInputStream(data)
// bytes: Stream[IOException, Byte] = Stream.fromAcquireRelease(...)
val result = bytes.runCollect
// result: Either[IOException, Chunk[Byte]] = Right(
//   IndexedSeq(72, 101, 108, 108, 111)
// )
```

#### `Stream.fromJavaReader`

Reads characters from a Java `Reader`:

```scala
object Stream {
  def fromJavaReader(r: java.io.Reader): Stream[java.io.IOException, Char]
}
```

Read characters from a string reader:

```scala
import zio.blocks.streams.*
import java.io.StringReader

val reader = new StringReader("hello world")
// reader: StringReader = java.io.StringReader@793e494
val stream = Stream.fromJavaReader(reader)
// stream: Stream[IOException, Char] = Stream.fromAcquireRelease(...)
val result = stream.runCollect
// result: Either[IOException, Chunk[Char]] = Right(
//   IndexedSeq('h', 'e', 'l', 'l', 'o', ' ', 'w', 'o', 'r', 'l', 'd')
// )
```

#### `Stream.fromInputStreamUnmanaged`

Reads bytes from a Java `InputStream` without automatic resource management. The caller is responsible for closing the stream:

```scala
object Stream {
  def fromInputStreamUnmanaged(is: java.io.InputStream): Stream[java.io.IOException, Int]
}
```

Use this when you need to manage the stream's lifecycle yourself, for example when the stream is created from a long-lived resource:

```scala
import zio.blocks.streams.*
import java.io.ByteArrayInputStream

val data = new ByteArrayInputStream("Data".getBytes)
val bytes = Stream.fromInputStreamUnmanaged(data)
val result = bytes.runCollect
// Caller must close data when done
```

#### `Stream.fromJavaReaderUnmanaged`

Reads characters from a Java `Reader` without automatic resource management. The caller is responsible for closing the reader:

```scala
object Stream {
  def fromJavaReaderUnmanaged(r: java.io.Reader): Stream[java.io.IOException, Char]
}
```

Use this when you need to manage the reader's lifecycle yourself:

```scala
import zio.blocks.streams.*
import java.io.StringReader

val reader = new StringReader("managed externally")
val stream = Stream.fromJavaReaderUnmanaged(reader)
val result = stream.runCollect
// Caller must close reader when done
```

## Transformations

Streams provide powerful operations for transforming elements, flattening nested structures, filtering, and managing state:

### Element-wise Transformations

These operations apply functions to stream elements one-by-one, applying the transformation lazily as elements are pulled:

#### `Stream#map[B]`

Applies a function to each element:

```scala
trait Stream[+E, +A] {
  def map[B](f: A => B): Stream[E, B]
}
```

`map` does not run immediately; it builds up a description of the transformation. Only when you call a terminal operation does the mapping happen:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3)
// nums: Stream[Nothing, Int] = Stream(1, 2, 3)
val doubled = nums.map(_ * 2)
// doubled: Stream[Nothing, Int] = Stream(1, 2, 3).map(...)
val result = doubled.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(2, 4, 6))
```

**Key point:** `Stream#map` is covariant in the output type because it preserves the error type and only transforms elements. The implicit `JvmType.Infer[A]` and `JvmType.Infer[B]` enable compile-time dispatch to unboxed fast paths for primitive types (Int, Long, Double, etc.).

#### `Stream#mapError[E2]`

Transforms typed errors without affecting elements:

```scala
trait Stream[+E, +A] {
  inline def mapError[E2](f: E => E2): Stream[E2, A]
}
```

Use `mapError` to convert one error type to another:

```scala
import zio.blocks.streams.*

sealed trait ApiError
case class ServerError(msg: String) extends ApiError
case class NetworkError() extends ApiError

val mayFail: Stream[NetworkError, String] = Stream.fail(NetworkError())
val mapped = mayFail.mapError(e => ServerError("Connection failed"))
```

#### `Stream#filter`

Emits only elements that satisfy a predicate:

```scala
trait Stream[+E, +A] {
  def filter(pred: A => Boolean): Stream[E, A]
}
```

Short-circuits: as soon as the sink says "stop," filtering stops:

```scala
import zio.blocks.streams.Stream

val nums = Stream(1, 2, 3, 4, 5)
// nums: Stream[Nothing, Int] = Stream(1, 2, 3, 4, 5)
val evens = nums.filter(_ % 2 == 0)
// evens: Stream[Nothing, Int] = Stream(1, 2, 3, 4, 5).filter(...)
val result = evens.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(2, 4))
```

#### `Stream#collect[B]`

Applies a partial function, emitting only defined results:

```scala
trait Stream[+E, +A] {
  def collect[B](pf: PartialFunction[A, B]): Stream[E, B]
}
```

This combines filtering and mapping in one step:

```scala
import zio.blocks.streams.*

val mixed = Stream(1, "a", 2, "b", 3)
// mixed: Stream[Nothing, Int | String] = Stream(1, a, 2, b, 3)
val numbers = mixed.collect { case n: Int => n }
// numbers: Stream[Nothing, Int] = Stream(1, a, 2, b, 3).collect(...)
val result = numbers.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(1, 2, 3))
```

### Stateful Transformations

These operations maintain internal state while processing elements, allowing you to fold computations into the transformation:

#### `Stream#mapAccum[S, B]`

Maintains state while transforming each element:

```scala
trait Stream[+E, +A] {
  def mapAccum[S, B](init: S)(f: (S, A) => (S, B)): Stream[E, B]
}
```

`mapAccum` threads a state value through the transformation. At each step, you receive the current state and the element, return a new state and output element:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3)
// nums: Stream[Nothing, Int] = Stream(1, 2, 3)
val indexed = nums.mapAccum(0)((idx, x) => (idx + 1, (idx, x)))
// indexed: Stream[Nothing, Tuple2[Int, Int]] = Stream(1, 2, 3).mapAccum(...)
val result = indexed.runCollect
// result: Either[Nothing, Chunk[Tuple2[Int, Int]]] = Right(
//   IndexedSeq((0, 1), (1, 2), (2, 3))
// )
```

#### `Stream#scan[S]`

Like `mapAccum`, but also emits the state at each step (not the mapped value):

```scala
trait Stream[+E, +A] {
  def scan[S](init: S)(f: (S, A) => S): Stream[E, S]
}
```

This is useful for computing running totals, moving averages, or other cumulative statistics:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4)
// nums: Stream[Nothing, Int] = Stream(1, 2, 3, 4)
val cumsum = nums.scan(0)(_ + _)
// cumsum: Stream[Nothing, Int] = Stream(1, 2, 3, 4).scan(...)
val result = cumsum.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(0, 1, 3, 6, 10))
```

### Flat-Mapping (Nested Streams)

`flatMap[E2, B]` — Maps each element to a stream and flattens the results.:

```scala
trait Stream[+E, +A] {
  def flatMap[E2, B](f: A => Stream[E2, B]): Stream[E | E2, B]
}
```

`Stream#flatMap` is sequential: streams are processed one at a time, in order. This is essential for resource safety: if each inner stream acquires a resource, `Stream#flatMap` ensures they are released in proper FIFO order:

```scala
import zio.blocks.streams.*

val ids = Stream(1, 2, 3)
// ids: Stream[Nothing, Int] = Stream(1, 2, 3)
val expanded = ids.flatMap(id => Stream(s"${id}-a", s"${id}-b"))
// expanded: Stream[Nothing, String] = Stream(1, 2, 3).flatMap(...)
val result = expanded.runCollect
// result: Either[Nothing, Chunk[String]] = Right(
//   IndexedSeq("1-a", "1-b", "2-a", "2-b", "3-a", "3-b")
// )
```

#### `Stream.flattenAll[E, A]`

Flattens a stream of streams into a single stream, processing them sequentially:

```scala
object Stream {
  def flattenAll[E, A](streams: Stream[E, Stream[E, A]]): Stream[E, A]
}
```

This is equivalent to `flatMap(identity)`. Use `flattenAll` when you already have a stream of streams and want to flatten it without applying a transformation:

```scala
import zio.blocks.streams.*

val nested = Stream.fromIterable(List(
  Stream(1, 2),
  Stream(3, 4)
))
// nested: Stream[Nothing, Stream[Nothing, Int]] = Stream.fromIterable(...)
val flat = Stream.flattenAll(nested)
// flat: Stream[Nothing, Int] = Stream.fromIterable(...).flatMap(...)
val result = flat.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(1, 2, 3, 4))
```

## Windowing

Streams can be grouped, sliced, and scanned to process data in temporal windows. These operations group elements into chunks and slide windows over the stream for batch processing:

### `Stream#grouped[A]`

Collects elements into fixed-size chunks:

```scala
trait Stream[+E, +A] {
  def grouped(n: Int): Stream[E, Chunk[A]]
}
```

The last chunk may contain fewer than `n` elements:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5)
// nums: Stream[Nothing, Int] = Stream(1, 2, 3, 4, 5)
val groups = nums.grouped(2)
// groups: Stream[Nothing, Chunk[Int]] = Stream(1, 2, 3, 4, 5).chunked(2)
val result = groups.runCollect
// result: Either[Nothing, Chunk[Chunk[Int]]] = Right(
//   IndexedSeq(IndexedSeq(1, 2), IndexedSeq(3, 4), IndexedSeq(5))
// )
```

### `Stream#sliding[A]`

Creates a sliding window of size `n`, optionally stepping by `step` elements:

```scala
trait Stream[+E, +A] {
  def sliding(n: Int, step: Int = 1): Stream[E, Chunk[A]]
}
```

This is useful for computing local statistics or detecting patterns in sequences:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5)
// nums: Stream[Nothing, Int] = Stream(1, 2, 3, 4, 5)
val windows = nums.sliding(3, step = 1)
// windows: Stream[Nothing, Chunk[Int]] = Stream(1, 2, 3, 4, 5).sliding(3, 1)
val result = windows.runCollect
// result: Either[Nothing, Chunk[Chunk[Int]]] = Right(
//   IndexedSeq(IndexedSeq(1, 2, 3), IndexedSeq(2, 3, 4), IndexedSeq(3, 4, 5))
// )
```

## Combining Streams

Streams can be sequentially concatenated, zipped together, or merged:

### Sequential Concatenation

`++[E2, A2]` or `concat[E2, A2]` — Emits all elements of the first stream, then all elements of the second stream:

```scala
trait Stream[+E, +A] {
  def ++[E2, A2](that: Stream[E2, A2]): Stream[E | E2, A | A2] = concat(that)
}
```

The result type follows the same widening rules as Scala 3 unions:

- identical types stay unchanged (`A ++ A => A`)
- subtypes widen to the supertype (`Dog ++ Animal => Animal`)
- siblings with a common meaningful supertype widen to that supertype (`Dog ++ Cat => Animal`, when both extend a sealed `Animal`)
- otherwise the result is a disjoint union (`String ++ Int => String | Int`)

On Scala 3, disjoint concat results are native unions. On Scala 2, the same/subtype and sibling cases collapse to the wider existing type (zero-cost, values are reused as-is); only types without a shared meaningful supertype fall back to `Either[L, R]`.

Evaluation is sequential: the second stream only starts when the first completes:

```scala
import zio.blocks.streams.*

val first = Stream(1, 2)
// first: Stream[Nothing, Int] = Stream(1, 2)
val second = Stream(3, 4)
// second: Stream[Nothing, Int] = Stream(3, 4)
val combined = first ++ second
// combined: Stream[Nothing, Int] = Stream(1, 2) ++ Stream(3, 4)
val result = combined.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(1, 2, 3, 4))
```

For unrelated element types, Scala 3 produces a direct union while Scala 2 produces `Either`:

  

```scala
val combined: Stream[Nothing, Either[String, Int]] =
  Stream.succeed("left") ++ Stream.succeed(1)
```

  
  

```scala
val combined: Stream[Nothing, String | Int] =
  Stream.succeed("left") ++ Stream.succeed(1)
```

  

```scala
import zio.blocks.streams.*
import zio.blocks.chunk.Chunk

val concatResult = (Stream.succeed("left") ++ Stream.succeed(1)).runCollect
// concatResult: Either[Nothing, Chunk[String | Int]] = Right(
//   IndexedSeq("left", 1)
// )

assert(concatResult == Right(Chunk[String | Int]("left", 1)))
```

The error channel follows the same rules. Same/subtype errors collapse; unrelated errors remain disjoint:

```scala
import zio.blocks.streams.*

sealed trait LeftError
case class Boom(msg: String) extends LeftError
case class Missing(code: Int)

val left: Stream[LeftError, String] = Stream.fail(Boom("boom"))
// left: Stream[LeftError, String] = Stream.fail(...)
val right = Stream.succeed(true)
// right: Stream[Nothing, Boolean] = Stream.succeed(...)

left.runCollect
// res37: Either[LeftError, Chunk[String]] = Left(Boom("boom"))

val failed = left ++ (Stream.fail(Missing(404)): Stream[Missing, Boolean])
// failed: Stream[LeftError | Missing, String | Boolean] = Stream.fail(...) ++ Stream.fail(...)
failed.runCollect
// res38: Either[LeftError | Missing, Chunk[String | Boolean]] = Left(
//   Boom("boom")
// )
```

There is no separate `choice` operator anymore. Use `++` / `concat` for all sequential combination; the result type already reflects the Scala 3-style union semantics.

### Zipping

Zips two streams together as tuples (an extension method, not an instance method):

```scala
extension [E, A](stream: Stream[E, A])
  def &&[E2, B, C](that: Stream[E2, B])(
    using Tuples[A, B] { Out = C }
  ): Stream[E | E2, C]
```

The result streams have the same length as the shorter input:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3)
// nums: Stream[Nothing, Int] = Stream(1, 2, 3)
val chars = Stream('a', 'b')
// chars: Stream[Nothing, Char] = Stream(a, b)
val zipped = nums && chars
// zipped: Stream[Nothing, Tuple2[Int, Char]] = Stream.fromReader(...)
val result = zipped.runCollect
// result: Either[Nothing, Chunk[Tuple2[Int, Char]]] = Right(
//   IndexedSeq((1, 'a'), (2, 'b'))
// )
```

## Other Operations

Common utilities for deduplication, draining, and error recovery:

### Filtering Duplicates

These operations remove duplicate elements, useful for deduplicating streams before processing:

#### `Stream#distinct[A]`

Emits only unique elements (using a mutable `HashSet` internally):

```scala
trait Stream[+E, +A] {
  def distinct(implicit jtA: JvmType.Infer[A]): Stream[E, A]
}
```

This consumes memory proportional to the number of unique elements:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 2, 3, 3, 3)
// nums: Stream[Nothing, Int] = Stream(1, 2, 2, 3, 3, ...)
val unique = nums.distinct
// unique: Stream[Nothing, Int] = Stream(1, 2, 2, 3, 3, ...).distinct
val result = unique.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(1, 2, 3))
```

#### `Stream#distinctBy[K]`

Emits only elements whose key (computed by `f`) has not been seen before:

```scala
trait Stream[+E, +A] {
  def distinctBy[K](f: A => K)(implicit jtA: JvmType.Infer[A]): Stream[E, A]
}
```

This deduplicates elements by a computed key, keeping only the first occurrence of each key:

```scala
import zio.blocks.streams.*

case class Person(id: Int, name: String)

val people = Stream(
  Person(1, "Alice"),
  Person(2, "Bob"),
  Person(1, "Alice2"),  // same id as first, dropped
  Person(3, "Charlie")
)

val unique = people.distinctBy(_.id)
val result = unique.runCollect
```

### Skipping and Taking

These operations skip or limit elements, allowing you to keep or drop unwanted portions of the stream:

#### `Stream#drop`

Skips the first `n` elements:

```scala
trait Stream[+E, +A] {
  def drop(n: Long): Stream[E, A]
}
```

Dropping the first 3 elements and collecting the remainder:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
val remaining = nums.drop(3)
val result = remaining.runCollect
```

#### `Stream#take`

Emits at most the first `n` elements, then stops:

```scala
trait Stream[+E, +A] {
  def take(n: Long): Stream[E, A]
}
```

This naturally short-circuits: the stream stops pulling from upstream:

```scala
import zio.blocks.streams.*

val nums = Stream.range(0, 1000)
// nums: Stream[Nothing, Int] = Stream.range(0, 1000)
val first10 = nums.take(10)
// first10: Stream[Nothing, Int] = Stream.range(0, 1000).take(10)
val result = first10.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(
//   IndexedSeq(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
// )
```

#### `Stream#takeWhile`

Emits elements while a predicate is true, then stops:

```scala
trait Stream[+E, +A] {
  def takeWhile(pred: A => Boolean): Stream[E, A]
}
```

Taking elements while they are less than 6 stops early without processing the rest:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
// nums: Stream[Nothing, Int] = Stream(1, 2, 3, 4, 5, ...)
val firstFive = nums.takeWhile(_ < 6)
// firstFive: Stream[Nothing, Int] = Stream(1, 2, 3, 4, 5, ...).takeWhile(...)
val result = firstFive.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(1, 2, 3, 4, 5))
```

### Interspersing

`intersperse[A1 >: A]` — Inserts a separator value between every two elements.:

```scala
trait Stream[+E, +A] {
  def intersperse[A1 >: A](sep: A1): Stream[E, A1]
}
```

This is useful for rendering comma-separated lists or row delimiters:

```scala
import zio.blocks.streams.*

val items = Stream("a", "b", "c")
// items: Stream[Nothing, String] = Stream(a, b, c)
val separated = items.intersperse(", ")
// separated: Stream[Nothing, String] = Stream(a, b, c).intersperse(...)
val result = separated.runCollect
// result: Either[Nothing, Chunk[String]] = Right(
//   IndexedSeq("a", ", ", "b", ", ", "c")
// )
```

### Repeating

`repeated` — Repeats each element once, then emits the entire stream again, repeatedly.:

```scala
trait Stream[+E, +A] {
  def repeated: Stream[E, A]
}
```

This creates an infinite repetition of the stream:

```scala
import zio.blocks.streams.*

val original = Stream(1, 2)
// original: Stream[Nothing, Int] = Stream(1, 2)
val repeated = original.repeated.take(6)
// repeated: Stream[Nothing, Int] = Stream(1, 2).repeated.take(6)
val result = repeated.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(1, 2, 1, 2, 1, 2))
```

### Side Effects

`tapEach` — Applies a function to each element for side effects, passing the element through unchanged.:

```scala
trait Stream[+E, +A] {
  def tapEach(f: A => Unit)(implicit jtA: JvmType.Infer[A]): Stream[E, A]
}
```

Use `tapEach` for logging or metrics:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3)
// nums: Stream[Nothing, Int] = Stream(1, 2, 3)
val logged = nums.tapEach(x => println(s"Element: $x"))
// logged: Stream[Nothing, Int] = Stream(1, 2, 3).map(...)
val result = logged.runCollect
// Element: 1
// Element: 2
// Element: 3
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(1, 2, 3))
```

## Error Handling

Streams distinguish between recoverable business errors and unexpected exceptions, providing separate recovery mechanisms for each:

### Typed Error vs Untyped Defect

ZIO Blocks distinguishes two error channels:

- **Typed errors (`E`)**: Recoverable business logic errors. Returned as `Left(e)` from terminal operations.
- **Untyped defects (`Throwable`)**: Unexpected exceptions (bugs, system failures). Propagate as thrown exceptions.

Internally, typed errors are wrapped in `StreamError` (a non-fatal exception) and caught by the terminal operation to surface as `Left(e)`. Untyped `Throwable`s are not caught and propagate upward.

This separation allows you to:
- Use `catchAll` and `orElse` for business logic errors
- Use `catchDefect` or try-catch for unexpected exceptions
- Avoid accidentally silencing real bugs by catching all errors

Streams distinguish between recoverable domain errors and fatal defects, with flexible recovery patterns:

### Recovering from Typed Errors

These operations handle typed errors gracefully by recovering with alternative streams:

#### `Stream#catchAll[E2, A1]`

Recovers from any typed error by switching to a recovery stream:

```scala
trait Stream[+E, +A] {
  def catchAll[E2, A1](f: E => Stream[E2, A1]): Stream[E2, A | A1]
}
```

The recovery function receives the error and can return a new stream:

```scala
import zio.blocks.streams.*

sealed trait Error
case object NotFound extends Error

val mayFail: Stream[Error, String] = Stream.fail(NotFound)
// mayFail: Stream[Error, String] = Stream.fail(...)
val recovered = mayFail.catchAll(_ => Stream.succeed("default"))
// recovered: Stream[Nothing, String] = Stream.fail(...).catchAll(...)
val result = recovered.runCollect
// result: Either[Nothing, Chunk[String]] = Right(IndexedSeq("default"))
```

#### `Stream#orElse[E2, A1]`

If this stream fails, tries the fallback stream. The fallback is evaluated lazily, only on error:

```scala
trait Stream[+E, +A] {
  def orElse[E2, A1](that: => Stream[E2, A1]): Stream[E2, A | A1]
}
```

`||` is an alias for `orElse`:

```scala
import zio.blocks.streams._

val primary = Stream.fail("error")
// primary: Stream[String, Nothing] = Stream.fail(...)
val fallback = Stream.succeed(42)
// fallback: Stream[Nothing, Int] = Stream.succeed(...)
val result = (primary || fallback).runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(42))
```

### Recovering from Defects

`catchDefect[E1, A1]` — Catches untyped defects (exceptions not wrapped as typed errors) using a partial function.:

```scala
trait Stream[+E, +A] {
  def catchDefect[E1, A1](
    f: PartialFunction[Throwable, Stream[E1, A1]]
  ): Stream[E | E1, A | A1]
}
```

Use `catchDefect` when you need to handle unexpected exceptions that were not wrapped by `attempt`:

```scala
import zio.blocks.streams.*

val risky = Stream.die(new IllegalArgumentException("Not allowed"))
val safe = risky.catchDefect {
  case e: IllegalArgumentException => Stream.succeed(-1)
}
val result = safe.runCollect
```

## Resource Management

**The Problem:** Resources like files, database connections, and network sockets must be explicitly closed after use. If you just process them in a stream and forget to close, you leak resources. If an error occurs during processing, manual cleanup code might be skipped.

**The Solution:** ZIO Blocks streams provide three patterns for safe, automatic resource cleanup:

### `Stream.fromAcquireRelease[R, E, A]`

Acquires a resource, uses it in a stream, and **guarantees cleanup regardless of success or failure**:

```scala
object Stream {
  def fromAcquireRelease[R, E, A](
    acquire: => R,                                    // How to open the resource
    release: R => Unit = (r: R) =>                    // How to close it (defaults to .close())
      r match { 
        case ac: AutoCloseable => ac.close()
        case _ => ()
      }
  )(use: R => Stream[E, A]): Stream[E, A]
}
```

This is the fundamental pattern for safe resource handling:
1. **Acquire** — opens the resource (runs once, before streaming)
2. **Use** — streams elements from the resource
3. **Release** — closes the resource in a `finally` block (always runs, even on error)

Here's an example with automatic cleanup:

```scala
import zio.blocks.streams.*

case class DatabaseConnection(id: String) {
  def close(): Unit = println(s"Closing connection $id")
  def query(q: String): List[String] = List("result1", "result2")
}

val managed = Stream.fromAcquireRelease(
  acquire = {
    println("Opening database connection")
    DatabaseConnection("db-1")
  },
  release = _.close()  // Guaranteed to run even if streaming fails
)(conn => Stream.fromIterable(conn.query("SELECT *")))

val result = managed.runCollect
// Output:
// Opening database connection
// Closing database connection  <-- always happens
```

Even if the stream fails, cleanup runs:

```scala
import zio.blocks.streams.*

val managed = Stream.fromAcquireRelease(
  acquire = { println("Opening"); "resource" },
  release = { r => println(s"Closing $r") }
)(_ => Stream.fail("error occurred"))

val result = managed.runCollect
// Output:
// Opening
// Closing resource  <-- cleanup still runs even with error
// result: Either[String, Chunk[Nothing]] = Left("error occurred")
```

### `Stream.fromResource[R, E, A]`

Uses a ZIO Blocks `Resource[R]` (more abstract, composable resource type) within a stream:

```scala
object Stream {
  def fromResource[R, E, A](resource: Resource[R])(use: R => Stream[E, A]): Stream[E, A]
}
```

Use `fromResource` when you already have a `Resource` value, or when you need resource composition. The resource is acquired at stream start and released when the stream terminates:

```scala
import zio.blocks.streams.*
import zio.blocks.scope.Resource

val resource = Resource.acquireRelease(acquire = {
  println("Acquiring resource")
  42
})(release = { value =>
  println(s"Releasing resource with value: $value")
})

val stream = Stream.fromResource(resource) { value =>
  Stream(value, value * 2, value * 3)
}

val result = stream.runCollect
```

### `Stream#ensuring`

Adds a **cleanup action to any stream**, regardless of how it is created. The finalizer runs in a `finally` block:

```scala
trait Stream[+E, +A] {
  def ensuring(finalizer: => Unit): Stream[E, A]
}
```

Use `ensuring` for simple cleanup tasks that don't fit the acquire-release pattern:

```scala
import zio.blocks.streams.*

val stream = Stream(1, 2, 3)
  .ensuring {
    println("Stream finished (success or error)")
  }

val result = stream.runCollect
```

The finalizer always runs, in a `finally` block:

```scala
import zio.blocks.streams.*

val managed = Stream(1, 2, 3)
  .ensuring { println("Cleaned up") }

val result = managed.runCollect
```

## Running Streams

All terminal operations are synchronous and return `Either[E, Z]`. The error type is the union of the stream's error type and any sink-specific error type.

### Collecting Results

These operations accumulate or examine stream results, running the entire stream to completion:

#### `Stream#runCollect`

Collects all elements into a `Chunk[A]`:

```scala
trait Stream[+E, +A] {
  def runCollect: Either[E, Chunk[A]]
}
```

This is the most common terminal operation for extracting results:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5)
// nums: Stream[Nothing, Int] = Stream(1, 2, 3, 4, 5)
val result = nums.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(1, 2, 3, 4, 5))
// result is Right(Chunk(1, 2, 3, 4, 5))
```

#### `Stream#run[E2, Z]`

Runs the stream with a custom sink, producing result `Z`:

```scala
trait Stream[+E, +A] {
  def run[E2 >: E, Z](sink: Sink[E2, A, Z]): Either[E2, Z]
}
```

Use `run` when you need a specialized sink operation:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5)
// nums: Stream[Nothing, Int] = Stream(1, 2, 3, 4, 5)
val sum = nums.run(Sink.foldLeft(0)((acc, x) => acc + x))
// sum: Either[Nothing, Int] = Right(15)
// sum is Right(15)
```

### Discarding Results

These operations consume streams without collecting their elements, useful when you only care about side effects:

#### `Stream#runDrain`

Consumes all elements and discards them, returning `Unit`:

```scala
trait Stream[+E, +A] {
  def runDrain: Either[E, Unit]
}
```

Use `runDrain` when you only care about side effects:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3)
// nums: Stream[Nothing, Int] = Stream(1, 2, 3)
val sideEffect = nums.tapEach(x => println(s"Processing $x"))
// sideEffect: Stream[Nothing, Int] = Stream(1, 2, 3).map(...)
val result = sideEffect.runDrain
// Processing 1
// Processing 2
// Processing 3
// result: Either[Nothing, Unit] = Right(())
```

#### `Stream#runForeach`

Applies a function to each element for side effects:

```scala
trait Stream[+E, +A] {
  def runForeach(f: A => Unit): Either[E, Unit]
}
```

Alias `foreach` also exists:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3)
val result = nums.foreach(x => println(s"Got: $x"))
```

### Aggregations

These operations reduce streams to single values, aggregating elements into results:

#### `Stream#runFold[Z]`

Folds all elements using an accumulator, returning the final result:

```scala
trait Stream[+E, +A] {
  def runFold[Z](z: Z)(f: (Z, A) => Z): Either[E, Z]
}
```

This is the most general aggregation, equivalent to `reduce` or `fold` on eager sequences:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4)
// nums: Stream[Nothing, Int] = Stream(1, 2, 3, 4)
val sum = nums.runFold(0)(_ + _)
// sum: Either[Nothing, Int] = Right(10)
```

Specialized overloads for primitives avoid boxing:

```scala
def runFold(z: Int)(f: (Int, A) => Int): Either[E, Int]
def runFold(z: Long)(f: (Long, A) => Long): Either[E, Long]
def runFold(z: Double)(f: (Double, A) => Double): Either[E, Double]
```

#### `Stream#count`

Returns the number of elements:

```scala
trait Stream[+E, +A] {
  def count: Either[E, Long]
}
```

Counting elements in a stream:

```scala
import zio.blocks.streams.*

val nums = Stream(10, 20, 30, 40, 50)
// nums: Stream[Nothing, Int] = Stream(10, 20, 30, 40, 50)
val total = nums.count
// total: Either[Nothing, Long] = Right(5L)
```

#### `Stream#head`

Returns the first element (or `None` if empty):

```scala
trait Stream[+E, +A] {
  def head: Either[E, Option[A]]
}
```

Getting the first element without collecting the entire stream:

```scala
import zio.blocks.streams.*

val nums = Stream(10, 20, 30, 40, 50)
// nums: Stream[Nothing, Int] = Stream(10, 20, 30, 40, 50)
val first = nums.head
// first: Either[Nothing, Option[Int]] = Right(Some(10))
```

#### `Stream#last`

Returns the last element (or `None` if empty):

```scala
trait Stream[+E, +A] {
  def last: Either[E, Option[A]]
}
```

Getting the final element of the stream:

```scala
import zio.blocks.streams.*

val nums = Stream(10, 20, 30, 40, 50)
// nums: Stream[Nothing, Int] = Stream(10, 20, 30, 40, 50)
val last = nums.last
// last: Either[Nothing, Option[Int]] = Right(Some(50))
```

#### `Stream#find[A]`

Returns the first element satisfying a predicate:

```scala
trait Stream[+E, +A] {
  def find(pred: A => Boolean): Either[E, Option[A]]
}
```

Finding the first element matching a condition:

```scala
import zio.blocks.streams.*

val nums = Stream(10, 20, 30, 40, 50)
// nums: Stream[Nothing, Int] = Stream(10, 20, 30, 40, 50)
val firstEven = nums.find(_ % 2 == 0)
// firstEven: Either[Nothing, Option[Int]] = Right(Some(10))
```

#### `Stream#exists[A]`

Returns `true` if any element satisfies a predicate, short-circuiting:

```scala
trait Stream[+E, +A] {
  def exists(pred: A => Boolean): Either[E, Boolean]
}
```

Checking if any element is greater than 35:

```scala
import zio.blocks.streams.*

val nums = Stream(10, 20, 30, 40, 50)
// nums: Stream[Nothing, Int] = Stream(10, 20, 30, 40, 50)
val hasLargeValue = nums.exists(_ > 35)
// hasLargeValue: Either[Nothing, Boolean] = Right(true)
```

#### `Stream#forall[A]`

Returns `true` if all elements satisfy a predicate, short-circuiting:

```scala
trait Stream[+E, +A] {
  def forall(pred: A => Boolean): Either[E, Boolean]
}
```

Checking if all elements are positive:

```scala
import zio.blocks.streams.*

val nums = Stream(10, 20, 30, 40, 50)
// nums: Stream[Nothing, Int] = Stream(10, 20, 30, 40, 50)
val allPositive = nums.forall(_ > 0)
// allPositive: Either[Nothing, Boolean] = Right(true)
```

## Integration with Pipeline and Sink

Streams compose with pipelines and sinks to form complete data processing flows:

### Using Pipelines

`via[B]` — Applies a `Pipeline[A, B]` transformation to the stream.:

```scala
trait Stream[+E, +A] {
  final def via[B](pipe: Pipeline[A, B]): Stream[E, B]
}
```

Pipelines are composable transformations that can be reused across streams and sinks. Common pipelines include `Pipeline.map`, `Pipeline.filter`, `Pipeline.take`, and `Pipeline.drop`:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5)
// nums: Stream[Nothing, Int] = Stream(1, 2, 3, 4, 5)
val pipe = Pipeline.filter((x: Int) => x > 2).andThen(Pipeline.map(_ * 10))
// pipe: Pipeline[Int, Int] = zio.blocks.streams.Pipeline$Composed@60361815
val result = nums.via(pipe).runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(30, 40, 50))
```

Pipelines are useful when you want to build reusable transformation logic:

```scala
import zio.blocks.streams.*

def positiveIntsPipe: Pipeline[Int, Int] =
  Pipeline.filter((x: Int) => x > 0)

val mixed = Stream(-2, -1, 0, 1, 2)
// mixed: Stream[Nothing, Int] = Stream(-2, -1, 0, 1, 2)
val positives = mixed.via(positiveIntsPipe)
// positives: Stream[Nothing, Int] = Stream(-2, -1, 0, 1, 2).filter(...)
val result = positives.runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(1, 2))
```

### Understanding Sinks

A `Sink[+E, -A, +Z]` is a consumer of elements of type `A` that produces a result `Z` or fails with `E`. Sinks are contravariant in `A` (they can accept a supertype of what they expect). Common sinks include:

- `Sink.collectAll: Sink[Nothing, A, Chunk[A]]` — collects all elements
- `Sink.drain: Sink[Nothing, A, Unit]` — discards all elements
- `Sink.count: Sink[Nothing, Any, Long]` — counts elements
- `Sink.foldLeft: Sink[Nothing, A, Z]` — folds elements with an accumulator
- `Sink.head: Sink[Nothing, A, Option[A]]` — takes the first element
- `Sink.foreach: Sink[Nothing, A, Unit]` — applies a function to each element

When you call `stream.run(sink)`, the stream is compiled to a `Reader` and the sink drains it, consuming all elements and producing the result.

## Low-Level Pull with Reader

`Reader[+Elem]` is the low-level, pull-based source that backs every stream at execution time. Most users never interact with `Reader` directly — it is the compilation target when a stream runs. However, you can open a stream for manual element-by-element pulling using `start` with a `Scope`.

### Manual Pull via `start`

`start` — Opens a stream for manual pulling within a `Scope`. The reader is closed automatically when the scope exits.:

```scala
trait Stream[+E, +A] {
  def start(using scope: Scope): scope.$[Reader[A]]
}
```

Use `start` to manually pull elements within a resource scope:

```scala title="streams-examples/src/main/scala/stream/ManualPullUsingStart.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stream

import zio.blocks.streams.*
import zio.blocks.streams.io.Reader
import zio.blocks.scope.*

object ManualPullUsingStart extends App {
  Scope.global.scoped { scope =>
    import scope.*

    // Open a stream for manual pulling
    val reader: $[Reader[Int]] = Stream.range(1, 6).start(using scope)

    $(reader) { r =>
      // Iterate through reader values using the protocol directly
      // (cannot use scoped value in nested function, so use it directly)
      var current = r.read(-1)
      while (current != -1) {
        println(current) // prints 1, 2, 3, 4, 5
        current = r.read(-1)
      }
    }
    // reader is closed automatically when scope exits
  }
}
```

Use `Stream#start` when you need element-by-element control rather than running through a Sink. The returned Reader is closed automatically when the scope closes.

### The Reader Protocol

The pull protocol uses a **sentinel value** to signal end-of-stream:

- `read(sentinel)` — returns the next element, or `sentinel` when exhausted
- `close()` — signals the consumer is done
- `isClosed` — checks whether the reader is closed

For primitive types, specialized methods avoid boxing:

- `readInt(sentinel: Long): Long`
- `readLong(sentinel: Long): Long`
- `readFloat(sentinel: Double): Double`
- `readDouble(sentinel: Double): Double`

:::note
Avoid holding references to a `Reader` obtained via `start` outside its `Scope`. The scope guarantees cleanup; escaping the reader defeats that guarantee.
:::

## Implementation Notes

ZIO Blocks Streams achieves zero-boxing via compile-time type detection and dual compilation strategies:

### JVM Primitive Specialization

By default, Scala's type system boxes primitive values (Int, Long, Double, etc.) into objects, which wastes memory and is slower. ZIO Blocks' `Stream` uses `JvmType.Infer[A]` (a compile-time implicit) to detect primitive types at compile time and dispatch to unboxed, specialized implementations.

For example, `Stream#map`, `Stream#filter`, and `Stream#scan` all have specialized branches for `JvmType.Int` that use `readInt(Long.MinValue)` instead of boxing:

```scala
if (jvmType eq JvmType.Int) {
  val i = source.readInt(Long.MinValue)(using unsafeEvidence)
  // ... unboxed, fast path
} else {
  val o = reader.read(EndOfStream)  // generic boxed path
  // ...
}
```

This optimization is transparent: you write normal, high-level code, and the compiler and runtime automatically use the fast path for primitives.

### Dual Compilation: Recursive vs Interpreter

Each stream node compiles in two ways:

1. **Recursive (`compile`)**: Builds a tree of `Reader` objects, where each operation wraps the previous one. This is fast for shallow pipelines (< 100 operations).

2. **Flat-Array Interpreter (`compileInterpreter`)**: For deep pipelines (> 100 operations), the recursive approach hits Scala's default stack-depth limit (~100) and risks `StackOverflowError`. Instead, the interpreter compiles the entire pipeline into a flat array of operations, executed iteratively.

The switch happens at `DepthCutoff = 100`. You should never see this in normal use, but it ensures that pipelines of any depth are safe.

## Running the Examples

All code from this guide is available as runnable examples in the `schema-examples` module.

Clone the repository and navigate to the project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt.** Here are the available examples:

---

### Basic Usage

This example demonstrates constructing streams from collections, transforming elements with `Stream#map` and `Stream#filter`, and collecting results:

```scala title="streams-examples/src/main/scala/stream/StreamBasicUsageExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stream

import zio.blocks.streams.Stream
import zio.sbt.ExprEval.show

object StreamBasicUsageExample extends App {
  println("=== Stream Basic Usage ===\n")

  // Construction from values
  println("1. Creating a stream from values:")
  val nums = Stream(1, 2, 3, 4, 5)
  show(nums.runCollect)

  // Map transformation
  println("\n2. Transforming with map:")
  val doubled = Stream(1, 2, 3).map(_ * 2)
  show(doubled.runCollect)

  // Filter operation
  println("\n3. Filtering elements:")
  val evens = Stream(1, 2, 3, 4, 5, 6).filter(_ % 2 == 0)
  show(evens.runCollect)

  // Chaining operations
  println("\n4. Chaining multiple operations:")
  val result = Stream(1, 2, 3, 4, 5)
    .map(_ * 2)
    .filter(_ > 4)
    .runCollect
  show(result)

  // Count operation
  println("\n5. Counting elements:")
  val count = Stream(1, 2, 3, 4, 5).count
  show(count)

  // Take operation (short-circuiting)
  println("\n6. Taking first n elements (short-circuits):")
  val first3 = Stream.range(0, 1000).take(3).runCollect
  show(first3)

  // Drop operation
  println("\n7. Dropping first n elements:")
  val afterDrop = Stream(1, 2, 3, 4, 5).drop(2).runCollect
  show(afterDrop)

  // Empty stream
  println("\n8. Working with empty streams:")
  val empty = Stream.empty.runCollect
  show(empty)

  // Concatenation
  println("\n9. Concatenating streams:")
  val combined = (Stream(1, 2) ++ Stream(3, 4)).runCollect
  show(combined)
}
```

To run this example:

```bash
sbt "streams-examples/runMain stream.StreamBasicUsageExample"
```

### Flat-Mapping Nested Streams

This example shows how `Stream#flatMap` sequences multiple streams and flattens the results:

```scala title="streams-examples/src/main/scala/stream/StreamFlatMapExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stream

import zio.blocks.streams.Stream
import zio.sbt.ExprEval.show

object StreamFlatMapExample extends App {
  println("=== Stream FlatMap and Nested Streams ===\n")

  // Basic flatMap
  println("1. Basic flatMap - expand each element into a stream:")
  val expanded = Stream(1, 2, 3).flatMap(x => Stream(x, x * 10))
  show(expanded.runCollect)

  // FlatMap with different stream sizes
  println("\n2. FlatMap with varying sizes:")
  val varySizes = Stream(1, 2, 3).flatMap(x => Stream.range(0, x))
  show(varySizes.runCollect)

  // FlatMap with string expansion
  println("\n3. Expanding into string streams:")
  val ids          = Stream("a", "b")
  val expanded_ids = ids.flatMap(id => Stream(s"${id}_1", s"${id}_2", s"${id}_3"))
  show(expanded_ids.runCollect)

  // FlattenAll for deeply nested streams
  println("\n4. Flattening nested streams with flattenAll:")
  val nested = Stream(
    Stream(1, 2),
    Stream(3, 4),
    Stream(5, 6)
  )
  val flat = Stream.flattenAll(nested)
  show(flat.runCollect)

  // Sequential processing guarantees
  println("\n5. Sequential processing (important for side effects and resources):")
  var order   = scala.collection.mutable.Buffer[String]()
  val tracked = Stream(1, 2, 3).flatMap { x =>
    order += s"expand($x)"
    Stream(x, x + 100).tapEach(y => order += s"emit($y)")
  }
  val _ = tracked.runCollect
  show(order.toList)

  // FlatMap with error recovery
  println("\n6. FlatMap can propagate errors:")
  sealed trait Error
  case object InvalidId extends Error

  val mayFail = Stream(1, 2, -1, 3).flatMap { x =>
    if (x < 0) Stream.fail(InvalidId)
    else Stream(x, x * 2)
  }
  show(mayFail.runCollect)
}
```

Run this example:

```bash
sbt "streams-examples/runMain stream.StreamFlatMapExample"
```

### Error Handling

This example demonstrates typed error recovery with `fail`, `catchAll`, and `orElse`:

```scala title="streams-examples/src/main/scala/stream/StreamErrorHandlingExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stream

import zio.blocks.streams.Stream
import zio.sbt.ExprEval.show

object StreamErrorHandlingExample extends App {
  println("=== Stream Error Handling ===\n")

  sealed trait ApiError
  case object NotFound                    extends ApiError
  case class ValidationError(msg: String) extends ApiError
  case class ServerError(code: Int)       extends ApiError

  // Basic fail
  println("1. Creating a failing stream:")
  val failed: Stream[ApiError, String] = Stream.fail(NotFound)
  show(failed.runCollect)

  // catchAll for recovery
  println("\n2. Recovering from errors with catchAll:")
  val recovered = Stream.fail(NotFound).catchAll(_ => Stream.succeed("default-value"))
  show(recovered.runCollect)

  // orElse for recovery
  println("\n3. Using orElse (lazy fallback evaluation):")
  val fallback = Stream.fail(NotFound) || Stream(1, 2, 3)
  show(fallback.runCollect)

  // Error transformation with error-producing flatMap
  println("\n4. Producing typed errors in flatMap:")
  val errorExample = Stream(1, 2, 3, 4).flatMap { x =>
    if (x == 3) Stream.fail[ApiError](ValidationError("cannot process"))
    else Stream(x)
  }
  show(errorExample.runCollect)

  // Handling errors in flatMap chains
  println("\n5. Error handling in flatMap chains:")
  val chain = Stream(1, 2, 3, 4).flatMap { x =>
    if (x == 3) Stream.fail(ValidationError(s"Cannot process $x"))
    else Stream(x * 10)
  }
  show(chain.runCollect)

  // Recovering from errors in flatMap
  println("\n6. Recovering from errors with catchAll in chains:")
  val recovered_chain = Stream(1, 2, 3, 4).flatMap { x =>
    if (x == 3) Stream.fail(ValidationError(s"Cannot process $x"))
    else Stream(x * 10)
  }
    .catchAll(_ => Stream.succeed(-1))

  show(recovered_chain.runCollect)

  // Handling typed errors from attempt
  println("\n7. Recovering typed errors from Stream.attempt with catchAll:")
  val risky = Stream.attempt("not-a-number".toInt)
  val safe  = risky.catchAll { case _: NumberFormatException =>
    Stream.succeed(-1)
  }
  show(safe.runCollect)

  // Multiple error branches
  println("\n8. Distinguishing error types in recovery:")
  val multi_errors = Stream(1, 2, 3, 4).flatMap { x =>
    x match {
      case 2 => Stream.fail(NotFound)
      case 3 => Stream.fail(ValidationError("Invalid data"))
      case _ => Stream(x * 10)
    }
  }.catchAll {
    case NotFound             => Stream("missing")
    case ValidationError(msg) => Stream(s"invalid: $msg")
    case _                    => Stream("unknown error")
  }

  show(multi_errors.runCollect)
}
```

Run this example:

```bash
sbt "streams-examples/runMain stream.StreamErrorHandlingExample"
```

### Resource Management

This example shows how `fromAcquireRelease` and `ensuring` manage resources safely:

```scala title="streams-examples/src/main/scala/stream/StreamResourceExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stream

import zio.blocks.streams.Stream
import zio.sbt.ExprEval.show
import scala.collection.mutable.Buffer

object StreamResourceExample extends App {
  println("=== Stream Resource Management ===\n")

  // Simulated resource type
  case class Database(name: String) {
    private var closed = false

    def query(q: String): List[String] = {
      if (closed) throw new Exception("Database is closed")
      q match {
        case "users" => List("Alice", "Bob", "Charlie")
        case "ids"   => List("1", "2", "3")
        case _       => List()
      }
    }

    def close(): Unit = {
      println(s"  → Closing database: $name")
      closed = true
    }

    def isClosed: Boolean = closed
  }

  // Basic resource management
  println("1. Basic fromAcquireRelease - automatic cleanup:")
  val log = Buffer[String]()

  val managed = Stream.fromAcquireRelease(
    acquire = {
      log += "opened"
      Database("main")
    },
    release = { db =>
      db.close()
      log += "closed"
    }
  )(db => Stream.fromIterable(db.query("users")))

  val result1 = managed.runCollect
  show(result1)
  show(log.toList)

  // Ensuring cleanup
  println("\n2. Using ensuring for guaranteed cleanup:")
  log.clear()
  var finalizing = false

  val withEnsure = Stream(1, 2, 3)
    .tapEach(x => log += s"processing $x")
    .ensuring {
      finalizing = true
      log += "finalizing"
    }

  val result2 = withEnsure.runCollect
  show(result2)
  show(log.toList)
  show(finalizing)

  // Error safety
  println("\n3. Cleanup happens even on error:")
  log.clear()

  sealed trait Error
  case object ProcessingFailed extends Error

  val errorStream = Stream.fromAcquireRelease(
    acquire = {
      log += "opened"
      Database("error-test")
    },
    release = { db =>
      db.close()
      log += "closed"
    }
  )(db =>
    Stream(1, 2, 3).flatMap { x =>
      if (x == 2) Stream.fail(ProcessingFailed)
      else Stream(x)
    }
  )

  val result3 = errorStream.runCollect
  show(result3)
  show(log.toList)

  // Multiple nested resources
  println("\n4. Multiple nested resources with proper cleanup order:")
  log.clear()

  val nested = Stream.fromAcquireRelease(
    acquire = {
      log += "open db1"
      Database("db1")
    },
    release = db => {
      db.close()
      log += "close db1"
    }
  )(db1 =>
    Stream.fromAcquireRelease(
      acquire = {
        log += "open db2"
        Database("db2")
      },
      release = db2 => {
        db2.close()
        log += "close db2"
      }
    ) { db2 =>
      val data = db1.query("users") ++ db2.query("ids")
      Stream.fromIterable(data).tapEach(x => log += s"emit $x")
    }
  )

  val result4 = nested.runCollect
  show(result4)
  show(log.toList)

  // AutoCloseable integration
  println("\n5. Using AutoCloseable for simpler cleanup:")
  log.clear()

  class AutoCloseableDb extends AutoCloseable {
    def close(): Unit =
      log += "auto-closed"
  }

  val autoCloseable = Stream.fromAcquireRelease(
    acquire = {
      log += "acquired"
      new AutoCloseableDb
    }
    // release defaults to calling .close() on AutoCloseable
  )(db => Stream.succeed(42))

  val result5 = autoCloseable.runCollect
  show(result5)
  show(log.toList)
}

object X extends App {
  import zio.blocks.streams.*
  import java.io.*

  val charCount: Either[IOException, Long] =
    Stream
      .fromJavaReader(new StringReader("Hello\nWorld")) // lazily acquires reader
      .filter(!_.isWhitespace)                          // process only non-whitespace
      .count                                            // count all matching characters

  println(charCount) // prints Right(10) — count of non-whitespace characters
}
```

Run this example:

```bash
sbt "streams-examples/runMain stream.StreamResourceExample"
```

### Windowing and Scanning

This example demonstrates `grouped`, `sliding`, and `scan` for windowing and stateful transformations:

```scala title="streams-examples/src/main/scala/stream/StreamWindowingExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stream

import zio.blocks.streams.Stream
import zio.sbt.ExprEval.show

object StreamWindowingExample extends App {
  println("=== Stream Windowing and Stateful Transformations ===\n")

  // Grouped - fixed-size windows
  println("1. Grouping into fixed-size chunks:")
  val nums    = Stream(1, 2, 3, 4, 5, 6, 7)
  val grouped = nums.grouped(3)
  show(grouped.runCollect)

  // Grouped with incomplete last chunk
  println("\n2. Last chunk may be smaller:")
  val ungrouped = Stream(1, 2, 3, 4, 5).grouped(2)
  show(ungrouped.runCollect)

  // Sliding window
  println("\n3. Sliding window (default step = 1):")
  val sliding1 = Stream(1, 2, 3, 4, 5).sliding(3)
  show(sliding1.runCollect)

  // Sliding with custom step
  println("\n4. Sliding window with step > 1:")
  val sliding2 = Stream(1, 2, 3, 4, 5, 6, 7, 8).sliding(3, step = 2)
  show(sliding2.runCollect)

  // Scan - running aggregate
  println("\n5. Scan for running sum (accumulator pattern):")
  val cumsum = Stream(1, 2, 3, 4, 5).scan(0)(_ + _)
  show(cumsum.runCollect)

  // Scan for running product
  println("\n6. Scan for running product:")
  val cumprod = Stream(1, 2, 3, 4).scan(1)(_ * _)
  show(cumprod.runCollect)

  // MapAccum - accumulator + transform
  println("\n7. MapAccum for indexed transformation:")
  val indexed = Stream("a", "b", "c").mapAccum(0)((idx, x) => (idx + 1, (idx, x)))
  show(indexed.runCollect)

  // MapAccum with state structure
  println("\n8. MapAccum with complex state:")
  case class Stats(count: Int, sum: Int, max: Int)

  val stats = Stream(5, 3, 8, 2, 9).mapAccum(Stats(0, 0, Int.MinValue)) { case (s, x) =>
    (
      Stats(s.count + 1, s.sum + x, math.max(s.max, x)),
      (x, Stats(s.count + 1, s.sum + x, math.max(s.max, x)))
    )
  }
  show(stats.runCollect)

  // Combining windowing with filtering
  println("\n9. Windowing + filtering (only windows with sum > 5):")
  val filtered = Stream(1, 2, 3, 4, 5, 6)
    .sliding(3, step = 1)
    .filter(chunk => chunk.foldLeft(0)(_ + _) > 5)
  show(filtered.runCollect)

  // Chaining scan with other operations
  println("\n10. Scan + filter for conditional processing:")
  val conditional = Stream(1, 1, 2, 1, 1, 3)
    .scan(0)(_ + _)
    .filter(_ >= 3) // emit when cumsum >= 3
  show(conditional.runCollect)

  // Intersperse - useful with grouping
  println("\n11. Intersperse (insert separator between elements):")
  val separated = Stream(1, 2, 3).intersperse(0)
  show(separated.runCollect)

  // Grouped + intersperse for row formatting
  println("\n12. Grouped + intersperse for CSV-like output:")
  val rows = Stream(1, 2, 3, 4, 5, 6)
    .grouped(2)
    .map(chunk => chunk.toList.mkString(","))
    .intersperse("\n")
  show(rows.runCollect)
}
```

Run this example:

```bash
sbt "streams-examples/runMain stream.StreamWindowingExample"
```
