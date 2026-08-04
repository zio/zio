# Pipeline

> `Pipeline[-In, +Out]` is a **reusable, composable stream transformation** that converts elements of type `In` into elements of type `Out`. Pipelines are first-class values: you can define them once, compose them with `andThen`, and apply them to any [Stream](./stream.md) via `stream.via(pipe)` or to any `Sink` via `pipe.andThenSink(sink)`.

`Pipeline[-In, +Out]` is a **reusable, composable stream transformation** that converts elements of type `In` into elements of type `Out`. Pipelines are first-class values: you can define them once, compose them with `andThen`, and apply them to any [Stream](./stream.md) via `stream.via(pipe)` or to any `Sink` via `pipe.andThenSink(sink)`.

`Pipeline`:
- Is contravariant in `In` and covariant in `Out` (like a function `In => Out`)
- Can be applied to a **Stream** (transforming the output) or a **Sink** (pre-processing the input)
- Participates in JVM primitive specialization to avoid boxing

Here is the structural shape of the `Pipeline` type:

```scala
abstract class Pipeline[-In, +Out] {
  def andThen[C](that: Pipeline[Out, C]): Pipeline[In, C]
  def applyToStream[E](stream: Stream[E, In]): Stream[E, Out]
  def applyToSink[E, Z](sink: Sink[E, Out, Z]): Sink[E, In, Z]
}
```

## Overview

Pipelines solve the problem of reusing stream transformations across different streams and sinks. Without pipelines, you repeat filtering and mapping logic for every stream. With pipelines, you define transformations once as first-class values, compose them freely, and apply them anywhere.

### The Problem

When you build stream processing logic, you often write chains like:

```scala
stream
  .filter(_ > 0)
  .map(_ * 2)
  .take(100)
```

This works, but the transformation is tied to a specific stream. If you want to apply the same logic to a different stream, or to a sink instead, you have to repeat yourself. You cannot pass the chain around as a value, store it in a variable, or compose it with other transformations.

### The Solution

`Pipeline[-In, +Out]` lifts stream transformations into first-class values. You define a pipeline once, compose it with other pipelines using `andThen`, and apply it wherever you need:

```scala
import zio.blocks.streams.*

// Define once
val normalize: Pipeline[Int, Int] =
  Pipeline.filter[Int](_ > 0)
    .andThen(Pipeline.map[Int, Int](_ * 2))
    .andThen(Pipeline.take(100))

// Apply to any stream
val stream1 = Stream(1, 2, 3, 4, 5)
val stream2 = Stream(10, 20, 30, 40, 50)
val stream3 = Stream(-5, 3, 7, 2, 8, 1, 9)

val result1 = stream1.via(normalize).runCollect
val result2 = stream2.via(normalize).runCollect

// Apply to a sink (pre-process input before the sink sees it)
val normalizedSink = normalize.andThenSink(Sink.collectAll[Int])
val result3 = stream3.run(normalizedSink)
```

`Pipeline` forms a **category** in the mathematical sense:

| Law            | Statement                                            |
|----------------|------------------------------------------------------|
| Left identity  | `Pipeline.identity andThen p == p`                   |
| Right identity | `p andThen Pipeline.identity == p`                   |
| Associativity  | `(p andThen q) andThen r == p andThen (q andThen r)` |

These laws guarantee that pipelines compose predictably, regardless of how you parenthesize.

### Architecture

`Pipeline` sits between `Stream` and `Sink`, mediating how elements flow:

```
  Applying to a Stream (via):
  ┌──────────────┐     ┌──────────────────┐     ┌──────────────┐
  │ Stream[E, In]│ ──→ │ Pipeline[In, Out]│ ──→ │Stream[E, Out]│
  └──────────────┘     └──────────────────┘     └──────────────┘

  Applying to a Sink (andThenSink):
  ┌──────────────────┐     ┌────────────────┐     ┌──────────────┐
  │ Pipeline[In, Out]│ ──→ │ Sink[E, Out, Z]│ ──→ │Sink[E, In, Z]│
  └──────────────────┘     └────────────────┘     └──────────────┘

  Composing two Pipelines (andThen):
  ┌──────────────────┐     ┌──────────────────┐     ┌─────────────────┐
  │ Pipeline[A, B]   │ ──→ │ Pipeline[B, C]   │ ──→ │ Pipeline[A, C]  │
  └──────────────────┘     └──────────────────┘     └─────────────────┘
```

