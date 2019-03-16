---
layout: docs
section: overview
title:  "Handling Errors"
---

# {{page.title}}

This section looks at some of the common ways to detect and respond to effects that fail.

```tut:invisible
import scalaz.zio._
```

# Either

You can surface failures with `ZIO#either`, which takes an `ZIO[R, E, A]` and produces an `ZIO[R, Nothing, Either[E, A]]`.

```tut:silent
val zeither: UIO[Either[String, Int]] = 
  IO.fail("Uh oh!").either
```

You can submerge failures with `ZIO.absolve`, which is the opposite of `either` and turns an `ZIO[R, Nothing, Either[E, A]]` into an `ZIO[R, E, A]`:

```tut:silent
def sqrt(io: UIO[Double]): IO[String, Double] =
  ZIO.absolve(
    io.map(value =>
      if (value < 0.0) Left("Value must be >= 0.0")
      else Right(Math.sqrt(value))
    )
  )
```

# Catching All Errors

If you want to catch and recover from all types of errors and effectfully attempt recovery, you can use the `catchAll` method:

```tut:invisible
import java.io.{ FileNotFoundException, IOException }

def openFile(s: String): IO[IOException, Array[Byte]] =   
  IO.succeedLazy(???)
```

```tut:silent
val z: IO[IOException, Array[Byte]] = 
  openFile("primary.json").catchAll(_ => 
    openFile("backup.json"))
```

# Catching Some Errors

If you want to catch and recover from only some types of exceptions and effectfully attempt recovery, you can use the `catchSome` method:

```tut:silent
val data: IO[IOException, Array[Byte]] = 
  openFile("primary.data").catchSome {
    case _ : FileNotFoundException => 
      openFile("backup.data")
  }
```

# Fallback

You can try one effect, or, if it fails, try another effect, with the `orElse` combinator:

```tut:silent
val z: IO[IOException, Array[Byte]] = 
  openFile("primary.data") orElse openFile("backup.data")
```

# Folding

Just like Scala's `Option` and `Either` data types have `fold`, which let you deal with both failure and success at the same time, `ZIO` effects also have several methods to fold over them.

The first fold method, `fold`, lets you non-effectfully handle both failure and success, by supplying a non-effectful function for each case:

```tut:silent
lazy val DefaultData: Array[Byte] = ???

val z: UIO[Array[Byte]] = 
  openFile("primary.data").fold(
    _    => DefaultData,
    data => data)
```

The second fold method, `foldM`, lets you effectfully handle both failure and success, by supplying an effectful (but still pure) function for each case:

```tut:silent
val z: IO[IOException, Array[Byte]] = 
  openFile("primary.data").foldM(
    _    => openFile("secondary.data"),
    data => ZIO.succeed(data))
```

Nearly all error handling methods are defined in terms of `foldM`, because it is both powerful and fast.

In the following example, `foldM` is used to handle both failure and success of the `readUrls` method:

```tut:invisible
sealed trait Content
case class NoContent(t: Throwable) extends Content
case class OkContent(s: String) extends Content
def readUrls(file: String): Task[List[String]] = IO.succeed("Hello" :: Nil)
def fetchContent(urls: List[String]): UIO[Content] = IO.succeed(OkContent("Roger"))
```
```tut:silent
val z: UIO[Content] =
  readUrls("urls.json").foldM(
    err => IO.succeedLazy(NoContent(err)), 
    fetchContent
  )
```

# Retrying

There are a number of useful methods on the ZIO data type for retrying failed effects. 

The most basic of these is `ZIO#retry`, which takes a `Schedule` and returns a new effect that will retry the first one if it fails, according to the specified policy:

```tut:silent
import scalaz.zio.clock._

val z: ZIO[Clock, IOException, Array[Byte]] = 
  openFile("primary.data").retry(Schedule.recurs(5))
```

The next most powerful function is `ZIO#retryOrElse`, which allows specification of a fallback to use, if the effect does not succeed with the specified policy:

```scala
  openFile("primary.data").retryOrElse(
    Schedule.recurs(5), 
    (_, _) => ZIO.succeed(DefaultData))
```

The final method, `ZIO#retryOrElseEither`, allows returning a different type for the fallback.

For more information on how to build schedules, see the documentation on [Schedule](/datatypes/schedule.html).

# Next Steps

If you are comfortable with basic error handling, then the next step is to learn about [safe resource handling](handling_resources.html).