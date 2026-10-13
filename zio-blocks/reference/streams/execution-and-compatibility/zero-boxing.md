# Zero-Boxing Optimization

> How ZIO Blocks Streams picks a primitive lane, signals end of stream on each one, and where boxing still happens.

Working with streams of primitives presents a performance challenge in languages with generic types: **boxing**. Without special care, primitive values get wrapped in objects. ZIO Blocks Streams therefore carries runtime element-type information and provides primitive physical lanes for synchronous interpretation. This removes boxing at important boundaries and hot paths; it is not a promise that every stream program, callback, terminal, or asynchronous operation is allocation-free.

## The Boxing Problem

In Scala, primitive types (`Int`, `Long`, `Double`, `Boolean`) are fundamentally different from their object counterparts (`Integer`, `Long`, `Double`, `Boolean`). When a generic class like `Stream[E, A]` works with primitives, the compiler must box them into objects to satisfy the generic contract:

```scala
// Without optimization, this boxes each Int into an Integer object
val stream: Stream[Nothing, Int] = Stream(1, 2, 3, 4, 5)
val doubled = stream.map(_ * 2)  // Each Int is boxed → Integer → boxed result
val result = doubled.runCollect
// Result: Each element was boxed, unboxed, boxed again — wasteful!
```

**Performance cost:**
- Extra heap allocations (memory pressure, more GC)
- Cache misses (objects spread across memory)
- Slower CPU operations (dereferencing objects instead of primitive registers)

For high-throughput data processing, this overhead is unacceptable.

## ZIO Blocks Streams' Solution: JvmType Dispatch

Instead of using Scala's `@specialized` annotation (which generates separate classes for each primitive type, bloating binaries), ZIO Blocks Streams uses **compile-time type detection + runtime dispatch**. This gives you the speed of specialization without the binary bloat.

### How It Works

**Step 1: Compile-Time Detection**

When you create a stream of primitives, the compiler infers a `JvmType` implicit that identifies the element type:

```scala
import zio.blocks.streams.*

val intStream: Stream[Nothing, Int] = Stream(1, 2, 3)
// Compiler infers: JvmType.Infer[Int]
// The stream records its element representation

val doubled = intStream.map(_ * 2)
// map receives JvmType.Infer evidence for its transformed result type.
// Its input representation comes from intStream, not call-site evidence.
```

**Step 2: Runtime Type Dispatch**

Stream and pipeline nodes retain their logical `JvmType`. When a run materializes a reader, the drain loop reads that reader's own `jvmType` tag and branches once onto the matching primitive pull. The tag describes what the reader physically pulls, not what the static type was widened to, so a `Stream[Nothing, Int]` widened to `Stream[Nothing, AnyVal]` and then filtered is still pulled through the `Int` lane. [When Zero-Boxing Applies](#when-zero-boxing-applies) lists the lanes and [How a lane is chosen](#how-a-lane-is-chosen) covers when each half of the decision is made.

**Step 3: Unboxed Accessors**

Instead of a single `read()` method that returns boxed `Any` (where boxed means wrapping primitives in object wrappers like `Integer`, `Long`, `Double`), primitives use specialized accessors that operate directly on primitive values:

```scala
abstract class Reader[+Elem] {
  def jvmType: JvmType = JvmType.AnyRef
}

object Reader {
  abstract class SyncReader[+Elem] extends Reader[Elem] {
    def read[A >: Elem](sentinel: A): A
    def readBoolean(_sentinel: Int)(implicit _ev: Elem <:< Boolean): Int
    def readInt(_sentinel: Long)(implicit _ev: Elem <:< Int): Long
    def readDouble(_sentinel: Double)(implicit _ev: Elem <:< Double): Double
    def readDoubles(buf: Array[Double], offset: Int, maxLen: Int)(implicit ev: Elem <:< Double): Int
  }

  abstract class AsyncReader[+Elem] extends Reader[Elem] with AsyncReaderPlatform[Elem] {
    def read[A >: Elem](sentinel: A): Async[A]
    def readBoolean(_sentinel: Int)(implicit _ev: Elem <:< Boolean): Async[Int]
    def readInt(_sentinel: Long)(implicit _ev: Elem <:< Int): Async[Long]
    def readDouble(_sentinel: Double)(implicit _ev: Elem <:< Double): Async[Double]
    def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Elem <:< Double): Async[Int]
  }
}
```

