---
id: datatypes_ref
title:  "Ref"
---

`Ref[A]` models a mutable reference to a value of type `A`. The two basic operations are `set`, which fills the `Ref` with a new value, and `get`, which retrieves its current content. All operations on a `Ref` are atomic and thread-safe, providing a reliable foundation for synchronizing concurrent programs.

```scala mdoc:silent
import zio._

for {
  ref <- Ref.make(100)
  v1 <- ref.get
  v2 <- ref.set(v1 - 50)
} yield v2
```

## Updating a `Ref`

The simplest way to use a `Ref` is by means of `update` or its more powerful sibling `modify`. Let's write a combinator `repeat` just because we can:

```scala mdoc:silent
def repeat[E, A](n: Int)(io: IO[E, A]): IO[E, Unit] =
  Ref.make(0).flatMap { iRef =>
    def loop: IO[E, Unit] = iRef.get.flatMap { i =>
      if (i < n)
        io *> iRef.update(_ + 1) *> loop
      else
        IO.unit
    }
    loop
  }
```

## State Transformers

Those who live on the dark side of mutation sometimes have it easy; they can add state everywhere like it's Christmas. Behold:

```scala mdoc:silent
var idCounter = 0
def freshVar: String = {
  idCounter += 1
  s"var${idCounter}"
}
val v1 = freshVar
val v2 = freshVar
val v3 = freshVar
```

As functional programmers, we know better and have captured state mutation in the form of functions of type `S => (A, S)`. `Ref` provides such an encoding, with `S` being the type of the value, and `modify` embodying the state mutation function.

```scala mdoc:silent
Ref.make(0).flatMap { idCounter =>
  def freshVar: UIO[String] =
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

```scala mdoc:silent
sealed trait S {
  def P: UIO[Unit]
  def V: UIO[Unit]
}

object S {
  def apply(v: Long): UIO[S] =
    Ref.make(v).map { vref =>
      new S {
        def V = vref.update(_ + 1).unit

        def P = (vref.get.flatMap { v =>
          if (v < 0)
            IO.fail(())
          else
            vref.modify(v0 => if (v0 == v) (true, v - 1) else (false, v)).flatMap {
              case false => IO.fail(())
              case true  => IO.unit
            }
        } <> P).unit
      }
    }
}
```

Let's rock these crocodile boots we found the other day at the market and test our semaphore at the night club, yiihaa:

```scala mdoc:silent
import zio.duration.Duration
import zio.clock._
import zio.console._
import zio.random._

val party = for {
  dancefloor <- S(10)
  dancers <- ZIO.foreachPar(1 to 100) { i =>
    dancefloor.P *> nextDouble.map(d => Duration.fromNanos((d * 1000000).round)).flatMap { d =>
      putStrLn(s"${i} checking my boots") *> sleep(d) *> putStrLn(s"${i} dancing like it's 99")
    } *> dancefloor.V
  }
} yield ()
```

It goes without saying you should take a look at ZIO's own `Semaphore`, it does all this and more without wasting all those CPU cycles while waiting.

## Polymorphic `Ref`s

`Ref[A]` is actually a type alias for `ZRef[Nothing, Nothing, A, A]`. The type signature of `ZRef` is:

```scala mdoc:silent
trait ZRef[+EA, +EB, -A, +B]
```

A `ZRef` is a polymorphic, purely functional description of a mutable reference. The fundamental operations of a `ZRef` are `set` and `get`. `set` takes a value of type `A` and sets the reference to a new value, potentially failing with an error of type `EA`. `get` gets the current value of the reference and returns a value of type `B`, potentially failing with an error of type `EB`.

When the error and value types of the `ZRef` are unified, that is, it is a `ZRef[E, E, A, A]`, the `ZRef` also supports atomic `modify` and `update` operations as discussed above.

A simple use case is passing out read-only or write-only views of a reference:

```scala mdoc:silent
for {
  ref       <- Ref.make(false)
  readOnly  = ref.readOnly
  writeOnly = ref.writeOnly
  _         <- writeOnly.set(true)
  value     <- readOnly.get
} yield value
```
