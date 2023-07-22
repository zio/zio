---
id: clock
title: "Clock"
---

Clock service contains some functionality related to time and scheduling. 

To get the current time in a specific time unit, the `currentTime` function takes a unit as `TimeUnit` and returns `UIO[Long]`:


```scala
val inMiliseconds: URIO[Clock, Long] = currentTime(TimeUnit.MILLISECONDS)
val inDays: URIO[Clock, Long] = currentTime(TimeUnit.DAYS)
```

To get current data time in the current timezone the `currentDateTime` function returns a ZIO effect containing `OffsetDateTime`.

Also, the Clock service has a very useful functionality for sleeping and creating a delay between jobs. The `sleep` takes a `Duration` and sleep for the specified duration. It is analogous to `java.lang.Thread.sleep` function, but it doesn't block any underlying thread. It's completely non-blocking.

In following example we are going to print the current time periodically by placing a one second`sleep` between each print call:

```scala
def printTimeForever: ZIO[Console with Clock, Throwable, Nothing] =
  currentDateTime.flatMap(time => putStrLn(time.toString)) *>
    sleep(1.seconds) *> printTimeForever
```

For scheduling purposes like retry and repeats, ZIO has a great data type called [Schedule](../misc/schedule.md). 
