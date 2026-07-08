---
id: reloadable-services
title: "Tutorial: Hot-Swapping Services with Reloadable"
sidebar_label: "Hot-Swapping Services with Reloadable"
---

## Introduction

Welcome! In this tutorial we explore one of ZIO's most compelling runtime features: the ability to **hot-swap a service** while the application keeps running.

Imagine your application connects to a database using credentials stored in a configuration file. When the credentials rotate, you want to tear down the old connection pool and build a fresh one without restarting the process. That is exactly what `Reloadable[Service]` lets you do.

By the end of this tutorial you will be able to:

- Wrap any resource-managed `ZLayer` in a `Reloadable` using `Reloadable.manual`.
- Reach the current live service instance through `Reloadable.get[Service]`.
- Trigger an explicit, atomic hot-swap with `Reloadable.reload[Service]` and watch resource teardown and acquisition happen in the console.
- Schedule a non-blocking background refresh with `Reloadable.reloadFork[Service]`.
- Drive periodic automatic reloads with `Reloadable.auto(layer, Schedule.fixed(n.seconds))`.

**Prerequisites:** You should be comfortable with `ZIO[R, E, A]`, `ZLayer`, `ZIO.acquireRelease` / `ZLayer.scoped`, and the concept of a `Scope`. No prior knowledge of `Reloadable` or `ScopedRef` is needed.

We recommend reading from top to bottom — every section builds on the previous one.

---

## 1. The Problem: Services With Resources

Most real-world services acquire resources when they start (open a file, establish a connection, allocate a thread pool) and release them when they stop. ZIO models this lifecycle with `ZLayer.scoped` and `ZIO.acquireRelease`.

To make this concrete, let's define a `Counter` service that *logs its own acquire and release events*. We will use this service throughout the tutorial because its output makes the lifecycle immediately visible:

```scala mdoc:silent
import zio._

// The service interface.
trait Counter {
  def increment: UIO[Unit]
  def get: UIO[Int]
}

// A layer that acquires a fresh Counter and logs when it is acquired/released.
val counterLayer: ZLayer[Any, Nothing, Counter] = ZLayer.scoped {
  for {
    ref     <- Ref.make(0)             // mutable state for the counter
    counter  = new Counter {
                 def increment = ref.update(_ + 1)
                 def get       = ref.get
               }
    _       <- ZIO.debug(">>> Counter acquired")  // visible on acquire
    _       <- ZIO.addFinalizer(             // runs when the scope closes
                 ZIO.debug("<<< Counter released")
               )
  } yield counter
}
```

Line-by-line:
- `Ref.make(0)` — allocates an in-memory counter; in a real service this would be a connection pool or file handle.
- `ZIO.debug(">>> Counter acquired")` — prints when the service is first built; lets us see *when* acquisition happens.
- `ZIO.addFinalizer(...)` — registers a cleanup effect that runs when the scope closes, i.e., when the service is torn down.

Let's verify the layer works on its own before adding `Reloadable`:

```scala mdoc:compile-only
import zio._

trait Counter { def increment: UIO[Unit]; def get: UIO[Int] }

val counterLayer: ZLayer[Any, Nothing, Counter] = ZLayer.scoped {
  for {
    ref     <- Ref.make(0)
    counter  = new Counter { def increment = ref.update(_ + 1); def get = ref.get }
    _       <- ZIO.debug(">>> Counter acquired")
    _       <- ZIO.addFinalizer(ZIO.debug("<<< Counter released"))
  } yield counter
}

object VerifyCounter extends ZIOAppDefault {
  def run =
    ZIO.serviceWithZIO[Counter] { c =>
      c.increment *> c.increment *> c.get.flatMap(n => ZIO.debug(s"Count: $n"))
    }.provide(counterLayer)
}
```

Line-by-line:
- `ZIO.serviceWithZIO[Counter]` — retrieves the `Counter` from the environment and passes it to the callback; equivalent to `ZIO.service[Counter].flatMap(c => ...)`.
- `c.increment *> c.increment` — two sequential increments on the same in-memory counter instance.
- `c.get.flatMap(n => ZIO.debug(s"Count: $n"))` — reads the current count and prints it.
- `.provide(counterLayer)` — injects the layer, opening its scope, running the body, then closing the scope.

