---
id: non-deterministic-test-data
title: "Non-deterministic Test Data"
---

The random process of the `TestRandom` is said to be deterministic since, with the initial seed, we can generate a sequence of predictable numbers. So with the same initial seed, it will generate the same sequence of numbers.

By default, the initial seed of the `TestRandom` is fixed. So repeating a generator more and more results in the same sequence:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}
import zio.test.TestAspect._

test("pseudo-random number generator with fixed initial seed") {
  check(Gen.int(0, 100)) { n =>
    ZIO.attempt(n).debug.map(_ => assertTrue(true))
  }
} @@
  samples(5) @@
  after(Console.printLine("----").orDie) @@
  repeat(Schedule.recurs(1))
```

Regardless of how many times we repeat this test, the output would be the same:

```
99
51
81
48
51
----
99
51
81
48
51
----
+ pseudo-random numbers with fixed initial seed - repeated: 2
Ran 1 test in 522 ms: 1 succeeded, 0 ignored, 0 failed
```

The `nondeterministic` test aspect, will change the seed of the pseudo-random generator before each test repetition:

```scala mdoc:compile-only
import zio._
import zio.test.{ test, _ }
import zio.test.TestAspect._

test("pseudo-random number generator with random initial seed on each repetition") {
  check(Gen.int(0, 100)) { n =>
    ZIO.attempt(n).debug.map(_ => assertTrue(true))
  }
} @@
  nondeterministic @@
  samples(5) @@
  after(Console.printLine("----").orDie) @@
  repeat(Schedule.recurs(1))
```

Here is a sample output, which we have different sequences of numbers on each run:

```
73
9
17
33
10
----
42
85
38
2
73
----
+ pseudo-random number generator with random initial seed on each repetition - repeated: 2
Ran 1 test in 733 ms: 1 succeeded, 0 ignored, 0 failed
```
