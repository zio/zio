---
id: classic-assertions
title: "Classic Assertions"
---

:::note
In almost all cases we encourage developers using _[smart assertions](smart-assertions.md)_ instead of [classic assertions](classic-assertions.md). They are more expressive and easier to use. So you can skip reading this section.

Only use _classic assertions_ when you know what you are doing. There are some rare cases where the smart assertions are not enough.
:::

The `assert` and its effectful counterpart `assertZIO` are the old way of asserting ordinary values and ZIO effects.

## Asserting Ordinary Values

In order to test ordinary values, we should use `assert`, like the example below:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("sum") {
  assert(1 + 1)(Assertion.equalTo(2))
}
```

## Asserting ZIO Effects

If we are testing an effect, we should use the `assertZIO` function:

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("updating ref") {
  val value = for {
    r <- Ref.make(0)
    _ <- r.update(_ + 1)
    v <- r.get
  } yield v
  assertZIO(value)(Assertion.equalTo(1))
}
```

## The for-comprehension Style

Having this all in mind, probably the most common and also most readable way of structuring tests is to pass a for-comprehension to `test` function and yield a call to `assert` function.

```scala mdoc:compile-only
import zio._
import zio.test.{test, _}

test("updating ref") {
  for {
    r <- Ref.make(0)
    _ <- r.update(_ + 1)
    v <- r.get
  } yield assert(v)(Assertion.equalTo(v))
} 
```

