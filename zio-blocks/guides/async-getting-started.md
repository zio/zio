# Getting Started with Async

> Learn to create, compose, and run async effects in Scala with the zero-allocation Async[A] type.

Welcome! This tutorial introduces `Async[A]`, a zero-allocation effect type from ZIO Blocks that unifies ready values, failures, and genuinely suspended computations under a single type and combinator set. If you know basic Scala syntax and have a sense of what an effect type is, you have everything you need to follow along.

## 1. Introduction

By the end of this tutorial, you will be able to:

- Create ready-value effects with `Async.succeed` and transform them with `Async#map` and `Async#block`.
- Handle errors with `Async.fail`, `Async#catchAll`, and `Async#either`.
- Write sequential async code in direct style using `Async.async { … .await … }`.
- Bridge throw-based code and callback-based APIs with `Async.attempt` and `Async.promise` / `Completer`.
- Fork background computations with `Async#start` and cancel them with `Async#cancel`.

To add the async module to your project, include this dependency in your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-async" % "0.0.56"
```

Then bring the full DSL into scope at the top of each file you use it from:

```scala
import zio.blocks.async._
```

We recommend reading from top to bottom — each section builds directly on the one before it.

## 2. Background: What Is `Async[A]`?

`Async[A]` was designed to solve a specific problem: code often juggles three different kinds of values — results that are already available, computations that need to wait for I/O or a callback, and failures. Treating these differently in different places creates friction. `Async[A]` is one abstraction that covers all three.

The design's most important property is its **happy-path allocation budget: zero**. When you chain `Async.succeed`, `Async#map`, and another `map` call together, every step is a plain function call. No wrapper objects accumulate on the heap. Only a computation that truly suspends — waiting for a callback, a timer, or a thread — leaves a pending object behind. This is the zero-allocation promise: you pay for suspension only when you actually suspend.

We will explore `Async[A]` through an order-processing scenario: looking up a user via a callback API, parsing an order ID, checking stock availability in the background, and recovering gracefully from any failure — one concept at a time.

## 3. Ready Values: `Async.succeed`, `map`, and `block`

The simplest async computation is one that already has its answer. `Async.succeed(value)` wraps an available value into an `Async[A]` so it can take part in async chains without allocating anything. Once you have an `Async`, you transform it with `Async#map` and drive it to its final result with `Async#block`.

Here we wrap the integer `42`, double it with `map`, then extract the result:

```scala
import zio.blocks.async._

val result: Int = Async.succeed(42).map(_ * 2).block
println(s"Ready mapped: $result")
```

Expected output:

```text
Ready mapped: 84
```

- `Async.succeed(42)` wraps `42` as a ready-value `Async[Int]` with zero allocation.
- Calling `map` with `_ * 2` applies the function; because the input is already ready, the entire chain is just a function call — no suspension object is created.
- The `block` call is the eager driver — it polls the async until it completes and returns the final value. It is safe to call on the JVM; on Scala.js it throws if the computation is still suspended.

Try changing `42` to a different number and watch the output change accordingly.

## 4. Error Handling: `Async.fail`, `catchAll`, and `either`

Not every computation succeeds. `Async.fail(throwable)` creates a failed async that short-circuits all downstream `map` calls without invoking their functions. `Async#catchAll` recovers by applying a function that returns a new `Async`.

Here we create a failed computation and recover from it with `catchAll`:

```scala
import zio.blocks.async._

val recovered: String = Async.fail(new Exception("oops"))
  .catchAll(_ => Async.succeed("default"))
  .block
println(s"Recovered: $recovered")
```

Expected output:

```text
Recovered: default
```

- `Async.fail(new Exception("oops"))` creates a computation that carries the exception as its failure.
- Calling `catchAll` with a recovery function intercepts the failure; the lambda ignores the specific error and returns a ready-value replacement.
- The `block` call drives the recovered chain to its result, `"default"`.

