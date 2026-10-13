# Platform Differences: JVM and Scala.js

> Which stream members exist on the JVM, which exist on Scala.js, and the cross-platform replacement for every blocking terminal.

The terminals whose names end in `Async` are the cross-platform API: they compile and run on the JVM and on Scala.js alike. The blocking terminals — `Stream#run`, `Stream#runCollect`, `Stream#start` and their siblings — exist only on the JVM. Code that cross-builds is written against the `*Async` family, and there is no configuration flag, shim, or runtime fallback that changes that.

The split is a compile-time one. A Scala.js source that calls `Stream#runCollect` does not fail when it runs; it fails to compile, because that member does not exist on that platform.

## Availability Matrix

Nineteen members behave differently across the two platforms. The JVM column gives what the member evaluates to there, the Scala.js column gives what happens instead, and the last column names what to write in shared source:

| Member                      | JVM result                      | Scala.js                     | Cross-platform replacement                   |
|-----------------------------|---------------------------------|------------------------------|----------------------------------------------|
| `Stream#count`              | `Either[E, Long]`               | Absent — compile error       | `Stream#countAsync`                          |
| `Stream#exists`             | `Either[E, Boolean]`            | Absent — compile error       | `Stream#existsAsync`                         |
| `Stream#find`               | `Either[E, Option[A]]`          | Absent — compile error       | `Stream#findAsync`                           |
| `Stream#forall`             | `Either[E, Boolean]`            | Absent — compile error       | `Stream#forallAsync`                         |
| `Stream#foreach`            | `Either[E, Unit]`               | Absent — compile error       | `Stream#foreachAsync`                        |
| `Stream#head`               | `Either[E, Option[A]]`          | Absent — compile error       | `Stream#headAsync`                           |
| `Stream#last`               | `Either[E, Option[A]]`          | Absent — compile error       | `Stream#lastAsync`                           |
| `Stream#run`                | `Either[E3, Z]`                 | Absent — compile error       | `Stream#runAsync`                            |
| `Stream#runCollect`         | `Either[E, Chunk[A]]`           | Absent — compile error       | `Stream#runCollectAsync`                     |
| `Stream#runDrain`           | `Either[E, Unit]`               | Absent — compile error       | `Stream#runDrainAsync`                       |
| `Stream#runFold(z: Double)` | `Either[E, Double]`             | Absent — compile error       | `Stream#runFoldAsync(z: Double)`             |
| `Stream#runFold(z: Int)`    | `Either[E, Int]`                | Absent — compile error       | `Stream#runFoldAsync(z: Int)`                |
| `Stream#runFold(z: Long)`   | `Either[E, Long]`               | Absent — compile error       | `Stream#runFoldAsync(z: Long)`               |
| `Stream#runFold(z: Z)`      | `Either[E, Z]`                  | Absent — compile error       | `Stream#runFoldAsync(z: Z)`                  |
| `Stream#runForeach`         | `Either[E, Unit]`               | Absent — compile error       | `Stream#runForeachAsync`                     |
| `Stream#start`              | `scope.$[Reader.SyncReader[A]]` | Absent — compile error       | `Stream#startAsync`, `Stream#useReaderAsync` |
| `Sink.create`               | `Sink[E, A, Z]`                 | Absent — compile error       | `Sink.createAsync`, `Sink.createBoth`        |
| `Reader.AsyncReader#toSync` | `Reader.SyncReader[A]`          | Absent — compile error       | None; drive the `AsyncReader` itself         |
| `Async#block`               | `A`, parking the thread         | Present — throws at run time | `Async#map`, `Async#flatMap`, `Async.start`  |

Three details in that table repay a second look. The replacements that take a callback take an *effectful* one: `Stream#exists` takes `A => Boolean` while `Stream#existsAsync` takes `A => Async[Boolean]`, and the same shift applies to `Stream#findAsync`, `Stream#forallAsync`, `Stream#foreachAsync`, `Stream#runForeachAsync`, and every `Stream#runFoldAsync` overload. The asynchronous fold family is also one overload wider than the blocking one — `Stream#runFoldAsync(z: Float)` has no blocking twin, so the `Float` accumulator lane is reachable only through the cross-platform API. And `Async#block` is the single row whose Scala.js cell says "run time" rather than "compile error", because it is declared in shared source and cannot be removed from the platform it cannot serve.

