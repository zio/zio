---
layout: docs
section: usage
title:  "Refs"
---

# Ref

A `Ref[A]` is a mutable location that contains a value of type `A`. The two basic operations are [`set`](#) which fills the `Ref` with a new value, and [`get`](#) which retrieves its current content. All operations on a `Ref` are atomic and thread-safe, providing a reliable foundation for synchronizing concurrent programs.

```tut:silent
import scalaz.zio._

for {
  ref <- Ref(100)
  v1 <- ref.get
  v2 <- ref.set(v1 - 50)
} yield v2
```

## Compare and swap

`Ref`s provide atomic [CAS](https://en.wikipedia.org/wiki/Compare-and-swap) primitives in the form of [`trySet`](#) and [`compareAndSet`](#), which both yield a `Boolean` indicating whether the operation succeeded or not. This is useful to avoid concurrency problems such as phantom writes, as in the classic bank account withdrawal example:

```tut:silent
val account: IO[Nothing, Ref[Int]] = Ref(100)
val john = (act: Ref[Int]) => act.get.flatMap(v => act.compareAndSet(v, v - 30))
val gaston = (act: Ref[Int]) => act.get.flatMap(v => act.compareAndSet(v, v - 70))
val res: IO[Nothing, Int] = for {
  accountR <- account
  _ <- john(accountR) par gaston(accountR)
  v <- accountR.get
} yield v
```

This example is non-deterministic, as both John and Gaston can either receive some money or none at all, but in all cases sequential consistency is maintained thanks to `compareAndSet`. Of course, the bank implementing this logic had to shut down after two days, but that's a story for another day.

## State transformers

Those who live on the dark side of mutation sometimes have it easy; they can add state everywhere like it's Christmas. Behold:

```scala
var idCounter = 0
def freshVar: String = {
  idCounter += 1
  s"var${idCounter}"
}
val v1 = freshVar
val v2 = freshVar
val v3 = freshVar
```

As functional programmers, we know better and have captured state mutation in the form of functions of type `S => (A, S)`. `Ref` provides such an encoding, with `S` being the type of the value, and [`modify`](#) embodying the state mutation function.

```tut:silent
Ref(0).flatMap { idCounter =>
  def freshVar: IO[Nothing, String] = 
    idCounter.modify(cpt => (s"var${cpt + 1}", cpt + 1))

  for {
    v1 <- freshVar
    v2 <- freshVar
    v3 <- freshVar
  } yield ()
}
```

## Building more sophisticated concurrency primitives

`Ref` is low-level enough that it can serve as the foundation for other concurrency data types.

Semaphores are a classic abstract data type for controlling access to shared resources. They are defined as a triple S = (v, P, V) where v is the number of units of the resource that are currently available, and P and V are operations that respectively decrement and increment v; P will only complete when v is non-negative and must wait if it isn't.

Well, with `Ref`s, that's easy to do! The only difficulty is in `P`, where we must fail and retry when either `v` is negative or its value has changed between the moment we read it and the moment we try to update it. A naive implementation could look like:

```tut:silent
sealed trait S {
  def P: IO[Nothing, Unit]
  def V: IO[Nothing, Unit]
}

object S {
  def apply(v: Long): IO[Nothing, S] =
    Ref(v).map { vref =>
      new S {
        def V = vref.update(_ + 1).void

        def P = vref.get.flatMap { v =>
          if (v < 0) 
            IO.terminate
          else
            vref.compareAndSet(v, v - 1).flatMap {
              case false => IO.terminate
              case true  => IO.unit
            }
        } <> P
      }
    }
}
```

Let's rock these crocodile boots we found the other day at the market and test our semaphore at the night club, yiihaa:

```
for {
  dancefloor <- Semaphore(10)
  dancers <- IO.traverse(1 to 100)(i => (dancefloor.P *> IO.sync(println(s"${i} dancing like it's 99")) *> dancefloor.V).fork)
  _ <- Fiber.joinAll(dancers)
} yield ()
```

It goes without saying you should take a look at ZIO's own [`Semaphore`](#).