Sometimes you want to observe both the success and failure branches as plain data rather than handle them immediately. `Async#either` reifies both outcomes as `Either[Throwable, A]` so you can pattern-match on them:

```scala
import zio.blocks.async._

val observed: Either[Throwable, Int] = Async.fail(new Exception("error"))
  .either
  .block
println(s"Observed: $observed")
```

Expected output:

```text
Observed: Left(java.lang.Exception: error)
```

- Calling `either` wraps the failure in `Left`; a successful result would appear in `Right`, turning the async's outcome into an ordinary Scala value.
- The `block` call materialises that `Either` so the learner can inspect it.

## 5. Direct Style: `Async.async` and `await`

Writing nested `Async#flatMap` chains is precise but becomes hard to read when many steps depend on each other. `Async.async { … }` lets you write that same sequencing in direct style: inside the block, call `Async#await` on any `Async` to extract its value and bind it to a local variable, as if you were writing straight-line code. The compiler rewrites every `await` call into a `flatMap` chain at compile time, so the runtime behaviour is identical.

Here we compose a user name and an order ID without a single explicit `flatMap`:

```scala
import zio.blocks.async._

val summary: String = Async.async {
  val user  = Async.succeed("Ada").await
  val order = Async.succeed(9001).await
  s"${user}'s order ${order}"
}.block
println(s"Summary: $summary")
```

Expected output:

```text
Summary: Ada's order 9001
```

- `Async.async { … }` opens a macro-powered block; the entire expression produces an `Async[String]`.
- Calling `await` on `Async.succeed("Ada")` extracts `"Ada"` and binds it to `user`; this is not a blocking call — the macro rewrites it into a `flatMap` continuation.
- Calling `await` on `Async.succeed(9001)` similarly binds the order number to `order`.
- The final string expression becomes the block's result value.
- The `block` call drives the whole composed async to completion.

:::caution[`await` Is Only Valid Inside `Async.async { … }`]
The `await` method is enforced by the compiler to be used only inside an `Async.async { … }` block. On Scala 3 the inline expansion fails with:

```
".await may only be used directly inside an Async.async { ... } block."
```

On Scala 2, the `@compileTimeOnly` annotation fires at the same point. Try deleting the `Async.async { … }` wrapper — the compiler will tell you immediately.
:::

## 6. Bridging Exceptions: `Async.attempt`

Scala code often signals failure by throwing exceptions rather than returning error values. `Async.attempt(body)` evaluates a block that may throw and captures any exception as an async failure, turning it into a value that `catchAll` can recover. A block that succeeds produces a ready-value `Async`; one that throws produces a failed `Async`.

The example parses a well-formed string and then a malformed one:

```scala
import zio.blocks.async._

val good: Int = Async.attempt("42".toInt).block
println(s"Parsed: $good")

val bad: Either[Throwable, Int] = Async.attempt("oops".toInt).either.block
println(s"Failed parse: $bad")
```

Expected output:

```text
Parsed: 42
Failed parse: Left(java.lang.NumberFormatException: For input string: "oops")
```

- `Async.attempt("42".toInt)` evaluates `"42".toInt`; because it succeeds, the result `42` becomes a ready-value `Async[Int]`.
- `Async.attempt("oops".toInt)` evaluates `"oops".toInt`; the `NumberFormatException` is caught and becomes a failed `Async`.
- Chaining `either` and `block` drives the failed async and reifies its outcome as `Left(…)`.

## 7. Callback Bridging: `Async.promise` and `Completer`

Many real-world APIs — database drivers, network libraries, timers — signal completion by calling a callback rather than returning a value. `Async.promise` lets you lift these APIs into `Async` without rewriting them. It suspends the computation and provides a `Completer[A]` — a thread-safe, one-shot handle — that you can pass to the callback. Calling `completer.succeed(value)` from any thread resolves the async and wakes up any awaiter.

