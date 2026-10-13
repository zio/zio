# Async

> Reference for the ZIO Blocks async module: Async[A], Pollable, Completer, Async.Running, and Cancelable.

The `async` module provides `Async[A]` — a small asynchronous effect type for Scala 2.13 and Scala 3, targeting both JVM and Scala.js.

An `Async[A]` value is a computation that either yields an `A` or fails with a `Throwable`. Unlike a lazy effect type, building one *runs* it: the synchronous work happens as you construct it, and only a computation that genuinely has to wait for something is left pending. [Evaluation model](#evaluation-model) explains where that line falls.

Conceptually, an `Async[A]` is one of three things — a value that is already available, a failure that has already happened, or a computation that will complete later:

```scala
// The mental model, not the real encoding.
enum Async[+A]:
  case Ready(value: A)                 // already available
  case Failed(cause: Throwable)        // already failed
  case Suspended(source: Pollable[A])  // completes later, via a callback
```

The real definition is a type alias whose runtime representation is `Any`. That is a performance decision, not a modelling one: a ready value *is* its own `Async`, so the common path allocates nothing and boxes nothing. Reach for the three cases above when reasoning about behaviour, and let the encoding stay invisible — no combinator in this module requires you to know it.

Suspension is where the other types enter. A `Pollable[A]` is the extension point that produces a not-yet-available result, [`Completer`](#completer) is the ready-made `Pollable` for bridging callbacks, [`Async.Running`](#asyncrunning) is a `Pollable` you can also cancel, and [`Cancelable`](#cancelable) is that cancellation interface on its own.

The type is aimed at infrastructure-level code: places that need a first-class asynchronous value, on both the JVM and Scala.js, without taking on an ecosystem to get one. Where a fuller effect system offers an environment type, a typed error channel and fibers, `Async` offers a computation, a `Throwable`, and a handle you can cancel — and in exchange stays cheap enough to use on paths that are usually synchronous.

## Installation

Add the module to your build:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-async" % "0.0.56"
```

In a cross-built project, use `%%%` so the same line resolves for both JVM and Scala.js:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-async" % "0.0.56"
```

The module publishes for JVM and Scala.js, on Scala 2.13 and Scala 3. That single coordinate is all you add: it brings `zio-blocks-combinators` with it, along with `dotty-cps-async` on Scala 3 or `scala-reflect` on Scala 2, both of which power the direct-style rewrite.

## Overview

`Async[A]` is the type you will spend nearly all your time with. Every way of creating a value, every way of transforming one, and every way of running one produces or consumes an `Async[A]`. If you only learn this type, you can already write complete programs — the rest of the module exists to feed values into it or to control one that is already running.

`Completer[A]` is the type you reach for next, and the reason is a problem you have almost certainly hit: some library hands you a result through a callback rather than returning it. `Async.promise` gives you a `Completer`, you pass that to the callback, and you get back an `Async[A]` that completes when the callback fires. From that point on it behaves like any other `Async[A]`, so the callback-based API disappears into ordinary code.

`Async.Running[A]` appears when you start work without waiting for it. Calling `.start` on an `Async[A]` begins the computation immediately and hands you a `Running` as a receipt. Keep it, and you can wait for the result later, run several pieces of work at once and collect them all, or stop the work early.

`Cancelable` is that last ability on its own — a single way to say "stop this." `Async.Running` provides it, and so can anything else you write that needs to be stoppable.

`Pollable[A]` is the one type most programs never touch. It is the extension point for teaching the module about a brand-new source of delayed results — a timer, a socket read, a platform-specific callback. Implementing one makes your source usable anywhere an `Async[A]` is expected. Reach for it only when you are wiring up something genuinely new; for ordinary callback bridging, `Async.promise` and a `Completer` are the right tools.

## Evaluation Model

`Async[A]` is **eager**, not a lazy `IO`. Building one performs its synchronous work straight away — constructing the value *runs* it, up to the first point where it genuinely has to wait:

```scala
import zio.blocks.async._

// "computing" is printed by this line, not by the one below it.
val fa: Async[Int] = Async.attempt { println("computing"); 42 }
val n: Int         = fa.block  // the value was already there; nothing more runs
```

The same is true across the module. `Async.promise` runs its setup block when you call it, and `Async.succeed(x).map(f)` applies `f` immediately, because `x` is right there. Only a combinator applied to a value that is *already suspended* defers: then the function is kept and runs when a driver settles the value.

A direct-style block splits the same way, at its first genuine wait. Everything above that line runs as you build the value; everything below it is kept for later:

```scala
Async.async {
  val cfg  = loadConfig()             // ┐
  logger.info("starting")             // ├ runs now, as this value is built
  val base = compute(cfg)             // ┘
  val row  = fetchRow(base).await     // the first genuine wait
  transform(row)                      // runs later, when a driver settles it
}
```

An `await` whose value is ready before it is asked does not count as a wait. It hands the value over on the spot and the block carries straight on, so the dividing line is not the first `await` you wrote — it is the first one that has nothing to give yet:

```scala
Async.async {
  val a = Async.succeed(1).await   // already a value: no wait, keep going
  val b = compute(a).await         // also ready: no wait, keep going
  val c = fetchFromNetwork().await // nothing yet — the block stops here
  a + b + c                        // deferred, along with everything below
}
```

The practical consequence is that you cannot find the pause by counting `await`s. A block whose values are all ready pauses nowhere and finishes as you construct it; a block whose *first* `await` is a network call has run none of the lines below it by the time `Async.async { … }` hands you a value.

So where does waiting come from at all? From exactly one place: a [`Pollable`](#pollable) that was asked for its value and answered *not yet*. That is the only thing in the module that can make a computation pending. Everything else already has its answer — `succeed` has a value, `fail` has a cause, `attempt` has run, `map` merely applies a function.

Waiting then spreads in one direction only: to the combinators stacked on top of that pending value, which cannot produce a result until it does.

```scala
val c = new Completer[Int]                    // a Pollable; not completed yet
val a = c.peek.map(_ + 1)                     // above a pending value → deferred
val b = a.flatMap(n => Async.succeed(n * 2))  // still above it → deferred

val d = Async.succeed(1).map(_ + 1)           // no pending value anywhere → already ran
```

`a` and `b` wait only because they sit on top of a `Completer` nobody has completed. `d` shares none of that history, so it is simply the number `2` — the `+ 1` happened as the line was evaluated.

One consequence catches people out. If a function you pass to `map`, `flatMap` or `tap` throws, and the value it is applied to is *ready*, that function runs as you build the value — so the exception escapes at that line rather than becoming a failed `Async` you can `catchAll`. Only `Async.attempt` turns a throw into a `Failure`; the combinators do not:

```scala
Async.succeed(text).map(_.toInt)                          // throws here if text is not a number
Async.succeed(text).flatMap(s => Async.attempt(s.toInt))  // fails as an Async instead
```

The "only one place" part is what distinguishes `Async` from a lazy effect type. `map`, `flatMap` and `zipWith` do not themselves defer anything, and building a chain does not create a plan to be executed later. If you cannot point at a value still waiting to be completed, nothing in your chain is pending: it has all already run.

Two things follow. Any pending computation can be traced to the thing it is waiting on — a `Completer` for a callback bridge, an [`Async.Running`](#asyncrunning) for started work, or your own `Pollable`. And the waiting is temporary and forward-only: when that value is completed, everything above it becomes ready, and nothing below it was ever held up, because that code had already run.

This is a deliberate trade. Eager evaluation is what keeps the ready path allocation-free — no effect tree, no per-step thunk, none of the wrapper objects most effect types build — and that is where the throughput comes from. It is also why `Async[A]` costs close to nothing on a mostly-synchronous path: when there is nothing to wait for, chaining operations onto a value just runs them.

The costs are real too. Building a value has effects, so `Async` is not referentially transparent: you cannot move a construction around, or replace a value with the expression that produced it, and be sure the program still means the same thing.

And letting go of a value does not stop it. With a lazy effect type, discarding an unused effect discards the work with it, because none of it had happened yet. Here the work is already under way:

```scala
val running = Async.start(uploadHugeFile())

// Reassigning or forgetting `running` does not stop the upload. The worker
// keeps going and the bytes keep moving; you have only thrown away your
// ability to watch it or stop it.
```

Stopping requires asking, through [`Cancelable#cancel`](#cancelable), and that reaches less far than you might expect.

The handle is your only route to that request. There is no registry of running computations to consult and no supervisor to ask, so a handle you have dropped cannot be recovered: that work becomes unstoppable for the rest of the process, and it finishes on its own schedule, holding its thread and its socket until it does. Nothing counts how many observers are left, so nothing notices when the last one goes away.

The rule that falls out is to decide at `start` time whether this work might ever need stopping. If it might, give the handle somewhere to live:

```scala
import java.util.concurrent.atomic.AtomicReference

class Uploader {
  private val current = new AtomicReference[Async.Running[Unit]]()

  def begin(): Unit = {
    val previous = current.getAndSet(Async.start(uploadHugeFile()))
    if (previous ne null) previous.cancel()  // stop watching the one displaced
  }

  def abort(): Unit = {
    val running = current.getAndSet(null)
    if (running ne null) running.cancel()    // possible only because it was kept
  }
}
```

Note what `begin` has to do on the way past: replacing a handle means cancelling the one it displaces, or that upload becomes unstoppable while still running. The `AtomicReference` is there because the field is reachable from more than one thread — `abort` may well be called while `begin` is assigning.

A [`Using`](#integration-points) block does the same job when the work is confined to a scope. And if the answer is that it never needs stopping, dropping the handle is fine — that is fire-and-forget, chosen deliberately rather than by accident.

In all of this `Async` sits beside `scala.concurrent.Future`, which is also eager, rather than beside cats-effect `IO` or ZIO.

### Depth Limits on Waiting Values

Call `map` on a value that is still waiting and you get back a wrapper: it remembers the original value and the function you gave it. Ask that wrapper for a result and it has to ask the original first, because until the original produces something there is nothing to apply the function to. That question is an ordinary method call, so the wrapper is left part-way through its own work — holding its place on the call stack — while the value underneath answers.

Stack several and each repeats the pattern. Take `c.peek.map(f).flatMap(g).map(h)`, where `c` is a `Completer` nobody has completed yet. Asking the outermost wrapper sets off a chain of questions inward, and every one of them waits where it stands:

```
map h asks …                  still waiting
 └─ flatMap g asks …          still waiting
     └─ map f asks …          still waiting
         └─ c answers "not yet" — and that travels back out through all three
```

None of them can finish until the innermost one answers, so all of them are held open at the same time. A chain written N deep costs N held-open calls, every single time it is asked.

Most effect systems avoid that with a trampoline: rather than calling its child directly, each step returns a small object meaning *"do this next"* to a loop that keeps running steps until one produces a value. One loop frame serves any depth. The price is paid on every step of every poll — an object allocated to describe the step, and a dispatch through the loop instead of a direct call.

There is no third option, so the choice was between the two:

| Approach     | Depth                     | Cost per step                                      |
|--------------|---------------------------|----------------------------------------------------|
| Trampoline   | Unlimited                 | An object allocated, and a dispatch, on every poll |
| Direct calls | Limited by the call stack | Nothing                                            |

`Async` takes the second. That is why nothing is allocated while polling, and it is also why a long enough chain over a waiting value ends in a `StackOverflowError` rather than a slowdown — the ceiling is the bill for the speed, not an oversight.

You are unlikely to meet it by hand. A handful of `map` and `flatMap` calls around a network request is nowhere near the limit. It becomes a real risk when the length of the chain is decided by *data* — one `flatMap` per row, per file, per retry — because then the depth is however large the input happens to be, and code that is comfortable in a test can overflow in production on a bigger batch.

Whether you are anywhere near the limit comes down to a single question: is the chain being built on top of a value that is still waiting?

- **On a value that is already there, chains are safe at any length.** Each step runs as you write it and gives back a plain value again, so nothing is left holding anything open. A loop like `var fa = …; while (…) fa = fa.flatMap(g)` stays flat no matter how many times it goes round — the suite takes it to a million.
- **On a value that is still waiting, a long chain is not safe.** Every `fa.flatMap(g)`, `fa.map(g)` or `fa.zipWith(…)` wraps the one before it, so asking for the result opens one call per wrapper before anything can answer, and the program runs out of stack somewhere around 50,000–100,000 on a default JVM. How you wrote the loop makes no difference — a recursive `def loop(n) = src.flatMap(_ => loop(n - 1))` and an iterative `fa = fa.flatMap(_ => src.flatMap(…))` build the same stack of wrappers.
- **`Async.collectAll` and a `while` loop inside `Async.async` stay safe even when the values are still waiting** — both are tested at 50,000 such steps. `collectAll` is a single step that walks the collection itself, stacking no wrappers at all, and the direct-style loop runs one turn each time it is asked rather than building the whole chain in advance.

So for long, wait-heavy work, reach for `collectAll` or an `Async.async` `while` loop instead of a hand-built tower of `flatMap`. `Future` never runs into this, but only because it sends every `flatMap` through an `ExecutionContext`; `Async` skips that hop to stay fast and takes the depth limit instead.

### Pending Suspensions Differ by Platform

Say a computation has hit a real wait. When the thing it waits for finally arrives, what makes the rest of the block run? Each platform answers with whatever its own runtime does fastest, so the answers differ:

- **JVM, and Scala.js on Scala 2 or Scala 3 before 3.8** — nothing runs it for you. The value sits there until something asks it for a result: `.block`, `.start`, or an interop converter. Build a value and never drive it, and the code after the wait does not run late — it never runs at all.
- **Scala.js on Scala 3.8 and later** — the block compiles into a real JavaScript async function, and JavaScript already has something whose job is resuming those: the event loop. It is always running, you did not start it, and you cannot opt out of it. So once the awaited value arrives, your code carries on by itself, whether or not anyone is watching.

```scala
val fa = Async.async { record(fetchRow().await) }

// JVM: fetchRow may well finish, but `record` has not run — nothing drove fa.
// Scala.js 3.8+: once fetchRow finishes, `record` runs anyway.
```

You are unlikely ever to see this. Values get built in order to be used, and the moment you `block` on one, `start` it, or hand it to a `Future`, both platforms behave identically and produce the same result. Noticing the difference takes a peculiar shape: build a block, let the thing it waits for arrive, then never drive it — a program that has already gone wrong, since it constructed work and then discarded it.

It is worth documenting because it changes *when side effects happen*. If the code after a wait prints, writes a file, or bumps a counter, Scala.js may do that without you driving anything, while the JVM will not. Cross-platform code that leans on "this has not run yet" is leaning on something true in only one of the two.

## How They Work Together

A computation moves through four phases: construct leaf values, compose them, drive the result, then observe or cancel, as shown in the following flow diagram:

```
┌─ 1. CONSTRUCT — the synchronous part runs now ─────────────────┐
│   Async.succeed(a)       a value you already have              │
│   Async.fail(t)          a failure you already have            │
│   Async.attempt { … }    runs the block, here, on this thread  │
│   Async.promise { … }    runs its setup block now              │
│   new Pollable[A] { … }  the one genuinely deferred leaf       │
└────────────────────────────────────────────────────────────────┘
                          │
                          ▼
        Async[A]  ──  ready, failed, or waiting on something
                          │
                          ▼
┌─ 2. COMPOSE — runs now if ready, defers if not ────────────────┐
│   map    flatMap    zipWith    tap                             │
│   catchAll    ensuring    collectAll                           │
│                                                                │
│   on a ready value the function runs immediately;              │
│   on a pending one it is kept for the driver to run            │
└────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─ 3. DRIVE — settle whatever is still pending ──────────────────┐
│   .block             .start            .toFuture               │
│   wait right here    run in the        .toJsPromise            │
│                      background        hand it to the          │
│        │                  │            platform                │
│        ▼                  ▼                                    │
│   the A, or          Async.Running[A]                          │
│   the Throwable      — your receipt                            │
└────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─ 4. OBSERVE or CANCEL — using the receipt ─────────────────────┐
│   .block  .flatMap  .zipWith    wait for it, or compose more   │
│   .cancel()                     stop it (via Cancelable)       │
└────────────────────────────────────────────────────────────────┘
```

Three of these types are closely related, and seeing why makes the module much smaller than it first looks. `Pollable[A]` answers one question — *is the result ready yet?* — and anything that can answer it is a `Pollable`:

```
Pollable[A]   —  "a result that is not here yet"
   │
   ├── Completer[A]       you complete it yourself, once, from a callback
   ├── Async.Running[A]   work that is already running; can also be stopped
   └── Failure            a computation that has already failed
```

That shared parent is what lets all three be used interchangeably. Wherever an `Async[A]` is expected, you can supply any of them, and every combinator — `map`, `flatMap`, `zipWith`, and the rest — works on the result without knowing or caring which one it is.

`Failure` is on the list because failing is just another way of being finished — a computation that has failed is not waiting for anything. [`Failure`](#failure) describes what that means for the combinators downstream of it.

You will use `Completer` and `Async.Running` constantly, and `Failure` mostly without naming it. Writing your own `Pollable` is the rare case, reserved for teaching the module about a new source of delayed results.

Two details the diagram leaves out. Composing over a ready value is allocation-free, while composing over a waiting one allocates a `Pollable` that the driver walks poll by poll. And the handle from phase 3 is itself an `Async`, which is what makes phase 4 ordinary composition rather than a separate API.

The following snippet grounds all four phases in an example: two off-thread `Completer` completions are composed with `zipWith` and driven by `block`:

```scala
import zio.blocks.async._

def delayed[A](value: A, ms: Long): Async[A] = {
  val c = new Completer[A]
  val t = new Thread(new Runnable {
    def run(): Unit = { Thread.sleep(ms); c.succeed(value) }
  })
  t.setDaemon(true)
  t.start()
  c.peek  // the Completer is itself an Async[A]
}

// Phase 1 and 2: construct two off-thread leaves and compose with zipWith
val r: Async[Int] = delayed(3, 30).zipWith(delayed(4, 5))(_ + _)

// Phase 3: drive — parks the calling thread until both off-thread wakers fire
val result: Int = r.block  // => 7
```

A second example shows what `Async.collectAll` guarantees: the results come back in the order you listed the computations, not the order they happened to finish. To make that visible, the delays below are deliberately reversed — the first element takes the longest, the last finishes almost immediately:

```scala
import zio.blocks.async._

// Completes with `value` after `ms`, on another thread.
def delayed[A](value: A, ms: Long): Async[A] = {
  val c = new Completer[A]
  val t = new Thread(new Runnable {
    def run(): Unit = { Thread.sleep(ms); c.succeed(value) }
  })
  t.setDaemon(true)
  t.start()
  c.peek
}

val ordered: Async[List[Int]] = Async.collectAll(List[Async[Int]](
  delayed(1, 90),  // finishes third
  delayed(2, 45),  // finishes second
  delayed(3, 5)    // finishes first
))

// Completion order is 3, 2, 1 — the list is still 1, 2, 3.
val results: List[Int] = ordered.block  // => List(1, 2, 3)
```

Without that guarantee you would have to tag each computation and re-sort the results yourself. Because `collectAll` keeps the positions, you can zip the output against the input list — or pattern-match on it positionally — and trust that element *n* belongs to computation *n*.

## Operations

Everything you can do with an `Async[A]`: make one, transform it, combine it with another, recover from a failure, and eventually run it. 

One property is worth carrying into the signatures below. `Async[A]` is declared `Async[+A]`, which makes it *covariant*: whenever `B` is a subtype of `A`, an `Async[B]` counts as an `Async[A]`. That matters because `Async.fail` and `Async.never` have no value to offer, so their type is `Async[Nothing]` — and `Nothing` is a subtype of every type in Scala. An `Async[Nothing]` is therefore an `Async[String]`, an `Async[User]`, an `Async` of anything at all:

```scala
import zio.blocks.async._

case class User(id: Int, name: String)

def fetchUser(id: Int): Async[User] =
  if (id < 0) Async.fail(new IllegalArgumentException("bad id"))  // Async[Nothing]
  else Async.succeed(User(id, "sam"))                             // Async[User]

val forever: Async[User] = Async.never                            // Async[Nothing] fits too
```

Without covariance neither of those would compile against the declared `Async[User]`, and you would be writing `Async.fail[User](…)` or a cast at every failure. This is a property you notice only through the errors it saves you from.

### Creating Values

The companion object provides factories for constructing leaf `Async[A]` values:

```scala
object Async {
  def succeed[A](a: A): Async[A]
  def fail(cause: Throwable): Async[Nothing]
  def attempt[A](body: => A): Async[A]
  def promise[A](body: Completer[A] => Unit): Async[A]  // shape differs on Scala 3; see Completer
  def start[A](body: => A): Async.Running[A]
  val never: Async[Nothing]
  def collectAll[A](as: IterableOnce[Async[A]]): Async[List[A]]
  def async[A](body: A): Async[A]                       // rewritten in place: a macro on Scala 2,
                                                        // a transparent inline def on Scala 3.
                                                        // See the Direct Style pattern

  // JVM only
  def fromFuture[A](future: scala.concurrent.Future[A]): Async[A]
  def fromCompletionStage[A](cs: java.util.concurrent.CompletionStage[A]): Async[A]
}
```

`Async.succeed` lifts a pure, immediately-available value into an `Async[A]`:

```scala
import zio.blocks.async._

val ready: Async[Int] = Async.succeed(42)
val result: Int = ready.block  // => 42
```

`Async.fail` creates a terminal failure; [`Failure`](#failure) covers how it short-circuits the rest of a chain:

```scala
import zio.blocks.async._

val boom: Async[Int] = Async.fail(new RuntimeException("boom"))
val result: Int = boom.catchAll(_ => Async.succeed(-1)).block  // => -1
```

`Async.attempt` captures a by-name expression and converts any thrown `Throwable` into a failure:

```scala
import zio.blocks.async._

val parsed: Async[Int] = Async.attempt("42".toInt)
val bad: Async[Int]    = Async.attempt("nope".toInt)  // => Async.fail(NumberFormatException)
val result: Int        = bad.catchAll(_ => Async.succeed(0)).block  // => 0
```

`Async.never` is a permanently-suspended `Async[Nothing]` — a placeholder where an `Async[A]` is required but no value should ever arrive, and the usual way to test cancellation, as shown under [`Async.Running`](#asyncrunning).

`Async.start(body: => A)` runs a body on a background worker and hands back an `Async.Running[A]`; [Concurrent Fan-Out](#concurrent-fan-out-via-running) covers when to reach for it and the trap to avoid:

```scala
import zio.blocks.async._

val running: Async.Running[Int] = Async.start { 42 }
val result: Int = running.block  // => 42
```

### Transformation

Pure transformations apply a function to the success value and return a new `Async`:

```scala
implicit class AsyncOps[A](fa: Async[A]) {
  def map[B](f: A => B): Async[B]
  def flatMap[B](f: A => Async[B]): Async[B]
  def as[B](b: B): Async[B]
  def unit: Async[Unit]
}

// flatten is a separate extension, on a nested Async:
implicit class AsyncNestedOps[A](ffa: Async[Async[A]]) {
  def flatten: Async[A]  // collapses one nesting level
}
```

`map` applies a pure function and `flatMap` sequences a dependent second computation:

```scala
import zio.blocks.async._

val result: Async[String] =
  Async.succeed(21)
    .map(_ * 2)
    .flatMap(n => Async.succeed(s"value: $n"))
val out: String = result.block  // => "value: 42"
```

### Composition

Compositional operators combine independent or dependent `Async` values:

```scala
implicit class AsyncOps[A](fa: Async[A]) {
  def zipWith[B, C](that: Async[B])(f: (A, B) => C): Async[C]
  def zip[B](that: Async[B])(implicit t: Tuples[A, B]): Async[t.Out]  // flattens; see below
  def tap(f: A => Async[Any]): Async[A]
  def ensuring(finalizer: Async[Any]): Async[A]
  def *>[B](that: Async[B]): Async[B]
  def <*[B](that: Async[B]): Async[A]
  def orElse[B](that: => Async[B]): Async[_]  // result type merges A and B via Concat typeclass
}
```

`zipWith` waits for both sides and combines their results; `tap` runs a side-effecting action while passing the original value through. `zip` pairs the two results, and chains of it stay flat rather than nesting — `a zip b zip c` yields `Async[(A, B, C)]`, not `Async[((A, B), C)]` — because it combines through the `Tuples` instances described in the [combinators reference](combinators.md):

```scala
import zio.blocks.async._

val combined: Async[Int] =
  Async.succeed(3).zipWith(Async.succeed(4))(_ + _)
val tapped: Async[Int] =
  combined.tap(v => Async.attempt(println(s"sum is $v")))
val result: Int = tapped.block  // => 7; "sum is 7" was printed by the line above
```

`*>` and `<*` sequence two effects and discard the left or right result respectively:

```scala
import zio.blocks.async._

val logged: Async[Int] =
  Async.attempt(println("starting")).*>(Async.succeed(42))
val result: Int = logged.block  // => 42
```

### Error Handling

`Async` represents failure as a `Throwable` and provides dedicated recovery operators:

```scala
implicit class AsyncOps[A](fa: Async[A]) {
  def catchAll[A1 >: A](f: Throwable => Async[A1]): Async[A1]
  def mapError(f: Throwable => Throwable): Async[A]
  def foldCause[B](onFailure: Throwable => B)(onSuccess: A => B): Async[B]
  def either: Async[Either[Throwable, A]]
}
```

`catchAll` recovers from any failure by supplying a replacement `Async[A]`; `either` converts the outcome to an `Either` so the failure surface is visible in the return type:

```scala
import zio.blocks.async._

val safe: Async[Either[Throwable, Int]] =
  Async.fail(new Exception("oops")).either
val result: Either[Throwable, Int] = safe.block  // => Left(Exception("oops"))
```

`foldCause` handles both the success and failure branches in a single call without allocating a recovery `Async`:

```scala
import zio.blocks.async._

val message: Async[String] =
  Async.attempt("42".toInt).foldCause(
    (err: Throwable) => s"failed: ${err.getMessage}"
  )(
    (n: Int) => s"parsed: $n"
  )
val result: String = message.block  // => "parsed: 42"
```

### Driving

Driving settles whatever part of an `Async[A]` is still waiting, and delivers the result through one of three mechanisms:

```scala
implicit class AsyncOps[A](fa: Async[A]) {
  def block: A                                        // parks calling thread; re-throws on failure
  def await: A                                        // inside Async.async { } only; the rewrite
                                                      // removes it. Using it elsewhere does not compile
  def start: Async.Running[A]
  def toFuture(implicit ec: scala.concurrent.ExecutionContext): scala.concurrent.Future[A]
  def toCompletableFuture(implicit ec: scala.concurrent.ExecutionContext)
    : java.util.concurrent.CompletableFuture[A]       // JVM only
}
```

`block` parks the calling thread until the computation settles, then returns the value or re-throws the underlying `Throwable`:

```scala
import zio.blocks.async._

val result: Int = Async.succeed(42).map(_ + 1).block  // => 43
```

Two limits apply. On the JVM, never call `block` from inside a `poll` — you would be putting to sleep the very thread that has to deliver your result, which deadlocks the loop. Keep it at the edge of your program: `main`, a test, the boundary with synchronous code.

On Scala.js there is no thread to park at all. A ready value returns as usual, but a *pending* one gets a single chance to complete synchronously, and if it has not, `block` throws `IllegalStateException`. Scala.js code should reach for `toFuture` or `toJsPromise` and let the event loop deliver the result, or stay inside `Async.async { … }` and use `await`.

`start` hands whatever is still waiting to a *driver* and returns an `Async.Running[A]` immediately, without blocking. The driver is the thing that keeps asking a pending value for its result. On the JVM it is a chain of serialized tasks on `ForkJoinPool.commonPool()`; on Scala.js it is the microtask queue. Neither holds a thread for the duration: a task exits as soon as its current pollable is still pending, and the waker that `poll` registered submits the next task when there is something new to see.

`Async.start(body)` is the other entry point and it is not the same mechanism. It takes a block of ordinary synchronous code rather than an `Async`, so it needs somewhere to run that block: on the JVM a dedicated daemon thread named `zio-blocks-async-eval`, and on Scala.js the next microtask.

```scala
import zio.blocks.async._

def compute(): Int = 42

val running: Async.Running[Int] = Async.start { compute() }
val result: Int = running.block  // wait here for the result
```

`toFuture` hands off to a `scala.concurrent.Future`, bridging into any code that already expects the standard-library async type:

```scala
import zio.blocks.async._
import scala.concurrent.ExecutionContext.Implicits.global

val future: scala.concurrent.Future[Int] =
  Async.succeed(99).toFuture
```

### Conditional Execution

`when` and `unless` are package-level functions (brought in by `import zio.blocks.async._`) that conditionally evaluate an `Async[Any]` based on a `Boolean` condition. The unevaluated branch is passed by name so no `Async` is constructed when the condition is false:

```scala
import zio.blocks.async._

val flag = true
val logged:  Async[Unit] = when(flag)(Async.attempt(println("running")))
val skipped: Async[Unit] = unless(flag)(Async.attempt(println("skipped")))
```

## Common Patterns

The five patterns below address the most frequent tasks: bridging callbacks, writing sequential-looking code, guaranteeing cleanup, sharing in-flight computations, and collecting parallel results.

### Callback Bridge

Callback-based APIs all share one shape. Instead of returning the result, they return `Unit` immediately and call one of two functions you hand them once the work is done:

```scala
// The API you are stuck with. A real one calls back later, from
// another thread; the shape is what matters here.
def legacyApi(onSuccess: String => Unit, onError: Throwable => Unit): Unit =
  onSuccess("done")
```

That signature is the problem. Because `legacyApi` returns `Unit`, there is no value to return from your own function, nothing to pass to another function, and no way to say "do this, then that" — the result only ever appears inside a callback body, so the rest of your program has to be written in there too.

`Async.promise` inverts it. It gives you a `Completer[A]`, which is a value that can be completed later, and hands you back an `Async[A]` representing the eventual result. You pass the completer's two methods where `legacyApi` expects its two functions. The body is written slightly differently on each Scala version — `c =>` on Scala 2, `c ?=>` on Scala 3, for the reason explained under [`Completer`](#completer):

**Scala 2**

```scala
import zio.blocks.async._

def legacyApi(onSuccess: String => Unit, onError: Throwable => Unit): Unit =
  onSuccess("done")

val async: Async[String] = Async.promise[String] { c =>
  legacyApi(
    result => c.succeed(result),
    err    => c.fail(err)
  )
}
val result: String = async.block  // waits for the callback; this stub already fired
```

**Scala 3**

```scala
import zio.blocks.async._

def legacyApi(onSuccess: String => Unit, onError: Throwable => Unit): Unit =
  onSuccess("done")

val async: Async[String] = Async.promise[String] { c ?=>
  legacyApi(
    result => c.succeed(result),
    err    => c.fail(err)
  )
}
val result: String = async.block  // waits for the callback; this stub already fired
```

The two lines inside `legacyApi` are the whole bridge: whichever callback fires, it completes `c`, and completing `c` completes the `Async[String]`. Note what has been gained — `async` is an ordinary value. You can return it, store it, or chain `map` and `flatMap` onto it, and the callback API is no longer visible to anything downstream.

The bridge is also safe against a callback that fires more than once — see [`Completer`](#completer).

### Direct Style

Inside `Async.async { ... }`, use `await` to extract values from `Async` computations in sequential-looking code without explicit `flatMap` chains:

```scala
import zio.blocks.async._

case class Order(id: Int, userId: Int)
case class User(id: Int, name: String, tier: String)
case class Shipment(orderId: Int, carrier: String)

def fetchOrder(id: Int): Async[Order]      = Async.succeed(Order(id, 1))
def fetchUser(id: Int): Async[User]        = Async.succeed(User(id, "sam", "gold"))
def fulfill(orderId: Int): Async[Shipment] = Async.succeed(Shipment(orderId, "express"))

def fulfillOrGuest(orderId: Int): Async[String] = Async.async {
  val order    = fetchOrder(orderId).catchAll(_ => fetchOrder(9001)).await
  val user     = fetchUser(order.userId)
                   .catchAll(_ => Async.succeed(User(0, "guest", "bronze"))).await
  val shipment = fulfill(order.id).await
  s"shipped ${shipment.orderId} for ${user.name} via ${shipment.carrier}"
}
val result: String = fulfillOrGuest(9001).block
```

Awaits run in source order; a failed `Async[A]` under `await` propagates as `Async.fail`.

Nothing new happens at runtime here. `Async.async` rewrites its body at compile time: the block is split at each `await` and reassembled into the `flatMap` chain you would have written by hand. The example above compiles to roughly this:

```scala
fetchOrder(orderId).catchAll(_ => fetchOrder(9001)).flatMap { order =>
  fetchUser(order.userId)
    .catchAll(_ => Async.succeed(User(0, "guest", "bronze")))
    .flatMap { user =>
      fulfill(order.id).map { shipment =>
        s"shipped ${shipment.orderId} for ${user.name} via ${shipment.carrier}"
      }
    }
}
```

So `await` is not a method that blocks or waits. It is a marker the rewrite removes, and everything after it becomes the continuation that runs once the value arrives. Direct style therefore costs nothing over writing the chain yourself — by the time the code runs, it *is* that chain. Choose whichever reads better.

One consequence is worth remembering: `await` only means something inside an `Async.async` block. Elsewhere there is no rewrite to remove it, and both Scala versions reject it at compile time — Scala 2 through `@compileTimeOnly`, Scala 3 by aborting the macro expansion. You will not ship this mistake.

Within the block, `await` is not restricted to statement position. It also works inside the closures you pass to the strict collections — `List`, `Option`, `Vector`, `Set`, `Map`, `Array`, `Queue`, `ArraySeq` — for `map`, `foreach`, `flatMap`, `filter`, `filterNot`, `collect`, `find`, `exists`, `forall`, `foldLeft`, `foldRight`, `reduce` and `reduceLeft`, and in for-comprehensions over them:

```scala
import zio.blocks.async._

def fetchName(id: Int): Async[String] = Async.succeed(s"user-$id")

val names: Async[List[String]] = Async.async {
  List(1, 2, 3).map(id => fetchName(id).await)
}
```

Read that list literally; the near neighbours are not all included. `reduceRight` and `reduceOption` are not, and neither is the two-argument `fold`. `takeWhile` and `dropWhile` are, but only over an ordered receiver — `List`, `Vector`, `Queue`, `ArraySeq` or `Array` — because the prefix they compute is meaningless on a `Set` or a `Map`. And two cases surprise people: `Map.filter` with an `await` inside works on Scala 2 only, and a `Map.collect` whose closure yields a pair is unsupported everywhere. Lazy collections are outside the set entirely — force them to a strict collection first.

The semantics are uniform across all of them. Each is lazy and sequential: the closure for element *n+1* runs only after element *n*'s `await` has completed, so a `List(a, b, c).map(fetch(_).await)` performs three fetches one after another rather than at once — use `Async.collectAll` when you want them overlapped. A failed `await` short-circuits the remainder, and the result keeps the receiver's collection type.

Where a collection method would throw on its own, it still does: `reduce` over an empty receiver fails with `UnsupportedOperationException`, which arrives as an ordinary `Async` failure you can `catchAll`.

The rewrite is performed by `dotty-cps-async` on Scala 3 and by a built-in `scala-reflect` macro on Scala 2.13. Both Scala versions support direct style, and neither asks you to add anything to your build.

Scala.js 3.8 and later takes a hybrid route, decided per call site. An `await` in direct position compiles to JavaScript's own `async`/`await`, which is the fastest path available; an `await` sitting under a lambda, a by-name argument, or a nested method falls back to the `dotty-cps-async` transform, because the native primitive is not legal in those positions. Nothing about this is yours to configure — the wider `Async.async` surface works either way.

### Bracket and Ensuring

Some work has to happen no matter what: closing a file, releasing a connection, deleting a temporary directory. In ordinary code you write that in a `finally` block. `ensuring` is the same idea for `Async`: you attach a cleanup value, and its *outcome* is applied once the computation settles, whether that produced a value or a failure. Note "value", not "thunk" — `ensuring` takes an `Async`, and under the [evaluation model](#evaluation-model) building one runs its synchronous part immediately:

```scala
import zio.blocks.async._

val result: Async[String] =
  Async.attempt(openResource()).flatMap { res =>
    Async.attempt(res.read()).ensuring(Async.attempt(res.close()))
  }
```

That example is safe only because `Async.attempt(res.read())` is already finished by the time `ensuring` is reached. Written over something that genuinely waits, the same shape closes the resource while the read is still in flight:

```scala
// WRONG: res.close() runs here, as the argument is built —
// not after the read completes.
readAsync(res).ensuring(Async.attempt(res.close()))
```

To defer the effect itself, put it somewhere that is only run when driven — a `flatMap` or `tap` closure, or a finalizer that is genuinely suspended:

```scala
readAsync(res).flatMap(v => Async.attempt(res.close()).as(v))
```

What `ensuring` does guarantee is the rule worth remembering: **the cleanup never changes the answer.** It cannot turn a failure into a success, and it cannot turn a success into a failure.

That last part raises an obvious question — what if the cleanup itself fails? Closing a file can throw too. The answer depends on how the main computation ended, so it is worth seeing both cases:

```scala
import zio.blocks.async._

// Both fail: reading the resource, and then closing it.
val bothFail: Async[String] =
  Async
    .attempt[String](throw new RuntimeException("read failed"))
    .ensuring(Async.attempt(throw new IllegalStateException("close failed")))

bothFail.either.block match {
  case Left(e) =>
    println(e.getMessage)                     // read failed   <- the original failure
    println(e.getSuppressed()(0).getMessage)  // close failed  <- attached to it
  case Right(_) => ()
}

// Only the cleanup fails.
val readOk: Async[String] =
  Async
    .succeed("contents")
    .ensuring(Async.attempt(throw new IllegalStateException("close failed")))

val value: String = readOk.block  // "contents" — the close failure is gone
```

In the first case the read had already failed, so you get the read's exception — the one that explains what actually went wrong. The close error is not thrown away, though: it is carried along inside that exception, in a list the JVM keeps for exactly this purpose. `getSuppressed` returns that list. Your logging framework almost certainly prints it, usually under a line beginning `Suppressed:`, so both problems end up on the page.

The second case is the one to watch. The read succeeded, so there is no exception to carry the close error, and it is simply dropped — `readOk.block` returns `"contents"` and you never hear that closing failed. If a cleanup error matters to you on the success path, catch it inside the cleanup step itself and log it there:

```scala
Async
  .attempt(res.read())
  .ensuring(Async.attempt(res.close()).catchAll { t =>
    Async.succeed(logger.warn("close failed", t))
  })
```

### Concurrent Fan-Out via Running

Suppose one expensive computation feeds several parts of your program — a report that is both summarised and emailed, say. The obvious approach is to build the work once and use that value in both places — but driving it is what produces the result, so each place that drives it does the work again. You want the work to happen once, in the background, with everyone reading the same outcome.

`Async.start` does that. It hands the body to a background worker and returns immediately with an `Async.Running[A]` — a handle to work already in flight:

```scala
import zio.blocks.async._

def heavyComputation(): Int = { Thread.sleep(50); 42 }

// Returns straight away; the work proceeds on a background worker.
val running: Async.Running[Int] = Async.start(heavyComputation())

// The handle is itself an Async[Int], so it composes like anything else.
val doubled: Async[Int]    = running.map(_ * 2)
val labelled: Async[String] = running.map(n => s"got $n")

val a: Int    = doubled.block   // 84
val b: String = labelled.block  // "got 42" — heavyComputation ran once, not twice
```

Both consumers see the same settled outcome, because they share one running computation rather than one recipe. `Async.Running[A]` is a subtype of `Async[A]`, so it works with `map`, `flatMap`, and `zipWith` without conversion, and `running.cancel()` stops the driver, which is [less than it sounds](#cancelable).

Sharing the handle is not merely tidier than the alternative — the alternative is unsafe. Driving the same raw `Async` from two places at once is **undefined behaviour**: two `fa.start` calls on the same `fa`, or an `fa.start` racing an `fa.block`. On the JVM that polls the same combinator concurrently, so a function you passed to `map`, `flatMap`, or `tap` may run more than once, and a `collectAll` may read its drain buffer mid-update. Start once, share the `Running`.

Sequential re-use is fine — polling or composing a value again after an earlier drive has settled is well defined. Only *concurrent* driving of the same raw value is not, and single-threaded Scala.js cannot hit it at all.

Take care to start the work the right way round, because the wrong version looks almost identical:

```scala
Async.start(heavyComputation())            // ✅ the worker evaluates it
Async.attempt(heavyComputation()).start    // ❌ already evaluated, on this thread
```

Both lines compile, and both hand you an `Async.Running[Int]`. Only the first one runs anything in the background.

The difference is *when the argument gets evaluated*. Scala normally evaluates an argument before passing it, so in `Async.attempt(heavyComputation())` the computation runs first — on your own thread, right at that line — and `attempt` merely wraps the answer it produced. Tacking `.start` on afterwards cannot un-run it; there is nothing left to move to a worker.

`Async.start` is declared differently. Its parameter is `body: => A`, and that `=>` means "don't evaluate this yet — hand me the code and I will run it when I am ready." It passes the code to a worker thread, which is why the call returns immediately.

The clock shows it plainly:

```scala
Async.start(heavyComputation())            // returns in about 0 ms
Async.attempt(heavyComputation()).start    // returns in about 50 ms — you waited for it
```

So: use `Async.start` for work you want moved off the calling thread. Use `fa.start` when `fa` is an `Async` you have already built and composed and now want driven.

### Batch Collection

Use `Async.collectAll` to sequence a list of `Async` values and gather results into a `List[A]` in input order:

```scala
import zio.blocks.async._

val batch: Async[List[Int]] = Async.collectAll(List(
  Async.attempt(compute(1)),
  Async.attempt(compute(2)),
  Async.attempt(compute(3))
))
val results: List[Int] = batch.block  // => List(r1, r2, r3) in input order
```

The first failure short-circuits and remaining elements are not driven. Already-ready lists take an optimized path that skips allocating a sequencing continuation.

## Integration Points

You are unlikely to be starting from scratch. Your codebase probably already returns `Future`s, calls a Java library that returns a `CompletionStage`, or talks to a JavaScript API that returns a `Promise`. `Async` is built to sit next to those, so you can adopt it in one part of a program without rewriting everything around it.

Conversions go in both directions, and none of them blocks a thread:

| You have                   | Bring it in with                | You need                     | Hand it out with         |
|----------------------------|---------------------------------|------------------------------|--------------------------|
| `Future[A]`                | `Async.fromFuture(f)`           | `Future[A]`                  | `fa.toFuture`            |
| `CompletionStage[A]` (JVM) | `Async.fromCompletionStage(cs)` | `CompletableFuture[A]` (JVM) | `fa.toCompletableFuture` |
| `js.Promise[A]` (Scala.js) | `Async.fromJsPromise(p)`        | `js.Promise[A]` (Scala.js)   | `fa.toJsPromise`         |

A round trip through `Future` looks like this — take what an existing service hands you, work with it as an `Async`, and give a `Future` back to a caller who still expects one:

```scala
import zio.blocks.async._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

// The service you already have.
def loadUserName(id: Int): Future[String] = Future.successful("sam")

// Bring it in, work with it as an Async, hand a Future back out.
def greet(id: Int): Future[String] =
  Async
    .fromFuture(loadUserName(id))
    .map(name => s"hello, $name")
    .catchAll(_ => Async.succeed("hello, guest"))
    .toFuture
```

Java's `CompletionStage` works the same way:

```scala
import zio.blocks.async._
import java.util.concurrent.{CompletableFuture, CompletionStage}
import scala.concurrent.ExecutionContext.Implicits.global

def fetchToken(): CompletionStage[String] = CompletableFuture.completedFuture("t-123")

val token: Async[String]                 = Async.fromCompletionStage(fetchToken())
val backToJava: CompletableFuture[String] = token.map(_.toUpperCase).toCompletableFuture
```

On Scala.js the pair is `fromJsPromise` and `toJsPromise`:

```scala
import zio.blocks.async._
import scala.scalajs.js

def fetchJson(url: String): js.Promise[String] = js.native

val parsed: Async[String]       = Async.fromJsPromise(fetchJson("/api/config"))
val handedBack: js.Promise[String] = parsed.map(_.trim).toJsPromise
```

Two details are worth knowing before you use them.

Handing a value out to `Future` or `CompletableFuture` needs an `ExecutionContext` in scope, exactly as ordinary `Future` code does — usually `import scala.concurrent.ExecutionContext.Implicits.global`, as above, or whichever one your application already provides. `toJsPromise` needs nothing, because JavaScript has a single built-in event loop to run the callback on.

Failures survive the trip. That takes some care on the Java side: when a `CompletionStage` fails, Java wraps your exception in a `CompletionException` before handing it over. `fromCompletionStage` unwraps it, so the `Async` fails with the exception you actually threw rather than with Java's wrapper — which means `catchAll` sees what you expect.

Two further integration points are worth knowing about:

**Cancelling with `Using`.** `Async.Running` is an `AutoCloseable` — `Cancelable` extends it — so `scala.util.Using` (or Java's try-with-resources) cancels the work automatically when the block ends, the same way it closes a file handle:

```scala
import zio.blocks.async._
import scala.util.Using

def pollForUpdates(): Nothing = { while (true) Thread.sleep(100); ??? }

Using(Async.start(pollForUpdates())) { running =>
  // Do other work while the poller runs.
  Thread.sleep(500)
} // leaving the block cancels the driver, whether or not the body threw —
  // the loop itself keeps running; see Cancelable
```

The [Scope reference](resource-management/scope.md) covers the wider resource-management model.

**Feeding [streams](streams/core/stream.md).** A callback-based source can be turned into a stream with `Async.promise` and a `Completer`. Because a stream pulls values as it is ready for them, a source that produces faster than the consumer can handle will not overwhelm it.

## Custom Suspension

A result that is not here yet arrives in one of two ways: either something tells you when it is ready, or you have to keep asking. This module has a type for each. `Completer[A]` covers being told, and it is the one you will almost always want. `Pollable[A]` covers having to ask, and exists for the sources that leave you no choice.

### Pollable

Imagine waiting on something that never calls you back — a non-blocking socket that answers "no data yet" when you read it, a hardware timer you have to check, a native handle that reports progress only when asked. There is no callback to hand a `Completer` to. The only way to learn whether the result has arrived is to *ask*, and to keep asking. The module cannot know how to ask your particular source; only your code knows that.

`Pollable[A]` is where you supply that knowledge. It is a single method:

```scala
abstract class Pollable[+A] {
  def poll(onComplete: Runnable): Async[A]
}
```

The driver calls `poll` whenever it gets the chance, and what you return tells it what to do next:

- **Ready?** Return `Async.succeed(a)`. The driver takes the value and stops asking.
- **Not yet?** Return `this` — *and make sure `onComplete` will be run*. The driver does not come back on its own.
- **Failed?** Return `Async.fail(t)`. The driver takes the failure and stops asking, and it travels downstream like any other failure.

`onComplete` is not an optimisation — it is the only thing that gets you polled again. After a `poll` returns `this`, the driver parks and waits for that callback; if nothing ever runs it, a `block` waits forever on the JVM and throws `IllegalStateException` on Scala.js. `Async.never` is precisely a `poll` that returns `this` and never arms it. Either run `onComplete` before returning, or hand it to whatever will know when to check again.

Most programs never need any of this. For a callback-based API, `Async.promise` with a `Completer` is simpler and already correct; for blocking I/O, `Async.attempt` on a worker via `Async.start` fits better. Reach for `Pollable` only when the result genuinely has to be checked rather than delivered.

Here is the smallest thing that behaves like a real suspension — a value that refuses to be ready for its first two visits:

```scala
import zio.blocks.async._

class Delayed[A](v: A, var ticks: Int) extends Pollable[A] {
  def poll(onComplete: Runnable): Async[A] =
    if (ticks <= 0) Async.succeed(v)
    else { ticks -= 1; onComplete.run(); this }
}

val result: String = new Delayed("done", ticks = 2).block  // => "done"
```

Follow it one visit at a time:

| Visit | `ticks` | What `poll` returns     | What the driver does          |
|-------|--------:|-------------------------|-------------------------------|
| 1st   |       2 | `this`                  | Not ready — asks again        |
| 2nd   |       1 | `this`                  | Not ready — asks again        |
| 3rd   |       0 | `Async.succeed("done")` | Takes the value, stops asking |

Returning `this` means "still me, still waiting." Returning `Async.succeed(v)` means "here it is." And `onComplete.run()` is the nudge that tells the driver to come back soon rather than in its own time. The toy above runs it immediately, which just asks for another visit right away; real code instead hands `onComplete` to whatever it is waiting on — a socket selector, a timer callback — and lets that source run it when something actually happens. The driver can then stay asleep in between, rather than burning a thread asking a question whose answer has not changed. Meanwhile `.block` waits through all three visits and hands you `"done"` at the end.

A real implementation replaces the counter with the actual question — has the socket got bytes, has the timer expired — but the shape does not change.

Here is a case you are likely to meet. A service starts a long job — rendering a report, transcoding a video, restoring an archive — and gives you back a job id. There is no webhook and no callback: the only way to find out whether it has finished is to call `GET /jobs/{id}` and look at the status. That is a `Pollable`:

```scala
import zio.blocks.async._

sealed trait JobStatus
case object Pending                 extends JobStatus
case class  Done(url: String)       extends JobStatus
case class  Failed(reason: String)  extends JobStatus

// The API you are given: you can ask it, it will never tell you.
def checkJob(id: String): JobStatus = Done("https://example.invalid/report.pdf")

def download(url: String): Unit = ()

// Run `task` once, later, without holding on to a thread in the meantime.
def scheduleIn(ms: Long, task: Runnable): Unit = {
  val t = new Thread(() => { Thread.sleep(ms); task.run() })
  t.setDaemon(true)
  t.start()
}

final class JobPollable(id: String) extends Pollable[String] {
  def poll(onComplete: Runnable): Async[String] =
    checkJob(id) match {
      case Done(url)      => Async.succeed(url)
      case Failed(reason) => Async.fail(new RuntimeException(s"job $id failed: $reason"))
      case Pending        =>
        // Nothing will announce the change, so arrange our own next look.
        scheduleIn(2000, onComplete)
        this
    }
}

// From here on it is an ordinary Async: compose it, start it, cancel it.
val reportUrl: Async[String] = new JobPollable("job-42")
val saved: Async[Unit]       = reportUrl.map(url => download(url))
```

Three things to notice. The status check happens inside `poll`, so it runs only when the driver visits — you are not running a loop of your own. The `Pending` branch schedules the next visit two seconds out, which is what stops this from hammering the service. And a failed job becomes `Async.fail`, so the error travels the same path as every other failure and `catchAll` can recover it.

Note also what is *not* in the example: no blocking wait, no lock, no shared mutable state. Polling puts you in charge of when the check happens, which is the reason to choose it. Because a `Pollable[A]` can be used wherever an `Async[A]` is expected, the value drops straight into any composition and works with every combinator.

### The Pollable Protocol

Everything above is what a `Pollable` looks like from the outside. This is the contract a driver and an implementation hold each other to, and it matters only if you are on one side of it — writing a `poll`, or writing a driver of your own. If you are composing `Async` values and running them with `block`, `start`, or an interop converter, the built-in drivers already honour every rule below and you can skip the section.

**Polling is one-shot.** A driver keeps polling only while `poll` returns a still-pending `Pollable`, and must stop as soon as it returns a terminal result — a raw value, or a failed `Async`. Re-polling a `Pollable` after that is outside the contract: it is not guaranteed to be a pure re-observation, a continuation may run a second time, and diagnostic state may be repeated.

**Identity carries meaning.** A still-pending result is either this pollable itself — the common case — or a *replacement* pollable standing for the rest of the computation, and the caller directs its next poll at whatever came back. Which of the two it is decides what the caller may do next:

| What `poll` returns          | What it means                       | What the driver does next              |
|------------------------------|-------------------------------------|----------------------------------------|
| `this` — the same identity   | Pending; `onComplete` is registered | May wait for that callback             |
| A different `Pollable`       | Synchronous progress, not readiness | Polls the replacement, without waiting |
| A value, or a failed `Async` | Terminal                            | Stops polling                          |

The middle row is the one that trips people up. An identity change is *not* a readiness event, so a caller or a wrapper must never synthesize an `onComplete` call for it — it means "there is more to do right now", and waiting on a callback that nobody will run is how such a wrapper hangs.

**Wakes are permits, not proofs.** Real callbacks may be stale, reentrant, or duplicated. `onComplete` is therefore a coalescible wake permit — "look again" — rather than proof that any particular generation has completed. A driver re-polls and lets `poll` decide; an implementation is free to run `onComplete` more times than strictly necessary without breaking anything, though never fewer.

**Cancellation reaches the leaf.** Alongside `poll`, a `Pollable` may override `cancel()`, which the driver signals on the active pending operation when cancellation wins the race against completion. The default is a no-op, so a source with abortable work has to supply it — and it must be idempotent and non-blocking, because cancellation never waits:

```scala
import zio.blocks.async._
import java.util.concurrent.atomic.AtomicBoolean

// `register` stands in for handing the waker to the real source: a socket
// selector, a timer, a third-party library's callback slot.
final class AbortableRead(register: Runnable => Unit) extends Pollable[Array[Byte]] {
  private val aborted = new AtomicBoolean(false)

  def poll(onComplete: Runnable): Async[Array[Byte]] =
    if (aborted.get()) Async.fail(new java.io.IOException("read aborted"))
    else { register(onComplete); this }

  // Idempotent and non-blocking: it records the intent and returns. The
  // driver has already stopped listening by the time this runs.
  override def cancel(): Unit = aborted.set(true)
}
```

The full contract lives in the scaladoc of `Pollable#poll`; [`Cancelable`](#cancelable) covers what cancelling does and does not stop.

### Completer

Polling is the awkward case. Far more often the source does call you back — that is what `Completer[A]` is for, and why you will reach for it and not `Pollable`.

`Completer[A]` is a `Pollable[A]` that is already written: instead of implementing "is it ready?", you hold a value someone else completes exactly once. It is thread-safe, and the first call to `Completer#succeed` or `Completer#fail` wins while every later call does nothing — so a callback that fires twice cannot corrupt the result.

The structural declaration is:

```scala
final class Completer[A] extends Pollable[A] {
  def succeed(a: A): Unit
  def fail(cause: Throwable): Unit
  def peek: Async[A]
  def poll(onComplete: Runnable): Async[A]
}
```

`Async.promise` creates a new `Completer[A]`, passes it to the body, and returns the `Completer` as an `Async[A]` that the driver polls until the callback fires. If the body happens to complete it before returning — a cache hit, a callback that fires inline — the result collapses to a plain ready value and no `Pollable` is allocated at all. It is the only place in the module where the *code you write* differs between Scala versions — elsewhere the signatures differ but the call sites are identical:

```scala
// Scala 2 — the completer is an ordinary function parameter
def promise[A](body: Completer[A] => Unit): Async[A]

// Scala 3 — the completer is a context parameter of the body
inline def promise[A](inline body: Completer[A] ?=> Unit): Async[A]
```

The `?=>` on Scala 3 makes the `Completer` a given inside the body rather than a plain argument, which is why the body is written `{ c ?=> ... }` there and `{ c => ... }` on Scala 2. (The two `inline` keywords are what splice the body into the call site instead of allocating a function object — the same technique behind the allocation-free ready path described under [Evaluation Model](#evaluation-model).)

Being a given is not just bookkeeping: it buys you top-level `succeed` and `fail` helpers that find the completer themselves, so on Scala 3 the bridge need not name it at all.

```scala
// Scala 3 only — `succeed` and `fail` take the Completer as a given.
val fetched: Async[Int] = Async.promise[Int] { c ?=>
  legacyLookup(onOk = value => succeed(value), onErr = cause => fail(cause))
}
```

Naming it, as the examples below do, is equally valid and reads better when the callback is registered several lines away from where it fires.

**Scala 2**

```scala
import zio.blocks.async._

val async: Async[Int] = Async.promise[Int] { c =>
  new Thread(() => { Thread.sleep(20); c.succeed(42) }).start()
}
val result: Int = async.block  // => 42
```

**Scala 3**

```scala
import zio.blocks.async._

val async: Async[Int] = Async.promise[Int] { c ?=>
  new Thread(() => { Thread.sleep(20); c.succeed(42) }).start()
}
val result: Int = async.block  // => 42
```

Now a case from the JDK rather than a sleeping thread. `AsynchronousFileChannel` reads a file without blocking, and reports the outcome through a `CompletionHandler` with two methods: `completed` when the bytes arrive, `failed` when the read goes wrong. Those two are exactly `succeed` and `fail`, so the bridge is almost mechanical:

```scala
import zio.blocks.async._
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler}
import java.nio.file.{Path, StandardOpenOption}

def readChunk(path: Path, size: Int): Async[ByteBuffer] = {
  val completer = new Completer[ByteBuffer]
  val channel   = AsynchronousFileChannel.open(path, StandardOpenOption.READ)
  val buffer    = ByteBuffer.allocate(size)

  channel.read(buffer, 0L, buffer, new CompletionHandler[Integer, ByteBuffer] {
    def completed(bytesRead: Integer, buf: ByteBuffer): Unit = {
      buf.flip()
      completer.succeed(buf)   // the read finished
    }
    def failed(cause: Throwable, buf: ByteBuffer): Unit =
      completer.fail(cause)    // the read went wrong
  })

  completer.peek               // hand the pending result to the caller
}

// An ordinary Async from here on.
val firstBytes: Async[Int] = readChunk(Path.of("data.bin"), 1024).map(_.remaining)
```

This is the same bridge as `Async.promise`, written out by hand: create the `Completer`, give its two methods to the callback, and return `completer.peek` as the `Async[ByteBuffer]` the caller waits on. `Async.promise` packages exactly those three steps, so the same function written with it is shorter:

```scala
import zio.blocks.async._
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler}
import java.nio.file.{Path, StandardOpenOption}

def readChunk(path: Path, size: Int): Async[ByteBuffer] =
  Async.promise[ByteBuffer] { c ?=>
    val channel = AsynchronousFileChannel.open(path, StandardOpenOption.READ)
    val buffer  = ByteBuffer.allocate(size)

    channel.read(buffer, 0L, buffer, new CompletionHandler[Integer, ByteBuffer] {
      def completed(bytesRead: Integer, buf: ByteBuffer): Unit = {
        buf.flip()
        c.succeed(buf)
      }
      def failed(cause: Throwable, buf: ByteBuffer): Unit =
        c.fail(cause)
    })
  }
```

The completer is created for you and named `c`, and there is no `peek` at the end — `promise` returns the `Async` itself.

Prefer this version. Write the completer out by hand when the registration does not fit neatly in a single block, when you need to keep the completer around to complete it from elsewhere, or when you want identical source on Scala 2 and Scala 3 — `new Completer[A]` has no context-function syntax to differ over.

`readChunk` returns before a single byte has been read. Nothing blocks, no thread waits, and the caller receives an `Async[ByteBuffer]` that behaves like any other — `map` it, `zipWith` another read, recover it with `catchAll`, or `block` on it at the edge of the program.

The once-only guarantee earns its keep in code like this. You are trusting a third-party library to call your handler correctly; if a buggy or retrying implementation calls `completed` twice, or calls both `completed` and `failed`, the first call still decides the outcome and the rest are ignored. You do not have to defend against it yourself.

`Completer#peek` returns the `Completer` itself as an `Async[A]`, bypassing the `Async.promise` body — useful when managing scheduling manually, as shown in the `delayed` helper under [How They Work Together](#how-they-work-together).

## Controlling In-Flight Work

Once `start` has handed you an `Async.Running[A]`, the computation is being driven for you — by the platform driver if it still has waiting to do, and already settled if it does not. These two types are how you keep a grip on it: `Async.Running` is the handle, and `Cancelable` is the ability to stop what it refers to.

### Async.Running

`Async.Running[A]` is the handle returned by `start`. It extends `Pollable[A]`, which makes it an `Async[A]` in its own right, and [`Cancelable`](#cancelable), which is what lets you stop it.

The structural declaration is:

```scala
abstract class Running[+A] extends Pollable[A] with Cancelable
```

Because a `Running` is an `Async[A]`, you can wait for its result with `block`, compose it further with `map` or `flatMap`, or pass it anywhere an `Async[A]` is expected. [Concurrent Fan-Out via Running](#concurrent-fan-out-via-running) walks through that pattern, and why several consumers should share one handle rather than each starting the work themselves.

What you should not do is call `poll` yourself. It is there for drivers, and its contract — stop at a terminal value, never re-poll a settled one — is easy to violate by hand. Drive a `Running` the same way you drive any other `Async`: `block`, `toFuture`, or composition. There is no `isCompleted`; if you want to know whether it has finished without waiting, keep that flag yourself where you complete the work.

Calling `cancel` stops the *driver*, and does nothing if the run has already settled. [`Cancelable`](#cancelable) covers how far that reaches; two consequences belong here:

```scala
import zio.blocks.async._

val running: Async.Running[Nothing] = Async.never.start
running.cancel()  // the driver stops polling; no value is ever published
```

A cancelled run never settles at all — it does not fail, it simply stops. So anything still holding that handle and calling `block` on it waits forever on the JVM, and gets an `IllegalStateException` on Scala.js. Cancel only when you own every consumer of the handle.

And with `Async.start(body)`, `cancel` stops the driver, not the evaluation of `body`. That block runs to completion regardless — on its own daemon thread on the JVM, on the next microtask on Scala.js — so cancelling that particular `Running` means you have stopped waiting for the result, not that the work behind it has stopped. A suspended `fa.start`, by contrast, does have its active leaf signalled; [`Cancelable`](#cancelable) draws the line.

A `Running` is also an `AutoCloseable`, so [`scala.util.Using`](#integration-points) cancels it on leaving a block.

`block` on a pending handle is available on the JVM only — see [Platform Support](#platform-support).

Attach what you want to observe *before* calling `start`, not after. `start` hands the still-waiting part of the value to a driver, and whatever you composed onto it beforehand is part of what that driver runs:

```scala
import zio.blocks.async._

// A row that arrives a moment from now, on another thread.
def fetchRow(): Async[String] = Async.promise[String] { c ?=>
  val t = new Thread(() => { Thread.sleep(250); c.succeed("row-1") })
  t.setDaemon(true)
  t.start()
}
def log(msg: String): Unit = ()

// Before start: the tap is part of what the driver runs, and fires when the
// row arrives.
val watched: Async.Running[String] =
  fetchRow().tap(row => Async.attempt(log(s"got $row"))).start

// After start: this builds a *new* Async that nobody is driving. The tap runs
// only if you drive this one too — by blocking on it, or starting it.
val bolted: Async[String] =
  fetchRow().start.tap(row => Async.attempt(log(s"got $row")))
```

The second version is not a compile error and not a lost value, which is what makes it easy to write by mistake: `bolted` is simply a value nobody has driven, so its `tap` has not run and will not until something asks `bolted` for a result. So if you want to time the fetch, log its progress, or react the moment it fails, the observer has to be inside the value you hand to `start`.

`either` and `foldCause` matter more, because they decide whether the run counts as failed. Written `fa.either.start`, where `fa` is the value you are about to start, the run always succeeds, carrying a `Left` or a `Right`. Written `fa.start.either`, the run has already failed; you get your `Either`, but everyone else holding that handle still gets the exception.

All of that assumes there was something to wait for. If the value already holds its answer there is nothing to hand to a driver: `start` wraps it and returns, spawning no worker, and anything you attached ran while you were building the value — see [Evaluation Model](#evaluation-model).

### Running#cancel

A `Running` carries two `cancel` methods. One it inherits from [`Cancelable`](#cancelable) and takes nothing; the other takes a reporter:

```scala
abstract class Running[+A] extends Pollable[A] with Cancelable {
  def cancel(): Unit                                    // inherited from Cancelable
  def cancel(onCleanupFailure: Throwable => Unit): Unit
}
```

They cancel identically. What differs is where a failure thrown by *cleanup* ends up — and cleanup is the one thing cancellation can still fail at. Cancelling signals `cancel()` on the active leaf, and the teardown that follows may be asynchronous and may throw. That failure has nowhere natural to go: the run has been cancelled, so it will never deliver a result, and there is no channel left to carry an exception to whoever was waiting.

So it is reported instead. `cancel()` sends it to the ambient handler for the platform — the calling thread's `UncaughtExceptionHandler` on the JVM, the queue's failure reporter on Scala.js. `cancel(onCleanupFailure)` sends it to your function, which is what you want whenever "a socket refused to close" should reach a log or a metric rather than stderr:

```scala
import zio.blocks.async._

val running: Async.Running[Nothing] = Async.never.start

running.cancel { cause =>
  System.err.println(s"cleanup after cancellation failed: ${cause.getMessage}")
}
```

Three properties are worth knowing before you rely on it:

- **The reporter is kept only if this cancellation wins.** If the run had already settled, or another `cancel` call claimed it first, your function is dropped and never invoked. A dropped reporter is not an error; it means there was no cancellation cleanup of yours to report on.
- **It is invoked at most once.** When cleanup fails in more than one place, the later causes are attached to the first as suppressed exceptions and the first is what you are handed — so check `getSuppressed` if you are logging the whole picture.
- **It may run on any thread.** Cleanup is driven by whichever party claims it, which is not necessarily the thread that called `cancel`. Keep the reporter short and make sure it cannot throw.

### Cancelable

`Cancelable` is the minimal cancellation interface: one `cancel()` method, safe to call from any thread and safe to call twice.

Be precise about what it stops. Cancellation does two things, and they reach different distances:

- **It stops the driver.** The poll loop halts and publication of a terminal value is suppressed. A cancelled run therefore never delivers at all — it does not fail, it simply stays pending forever, which is why anything still calling `block` on that handle waits indefinitely.
- **It signals the active pending operation.** When cancellation wins the race against completion, the driver calls [`Pollable.cancel()`](#the-pollable-protocol) on whatever leaf the run is currently suspended on. A leaf that owns abortable work — an in-flight socket read, a timer, a registration with a third-party library — implements that hook and gets the chance to tear it down.

The second point is the part to get right, because the default hook is a **no-op**. A leaf that does not override `cancel()` is not aborted by cancelling the run: its socket read stays outstanding, its timer still fires, its `js.Promise` still settles. Whether cancellation reaches the work is a property of the leaf, not of the handle you called `cancel()` on.

Cancellation is also **cooperative**, never preemptive. It does not wait for an already-running `poll` invocation to return, and it interrupts no thread. Calling `cancel()` publishes the signal and returns; whatever teardown that triggers is driven afterwards, on whichever party wins the claim.

That is why a leaf holding a resource still deserves an explicit finalizer. If a cancelled computation would otherwise leave a socket or a file handle open and you do not control its `Pollable`, close it yourself — pair the cancellation with [`ensuring`](#bracket-and-ensuring), or hold the resource in a `Using` block.

The structural declaration is:

```scala
trait Cancelable extends AutoCloseable {
  def cancel(): Unit
  final def close(): Unit = cancel()
}
```

`Cancelable.noop` is the predefined no-op instance, useful as a placeholder when no real cancellation is needed:

```scala
import zio.blocks.async._

val c: Cancelable = Cancelable.noop
c.cancel()  // no-op
c.close()   // no-op; delegates to cancel()
```

## AsyncSelector

`AsyncSelector[A]` is a low-level building block, and that is worth saying before anything else: most code should not reach for it. If what you want is "run N of these at a time and give me results as they land", the streams module already provides it — [`mapPar`](streams/core/stream.md#streammappar), [`mergeAll`](streams/core/stream.md#streammergeall), and `mapParAsync` — with backpressure, ordering rules, and resource cleanup you would otherwise write yourself. The selector exists because those operators needed a primitive underneath them, and the streams concurrency engine is its only production consumer.

What it gives you is a **repeatable** multi-way wait. You hand it a fixed number of slots, each holding an independently-pending computation. `select` waits until one of them completes, hands you the winning slot index along with its value, and disarms that slot. `replace` arms the same slot with its next computation, and you select again. That loop is the whole point: a fan-in that runs for the life of a connection, a worker pool that keeps N requests in flight, anything where the same slot is re-used thousands of times.

A hand-rolled race loop can do the first round of that, but not the thousandth. Racing N computations registers a callback on every loser, and the losers survive into the next round, so a fresh callback piles up on each of them every time round. The selector instead keeps **one stable waker per armed slot** for as long as that slot stays armed, no matter how many selections pass over it.

Selection is round-robin rather than first-past-the-post. The scan starts at the slot *after* the previous winner, so a slot that is continuously eligible — armed, and with a completed computation — wins within at most `armedCount` *successful* selections. No slot can be starved by a faster neighbour.

The public surface is small:

```scala
final class AsyncSelector[A] private (slotCount: Int, initial: IndexedSeq[(Int, Async[A])]) extends Cancelable {
  def size: Int
  def replace(index: Int, value: => Async[A]): Unit
  def select: Async[(Int, A)]
  def shutdown: Async[Unit]
  override def cancel(): Unit
}
```

You never construct one directly; `Async.selector` does it:

```scala
def selector[A](inputs: IndexedSeq[Async[A]]): AsyncSelector[A]
```

The `inputs` sequence fixes the slot count for the life of the selector — `size` reports it, and it never changes, disarmed slots included. Here is the full loop:

```scala
import zio.blocks.async._

val selector: AsyncSelector[Int] =
  Async.selector(Vector(Async.succeed(10), Async.succeed(20), Async.succeed(30)))

// Whichever slot is eligible, scanning from just after the previous winner.
val (firstSlot, firstValue) = selector.select.block

// Re-arm the slot that just won. Until this runs, that slot sits disarmed
// and is skipped by every selection.
selector.replace(firstSlot, Async.succeed(firstValue + 1))

val (secondSlot, secondValue) = selector.select.block

// Cancel every still-armed loser and join all of their cleanup.
selector.shutdown.block
```

Four rules keep that loop honest:

- **A slot can only be armed when it is disarmed.** `replace` on a slot that is still armed throws `IllegalStateException("selector slot N is already armed")`, and `replace` after shutdown throws `IllegalStateException("selector is closed")`. The safe pattern is the one above: re-arm the index you were just handed by `select`, and nothing else.
- **`value` is by-name for a reason.** The thunk is evaluated lazily and outside the selector's own lock, so constructing the next computation cannot deadlock against a concurrent `select`. A thunk that returns `null` is reified as a failed `Async` carrying `NullPointerException("selector input returned null Async")` rather than corrupting the slot.
- **A winner is removed exactly once.** Disarming happens atomically with the win, so the same completion cannot be handed to two selections, and a failure from an input surfaces as a failed `select` rather than poisoning the selector.
- **`shutdown` is where cleanup is joined.** It cancels every armed loser, is idempotent, and — importantly — does not complete until cleanup for every still-armed loser has finished. `cancel()` is the `Cancelable` spelling of the same thing: it starts `shutdown` and returns immediately without waiting. Use `cancel()` when the selector is a resource being closed on the way out of a scope, and `shutdown` when you need to know the cleanup actually finished.

:::warning[`.block` per selection is for the example only]
The snippet above blocks once per round so it reads top to bottom. Real code composes `select` like any other `Async` — `flatMap` it, or `await` it inside `Async.async` — and blocks once at the edge, if at all. Blocking inside a driver deadlocks it; see [Driving](#driving).
:::

## Failure

`Failure` is how a failed `Async` is represented. You never construct one — `Async.fail` and `Completer#fail` produce it, `catchAll` and `either` recover from it — but it explains why a failure travels through a chain untouched: it extends `Pollable[Nothing]`, and `map` and `flatMap` return it unchanged instead of running their functions.

```scala
final class Failure private (val cause: Throwable, private[async] val trusted: Boolean) extends Pollable[Nothing] {

  /** Public construction is deliberately an ordinary, untrusted failure. */
  def this(cause: Throwable) = this(cause, false)

  def poll(onComplete: Runnable): Async[Nothing] = this
}
```

The one-argument constructor is the only one you can call, and it produces an ordinary, untrusted failure. The `trusted` flag is `private[async]`: it marks a failure that entered through one of the library's own internal boundaries — the typed error channel that `zio-blocks-streams` carries in its `Either` — rather than through user code. It changes nothing in the public API: `cause` is the same `Throwable` either way, and every recovery combinator treats both kinds identically. The distinction is visible only to the module's internal fold, which routes a trusted cause down the typed-error path instead of the defect path.

[`block`](#driving) re-throws `cause`; `catchAll` hands your recovery function the original `Throwable`, unwrapped; [`either`](#error-handling) turns it into a `Left` instead.

## Platform Support

The core API behaves identically everywhere by design, and the cross-platform test suite fails if any user-visible core behaviour diverges. What varies is the interop surface, which is deliberately platform-specific, and the two operations that depend on having a thread:

| Feature                      | JVM | Scala.js | How it differs                                                     |
|------------------------------|:---:|:--------:|--------------------------------------------------------------------|
| Constructors and combinators | yes |   yes    | Identical on both                                                  |
| `Async.async` / `await`      | yes |   yes    | Different backend per platform — see [Direct Style](#direct-style) |
| `block` on a pending value   | yes |    no    | Throws on Scala.js: no thread to park                              |
| `fa.start` / `Async.Running` | yes |   yes    | Serialized `ForkJoinPool` tasks on the JVM, microtasks on Scala.js |
| `Async.start(body)`          | yes |   yes    | Daemon thread on the JVM, the next microtask on Scala.js           |
| `Future` interop             | yes |   yes    | Same API on both                                                   |
| `CompletionStage` interop    | yes |    no    | JVM only                                                           |
| `js.Promise` interop         |  no |   yes    | Scala.js only                                                      |

All of it works on Scala 2.13 and Scala 3.

### Where Suspended Work Runs

On the **JVM**, a suspended run is a chain of serialized tasks on `ForkJoinPool.commonPool()`. There is no dedicated worker thread per run and no pool of the module's own: a task exits the moment its current pollable is still pending, and the waker that `poll` registered submits the next task. A run that spends most of its life waiting therefore occupies no thread at all while it waits.

The one exception is `Async.start(body)`, which evaluates an arbitrary synchronous block rather than driving an `Async`. That block gets a thread of its own — a daemon named `zio-blocks-async-eval`.

`block` parks with `LockSupport.park` / `unpark` rather than a monitor, which is a deliberate choice for **virtual threads**: a parked virtual thread (JDK 21+) unmounts its carrier instead of pinning it. The module is Loom-*friendly* in this sense, but it is not Loom-based — it configures no virtual-thread executor, and running on a virtual thread is entirely the caller's decision.

On **Scala.js** there is no thread to hand work to, so scheduling goes through `JSExecutionContext.queue` as microtasks, with a `setTimeout(0)` macrotask escape for work that must yield back to the host event loop. A cap of 1024 consecutive ready resumptions bounds how long a run of already-ready steps can hold the queue before yielding, so a long synchronous chain cannot starve rendering or I/O.

That is also why `block` cannot work there. A pending value gets one chance to complete synchronously inside `poll`; if it has not, `block` throws:

```
java.lang.IllegalStateException: Async.block: suspension did not complete synchronously
and JavaScript cannot block. Drive the Pollable from a non-blocking entry point instead.
```

:::note[There is no blocking-operations API]
The async module has no `attemptBlocking`, no blocking thread pool, and no way to mark an operation as blocking. `block` is the only "blocking" thing in it, and it is a terminal — the point where you leave `Async` and go back to synchronous code, not a place to put a blocking call. Run genuinely blocking work on a thread you control, for example with `Async.start(body)`, and bridge the result back through the resulting `Running`.
:::

## Using Async with Streams

`zio-blocks-streams` depends on this module, so `Async` is not a neighbouring library to streams — it is part of the streams vocabulary. Every cross-platform stream terminal hands you an `Async`, and everything on this page applies to the value you get back.

One convention travels with it. Stream terminals return `Async[Either[E, Z]]`, never `Async[Z]`, because a stream has a typed error channel and `Async` does not. The two are deliberately kept apart:

- The stream's **typed** error `E` stays inside the `Either`. A stream that fails with a typed error still completes its `Async` *successfully*, carrying a `Left`.
- `Async`'s own untyped `Throwable` channel is reserved for **defects** — a callback that threw, a finalizer that failed, a cleanup failure. Those fail the outer `Async` and never appear as a `Left`.

So `catchAll` on a stream terminal recovers bugs, not the errors the stream declares. Those you match on:

```scala
import zio.blocks.async._
import zio.blocks.streams._

val readings: Stream[String, Int] = Stream(12, 7, 30)

// A terminal is an ordinary Async, so every combinator on this page applies.
val summary: Async[String] =
  readings.runCollectAsync.map(result =>
    result match {
      case Right(values) => s"collected ${values.length} readings"
      case Left(error)   => s"typed error: $error"
    }
  )

// At the edge of a JVM `main`, and nowhere else:
val text: String = summary.block
```

`.block` is the edge-of-the-world move described under [Driving](#driving), and the same two limits hold: never inside a stream callback or a `poll`, and never on a value that may still be pending when you are on Scala.js, where it throws. Cross-platform stream code keeps the `Async` and hands it to the host — `toFuture`, `toJsPromise` — or stays inside `Async.async { … }` and uses `await`.

Cancellation carries across too: cancelling a running stream is the [`Cancelable`](#cancelable) contract on this page, applied to a reader rather than a single leaf.

[Asynchronous Stream Execution](streams/execution-and-compatibility/async-execution.md) is the reference for all of it — the terminal family, the async source constructors and operators, manual pull and reader ownership, and what cancelling a stream cleans up.

## Running the Examples

The `async-examples` module ships `AsyncShowcaseExample`, a single runnable pipeline exercising the whole module: `Completer`-backed callback bridges, the `Async.async` direct-style DSL, `catchAll` recovery, and a final `block`. Its `fulfillOrGuest` function is the one shown under [Direct Style](#direct-style); the file also carries the helpers it calls, which is what makes it runnable as it stands.

To run the full example, clone the repository and execute:

```
sbt "async-examples/run"
```

## See Also

- [Asynchronous Stream Execution](streams/execution-and-compatibility/async-execution.md) — the `Async` terminal family, async source constructors and operators, reader ownership, and stream cancellation
- [Stream Reference](streams/core/stream.md) — pull-based streaming with resource safety; use `Async.promise` and `Completer` to bridge callback-based push sources into the pull-based stream model
- [Scope Reference](resource-management/scope.md) — compile-time resource safety; `Async.Running` extends `AutoCloseable` and can be used inside `scala.util.Using` or any Scope-managed context for structured cancellation
- [Compile-Time Resource Safety with Scope](../guides/compile-time-resource-safety-with-scope.md) — step-by-step tutorial on resource ownership that applies equally to `Async.Running` handles