Expected output:

```
>>> Counter acquired
Count: 2
<<< Counter released
```

The acquire message appears before any work, and the release message appears when the application exits. This is the resource lifecycle we will observe across reloads.

---

## 2. Wrapping a Layer with `Reloadable.manual`

`Reloadable[Service]` is a thin wrapper around any service that adds two capabilities: getting the current live instance, and replacing it with a freshly-built one at runtime. The simplest constructor is `Reloadable.manual`, which accepts any `ZLayer[In, E, Out]` and returns a `ZLayer[In, E, Reloadable[Out]]`:

```scala mdoc:compile-only
import zio._

trait Counter { def increment: UIO[Unit]; def get: UIO[Int] }

val counterLayer: ZLayer[Any, Nothing, Counter] = ZLayer.scoped {
  for {
    ref     <- Ref.make(0)
    counter  = new Counter { def increment = ref.update(_ + 1); def get = ref.get }
    _       <- ZIO.debug(">>> Counter acquired")
    _       <- ZIO.addFinalizer(ZIO.debug("<<< Counter released"))
  } yield counter
}

// Lift the plain layer into a reloadable one.
// The environment type changes from Counter to Reloadable[Counter].
val reloadableLayer: ZLayer[Any, Nothing, Reloadable[Counter]] =
  Reloadable.manual(counterLayer)
```

Line-by-line:
- `Reloadable.manual(counterLayer)` — captures `counterLayer` and wraps it so the same build recipe can be run again on demand.
- The result type is `ZLayer[Any, Nothing, Reloadable[Counter]]`, not `ZLayer[Any, Nothing, Counter]`. Callers see `Reloadable[Counter]` in their environment, which is an explicit signal that the service can be swapped.

:::note
`Reloadable.manual` does not require any changes to the original `counterLayer`. Any existing `ZLayer.scoped` layer works unchanged.
:::

---

## 3. Accessing the Current Instance with `Reloadable.get`

Once the environment holds a `Reloadable[Counter]`, we need a way to talk to the *currently live* `Counter`. `Reloadable.get[Counter]` is the accessor effect:

```scala mdoc:compile-only
import zio._

trait Counter { def increment: UIO[Unit]; def get: UIO[Int] }

val counterLayer: ZLayer[Any, Nothing, Counter] = ZLayer.scoped {
  for {
    ref     <- Ref.make(0)
    counter  = new Counter { def increment = ref.update(_ + 1); def get = ref.get }
    _       <- ZIO.debug(">>> Counter acquired")
    _       <- ZIO.addFinalizer(ZIO.debug("<<< Counter released"))
  } yield counter
}

object GetExample extends ZIOAppDefault {
  def run = {
    val program: ZIO[Reloadable[Counter], Any, Unit] =
      for {
        c     <- Reloadable.get[Counter]   // obtain the live Counter
        _     <- c.increment               // use it as any plain Counter
        _     <- c.increment
        count <- c.get
        _     <- ZIO.debug(s"Count: $count")
      } yield ()

    program.provide(Reloadable.manual(counterLayer))
  }
}
```

Expected output:

```
>>> Counter acquired
Count: 2
<<< Counter released
```

Notice the call pattern:
- `Reloadable.get[Counter]` — a static accessor on the companion object; equivalent to `ZIO.service[Reloadable[Counter]].flatMap(_.get)`.
- The returned `Counter` is the *same instance* for the lifetime of a reload boundary; calling `Reloadable.get` multiple times before a reload always returns the same object.

---

## 4. Hot-Swapping with `Reloadable.reload`

This is the core capability. `Reloadable.reload[Counter]` is a single effect that does two things in one *uninterruptible* step:

1. Closes the current `Counter`'s resource scope, running all registered finalizers.
2. Builds a brand-new `Counter` from the original layer recipe.

The old instance is gone; the new one is in place. No caller can observe a half-torn-down service:

