# Compositional FiberRef Updates with Differ

> Learn how Differ[Value, Patch] powers compositional, patch-based FiberRef updates that faithfully merge concurrent fiber changes.

Welcome to this tutorial on `Differ` — the algebra behind ZIO's most powerful fiber-local state operations. By the end, you will understand why ordinary `FiberRef` values use last-write-wins, how `Differ` replaces that with a richer model where every change is a composable value, and how ZIO's own runtime exploits this mechanism for environments, loggers, and runtime flags.

Read this tutorial from top to bottom — each section builds on the previous one.

## Learning Objectives

By the end of this tutorial you will be able to:

- Explain what `Differ[Value, Patch]` is and why it exists: to reify incremental changes as a composable, associative `Patch` so concurrent fiber updates can be merged faithfully on `join`
- Use built-in factory methods (`Differ.update`, `Differ.set`, `Differ.map`, `Differ.chunk`) to create a patch-backed `FiberRef` with `FiberRef.makePatch` and observe merged updates from forked fibers
- Implement a custom `Differ` by hand (`diff` / `patch` / `combine` / `empty`) and verify the five correctness laws
- Compose `Differ`s with `Differ#zip` (`<*>`) for product types and `Differ#orElseEither` (`<+>`) for sum types, and with `Differ#transform` for isomorphic types
- Explain how ZIO's own internal `FiberRef`s (`currentEnvironment`, `currentLoggers`, `currentSupervisor`, `currentRuntimeFlags`) rely on `Differ` to make concurrent fiber updates compositional

## Prerequisites

Before continuing, make sure you are comfortable with:

- Basic ZIO fibers: `ZIO.fork`, `Fiber.join`, and ZIO effects
- `FiberRef.make` and the `get` / `set` / `update` operations
- The fact that a forked fiber receives its own copy of `FiberRef` values
- Familiarity with Scala traits and type parameters

---

## 1. The Problem: Why Last-Write-Wins Breaks Concurrent Updates

`FiberRef` gives each fiber its own copy of a value — that's the point. But when a forked fiber rejoins its parent, ZIO must answer a hard question: *whose value wins?*

The default `FiberRef.make` answers that question by making the **parent's value win**, discarding the child's update entirely. Alternatively, the fiber-local value can flow back via a custom `join` function, but that function still takes only two values — old parent, child result — and must return one answer. If two child fibers both update the ref and both join, only one update can survive.

Let's see this in action with `Differ.update[A]`, which is ZIO's built-in "last-write-wins" differ:

```scala
import zio._
```

Now let's observe what happens when two fibers concurrently update a `FiberRef` backed by `Differ.update[Int]`:

```scala
// Differ.update[Int] — its patch type is Int => Int
// diff(old, new) returns Function.const(new): always the new value, ignoring old
// combine(f, g) = f.andThen(g): the later update overwrites the earlier one
ZIO.scoped {
  for {
    ref    <- FiberRef.makePatch(0, Differ.update[Int])
    left   <- ref.update(_ + 10).fork   // fiber 1 wants 0 + 10 = 10
    right  <- ref.update(_ + 5).fork    // fiber 2 wants 0 + 5 = 5
    _      <- left.join
    _      <- right.join
    result <- ref.get
    _      <- Console.printLine(s"Result: $result")
    // Prints either 10 or 5 — never 15
    // Whichever fiber's patch is combined last "wins"
  } yield ()
}
```

The two fibers each read the initial value `0`, compute their new value, and record their change as an `Int => Int` function. When they join, ZIO calls `combine` on the two patches. With `Differ.update`, `combine(f, g) = f.andThen(g)` — so the result of the second patch overwrites the first. Neither fiber's change is truly preserved alongside the other.

The root cause is conceptual: an `Int => Int` function doesn't carry enough information to let two independent updates coexist. We only know *what the new value is* — not *what change was made*.

To merge concurrent updates faithfully, we need a richer representation: one where a `Patch` describes *what changed*, rather than *what the new value is*. That richer representation is exactly what `Differ` provides.

---

## 2. The Differ Contract: diff, patch, combine, and empty

`Differ[Value, Patch]` is a trait with four abstract methods:

```scala
trait Differ[Value, Patch] extends Serializable {
  def diff(oldValue: Value, newValue: Value): Patch
  def patch(patch: Patch)(oldValue: Value): Value
  def combine(first: Patch, second: Patch): Patch
  def empty: Patch
}
```

Each method has a precise role:

| Method           | Purpose                                                     |
| ---------------- | ----------------------------------------------------------- |
| `Differ#diff`    | Records what changed between two `Value`s as a `Patch`      |
| `Differ#patch`   | Applies a `Patch` to a `Value` to produce the updated value |
| `Differ#combine` | Merges two `Patch`es so both changes are preserved          |
| `Differ#empty`   | The no-op `Patch` that leaves the value unchanged           |

`combine` is the key: if fiber 1 produces `patch1` and fiber 2 produces `patch2`, ZIO calls `combine(patch1, patch2)` on join and applies the result to the base value. As long as `combine` is associative and both fibers' intents can coexist, no update is lost.

### Building a custom Differ

Here is a concrete example — a `Differ` for integers where the patch type is simply `Int`, representing a numeric delta:

```scala
val addDiffer: Differ[Int, Int] = new Differ[Int, Int] {
  // diff: records the delta (how much the value changed)
  def diff(oldValue: Int, newValue: Int): Int = newValue - oldValue
  // patch: applies the delta to the old value
  def patch(patch: Int)(oldValue: Int): Int   = oldValue + patch
  // combine: merges two deltas by adding them together
  def combine(first: Int, second: Int): Int   = first + second
  // empty: the zero delta — applying it changes nothing
  def empty: Int                              = 0
}
```

Because `combine` is addition — commutative and associative — concurrent fiber patches always merge correctly regardless of join order. Let's verify the four operations in isolation before wiring the `Differ` into a `FiberRef`:

```scala
val delta1  = addDiffer.diff(0, 10)            // records: +10
// delta1: Int = 10
val delta2  = addDiffer.diff(10, 15)           // records: +5
// delta2: Int = 5
val merged  = addDiffer.combine(delta1, delta2) // merges:  +15
// merged: Int = 15
val applied = addDiffer.patch(merged)(0)        // applies: 0 + 15 = 15
// applied: Int = 15
```

Now let's wire it into a `FiberRef` and run two concurrent fibers:

```scala
ZIO.scoped {
  for {
    ref    <- FiberRef.makePatch(0, addDiffer, 0)  // initial = 0, fork patch = 0
    left   <- ref.update(_ + 10).fork              // fiber 1 produces delta +10
    right  <- ref.update(_ + 5).fork               // fiber 2 produces delta +5
    _      <- left.join
    _      <- right.join
    result <- ref.get
    _      <- Console.printLine(s"Result: $result").orDie
  } yield ()
}
```