The example starts a background thread that completes the promise after a short delay:

**Scala 2**

```scala
import zio.blocks.async._

val result: String = Async.promise[String] { c =>
  new Thread {
    override def run(): Unit = {
      Thread.sleep(10)
      c.succeed("hello from callback")
    }
  }.start()
}.block
println(s"Promise resolved: $result")
```

**Scala 3**

```scala
import zio.blocks.async._

val result: String = Async.promise[String] {
  val c = summon[Completer[String]]
  new Thread {
    override def run(): Unit = {
      Thread.sleep(10)
      c.succeed("hello from callback")
    }
  }.start()
}.block
println(s"Promise resolved: $result")
```

Expected output:

```text
Promise resolved: hello from callback
```

- `Async.promise[String] { … }` opens the promise body; on Scala 2 the `Completer[String]` arrives as an explicit parameter `c`; on Scala 3 it arrives as a context function argument retrieved with `summon[Completer[String]]`.
- We capture the completer in `c` so it can be referenced from the Thread's `run()` method — implicits and givens do not propagate across thread boundaries, so we capture explicitly.
- Calling `c.succeed("hello from callback")` completes the promise; the first call wins and subsequent calls are no-ops.
- The `block` call waits until the completer fires and returns the resolved value.

## 8. Forking and Cancellation: `start` and `Async.Running`

By default, async chains run eagerly on the calling thread until the first suspension or completion. To drive a computation on a **background worker** (a separate JVM thread or a Scala.js microtask queue entry) instead, call `Async#start` on any `Async`. This returns an `Async.Running[A]` handle — itself an `Async[A]` — representing the in-flight computation. You can join it by calling `block` on the handle, or stop it early with `cancel`.

The first block forks a computation and joins it:

```scala
import zio.blocks.async._

val running: Async.Running[Int] = Async.succeed(42)
  .map { x => println(s"Running in background: $x"); x * 2 }
  .start
val joined: Int = running.block
println(s"Joined: $joined")
```

Expected output:

```text
Running in background: 42
Joined: 84
```

- Calling `start` forks the entire chain — `Async.succeed(42)` plus the `map` — onto a background worker; the calling thread continues immediately.
- `running.block` blocks the calling thread until the background computation finishes and returns the result `84`.
- The background computation prints its message before returning the value, so that line appears first.

The companion method `Async.start` forks a plain expression. The following block demonstrates cancellation:

```scala
import zio.blocks.async._

val running2: Async.Running[Int] = Async.start { Thread.sleep(100); 99 }
running2.cancel()
println("Cancelled running2")
```

Expected output:

```text
Cancelled running2
```

- `Async.start { Thread.sleep(100); 99 }` forks the block onto a background worker; the call returns the `Running` handle immediately.
- Calling `cancel` stops the driver loop; if cancellation linearises before the computation finishes, the result is never published to any awaiter. The call is idempotent.

## 9. Putting It Together

Let's combine all six concepts into a single order-processing pipeline. The program bridges a callback-based user-lookup API with `Async.promise`, safely parses an order ID with `Async.attempt`, runs a stock check in the background with `start`, sequences everything in direct style inside `Async.async`, and recovers from any failure with `catchAll`:

