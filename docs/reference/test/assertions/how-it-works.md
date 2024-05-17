---
id: how-it-works
title: "How it Works?"
---

:::note
In this section we are going to learn about the internals of the `Assertion` data type. So feel free to skip this section if you are not interested.
:::

## The `test` Function

In order to understand the `Assertion` data type, let's first look at the `test` function:

```scala
def test[In](label: String)(assertion: => In)(implicit testConstructor: TestConstructor[Nothing, In]): testConstructor.Out
```

Its signature is a bit complicated and uses _path dependent types_, but it doesn't matter. We can think of a `test` as a function from `TestResult` (or its effectful versions such as `ZIO[R, E, TestResult]` or `ZSTM[R, E, TestResult]`) to the `Spec[R, E]` data type:

```scala
def test(label: String)(assertion: => TestResult): Spec[Any, Nothing]
def test(label: String)(assertion: => ZIO[R, E, TestResult]): Spec[R, E]
```

Therefore, the function `test` needs a `TestResult`. The most common way to produce a `TestResult` is to resort to `assert` or its effectful counterpart `assertZIO`. The former one is for creating ordinary `TestResult` values and the latter one is for producing effectful `TestResult` values. Both of them accept a value of type `A` (effectful version wrapped in a `ZIO`) and an `Assertion[A]`.

## The `assert` Function

Let's look at the `assert` function:

```scala
def assert[A](expr: => A)(assertion: Assertion[A]): TestResult
``` 

It takes an expression of type `A` and an `Assertion[A]` and returns the `TestResult` which is the boolean algebra of the `AssertionResult`. Furthermore, we have an `Assertion[A]` which is capable of producing _assertion results_ on any value of type `A`. So the `assert` function can apply the expression to the assertion and produce the `TestResult`.