Both fibers' deltas were combined into a single patch (+15) and applied to the initial value. The result is always 15, never 10 or 5.

### FiberRef.makePatch signatures

There are two overloads of `FiberRef.makePatch`:

```scala
object FiberRef {
  // Uses differ.empty as the patch inherited by forked child fibers
  def makePatch[Value, Patch](
    initial: Value,
    differ:  Differ[Value, Patch]
  ): ZIO[Scope, Nothing, FiberRef.WithPatch[Value, Patch]]

  // Lets you specify an explicit fork patch
  def makePatch[Value, Patch](
    initial: Value,
    differ:  Differ[Value, Patch],
    fork:    Patch
  ): ZIO[Scope, Nothing, FiberRef.WithPatch[Value, Patch]]
}
```

The `fork` patch is the delta that a child fiber *starts with* relative to the parent's current value. Passing `differ.empty` (or using the single-argument form) means a forked child inherits the parent's current value unchanged. Passing a non-empty patch is how you give children a head-start delta — useful for counter strategies where child fibers should begin from a distinct baseline.

`WithPatch[Value, Patch]` is a type alias:

```scala
type WithPatch[Value0, Patch0] = FiberRef[Value0] { type Patch = Patch0 }
```

The `Patch` type member is what lets ZIO's runtime locate the right `Differ` at join time to merge concurrent updates.

:::note[ZIO.scoped is required]
`FiberRef.makePatch` requires a `Scope` because the `FiberRef` must be cleaned up when the scope exits. Wrap your entire program in `ZIO.scoped { ... }` to provide the scope.
:::

---

## 3. Built-in Differs: set, map, chunk, and update

Writing a custom `Differ` gives you full control, but ZIO ships four factory methods that handle the most common collection types correctly:

| Factory                       | Value type  | Patch type          | Merge strategy                               |
|-------------------------------|-------------|---------------------|----------------------------------------------|
| `Differ.update[A]`            | `A`         | `A => A`            | Last-write-wins (function composition)       |
| `Differ.set[A]`               | `Set[A]`    | `SetPatch[A]`       | Merges `Add` and `Remove` operations         |
| `Differ.map[K, V, P](differ)` | `Map[K, V]` | `MapPatch[K, V, P]` | Merges `Add`, `Remove`, `Update` per key     |
| `Differ.chunk[V, P](differ)`  | `Chunk[V]`  | `ChunkPatch[V, P]`  | Merges `Append`, `Slice`, `Update` per index |

The patch types are sealed ADTs whose `combine` operations sequence their operations in order.

### Differ.set — membership merging

`Differ.set[A]` records every element addition and removal as a `SetPatch`. Two fibers that each `Add` a different element produce patches that combine into a patch that adds both elements:

**Scala 2**

```scala
Unsafe.unsafe { implicit u =>
  Runtime.default.unsafe.run(
    ZIO.scoped {
      for {
        ref    <- FiberRef.makePatch(Set.empty[String], Differ.set[String])
        left   <- ref.update(_ + "left").fork
        right  <- ref.update(_ + "right").fork
        _      <- left.join
        _      <- right.join
        result <- ref.get
        _      <- Console.printLine(s"Set result: $result").orDie
      } yield ()
    }
  ).getOrThrowFiberFailure()
}
// Set result: Set(left, right)
```

**Scala 3**

```scala
Unsafe.unsafely {
  Runtime.default.unsafe.run(
    ZIO.scoped {
      for {
        ref    <- FiberRef.makePatch(Set.empty[String], Differ.set[String])
        left   <- ref.update(_ + "left").fork
        right  <- ref.update(_ + "right").fork
        _      <- left.join
        _      <- right.join
        result <- ref.get
        _      <- Console.printLine(s"Set result: $result").orDie
      } yield ()
    }
  ).getOrThrowFiberFailure()
}
```

Both `"left"` and `"right"` appear in the final set — neither fiber's addition was lost.

### Differ.map — key-level merging

`Differ.map[Key, Value, Patch](differ)` wraps a value-level `Differ` to operate at the key level. Two fibers that add different keys produce `Add` patches that combine cleanly:

```scala
ZIO.scoped {
  for {
    differ  <- ZIO.succeed(Differ.map[String, Int, Int => Int](Differ.update[Int]))
    ref    <- FiberRef.makePatch(Map.empty[String, Int], differ)
    left   <- ref.update(_ + ("score" -> 100)).fork
    right  <- ref.update(_ + ("level" -> 5)).fork
    _      <- left.join
    _      <- right.join
    result <- ref.get
    _      <- Console.printLine(s"Map result: $result").orDie
    // Map(score -> 100, level -> 5) — both keys present
  } yield ()
}
```

### Differ.chunk — append merging

`Differ.chunk[Value, Patch](differ)` produces a `ChunkPatch` with `Append`, `Slice`, and `Update` operations. It is especially useful for building up log-style buffers from multiple concurrent fibers.

### Differ.update — deliberate last-write-wins

`Differ.update[A]` deliberately provides last-write-wins semantics. Use it when you want the most recent write to take effect — for example, a fiber that owns an entire configuration object and should replace it atomically. When you use it, you are explicitly opting out of compositional merging.

:::caution[Differ.update discards concurrent updates]
If two fibers both update a `FiberRef` backed by `Differ.update`, only one update survives on join. Choose a different Differ when concurrent updates from multiple fibers must all be reflected.
:::

---

## 4. Composing Differs: zip, orElseEither, and transform

Real programs often need to track complex state — case classes, `Either` values, wrapped types. `Differ` provides three combinators for building composite `Differ`s from simpler ones.

### zip (`<*>`) — product types

`differ1.zip(differ2)` (also written `differ1 <*> differ2`) creates a `Differ[(V1, V2), (P1, P2)]`. It applies each sub-`Differ` to its corresponding field independently, so concurrent updates to different fields never interfere:

```scala
// A Differ for (Set[String], Int): the set field uses set-merging,
// the int field uses last-write-wins.
val pairDiffer: Differ[(Set[String], Int), (Differ.SetPatch[String], Int => Int)] =
  Differ.set[String] <*> Differ.update[Int]
```

Let's run two concurrent fibers against this composed `Differ` and inspect the merged result:

**Scala 2**

```scala
Unsafe.unsafe { implicit u =>
  Runtime.default.unsafe.run(
    ZIO.scoped {
      for {
        ref    <- FiberRef.makePatch((Set.empty[String], 0), pairDiffer)
        left   <- ref.update { case (s, n) => (s + "item", n) }.fork  // adds to the set
        right  <- ref.update { case (s, n) => (s, 42) }.fork          // updates the int
        _      <- left.join
        _      <- right.join
        result <- ref.get
        _      <- Console.printLine(s"Pair result: $result").orDie
      } yield ()
    }
  ).getOrThrowFiberFailure()
}
// Pair result: (Set(item),42)
```