Porting a blocking call is a rename plus a change of result type, since every replacement wraps its answer in `Async`:

```scala
import zio.blocks.streams._
import zio.blocks.async._

// JVM only: Either[Nothing, Chunk[Int]]
val onTheJvm = Stream(1, 2, 3).map(_ * 2).runCollect

// JVM and Scala.js: Async[Either[Nothing, Chunk[Int]]]
val everywhere = Stream(1, 2, 3).map(_ * 2).runCollectAsync
```

[Asynchronous Stream Execution](./async-execution.md) documents the full `*Async` surface, including the [`Async[Either[E, Z]]` convention](./async-execution.md#the-asynceithere-z-convention) that the second line above returns.

## Why Blocking Terminals Are JVM-Only

A blocking terminal makes one promise: when it returns, the pipeline has finished and the answer is in hand as an `Either`. On the JVM that promise is kept by parking the calling thread until the pipeline completes, and some other thread does the completing.

JavaScript has no other thread. Its single execution thread is also the thread that would have to run the callbacks that deliver the result, so a terminal that waited for completion would prevent the completion it waits for. There is no implementation of `Stream#runCollect` on Scala.js that both blocks and terminates.

That leaves two ways to express the constraint. The blocking terminals could stay in shared source and throw on Scala.js, or they could be removed from the Scala.js API surface entirely. The library removed them. The cost is that a cross-building source cannot always be compiled unchanged; the benefit is that the compiler reports it, at the call site, before anything ships.

`Async#block` is the one member that had to take the other route, because it is an extension method in shared `async` source with no stream terminal to hide behind. On Scala.js it returns normally when the effect has already completed, and throws `IllegalStateException` the moment a suspension actually has to wait — the message tells the caller to drive the `Pollable` from a non-blocking entry point instead.

## `StreamPlatformSpecific`

The sixteen relocated `Stream` members are not scattered through the platform trees. They live in one trait per platform, `StreamPlatformSpecific[+E, +A]`, which `Stream[E, A]` mixes in under a self-type:

```
┌────────────────────────────────────────────────────────────────┐
│ streams/shared   one Stream[E, A], one Sink, one Reader        │
│ every *Async terminal lives here and compiles on both          │
└────────────────────────────────┬───────────────────────────────┘
                                 │ mixed into Stream by self-type
                ┌────────────────┴────────────────┐
                ▼                                 ▼
┌──────────────────────────────┐  ┌──────────────────────────────┐
│ streams/jvm                  │  │ streams/js                   │
│ StreamPlatformSpecific:      │  │ StreamPlatformSpecific:      │
│   16 blocking members        │  │   empty trait, 0 members     │
│ Sink.create                  │  │ no Sink.create               │
│ AsyncReader#toSync           │  │ no AsyncReader#toSync        │
└──────────────────────────────┘  └──────────────────────────────┘
  a blocking terminal parks       a blocking terminal is not a
  a thread and returns Either     member: it fails to compile
```

On the JVM the trait carries the whole blocking family, each member delegating to a blocking run of a `Sink`:

```scala
trait StreamPlatformSpecific[+E, +A] { self: Stream[E, A] =>
  def count: Either[E, Long]
  def exists(pred: A => Boolean): Either[E, Boolean]
  def find(pred: A => Boolean): Either[E, Option[A]]
  def forall(pred: A => Boolean): Either[E, Boolean]
  def foreach(f: A => Unit): Either[E, Unit]
  def head: Either[E, Option[A]]
  def last: Either[E, Option[A]]
  def run[ES, E3, Z](sink: Sink[ES, A, Z])(implicit
    errorConcat: Concat.WithOut[E, ES, E3]
  ): Either[E3, Z]
  def runCollect: Either[E, Chunk[A]]
  def runDrain: Either[E, Unit]
  def runFold(z: Double)(f: (Double, A) => Double): Either[E, Double]
  def runFold(z: Int)(f: (Int, A) => Int): Either[E, Int]
  def runFold(z: Long)(f: (Long, A) => Long): Either[E, Long]
  def runFold[Z](z: Z)(f: (Z, A) => Z)(implicit jtZ: JvmType.Infer[Z]): Either[E, Z]
  def runForeach(f: A => Unit): Either[E, Unit]
  def start(implicit scope: Scope): scope.$[Reader.SyncReader[A]]
}
```

The Scala.js counterpart is the same trait name, the same type parameters, and the same self-type, with nothing inside it:

```scala
trait StreamPlatformSpecific[+E, +A] { self: Stream[E, A] => }
```

That empty body is the whole mechanism. `Stream` still mixes the trait in on Scala.js, so no type signature anywhere in shared source changes; the sixteen names simply have no definition to resolve to, and the compiler reports each one as "not a member".

## `Sink.create` and Custom Sinks

Custom sinks follow the same rule as terminals, because a custom sink's callback is handed a reader and a reader is where the two execution modes part company. `Sink.create` is declared in `SinkCompanionPlatformSpecific`, which the `Sink` companion extends, and it exists only in the JVM copy of that trait:

```scala
object Sink {
  // JVM only — the callback consumes a blocking SyncReader
  def create[E, A, Z](f: Reader.SyncReader[A] => Z): Sink[E, A, Z]

  // JVM and Scala.js
  def createAsync[E, A, Z](f: Reader.AsyncReader[A] => Async[Z]): Sink[E, A, Z]
  def createBoth[E, A, Z](
    sync: Reader.SyncReader[A] => Z,
    async: Reader.AsyncReader[A] => Async[Z]
  ): Sink[E, A, Z]
}
```

Note the parameter type of `Sink.create`: it is `Reader.SyncReader[A]`, not the wider `Reader[A]`. The callback is guaranteed a synchronous reader, and when a `Sink` built this way is driven from an asynchronous terminal the JVM implementation bridges by calling `Reader.AsyncReader#toSync` — the member that does not exist on Scala.js either.

To write the same aggregation for both platforms, use `Sink.createAsync` and sequence the reader's pulls with `Async#flatMap` instead of a loop:

```scala
import zio.blocks.streams._
import zio.blocks.streams.io.Reader
import zio.blocks.async._

val average: Sink[Nothing, Int, Double] =
  Sink.createAsync[Nothing, Int, Double] { reader =>
    def loop(sum: Long, count: Long): Async[(Long, Long)] =
      reader.readInt(Long.MinValue).flatMap { value =>
        if (value == Long.MinValue) Async.succeed((sum, count))
        else loop(sum + value, count + 1L)
      }

    loop(0L, 0L).map { case (sum, count) =>
      if (count == 0L) 0.0 else sum.toDouble / count
    }
  }
```

`Sink.createBoth` is the option to reach for when the synchronous drain is worth keeping: it takes both callbacks, and the terminal selects exactly one of them, so the JVM keeps its direct blocking loop — with no per-pull `Async` to allocate and resume — while Scala.js gets a working implementation. Both callbacks must agree on how much input they consume and what they produce. [Sink](../core/sink.md) documents the reader protocol these callbacks drive.

## Manual Pull Across Platforms

Handing the reader to a protocol loop rather than a sink runs into the same split, and here the replacement changes shape rather than just its name. On the JVM, `Stream#start` is `Scope`-based and yields a synchronous reader:

```scala
trait StreamPlatformSpecific[+E, +A] { self: Stream[E, A] =>
  def start(implicit scope: Scope): scope.$[Reader.SyncReader[A]]
}
```

The reader is allocated into the enclosing [`Scope`](../../resource-management/scope.md) as an acquire-release resource, so closing the scope closes the reader, and the dependent result type `scope.$[Reader.SyncReader[A]]` keeps it from escaping that scope. The return type is `Reader.SyncReader[A]`: `Stream#start` never hands back the `Reader` union, because asynchronous boundaries inside the pipeline are bridged by the JVM runtime before you see it.

The cross-platform pair is `Stream#startAsync` and `Stream#useReaderAsync`, and they differ from `Stream#start` and from each other in who closes the reader:

```scala
abstract class Stream[+E, +A] {
  def startAsync: Async[Reader.AsyncReader[A]]
  def useReaderAsync[Z](f: Reader.AsyncReader[A] => Async[Z]): Async[Z]
}
```

`Stream#startAsync` transfers ownership to the caller, who must await `close()`; `Stream#useReaderAsync` retains it, passing the reader to `f` and awaiting its close on every outcome. The scoped one is the direct analogue of `Stream#start`, so it is the one to reach for when porting:

```scala
import zio.blocks.streams._
import zio.blocks.streams.io.Reader
import zio.blocks.async._
import zio.blocks.chunk.Chunk

val collected: Async[Chunk[Int]] =
  Stream(1, 2, 3).useReaderAsync[Chunk[Int]](reader => reader.readAll[Int]())
```

One constraint carries over from the asynchronous reader contract and has no synchronous counterpart: an `AsyncReader` allows at most one operation in flight at a time. Await each `Async` before beginning the next. [Manual Pull and Ownership](./async-execution.md#manual-pull-and-ownership) covers the ownership rules in full.

## Concurrency and Threading

Underneath the availability split sits a capability split: the JVM has threads and Scala.js does not. That difference decides how the concurrent operators execute, and how a suspended `Async` is resumed.

### The JVM

Concurrent stream operators run their workers on virtual threads when the runtime provides them. `Platform.startVirtualThread` obtains `Thread.ofVirtual()` reflectively, so the module compiles and runs against any supported JDK and uses virtual threads on JDK 21 and later; when the reflective lookup fails for any reason — an older JDK, a security restriction, a linkage error — it falls back to starting a named daemon platform thread rather than failing class initialization.

Workers are named, which makes them identifiable in a thread dump or profiler. The `Stream#mapPar` family names its workers `zio-blocks-mappar-worker-<n>-<index>` and its coordinator `zio-blocks-mappar-coordinator-<n>`, where `<n>` is drawn from a counter shared by every thread that lane's reader class starts and `<index>` identifies the worker within one reader.

### Scala.js

`Platform.supportsConcurrency` is `false` on Scala.js and `Platform.startVirtualThread` throws `UnsupportedOperationException`. The observable effect is that `Stream#mapPar`, `Stream#flatMapPar`, and `Stream.mergeAll` do not overlap any work: there is no second execution context for work to overlap on.

The mechanism is not what the scaladoc on those three operators says. They do not simply become `Stream#map` and `Stream#flatMap` on Scala.js. The factories that build the concurrent readers, `Platform.createMergeReaderFromReader` and `Platform.createMapParReaderFromReader`, route to the same shared concurrent engine on Scala.js as they do on the JVM — merge unconditionally, and `Stream#mapPar` whenever the upstream compiles to an asynchronous reader, with the mapping function wrapped in an immediately-ready effect:

```scala
internal.AsyncConcurrentReaders.mapPar(reader, n, (a: A) => zio.blocks.async.Async.succeed(f(a)), outType)
```

The genuinely sequential implementations exist only on the synchronous lane, and are reached when the pipeline compiles end to end to a `SyncReader`: there `Stream#mapPar` becomes a plain mapped reader and merge becomes a flat-mapped one. That single case is what the scaladoc describes, stated as if it were the whole story.

The consequence matters more than the plumbing. Wherever the concurrent engine runs, **unordered arrival is retained on Scala.js**. `Stream#mapPar` and `Stream#flatMapPar` are documented to emit in completion order rather than input order, and that stays true on a single-threaded platform. Do not write code that relies on Scala.js restoring source order.

:::warning[The scaladoc is stale here]
The scaladoc on `Stream#mapPar`, `Stream#flatMapPar`, and `Stream.mergeAll` states that on Scala.js each "degrades to sequential `map`" or "sequential `flatMap`". Treat that as a statement about throughput, not about ordering. Relying on it for element order is a bug that will not reproduce on the JVM.
:::

### The Async Execution Model

Under the streams layer, the `async` module resumes suspended computations differently on each platform. On the JVM, a suspended run executes as a serialized sequence of tasks on `ForkJoinPool.commonPool()`; only `Async.start(body)` spawns a thread of its own, a daemon thread named `zio-blocks-async-eval`; and `Async#block` parks the caller with `LockSupport` until the result arrives.

On Scala.js, resumptions are queued as microtasks with a `setTimeout(0)` macrotask escape hatch and a ready-resumption limit of 1024, so a long chain of already-complete steps yields to the event loop instead of starving it. `Async#block` throws, as described above. There is no blocking-operations API in the `async` module on either platform — nothing corresponding to a `blocking` executor or an `attemptBlocking` wrapper exists to be looked for.

[Async](../../async.md) documents both execution models in detail; this page states only the part that decides what compiles where.

## Platform Capabilities

The capability surface is deliberately small. `Platform` is a trait in shared source with one flag, one thread starter, and three reader factories, and the `Platform` object extends the platform-specific implementation of it, so call sites use the same `Platform.*` names regardless of platform:

```scala
trait Platform {
  def supportsConcurrency: Boolean
  def startVirtualThread(name: String, task: Runnable): Thread
  def createBufferedReaderFromReader[A](upstream: Reader[A], bufferSize: Int): Reader[A]
  def createMergeReaderFromReader[A](
    outerReader: Reader[?],
    maxOpen: Int,
    bufferSize: Int,
    elemType: JvmType
  ): Reader[A]
  def createMapParReaderFromReader[A, B](
    upstream: Reader[A],
    n: Int,
    f: A => B,
    bufferSize: Int,
    inType: JvmType,
    outType: JvmType
  ): Reader[B]
}

object Platform extends PlatformSpecific
```

Two `final` convenience methods, `Platform.createBufferedReader` and `Platform.createMapParReader`, narrow the corresponding factory's result to `Reader.SyncReader`. They add no capability of their own.

What `Platform` does *not* expose is worth stating, because it is the shape of query a cross-platform codebase tends to reach for first. There is no parallelism count, no executor or execution-context accessor, and no `isJS` flag. `Platform.supportsConcurrency` is the only capability flag, and the only supported way to ask at run time whether concurrent work will actually overlap.

## Enforcement

The placement of the blocking API is not maintained by convention; it is verified by a negative compilation test that asserts each blocking member is *absent* on Scala.js. Scala 3 asserts this in-band, using `scala.compiletime.testing.typeCheckErrors` to require that the compiler rejects each snippet with a "not a member" error:

```scala
private inline def missingMember(inline code: String, member: String): Boolean =
  typeCheckErrors(code).exists(error => error.message.contains(member) && error.message.contains("not a member"))
```

The spec covers `Reader.AsyncReader#toSync` (both directly and through wildcard imports, so a re-export cannot reintroduce it), `Stream#run`, `Stream#runCollect`, `Stream#start`, and `Sink.create`. Scala 2 has no `typeCheckErrors`, so those five references live in a fixture at `streams/js/src/test/negative-scala-2/BlockingApiPlacement.scala`, alongside two further references — `Writer#concatAsync` and `Writer#contramapAsync` — that name members `Writer` has on neither platform. That path sits outside the normal test source directories on purpose: it is not compiled by the ordinary Scala 2 test run, and no build task in this repository currently wires it in, so on Scala 2 the placement is documented by the fixture rather than enforced by it.

For the reader the point is a guarantee rather than a test detail: a cross-platform source that uses a blocking terminal fails at compile time on Scala.js. It will not build a Scala.js artifact that throws in a browser, and it will not pass a JVM build and then fail a JS one for a reason that only shows up under load.

## Scala 2 Versus Scala 3

There is no public-API difference between Scala 2.13 and Scala 3 in the streams module. Each version tree holds exactly two files, `LowPriorityJvmTypeInferPlatform.scala` and `internal/InternalVersionSpecific.scala`, and both declare `private[streams]` traits — implementation detail for `JvmType` inference and for primitive pull loops, invisible from user code.

The axis that changes what you can call is JVM versus Scala.js, which is what the [availability matrix](#availability-matrix) above records. [Scala 2 Compatibility](./scala-2-compatibility.md) covers the syntax differences that do affect how you write against the API, such as wildcard imports and implicit resolution.

## See Also

- [Asynchronous Stream Execution](./async-execution.md) — the full cross-platform `*Async` API
- [Reader](../primitives/reader.md#from-native-asynchronous-sources) — the JVM NIO and Scala.js `ReadableStream` adapters
- [Bounded Concurrency](../core/stream.md#bounded-concurrency) — `Stream#mapPar`, `Stream#flatMapPar`, `Stream.mergeAll`, and `Stream#mapParAsync`
- [Async](../../async.md) — the effect type and both execution models
