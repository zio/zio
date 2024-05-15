---
id: smart-assertions
title: "Smart Assertions"
---

The smart assertion is a simple way to assert both _ordinary values_ and _ZIO effects_. It uses the `assertTrue` function, which uses macro under the hood.

## Asserting Ordinary Values

In the following example, we assert simple ordinary values using the `assertTrue` method:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("sum"){
  assertTrue(1 + 1 == 2)
}
```

We can assert multiple assertions inside a single `assertTrue`:

```scala
test("multiple assertions"){
  assertTrue(
    true,
    1 + 1 == 2,
    Some(1 + 1) == Some(2)
  )
}
```

## Asserting ZIO effects

The `assertTrue` method can also be used to assert ZIO effects:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("updating ref") {
  for {
    r <- Ref.make(0)
    _ <- r.update(_ + 1)
    v <- r.get
  } yield assertTrue(v == 1)
}
```

Using `assertTrue` with for-comprehension style, we can think of testing as these three steps:

1. **Set up the test** — In this section we should setup the system under test (e.g. `Ref.make(0)`).
2. **Running the test** — Then we run the test scenario according to the test specification. (e.g `ref.update(_ + 1)`)
3. **Making assertions about the test** - Finally, we should assert the result with the right expectations (e.g. `assertTrue(v == 1)`)

## Assertion Operators

Each `assertTrue` returns a `AssertResult`, so they have the same operators as `AssertResult`. Here are some of the useful operators:

1. **`&&`** - This is the logical and operator to make sure that both assertions are true:

```scala mdoc:compile-only
import zio.test._

test("&&") {
  check(Gen.int <*> Gen.int) { case (x: Int, y: Int) =>
    assertTrue(x + y == y + x) && assertTrue(x * y == y * x)
  }
}
```

2. **||** - This is the logical or operator to make sure that at least one of the assertions is true:

```scala mdoc:compile-only
import zio.test._

suite("||")(
  test("false || true") {
    assertTrue(false) || assertTrue(true) // this will pass
  },
  test("true || false") {
    assertTrue(true) || assertTrue(false) // this will pass
  },
  test("true || true") {
    assertTrue(true) || assertTrue(true) // this will pass
  },
  test("false || false") {
    assertTrue(false) || assertTrue(false) // this will false
  },
)
```

3. **`!`** - This is the logical not operator to negate the assertion:

```scala mdoc:compile-only
suite("unary !") (
    test("negate true") {
        assertTrue(!assertTrue(true)) // this will fail
    },
    test("negate false") {
        assertTrue(!assertTrue(false)) // this will pass
    }
)
```

4. **implies** - This is the logical implies operator to make sure that the first assertion implies the second assertion. It is equivalent to `!p || q` which is a conditional statement of the form "if p, then q" where p and q are propositions. The `==>` operator is an alias for `implies`.

```scala mdoc:compile-only
suite("implies") (
  test("true implies true")(
    assertTrue(true) implies assertTrue(true) // this will pass
  ),
  test("true implies false")(
    assertTrue(true) implies assertTrue(false) // this will fail
  ),
  test("false implies true")(
    assertTrue(false) implies assertTrue(true) // this will pass
  ),
  test("false implies false")(
    assertTrue(false) implies assertTrue(false) // this will pass
  ),
)
```

The `implies` assertion is true if either the p is false or when both p and q are true:

| P     | Q     | P implies Q |
|-------|-------|-------------|
| true  | true  | true        |
| true  | false | false       |
| false | true  | true        |
| false | false | true        |

5. **iff** - This is the logical iff operator to make sure that the first assertion is true if and only if the second assertion is true. It is equivalent to `(p implies q) && (q implies p)`. The `<==>` operator is an alias for `iff`.

```scala
suite("iff") (
  test("true iff true")(
    assertTrue(true) iff assertTrue(true) // this will pass
  ),
  test("true iff false")(
    assertTrue(true) iff assertTrue(false) // this will fail
  ),
  test("false iff true")(
    assertTrue(false) iff assertTrue(true) // this will fail
  ),
  test("false iff false")(
    assertTrue(false) iff assertTrue(false) // this will pass
  ),
)
```

Here is the truth table for the iff operator:

| P     | Q     | P iff Q |
|-------|-------|---------|
| true  | true  | true    |
| true  | false | false   |
| false | true  | false   |
| false | false | true    |

6. **??**- We can add a custom message to the assertion using the `??` operator. This will be useful when assertion fails, and we want to provide more information about the failure:

```scala mdoc:compile-only
assertTrue(1 + 1 == 3) ?? "1 + 1 should be equal to 2"
```