```scala title="async-examples/src/main/scala/zio/blocks/async/gettingstarted/CompleteExample.scala"
package zio.blocks.async.gettingstarted

import zio.blocks.async._

/**
 * Complete Example: Order Processing Pipeline
 *
 * Combines all core `Async` concepts in a single order-processing scenario:
 *   - `Async.promise` + `Completer` to bridge a callback-based user-lookup API
 *   - `Async.attempt` to safely parse a potentially malformed order ID
 *   - `.start` + `Async.Running` to check stock availability in the background
 *   - `Async.async { .await }` for direct-style sequential composition
 *   - `.catchAll` to recover from any failure in the pipeline
 *
 * Run with:
 * {{{
 *   sbt "async-examples/runMain zio.blocks.async.gettingstarted.CompleteExample"
 * }}}
 */
object CompleteExample {
  def main(args: Array[String]): Unit = {

    // Step 1: Bridge a callback-based user-lookup API with promise + Completer
    val userLookup: Async[String] = Async.promise[String] {
      val c = summon[Completer[String]]
      new Thread {
        override def run(): Unit = {
          Thread.sleep(10)
          c.succeed("Ada")
        }
      }.start()
    }

    // Step 2: Parse an order ID that might be malformed
    val orderId: Async[Int] = Async.attempt("9001".toInt)

    // Step 3: Start a stock-availability check in the background
    val stockCheck: Async.Running[Boolean] = Async.succeed(true).map { v => Thread.sleep(5); v }.start

    // Step 4: Compose all steps in direct style and recover from any failure
    val report: String = Async.async {
      val user    = userLookup.await
      val id      = orderId.await
      val inStock = stockCheck.await
      s"Order $id for $user: ${if (inStock) "in stock" else "out of stock"}"
    }.catchAll { err =>
      Async.succeed(s"Order failed: ${err.getMessage}")
    }.block

    println(report)
  }
}
```

## 10. Running the Examples

Clone the repository and move into its root directory:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

Each concept's standalone example is shown below. Expand a section to see the source and the command to run it.

<details>
<summary>Concept 1: Ready Values</summary>

```scala title="async-examples/src/main/scala/zio/blocks/async/gettingstarted/ReadyValuesExample.scala" showLineNumbers
package zio.blocks.async.gettingstarted

import zio.blocks.async._

/**
 * Section 1: Ready Values
 *
 * Demonstrates how to wrap an already-available value into an `Async` with
 * `Async.succeed`, transform it using `.map`, and drive it to a result with
 * `.block`.
 *
 * Run with:
 * {{{
 *   sbt "async-examples/runMain zio.blocks.async.gettingstarted.ReadyValuesExample"
 * }}}
 */
object ReadyValuesExample {
  def main(args: Array[String]): Unit = {
    val result: Int = Async.succeed(42).map(_ * 2).block
    println(s"Ready mapped: $result")
  }
}
```

Run it with:

```bash
sbt "async-examples/runMain zio.blocks.async.gettingstarted.ReadyValuesExample"
```

</details>

<details>
<summary>Concept 2: Error Handling</summary>

```scala title="async-examples/src/main/scala/zio/blocks/async/gettingstarted/ErrorHandlingExample.scala" showLineNumbers
package zio.blocks.async.gettingstarted

import zio.blocks.async._

/**
 * Section 2: Error Handling
 *
 * Demonstrates how to create failed async computations with `Async.fail`,
 * recover from failures using `.catchAll`, and reify both outcomes as
 * `Either[Throwable, A]` using `.either`.
 *
 * Run with:
 * {{{
 *   sbt "async-examples/runMain zio.blocks.async.gettingstarted.ErrorHandlingExample"
 * }}}
 */
object ErrorHandlingExample {
  def main(args: Array[String]): Unit = {
    val recovered: String = Async
      .fail(new Exception("oops"))
      .catchAll(_ => Async.succeed("default"))
      .block
    println(s"Recovered: $recovered")

    val observed: Either[Throwable, Int] = Async.fail(new Exception("error")).either.block
    println(s"Observed: $observed")
  }
}
```

Run it with:

```bash
sbt "async-examples/runMain zio.blocks.async.gettingstarted.ErrorHandlingExample"
```

</details>

<details>
<summary>Concept 3: Direct Style</summary>

