---
layout: docs
section: usage
title:  "Failure"
---

# Failure

You can create `IO` actions that describe failure with `IO.fail`:

```tut:silent
import scalaz.zio._

val _: IO[String, Unit] = IO.fail("Oh noes!")
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
val _: IO[IOException, Array[Byte]] = openFile("primary.json").catchAll(_ => openFile("backup.json"))
```

If you want to catch and recover from only some types of exceptions and effectfully attempt recovery, you can use the `catchSome` method:

<!-- https://github.com/scalaz/scalaz-zio/issues/164 -->
```scala
val _: IO[IOException, Array[Byte]] = openFile("primary.json").catchSome {
  case FileNotFoundException(_) => openFile("backup.json")
}
```

You can execute one action, or, if it fails, execute another action, with the `orElse` combinator:

```tut:silent
val _: IO[IOException, Array[Byte]] = openFile("primary.json").orElse(openFile("backup.json"))
```

If you want more control on the next action and better performance you can use the primitive which all the previous operations are based on, it's called `redeem` and it can be seen as the combination of `flatMap` and `catchAll`. It is useful if you find yourself using combinations of `attempt` or `catchAll` with `flatMap`, using `redeem` you can achieve the same and avoid the intermediate `Either` allocation and the subsequent call to `flatMap`.

<!-- Inventing APIs here -->
```scala
val _: IO[Nothing, Content] =
  readUrls("urls.json").redeem[Nothing](e => IO.point(NoContent(cause = e)))(fetchContent)
```

# Retry

There are a number of useful combinators for repeating actions until failure or success:

 * `IO.forever` &mdash; Repeats the action until the first failure.
 * `IO.retry` &mdash; Repeats the action until the first success.
 * `IO.retryN(n)` &mdash; Repeats the action until the first success, for up to the specified number of times (`n`).
 * `IO.retryFor(d)` &mdash; Repeats the action until the first success, for up to the specified amount of time (`d`).
