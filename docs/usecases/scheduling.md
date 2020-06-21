---
id: usecases_scheduling
title:  "Scheduling"
---
For scenarios where an action needs to be performed multiple times, `Schedule` can be used to customize the:
* number of repetitions
* rate of repetition
* effect performed on each repetition

## Retry strategy for HTTP requests
One potential issue when dealing with a 3rd party API is the unreliability of a given endpoint. Since you have no control over the software, you cannot directly improve the reliability. Here's a mock request that has approximately a 70% chance of succeeding:
```
object API {
  def makeRequest = Task.effect {
    if (Random.nextInt(10) > 7) "some value" else throw new Exception("hi")
  }
}
```
One solution to improve reliability is to retry the request until success. There are many considerations:
* What should the maximum number of attempts be?
* How often should you make the request?
* Do you want to log attempts?

`Schedule` can be used to address all of these concerns.

If you don't want to retry the request forever, create a schedule that specifies max number of attempts.
```
Schedule.recurs(4)
```
The above schedule retries immediately after failing.
Typically, you will want to space out your requests a bit to give the endpoint a chance to stabilize.
There are many rates which you can use such as `spaced`, `exponential`, `fibonacci`, `forever`. For simplicity, we will retry the request every second.
```
import zio.duration._
Schedule.spaced(1.second)
```
You can compose the schedules using operators to create a more compelx schedule:
```
def schedule = Schedule.recurs(4) && Schedule.spaced(1.second)
```

For monitoring purposes, you may also want to log attempts. While this logic can be placed in the request itself, it's more scalable to add that logic to the schedule so it can be reused.
```
object ScheduleUtil {
  def schedule[A] = Schedule.recurs(4) && Schedule.spaced(1.second).onDecision((a: A, s) => s match {
    case None => putStrLn(s"done trying")
    case Some(att) => putStrLn(s"attempt #$att")
  })
}
```
You've now created a retry strategy that will attempt an effect every second for a maximum of 5 attempts while logging each attempt. The usage of the schedule would look like this:
```
package example

import zio._
import zio.duration._
import zio.console._
import zio.clock._
import ScheduleUtil._
import API._

object ScheduleApp extends scala.App {

  implicit val rt: Runtime[Clock with Console] = Runtime.default

  rt.unsafeRun(makeRequest.retry(schedule).foldM(
    ex => putStrLn("Exception Failed"),
    v => putStrLn(s"Succeeded with $v"))
  )
}
```

The output of the above program where the request succeeds in time could be:
```
attempt #1
attempt #2
attempt #3
Succeeded with some value
```
If the server is completely down with no chance of the request succeeding, the output would look like:
```
attempt #1
attempt #2
attempt #3
attempt #4
Exception Failed
```
