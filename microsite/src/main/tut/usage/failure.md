---
layout: docs
section: usage
title:  "Failure"
---

# Failure

You can create `IO` actions that describe failure with `IO.fail`:

```tut:silent
import scalaz.zio._

val z: IO[String, Unit] = IO.fail("Oh noes!")
```

Like all `IO` values, these are immutable values and do not actually throw any exceptions; they merely describe failure as a first-class value.

You can surface failures with `attempt`, which takes an `IO[E, A]` and produces an `IO[E2, Either[E, A]]`. The choice of `E2` is unconstrained, because the resulting computation cannot fail with any error.

You can use `Nothing` to describe computations that cannot fail:

```tut:invisible
import java.io.IOException

def readData(file: String): IO[IOException, String] = IO.point(???)
```

```tut:silent
readData("data.json").attempt.map {
  case Left(_)     => "42"
  case Right(data) => data
}
```

You can submerge failures with `IO.absolve`, which is the opposite of `attempt` and turns an `IO[E, Either[E, A]]` into an `IO[E, A]`:

```tut:silent
def sqrt(io: IO[Nothing, Double]): IO[String, Double] =
  IO.absolve(
    io.map(value =>
      if (value < 0.0) Left("Value must be >= 0.0")
      else Right(Math.sqrt(value))
    )
  )
```

If you want to catch and recover from all types of errors and effectfully attempt recovery, you can use the `catchAll` method:

```tut:invisible
def openFile(s: String): IO[IOException, Array[Byte]] = IO.point(???)
```

```tut:silent
val z: IO[IOException, Array[Byte]] = openFile("primary.json").catchAll(_ => openFile("backup.json"))
```

If you want to catch and recover from only some types of exceptions and effectfully attempt recovery, you can use the `catchSome` method:

<!-- https://github.com/scalaz/scalaz-zio/issues/164 -->
```scala
val z: IO[IOException, Array[Byte]] = openFile("primary.json").catchSome {
  case FileNotFoundException(_) => openFile("backup.json")
}
```

You can execute one action, or, if it fails, execute another action, with the `orElse` combinator:

```tut:silent
val z: IO[IOException, Array[Byte]] = openFile("primary.json").orElse(openFile("backup.json"))
```

If you want more control on the next action and better performance you can use the primitive which all the previous operations are based on, it's called `redeem` and it can be seen as the combination of `flatMap` and `catchAll`. It is useful if you find yourself using combinations of `attempt` or `catchAll` with `flatMap`, using `redeem` you can achieve the same and avoid the intermediate `Either` allocation and the subsequent call to `flatMap`.

<!-- Inventing APIs here -->
```scala
val z: IO[Nothing, Content] =
  readUrls("urls.json").redeem[Nothing](e => IO.point(NoContent(cause = e)))(fetchContent)
```

# Retry

There are a number of useful combinators for retrying failed actions:

 * `IO.forever` &mdash; Repeats the action until the first failure.
 * `IO.retry(policy)` &mdash; Repeats the action using a specified retry policy.
 * `IO.retryOrElse(policy, fallback)` &mdash; Repeats the action using a specified retry policy, or if the policy fails, uses the fallback.

Retry policies are a powerful, composable way to dictate retry behavior.

## Retry Policies

## Base Policies

```tut:invisible
import scala.concurrent.duration._
```

A policy that always retries immediately:

```tut:silent
val always = Retry.always
```

A policy that never retries:

```tut:silent
val never = Retry.never
```

A policy that retries up to 10 times:

```tut:silent
val upTo10 = Retry.retries(10)
```

A policy that retries every 10 milliseconds:

```tut:silent
val fixed = Retry.fixed(10.milliseconds)
```

A policy that retries using exponential backoff:

```tut:silent
val exponential = Retry.exponential(10.milliseconds)
```

A policy that retries using fibonacci backoff:

```tut:silent
val fibonacci = Retry.fibonacci(10.milliseconds)
```

## Policy Combinators

Applying random jitter to a policy:

```tut:silent
val jitteredExp = Retry.exponential(10.milliseconds).jittered
```

Modifying the delay of a policy:

```tut:silent
val boostedFixed = Retry.fixed(1.second).delayed(_ + 100.milliseconds)
```

Combining two policies sequentially, by following the first policy until it gives up, and then following the second policy:

```tut:silent
val sequential = Retry.retries(10) <||> Retries.fixed(1.second)
```

Combining two policies through intersection, by retrying only if both policies want to retry, using the maximum of the two delays between retries:

```tut:silent
val expUpTo10 = Retry.exponential(1.second) && Retry.retries(10)
```

Combining two policies through union, by retrying if either policy wants to
retry, using the minimum of the two delays between retries:

```tut:silent
val expCapped = Retry.exponential(100.milliseconds) || Retry.fixed(1.seconds)
```
