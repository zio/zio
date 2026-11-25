# How to Test Instances with Laws

> Check your Identity, Ord and Hash instances against their laws with property-based tests using ZIO Test.

In this guide, we test hand-written instances against the laws that the type classes require, using the laws module and ZIO Test. By the end, you will have a test suite that checks an `Identity` instance for `Stats`, and an `Ord` and a `Hash` instance for `Version`, and you will know how a broken instance shows up in the test output.

We write a generator for each type, run the laws, and then break an instance on purpose to read a failure.

## The Problem

The compiler checks that an instance has the right type, not that it behaves correctly. A combining function that is not associative, or a `Hash` that disagrees with `Equal`, compiles without a warning and returns wrong results in the folds, sorts and lookups that use it.

Hand-written tests catch only the cases we think of. For associativity, we would write a few triples of values and check that the grouping does not matter:

```scala
final case class Stats(count: Int, total: Long, largest: Int)

def combine(left: Stats, right: Stats): Stats =
  Stats(left.count + right.count, left.total + right.total, left.largest max right.largest)

val a = Stats(1, 10L, 10)
val b = Stats(2, 20L, 12)
val c = Stats(3, 30L, 15)

val associative = combine(combine(a, b), c) == combine(a, combine(b, c))
```

This passes for these three values, and says nothing about the rest of the input space. We also have to remember every law: the identity laws, ordering laws, and the consistency of hashing and equality.

In this guide, we let the laws module state each law once and check it against many generated values.

## Prerequisites

The laws module has its own artifact, `zio-laws-laws`, and it builds on ZIO Test. We add it to the test scope, and register the ZIO Test framework with sbt:

```scala
libraryDependencies += "dev.zio" %% "zio-laws-laws" % "1.0.0-RC49" % Test
libraryDependencies += "dev.zio" %% "zio-test-sbt"  % "ZIO_VERSION" % Test

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
```

Replace `ZIO_VERSION` with the version of ZIO you use. The code blocks of this page compile in the documentation build, because that build has the laws module and ZIO Test on its classpath. The test runner itself is not run here.

We import the package, the laws and ZIO Test, and define the types under test. `Stats` has an `Identity` instance, and `Version` has a combined `Hash` and `Ord` instance. A type that is checked by the laws also needs an `Equal` instance, because the laws compare results with it, so we add one with `Equal.default` for `Stats`:

```scala
import zio.prelude._
import zio.prelude.coherent.HashOrd
import zio.prelude.laws._
import zio.test._
import zio.test.laws._

final case class Stats(count: Int, total: Long, largest: Int)

object Stats {
  implicit val equal: Equal[Stats] = Equal.default

  implicit val statsIdentity: Identity[Stats] =
    Identity.make(
      Stats(0, 0L, Int.MinValue),
      (left, right) =>
        Stats(left.count + right.count, left.total + right.total, left.largest max right.largest)
    )
}

final case class Version(major: Int, minor: Int, label: String)

object Version {
  private def key(version: Version): (Int, Int) =
    (version.major, version.minor)

  implicit val hashOrd: Hash[Version] with Ord[Version] =
    HashOrd.make(
      version => key(version).hash,
      (left, right) => key(left) =?= key(right)
    )
}
```

## Writing Generators

A law runs against generated values, so each type needs a `Gen`. We build one with the combinators of ZIO Test. For `Version` we use small ranges, so generated pairs often have equal keys, which is where equality and hashing bugs show up:

```scala
val genStats: Gen[Any, Stats] =
  for {
    count   <- Gen.int(0, 1000)
    total   <- Gen.long(0L, 100000L)
    largest <- Gen.int
  } yield Stats(count, total, largest)

val genVersion: Gen[Any, Version] =
  for {
    major <- Gen.int(0, 5)
    minor <- Gen.int(0, 5)
    label <- Gen.elements("rc", "final")
  } yield Version(major, minor, label)
```

## Running the Laws

`checkAllLaws` takes a set of laws and a generator, and runs every law of the set against values from the generator. `IdentityLaws` contains the left identity, the right identity and associativity. `OrdLaws` contains the ordering laws, and `HashLaws` states that equal values hash equally and includes the laws of `Equal`:

```scala
object InstancesSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] =
    suite("instances")(
      test("Stats obeys the Identity laws")(checkAllLaws(IdentityLaws)(genStats)),
      test("Version obeys the Ord laws")(checkAllLaws(OrdLaws)(genVersion)),
      test("Version obeys the Hash laws")(checkAllLaws(HashLaws)(genVersion))
    )
}
```

The `<->` operator from `zio.prelude.laws._` compares two values with their `Equal` instance as a test assertion. We use it to check a rule that is specific to our domain, that two versions with the same numbers are equal whatever their labels:

```scala
object VersionRulesSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] =
    test("the label does not affect equality") {
      Version(1, 2, "rc") <-> Version(1, 2, "final")
    }
}
```

## Reading a Failure

A broken instance fails with the name of the law and the smallest input that breaks it. Subtracting is not associative, so an `Associative` that subtracts fails the associativity law:

```scala
final case class Broken(n: Int)

object Broken {
  implicit val equal: Equal[Broken] = Equal.default

  implicit val subtract: Associative[Broken] =
    Associative.make((left, right) => Broken(left.n - right.n))
}

object BrokenSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] =
    test("Broken obeys the Associative laws")(
      checkAllLaws(AssociativeLaws)(Gen.int.map(Broken(_)))
    )
}
```

The failing run reports `associativityLaw`, and the shrunk input `(Broken(n = 0), Broken(n = 0), Broken(n = -1))`: `0 - (0 - -1)` is `-1`, while `(0 - 0) - -1` is `1`. The report also shows the original input before shrinking, so we can reproduce the problem.

:::tip
Keep one `checkAllLaws` call per instance in the same test file as the instance's tests. A new instance without a law test is the first place a bug appears.
:::

## Going Further

- Laws exist for most abstractions, such as `CommutativeLaws`, `InverseLaws`, `CovariantLaws` and `ForEachLaws`. For the parameterized ones, `zio.test.laws` provides generators for type constructors such as `GenF.list`, as the ZIO Prelude tests do.
- See [How to Define Equal, Ord, Hash and Debug Instances](./define-equal-ord-hash-debug-instances.md) and [How to Combine Values with Associative and Identity](./combine-values-with-associative-and-identity.md) for the instances tested here.