```scala title="async-examples/src/main/scala/zio/blocks/async/gettingstarted/DirectStyleExample.scala" showLineNumbers
package zio.blocks.async.gettingstarted

import zio.blocks.async._

/**
 * Section 3: Direct Style
 *
 * Demonstrates how to write sequential async code in direct style using
 * `Async.async { ... }` and `.await`. The compiler rewrites `.await` calls into
 * `flatMap` chains, so the code reads like straight-line imperative code
 * without explicit callback nesting.
 *
 * Run with:
 * {{{
 *   sbt "async-examples/runMain zio.blocks.async.gettingstarted.DirectStyleExample"
 * }}}
 */
object DirectStyleExample {
  def main(args: Array[String]): Unit = {
    val summary: String = Async.async {
      val user  = Async.succeed("Ada").await
      val order = Async.succeed(9001).await
      s"${user}'s order ${order}"
    }.block
    println(s"Summary: $summary")
  }
}
```

Run it with:

```bash
sbt "async-examples/runMain zio.blocks.async.gettingstarted.DirectStyleExample"
```

</details>

<details>
<summary>Concept 4: Bridging Exceptions</summary>

```scala title="async-examples/src/main/scala/zio/blocks/async/gettingstarted/AttemptExample.scala" showLineNumbers
package zio.blocks.async.gettingstarted

import zio.blocks.async._

/**
 * Section 4: Bridging Exceptions
 *
 * Demonstrates how `Async.attempt` evaluates a block that may throw and
 * captures any exception as an async failure rather than propagating it as a
 * JVM exception. A successful evaluation produces a ready-value `Async`; a
 * thrown exception produces a failed `Async` recoverable with `.catchAll`.
 *
 * Run with:
 * {{{
 *   sbt "async-examples/runMain zio.blocks.async.gettingstarted.AttemptExample"
 * }}}
 */
object AttemptExample {
  def main(args: Array[String]): Unit = {
    val good: Int = Async.attempt("42".toInt).block
    println(s"Parsed: $good")

    val bad: Either[Throwable, Int] = Async.attempt("oops".toInt).either.block
    println(s"Failed parse: $bad")
  }
}
```

Run it with:

```bash
sbt "async-examples/runMain zio.blocks.async.gettingstarted.AttemptExample"
```

</details>

<details>
<summary>Concept 5: Callback Bridging</summary>

```scala title="async-examples/src/main/scala/zio/blocks/async/gettingstarted/CallbackBridgeExample.scala" showLineNumbers
package zio.blocks.async.gettingstarted

import zio.blocks.async._

/**
 * Section 5: Callback Bridging
 *
 * Demonstrates how to lift a callback-based API into an `Async` using
 * `Async.promise` and a `Completer`. The body of `Async.promise` is a Scala 3
 * context function: `summon[Completer[A]]` retrieves the completer, which can
 * then be captured and called from any thread.
 *
 * Run with:
 * {{{
 *   sbt "async-examples/runMain zio.blocks.async.gettingstarted.CallbackBridgeExample"
 * }}}
 */
object CallbackBridgeExample {
  def main(args: Array[String]): Unit = {
    val result: String = Async
      .promise[String] {
        val c = summon[Completer[String]]
        new Thread {
          override def run(): Unit = {
            Thread.sleep(10)
            c.succeed("hello from callback")
          }
        }.start()
      }
      .block
    println(s"Promise resolved: $result")
  }
}
```

Run it with:

```bash
sbt "async-examples/runMain zio.blocks.async.gettingstarted.CallbackBridgeExample"
```

</details>

<details>
<summary>Concept 6: Forking and Cancellation</summary>

```scala title="async-examples/src/main/scala/zio/blocks/async/gettingstarted/ForkingExample.scala" showLineNumbers
package zio.blocks.async.gettingstarted

import zio.blocks.async._

/**
 * Section 6: Forking and Cancellation
 *
 * Demonstrates how to run a computation on a background worker with `.start`,
 * which returns an `Async.Running[A]` handle. The handle can be joined with
 * `.block` (blocks the caller until the computation completes) or cancelled
 * with `.cancel()` (stops the driver loop before the result is published).
 *
 * Run with:
 * {{{
 *   sbt "async-examples/runMain zio.blocks.async.gettingstarted.ForkingExample"
 * }}}
 */
object ForkingExample {
  def main(args: Array[String]): Unit = {
    // Fork and join
    val running: Async.Running[Int] = Async.succeed(42).map { x => println(s"Running in background: $x"); x * 2 }.start
    val joined: Int                 = running.block
    println(s"Joined: $joined")

    // Fork and cancel
    val running2: Async.Running[Int] = Async.start { Thread.sleep(100); 99 }
    running2.cancel()
    println("Cancelled running2")
  }
}
```

