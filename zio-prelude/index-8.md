# Scala Parallel Collections

> ZIO Prelude type class instances and syntax for the immutable ParSeq, ParMap and ParSet parallel collections.

:::caution
The published `zio-prelude-scala-parallel-collections` artifact contains no instances from `1.0.0-RC37` to `1.0.0-RC48`: the jars hold only a `BuildInfo` class, because the build does not compile this module's sources. The examples on this page describe the code in the repository, not those published releases.
:::

The Scala Parallel Collections module provides ZIO Prelude type class instances for the immutable parallel collections `ParSeq`, `ParMap` and `ParSet` from `scala.collection.parallel.immutable`, together with syntax for comparing maps. The core module has instances for the standard sequential collections only, and the parallel collections have been a separate library since Scala 2.13, so their instances live in a separate module that depends on it.

The module is one package object, `zio.prelude.scalaparallelcollections`. Importing its members brings every instance and the syntax into implicit scope:

```scala
package object scalaparallelcollections
    extends EqualInstances with HashInstances with OrdInstances with PartialOrdInstances
    with InvariantInstances with AssociativeFlattenInstances with SPCDerive with Syntax
```

## Installation

We add the module next to the core library. It is available for the JVM and Scala Native only, because the parallel collections do not exist on Scala.js:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-prelude"                          % "1.0.0-RC49",
  "dev.zio" %% "zio-prelude-scala-parallel-collections" % "1.0.0-RC49"
)
```

On Scala 2.13 and Scala 3 the module brings in `org.scala-lang.modules::scala-parallel-collections` 1.2.0 as a dependency, and we need `import scala.collection.parallel.CollectionConverters._` to call `.par` on a sequential collection. On Scala 2.12 the build adds no extra dependency, because the 2.12 standard library already contains the parallel collections, and `.par` needs no import.

## Using the Instances

To use the instances, we import the package object next to `zio.prelude._`. Because `zio.prelude` has its own `ParSeq` type, we import the parallel collection types explicitly:

```scala
import zio.prelude._
import zio.prelude.scalaparallelcollections._
import scala.collection.parallel.immutable.{ParMap, ParSeq, ParSet}
```

The instances are:

| Collection   | Instances                                                                                         |
| ------------ | ------------------------------------------------------------------------------------------------- |
| `ParSeq[A]`  | `Equal`, `Hash`, `PartialOrd` and `Ord` (given the same instance for `A`), `ForEach`, `IdentityFlatten` |
| `ParMap[K, V]` | `PartialOrd` and `Equal` (given `Equal[V]`), `Hash` (given `Hash[V]`), `ForEach` over `V`, `AssociativeFlatten` |
| `ParSet[A]`  | `Hash` and `PartialOrd` (and so `Equal`), `Invariant`                                             |

The module also provides the matching `Derive` instances, so the collections work with the `DeriveEqual`-based laws and syntax. It does not provide a `Debug` instance for any of them.

## Comparing

`ParSeq` is compared element by element, with a shorter sequence that is a prefix of a longer one ordering first. To compare two sequences with `Ord`:

```scala
val a: ParSeq[Int] = ParSeq(1, 2, 3)
val c: ParSeq[Int] = ParSeq(1, 2, 4)

a =?= c
// Ordering.LessThan
```

`ParSet` is only partially ordered, by the subset relation, and it uses object equality and hash codes on its elements, because the parallel collections do not expose their own element equality. Two sets where neither contains the other are `Incomparable`.

`ParMap` uses object equality on its keys, for the same reason. Beyond `PartialOrd`, the module adds three comparison methods on `ParMap` in the `Syntax` trait. `compareStrict` requires the values of the common keys to be equal, `compareSoft` lets the smaller map have smaller values for the common keys, and `compareWith` takes a function that decides how the values compare:

```scala
val m1: ParMap[String, Int] = ParMap("a" -> 1)
val m2: ParMap[String, Int] = ParMap("a" -> 1, "b" -> 2)
val m3: ParMap[String, Int] = ParMap("a" -> 2)

m1.compareStrict(m2)
// PartialOrdering.LessThan

m1.compareStrict(m3)
// PartialOrdering.Incomparable

m1.compareSoft(m3)
// PartialOrdering.LessThan
```

`compareStrict` is what `PartialOrd[ParMap[K, V]]` uses, so `m1 =??= m2` gives the same answer as `m1.compareStrict(m2)`.

## Traversing and Flattening

`ParSeq` and `ParMap` have `ForEach` instances, so we can traverse them with an effect type that has `IdentityBoth` and `Covariant`. To double every element and fail if any step fails:

```scala
ForEach[ParSeq].forEach(a)((i: Int) => Option(i * 2))
// Some(ParVector(2, 4, 6))
```

`ParSeq` also has an `IdentityFlatten` instance, whose identity is a sequence with a single `()`, and `ParMap` has `AssociativeFlatten`, which merges the inner maps. The `flatten` syntax works for `ParSeq`:

```scala
val nested: ParSeq[ParSeq[Int]] = ParSeq(ParSeq(1, 2), ParSeq(3))

nested.flatten
// ParVector(1, 2, 3)
```

## Equal Resolution

A `ParSeq[A]` has an `Equal` instance through each of `ParSeqEqual`, `ParSeqHash`, `ParSeqOrd` and `ParSeqPartialOrd`. When an element type has both `Hash` and `Ord` instances, asking for `Equal[ParSeq[A]]` implicitly, for example with `===`, is ambiguous between the `Hash` and `Ord` instances. In that case we call the instance directly:

```scala
ParSeqEqual[Int].equal(a, ParSeq(1, 2, 3))
```

Asking for `Hash[ParSeq[A]]` or `Ord[ParSeq[A]]` is not ambiguous, and both can be used directly.