## Construction

Pipelines are built using factory methods on the `Pipeline` companion object. Each factory creates a pipeline that performs a specific transformation: mapping elements, filtering, collecting, or controlling flow. All factories support JVM primitive specialization through implicit `JvmType.Infer` parameters.

### `Pipeline.map[A, B]` — Transform Each Element

Applies a function to every element, producing a new element type. Here is the signature:

```scala
object Pipeline {
  def map[A, B](f: A => B)(implicit jtA: JvmType.Infer[A], jtB: JvmType.Infer[B]): Pipeline[A, B]
}
```

This is the most common pipeline constructor:

```scala
import zio.blocks.streams.*

val doubler = Pipeline.map[Int, Int](_ * 2)
// doubler: Pipeline[Int, Int] = zio.blocks.streams.Pipeline$MapPipeline@5751527e
val toStr = Pipeline.map[Int, String](_.toString)
// toStr: Pipeline[Int, String] = zio.blocks.streams.Pipeline$MapPipeline@21969325

val result = Stream(1, 2, 3).via(doubler).runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(2, 4, 6))
```

### `Pipeline.filter[A]` — Keep Matching Elements

Keeps only elements that satisfy a predicate. Here is the signature:

```scala
object Pipeline {
  def filter[A](pred: A => Boolean)(implicit jtA: JvmType.Infer[A]): Pipeline[A, A]
}
```

Note that the output type is the same as the input type — filtering does not change the element type:

```scala
import zio.blocks.streams.*

val positives = Pipeline.filter[Int](_ > 0)
// positives: Pipeline[Int, Int] = zio.blocks.streams.Pipeline$FilterPipeline@2148b90d

val result = Stream(-2, -1, 0, 1, 2).via(positives).runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(1, 2))
```

### `Pipeline.collect[A, B]` — Partial Function Transformation

Applies a partial function: only elements for which the function is defined pass through, and they are transformed to the output type. This combines filtering and mapping in one step. Here is the signature:

```scala
object Pipeline {
  def collect[A, B](pf: PartialFunction[A, B])(implicit jtA: JvmType.Infer[A], jtB: JvmType.Infer[B]): Pipeline[A, B]
}
```

Use `collect` when you need to filter and transform simultaneously:

```scala
import zio.blocks.streams.*

val extractInts = Pipeline.collect[Any, Int] { case n: Int => n }
// extractInts: Pipeline[Any, Int] = zio.blocks.streams.Pipeline$CollectPipeline@63a54682

val result = Stream(1, "a", 2, "b", 3).via(extractInts).runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(1, 2, 3))
```

### `Pipeline.take[A]` — First N Elements

Passes through at most the first `n` elements, then stops. Here is the signature:

```scala
object Pipeline {
  def take[A](n: Long): Pipeline[A, A]
}
```

This naturally short-circuits — upstream stops producing once `n` elements have passed:

```scala
import zio.blocks.streams.*

val firstFive = Pipeline.take[Int](5)
// firstFive: Pipeline[Int, Int] = zio.blocks.streams.Pipeline$TakePipeline@53108fba

val result = Stream.range(0, 1000).via(firstFive).runCollect
// result: Either[Nothing, Chunk[Int]] = Right(IndexedSeq(0, 1, 2, 3, 4))
```

### `Pipeline.drop[A]` — Skip First N Elements

Skips the first `n` elements, then passes through the rest. Here is the signature:

```scala
object Pipeline {
  def drop[A](n: Long): Pipeline[A, A]
}
```

Use `drop` to skip headers, metadata, or warm-up elements:

