# Built-in Schedules

> Returns a schedule that repeats one time, producing the specified constant value:

## succeed

Returns a schedule that repeats one time, producing the specified constant value:

```scala
val constant = Schedule.succeed(5)
```

## fromFunction

A schedule that always recurs, mapping input values through the specified function:

```scala
val inc = Schedule.fromFunction[Int, Int](_ + 1)
```

## stop

A schedule that does not recur, just stops and returns one `Unit` element:

```scala
val stop = Schedule.stop
```

## once

A schedule that recurs one time an returns one `Unit` element:

```scala
val once = Schedule.once
```

## forever

A schedule that always recurs and produces number of recurrence at each run:

```scala
val forever = Schedule.forever
```

## recurs

A schedule that only recurs the specified number of times:

```scala
val recurs = Schedule.recurs(5)
```

## spaced

A schedule that recurs continuously, each repetition spaced the specified duration from the last run:

```scala
val spaced = Schedule.spaced(10.milliseconds)
```

## fixed

A schedule that recurs on a fixed interval. Returns the number of repetitions of the schedule so far:

```scala
val fixed = Schedule.fixed(10.seconds)
```

## exponential

A schedule that recurs using exponential backoff:

```scala
val exponential = Schedule.exponential(10.milliseconds)
```

## fibonacci

A schedule that always recurs, increasing delays by summing the preceding two delays (similar to the fibonacci sequence). Returns the current duration between recurrences:

```scala
val fibonacci = Schedule.fibonacci(10.milliseconds)
```

## identity

A schedule that always decides to continue. It recurs forever, without any delay. `identity` schedule consumes input, and emit the same as output (`Schedule[Any, A, A]`):

```scala
val identity = Schedule.identity[Int]
```

## unfold

A schedule that repeats one time from the specified state and iterator:

```scala
val unfold = Schedule.unfold(0)(_ + 1)
```