**Scala 3**

```scala
Unsafe.unsafely {
  Runtime.default.unsafe.run(
    ZIO.scoped {
      for {
        ref    <- FiberRef.makePatch((Set.empty[String], 0), pairDiffer)
        left   <- ref.update { case (s, n) => (s + "item", n) }.fork  // adds to the set
        right  <- ref.update { case (s, n) => (s, 42) }.fork          // updates the int
        _      <- left.join
        _      <- right.join
        result <- ref.get
        _      <- Console.printLine(s"Pair result: $result").orDie
      } yield ()
    }
  ).getOrThrowFiberFailure()
}
```

The set received `"item"` and the int became `42` — each field was updated by its own `Differ` independently.

To apply this to a case class instead of a raw tuple, pair `zip` with `transform`:

```scala
case class State(tags: Set[String], version: Int)

val stateDiffer: Differ[State, (Differ.SetPatch[String], Int => Int)] =
  (Differ.set[String] <*> Differ.update[Int]).transform(
    f = { case (tags, version) => State(tags, version) },
    g = state => (state.tags, state.version)
  )
```

### orElseEither (`<+>`) — sum types

`differ1.orElseEither(differ2)` (also written `differ1 <+> differ2`) creates a `Differ[Either[V1, V2], OrPatch[V1, V2, P1, P2]]`. The `OrPatch` ADT handles four cases:

- `UpdateLeft(patch)` — both old and new values are `Left`, apply the left differ
- `UpdateRight(patch)` — both old and new values are `Right`, apply the right differ
- `SetLeft(value)` — old was `Right`, new is `Left` (side switch)
- `SetRight(value)` — old was `Left`, new is `Right` (side switch)

Here is a concrete example showing how `orElseEither` handles both in-side updates and a side switch in concurrent fibers:

```scala
val eitherDiffer: Differ[Either[String, Int], Differ.OrPatch[String, Int, String => String, Int => Int]] =
  Differ.update[String] <+> Differ.update[Int]

ZIO.scoped {
  for {
    // Type annotation on the value (not the method) lets Scala infer both type params
    ref    <- FiberRef.makePatch(Left("hello"): Either[String, Int], eitherDiffer)
    left   <- ref.update { case Left(s)  => Left(s + " world")
                           case Right(n) => Right(n) }.fork
    right  <- ref.update { case Left(_)  => Right(99)    // switches to Right
                           case Right(n) => Right(n) }.fork
    _      <- left.join
    _      <- right.join
    result <- ref.get
    _      <- Console.printLine(s"Either result: $result").orDie
  } yield ()
}
```

### transform — isomorphic types

`differ.transform(f, g)` lifts a `Differ[V, P]` to `Differ[V2, P]` via the isomorphism `f: V => V2` and `g: V2 => V`. The patch type stays the same; only the value type changes. This is how we adapt a `Differ` to work with a wrapper type, a newtype, or any type that is structurally equivalent to `V`:

```scala
// NonEmptyList[A] is isomorphic to (A, List[A])
final case class Nel[A](head: A, tail: List[A])

val nelDiffer: Differ[Nel[Int], (Int => Int, List[Int] => List[Int])] =
  (Differ.update[Int] <*> Differ.update[List[Int]]).transform(
    f = { case (h, t) => Nel(h, t) },
    g = nel         => (nel.head, nel.tail)
  )
```

---

## 5. The Five Differ Laws

A `Differ` is only correct if its four methods satisfy a set of algebraic laws. Violating any law silently corrupts `FiberRef` state on fiber join — the merged value will not reflect what either fiber intended.

