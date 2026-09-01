# Async

> The `async` module provides `Async[A]`, a lightweight, zero-dependency
> asynchronous effect type for modern Scala. It is designed around a single idea:
> **a ready `Async[A]` is just an `A`**. The happy path allocates nothing — no
> effect tree, no wrapper, no boxing beyond what a generic JVM method already
> requires — so synchronous code composed with `map` / `flatMap` runs at
> hand-written speed while still being able to suspend on genuinely asynchronous
> work.

## Overview

`Async[A]` is an opaque type whose ready representation is the value itself and
whose pending representation is a `Pollable[A]`. You never construct it
directly; you enter the type through constructors and transform it through
extension methods:

- **Constructors** — `Async.succeed`, `Async.fail`, `Async.attempt`,
  `Async.never`, `Async.collectAll`, and the callback bridge `Async.promise`.
- **Transformers** — `map`, `flatMap`, `zip`, `zipWith`, `catchAll`,
  `mapError`, `orElse`, `foldCause`, `either`, `tap`, `ensuring`, `as`, `unit`,
  `*>`, `<*`, `flatten`, and the conditional helpers `when` / `unless`.
- **Direct style** — `Async.async { ... .await ... }` lets you write
  straight-line code with `.await`, rewritten at compile time into a
  non-blocking `flatMap` chain.
- **Running** — `.block` drives an `Async` to its value (blocking on the JVM,
  throwing on a genuinely pending value on JS).
- **Interop** — conversions to and from `scala.concurrent.Future` on every
  platform, Java's `CompletionStage` / `CompletableFuture` on the JVM, and
  `js.Promise` on Scala.js.

On Scala 3 the transformers are zero-cost `inline` extension methods (the ready
path applies your function directly to the underlying value with no `Function1`
allocation). On Scala 2 they are methods on an implicit `AsyncOps` class. The
raw-value representation — a ready `Async[A]` *is* an `A` — holds identically on
both.

## Runnable example