```scala
import zio.blocks.streams.*

val skipHeader = Pipeline.drop[String](1)
// skipHeader: Pipeline[String, String] = zio.blocks.streams.Pipeline$DropPipeline@5e27a41e

val result = Stream("header", "row1", "row2").via(skipHeader).runCollect
// result: Either[Nothing, Chunk[String]] = Right(IndexedSeq("row1", "row2"))
```

### `Pipeline.identity[A]` — Pass-Through

The identity pipeline that passes all elements through unchanged. This is the neutral element for `andThen` composition. Here is the signature:

```scala
object Pipeline {
  def identity[A](implicit jtA: JvmType.Infer[A]): Pipeline[A, A]
}
```

You rarely construct `identity` explicitly, but it is important as a base case in generic pipeline-building code:

```scala
import zio.blocks.streams.*

val noOp = Pipeline.identity[Int]
// noOp: Pipeline[Int, Int] = zio.blocks.streams.Pipeline$MapPipeline@52ba998

// These are equivalent:
// stream.via(noOp)  ==  stream
// noOp.andThen(p)   ==  p
// p.andThen(noOp)   ==  p
```

## Composing Pipelines

Pipelines compose into larger, more complex transformations using `andThen`. Because `Pipeline` forms a mathematical category, composition is associative and respects identity, so you can build pipelines incrementally or conditionally without worrying about how you parenthesize or combine them.

### `Pipeline#andThen[C]` — Sequential Composition

Composes two pipelines into one, applying `this` first and `that` second. Here is the signature:

```scala
trait Pipeline[-In, +Out] {
  def andThen[C](that: Pipeline[Out, C]): Pipeline[In, C]
}
```

`andThen` is the key operation that makes pipelines composable. Because `Pipeline` forms a category, composition is associative — you can group `andThen` calls however you like and get the same result:

```scala
import zio.blocks.streams.*

// Individual steps
val filterPositive = Pipeline.filter[Int](_ > 0)
val double         = Pipeline.map[Int, Int](_ * 2)
val takeFirst10    = Pipeline.take[Int](10)

// Compose into a single reusable pipeline
val normalize = filterPositive
  .andThen(double)
  .andThen(takeFirst10)

// Apply to any stream
val result = Stream(-5, 3, -1, 7, 2, 0, 9, 4, 8, 6, 1, 10)
  .via(normalize)
  .runCollect
```

### Building Pipelines Conditionally

Because pipelines are values, you can build them dynamically:

```scala
import zio.blocks.streams.*

def buildPipeline(limit: Option[Int], onlyPositive: Boolean): Pipeline[Int, Int] = {
  val base = if (onlyPositive) Pipeline.identity[Int].andThen(Pipeline.filter(_ > 0)) else Pipeline.identity[Int]
  limit.fold(base)(n => base.andThen(Pipeline.take(n.toLong)))
}
```

## Applying to a Stream

Apply a pipeline to a stream using `via` to transform its output elements. This is the most direct way to use a pipeline: define it once and apply it to multiple streams without repeating the transformation logic.

### `Stream#via[B]` — Apply a Pipeline to a Stream

The primary way to use a pipeline is through `Stream.via`. Here is the signature:

```scala
trait Stream[+E, +A] {
  def via[B](pipe: Pipeline[A, B]): Stream[E, B]
}
```

Under the hood, `via` calls `pipe.applyToStream(this)`. Each pipeline type delegates to a specific `Stream` node — for example, `Pipeline.map` creates a `Stream.Mapped`, and `Pipeline.filter` creates a `Stream.Filtered`.

The key advantage of `via` over inline methods is **reuse**: define the pipeline once and apply it to multiple streams:

