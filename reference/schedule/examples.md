# Examples

> Practical examples of creating and combining ZIO schedules for retrying with exponential backoff and exception handling.

Let's try some example of creating and combining schedules.

1. Stop retrying after a specified amount of time has elapsed:

```scala
val expMaxElapsed = (Schedule.exponential(10.milliseconds) >>> Schedule.elapsed).whileOutput(_ < 30.seconds)
```

2. Retry only when a specific exception occurs:

```scala
import scala.concurrent.TimeoutException

val whileTimeout = Schedule.exponential(10.milliseconds) && Schedule.recurWhile[Throwable] {
  case _: TimeoutException => true
  case _ => false
}
```