The [`async-examples`](https://github.com/zio/zio-blocks/tree/main/async-examples)
module contains a single self-contained program that walks through the major
features in one file — ready-path composition, direct-style `Async.async` /
`.await`, `zip` / `collectAll`, error handling, `Async.promise`, a custom
[[Pollable]] leaf, `tap` / `ensuring`, cancellable `Async.start` /
`Async.Running`, and JVM `Future` interop.

```bash
sbt "++3.8.3; async-examples/run"
```

The program models a small order-fulfillment pipeline: fetch a user and order,
check warehouse stock, pack a shipment, and audit the steps. The structure is
intentionally linear so you can read it top-to-bottom as a tutorial.

### Direct-style fulfillment

The heart of the demo is straight-line code over suspending steps — no
callback nesting, no manual `flatMap` chains:

```scala
def fulfill(orderId: Int): Async[Shipment] = Async.async {
  val order = fetchOrder(orderId).await
  val lines = order.items.map { item =>
    val stock = stockFor(item.sku).await
    if (stock.onHand < item.qty)
      throw new IllegalStateException(s"short ${item.sku}")
    (item.sku, item.qty)
  }
  Shipment(orderId, lines, carrier = "zio-blocks-express")
}
```

A failure from any `.await` short-circuits the block as a failed `Async`, the
same as throwing inside synchronous code.

### Callback bridge

Legacy APIs that take success/error callbacks lift cleanly through
`Async.promise` (Scala 3 context-function style):

```scala
val json: Async[String] =
  Async.promise[String] {
    // Capture the completer — nested callbacks do not inherit the `?=>` context.
    val completer = summon[Completer[String]]
    legacyHttpGet("/users/42", completer)
  }
```

### Custom asynchronous leaves

When you need a bespoke source of suspension — a socket read, a timer, a
foreign runtime — implement [[Pollable]] and return it from `flatMap` to
**sequence** it, or store it via `Async.succeed` / `map` to keep it as a
**value** (the runtime wraps pollable success values so combinators never
mistake them for suspended computations; note that the top-level drivers —
`.block`, `Async.start`, and the interop converters — do drive a directly
stored pollable for its effects at delivery, settling to the pollable itself).
The showcase includes a `Delayed` pollable that becomes ready after a few
scheduler ticks.

See
[`AsyncShowcaseExample.scala`](https://github.com/zio/zio-blocks/blob/main/async-examples/src/main/scala/async/AsyncShowcaseExample.scala)
for the full program.

## Installation

Add the following to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-blocks-async" % "0.0.51"
```

For cross-platform projects (Scala.js):

```sbt
libraryDependencies += "dev.zio" %%% "zio-blocks-async" % "0.0.51"
```

Supported platforms: JVM and Scala.js, Scala 2.13 and Scala 3.x. The
direct-style `Async.async` block is rewritten by dotty-cps-async on Scala 3
(JVM and older Scala 3 JS), by a hybrid backend on Scala 3.8+ JS (native
`js.async` / `js.await` for direct-position awaits, with the dotty-cps-async
transform as fallback for awaits inside closures, by-name arguments, or nested
methods), and by a hand-written macro on Scala 2.

## Constructors

`Async.succeed` lifts a pure value; `Async.fail` lifts an error; `Async.attempt`
catches a thrown exception and turns it into a failure.

```scala
import zio.blocks.async._

val ready: Async[Int] = Async.succeed(42)
// ready: Async[Int] = 42

val failed: Async[Nothing] = Async.fail(new RuntimeException("boom"))
// failed: Async[Nothing] = zio.blocks.async.Failure@12260721

val caught: Async[Int] = Async.attempt(Integer.parseInt("123"))
// caught: Async[Int] = 123
```

`Async.collectAll` sequences a collection of `Async` values, short-circuiting on
the first failure:

```scala
val all: Async[List[Int]] =
  Async.collectAll(List(Async.succeed(1), Async.succeed(2), Async.succeed(3)))
// all: Async[List[Int]] = List(1, 2, 3)
```

## Evaluation model: eager up to suspension

`Async` is **eager**, not a lazy `IO`. Constructing an `Async` performs all of
its synchronous work immediately — building the value *runs* it, up to the first
point where it genuinely has to wait:

- `Async.attempt(body)` runs `body` now (on the calling thread); `Async.promise`
  runs its setup block now; `Async.async { ... }` runs its synchronous prefix
  (and any **ready** `.await`s) now; `succeed(x).map(f)` runs `f` now. Only a
  combinator applied to an **already-suspended** value defers — its function
  runs when the value is later driven.
- The single genuinely-lazy primitive is a custom [`Pollable`](#low-level-building-blocks-pollable):
  its `poll` runs only when a driver asks for the value. Suspension exists only
  *downstream of* an unresolved `poll`.

This makes the success/ready path allocation-free (no effect tree, no per-step
thunk) — the source of its throughput — at the cost of referential transparency
(building has effects) and cancel-by-drop (use [`Cancelable.cancel`](#eager-cancellable-running-asyncstart-and-asyncrunning)
instead). It sits next to `scala.concurrent.Future` (also eager) rather than
cats-effect `IO` / ZIO (lazy).

### What happens at a pending suspension differs by platform — by design

Once an `Async` hits a genuinely **pending** suspension (an await of a
not-yet-complete value), what advances it follows each platform's *fastest*
suspension mechanism, so the two platforms diverge:

- **JVM (and Scala.js on Scala 3 < 3.8, and Scala 2):** the value is a
  poll-driven `Pollable` with no ambient driver. The continuation after the
  pending suspension runs only when an external driver polls it — `.block`,
  `fa.start`, or an interop runner (`toFuture` / `unsafeRunAsync`). A built-but-
  never-driven block leaves that continuation un-run.
- **Scala.js on Scala 3.8+:** `Async.async`/`.await` compile to native
  `js.async` / `js.await` — a real JavaScript async function whose driver *is*
  the event loop. Once the awaited value settles, the continuation self-resumes
  off the microtask queue even if nothing polls the `Async`. This is the same
  event-loop driving that makes await-heavy direct-style blocks substantially
  faster than the dotty-cps-async backend, so the behavior is intentional, not a
  defect: it is the zero-cost default of the fastest JS suspension primitive.

In practice this is invisible — you always drive an `Async` you build — and the
*value* is identical on every cell. The divergence is observable only by a block
that is constructed, has its awaited value settle, and is then never driven.

## Transforming values

On the ready path the transformers apply your function directly to the
underlying value; only a genuinely pending `Async` takes the suspended slow
path.

```scala
val mapped: Async[Int] = Async.succeed(20).map(_ + 1)
// mapped: Async[Int] = 21

val chained: Async[Int] = Async.succeed(20).flatMap(n => Async.succeed(n * 2))
// chained: Async[Int] = 40

val recovered: Async[Int] =
  Async.fail(new RuntimeException("nope")).catchAll(_ => Async.succeed(-1))
// recovered: Async[Int] = -1
```

### Combining with `zip`

`zip` fuses two `Async` values into a tuple using the `combinators` module's
`Tuples` combiner, so chained zips flatten automatically (`a zip b zip c`
yields `Async[(A, B, C)]`, not `Async[((A, B), C)]`). Use `zipWith` to combine
with an explicit function:

```scala
val zipped: Async[(Int, String)] =
  Async.succeed(1).zip(Async.succeed("two"))
// zipped: Async[Tuple2[Int, String]] = (1, "two")

val summed: Async[Int] =
  Async.succeed(3).zipWith(Async.succeed(4))(_ + _)
// summed: Async[Int] = 7
```

### Error handling

`catchAll` recovers a failure, `mapError` transforms the cause, `orElse`
falls back to another `Async`, and `either` reifies the outcome:

```scala
val asEither: Async[Either[Throwable, Int]] =
  Async.fail(new RuntimeException("x")).either
// asEither: Async[Either[Throwable, Int]] = Left(
//   java.lang.RuntimeException: x
// )

val fallback: Async[Int] =
  Async.fail(new RuntimeException("x")).orElse(Async.succeed(0))
// fallback: Async[Int] = 0
```

### Conditional effects

`when` / `unless` run an `Async` only when a condition holds, discarding its
value. `Async.never` is an `Async` that never completes — useful as a sentinel:

```scala
val maybe: Async[Unit] = when(1 < 2)(Async.succeed(()))
// maybe: Async[Unit] = ()

val skipped: Async[Unit] = unless(1 < 2)(Async.succeed(()))
// skipped: Async[Unit] = ()

val forever: Async[Nothing] = Async.never
// forever: Async[Nothing] = zio.blocks.async.Async$$anon$1@65db325f
```

## Direct style: `Async.async` and `.await`

Inside an `Async.async { ... }` block you can write straight-line code and use
`.await` to extract the value of any `Async`. The block is rewritten at compile
time into a non-blocking `flatMap` / `map` chain — there is no thread blocking
on the happy path. `.await` is **lexically restricted** to `Async.async`
blocks; using it elsewhere is a compile error.

```scala
def loadUser(id: Int): Async[String] = Async.succeed(s"user-$id")
def loadOrders(user: String): Async[List[String]] = Async.succeed(List(s"$user-order"))

val program: Async[Int] =
  Async.async {
    val user   = loadUser(1).await
    val orders = loadOrders(user).await
    orders.size
  }
```

A failure encountered by `.await` short-circuits the block and surfaces as a
failed `Async`, exactly as if you had thrown — `Async.async { Async.fail(t).await }`
is equivalent to `Async.fail(t)`.

`.await` is also supported inside the higher-order-function closures of the
standard strict collections — `List`, `Option`, `Vector`, immutable `Set`,
immutable `Map`, `Array`, immutable `Queue`, and immutable `ArraySeq` — across a
broad set of methods (`map` / `foreach` / `flatMap`, the predicate scans
`find` / `exists` / `forall` / `filter` / `filterNot`, the folds
`foldLeft` / `foldRight` / `reduce` / `reduceLeft`, the prefix scans
`takeWhile` / `dropWhile`, and `collect`). Each is detailed below, with semantics
that match the method's natural meaning (and the Scala 3 backends exactly). A few
positions diverge between Scala 2 and Scala 3 — those are called out explicitly as
**Divergence** notes:

- **`List.map`** is **eager**: strict `map` applies the closure to every element
  first — running all construction-time side effects — producing a
  `List[Async[B]]`, and the awaits are then sequenced left-to-right via
  `Async.collectAll` (fail-fast on the first failure). This mirrors how
  `Array.map(async ...)` composes in JavaScript.
- **`List.foreach`** is **lazy / sequential**: the closure for element `n+1` runs
  only after element `n`'s `.await` completes successfully, and a failed await
  short-circuits the remaining elements. The result is `Unit`.
- **`List.flatMap`** is **lazy / sequential** like `foreach`, but accumulates each
  closure's `IterableOnce` into the result `List`.
- **`Option.map` / `Option.flatMap` / `Option.foreach`**: an `Option` holds at
  most one element, so the eager/lazy distinction collapses to a single
  `Some`/`None` branch — `None` short-circuits (the closure never runs), `Some(x)`
  runs the closure and (for `map`/`flatMap`) rewraps the result; a failed await
  propagates.
- **`Vector` / immutable `Set` / immutable `Queue` / immutable `ArraySeq`**
  (`map` / `flatMap` / `foreach`, plus the builder-backed methods below): **lazy
  / sequential** like `List.foreach` (the closure for element `n+1` runs only
  after element `n`'s await completes; a failure short-circuits the rest). Note
  that `Vector.map` / `Queue.map` / `ArraySeq.map` are lazy — only `List.map` is
  eager (it is the special case backed by dotty-cps-async's `ListAsyncShift`).
  The result **collection type is preserved** (`Vector.map` → `Vector`,
  `Queue.map` → `Queue`, `ArraySeq.map` → `ArraySeq`, `Set.map` → `Set`); for
  `Set`, the *awaited* values are deduplicated.
- **`Array`** (`map` / `flatMap` / `foreach` / `filter` / `takeWhile` /
  `dropWhile` / `foldLeft` / `collect` / `find` / `exists` / `forall`): the
  result is always an `Array[B]` with the **element type preserved, including
  primitives** (e.g. `Array[Int].map(_.toLong)` → a primitive `Array[Long]`).
  `Array.map` is **eager** like `List.map` (a failing await still runs every
  preceding closure); `Array.flatMap` and the rest are **lazy / sequential**.
  The result-building HOFs (`map` / `flatMap` / `filter` / `takeWhile` /
  `collect`) rebuild via `Array.newBuilder`, which needs a `ClassTag[B]` — the
  same one the user's own `Array` HOF already required, so it resolves for any
  concrete element type (an abstract/path-dependent `B` without a `ClassTag` in
  scope is not supported, exactly as the standard-library call would not be).
- **immutable `Map`** (`map` / `flatMap` / `foreach`): **lazy / sequential** over
  the map's `(K, V)` entries. A pair-returning `map`/`flatMap` rebuilds a
  `Map[K2, V2]` (later entries with the same key win); a non-pair `map`/`flatMap`
  widens the result to an `Iterable`, matching the standard library's overload
  choice. `foreach` runs the closure for each entry, returning `Unit`.
- **Short-circuiting predicate scans** (`find` / `exists` / `forall`, predicate
  `A => Boolean`): **lazy / sequential** over any whitelisted receiver — the
  predicate for element `n+1` runs only after element `n`'s await completes, and
  the scan stops at the first decisive element (`exists` → first `true`; `forall`
  → first `false`; `find` → first matching element as `Some`, else `None`).
  `Option.find` is covered on every cell too — on Scala 2 it resolves via the
  `Option`→`Iterable` implicit conversion, which the macro recognizes specifically
  for `find`.
- **`foldLeft`** (op `(B, A) => B`): **lazy / sequential** over any whitelisted
  receiver via `.iterator` — a left fold is inherently sequential (element
  `n+1`'s op needs `n`'s accumulator), so the op for element `n+1` runs only
  after element `n`'s await completes, and a failed await short-circuits the
  rest. The accumulator is threaded through and `foldLeft[B]` returns `B`
  directly (it may differ from the element type), so awaits in the initial
  accumulator are sequenced before the fold.
- **`reduce` / `reduceLeft`** (op `(B, A) => B`): **lazy / sequential** over any
  whitelisted receiver via `.iterator` — `foldLeft` seeded by the FIRST element
  instead of an initial value, so the op for element `n+1` runs only after
  element `n`'s await completes and a failed await short-circuits the rest. A
  single-element receiver returns that element without running the op; an EMPTY
  receiver fails with `UnsupportedOperationException` (catchable via `catchAll`,
  rethrown by `.block`).
- **`foldRight`** (op `(A, B) => B`): **lazy / sequential** but
  **right-associative** — `op(x1, op(x2, ..., op(xn, z)))` — so the op for the
  RIGHTMOST element runs first (the receiver is materialized and drained in
  reverse to keep the await-ordering correct). An empty receiver yields the
  initial accumulator (the op never runs); a failed await short-circuits the
  remaining (right-to-left) elements.
- **`filter` / `filterNot`** (predicate `A => Boolean`): **lazy / sequential**
  over a `List` / `Vector` / `Array` / immutable `Set` / immutable `Queue` /
  immutable `ArraySeq` / `Option` — the predicate for element `n+1` runs only
  after element `n`'s await completes, and a failed await short-circuits the
  rest. The result **collection type is preserved** (`filter` keeps elements
  whose predicate is `true`, `filterNot` those whose predicate is `false`).
  **Divergence:** `Map.filter` / `Map.filterNot` with `.await` is a
  **Scala-2-only superset** — dotty-cps-async has no working `MapOpsAsyncShift.filter`
  and rejects it on Scala 3.
- **`takeWhile` / `dropWhile`** (predicate `A => Boolean`): **lazy / sequential**
  over an ordered receiver (`List` / `Vector` / immutable `Queue` / immutable
  `ArraySeq` / `Array`) — these are **prefix-ordered**, so the predicate for
  element `n+1` runs only after element `n`'s await completes, and the FIRST
  element whose predicate is `false` decides the boundary (`takeWhile` keeps the
  leading run and discards it and the rest; `dropWhile` drops the leading run and
  keeps it and the rest **unconditionally**, never re-evaluating the predicate).
  A failed await short-circuits the rest. The result **collection type is
  preserved**. They are restricted to ordered receivers because a leading-prefix
  predicate is ill-defined on an unordered `Set` / `Map` (and `Option` does not
  provide them); the Scala 2 macro rejects those with an actionable compile
  error.
- **`collect`** (partial function `{ case ... }`): **lazy / sequential** over a
  `List` / `Vector` / `Array` / immutable `Set` / immutable `Queue` / immutable
  `ArraySeq` — keeps the elements the partial function is defined at, mapping
  each through its (awaiting) case body; the case for element `n+1` runs only
  after element `n`'s await completes, and a failed await short-circuits the
  rest. The result **collection type is preserved**. An `Option` receiver is
  supported too: `None` short-circuits without evaluating the partial function,
  `Some(a)` yields `Some(b)` if a case matches, else `None`. A **non-pair
  `Map.collect`** (whose case bodies yield a `B`, so the result is an
  `Iterable[B]`) is supported on every cell. The case guard runs exactly once per
  element (Scala 2). A `.await` in a case GUARD is rejected.
  **Divergence:** a **pair-yielding `Map.collect`** (whose case bodies yield
  `(K2, V2)` pairs, so the result is a `Map[K2, V2]`) is **unsupported on every
  cell** — dotty-cps-async has only an `IterableOpsAsyncShift.collect[F, B]`
  shift (no Map-specific one), so the `Map`-returning overload is a compile error
  on Scala 3, and the Scala 2 macro rejects it to stay at parity. Rewrite it as
  `m.toVector.collect { case ... => k -> v.await }.toMap`.

These behave identically across Scala 2/3 and JVM/JS **except** for the handful of
positions flagged **Divergence** above (`Map.filter` / `filterNot` is a
Scala-2-only superset; a pair-yielding `Map.collect` is unsupported everywhere).
Because Scala desugars
for-comprehensions over a `List` / `Option` / `Vector` / `Set` / `Map` into these
methods,
single- and multi-generator `for` comprehensions with `.await` work too
(`for ... yield` → `map`; nested generators → `flatMap`/`map`; `for { ... }`
without `yield` → `foreach`; a guard `if` → `withFilter`):

```scala
val pairs: Async[List[Int]] = Async.async {
  for {
    i <- List(1, 2)
    j <- List(10, 20)
  } yield Async.succeed(i + j).await
} // List(11, 21, 12, 22)
```

> **Scala 2 limitation (current):** the Scala 2 macro supports `.await` in
> sequential statements, `if` / `match` / `while` / `try`-`catch`-`finally`,
> `throw`, assignments, `List` / `Option` / `Vector` / `Array` / immutable `Set` /
> immutable `Queue` / immutable `ArraySeq` / immutable
> `Map` `map` / `foreach` / `flatMap` closures, the short-circuiting predicate
> scans `find` / `exists` / `forall`, `filter` / `filterNot`, `foldLeft`,
> `foldRight`, and `reduce` / `reduceLeft` over
> those receivers, the prefix-ordered `takeWhile` / `dropWhile` over ordered
> receivers (`List` / `Vector` / immutable `Queue` / immutable `ArraySeq` /
> `Array`), `collect` over builder-backed receivers (`List` / `Vector` / `Array`
> / immutable `Set` / immutable `Queue` / immutable `ArraySeq`), and the
> for-comprehensions that desugar to the former (including guards), but **rejects**
> `.await` inside other function
> literals / higher-order-function arguments (and HOFs over collections other than
> those whitelisted families), with an actionable compile error. Those positions
> are supported on Scala 3. The whitelisted set above is the **final, stable**
> Scala 2 contract for the standard strict collections; positions outside it are
> intentionally unsupported on Scala 2 (a custom collection, or `.await` inside an
> arbitrary user lambda passed to a third-party HOF, cannot be rewritten without a
> shift typeclass the Scala 2 macro deliberately does not depend on).
>
> Conversely, the Scala 2 macro is a strict superset for some guard shapes that
> dotty-cps-async on Scala 3 currently rejects: *multiple* `List`
> for-comprehension guards (chained `withFilter`), and *any* `Option`
> for-comprehension guard (DCA has no `AsyncShift[Option#WithFilter]`). Single
> `List` guards behave identically on every cell.

## The callback bridge: `Async.promise`

`Async.promise` builds an `Async` from a callback-style API. You receive a
`Completer` and call `succeed` / `fail` when the result arrives. Completion is
one-shot — the first `succeed` or `fail` wins; later calls are silent no-ops. If
the body completes the completer synchronously, the result collapses to a bare
value with no `Pollable` allocation.

On Scala 3 the completer is supplied via a context function, so you can call the
top-level `succeed` / `fail` helpers directly:

```scala
import zio.blocks.async._

val fromCallback: Async[Int] =
  Async.promise[Int] {
    // register a callback with some external system, then:
    succeed(42)
  }
```

On Scala 2 the body receives the `Completer` explicitly; mark it `implicit` to
use the same top-level `succeed` / `fail` helpers, or call its methods directly:

```scala
// Scala 2
Async.promise[Int] { implicit c => succeed(42) }
Async.promise[Int] { c => c.succeed(42) }
```

## Running an `Async`

`.block` drives an `Async` to its value. A ready value returns immediately. A
pending value blocks the calling thread on the JVM (Loom-friendly) and throws
on JS, where the platform cannot block. Use `.block` only at the edge of your
program, never on a scheduler/reactor thread.

```scala
val result: Int = Async.succeed(20).map(_ + 1).block
// result: Int = 21
```

### Eager, cancellable running: `Async.start` and `Async.Running`

The `fa.start` extension eagerly drives an already-built `Async` without
blocking and returns a `Running[A]` handle — itself an `Async[A]` you can poll,
compose, or cancel. Compose with `either`, `tap`, `foldCause`, and the other
operators **before** `start` to observe or transform the outcome:

```scala
import zio.blocks.async._

val running: Async.Running[Either[Throwable, Int]] =
  Async.succeed(1).map(_ + 1).either.tap {
    case Right(value) => Async.succeed(println(s"done: $value"))
    case Left(cause)  => Async.succeed(println(s"failed: $cause"))
  }.start

running.cancel() // idempotent; no-op once the run has completed
```

The companion `Async.start(body)` is a single by-name method that evaluates
`body` on a background worker (JVM) or microtask (JS) and returns a `Running`
for the result — the `Async` analogue of `Future.apply`. It captures a throwing
body (even a statically `Nothing`-typed one such as `Async.start(sys.error(...))`)
as a failed run rather than letting it escape at the call site. (Driving an
existing `Async` value is the `fa.start` extension above, not `Async.start(fa)`,
which would treat `fa` as a by-name body to evaluate.)

For an already-ready `Async`, observers composed before `start` run synchronously
on the calling thread. For a suspended `Async`, driving proceeds on a daemon
worker thread on the JVM, or via microtasks on Scala.js. `cancel()` is
driver-level only: it stops the poll loop and suppresses publishing a terminal
value, but does not abort an in-flight leaf (socket, timer, JS promise).

#### Fanning one `Async` out to several consumers

To deliver one `Async`'s result to multiple consumers, **start it once and share
the `Running` handle** — `Running` publishes its outcome through an atomic, so
the underlying `Async` (and any side effects in `map`/`flatMap`/`tap`) is driven
exactly once no matter how many consumers poll, block, or compose on the handle:

```scala
import zio.blocks.async._

val shared: Async.Running[Int] = Async.succeed(1).map(_ + 1).start
val a: Int = shared.block // both observe the one result;
val b: Int = shared.block // the `+ 1` ran once, on the worker
```

Do **not** instead drive the same raw `Async` from two places at once (two
separate `fa.start`s on the same `fa`, or `fa.start` racing `fa.block`). On the
JVM that polls the same combinator concurrently, which is **undefined**: a
`map`/`flatMap`/`tap` function may run more than once, and a `collectAll` batch
may observe its drain buffer mid-update. This matches `Pollable`'s contract that
re-polling a settled value is undefined and platform-specific — it cannot arise
on single-threaded Scala.js. Sequential re-use (re-polling or composing after an
earlier drive has settled) is fine; only *concurrent* re-driving of the raw
value is not.

## Interop

`Async` converts to and from the platform's standard async types, preserving
both success and failure. Ingress lives on the `Async` companion
(`Async.fromFuture`, `Async.fromCompletionStage`, `Async.fromJsPromise`) and
egress lives on extension methods (`fa.toFuture`, `fa.toCompletableFuture`,
`fa.toJsPromise`). `scala.concurrent.Future` conversion is available on both
platforms; the JVM additionally offers Java `CompletionStage` /
`CompletableFuture`, and Scala.js offers `js.Promise`.

On the JVM:

```scala
import zio.blocks.async._
import scala.concurrent.{ExecutionContext, Future}
import java.util.concurrent.CompletableFuture

implicit val ec: ExecutionContext = ExecutionContext.global

val fromFut: Async[Int]             = Async.fromFuture(Future.successful(1))
val toFut: Future[Int]              = Async.succeed(1).toFuture
val fromStage: Async[Int]           = Async.fromCompletionStage(CompletableFuture.completedFuture(1))
val toStage: CompletableFuture[Int] = Async.succeed(1).toCompletableFuture
```

On Scala.js, the companion provides `Async.fromFuture` / `Async.fromJsPromise`
and the egress extensions provide `fa.toFuture` / `fa.toJsPromise` for native
`scala.scalajs.js.Promise` interop.

## Low-level building blocks: `Pollable`

Most code should use the constructors and `Async.promise`. For custom
asynchronous leaves you can implement a `Pollable[A]` directly. `poll(onComplete)`
returns the ready value (or a `Failure`) when available, or a `Pollable`
(commonly `this`, optionally a replacement representing the rest of the
computation — drivers and combinators direct their next poll at whichever
pollable was returned) when still pending; a pending pollable must arrange to
call `onComplete.run()` once progress can be made, prompting the scheduler to
re-poll.

### Combinator chain depth

Combinator continuations poll their children recursively without a trampoline
(a deliberate trade: the poll path stays allocation- and indirection-free). The
determinant of depth-safety is whether driving has to unwind a deep chain of
combinators in **receiver position** over a value that is still **pending** —
not whether the chain was written iteratively or recursively:

- **Over a ready source, `flatMap` / `map` / `zipWith` chains are depth-safe to
  any length.** When the receiver is already a value, each step resolves
  eagerly and collapses — no `Pollable` is retained — so `var fa = …;
  while (…) fa = fa.flatMap(g)` (and the `.map` form) consume constant stack
  regardless of length (verified into the millions).
- **Over a pending source, a deep receiver-position chain is NOT depth-safe.**
  When the receiver stays pending, each `fa.flatMap(g)` / `fa.map(g)` /
  `fa.zipWith(...)` wraps the previous pending value, so driving descends one
  stack frame per level before anything settles and overflows around
  default-JVM-stack depths of a few tens of thousands (~50–100k). This is true
  for **both** a recursive shape (`def loop(n) = src.flatMap(_ => loop(n-1))`)
  **and** an iterative accumulation (`fa = fa.flatMap(_ => src.flatMap(...))`) —
  the syntax doesn't matter, the pending receiver spine does.
- **`Async.collectAll` and direct-style `Async.async` `while` loops are
  depth-safe even over pending sources.** `collectAll` is a single `Pollable`
  that iterates its elements internally (no receiver spine), and the
  `Async.async` loop rewrite advances one iteration per driver poll rather than
  pre-building a deep chain — both verified at 200k+ pending steps.

So: to sequence a large, *pending-heavy* workload, reach for `collectAll` or an
`Async.async` `while` loop, not a hand-built `flatMap`/`map`/`zipWith` tower over
a pending value. (`Future` avoids this overflow for any shape only because it
bounces every `flatMap` through its `ExecutionContext`; `Async` skips that hop
for speed and accepts the depth bound instead.)

## Cross-platform and cross-version notes

| Feature                          | JVM | JS | Scala 2.13 | Scala 3.x | Notes                                                   |
|----------------------------------|-----|----|------------|-----------|---------------------------------------------------------|
| Constructors & transformers      | ✅  | ✅ | ✅         | ✅        | Identical behavior everywhere                           |
| `Async.async` / `.await`         | ✅  | ✅ | ✅         | ✅        | DCA (Scala 3), native `js.async`/`js.await` for direct-position awaits with DCA fallback for closure/by-name awaits (3.8+ JS), macro (Scala 2); `.await` in the standard strict-collection HOF closures (`List` / `Option` / `Vector` / `Set` / `Map` / `Array` / `Queue` / `ArraySeq`: `map`/`foreach`/`flatMap`/`filter`/`collect`/`fold*`/`reduce*`/`takeWhile`/`dropWhile`/`find`/`exists`/`forall`) and their for-comprehensions is supported on every cell, except a few explicitly-noted divergences (`Map.filter` Scala-2-only; a pair-yielding `Map.collect` unsupported everywhere) — see the HOF section above |
| `.block` on a pending value      | ✅  | ❌ | ✅         | ✅        | Blocks on JVM; throws on JS (cannot block)              |
| `Async.start` / `Async.Running`       | ✅ | ✅ | ✅        | ✅        | Eager non-blocking runner; worker thread (JVM) / microtask (JS) |
| `Future` interop                 | ✅  | ✅ | ✅         | ✅        | `Async.fromFuture` / `fa.toFuture` on both platforms |
| `CompletionStage` interop        | ✅  | ❌ | ✅         | ✅        | JVM-only (`fromCompletionStage` / `toCompletableFuture`) |
| `js.Promise` interop             | ❌  | ✅ | ✅         | ✅        | JS-only (`fromJsPromise` / `toJsPromise`)              |

The core `Async` API is identical across all platforms and Scala versions by
design; platform interop APIs are intentionally platform-specific as shown
above. The cross-platform test suite fails if any user-visible core behavior
diverges.

## See Also

- [Runnable example](#runnable-example) — `async-examples` single-file showcase
- [Combinators](./combinators.md) — `Async#zip` uses the `Tuples` combiner for
  automatic tuple flattening.