The complete primitive pull surface distinguishes all eight identities: `Boolean`, `Byte`, `Short`, and `Char` use `Int` carriers; `Int` uses a widened `Long`; `Long` uses `Long`; `Float` uses a widened `Double`; and `Double` uses `Double`. The scalar reads name their sentinel parameter `_sentinel` because the base implementations spend it only on the reference path and reject it on a genuine primitive lane, which primitive readers override. `Long` and `Double` never spend it at all — those two lanes detect end of stream through the bulk reads instead, which is why `readDoubles` appears above beside its scalar twin. [EOF signalling per lane](#eof-signalling-per-lane) has the full table.

For `SyncReader`, the carrier is a JVM primitive at the method boundary. `SyncInterpreter` also stores primitive state in `Long`-backed physical arrays (using raw bits where necessary) and invokes lane-adapted operators. By contrast, `AsyncReader` returns generic `Async[T]` values. `AsyncInterpreter` keeps primitive lane state internally, but completed primitive results cross the generic async carrier and may be boxed — see [Async and boxing](#async-and-boxing).

## Practical Benefits

To understand the intended benefit, consider how boxing can accumulate through a pipeline. Compare a hypothetical boxed implementation with ZIO Streams' primitive-lane approach.

### Before (Hypothetical Boxed Streams)

Without optimization, each operation in a pipeline adds boxing overhead:

```scala
val nums = Stream(1, 2, 3, 4, 5)
val result = nums
  .map(_ * 2)        // boxes each Int → Integer, applies *, unboxes result
  .filter(_ > 5)     // boxes again, compares, unboxes
  .map(_ + 1)        // boxes, adds, unboxes
  .runCollect
// 5 elements × 3 operations × boxing overhead = significant waste
```

**Memory profile:** Each element is boxed/unboxed multiple times, creating temporary objects.

### With ZIO Streams (Primitive Lanes)

The synchronous interpreter can keep the same pipeline on primitive physical lanes:

```scala
import zio.blocks.streams.*

val nums = Stream(1, 2, 3, 4, 5)
val result = nums
  .map(_ * 2)
  .filter(_ > 5)
  .map(_ + 1)
  .runCollect
```

This design avoids per-stage primitive wrapper storage in the fused synchronous interpreter. It does **not** prove that the whole expression allocates nothing: generic Scala function interfaces, source or terminal construction, result collection, fallback paths, and JIT decisions can still introduce boxing or allocation. `runCollect` above is a JVM-only terminal; in cross-platform source write `runCollectAsync`, which drives the same primitive lanes and returns `Async[Either[E, Chunk[Int]]]`.

## When Zero-Boxing Applies

There are nine logical lanes: the eight JVM primitives — `Boolean`, `Byte`, `Char`, `Short`, `Int`, `Long`, `Float`, `Double` — and `AnyRef` for everything else. Each is a separate *physical pull identity*, because each has its own exact read method and its own end-of-stream convention. `readInt` is not an acceptable substitute for `Boolean`, `Byte`, `Char`, or `Short`, and a generic `read` followed by a cast is never an acceptable primitive pull.

Nine identities do not mean nine interpreter arrays. The interpreter compacts them into five storage lanes — int-like, `Long`, `Float`, `Double`, and reference — where the five small integral types (`Boolean`, `Byte`, `Char`, `Short`, `Int`) all land in the int-like lane. That compaction is visible in the arithmetic of the operator tags: a fused `map` is tagged `inLane * 5 + outLane`, twenty-five crossings rather than eighty-one. The read tag stays deliberately independent of the storage lane, so a `Short` stream is pulled by `readShort` and only afterwards shares int-like storage with an `Int` stream.

Primitive type inference and lane selection are automatic. Primitive streams are eligible for specialized synchronous paths, subject to the operation, source, terminal, and any fallback boundaries:

```scala
import zio.blocks.streams.*

// Primitive-specialized logical types
val ints = Stream(1, 2, 3).map(_ * 2)
val longs = Stream(1L, 2L, 3L).filter(_ > 0L)
val doubles = Stream(1.5, 2.5, 3.5).map(_ + 1.0)
val bools = Stream(true, false, true).filter(identity)

// Reference lane: fields are primitive, but Point itself is an object
case class Point(x: Int, y: Int)
val points = Stream(Point(1, 2), Point(3, 4))
  .map(p => Point(p.x * 2, p.y * 2))

// Reference lane: tuples are objects
val pairs = Stream((1, 2), (3, 4))
  .map { case (x, y) => (x + 1, y + 1) }

// Works, but may box for non-primitive types
val strings = Stream("a", "b", "c").map(_.toUpperCase)
```

You don't need to do anything special — the compiler and runtime handle it automatically.

## How a Lane Is Chosen

The decision is hybrid and asymmetric: half of it is made when your code compiles, half when the stream runs.

```
┌────────────────────────────────────────────────────────────────┐
│ COMPILE TIME — output lane                                     │
│ JvmType.Infer[B] resolves where the result of map/collect      │
│ lands. Nothing is generated: no @specialized, no macros.       │
└────────────────────────────────────────────────────────────────┘

    the node stores that tag and is then complete
    ▼

┌────────────────────────────────────────────────────────────────┐
│ RUNTIME, ONCE PER DRAIN — input lane                           │
│ reader.jvmType names the lane the materialized reader          │
│ actually pulls on, whatever the widened static type says.      │
└────────────────────────────────────────────────────────────────┘

    one match picks the loop, then the loop runs
    ▼

┌────────────────────────────────────────────────────────────────┐
│ PER ELEMENT — no dispatch left to do                           │
│ The chosen loop calls one exact pull and one callback.         │
│ The lane is never re-decided while elements flow.              │
└────────────────────────────────────────────────────────────────┘
```

**The output lane is fixed at compile time.** An operation that can change the element representation asks for evidence about what it produces, never about what it consumes:

```scala
def map[B](f: A => B)(implicit jtB: JvmType.Infer[B]): Stream[E, B]
def filter(pred: A => Boolean): Stream[E, A]
```

One implicit, and it is about `B`. Implicit resolution finds one of the eight primitive instances or falls through to `Infer.boxed[A]` and records `AnyRef`; adding a `JvmType.Infer` parameter to a signature is always source-compatible, because the low-priority fallback never fails to resolve. Lane-preserving operations such as `filter` ask for nothing and keep whatever representation they were handed.

`Infer` is invariant in `A`. Contravariance would let primitive evidence satisfy `Infer[Nothing]` by accident and let fallback evidence drag a precise output toward `Any`; invariance plus an explicit `Infer[Nothing]` instance closes both. The fallback itself lives in per-Scala-version `LowPriorityJvmTypeInferPlatform` files whose Scala 2 and Scala 3 bodies are byte-identical, so the split is directory-only and there is no version-dependent inference behavior to reason about.

**The input lane is chosen at runtime, from the reader.** A `Stream` only describes; a run materializes a `Reader`, and that reader reports the lane it physically pulls on through `jvmType`. This is a contract about the reader, not about the static type: widening `Stream[Nothing, Int]` to `Stream[Nothing, AnyVal]` and then filtering, tapping, taking, or buffering still pulls the source through the `Int` lane, because none of those operations changes the representation.

**Dispatch happens once per drain, never per element.** A synchronous fold matches on `reader.jvmType` one time, enters the loop belonging to that lane, and stays in it; the asynchronous puller goes further and resolves its per-element pull function once, when it is constructed. Inside the loop no lane test remains — one exact pull, one callback.

### There Is No Lane Diagnostic for a Stream

Readers reach this section wanting a way to confirm that a pipeline stayed on a primitive lane. Nothing on `Stream` answers that, and it is worth being explicit about why each apparent candidate is not it either.

- `JvmType.Infer[A]` reports the **static** type, which is exactly the thing the representation machinery stopped trusting. A `Stream[Nothing, AnyVal]` whose reader is still on the `Int` lane infers `AnyRef`.
- `ElementRepresentation` — the three-case value (`Known(jvmType)`, `Boxed`, `LateBound`) that stream nodes actually carry — is `private[streams]` and not part of the public API.
- `Reader#jvmType` **is** public, so inside a `Sink.createBoth` synchronous callback, or after `Stream#startAsync` or `Stream#useReaderAsync`, you can read the materialized reader's lane. That answers a narrower question than the one being asked: it names the lane at that one boundary, not whether every fused stage kept it.

For the end-to-end question, allocation profiling is the real diagnostic. Measure the workload under an allocation profiler and read the normalized allocation rate; a throughput number on its own cannot tell you whether a lane was held.

## Generalized Specialization

All eight primitive lanes are specialized — `Boolean`, `Byte`, `Char`, `Short`, `Int`, `Long`, `Float`, and `Double` — across `Stream`, `Sink`, `Pipeline`, and `Reader`, in both the synchronous and the asynchronous interpreter, on the JVM and on Scala.js. Counting the reference fallback, that is nine-lane dispatch.

The specialized path is not reserved for any one element and accumulator pairing. A `Short` stream folded into a `Double`, a `Char` stream merged across workers, a `Boolean` stream collected — each reaches the same shared drivers, the same selector lifecycle, and the same buffer policy as an `Int` stream reduced by a `Long` checksum.

Specialization is about where boxing is avoided by construction, not a throughput promise: it says which lane a pull travels on, not what a given workload will measure.

## EOF Signalling Per Lane

A pull has to be able to say "there are no more elements" without allocating an `Option` to say it in, so every lane reports exhaustion out of band. The schemes differ because the lanes differ. The authority is the nine-branch dispatch in `Sink.foldSyncReader`; the library's other synchronous drain loops — `Reader#readAll`, `Sink.exists`, `Sink.find` — use the same scheme for each lane. The asynchronous puller differs on two lanes: it detects end of stream on `Byte` and `Float` by a negative count from `readBytes` and `readFloats`, rather than by the scalar sentinel.

| Lane      | Public pull for this lane    | Carrier          | End of stream                       |
|-----------|------------------------------|------------------|-------------------------------------|
| `Boolean` | `readBoolean(-1)`            | `Int`            | any negative value                  |
| `Byte`    | `readByte()`                 | `Int`            | `-1` (any negative value); takes no sentinel parameter |
| `Char`    | `readChar(-1)`               | `Int`            | any negative value                  |
| `Short`   | `readShort(Int.MinValue)`    | `Int`            | `Int.MinValue`                      |
| `Int`     | `readInt(Long.MinValue)`     | `Long`           | `Long.MinValue`                     |
| `Long`    | `readLongs(scratch, 0, 1)`   | `Array[Long]`    | **negative returned count**         |
| `Float`   | `readFloat(Double.MaxValue)` | `Double`         | `Double.MaxValue`                   |
| `Double`  | `readDoubles(scratch, 0, 1)` | `Array[Double]`  | **negative returned count**         |
| `AnyRef`  | `read(sentinel)`             | the element type | the sentinel, by reference identity |

Three things are worth reading off that table.

**The sentinel travels in the widened carrier.** `readInt` returns `Long` rather than `Int` precisely so a value outside the element's domain exists to spend; `Boolean`, `Char`, and `Short` widen to `Int` for the same reason. `readByte()` is the exception that proves the rule — it yields unsigned bytes in `0..255`, so it can reserve `-1` permanently and takes no sentinel parameter at all.

**`Long` and `Double` use no sentinel.** For those two lanes the carrier is the element type itself, and there is no `Long` value and no `Double` bit pattern — NaN payloads and signed zero included — that is not also legitimate data, so every candidate sentinel collides with something a stream may legitimately carry. They detect end of stream by count instead: a length-one `readLongs` or `readDoubles` whose returned count is negative. The one-element scratch array is allocated once by the owner, outside the loop, never inside it. That is what makes these two lanes fully lossless — every `Long` value and every `Double` bit pattern stays readable as data.

**`readLong` and `readDouble` still take a sentinel parameter.** They are kept for symmetry with the five other sentinel-taking pulls, and for callers who can prove that a particular sentinel lies outside their own data domain. Without that qualifier the prose would contradict the signatures; with it, the point stands, because nothing can safely fill the parameter in general-purpose code and the library never relies on it. `SinkExactLaneSpec` and `RouteSpecializationProofSpec` install probe readers whose scalar `readLong` and `readDouble` throw an `AssertionError` even on their own matching lane, so a regression that reintroduced sentinel detection fails a test rather than silently truncating a value.

:::warning[Do not detect end of stream with `readLong` or `readDouble`]
Any value you reserve as an end marker on those two lanes is also a value the stream may carry, and the truncation is silent. Use `readLongs(scratch, 0, 1)` or `readDoubles(scratch, 0, 1)` and branch on the returned count.
:::

[Reader](../primitives/reader.md#sentinel-protocol) states the same contract from the implementor's side.

## Comparison: @specialized Vs JvmType Dispatch

ZIO Blocks Streams' approach differs fundamentally from Scala's traditional `@specialized` annotation. Here's how they compare:

**Traditional Scala `@specialized` annotation** generates separate specialized classes at compile time:

```scala
@specialized(Int, Long, Double)
class Stream[+E, +A] { ... }
// Generates separate classes:
// - Stream$mcI$sp (specialized for Int)
// - Stream$mcJ$sp (specialized for Long)
// - Stream$mcD$sp (specialized for Double)
// - Stream (generic fallback)
// Result: additional generated classes and bytecode
```

**ZIO Blocks `JvmType` dispatch** records logical input and output types on stream and pipeline nodes. Compilation turns those types into interpreter lane and operator tags; it does not generate a separate `Stream` class for every primitive.

| Metric | `@specialized` | JvmType |
|--------|---|---|
| **Binary size** | Additional specialized classes | No class-per-primitive specialization |
| **Bytecode complexity** | High | Moderate |
| **Runtime dispatch** | Selected by specialized class | Interpreter lane and operator-tag dispatch |
| **Flexibility** | Fixed at compile time | Adaptive at runtime |
| **Primitive support** | Configurable | Boolean, Byte, Short, Char, Int, Long, Float, Double |
| **Generality** | Good for all generics | Specialized for Stream/Sink |

## Implementation Architecture

Primitive type metadata is carried across ZIO Blocks Streams' three core abstractions. Whether a concrete execution remains on primitive physical lanes depends on the interpreter and operation.

### Stream[E, A]

Stores element representation when a source is constructed and dispatches `Reader` accesses. Transformations request fresh evidence for their result, not for an input whose representation the stream already knows:

```scala
import zio.blocks.streams.*

val stream: Stream[Nothing, Int] = Stream(1, 2, 3)
// JvmType.Int is inferred and available to all operations

val asLong = stream.map(_.toLong)
// Infer[Long] records the transformed result representation
```

### Sink[E, A, Z]

Accepts elements via unboxed `write` methods matched to the detected type:

```scala
import zio.blocks.streams.*
import zio.blocks.chunk.Chunk

val nums = Stream(1, 2, 3)
val sum = nums.runFold(0)(_ + _)
// The synchronous interpreter can feed the fold through an Int lane
```

### Pipeline[A, B]

Preserves the input representation and records evidence for transformed results:

```scala
import zio.blocks.streams.*

val pipe = Pipeline.map[Int, Int](_ * 2)
// Infer[Int] describes the result. The input representation is supplied when
// the pipeline is applied to a Stream or Sink.

val throughStream = Stream(1, 2, 3).via(pipe)
val throughSink   = pipe.andThenSink(Sink.sumInt)
// Both application routes propagate the same representation information.
```

## Performance Impact

Performance falls into distinct categories:

- **Scalar CPU pipelines** (`map`, `filter`, folds) benefit most from primitive lanes because boxing, allocation, and dispatch are a large share of the work.
- **Bulk memory paths** (`readInts`, `readLongs`, `readDoubles`, collection) additionally benefit from contiguous arrays and fewer per-element calls.
- **Bounded concurrency** pays queue/selector and scheduling costs; use it when callback or child latency dominates, not for trivial arithmetic.
- **Native asynchronous I/O** is governed mainly by source latency, chunk size, and cancellation/ownership costs; zero-boxing is usually secondary.
- **Writer `*Async` adapters** defer synchronous work and may still block, so they should not be benchmarked as native async I/O.

Do not infer a universal multiplier from these categories. Results depend on JDK, Scala version, platform, element type, pipeline shape, buffer size, and terminal; use the repository JMH benchmarks with a workload representative of the application. This page publishes no speedup figure for generalized specialization: any single multiplier would be drawn from a narrower set of benchmark shapes than the claim it was used to support.

Repository JMH results measure throughput for named benchmark methods and configurations, and a suite is evidence only about the contract it actually measures — a ready-effect suite measures ready effects, not asynchronous stream performance in general, and results for unlike contracts must not be aggregated into one ranking. Unless a run also records an allocation profiler (for example `gc.alloc.rate.norm`), it is **not** evidence of zero allocations; a near-zero figure from a throughput run is profiler noise, not a promise. The lane layout and primitive reader signatures establish where boxing is avoided by construction; claims about callback invocation, complete pipelines, async carriers, or parity with handwritten loops remain unproven until measured with allocation profiling for that exact workload.

## Async and Boxing

The claims above are about the synchronous interpreter, and the asynchronous one has an honest limit that belongs right next to them: **asynchronous execution is lane-aware, not end-to-end zero-boxing.**

Lane awareness is real. `AsyncInterpreter` keeps primitive lane state internally, the asynchronous puller resolves its exact per-element pull once from `reader.jvmType`, and `AsyncReader` exposes the same nine-identity pull surface as its synchronous counterpart, bulk `readLongs` and `readDoubles` included.

What changes is the carrier at the boundary. Every asynchronous result crosses a generic `Async[T]`, and a completed primitive in an `Async[Long]` is a `Long` in a generic position, so it may be boxed. The same applies wherever a primitive passes through another generic carrier: an erased Scala function, an `Option`, an `Either`, or the `Async[Either[E, Z]]` convention the asynchronous terminals use. That carrier-boundary boxing is a different thing from the error the lanes exist to prevent — pulling an upstream primitive through generic `read`, or routing it through the wrong lane — and only the second is ruled out by construction.

The practical consequence is that "zero-boxing" should not be read as a property of an asynchronous pipeline end to end. It describes the pull surface and the interpreter's own state, and stops at the carrier. As everywhere else on this page, whether a particular asynchronous workload allocates is a question for an allocation profiler. [Asynchronous Stream Execution](./async-execution.md#why-two-engines) explains why the two engines exist at all.

## When Polymorphism Is Necessary

If you need polymorphic behavior (e.g., different handling for different types), use `JvmType` directly:

```scala
import zio.blocks.streams.*
import zio.blocks.streams.JvmType

def processStream[A](stream: Stream[Nothing, A])(implicit jt: JvmType.Infer[A]): Unit = {
  jt.jvmType match {
    case JvmType.Int =>
      println("Processing integers")
    case JvmType.Long =>
      println("Processing longs")
    case _ =>
      println("Processing generic type")
  }
}
```

This gives code runtime type information. It does not by itself guarantee allocation-free execution; the selected operation and interpreter still determine the physical path.

## Summary

ZIO Blocks Streams reduces primitive boxing through:

1. **Compile-time output lanes** fixed by `JvmType.Infer[A]` implicit resolution
2. **Runtime input lanes** read from `reader.jvmType`, dispatched once per drain rather than per element
3. **Primitive synchronous accessors** across all nine identities, with widened sentinel carriers, and count-based end of stream on the two lossless lanes
4. **Lane-aware async interpretation**, while accepting that generic `Async[T]` carriers may box completed primitives

The result is specialized hot paths without `@specialized` class proliferation, available to every lane and every accumulator type rather than to one benchmark-shaped product. Some generic callbacks, reference values, tuples/case classes, and async/concurrent coordination can still allocate or box; verify important workloads with an allocation profiler rather than assuming allocation-free execution end to end.

## See Also

- [Reader](../primitives/reader.md#the-reader-union) — the two reader kinds, and the sentinel protocol from the implementor's side
- [Sink](../core/sink.md) — the typed sinks that drain through these lanes
- [Pipeline](../core/pipeline.md) — why the transforming factories ask for `JvmType.Infer` on their result type
- [Asynchronous Stream Execution](./async-execution.md#one-stream-type-two-execution-modes) — how a graph becomes synchronous or asynchronous, and why there is no mode annotation either
- [Platform Differences](./platform-differences.md) — what changes on Scala.js, where the same lanes apply
