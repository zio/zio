---
id: index
title: "Introduction To Property Testing"
---

## What is Property-Based Testing?

In property-based testing, instead of testing individual values and making assertions on the results, we rely on testing the properties of the system which is under the test.

To be more acquainted with property-based testing, let's look at how we can test a simple addition function. So assume we have a function `add` that adds two numbers:

```scala mdoc:silent
def add(a: Int, b: Int): Int = ???
```

in a typical test we start with some well-known values as test inputs and check if the function returns the expected values for each of the pair inputs:


|   Input   |  Expected Output  |
|:---------:|:-----------------:|
|  (0, 0)   |         0         |
|  (1, 0)   |         1         |
|  (0, 1)   |         1         |
|  (0, -1)  |        -1         |
|  (-1, 0)  |        -1         |
|    ...    |        ...        |


Now we can test all the inputs and make sure the `add` function returns the expected values:

```scala mdoc:compile-only
import zio.test._

object AdditionSpec extends ZIOSpecDefault {

  def add(a: Int, b: Int): Int = ???

  val testData = Seq(
    ((0, 0), 0),
    ((1, 0), 1),
    ((0, 1), 1),
    ((0, -1), -1),
    ((-1, 0), -1),
    ((1, 1), 2),
    ((1, -1), 0),
    ((-1, 1), 0)
  )

  def spec =
    test("test add function") {
      assertTrue {
        testData.forall { case ((a, b), expected) =>
          add(a, b) == expected
        }
      }
    }
}
```

This is not a very good approach because it is very hard to find a set of inputs that will cover all possible behaviors of the addition function.

Instead, in property-based testing, we extract the set of properties that our function must satisfy. So let's think about the `add` function and find out what properties it must satisfy:

```scala mdoc:invisible
import zio.test.assertTrue
val (a, b, c) = (0, 0, 0)
```

1. **Commutative Property**— It says that changing the order of addends does not change the result. So for all `a` and `b`, `add(a, b)` must be equal to `add(a, b)`:

```scala mdoc:compile-only
assertTrue(add(a, b) == add(b, a))
```

2. **Associative Property**— This says that changing the grouping of addends does not change the result. So for all `a`, `b` and `c`, the `add(add(a, b), c)` must be equal to `add(a, add(b, c))`:

```scala mdoc:compile-only
assertTrue(add(add(a, b), c) == add(a, add(b, c)))
```

3. **Identity Property**— For all `a`, `add(a, 0)` must be equal to `a`:

```scala mdoc:compile-only
assertTrue(add(a, 0) == a)
```

```scala mdoc:invisible:reset

```

If we test all of these properties we can be sure that the `add` function works as expected, so let's see how we can do that using the `Gen` data type:

```scala mdoc:compile-only
import zio.test._
import zio.test._

object AdditionSpec extends ZIOSpecDefault {

  def add(a: Int, b: Int): Int = ???

  def spec = suite("Add Spec")(
    test("add is commutative") {
      check(Gen.int, Gen.int) { (a, b) =>
        assertTrue(add(a, b) == add(b, a))
      }
    },
    test("add is associative") {
      check(Gen.int, Gen.int, Gen.int) { (a, b, c) =>
        assertTrue(add(add(a, b), c) == add(a, add(b, c)))
      }
    },
    test("add is identitive") {
      check(Gen.int) { a =>
        assertTrue(add(a, 0) == a)
      }
    }
  )
}
```
