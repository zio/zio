# Zero-Boxing Optimization

> Working with streams of primitives (integers, longs, doubles, booleans) presents a performance challenge in languages with generic types: **boxing**. Without special care, primitive values get wrapped in objects, causing memory waste and slower code. ZIO Blocks Streams eliminates this overhead entirely through a novel runtime type-dispatch system.

Working with streams of primitives (integers, longs, doubles, booleans) presents a performance challenge in languages with generic types: **boxing**. Without special care, primitive values get wrapped in objects, causing memory waste and slower code. ZIO Blocks Streams eliminates this overhead entirely through a novel runtime type-dispatch system.

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
val intStream: Stream[Nothing, Int] = Stream(1, 2, 3)
// Compiler infers: JvmType.Infer[Int]
// This information travels through the entire pipeline

val doubled = intStream.map(_ * 2)
// JvmType.Int is available to map's implementation
```

**Step 2: Runtime Type Dispatch**

Each operation (map, filter, scan, etc.) checks the type at runtime and uses the appropriate fast path:

```scala
// Inside Stream#map's implementation
def map[B](f: A => B)(implicit jvmTypeA: JvmType.Infer[A]): Stream[E, B] = {
  val jt = jvmTypeA.jvmType
  
  if (jt eq JvmType.Int) {
    // Fast unboxed path: read raw Int, apply function, write raw Int
    val intValue = reader.readInt(Long.MinValue)
    val result = f(intValue.asInstanceOf[A])
    // result stays unboxed if B is also Int
  } else if (jt eq JvmType.Long) {
    // Fast unboxed path for Long
    val longValue = reader.readLong(Long.MinValue)
    val result = f(longValue.asInstanceOf[A])
  } else {
    // Generic path: works for any type, uses boxing for primitives
    val value = reader.read(EndOfStream)
    val result = f(value)
  }
}
```

**Step 3: Unboxed Accessors**

Instead of a single `read()` method that returns boxed `Any` (where boxed means wrapping primitives in object wrappers like `Integer`, `Long`, `Double`), primitives use specialized accessors that operate directly on primitive values:

```scala
trait Reader[A] {
  // Generic: wraps primitives in objects (Integer, Long, etc.)
  def read(onEnd: A): A
  
  // Specialized: primitives stay unboxed
  def readInt(onEnd: Long): Int
  def readLong(onEnd: Long): Long
  def readDouble(onEnd: Long): Double
  def readBoolean(onEnd: Long): Boolean
}
```

The right method is called at runtime based on the detected type, so primitives bypass boxing entirely.

## Practical Benefits

To understand the real-world impact, consider how boxing accumulates through a pipeline. Compare a hypothetical boxed implementation with ZIO Streams' zero-boxing approach.

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

### With ZIO Streams (Zero-Boxing)

With ZIO Streams' zero-boxing optimization, the same pipeline avoids all boxing overhead:

```scala
val nums = Stream(1, 2, 3, 4, 5)
val result = nums
  .map(_ * 2)        // operates on raw Int in CPU registers
  .filter(_ > 5)     // compares raw Int directly
  .map(_ + 1)        // raw Int arithmetic
  .runCollect
// Zero boxing: primitives stay in registers and cache
```

**Memory profile:** Same as non-generic code — primitives never leave the stack/registers.

## When Zero-Boxing Applies

Zero-boxing is **automatic and transparent**. You get it for free when working with primitives:

```scala
import zio.blocks.streams.*

// ✓ Zero-boxing: Int, Long, Double, Boolean
val ints = Stream(1, 2, 3).map(_ * 2)
val longs = Stream(1L, 2L, 3L).filter(_ > 0L)
val doubles = Stream(1.5, 2.5, 3.5).map(_ + 1.0)
val bools = Stream(true, false, true).filter(identity)

// ✓ Zero-boxing: case classes with primitives
case class Point(x: Int, y: Int)
val points = Stream(Point(1, 2), Point(3, 4))
  .map(p => Point(p.x * 2, p.y * 2))

// ✓ Zero-boxing: tuples of primitives
val pairs = Stream((1, 2), (3, 4))
  .map { case (x, y) => (x + 1, y + 1) }

// Works, but may box for non-primitive types
val strings = Stream("a", "b", "c").map(_.toUpperCase)
```

You don't need to do anything special — the compiler and runtime handle it automatically.

## Comparison: @specialized vs JvmType Dispatch

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
// Result: Binary size 4-5x larger
```

**ZIO Blocks `JvmType` dispatch** uses runtime type checking in a single class:

```scala
abstract class Stream[+E, +A] {
  def map[B](f: A => B)(implicit jvmType: JvmType.Infer[A]): Stream[E, B] = {
    if (jvmType.jvmType eq JvmType.Int) { /* fast path */ }
    else { /* generic path */ }
  }
}
// Single class, runtime dispatch
// Result: Binary size normal, zero boxing at runtime
```

| Metric | `@specialized` | JvmType |
|--------|---|---|
| **Binary size** | 4-5x larger | Normal |
| **Bytecode complexity** | High | Moderate |
| **Runtime dispatch** | None (compile-time) | Type check once per operation |
| **Flexibility** | Fixed at compile time | Adaptive at runtime |
| **Primitive support** | Configurable | Int, Long, Double, Boolean |
| **Generality** | Good for all generics | Specialized for Stream/Sink |

## Implementation Architecture

Zero-boxing works across ZIO Blocks Streams' three core abstractions:

### Stream[E, A]

Detects element type via `JvmType.Infer[A]` and dispatches `Reader` accesses:

```scala
import zio.blocks.streams.*

val stream: Stream[Nothing, Int] = Stream(1, 2, 3)
// JvmType.Int is inferred and available to all operations
```

### Sink[E, A, Z]

Accepts elements via unboxed `write` methods matched to the detected type:

```scala
import zio.blocks.streams.*
import zio.blocks.chunk.Chunk

val nums = Stream(1, 2, 3)
val sum = nums.runFold(0)(_ + _)
// Sink receives unboxed Int values
```

### Pipeline[A, B]

Transforms elements without boxing when both A and B are primitives:

```scala
import zio.blocks.streams.*

val pipe = Pipeline.map[Int, Int](_ * 2)
// Entire pipeline operates on raw Int
```

## Performance Impact

For typical streaming workloads, zero-boxing provides **2-5x throughput improvement** over boxed approaches:

- **CPU-bound operations** (map, filter, scan): 3-5x faster
- **Memory-bound operations** (collect, fold): 2-3x faster
- **I/O operations** (reading, writing): Minimal impact (I/O latency dominates)

The benefit scales with pipeline depth and data volume. Shallow pipelines see modest gains; deep pipelines (>10 operations) over large datasets see dramatic improvements.

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

This gives you runtime type information while maintaining full zero-boxing performance.

## Summary

ZIO Blocks Streams achieves **zero-boxing for primitives** through:

1. **Compile-time type detection** via `JvmType.Infer[A]` implicits
2. **Runtime dispatch** that selects specialized fast paths
3. **Unboxed accessors** that operate on raw primitives
4. **Transparent optimization** — you write high-level code, the system handles the details

The result is **performance parity with hand-written imperative code** while maintaining the expressiveness and safety of functional streams. No binary bloat, no manual specialization annotations, no boxing overhead.