```scala
import zio.blocks.streams.*

// A reusable cleaning pipeline for sensor data
val cleanSensorData: Pipeline[Double, Double] =
  Pipeline.filter[Double](d => !d.isNaN && !d.isInfinite)
    .andThen(Pipeline.filter(d => d >= -100.0 && d <= 100.0))
// cleanSensorData: Pipeline[Double, Double] = zio.blocks.streams.Pipeline$Composed@1eea4c07

// Apply to different sensor streams
val sensorStream1 = Stream(45.5, 67.2, Double.NaN, 23.1)
// sensorStream1: Stream[Nothing, Double] = Stream(45.5, 67.2, NaN, 23.1)
val sensorStream2 = Stream(89.9, -200.0, 12.5, 55.0)
// sensorStream2: Stream[Nothing, Double] = Stream(89.9, -200.0, 12.5, 55.0)

val sensor1Result = sensorStream1.via(cleanSensorData).runCollect
// sensor1Result: Either[Nothing, Chunk[Double]] = Right(
//   IndexedSeq(45.5, 67.2, 23.1)
// )
val sensor2Result = sensorStream2.via(cleanSensorData).runCollect
// sensor2Result: Either[Nothing, Chunk[Double]] = Right(
//   IndexedSeq(89.9, 12.5, 55.0)
// )
```

### `Pipeline#applyToStream[E]` — Direct Application

You can also call `applyToStream` directly. This is equivalent to `via` but reads left-to-right from the pipeline's perspective. Here is the signature:

```scala
trait Pipeline[-In, +Out] {
  def applyToStream[E](stream: Stream[E, In]): Stream[E, Out]
}
```

`stream.via(pipe)` and `pipe.applyToStream(stream)` are identical in behavior. Prefer `via` for readability in stream chains.

## Applying to a Sink

Apply a pipeline to a sink using `andThenSink` to pre-process the sink's input elements. This is the dual of `via`: instead of transforming a stream's output, you transform what the sink receives before it processes it.

### `Pipeline#andThenSink[E, Z]` — Pre-Process Sink Input

The dual of `via`: instead of transforming a stream's output, you pre-process a sink's input. Here is the signature:

```scala
trait Pipeline[-In, +Out] {
  def andThenSink[E, Z](sink: Sink[E, Out, Z]): Sink[E, In, Z]
}
```

This is an alias for `applyToSink`. After calling `andThenSink`, the resulting sink accepts `In` elements, transforms them through the pipeline, and feeds the `Out` elements to the original sink:

```scala
import zio.blocks.streams.*

// A pipeline that normalizes strings
val normalize = Pipeline.map[String, String](_.trim.toLowerCase)
// normalize: Pipeline[String, String] = zio.blocks.streams.Pipeline$MapPipeline@2a3c97bf

// Apply to different sinks
val collectNormalized = normalize.andThenSink(Sink.collectAll[String])
// collectNormalized: Sink[Nothing, String, Chunk[String]] = zio.blocks.streams.Sink$Contramapped@7c1204c4
val countNormalized   = normalize.andThenSink(Sink.count)
// countNormalized: Sink[Nothing, String, Long] = zio.blocks.streams.Sink$Contramapped@3a99eae2

val result = Stream("  Hello ", " WORLD  ").run(collectNormalized)
// result: Either[Nothing, Chunk[String]] = Right(IndexedSeq("hello", "world"))
```

### When to Use `andThenSink` vs `via`

Both achieve the same result. Choose based on which side you want to reuse:

| Approach                             | Use when…                                   |
|--------------------------------------|---------------------------------------------|
| `stream.via(pipe).run(sink)`         | You have a fixed pipeline and varying sinks |
| `stream.run(pipe.andThenSink(sink))` | You want a reusable "pre-processing sink"   |

The laws guarantee equivalence: `stream.via(pipe).run(sink) == stream.run(pipe.andThenSink(sink))`.

### `Pipeline#applyToSink[E, Z]` — Direct Application

`andThenSink` is an alias for `applyToSink`. Here is the signature:

```scala
trait Pipeline[-In, +Out] {
  def applyToSink[E, Z](sink: Sink[E, Out, Z]): Sink[E, In, Z]
}
```

Prefer `andThenSink` for readability.

## JVM Primitive Specialization