Run it with:

```bash
sbt "async-examples/runMain zio.blocks.async.gettingstarted.ForkingExample"
```

</details>

<details>
<summary>Complete Example: Order Processing Pipeline</summary>

```scala title="async-examples/src/main/scala/zio/blocks/async/gettingstarted/CompleteExample.scala" showLineNumbers
package zio.blocks.async.gettingstarted

import zio.blocks.async._

/**
 * Complete Example: Order Processing Pipeline
 *
 * Combines all core `Async` concepts in a single order-processing scenario:
 *   - `Async.promise` + `Completer` to bridge a callback-based user-lookup API
 *   - `Async.attempt` to safely parse a potentially malformed order ID
 *   - `.start` + `Async.Running` to check stock availability in the background
 *   - `Async.async { .await }` for direct-style sequential composition
 *   - `.catchAll` to recover from any failure in the pipeline
 *
 * Run with:
 * {{{
 *   sbt "async-examples/runMain zio.blocks.async.gettingstarted.CompleteExample"
 * }}}
 */
object CompleteExample {
  def main(args: Array[String]): Unit = {

    // Step 1: Bridge a callback-based user-lookup API with promise + Completer
    val userLookup: Async[String] = Async.promise[String] {
      val c = summon[Completer[String]]
      new Thread {
        override def run(): Unit = {
          Thread.sleep(10)
          c.succeed("Ada")
        }
      }.start()
    }

    // Step 2: Parse an order ID that might be malformed
    val orderId: Async[Int] = Async.attempt("9001".toInt)

    // Step 3: Start a stock-availability check in the background
    val stockCheck: Async.Running[Boolean] = Async.succeed(true).map { v => Thread.sleep(5); v }.start

    // Step 4: Compose all steps in direct style and recover from any failure
    val report: String = Async.async {
      val user    = userLookup.await
      val id      = orderId.await
      val inStock = stockCheck.await
      s"Order $id for $user: ${if (inStock) "in stock" else "out of stock"}"
    }.catchAll { err =>
      Async.succeed(s"Order failed: ${err.getMessage}")
    }.block

    println(report)
  }
}
```

Run it with:

```bash
sbt "async-examples/runMain zio.blocks.async.gettingstarted.CompleteExample"
```

</details>

## 11. What You've Learned

By completing this tutorial, you can now:

- Create ready-value effects with `Async.succeed`, transform them with `map`, and drive them to a result with `block`.
- Handle failures with `Async.fail`, recover them with `catchAll`, and observe both outcomes as data with `either`.
- Write sequential async pipelines in direct style using `Async.async { … .await … }` — the compiler threads the `flatMap` calls for you.
- Lift throw-based Scala code safely into the async error channel with `Async.attempt`.
- Bridge a callback-based API with `Async.promise` and `Completer`, fork background work with `start`, and hold a cancellable `Async.Running` handle.

## 12. Where to Go Next

The [Async reference page](../reference/async.md) documents every method and combinator with full signatures — it is the natural next stop once you are comfortable with the basics in this tutorial.

If your application manages resources that need deterministic cleanup — database connections, file handles, or connection pools — read the [Compile-Time Resource Safety with Scope](./compile-time-resource-safety-with-scope.md) tutorial, which shows how to tie resource lifetimes to lexical scopes and compose them without try/finally boilerplate.