```scala mdoc:compile-only
import zio._

trait Counter { def increment: UIO[Unit]; def get: UIO[Int] }

val counterLayer: ZLayer[Any, Nothing, Counter] = ZLayer.scoped {
  for {
    ref     <- Ref.make(0)
    counter  = new Counter { def increment = ref.update(_ + 1); def get = ref.get }
    _       <- ZIO.debug(">>> Counter acquired")
    _       <- ZIO.addFinalizer(ZIO.debug("<<< Counter released"))
  } yield counter
}

object ReloadExample extends ZIOAppDefault {
  def run = {
    val program: ZIO[Reloadable[Counter], Any, Unit] =
      for {
        c1     <- Reloadable.get[Counter]
        _      <- c1.increment
        _      <- c1.increment
        before <- c1.get
        _      <- ZIO.debug(s"Before reload: $before")     // prints 2

        _      <- Reloadable.reload[Counter]               // ← the hot-swap

        c2     <- Reloadable.get[Counter]
        _      <- c2.increment
        after  <- c2.get
        _      <- ZIO.debug(s"After reload: $after")       // prints 1
      } yield ()

    program.provide(Reloadable.manual(counterLayer))
  }
}
```

Expected output:

```
>>> Counter acquired
Before reload: 2
<<< Counter released
>>> Counter acquired
After reload: 1
<<< Counter released
```

Line-by-line:
- `c1 <- Reloadable.get[Counter]` — fetches the *currently live* instance; all method calls on `c1` go to the same object until the next reload.
- `c1.increment *> c1.increment` — two increments on the first instance; its internal count is now 2.
- `Reloadable.reload[Counter]` — one uninterruptible step: closes the old scope (prints `<<< Counter released`), then builds a fresh instance (prints `>>> Counter acquired`).
- `c2 <- Reloadable.get[Counter]` — fetches the *newly built* instance; its count starts at 0.
- `c2.increment` — one increment on the fresh instance; its count is now 1.

Notice the aha moment: `<<< Counter released` appears *between* the two "Before/After" messages. The old instance — with its count of 2 — was torn down, and the new instance started fresh at 0. One call to `Reloadable.reload` orchestrated both halves atomically.

:::tip
Try changing the code to call `Reloadable.reload[Counter]` a second time between the two `ZIO.debug` lines and observe that a second acquire/release cycle appears in the output.
:::

---

## 5. Background Refresh with `Reloadable.reloadFork`

Sometimes you want to trigger a reload without blocking the current fiber. `Reloadable.reloadFork[Counter]` forks the reload onto a daemon fiber and returns immediately. If the reload fails, the error is logged via `ignoreLogged` — it is neither swallowed silently nor allowed to crash the calling fiber:

```scala mdoc:compile-only
import zio._

trait Counter { def increment: UIO[Unit]; def get: UIO[Int] }

val counterLayer: ZLayer[Any, Nothing, Counter] = ZLayer.scoped {
  for {
    ref     <- Ref.make(0)
    counter  = new Counter { def increment = ref.update(_ + 1); def get = ref.get }
    _       <- ZIO.debug(">>> Counter acquired")
    _       <- ZIO.addFinalizer(ZIO.debug("<<< Counter released"))
  } yield counter
}

object ForkExample extends ZIOAppDefault {
  def run = {
    val program: ZIO[Reloadable[Counter], Any, Unit] =
      for {
        c1 <- Reloadable.get[Counter]
        _  <- c1.increment
        _  <- c1.increment

        _  <- Reloadable.reloadFork[Counter]            // returns immediately
        _  <- ZIO.debug("Main fiber continues here")   // may appear before acquire/release
        _  <- ZIO.sleep(100.millis)                    // give the daemon time to finish

        c2    <- Reloadable.get[Counter]
        after <- c2.get
        _     <- ZIO.debug(s"After fork-reload: $after")
      } yield ()

    program.provide(Reloadable.manual(counterLayer))
  }
}
```

Expected output (order of the first three lines may vary):

```
>>> Counter acquired
Main fiber continues here
<<< Counter released
>>> Counter acquired
After fork-reload: 0
<<< Counter released
```

