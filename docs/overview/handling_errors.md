---
id: overview_handling_errors
title:  "Handling Errors"
---

ZIO effects may fail due to foreseen or unforeseen problems. In order to help you build robust applications, ZIO tracks foreseen errors at compile-time, letting you know which effects can fail, and how they can fail. For non-recoverable problems, ZIO gives you full insight into the cause of failures (even if unexpected or catastrophic), preserving all information and automatically logging unhandled errors.

In this section, you will learn about some of the tools ZIO gives you to build applications with robust error management.

```scala mdoc:invisible
import zio._
```

## Either

With the `ZIO#either` method, you can transform an effect that fails into an infallible effect that places both failure and success into Scala's `Either` type. This brings the error from the error channel to the success channel, which is useful because many ZIO operators work on the success channel, not the error channel.

```scala mdoc:silent
val zeither: ZIO[Any, Nothing, Either[String, Nothing]] = 
  ZIO.fail("Uh oh!").either
```

## Catching All Errors

If you want to catch and recover from all types of recoverable errors and effectfully attempt recovery, then you can use the `catchAll` method, which lets you specify an error handler that returns the effect to execute in the event of an error:

```scala mdoc:invisible
import java.io.{ FileNotFoundException, IOException }

def openFile(s: String): ZIO[Any, IOException, Array[Byte]] = 
  ZIO.attempt(???).refineToOrDie[IOException]
```

```scala mdoc:silent
val z: ZIO[Any, IOException, Array[Byte]] = 
  openFile("primary.json").catchAll { error => 
    for {
      _    <- ZIO.logErrorCause("Could not open primary file", error)
      file <- openFile("backup.json")
    } yield file 
  }
```

In the error handler passed to `catchAll`, you may return an effect with a _different_ error type (perhaps `Nothing`, if the error handler cannot fail), which is then reflected in the type of effect returned by `catchAll`.

## Catching Some Errors

If you want to catch and recover from only some types of recoverable errors and effectfully attempt recovery, then you can use the `catchSome` method:

```scala mdoc:silent
val data: ZIO[Any, IOException, Array[Byte]] = 
  openFile("primary.data").catchSome {
    case _ : FileNotFoundException => 
      openFile("backup.data")
  }
```

Unlike `catchAll`, `catchSome` cannot reduce or eliminate the error type, although it can widen the error type to a broader class of errors.

## Fallback

You can try one effect or if it fails, try another effect with the `orElse` combinator:

```scala mdoc:silent
val primaryOrBackupData: ZIO[Any, IOException, Array[Byte]] = 
  openFile("primary.data").orElse(openFile("backup.data"))
```

## Folding

In the Scala standard library, the data types `Option` and `Either` have a `fold` method, which let you handle both failure and success cases at the same time.

In a similar fashion, `ZIO` effects also have several methods that allow you to handle both failure and success at the same time.

The first fold method, `fold`, lets you separately convert both failure and success into some common type:

```scala mdoc:silent
lazy val DefaultData: Array[Byte] = Array(0, 0)

val primaryOrDefaultData: ZIO[Any, Nothing, Array[Byte]] = 
  openFile("primary.data").fold(
    _    => DefaultData, // Failure case
    data => data)        // Suyccess case
```

The second fold method, `foldZIO`, lets you separately handle both failure and success by specifying effects that will be executed in each respective case:

```scala mdoc:silent
val primaryOrSecondaryData: ZIO[Any, IOException, Array[Byte]] = 
  openFile("primary.data").foldZIO(
    _    => openFile("secondary.data"), // Error handler
    data => ZIO.succeed(data))          // Success handler
```

The `foldZIO` method is almost the most powerful error recovery method in ZIO, with only `foldCauseZIO` being more powerful. Most other operators, such as `either` or `orElse`, are implemented in terms of these powerful methods.

In the following additional example, `foldZIO` is used to handle both the failure and the success of the `readUrls` method:

```scala mdoc:invisible
sealed trait Content

object Content {
  case class NoContent(t: Throwable) extends Content
  case class OkContent(s: String)    extends Content
}

def readUrls(file: String): Task[List[String]]     = ZIO.succeed("Hello" :: Nil)
def fetchContent(urls: List[String]): UIO[Content] = ZIO.succeed(Content.OkContent("Roger"))
```
```scala mdoc:silent
val urls: ZIO[Any, Nothing, Content] =
  readUrls("urls.json").foldZIO(
    error   => ZIO.succeed(Content.NoContent(error)), 
    success => fetchContent(success)
  )
```

## Retrying

In order to deal with transient errors, which are the norm when interacting with external cloud systems, ZIO provides very powerful retry mechanisms.

One of these mechanisms is the `ZIO#retry` method, which takes a `Schedule`, and returns a new effect that will retry the original effect if it fails, according to the specified schedule:

```scala mdoc:silent
val retriedOpenFile: ZIO[Any, IOException, Array[Byte]] = 
  openFile("primary.data")
      .retry(Schedule.recurs(5))
```

The next most powerful function is `ZIO#retryOrElse`, which allows specification of a fallback to use if the effect does not succeed with the specified policy:

```scala
val retryOpenFile: ZIO[Any, IOException, DefaultData) = 
  openFile("primary.data")
      .retryOrElse(Schedule.recurs(5), (_, _) => ZIO.succeed(DefaultData))
```

For more information on how to build schedules, see the documentation on [Schedule](../datatypes/misc/schedule.md).

## Next Steps

If you are comfortable with basic error handling, including applying simple retry logic to effects, the next step is to learn about safe [resource handling](handling_resources.md).