`Pipeline.map`, `Pipeline.filter`, `Pipeline.collect`, and `Pipeline.identity` all require `JvmType.Infer[A]` implicit parameters. These are resolved at compile time and enable unboxed, specialized code paths for primitive types (`Int`, `Long`, `Float`, `Double`, etc.). You never need to provide these explicitly — the compiler infers them automatically.

`Pipeline.take` and `Pipeline.drop` do not require `JvmType.Infer` because they do not inspect or transform element values — they only count positions.

## Integration

`Pipeline` integrates seamlessly with the other core streaming primitives: `Stream` and `Sink`. Understanding these integrations shows how pipelines fit into the broader streaming architecture.

### With Stream

`Pipeline` is the mechanism behind `Stream.via`. Every call to `stream.via(pipe)` delegates to `pipe.applyToStream(stream)`, which constructs the appropriate `Stream` subtype node. See [Stream — Integration with Pipeline and Sink](./stream.md#integration-with-pipeline-and-sink) for the stream-side perspective.

### With Sink

`Pipeline.andThenSink` creates a new `Sink` that pre-processes its input through the pipeline before the original sink consumes it. The `Sink` type provides its own transformation methods (`contramap`, `map`, `mapError`) — `andThenSink` extends these with the full power of pipeline composition (filtering, taking, dropping, collecting).

## Running the Examples

All code from this guide is available as runnable examples in the `streams-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Basic Usage

This example demonstrates all six Pipeline factory methods: `map`, `filter`, `collect`, `take`, `drop`, and `identity`. Here is the source code:

```scala title="streams-examples/src/main/scala/pipeline/PipelineBasicUsageExample.scala" 
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

package pipeline

import zio.blocks.streams.*
import zio.sbt.ExprEval.show

object PipelineBasicUsageExample extends App {
  println("=== Pipeline Basic Usage ===\n")

  // 1. Pipeline.map — transform each element
  println("1. Pipeline.map — transform each element:")
  val doubler = Pipeline.map[Int, Int](_ * 2)
  show(Stream(1, 2, 3).via(doubler).runCollect)

  // 2. Pipeline.map — cross-type transformation
  println("\n2. Pipeline.map — type-changing transformation:")
  val intToString = Pipeline.map[Int, String](n => s"item-$n")
  show(Stream(1, 2, 3).via(intToString).runCollect)

  // 3. Pipeline.filter — keep elements matching a predicate
  println("\n3. Pipeline.filter — keep matching elements:")
  val positives = Pipeline.filter[Int](_ > 0)
  show(Stream(-2, -1, 0, 1, 2).via(positives).runCollect)

  // 4. Pipeline.collect — partial function (filter + map)
  println("\n4. Pipeline.collect — partial function transformation:")
  val extractInts = Pipeline.collect[Any, Int] { case n: Int => n * 10 }
  show(Stream(1, "a", 2, "b").via(extractInts).runCollect)

  // 5. Pipeline.take — first n elements
  println("\n5. Pipeline.take — first n elements (short-circuits):")
  val firstThree = Pipeline.take[Int](3)
  show(Stream.range(0, 1000).via(firstThree).runCollect)

  // 6. Pipeline.drop — skip first n elements
  println("\n6. Pipeline.drop — skip first n elements:")
  val skipTwo = Pipeline.drop[String](2)
  show(Stream("header", "subheader", "data1", "data2").via(skipTwo).runCollect)

  // 7. Pipeline.identity — pass-through (no-op)
  println("\n7. Pipeline.identity — pass-through (neutral element):")
  val noOp = Pipeline.identity[Int]
  show(Stream(1, 2, 3).via(noOp).runCollect)

  // 8. Combining drop and take to get a range
  println("\n8. Combining drop and take for pagination:")
  val page2 = Pipeline.drop[Int](3).andThen(Pipeline.take(3))
  show(Stream.range(0, 10).via(page2).runCollect)
}
```

([source](https://github.com/zio/zio-blocks/blob/main/streams-examples/src/main/scala/pipeline/PipelineBasicUsageExample.scala))

Run it with:

```bash
sbt "streams-examples/runMain pipeline.PipelineBasicUsageExample"
```

### Pipeline Composition

This example shows how to compose pipelines with `andThen`, apply them to multiple streams, and build pipelines conditionally. Here is the source code:

```scala title="streams-examples/src/main/scala/pipeline/PipelineCompositionExample.scala" 
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

package pipeline

import zio.blocks.streams.*
import zio.sbt.ExprEval.show

object PipelineCompositionExample extends App {
  println("=== Pipeline Composition ===\n")

  // 1. Basic andThen composition
  println("1. Composing filter + map with andThen:")
  val filterPositive = Pipeline.filter[Int](_ > 0)
  val double         = Pipeline.map[Int, Int](_ * 2)
  val composed       = filterPositive.andThen(double)

  show(Stream(-3, -1, 0, 2, 5).via(composed).runCollect)

  // 2. Multi-stage pipeline
  println("\n2. Multi-stage pipeline (filter → map → take):")
  val multiStage = Pipeline
    .filter[Int](_ % 2 == 0)
    .andThen(Pipeline.map[Int, Int](_ * 10))
    .andThen(Pipeline.take(3))

  show(Stream.range(1, 11).via(multiStage).runCollect)

  // 3. Reusing the same pipeline across different streams
  println("\n3. Reusing the same pipeline across different streams:")
  val normalize = Pipeline.filter[Int](_ >= 0).andThen(Pipeline.map[Int, Double](_.toDouble / 100.0))

  val dataset1 = Stream(150, -20, 75, 200, -10)
  val dataset2 = Stream(50, 100, -5, 300)

  show(dataset1.via(normalize).runCollect)
  show(dataset2.via(normalize).runCollect)

  // 4. Category law: left identity
  println("\n4. Category law — left identity (identity andThen p == p):")
  val pipe = Pipeline.map[Int, Int](_ + 1)
  val data = Stream(1, 2, 3)

  val withIdentityL   = data.via(Pipeline.identity[Int].andThen(pipe)).runCollect
  val withoutIdentity = data.via(pipe).runCollect
  show(withIdentityL)
  show(withoutIdentity)

  // 5. Category law: right identity
  println("\n5. Category law — right identity (p andThen identity == p):")
  val withIdentityR = data.via(pipe.andThen(Pipeline.identity[Int])).runCollect
  show(withIdentityR)
  show(withoutIdentity)

  // 6. Category law: associativity
  println("\n6. Category law — associativity ((p andThen q) andThen r == p andThen (q andThen r)):")
  val p = Pipeline.filter[Int](_ > 0)
  val q = Pipeline.map[Int, Int](_ * 3)
  val r = Pipeline.take[Int](2)

  val leftGrouped  = (p.andThen(q)).andThen(r)
  val rightGrouped = p.andThen(q.andThen(r))

  val source = Stream(-1, 2, -3, 4, 5, 6)
  show(source.via(leftGrouped).runCollect)
  show(source.via(rightGrouped).runCollect)

  // 7. Building pipelines conditionally
  println("\n7. Building pipelines conditionally:")
  def buildPipeline(limit: Option[Int], onlyPositive: Boolean): Pipeline[Int, Int] = {
    var pipe: Pipeline[Int, Int] = Pipeline.identity[Int]
    if (onlyPositive) pipe = pipe.andThen(Pipeline.filter(_ > 0))
    limit.foreach(n => pipe = pipe.andThen(Pipeline.take(n.toLong)))
    pipe
  }

  val conditionalPipe = buildPipeline(limit = Some(3), onlyPositive = true)
  show(Stream(-1, 2, -3, 4, 5, 6).via(conditionalPipe).runCollect)

  val noPipe = buildPipeline(limit = None, onlyPositive = false)
  show(Stream(-1, 2, -3).via(noPipe).runCollect)
}
```

([source](https://github.com/zio/zio-blocks/blob/main/streams-examples/src/main/scala/pipeline/PipelineCompositionExample.scala))

Run it with:

```bash
sbt "streams-examples/runMain pipeline.PipelineCompositionExample"
```

### Sink Integration

This example demonstrates applying pipelines to sinks with `andThenSink`, showing the equivalence between `stream.via(pipe).run(sink)` and `stream.run(pipe.andThenSink(sink))`. Here is the source code:

```scala title="streams-examples/src/main/scala/pipeline/PipelineSinkIntegrationExample.scala" 
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

package pipeline

import zio.blocks.streams.*
import zio.sbt.ExprEval.show

object PipelineSinkIntegrationExample extends App {
  println("=== Pipeline ↔ Sink Integration ===\n")

  // 1. Basic andThenSink usage
  println("1. Basic andThenSink — pre-process before collecting:")
  val doubler        = Pipeline.map[Int, Int](_ * 2)
  val collectDoubled = doubler.andThenSink(Sink.collectAll[Int])

  show(Stream(1, 2, 3).run(collectDoubled))

  // 2. Equivalence law: via + run == run + andThenSink
  println("\n2. Equivalence law: stream.via(p).run(sink) == stream.run(p.andThenSink(sink)):")
  val pipe   = Pipeline.filter[Int](_ > 2).andThen(Pipeline.map[Int, Int](_ * 10))
  val source = Stream(1, 2, 3, 4, 5)
  val sink   = Sink.collectAll[Int]

  val viaResult         = source.via(pipe).run(sink)
  val andThenSinkResult = source.run(pipe.andThenSink(sink))
  show(viaResult)
  show(andThenSinkResult)

  // 3. Reusable pre-processing sink
  println("\n3. Reusable pre-processing sink:")
  val cleanString    = Pipeline.map[String, String](_.trim.toLowerCase)
  val collectCleaned = cleanString.andThenSink(Sink.collectAll[String])
  val countCleaned   = cleanString.andThenSink(Sink.count)

  val rawData = Stream("  Hello ", " WORLD  ", "  Scala  ")

  show(rawData.run(collectCleaned))
  show(rawData.run(countCleaned))

  // 4. andThenSink with foldLeft
  println("\n4. Pipeline + foldLeft sink:")
  val sumPositives = Pipeline
    .filter[Int](_ > 0)
    .andThenSink(Sink.foldLeft(0)((acc, x) => acc + x))

  show(Stream(-5, 3, -2, 7, 1).run(sumPositives))

  // 5. andThenSink with head/find
  println("\n5. Pipeline + head/find sinks:")
  val firstEven = Pipeline.filter[Int](_ % 2 == 0).andThenSink(Sink.head[Int])

  show(Stream(1, 3, 4, 6).run(firstEven))

  // 6. Multiple pipelines, same sink
  println("\n6. Multiple pipelines applied to the same sink:")
  val baseSink = Sink.collectAll[Int]

  val evens = Pipeline.filter[Int](_ % 2 == 0).andThenSink(baseSink)
  val odds  = Pipeline.filter[Int](_ % 2 != 0).andThenSink(baseSink)

  val nums = Stream(1, 2, 3, 4, 5, 6)
  show(nums.run(evens))
  show(nums.run(odds))

  // 7. Complex pipeline applied to sink
  println("\n7. Multi-stage pipeline applied to sink:")
  val processingPipe = Pipeline
    .filter[Int](_ > 0)
    .andThen(Pipeline.map[Int, Int](_ * 2))
    .andThen(Pipeline.take(3))

  val processedSum = processingPipe.andThenSink(Sink.foldLeft(0)(_ + _))

  show(Stream(-1, 5, 3, 8, 2, 9).run(processedSum))
}
```

([source](https://github.com/zio/zio-blocks/blob/main/streams-examples/src/main/scala/pipeline/PipelineSinkIntegrationExample.scala))

Run it with:

```bash
sbt "streams-examples/runMain pipeline.PipelineSinkIntegrationExample"
```