Line-by-line:
- `Reloadable.reloadFork[Counter]` — forks the reload effect onto a daemon fiber and returns `UIO[Unit]` immediately; the calling fiber does not wait.
- `ZIO.debug("Main fiber continues here")` — executes right after the fork, potentially before the daemon completes.
- `ZIO.sleep(100.millis)` — a brief pause to let the daemon fiber finish before the next `Reloadable.get`; in production you would not add an explicit sleep.
- `c2 <- Reloadable.get[Counter]` — retrieves the reloaded instance; its count is 0 because `Ref.make(0)` ran again during the background rebuild.

The key observation is that `"Main fiber continues here"` can appear *before* the release/acquire pair because `reloadFork` does not block the calling fiber. Use `reloadFork` for best-effort background refreshes where a failure should be logged rather than propagated.

---

## 6. Automatic Scheduled Reloads with `Reloadable.auto`

For many use cases — rotating credentials, refreshing caches, picking up config changes — you want reloads to happen automatically on a fixed schedule. `Reloadable.auto` accepts your original layer and a `Schedule`, then manages a background daemon fiber for you:

```scala mdoc:compile-only
import zio._

trait Counter { def increment: UIO[Unit]; def get: UIO[Int] }

val counterLayer: ZLayer[Any, Nothing, Counter] = ZLayer.scoped {
  for {
    ref     <- Ref.make(0)
    counter  = new Counter { def increment = ref.update(_ + 1); def get = ref.get }
    _       <- ZIO.debug(">>> Counter acquired")
    _       <- ZIO.addFinalizer(ZIO.debug("<<< Counter released"))
  } yield counter
}

object AutoReloadExample extends ZIOAppDefault {
  // Reload the Counter every 2 seconds, automatically.
  val autoLayer: ZLayer[Any, Nothing, Reloadable[Counter]] =
    Reloadable.auto(counterLayer, Schedule.fixed(2.seconds))

  def run = {
    val program: ZIO[Reloadable[Counter], Any, Unit] =
      for {
        c1     <- Reloadable.get[Counter]
        _      <- c1.increment *> c1.increment *> c1.increment
        before <- c1.get
        _      <- ZIO.debug(s"Before auto-reload: $before")

        _      <- ZIO.sleep(3.seconds)                 // wait for at least one auto-reload

        c2     <- Reloadable.get[Counter]
        after  <- c2.get
        _      <- ZIO.debug(s"After auto-reload: $after")
      } yield ()

    program.provide(autoLayer)
  }
}
```

Expected output:

```
>>> Counter acquired
Before auto-reload: 3
<<< Counter released
>>> Counter acquired
After auto-reload: 0
<<< Counter released
```

Line-by-line for `Reloadable.auto`:
- `Reloadable.auto(counterLayer, Schedule.fixed(2.seconds))` — every 2 seconds the daemon re-runs `counterLayer`, tearing down the old instance and building a new one.
- The daemon fiber is lifecycle-managed: it is forked when the layer's scope opens and interrupted when the scope closes, so there is no fiber leak.
- The application code never calls `Reloadable.reload` — the schedule drives it automatically.

:::note
`Reloadable.auto` is equivalent to calling `Reloadable.reloadFork` on the given schedule inside a managed fiber. The daemon loop runs `reload.ignoreLogged.schedule(schedule)` and is shut down cleanly when the application stops.
:::

---

## 7. Putting It All Together

Here is a self-contained program that demonstrates all five concepts in one place:

