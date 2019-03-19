---
layout: docs
section: usecases
title:  "Concurrency"
---

# {{page.title}}

```scala
trait IO[E, A] {
  def race(that: IO[E, A]): IO[E, A]
}

object IO {
  def raceAll(as: Iterable[IO[E, A]]): IO[E, A]
}
```

`race` and `raceAll` let you do that
raceAll take many actions and get the winner
and if you want to race 2 action s you can use race

the all other losers will be interrupted and there is no leak of resources!

```scala
val availableService: IO[Error, Service] = IO.raceAll(services.map(_.await))

val fastTask: IO[Error, Task] = task1 race task2
```
