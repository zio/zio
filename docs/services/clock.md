---
id: clock 
title: "Clock"
---

Clock service contains some functionality related to time and scheduling. 

To get the current time in a specific time unit, the `currentTime` function takes a unit as `TimeUnit` and returns `UIO[Long]`:

```scala mdoc:invisible
import zio.duration._
import zio._
import java.util.concurrent.TimeUnit
import java.time.DateTimeException
```

```scala mdoc:silent
val inMiliseconds: URIO[Has[Clock], Long] = Clock.currentTime(TimeUnit.MILLISECONDS)
val inDays: URIO[Has[Clock], Long] = Clock.currentTime(TimeUnit.DAYS)
```

To get current data time in the current timezone the `currentDateTime` function returns a ZIO effect containing `OffsetDateTime`.

Also, the Clock service has a very useful functionality for sleeping and creating a delay between jobs. The `sleep` takes a `Duration` and sleep for the specified duration. It is analogous to `java.lang.Thread.sleep` function, but it doesn't block any underlying thread. It's completely non-blocking.

In following example we are going to print the current time periodically by placing a one second`sleep` between each print call:

```scala mdoc:silent
def printTimeForever: ZIO[Has[Console] with Has[Clock], DateTimeException, Nothing] =
  Clock.currentDateTime.flatMap(time => Console.printLine(time.toString)) *>
    ZIO.sleep(1.seconds) *> printTimeForever
```

For scheduling purposes like retry and repeats, ZIO has a great data type called [Schedule](../datatypes/misc/schedule.md). 
