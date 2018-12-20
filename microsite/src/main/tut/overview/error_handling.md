---
layout: docs
section: overview
title:  "Error Handling"
---

# {{page.title}}

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

```tut:silent
val z: IO[IOException, Array[Byte]] = openFile("primary.json").catchSome {
  case x: java.io.FileNotFoundException => openFile("backup.json")
}
```

You can execute one action, or, if it fails, execute another action, with the `orElse` combinator:

```tut:silent
val z: IO[IOException, Array[Byte]] = openFile("primary.json").orElse(openFile("backup.json"))
```

If you want more control on the next action and better performance you can use the primitive which all the previous operations are based on, it's called `redeem` and it can be seen as the combination of `flatMap` and `catchAll`. It is useful if you find yourself using combinations of `attempt` or `catchAll` with `flatMap`, using `redeem` you can achieve the same and avoid the intermediate `Either` allocation and the subsequent call to `flatMap`.

```tut:invisible
sealed abstract class Content
case class NoContent(t: Throwable) extends Content
case class OkContent(s: String) extends Content
def readUrls(file: String): IO[Throwable, List[String]] = IO.now("Hello" :: Nil)
def fetchContent(urls: List[String]): IO[Nothing, Content] = IO.now(OkContent("Roger"))
```
```tut:silent
val z: IO[Nothing, Content] =
  readUrls("urls.json").redeem(e => IO.point(NoContent(e)), fetchContent)
```

# Retry

There are a number of useful combinators for retrying failed actions:

 * `IO.forever` &mdash; Repeats the action until the first failure.
 * `IO.retry(policy)` &mdash; Repeats the action using a specified schedule until the schedule completes, or a failure occurs.
 * `IO.retryOrElse(policy, fallback)` &mdash; Repeats the action using a specified schedule until the schedule completes, or using `fallback` to compute the final value (or error) in case of failure.

Schedules are a powerful, composable way to dictate retry behavior.
