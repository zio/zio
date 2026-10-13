# Asynchronous Stream Execution

> How one Stream type describes synchronous and asynchronous pipelines, and the full async constructor, operator, and terminal API.

One `Stream[E, A]` describes both synchronous and asynchronous pipelines. There is no asynchronous stream type to convert to, no mode parameter to thread through your signatures, and no annotation that marks a description as one or the other. The API supports mixed synchronous and asynchronous stream composition without a second stream type or mode parameter, including dynamic inner streams and platform-specific materialization.

The terminals ending in `Async` are the cross-platform ones. They compile and run on the JVM and on Scala.js, and they are the family shared code should be written against. The blocking terminals (`run`, `runCollect`, `head`, `start`, and their siblings) still exist, but only on the JVM.

## Overview

The asynchronous surface is five things: constructors that produce a stream from an `Async`, element-level operators that take an `Async` callback, the `*Async` terminal family, one bounded-concurrency operator, and the platform adapters that turn native asynchronous I/O into a stream.

| Addition                   | Size                    | Documented in                                           |
|----------------------------|-------------------------|---------------------------------------------------------|
| Async source constructors  | 10 names / 14 overloads | [Async Source Constructors](#async-source-constructors) |
| Sequential async operators | 10 names                | [Async Operators](#async-operators)                     |
| Async terminals            | 12 names / 16 overloads | [Async Terminals](#async-terminals)                     |
| Manual-pull terminals      | 2 names                 | [Manual Pull and Ownership](#manual-pull-and-ownership) |
| Bounded concurrency        | 1 name (`mapParAsync`)  | [Bounded Concurrency](../core/stream.md#bounded-concurrency)  |
| The `Reader` union         | 2 subtypes              | [Reader](../primitives/reader.md)                                   |
| Platform I/O adapters      | JVM NIO and JS streams  | [Reader](../primitives/reader.md#from-native-asynchronous-sources)  |

Every asynchronous addition follows one naming convention: the synchronous name with `Async` appended. There is no `fromAsync` and no `asyncPush`.

## Dependency and Imports

The streams module carries the asynchronous effect type with it — `zio-blocks-streams` depends on `zio-blocks-async`, so one coordinate is all you add:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-streams" % "0.0.56"
```

Use `%%%` in a cross-built project so the same line resolves for both JVM and Scala.js; `%%` is enough for a JVM-only build.

Every snippet on this page assumes these imports:

```scala
import zio.blocks.streams._          // Stream, Sink, Pipeline, JvmType
import zio.blocks.streams.io.Reader  // Reader, Reader.SyncReader, Reader.AsyncReader
import zio.blocks.async._            // Async, Pollable, Completer, and the Async extension methods
import zio.blocks.chunk.Chunk
```

Importing `zio.blocks.async._` rather than `zio.blocks.async.Async` matters: `map`, `flatMap`, `block`, `either`, and the rest of the `Async` combinators are extension methods brought into scope by the package import.

## One Stream Type, Two Execution Modes

The central claim of this page is short: the type that decides between synchronous and asynchronous execution is `Reader`, not `Stream`.

`Stream[E, A]` is a description. Nothing in it runs until a terminal is driven, and at that moment the description is compiled into a `Reader[A]`. `Reader` is the union of a synchronous and an asynchronous kind, and that compilation is the only place the two modes part ways.

```
┌───────────────────────────────────────────────────────────────┐
│ Stream[E, A]  -  a description; nothing has run yet           │
└───────────────────────────────────────────────────────────────┘
       │  a terminal is driven
       ▼
┌───────────────────────────────────────────────────────────────┐
│ Stream.compile  -  compile the graph structurally             │
│ once, at materialization; never per element                   │
└───────────────────────────────────────────────────────────────┘
       │                            │
       │ every node compiles        │ any asynchronous node
       │ synchronously              │ is present
       │                            │
       ▼                            ▼
┌────────────────────────┐   ┌──────────────────────────────────┐
│ Reader.SyncReader[A]   │   │ Reader.AsyncReader[A]            │
│ read and close         │   │ read and close return Async;     │
│ return directly        │   │ sync stages lifted in place      │
└────────────────────────┘   └──────────────────────────────────┘
```

### Classification Happens at Compile Time

Compilation is structural and single-pass. `Stream#compile` is an abstract per-node method: each node compiles its upstream and returns a `Reader` directly, so a graph whose every node compiles synchronously yields a `SyncReader`, and a graph containing any asynchronous node yields an `AsyncReader`. Asynchronous operator nodes accept either upstream kind — an already-asynchronous upstream is extended through `AsyncInterpreter.transform`, and a synchronous one is lifted through `AsyncInterpreter.transformSync` — so a synchronous source needs no annotation to sit beneath an asynchronous stage.

Separately, and only on the JVM, a blocking terminal first tries to fuse the whole graph into the flat-array `SyncInterpreter`. Nine node types cannot be represented in that form and throw `AsyncBoundaryRequired`; the fallback then compiles the graph the ordinary way and converts the result back to a `SyncReader` at the terminal. That fusion is a performance path for blocking terminals, not the mechanism that decides between the two execution modes.

This decision is made **once, at materialization**. It is never made per element, and it is never made per pull. A stream that turns out to be entirely synchronous runs through the synchronous engine with no asynchronous machinery in the loop at all.

The same description can be materialized more than once, and each materialization classifies independently. Classification is a property of the graph, not of the value's type.

### The Reader Union

`Reader[+Elem]` is the root over two kinds:

```scala
abstract class Reader[+Elem] {
  def ++[Elem2 >: Elem](next: => Reader[Elem2]): Reader[Elem2]
  def concat[Elem2 >: Elem](next: () => Reader[Elem2]): Reader[Elem2]
  def concatAsync[Elem2 >: Elem](next: () => Async[Reader[Elem2]]): Reader.AsyncReader[Elem2]
  def withReleaseAsync(release: () => Async[Unit]): Reader.AsyncReader[Elem]
  def jvmType: JvmType
}
```

The root carries only kind-independent composition and one piece of metadata. Everything that actually pulls or closes lives on one of the two subtypes: `Reader.SyncReader[Elem]`, whose `read` and `close` return directly, and `Reader.AsyncReader[Elem]`, whose pull and lifecycle operations return `Async`.

A synchronous graph materializes as the former; a graph containing any asynchronous node materializes as the latter. See [Reader](../primitives/reader.md) for the full member list of both kinds, for how to implement a custom reader, and for `SyncReader#toAsync` and the JVM-only `AsyncReader#toSync`.

### Mixing Synchronous and Asynchronous Stages

When a synchronous source meets an asynchronous operator, the asynchronous node compiles to an `AsyncReader` and lifts its synchronous upstream through `AsyncInterpreter.transformSync`. Nothing in user code needs annotating, and no static type changes.

Composition widens. Two synchronous participants stay synchronous; a single asynchronous participant makes the result asynchronous:

```scala
import zio.blocks.streams.io.Reader
import zio.blocks.async._

val syncReader  = Reader.singleInt(1)
val asyncReader = Reader.singleInt(2).toAsync

val ss: Reader.SyncReader[Int]  = syncReader ++ Reader.singleInt(2)
val sa: Reader.AsyncReader[Int] = Reader.singleInt(1) ++ asyncReader
val as: Reader.AsyncReader[Int] = asyncReader ++ Reader.singleInt(3)
val aa: Reader.AsyncReader[Int] = asyncReader ++ Reader.singleInt(4).toAsync
```

At the stream level the same widening happens with no visible type at all. Adding one asynchronous stage to a synchronous pipeline leaves the annotation exactly as it was:

```scala
import zio.blocks.streams._
import zio.blocks.streams.io.Reader
import zio.blocks.async._

val syncOnly: Stream[Nothing, Int] =
  Stream.fromReader[Nothing, Int](Reader.fromIterable(List(1, 2, 3, 4, 5))).map(_ * 10)

val mixed: Stream[Nothing, Int] =
  syncOnly.filterAsync(i => Async.succeed(i > 20))
```

On the JVM a blocking terminal still accepts `mixed`: the asynchronous reader is converted back at the final boundary. On Scala.js, use an `*Async` terminal.

### Why Two Engines

The synchronous engine keeps its lane registers as stack locals inside a single loop. Stack locals cannot survive a suspension — the moment a callback returns a value that is not yet ready, the loop's frame has to unwind and there is nowhere for those registers to live. The asynchronous path is therefore a separate, heap-allocated engine that keeps the equivalent state in an object it can park and resume.

That is the whole reason classification exists. It is also the reason a purely synchronous stream pays nothing for the library's asynchronous support: a graph with no asynchronous node never touches the heap-allocated engine.

### There Is No Mode Annotation, and No Lane Diagnostic

Two things readers look for here, and will not find:

- **No type-level marker.** `Stream[E, A]` carries no phantom parameter, no `Sync`/`Async` tag, and no evidence that says which way a description will compile. You cannot write a signature that only accepts asynchronous streams, and you cannot ask a `Stream` value whether it will materialize asynchronously.
- **No public lane diagnostic.** A `Stream` exposes no lane of its own; `Reader#jvmType` reports the lane of a reader you already hold, which answers a narrower question than whether every fused stage preserved it. `JvmType.Infer` reports the static type, which is exactly the thing the representation machinery stopped trusting. See [Zero-Boxing Optimization](./zero-boxing.md) for what the lanes are and how one is chosen.

If you need to control the kind rather than observe it, use the union-preserving `Stream.fromReader` overloads below: they let you hand a specific reader kind to the stream.

## Async Source Constructors

These are the companion constructors that turn an `Async` into a stream. Each of them defers its thunk until the first reader operation is driven.

### `Stream.attemptAsync`

```scala
def attemptAsync[A](f: => Async[A])(implicit jtA: JvmType.Infer[A]): Stream[Throwable, A]
```

Lazily evaluates an asynchronous thunk once per materialization and emits its result. Non-fatal synchronous throws and asynchronous failures become typed errors; fatal throwables remain defects.

### `Stream.attemptEvalAsync`

```scala
def attemptEvalAsync(f: => Async[Any]): Stream[Throwable, Nothing]
```

Lazily executes an asynchronous effect once per materialization and emits nothing. Non-fatal synchronous throws and asynchronous failures become typed errors; fatal throwables remain defects. Use it for an effect whose result you do not want in the stream.

### `Stream.deferAsync`

```scala
def deferAsync(finalizer: => Async[Unit]): Stream[Nothing, Nothing]
```

Creates an empty stream that lazily registers an asynchronous release action. It is awaited exactly once when each materialization closes, including after failure, early termination, or cancellation; failure is a defect.

### `Stream.evalAsync`

```scala
def evalAsync(f: => Async[Any]): Stream[Nothing, Nothing]
```

Lazily executes an asynchronous effect once per materialization and emits nothing; synchronous throws and asynchronous failures are defects. This is `attemptEvalAsync` without the typed error channel.

### `Stream.fromAcquireReleaseAsync`

```scala
def fromAcquireReleaseAsync[R, E, A](
  acquire: => Async[R],
  release: R => Async[Unit]
)(use: R => Stream[E, A])(implicit jtA: JvmType.Infer[A]): Stream[E, A]
```

Lazily acquires one resource per materialization, constructs the stream with `use`, and awaits release exactly once after completion, failure, early termination, or cancellation — including cancellation during acquisition, once the resource is obtained. Acquisition, `use`, and release failures are defects.

### `Stream.fromIteratorAsync`

```scala
def fromIteratorAsync[A](it: => Async[Iterator[A]])(implicit jtA: JvmType.Infer[A]): Stream[Nothing, A]
```

Asynchronously obtains one iterator per materialization and consumes it in order. Acquisition and iterator failures are defects.

### `Stream.fromReaderAsync`

```scala
def fromReaderAsync[E, A](mkReader: => Async[Reader[A]])(implicit jtA: JvmType.Infer[A]): Stream[E, A]
```

Lazily runs `mkReader` once per materialization and closes the resulting reader when the stream closes. Effect and thunk failures are defects.

### `Stream.unfoldAsync`

```scala
def unfoldAsync[S, A](s: S)(f: S => Async[Option[(A, S)]])(implicit jtA: JvmType.Infer[A]): Stream[Nothing, A]
```

Lazily unfolds state sequentially, emitting each `A` and continuing with the paired state until `f` returns `None`. At most one callback is active at a time; callback failure is a defect.

### `Stream.unwrap`

```scala
def unwrap[E](stream: => Async[Stream[E, Nothing]])(implicit dummy: DummyImplicit): Stream[E, Nothing]
def unwrap[E, A](stream: => Async[Stream[E, A]])(implicit jtA: JvmType.Infer[A]): Stream[E, A]
```

Flattens an asynchronously produced stream. The effect is evaluated lazily once per materialization; effect failures are defects, while the produced stream retains its typed error channel. The `DummyImplicit` overload exists to preserve inference when the element type is `Nothing`.

`unwrap` is the idiom for feeding an asynchronously produced stream into an operator that has no asynchronous twin. `flatMap`, `catchAll`, `catchDefect`, `orElse`, and `flatMapPar` all compose with it unchanged:

```scala
import zio.blocks.streams._
import zio.blocks.async._

val stream: Stream[Nothing, Int] = Stream(1, 2, 3)

val flatMapped: Stream[Nothing, Long] =
  stream.flatMap(i => Stream.unwrap(Async.succeed(Stream(i.toLong))))

val recovered: Stream[String, Int] =
  Stream.fail[String]("failure").catchAll(_ => Stream.unwrap(Async.succeed(stream)))
```

### `Stream.fromReader`

Four overloads dispatch on the kind of reader you hand them, so the kind you chose is the kind the stream materializes:

```scala
def fromReader[E, A](mkReader: => Reader[A]): Stream[E, A]

def fromReader[E, A](mkReader: => Reader.SyncReader[A])(implicit
  dummy1: DummyImplicit,
  dummy2: DummyImplicit
): Stream[E, A]

def fromReader[E, A](mkReader: => Nothing)(implicit
  dummy1: DummyImplicit,
  dummy2: DummyImplicit,
  dummy3: DummyImplicit
): Stream[E, A]

def fromReader[E, A](mkReader: => Reader.AsyncReader[A])(implicit dummy: DummyImplicit): Stream[E, A]
```

Each overload lazily obtains one reader per materialization and closes it when the stream closes; reader-thunk failures are defects. The `Reader[A]` overload is documented as advanced: it is the union-preserving escape hatch, and it preserves whether the returned reader is synchronous or asynchronous. The `AsyncReader` overload awaits the reader's close.

### Laziness and Error Conventions

Two rules govern this whole family, and they are worth stating on their own because they are the two things most often assumed backwards.

**Laziness.** Compilation and materialization remain synchronous. Closing a stream before initialization neither invokes the thunk nor acquires a resource — an asynchronous constructor's effect starts only when the stream is first pulled. Do not compensate by eagerly opening a resource before constructing the stream.

**Errors.** Only the two `attempt*` constructors — `attemptAsync` and `attemptEvalAsync` — convert non-fatal callback failures into typed `Throwable` errors. Every other constructor's callback failure remains a defect, which fails the outer `Async` rather than appearing as a `Left`.

:::warning[A defect is not a typed error]
A defect does not surface in the `Either` that a terminal returns. It fails the surrounding `Async`, so a `match` on `Left`/`Right` will never see it. Reach for `attemptAsync` when you want a callback's failure in the `Left` channel.
:::

## Async Operators

These are the sequential, element-level twins of the synchronous operators. Each applies its callback to one element at a time, in order, with at most one invocation active.

### `Stream#mapAsync`

```scala
def mapAsync[B](f: A => Async[B])(implicit jtB: JvmType.Infer[B]): Stream[E, B]
```

Asynchronously transforms each element. Synchronous twin: [`map`](../core/stream.md#streammapb).

### `Stream#mapErrorAsync`

```scala
def mapErrorAsync[E2](f: E => Async[E2]): Stream[E2, A]
```

Asynchronously transforms the typed error channel. It runs only when the source fails with a typed error; callback failure is a defect. It genuinely changes the error type, so a `Stream[String, A]` can become a `Stream[Long, A]`. Synchronous twin: [`mapError`](../core/stream.md#streammaperrore2).

### `Stream#filterAsync`

```scala
def filterAsync(pred: A => Async[Boolean]): Stream[E, A]
```

Tests elements sequentially and emits those satisfying the asynchronous predicate, preserving order. Predicate failure is a defect. Synchronous twin: [`filter`](../core/stream.md#streamfilter).

### `Stream#collectAsync`

```scala
def collectAsync[B](f: A => Async[Option[B]])(implicit jtB: JvmType.Infer[B]): Stream[E, B]
```

Asynchronously transforms defined elements, dropping `None` results. Synchronous twin: [`collect`](../core/stream.md#streamcollectb).

### `Stream#mapAccumAsync`

```scala
def mapAccumAsync[S, B](init: S)(f: (S, A) => Async[(S, B)])(implicit jtB: JvmType.Infer[B]): Stream[E, B]
```

Asynchronously transforms elements while threading state sequentially. At most one invocation of `f` is active at a time. Synchronous twin: [`mapAccum`](../core/stream.md#stateful-transformations).

### `Stream#scanAsync`

```scala
def scanAsync[S](init: S)(f: (S, A) => Async[S])(implicit jtS: JvmType.Infer[S]): Stream[E, S]
```

Asynchronously emits the accumulator at each step, starting with `init`. The output stream has one more element than the input. Synchronous twin: [`scan`](../core/stream.md#stateful-transformations).

### `Stream#takeWhileAsync`

```scala
def takeWhileAsync(pred: A => Async[Boolean]): Stream[E, A]
```

Tests elements sequentially and emits them while the asynchronous predicate holds, then closes upstream at the first `false`. Predicate failure is a defect. Synchronous twin: [`takeWhile`](../core/stream.md#skipping-and-taking).

### `Stream#distinctByAsync`

```scala
def distinctByAsync[K](f: A => Async[K]): Stream[E, A]
```

Sequentially computes keys and emits the first element for each key, preserving order. Key state is per materialization and may grow without bound; asynchronous failures are defects. Synchronous twin: [`distinctBy`](../core/stream.md#streamdistinctbyk).

### `Stream#tapEachAsync`

```scala
def tapEachAsync(f: A => Async[Unit]): Stream[E, A]
```

Runs an asynchronous effect for each element and passes it through. Synchronous twin: [`tapEach`](../core/stream.md#other-operations).

### `Stream#ensuringAsync`

```scala
def ensuringAsync(finalizer: => Async[Unit]): Stream[E, A]
```

Registers an asynchronous finalizer lazily and awaits it exactly once when the materialized stream closes, including normal completion, failure, early termination, and cancellation. Finalizer failure is a defect. Synchronous twin: [`ensuring`](../core/stream.md#streamensuring).

For the one *concurrent* asynchronous operator, `mapParAsync`, see [Bounded Concurrency](../core/stream.md#bounded-concurrency).

## Async Terminals

A terminal is what drives a stream. The cross-platform family all ends in `Async` and all shares one return shape.

### The `Async[Either[E, Z]]` Convention

Every cross-platform terminal returns `Async[Either[E, Z]]`, never `Async[Z]`. The two channels are kept apart deliberately:

- The **typed error channel** `E` stays inside the `Either`. A stream that fails with a typed error still succeeds at the `Async` level: the `Async` completes normally, carrying `Left(e)`.
- `Async`'s own **untyped `Throwable` channel** is reserved for defects — a callback that threw, a finalizer that failed, a cleanup failure. These fail the outer `Async` and never appear as a `Left`.

This one rule explains the shape of every signature in this section:

```scala
import zio.blocks.streams._
import zio.blocks.chunk.Chunk
import zio.blocks.async._

val readings: Stream[String, Int] = Stream(12, 7, 30)

val collected: Async[Either[String, Chunk[Int]]] = readings.runCollectAsync

val described: Async[String] = collected.map(result =>
  result match {
    case Right(values) => s"collected ${values.length} readings"
    case Left(error)   => s"typed error: $error"
  }
)
```

### Collecting and Running

```scala
def runAsync[ES, E3, Z](sink: Sink[ES, A, Z])(implicit
  errorConcat: Concat.WithOut[E @uncheckedVariance, ES, E3]
): Async[Either[E3, Z]]

def runCollectAsync: Async[Either[E, Chunk[A]]]

def runDrainAsync: Async[Either[E, Unit]]

def runForeachAsync(f: A => Async[Unit]): Async[Either[E, Unit]]
```

`runAsync` is the general form: it runs the stream through the asynchronous drain of a `Sink`, and materialization and cleanup are lazy, cancellation-safe, and performed exactly once. Its error type is the concatenation of the stream's error type and the sink's, which is why the implicit `Concat` evidence appears.

`runCollectAsync` collects all elements in order. It requires memory proportional to the entire output and does not terminate for an infinite stream. `runDrainAsync` discards them. `runForeachAsync` applies an asynchronous callback to each element sequentially.

### Folding

`runFoldAsync` is a five-member family: four primitive accumulator lanes and one generic.

| Accumulator | Signature                                                  | Blocking `runFold` twin |
|-------------|------------------------------------------------------------|-------------------------|
| `Double`    | `runFoldAsync(z: Double)(f: (Double, A) => Async[Double])` | yes                     |
| `Float`     | `runFoldAsync(z: Float)(f: (Float, A) => Async[Float])`    | none                    |
| `Int`       | `runFoldAsync(z: Int)(f: (Int, A) => Async[Int])`          | yes                     |
| `Long`      | `runFoldAsync(z: Long)(f: (Long, A) => Async[Long])`       | yes                     |
| generic `Z` | `runFoldAsync[Z](z: Z)(f: (Z, A) => Async[Z])`             | yes                     |

The generic overload takes an implicit `JvmType.Infer[Z]`; the four primitive ones do not, and the overload is selected by the static type of `z`. Write `0L` rather than `0` when you want the `Long` lane.

The `Float` lane has no counterpart in the blocking `runFold` family, which offers only `Double`, `Int`, `Long`, and generic. It is new with the asynchronous terminals.

Each fold callback is applied sequentially, one element at a time.

### Queries

The query terminals are one-liners over `runAsync`. Knowing which `Sink` each delegates to tells you its semantics exactly:

| Terminal            | Returns                       | Delegates to             |
|---------------------|-------------------------------|--------------------------|
| `countAsync`        | `Async[Either[E, Long]]`      | `Sink.count`             |
| `existsAsync(pred)` | `Async[Either[E, Boolean]]`   | `Sink.existsAsync(pred)` |
| `findAsync(pred)`   | `Async[Either[E, Option[A]]]` | `Sink.findAsync(pred)`   |
| `forallAsync(pred)` | `Async[Either[E, Boolean]]`   | `Sink.forallAsync(pred)` |
| `foreachAsync(f)`   | `Async[Either[E, Unit]]`      | `runForeachAsync(f)`     |
| `headAsync`         | `Async[Either[E, Option[A]]]` | `Sink.head`              |
| `lastAsync`         | `Async[Either[E, Option[A]]]` | `Sink.last`              |

`existsAsync`, `findAsync`, and `forallAsync` take an `A => Async[Boolean]` predicate; `foreachAsync` is an alias for `runForeachAsync`. `countAsync`, `headAsync`, and `lastAsync` take no callback and therefore reuse the ordinary callback-free sinks, which drain a synchronous or an asynchronous reader alike.

### Blocking Twins Are JVM-only

Thirteen blocking members — `count`, `exists`, `find`, `forall`, `foreach`, `head`, `last`, `run`, `runCollect`, `runDrain`, `runFold`, `runForeach`, and `start` — live on the JVM only. Shared, cross-compiled sources cannot call them; they must use the `*Async` family, `startAsync`, and `useReaderAsync` instead.

For the full platform matrix, including which reader conversions and sink constructors exist on which platform, see [Platform Differences](./platform-differences.md).

### Driving an `Async` From a JVM `main`

An `Async[Either[E, Z]]` is a value. Something has to drive it, and on the JVM that something is `.block`:

```scala
import zio.blocks.streams._
import zio.blocks.chunk.Chunk
import zio.blocks.async._

val stream: Stream[String, Int] = Stream(1, 2, 3)

// At the edge of the world, and on the JVM only:
val result: Either[String, Chunk[Int]] = stream.runCollectAsync.block
```

`.block` drives the effect to its value, parking the calling thread until it is ready; a failure is re-thrown as its cause. A ready value returns immediately without parking.

This is the edge-of-the-world idiom, and it is JVM-only. Two rules keep it honest:

1. **`.block` belongs in `main`, or in a test, and nowhere else.** Never call it inside a stream callback or inside a `poll` — blocking the driver from within the loop it is driving deadlocks it.
2. **Scala.js code must not use it at all.** JavaScript cannot block, so unless the effect has already completed synchronously, `.block` throws an `IllegalStateException` there. Cross-platform code should keep the `Async` and hand it to the host: convert it at the boundary (for example with `toFuture`) and let the runtime drive it.

Inside an `Async.async { ... }` block, use the direct-style `.await` instead, which extracts the value without blocking. See [Async](../../async.md) for both.

### A Downstream Adopter: `Body`

`Body` in `http-model` is the clearest in-repo illustration of what the `*Async` convention looks like for a cross-platform consumer, because a body is exactly a `Stream[Nothing, Byte]` that someone eventually wants as bytes or as text.

Each blocking accessor has an asynchronous twin under the library-wide naming convention, five in all: `Body#toChunkAsync`, `Body#toArrayAsync`, `Body#asStringAsync`, `Body#asStringFromContentTypeAsync`, and `Body#textAsync`. The twins are the cross-platform API. The original accessors block, so they compile on Scala.js but throw `IllegalStateException` the moment the stream actually has to suspend — see [Why Blocking Terminals Are JVM-Only](./platform-differences.md#why-blocking-terminals-are-jvm-only). `Body#toChunk` is implemented in terms of the asynchronous one, taking a known-chunk fast path first and otherwise running `runCollectAsync` and blocking on the result.

Writing a cross-platform call site is the rename plus a change of result type:

```scala
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.http.Body

// Blocking: works on the JVM; on Scala.js this throws once the stream suspends
def bytesBlocking(body: Body): Chunk[Byte] = body.toChunk

// JVM and Scala.js
def bytes(body: Body): Async[Chunk[Byte]] = body.toChunkAsync
def text(body: Body): Async[String]       = body.textAsync
```

[Body](../../http-model/model.md#body) documents the type itself, its constructors, and the rest of its accessors.

## Manual Pull and Ownership

Sometimes you want the reader rather than a result — to interleave pulls with other work, or to hand the source to a protocol loop. Two terminals give you one, and they differ in exactly one respect: who is responsible for closing it.

### `Stream#startAsync`

```scala
def startAsync: Async[Reader.AsyncReader[A]]
```

Materializes this stream as a caller-owned asynchronous reader. **Ownership transfers to the caller**, who must drive the reader and await `close()`. This is the one place in the API where the library does not close what it opened — if you forget the `close()`, finalizers registered by `ensuringAsync`, `deferAsync`, and `fromAcquireReleaseAsync` never run.

### `Stream#useReaderAsync`

```scala
def useReaderAsync[Z](f: Reader.AsyncReader[A] => Async[Z]): Async[Z]
```

The scoped alternative. Ownership is **retained** by the library: the reader is passed to `f`, and its close is awaited on every outcome — success, typed failure, defect, and cancellation alike. Prefer it whenever the reader's lifetime is bounded by a single block of code.

Note the return type: `Async[Z]`, not `Async[Either[E, Z]]`. `useReaderAsync` hands you the reader, so whatever `f` produces is what you get back; stream errors surface through the reader's own pulls.

### One Active Operation Per Reader

An `AsyncReader` is a single-consumer cursor, not a concurrent work queue. **At most one operation may be in flight at a time**: await the `Async` returned by a `read`, `readAll`, `skip`, or `close` before beginning the next one.

Readers are not thread-safe either. Overlapping pulls, or driving one reader from two threads without external synchronization, is outside the contract — the reader's internal position and lifecycle state are not defended against it, and the result is not specified.

### Cleanup Failures

When cleanup fails on a path that has already failed, the cleanup failure is **attached to** the primary failure rather than replacing it. The original cause is what propagates; the cleanup cause is recorded as a suppressed exception on it.

This means a `Throwable` that reaches you from a failed `Async` may carry more than one story. Inspect `getSuppressed` before concluding that a close error was the only thing that went wrong.

## Cancellation

Cancellation in this library is **cooperative, never preemptive**. Cancelling signals the in-flight operation; it does not interrupt a thread, and it never waits for an in-flight `poll` to return.

Two pieces of the API matter here:

- **`Pollable#cancel()`** signals cancellation of the currently pending operation, and reaches the active leaf operation rather than stopping at the driver. Implementations that own cancellable work override `cancel()` with an idempotent, non-blocking signal; implementations without such work inherit the no-op. A running driver invokes it only when cancellation wins the race against completion.
- **`Async.Running#cancel(onCleanupFailure: Throwable => Unit)`** cancels a run and reports a failure from its asynchronous cleanup. The reporter is retained only when this cancellation wins completion, and it is invoked at most once. Use it when a cleanup failure during cancellation must not be lost.

Cancellation closes an acquired reader and awaits its finalizer. `startAsync` is the deliberate exception, because it has already transferred that responsibility to its caller.

See [Async](../../async.md) for `Pollable`, `Cancelable`, and `Async.Running` themselves.

## Resource Management

Two members carry resources through an asynchronous stream, and they compose with the ownership rules above.

`Stream.fromAcquireReleaseAsync` brackets a resource around a stream: one acquisition per materialization, and release awaited exactly once after completion, failure, early termination, or cancellation — including cancellation that arrives during acquisition, once the resource is obtained.

`Stream#ensuringAsync` registers a finalizer without a resource: awaited exactly once when the materialized stream closes, on every outcome.

Both are driven by the *close* of the materialized reader, which is what ties them to ownership:

- Under `runCollectAsync` and every other terminal, the library closes the reader, so both run without your involvement.
- Under `useReaderAsync`, the library still closes the reader, so both still run — on success and on failure alike.
- Under `startAsync`, **you** close the reader. Until you await `close()`, neither the release action nor the finalizer has run.

A failure inside a finalizer is a defect, and if the stream had already failed, that defect is attached to the primary failure rather than replacing it.

## How This Is Verified

The asynchronous execution path is covered by three-way differential equivalence: for each generated program, the *ready* execution, the *suspended* execution, and an independent reference model must agree on the result and on the materialized reader kind. The generated campaign is frozen at a fixed seed, and a separate sweep runs every physical lane against every logical terminal. A production run records only demand and callback counts; failure provenance, throwable order, ownership transitions, outstanding resources, epoch, and suppressed exceptions are computed by the reference model, and a hand-written scenario asserts them against it — that scenario is also the only one carrying a non-empty fault script, injecting a throw and a late close.

:::note[On allocation figures]
Near-zero allocation numbers observed for this path are profiler noise, not a promise. An allocation profiler is required before claiming that any particular stream program allocates nothing.
:::

## Running the Examples

Every example below is a runnable file in the `streams-examples` module. Clone the repository and run them with sbt:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

### Async Terminals and `.block`

Three terminals on one description, a fourth on a stream that fails with a typed error, the `Either` unwrapped on both branches, and `.block` confined to the edge of `main`:

```scala title="streams-examples/src/main/scala/stream/StreamAsyncTerminalsExample.scala" 
package stream

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.Stream

/**
 * The cross-platform asynchronous terminal family.
 *
 * Every `*Async` terminal returns `Async[Either[E, Z]]`: the typed error
 * channel stays in the `Either`, and `Async`'s own `Throwable` channel is
 * reserved for defects. `.block` drives the `Async` to its value and belongs
 * only here, at the edge of a JVM `main`.
 */
object StreamAsyncTerminalsExample {
  final case class Reading(sensor: String, celsius: Int)

  def main(args: Array[String]): Unit = {
    val readings: Stream[String, Reading] =
      Stream(Reading("north", 12), Reading("south", 7), Reading("east", 30))

    // One description, three terminals. Nothing has run yet.
    val collected: Async[Either[String, Chunk[Reading]]] = readings.runCollectAsync
    val total: Async[Either[String, Long]]               =
      readings.runFoldAsync(0L)((sum, reading) => Async.succeed(sum + reading.celsius))
    val first: Async[Either[String, Option[Reading]]] = readings.headAsync

    // A stream that fails with a typed error surfaces it as `Left`, not as a
    // failure of the outer `Async`.
    val offline: Stream[String, Reading]              = Stream.fail("west sensor is offline")
    val failed: Async[Either[String, Chunk[Reading]]] = offline.runCollectAsync

    // `.block` parks the calling thread until the value is ready. It is JVM
    // only: on Scala.js it throws, so keep the `Async` and let the host drive it.
    report("runCollectAsync", collected.block.map(_.length))
    report("runFoldAsync", total.block)
    report("headAsync", first.block.map(_.map(_.sensor)))
    report("runCollectAsync (failing)", failed.block.map(_.length))
  }

  private def report[Z](label: String, result: Either[String, Z]): Unit =
    result match {
      case Right(value) => println(s"$label -> $value")
      case Left(error)  => println(s"$label -> typed error: $error")
    }
}
```

Run it with:

```bash
sbt "streams-examples/runMain stream.StreamAsyncTerminalsExample"
```

### Ownership: `startAsync` Versus `useReaderAsync`

A finalizer that counts its own runs, proving that `useReaderAsync` closes on success *and* on failure, while `startAsync` closes only because the caller does it:

```scala title="streams-examples/src/main/scala/stream/StreamAsyncOwnershipExample.scala" 
package stream

import java.util.concurrent.atomic.AtomicInteger

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.Stream
import zio.blocks.streams.io.Reader

/**
 * Manual pull and close ownership.
 *
 * `startAsync` hands the reader to the caller, who must drive it and await
 * `close()`. `useReaderAsync` keeps ownership and awaits `close()` on every
 * outcome. An observable finalizer makes the difference visible.
 */
object StreamAsyncOwnershipExample {
  def main(args: Array[String]): Unit = {
    // startAsync: ownership transfers. Forget the `close()` and the finalizer
    // never runs.
    val startFinalized                  = new AtomicInteger
    val reader: Reader.AsyncReader[Int] = source(startFinalized).startAsync.block
    val started: Chunk[Int]             =
      try reader.readAll[Int]().block
      finally reader.close().block
    println(s"startAsync              -> $started, finalizer ran ${startFinalized.get()} time(s)")

    // useReaderAsync on success: ownership is retained, close is awaited.
    val useFinalized     = new AtomicInteger
    val used: Chunk[Int] = source(useFinalized).useReaderAsync(r => r.readAll[Int]()).block
    println(s"useReaderAsync          -> $used, finalizer ran ${useFinalized.get()} time(s)")

    // useReaderAsync on failure: close is awaited just the same.
    val failFinalized                         = new AtomicInteger
    val failed: Either[Throwable, Chunk[Int]] =
      source(failFinalized)
        .useReaderAsync[Chunk[Int]](_ => Async.fail(new RuntimeException("consumer gave up")))
        .either
        .block
    val message = failed.left.map(_.getMessage)
    println(s"useReaderAsync (failing) -> $message, finalizer ran ${failFinalized.get()} time(s)")
  }

  /** A stream carrying an asynchronous finalizer that counts its own runs. */
  private def source(finalized: AtomicInteger): Stream[Nothing, Int] =
    Stream(1, 2, 3).ensuringAsync(Async.succeed {
      finalized.incrementAndGet()
      ()
    })
}
```

Run it with:

```bash
sbt "streams-examples/runMain stream.StreamAsyncOwnershipExample"
```

### A Mixed Synchronous and Asynchronous Pipeline

A synchronous source, two asynchronous operators, and no change to any annotation:

```scala title="streams-examples/src/main/scala/stream/StreamMixedKindExample.scala" 
package stream

import zio.blocks.async._
import zio.blocks.streams.Stream
import zio.blocks.streams.io.Reader

/**
 * Mixing a synchronous source with an asynchronous operator.
 *
 * Adding an asynchronous stage changes no static type and requires no
 * annotation. The description is still `Stream[Nothing, Int]`; what changes is
 * the reader it compiles to, and that happens once, at materialization.
 */
object StreamMixedKindExample {
  def main(args: Array[String]): Unit = {
    // Entirely synchronous: a synchronous reader plus a synchronous callback.
    val syncOnly: Stream[Nothing, Int] =
      Stream.fromReader[Nothing, Int](Reader.fromIterable(List(1, 2, 3, 4, 5))).map(_ * 10)

    // The same source with one asynchronous stage appended. Note that the
    // annotation on the left is identical.
    val mixed: Stream[Nothing, Int] =
      syncOnly.filterAsync(i => Async.succeed(i > 20)).mapAsync(i => Async.succeed(i + 1))

    println(s"syncOnly -> ${syncOnly.runCollectAsync.block}")
    println(s"mixed    -> ${mixed.runCollectAsync.block}")

    // The JVM blocking terminal accepts the mixed graph too: the asynchronous
    // reader is converted back at the final boundary.
    println(s"mixed (blocking terminal) -> ${mixed.runCollect}")
  }
}
```

Run it with:

```bash
sbt "streams-examples/runMain stream.StreamMixedKindExample"
```

### A Composed Asynchronous Pipeline

Real suspension through a `Completer`, `Stream.unwrap` feeding `filterAsync`, `mapAsync`, and `ensuringAsync`, 33,000 nested stages to demonstrate that the asynchronous path is stack-safe, and an assertion that the finalizer runs exactly once:

```scala title="streams-examples/src/main/scala/stream/StreamAsyncOrderPipelineExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package stream

import java.util.concurrent.atomic.AtomicInteger

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.Stream

/** A composed asynchronous order-validation and audit pipeline. */
object StreamAsyncOrderPipelineExample {
  final case class Order(id: Int, amount: Int)

  def deferred[A](value: => A): Async[A] = {
    val result = new Completer[A]
    val thread = new Thread(() => result.succeed(value))
    thread.start()
    result
  }

  def main(args: Array[String]): Unit = {
    val finalized                         = new AtomicInteger
    val validated: Stream[Nothing, Order] = Stream
      .unwrap(deferred(Stream(Order(1, 20), Order(2, -1), Order(3, 40))))
      .filterAsync(order => Async.succeed(order.amount > 0))
      .mapAsync(order => deferred(order.copy(amount = order.amount + 5)))
      .ensuringAsync(Async.succeed { finalized.incrementAndGet(); () })

    // Real applications commonly assemble reusable generated middleware. Its
    // finite depth must not change the meaning of an identity transformation.
    val withMiddleware =
      (0 until 33000).foldLeft(validated)((orders, _) => orders.map(identity))

    val result = withMiddleware.runCollectAsync.block
    require(result == Right(Chunk(Order(1, 25), Order(3, 45))), s"unexpected result: $result")
    require(finalized.get() == 1, s"finalizer ran ${finalized.get()} times")
    println(result)
  }
}
```

Run it with:

```bash
sbt "streams-examples/runMain stream.StreamAsyncOrderPipelineExample"
```

## See Also

- [Reader](../primitives/reader.md) — the `SyncReader` / `AsyncReader` union, custom reader implementations, mixed-kind composition, and the JVM NIO and Scala.js `ReadableStream` adapters
- [Bounded Concurrency](../core/stream.md#bounded-concurrency) — `mapPar`, `mapParAsync`, `mergeAll`, and `flatMapPar`
- [Platform Differences](./platform-differences.md) — what exists on the JVM, what exists on Scala.js, and what throws
- [Async](../../async.md) — `Async[A]`, `Pollable`, `Completer`, `Async.Running`, and cancellation
- [Zero-Boxing Optimization](./zero-boxing.md) — primitive lanes, and why async is lane-aware rather than end-to-end allocation-free