Here are the five laws (ZIO's `DifferSpec` calls them this way):

1. **Associativity** — `combine(combine(p1, p2), p3)` produces the same result as `combine(p1, combine(p2, p3))`
2. **Identity** — `combine(p, empty)` and `combine(empty, p)` both behave like `p`
3. **Self-diff is empty** — `diff(v, v) == empty` for every value `v`
4. **Round-trip** — `patch(diff(old, new))(old) == new`
5. **Empty patch is identity** — `patch(empty)(v) == v`

Let's verify all five for `addDiffer` using pure expressions:

```scala
// Setup: three sequential patches
val p1 = addDiffer.diff(0, 3)    // delta +3
// p1: Int = 3
val p2 = addDiffer.diff(3, 7)    // delta +4
// p2: Int = 4
val p3 = addDiffer.diff(7, 12)   // delta +5
// p3: Int = 5

// Law 1: combine is associative
val law1 = {
  val lhs = addDiffer.patch(addDiffer.combine(addDiffer.combine(p1, p2), p3))(0)
  val rhs = addDiffer.patch(addDiffer.combine(p1, addDiffer.combine(p2, p3)))(0)
  lhs == rhs  // (3+4)+5 == 3+(4+5)
}
// law1: Boolean = true

// Law 2: empty is the combine identity
val law2 = {
  val p   = addDiffer.diff(0, 5)
  val lhs = addDiffer.patch(addDiffer.combine(p, addDiffer.empty))(0)
  val rhs = addDiffer.patch(addDiffer.combine(addDiffer.empty, p))(0)
  lhs == 5 && rhs == 5
}
// law2: Boolean = true

// Law 3: diff(v, v) == empty
val law3 = addDiffer.diff(42, 42) == addDiffer.empty  // 0 == 0
// law3: Boolean = true

// Law 4: patch(diff(old, new))(old) == new
val law4 = {
  val p = addDiffer.diff(0, 15)
  addDiffer.patch(p)(0) == 15
}
// law4: Boolean = true

// Law 5: patch(empty)(v) == v
val law5 = addDiffer.patch(addDiffer.empty)(42) == 42  // 42 + 0 == 42
// law5: Boolean = true

(law1, law2, law3, law4, law5)
// res8: (Boolean, Boolean, Boolean, Boolean, Boolean) = (
//   true,
//   true,
//   true,
//   true,
//   true
// )
```

All five laws hold. ZIO's `DifferSpec` tests these same laws for every built-in `Differ` using property-based generators via `zio-test`. To run those tests yourself:

```bash
sbt "coreTestsJVM/testOnly *DifferSpec"
```

The suite runs five law-groups — associativity, identity, self-diff-is-empty, round-trip, and empty-patch-identity — across `Differ.set[Int]`, `Differ.update[Int] <*> Differ.update[Int]`, `Differ.update[Int] <+> Differ.update[Int]`, `Differ.map`, and `Differ.chunk`.

:::tip[Write your own law tests]
When you build a custom `Differ`, copy the `diffLaws` helper from `DifferSpec` and run it against your own `Differ` with an appropriate `Gen`. A failing law test is far easier to debug than a corrupted `FiberRef` value at runtime.
:::

---

## 6. How ZIO Uses Differ Internally

`Differ` is not a power-user feature layered on top of ZIO — it is woven into ZIO's fiber runtime itself. Every `ZIO` application is already using `Differ` whenever it spawns fibers that interact with the environment, logging, or runtime flags.

ZIO maintains several internal `FiberRef`s that are all backed by `Differ`:

```scala
object FiberRef {
  // ZEnvironment changes (ZLayer, withEnvironment, provideLayer, etc.)
  private[zio] val currentEnvironment: FiberRef.WithPatch[ZEnvironment[Any], ZEnvironment.Patch[Any, Any]]

  // Logger set (ZIO.loggers, ZLayer providing a logger)
  private[zio] val currentLoggers: FiberRef.WithPatch[Set[ZLogger[String, Any]], SetPatch[ZLogger[String, Any]]]

  // Supervisor set (ZIO.withRuntimeFlags, custom supervisors)
  private[zio] val currentSupervisor: FiberRef.WithPatch[Supervisor[Any], Supervisor.Patch]

  // RuntimeFlags (ZIO.withRuntimeFlags, debug mode, etc.)
  private[zio] val currentRuntimeFlags: FiberRef.WithPatch[RuntimeFlags, RuntimeFlags.Patch]
}
```

Because each of these uses a proper `Differ`, two fibers can both call `ZIO.withRuntimeFlags`, or two `ZLayer` instances can each provide a different service, and after they join, both changes are preserved.

The `FiberRefSpec.makeEnvironment` test in ZIO's own test suite demonstrates this directly: after two fibers each add a different service to a `ZEnvironment` `FiberRef` and join, all services are accessible. Let's mirror that pattern with our own environment:

```scala
case class ServiceA(name: String)
case class ServiceB(port: Int)
```

Now let's run two concurrent fibers that each update the environment and confirm both services survive the join:

**Scala 2**

```scala
// ZEnvironment[+R] is covariant; get[A >: R] retrieves A when A is a supertype of R.
// Starting from ServiceA with ServiceB lets us get either after join.
Unsafe.unsafe { implicit u =>
  Runtime.default.unsafe.run(
    ZIO.scoped {
      val initial = ZEnvironment(ServiceA("default"), ServiceB(0))
      for {
        ref    <- FiberRef.makeEnvironment[ServiceA with ServiceB](initial)
        left   <- ref.update(_.add(ServiceA("auth"))).fork
        right  <- ref.update(_.add(ServiceB(8080))).fork
        _      <- left.join
        _      <- right.join
        env    <- ref.get
        _      <- Console.printLine(s"ServiceA: ${env.get[ServiceA].name}").orDie
        _      <- Console.printLine(s"ServiceB: ${env.get[ServiceB].port}").orDie
      } yield ()
    }
  ).getOrThrowFiberFailure()
}
// ServiceA: auth
// ServiceB: 8080
```

**Scala 3**

```scala
// ZEnvironment[+R] is covariant; get[A >: R] retrieves A when A is a supertype of R.
// Starting from ServiceA with ServiceB lets us get either after join.
Unsafe.unsafely {
  Runtime.default.unsafe.run(
    ZIO.scoped {
      val initial = ZEnvironment(ServiceA("default"), ServiceB(0))
      for {
        ref    <- FiberRef.makeEnvironment[ServiceA with ServiceB](initial)
        left   <- ref.update(_.add(ServiceA("auth"))).fork
        right  <- ref.update(_.add(ServiceB(8080))).fork
        _      <- left.join
        _      <- right.join
        env    <- ref.get
        _      <- Console.printLine(s"ServiceA: ${env.get[ServiceA].name}").orDie
        _      <- Console.printLine(s"ServiceB: ${env.get[ServiceB].port}").orDie
      } yield ()
    }
  ).getOrThrowFiberFailure()
}
```

Both services are present after the join. ZIO achieves this by using `Differ.environment` internally, which tracks key-level additions and removals as a `Patch` ADT. When the two fibers join, ZIO calls `combine` on their patches — the same `combine` you implement when writing your own `Differ`.

This is the unifying insight: `withEnvironment`, `ZLayer`, `ZIO.withRuntimeFlags`, and logger installation are all compositional across concurrent fibers *because* every one of them is backed by a `Differ` that knows how to merge patches.

---

## Putting It Together

The complete example below shows the single idea the whole tutorial builds toward: four `FiberRef`s — each backed by a different `Differ` — all updated by concurrent fibers and all correctly merged after join. No update is lost.

```scala
import zio._

object CompleteExample extends ZIOAppDefault {

  // A custom additive Differ: patch type is Int (a numeric delta)
  val addDiffer: Differ[Int, Int] = new Differ[Int, Int] {
    def combine(first: Int, second: Int): Int   = first + second
    def diff(oldValue: Int, newValue: Int): Int = newValue - oldValue
    def empty: Int                              = 0
    def patch(patch: Int)(oldValue: Int): Int   = oldValue + patch
  }

  case class ServiceA(name: String)
  case class ServiceB(port: Int)

  override def run: ZIO[Any, Any, Unit] = ZIO.scoped {
    for {
      // One ref per Differ — each uses a different merge strategy
      counterRef <- FiberRef.makePatch(0, addDiffer, 0)
      tagsRef    <- FiberRef.makePatch(Set.empty[String], Differ.set[String])
      pairRef    <- FiberRef.makePatch(
                      (Set.empty[String], 0),
                      Differ.set[String] <*> Differ.update[Int]
                    )
      envRef     <- FiberRef.makeEnvironment[ServiceA with ServiceB](
                      ZEnvironment(ServiceA("default")).add(ServiceB(0))
                    )

      // Eight concurrent fibers — two per ref, each making an independent change
      f1a <- counterRef.update(_ + 10).fork
      f1b <- counterRef.update(_ + 5).fork
      f2a <- tagsRef.update(_ + "alpha").fork
      f2b <- tagsRef.update(_ + "beta").fork
      f3a <- pairRef.update { case (s, n) => (s + "tag", n) }.fork
      f3b <- pairRef.update { case (s, n) => (s, 99) }.fork
      f4a <- envRef.update(_.add(ServiceA("auth"))).fork
      f4b <- envRef.update(_.add(ServiceB(8080))).fork

      // Join all — ZIO calls Differ#combine on every pair of patches
      _ <- f1a.join
      _ <- f1b.join
      _ <- f2a.join
      _ <- f2b.join
      _ <- f3a.join
      _ <- f3b.join
      _ <- f4a.join
      _ <- f4b.join

      // Read the merged state — every fiber's change survived
      counter <- counterRef.get
      tags    <- tagsRef.get
      pair    <- pairRef.get
      env     <- envRef.get

      _ <- Console.printLine("=== All concurrent updates merged, none overwritten ===").orDie
      _ <- Console.printLine(s"counter (addDiffer):        $counter").orDie   // 15
      _ <- Console.printLine(s"tags    (Differ.set):       $tags").orDie      // Set(alpha, beta)
      _ <- Console.printLine(s"pair    (zip <*>):          $pair").orDie      // (Set(tag),99)
      _ <- Console.printLine(s"ServiceA (ZEnvironment):   ${env.get[ServiceA].name}").orDie  // auth
      _ <- Console.printLine(s"ServiceB (ZEnvironment):   ${env.get[ServiceB].port}").orDie  // 8080
    } yield ()
  }
}
```

---

## Running the Examples

Clone the ZIO repository and navigate to the examples module:

```bash
git clone https://github.com/zio/zio.git
cd zio/zio-examples
```

### LastWriteWinsVsCompositionalMergingExample — Last-Write-Wins vs. Compositional Merging

Runs two fibers concurrently against a `Differ.update[Int]` FiberRef (non-deterministic) and then against a custom `addDiffer` FiberRef (always 15), making the difference between the two strategies concrete.

<details>
  <summary>zio-examples/differ-compositional-updates/src/main/scala/differcompositionalupdates/LastWriteWinsVsCompositionalMergingExample.scala</summary>

```scala title="zio-examples/differ-compositional-updates/src/main/scala/differcompositionalupdates/LastWriteWinsVsCompositionalMergingExample.scala" showLineNumbers
package differcompositionalupdates

import zio._

/** Title: Last-Write-Wins vs. Compositional Merging
  * Description: Demonstrates that Differ.update (last-write-wins) loses one fiber's update
  * when two fibers concurrently modify a FiberRef, then contrasts it with a custom addDiffer
  * that merges both changes faithfully.
  * Run: sbt "differ-compositional-updates/runMain differcompositionalupdates.LastWriteWinsVsCompositionalMergingExample"
  */
object LastWriteWinsVsCompositionalMergingExample extends ZIOAppDefault {

  // A custom Differ that records numeric deltas: diff = subtraction, patch = addition.
  // Because both operations are inverse (and combine is addition), concurrent fiber
  // patches are always merged: delta1 + delta2 = the sum of both updates.
  val addDiffer: Differ[Int, Int] = new Differ[Int, Int] {
    def combine(first: Int, second: Int): Int   = first + second
    def diff(oldValue: Int, newValue: Int): Int = newValue - oldValue
    def empty: Int                              = 0
    def patch(patch: Int)(oldValue: Int): Int   = oldValue + patch
  }

  // ===== Demo 1: Differ.update — last-write-wins =====
  // Two fibers both update the ref; the last one to join wins.
  // The result is either 10 or 5 — never 15.
  val lastWriteWinsDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("--- Differ.update (last-write-wins) ---").orDie
      ref    <- FiberRef.makePatch(0, Differ.update[Int])
      left   <- ref.update(_ + 10).fork  // wants to add 10
      right  <- ref.update(_ + 5).fork   // wants to add 5
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"Last-write-wins result: $result  (10 or 5, never 15)").orDie
    } yield ()
  }

  // ===== Demo 2: addDiffer — compositional merge =====
  // Two fibers both update the ref; both deltas (10 and 5) are merged.
  // The result is always 15 — deterministic and correct.
  val compositionalMergeDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("--- addDiffer (compositional merge) ---").orDie
      ref    <- FiberRef.makePatch(0, addDiffer, 0)  // initial = 0, fork patch = 0
      left   <- ref.update(_ + 10).fork              // fiber 1 adds 10 → delta 10
      right  <- ref.update(_ + 5).fork               // fiber 2 adds 5  → delta 5
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"Compositional merge result: $result  (always 15)").orDie
    } yield ()
  }

  override def run: ZIO[Any, Any, Unit] =
    for {
      _ <- Console.printLine("=== Concept 1: Last-Write-Wins vs. Compositional Merging ===").orDie
      _ <- lastWriteWinsDemo
      _ <- Console.printLine("").orDie
      _ <- compositionalMergeDemo
    } yield ()
}
```

</details>

Run it with:

```bash
sbt "differ-compositional-updates/runMain differcompositionalupdates.LastWriteWinsVsCompositionalMergingExample"
```

### BuiltInDiffersExample — Built-in Differs

Demonstrates `Differ.set`, `Differ.map`, and `Differ.chunk` each wired into `FiberRef.makePatch`. Two fibers add independent elements in each case; after the join, all additions are present.

<details>
  <summary>zio-examples/differ-compositional-updates/src/main/scala/differcompositionalupdates/BuiltInDiffersExample.scala</summary>

```scala title="zio-examples/differ-compositional-updates/src/main/scala/differcompositionalupdates/BuiltInDiffersExample.scala" showLineNumbers
package differcompositionalupdates

import zio._

/** Title: Built-in Differs — set, map, chunk, and update
  * Description: Shows how to wire Differ.set, Differ.map, and Differ.chunk into
  * FiberRef.makePatch and observe that concurrent fiber additions are always merged,
  * not overwritten.
  * Run: sbt "differ-compositional-updates/runMain differcompositionalupdates.BuiltInDiffersExample"
  */
object BuiltInDiffersExample extends ZIOAppDefault {

  // ===== Differ.set — membership merging =====
  // Two fibers each add a different element; both appear in the final Set.
  val setDifferDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("--- Differ.set ---").orDie
      ref    <- FiberRef.makePatch(Set.empty[String], Differ.set[String])
      left   <- ref.update(_ + "left").fork
      right  <- ref.update(_ + "right").fork
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"Set result: $result").orDie  // Set(left, right)
    } yield ()
  }

  // ===== Differ.map — key-level merging =====
  // Two fibers add different keys; both entries appear in the final Map.
  val mapDifferDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("--- Differ.map ---").orDie
      differ  = Differ.map[String, Int, Int => Int](Differ.update[Int])
      ref    <- FiberRef.makePatch(Map.empty[String, Int], differ)
      left   <- ref.update(_ + ("a" -> 1)).fork
      right  <- ref.update(_ + ("b" -> 2)).fork
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"Map result: $result").orDie  // Map(a -> 1, b -> 2)
    } yield ()
  }

  // ===== Differ.chunk — append merging =====
  // Two fibers each append different elements; both appear in the final Chunk.
  val chunkDifferDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("--- Differ.chunk ---").orDie
      differ  = Differ.chunk[String, String => String](Differ.update[String])
      ref    <- FiberRef.makePatch(Chunk.empty[String], differ)
      left   <- ref.update(_ :+ "alpha").fork
      right  <- ref.update(_ :+ "beta").fork
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"Chunk result: $result").orDie  // Chunk(alpha, beta) or Chunk(beta, alpha)
    } yield ()
  }

  override def run: ZIO[Any, Any, Unit] =
    for {
      _ <- Console.printLine("=== Concept 2: Built-in Differs ===").orDie
      _ <- setDifferDemo
      _ <- Console.printLine("").orDie
      _ <- mapDifferDemo
      _ <- Console.printLine("").orDie
      _ <- chunkDifferDemo
    } yield ()
}
```

</details>

Run it with:

```bash
sbt "differ-compositional-updates/runMain differcompositionalupdates.BuiltInDiffersExample"
```

### ComposingDiffersExample — Composing Differs

Covers `zip` (`<*>`), `orElseEither` (`<+>`), and `transform` with runnable programs showing the composed `Differ` applied to a `(Set[String], Int)` pair, an `Either[String, Int]`, and an isomorphic wrapper type.

<details>
  <summary>zio-examples/differ-compositional-updates/src/main/scala/differcompositionalupdates/ComposingDiffersExample.scala</summary>

```scala title="zio-examples/differ-compositional-updates/src/main/scala/differcompositionalupdates/ComposingDiffersExample.scala" showLineNumbers
package differcompositionalupdates

import zio._

/** Title: Composing Differs — zip, orElseEither, and transform
  * Description: Demonstrates how to compose Differs with <*> (zip) for product types,
  * <+> (orElseEither) for Either values, and transform for isomorphic types.
  * Run: sbt "differ-compositional-updates/runMain differcompositionalupdates.ComposingDiffersExample"
  */
object ComposingDiffersExample extends ZIOAppDefault {

  // ===== zip (<*>) — product type merging =====
  // One fiber adds an element to the Set; another updates the Int.
  // Both changes are merged independently by their respective Differs.
  val zipDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("--- zip (<*>): (Set[String], Int) ---").orDie
      differ  = Differ.set[String] <*> Differ.update[Int]
      ref    <- FiberRef.makePatch((Set.empty[String], 0), differ)
      left   <- ref.update { case (s, n) => (s + "item", n) }.fork   // adds to set
      right  <- ref.update { case (s, n) => (s, 42) }.fork           // overwrites int
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"zip result: $result").orDie  // (Set(item), 42)
    } yield ()
  }

  // ===== orElseEither (<+>) — Either type merging =====
  // When two fibers both update the same side, their patches are combined.
  // When one switches sides, SetLeft / SetRight describes the transition.
  val orElseEitherDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("--- orElseEither (<+>): Either[String, Int] ---").orDie
      differ  = Differ.update[String] <+> Differ.update[Int]
      ref    <- FiberRef.makePatch(Left("hello"): Either[String, Int], differ)
      left   <- ref.update {
                  case Left(s)  => Left(s + " world")   // same side: updates the string
                  case Right(n) => Right(n)
                }.fork
      right  <- ref.update {
                  case Left(_)  => Right(99)             // switches to Right side
                  case Right(n) => Right(n)
                }.fork
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"orElseEither result: $result").orDie
    } yield ()
  }

  // ===== transform — isomorphic type adaptation =====
  // Lift a Differ[String, String => String] to Differ[Option[String], String => String]
  // using the isomorphism Option[String] <-> String (with a default).
  val transformDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("--- transform: Option[String] via Differ.update[String] ---").orDie
      // f: String → Option[String], g: Option[String] → String
      differ  = Differ.update[String].transform[Option[String]](
                  f = s    => if (s.isEmpty) None else Some(s),
                  g = opt  => opt.getOrElse("")
                )
      ref    <- FiberRef.makePatch(None: Option[String], differ)
      left   <- ref.update(_ => Some("hello")).fork
      _      <- left.join
      result <- ref.get
      _      <- Console.printLine(s"transform result: $result").orDie  // Some(hello)
    } yield ()
  }

  override def run: ZIO[Any, Any, Unit] =
    for {
      _ <- Console.printLine("=== Concept 3: Composing Differs ===").orDie
      _ <- zipDemo
      _ <- Console.printLine("").orDie
      _ <- orElseEitherDemo
      _ <- Console.printLine("").orDie
      _ <- transformDemo
    } yield ()
}
```

</details>

Run it with:

```bash
sbt "differ-compositional-updates/runMain differcompositionalupdates.ComposingDiffersExample"
```

### DifferLawsVerificationExample — The Four Differ Laws Verified

Verifies all five laws for `addDiffer` using pure expressions and prints `[PASS]` / `[FAIL]` for each. Useful as a template for testing your own custom `Differ`s.

<details>
  <summary>zio-examples/differ-compositional-updates/src/main/scala/differcompositionalupdates/DifferLawsVerificationExample.scala</summary>

```scala title="zio-examples/differ-compositional-updates/src/main/scala/differcompositionalupdates/DifferLawsVerificationExample.scala" showLineNumbers
package differcompositionalupdates

import zio._

/** Title: The Four Differ Laws — Verified by Hand
  * Description: Manually verifies all four correctness laws for a custom addDiffer:
  * associativity, identity, self-diff-is-empty, and round-trip. Violations would
  * silently corrupt FiberRef state on join.
  * Run: sbt "differ-compositional-updates/runMain differcompositionalupdates.DifferLawsVerificationExample"
  */
object DifferLawsVerificationExample extends ZIOAppDefault {

  val addDiffer: Differ[Int, Int] = new Differ[Int, Int] {
    def combine(first: Int, second: Int): Int   = first + second
    def diff(oldValue: Int, newValue: Int): Int = newValue - oldValue
    def empty: Int                              = 0
    def patch(patch: Int)(oldValue: Int): Int   = oldValue + patch
  }

  def checkLaw(name: String, holds: Boolean): ZIO[Any, Nothing, Unit] =
    Console.printLine(s"  [${ if (holds) "PASS" else "FAIL" }] $name").orDie

  override def run: ZIO[Any, Any, Unit] = {
    val p1 = addDiffer.diff(0, 3)    // delta = 3
    val p2 = addDiffer.diff(3, 7)    // delta = 4
    val p3 = addDiffer.diff(7, 12)   // delta = 5

    for {
      _ <- Console.printLine("=== Concept 4: The Four Differ Laws ===").orDie

      // Law 1: combine is associative
      // (p1 combine p2) combine p3  ==  p1 combine (p2 combine p3)
      _ <- {
        val lhs = addDiffer.patch(addDiffer.combine(addDiffer.combine(p1, p2), p3))(0)
        val rhs = addDiffer.patch(addDiffer.combine(p1, addDiffer.combine(p2, p3)))(0)
        checkLaw("combine is associative", lhs == rhs)
      }

      // Law 2: empty is the identity for combine
      // patch(combine(p, empty))(old) == patch(p)(old)
      _ <- {
        val p   = addDiffer.diff(0, 5)
        val lhs = addDiffer.patch(addDiffer.combine(p, addDiffer.empty))(0)
        val rhs = addDiffer.patch(addDiffer.combine(addDiffer.empty, p))(0)
        checkLaw("empty is the combine identity", lhs == 5 && rhs == 5)
      }

      // Law 3: diffing a value with itself returns empty
      _ <- {
        val selfPatch = addDiffer.diff(42, 42)
        checkLaw("diff(v, v) == empty", selfPatch == addDiffer.empty)
      }

      // Law 4: diff then patch is a round-trip
      // patch(diff(old, new))(old) == new
      _ <- {
        val p      = addDiffer.diff(0, 15)
        val result = addDiffer.patch(p)(0)
        checkLaw("patch(diff(old, new))(old) == new", result == 15)
      }

      // Law 5: patching with empty is identity
      // patch(empty)(v) == v
      _ <- {
        val result = addDiffer.patch(addDiffer.empty)(42)
        checkLaw("patch(empty)(v) == v", result == 42)
      }
    } yield ()
  }
}
```

</details>

Run it with:

```bash
sbt "differ-compositional-updates/runMain differcompositionalupdates.DifferLawsVerificationExample"
```

### ZIOInternalDiffersExample — How ZIO Uses Differ Internally

Mirrors ZIO's own `FiberRefSpec.makeEnvironment` test: two fibers add different services to a `ZEnvironment` FiberRef and after joining, both services are present. Also lists the four internal `FiberRef`s and their patch types.

<details>
  <summary>zio-examples/differ-compositional-updates/src/main/scala/differcompositionalupdates/ZIOInternalDiffersExample.scala</summary>

```scala title="zio-examples/differ-compositional-updates/src/main/scala/differcompositionalupdates/ZIOInternalDiffersExample.scala" showLineNumbers
package differcompositionalupdates

import zio._

/** Title: How ZIO Uses Differ Internally — Environment, Loggers, RuntimeFlags
  * Description: Shows that ZIO's own FiberRefs (currentEnvironment, currentLoggers,
  * currentRuntimeFlags) are all backed by Differ, making withEnvironment and ZLayer
  * compositional across concurrent fibers. Demonstrates via FiberRef.makeEnvironment
  * that two concurrent fiber updates to a ZEnvironment are always merged.
  * Run: sbt "differ-compositional-updates/runMain differcompositionalupdates.ZIOInternalDiffersExample"
  */
object ZIOInternalDiffersExample extends ZIOAppDefault {

  // Services for demonstration
  case class ServiceA(value: String)
  case class ServiceB(value: Int)

  // Mirrors FiberRefSpec's makeEnvironment test: two fibers each add a different
  // service to a ZEnvironment FiberRef; after joining, both services are present.
  // ZEnvironment[+R] is covariant; get[A >: R] works when A is a supertype of R.
  // Starting with ServiceA with ServiceB lets us get either service after join.
  val environmentMergeDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    val initial = ZEnvironment(ServiceA("default"), ServiceB(0))
    for {
      _      <- Console.printLine("--- FiberRef.makeEnvironment: concurrent environment updates ---").orDie
      ref    <- FiberRef.makeEnvironment[ServiceA with ServiceB](initial)
      left   <- ref.update(_.add(ServiceA("hello"))).fork
      right  <- ref.update(_.add(ServiceB(42))).fork
      _      <- left.join
      _      <- right.join
      env    <- ref.get
      _      <- Console.printLine(s"ServiceA present: ${env.get[ServiceA].value}").orDie
      _      <- Console.printLine(s"ServiceB present: ${env.get[ServiceB].value}").orDie
    } yield ()
  }

  // Demonstrates that ZIO.withRuntimeFlags is compositional: two fibers can each
  // modify runtime flags and both modifications are merged on join.
  val runtimeFlagsInfo: ZIO[Any, Nothing, Unit] =
    for {
      _ <- Console.printLine("--- ZIO internal FiberRefs use Differ ---").orDie
      _ <- Console.printLine("  currentEnvironment  → Differ.environment[A] (ZEnvironment.Patch)").orDie
      _ <- Console.printLine("  currentLoggers      → Differ.set[ZLogger] (SetPatch)").orDie
      _ <- Console.printLine("  currentSupervisor   → Differ.supervisor (Supervisor.Patch)").orDie
      _ <- Console.printLine("  currentRuntimeFlags → Differ.runtimeFlags (RuntimeFlags.Patch)").orDie
      _ <- Console.printLine("  All of these compose via combine on fiber join.").orDie
    } yield ()

  override def run: ZIO[Any, Any, Unit] =
    for {
      _ <- Console.printLine("=== Concept 5: How ZIO Uses Differ Internally ===").orDie
      _ <- runtimeFlagsInfo
      _ <- Console.printLine("").orDie
      _ <- environmentMergeDemo
    } yield ()
}
```

</details>

Run it with:

```bash
sbt "differ-compositional-updates/runMain differcompositionalupdates.ZIOInternalDiffersExample"
```

### CompleteExample — End-to-End Tour

Runs all five demonstrations in sequence: `addDiffer`, `Differ.set`, `Differ.map`, `zip`, and `ZEnvironment`. Each prints the merged result after joining concurrent fibers.

<details>
  <summary>zio-examples/differ-compositional-updates/src/main/scala/differcompositionalupdates/CompleteExample.scala</summary>

```scala title="zio-examples/differ-compositional-updates/src/main/scala/differcompositionalupdates/CompleteExample.scala" showLineNumbers
package differcompositionalupdates

import zio._

/** Title: The Differ Data Type — Compositional FiberRef Updates End-to-End
  * Description: A comprehensive example showing how Differ[Value, Patch] enables
  * concurrent fiber updates to a FiberRef to be merged faithfully rather than
  * overwritten. Covers a custom Differ, built-in Differs (set, map), composed
  * Differs (zip), and the internal ZEnvironment Differ used by ZIO itself.
  * Run: sbt "differ-compositional-updates/runMain differcompositionalupdates.CompleteExample"
  */
object CompleteExample extends ZIOAppDefault {

  // ===== Custom Differ: additive numeric deltas =====
  // diff records the delta (newValue - oldValue), patch applies it (oldValue + delta),
  // combine merges two deltas (addition is associative and commutative), empty = 0.
  val addDiffer: Differ[Int, Int] = new Differ[Int, Int] {
    def combine(first: Int, second: Int): Int   = first + second
    def diff(oldValue: Int, newValue: Int): Int = newValue - oldValue
    def empty: Int                              = 0
    def patch(patch: Int)(oldValue: Int): Int   = oldValue + patch
  }

  // Two fibers each add a number; because addDiffer.combine merges deltas,
  // the result is always the sum — never one or the other.
  val addDifferDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("[addDiffer] Starting concurrent fiber updates...").orDie
      ref    <- FiberRef.makePatch(0, addDiffer, 0)
      left   <- ref.update(_ + 10).fork   // fiber 1 contributes delta +10
      right  <- ref.update(_ + 5).fork    // fiber 2 contributes delta +5
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"[addDiffer] Result after join: $result").orDie
    } yield ()
  }

  // ===== Built-in Differ.set — membership merging =====
  // Both fibers' Add patches are combined: the final Set always contains both elements.
  val setDifferDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("[Differ.set] Starting concurrent fiber updates...").orDie
      ref    <- FiberRef.makePatch(Set.empty[String], Differ.set[String])
      left   <- ref.update(_ + "fiber-left").fork
      right  <- ref.update(_ + "fiber-right").fork
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"[Differ.set] Members after join: $result").orDie
    } yield ()
  }

  // ===== Built-in Differ.map — key-level merging =====
  // Each fiber adds a different key; MapPatch.combine keeps both Add operations.
  val mapDifferDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("[Differ.map] Starting concurrent fiber updates...").orDie
      differ  = Differ.map[String, Int, Int => Int](Differ.update[Int])
      ref    <- FiberRef.makePatch(Map.empty[String, Int], differ)
      left   <- ref.update(_ + ("score" -> 100)).fork
      right  <- ref.update(_ + ("level" -> 5)).fork
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"[Differ.map] Entries after join: $result").orDie
    } yield ()
  }

  // ===== Composed Differ: zip (<*>) — field-by-field merging =====
  // Differ.set tracks the Set field; Differ.update tracks the Int field independently.
  // Each fiber modifies a different field; both changes survive the join.
  val zipDifferDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    for {
      _      <- Console.printLine("[zip (<*>)] Starting concurrent fiber updates...").orDie
      differ  = Differ.set[String] <*> Differ.update[Int]
      ref    <- FiberRef.makePatch((Set.empty[String], 0), differ)
      left   <- ref.update { case (s, n) => (s + "tag", n) }.fork   // adds "tag" to set
      right  <- ref.update { case (s, n) => (s, 99) }.fork          // sets int to 99
      _      <- left.join
      _      <- right.join
      result <- ref.get
      _      <- Console.printLine(s"[zip (<*>)] Pair after join: $result").orDie
    } yield ()
  }

  // ===== Internal ZIO: ZEnvironment Differ =====
  // ZIO uses Differ.environment internally for currentEnvironment so that two fibers
  // running withEnvironment with different services both keep their service after join.
  // ZEnvironment[+R] is covariant; get[A >: R] retrieves a service that is a supertype
  // of the environment type R. Starting from ServiceA with ServiceB lets us get either.
  case class ServiceA(name: String)
  case class ServiceB(port: Int)

  val environmentDemo: ZIO[Any, Nothing, Unit] = ZIO.scoped {
    // ZEnvironment.apply[A, B] creates ZEnvironment[A with B] — correctly typed
    val initial = ZEnvironment(ServiceA("default"), ServiceB(0))
    for {
      _      <- Console.printLine("[ZEnvironment] Starting concurrent fiber updates...").orDie
      ref    <- FiberRef.makeEnvironment[ServiceA with ServiceB](initial)
      left   <- ref.update(_.add(ServiceA("auth"))).fork
      right  <- ref.update(_.add(ServiceB(8080))).fork
      _      <- left.join
      _      <- right.join
      env    <- ref.get
      _      <- Console.printLine(s"[ZEnvironment] ServiceA: ${env.get[ServiceA].name}").orDie
      _      <- Console.printLine(s"[ZEnvironment] ServiceB port: ${env.get[ServiceB].port}").orDie
    } yield ()
  }

  override def run: ZIO[Any, Any, Unit] =
    for {
      _ <- Console.printLine("=== The Differ Data Type: Compositional FiberRef Updates ===").orDie
      _ <- Console.printLine("").orDie
      _ <- addDifferDemo
      _ <- Console.printLine("").orDie
      _ <- setDifferDemo
      _ <- Console.printLine("").orDie
      _ <- mapDifferDemo
      _ <- Console.printLine("").orDie
      _ <- zipDifferDemo
      _ <- Console.printLine("").orDie
      _ <- environmentDemo
      _ <- Console.printLine("").orDie
      _ <- Console.printLine("All updates from concurrent fibers were merged, not overwritten.").orDie
    } yield ()
}
```

</details>

Run it with:

```bash
sbt "differ-compositional-updates/runMain differcompositionalupdates.CompleteExample"
```

To compile all examples without running them:

```bash
sbt "differ-compositional-updates/compile"
```

---

## What You've Learned

Here is a summary of what you now understand:

- **The core problem** — a plain `FiberRef.make` uses last-write-wins because its patch type is `A => A` (recording only the new value, not the delta). When two fibers join, only one update survives.
- **The Differ contract** — four methods (`diff`, `patch`, `combine`, `empty`) form an algebra that reifies "what changed" as a composable, associative `Patch`. `combine` is the key: it merges two independent patches so both fibers' intents are preserved.
- **Built-in Differs** — `Differ.set`, `Differ.map`, `Differ.chunk`, and `Differ.update` cover the common collection types, each using a sealed patch ADT (`SetPatch`, `MapPatch`, `ChunkPatch`) that records element-level deltas.
- **Composition** — `zip` (`<*>`) composes two `Differ`s for product types field by field; `orElseEither` (`<+>`) handles `Either` values including side switches; `transform` adapts a `Differ` to a structurally isomorphic type.
- **The five laws** — associativity, identity, self-diff-is-empty, round-trip, and empty-patch-identity. A `Differ` that violates any law will silently corrupt `FiberRef` state on join.
- **ZIO internals** — `currentEnvironment`, `currentLoggers`, `currentSupervisor`, and `currentRuntimeFlags` are all backed by `Differ`. Every `ZLayer`, `withRuntimeFlags`, and logger installation in a ZIO application is compositional because of this mechanism.

---

## Where to Go Next

- **FiberRef reference** — the [FiberRef documentation](../reference/state-management/fiberref.md) covers the complete `FiberRef` API including `locally`, `getWith`, and the `join` function used by plain `FiberRef.make`. Understanding `FiberRef.make`'s `join` parameter alongside `FiberRef.makePatch`'s `Differ` gives you the full picture of fiber-local state merging strategies.
- **Fiber reference** — the [Fiber documentation](../reference/fiber/fiber.md) explains the fiber lifecycle and how ZIO's runtime calls `combine` at join time.
- **ZIO test for property-based law testing** — run `sbt "coreTestsJVM/testOnly *DifferSpec"` to see how ZIO's `diffLaws` helper checks each built-in `Differ` against random inputs. The same pattern works for custom `Differ`s in your own test suites.