```scala mdoc:compile-only
import zio._

// ── Service definition ──────────────────────────────────────────────────────
trait Counter {
  def increment: UIO[Unit]
  def get: UIO[Int]
}

// ── Service implementation with visible lifecycle ────────────────────────────
val counterLayer: ZLayer[Any, Nothing, Counter] = ZLayer.scoped {
  for {
    ref     <- Ref.make(0)
    counter  = new Counter {
                 def increment = ref.update(_ + 1)
                 def get       = ref.get
               }
    _       <- ZIO.debug(">>> Counter acquired")
    _       <- ZIO.addFinalizer(ZIO.debug("<<< Counter released"))
  } yield counter
}

// ── Main program ─────────────────────────────────────────────────────────────
object FullExample extends ZIOAppDefault {

  val program: ZIO[Reloadable[Counter], Any, Unit] =
    for {
      // 1. Get the initial instance and use it.
      c1    <- Reloadable.get[Counter]
      _     <- c1.increment *> c1.increment
      v1    <- c1.get
      _     <- ZIO.debug(s"Initial count: $v1")        // 2

      // 2. Explicit atomic hot-swap.
      _     <- Reloadable.reload[Counter]

      // 3. Fresh instance starts at 0.
      c2    <- Reloadable.get[Counter]
      _     <- c2.increment
      v2    <- c2.get
      _     <- ZIO.debug(s"After manual reload: $v2")  // 1

      // 4. Fork a background reload (non-blocking).
      _     <- Reloadable.reloadFork[Counter]
      _     <- ZIO.sleep(50.millis)                    // wait for daemon to finish

      // 5. Another fresh instance.
      c3    <- Reloadable.get[Counter]
      v3    <- c3.get
      _     <- ZIO.debug(s"After fork reload: $v3")    // 0
    } yield ()

  def run = program.provide(Reloadable.manual(counterLayer))
}
```

Expected output:

```
>>> Counter acquired
Initial count: 2
<<< Counter released
>>> Counter acquired
After manual reload: 1
<<< Counter released
>>> Counter acquired
After fork reload: 0
<<< Counter released
```

Line-by-line:
- `Reloadable.manual(counterLayer)` — the single layer that powers all three reload strategies; the original `counterLayer` is unchanged.
- Each `Reloadable.reload[Counter]` call replaces the running instance and resets its count to 0.
- `Reloadable.reloadFork[Counter]` followed by `ZIO.sleep(50.millis)` — fires the reload in the background and waits long enough for the daemon to complete before checking the count.

Three complete acquire/release cycles, each driven by a different reload strategy, all from the same original `counterLayer` without any changes to the layer itself.

---

## 8. Running the Examples

The companion source files live in the `zio-examples/reloadable-services` project of the ZIO repository. Clone the repository and then run any example from the sbt shell:

```bash
git clone https://github.com/zio/zio.git
cd zio/zio-examples/reloadable-services
```

To compile all companion examples at once:

```bash
sbt compile
```

To run each concept example individually:

```bash
sbt "runMain reloadableservices.ReloadableManualExample"
sbt "runMain reloadableservices.ReloadableReloadExample"
sbt "runMain reloadableservices.ReloadableAutoExample"
sbt "runMain reloadableservices.ReloadableFullExample"
```

The only dependency you need is the core ZIO library:

```scala
libraryDependencies += "dev.zio" %% "zio" % "@VERSION@"
```

---

## 9. What You've Learned

You started this tutorial with no knowledge of `Reloadable` and a plain `ZLayer`-based service. Here is what you can now do:

- **Wrap any `ZLayer` in a `Reloadable`** using `Reloadable.manual(layer)` — no changes to the layer itself are required.
- **Access the live service** with `Reloadable.get[Counter]`, which always returns the most recently built instance.
- **Atomically hot-swap the service** with `Reloadable.reload[Counter]` — the old finalizers run, the new acquirers run, and no caller sees an inconsistent state.
- **Trigger a non-blocking background refresh** with `Reloadable.reloadFork[Counter]`, which logs errors rather than propagating them.
- **Drive automatic periodic reloads** with `Reloadable.auto(layer, Schedule.fixed(n.seconds))`, which manages the daemon fiber's lifecycle automatically.

---

## 10. Where to Go Next

- [Reloadable Services Reference](../../reference/service-pattern/reloadable-services.md) — The full reference covering `ServiceReloader` from `zio-macros`, which eliminates `Reloadable[T]` from the environment type using a transparent proxy.
- [ScopedRef Reference](../../reference/resource/scopedref.md) — The underlying primitive that powers `Reloadable`: a mutable reference whose value owns a resource scope.
- [ZLayer Reference](../../reference/contextual/zlayer.md) — Deep dive into how `ZLayer` models dependency graphs and resource lifecycles.
- [Schedule Reference](../../reference/schedule/index.md) — All the built-in schedules you can combine to control the auto-reload interval.
