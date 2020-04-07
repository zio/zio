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

## Concurrent Optics

A `ZRef` can be used as a concurrent optic. Transformations of the `ZRef` access different parts of the underlying data, supporting "zooming in" to fields in product types, cases in sum types, and elements in collections. Optics allow modifying parts of immutable data structures in an ergonomic, composable way. Operations on `ZRef` are guaranteed to be atomic, even if the `ZRef` is composed from multiple optics.

```scala mdoc:invisible
import zio.optics._
```

## Accessing Fields of a Product Type

A `Lens[S, A]` allows accessing an element `A` in a product type `S`, such as a case class. A lens is defined in terms of a `get` and a `set` function.

```scala mdoc:silent
object Lens {
  def apply[S, A](get: S => A, set: A => S => S): Lens[S, A] = ???
}
```

Given a larger structure `S` we can always get a value of type `A` out, and given a value of type `A` and a larger structure `S` we can always set an `A` value in the structure. For example, we can define a lens for a `Person` as:

```scala mdoc:silent
case class Person(name: String, age: Int)

def age: Lens[Person, Int] =
  Lens(person => person.age, age => person => person.copy(age = age))
```

Given this definition, we can "zoom in" on a `Ref[Person]` to get a `Ref[Int]` using `accessField`. We can then modify the `age` field using the `update` operation. Getting the value of the original reference will then return a new `Person` with the updated `age`.

```scala mdoc:silent
for {
  ref <- Ref.make(Person("User", 42))
  view   =  ref.accessField(age)
  _      <- view.update(_ + 1)
  value  <- ref.get
} yield value
```

## Accessing Cases of a Sum Type

A `Prism[S, A]` allows accessing one of the potential cases of a product type `S`, such as a sealed trait. A prism is also defined in terms of a `get` and a `set` function, but with sligtly different signatures.

```scala mdoc:silent
object Prism {
  def apply[S, A](get: S => Option[A], set: A => S): Prism[S, A] = ???
}
```

Notice that the `get` function here returns an `Option[A]` rather than an `A`. A value of type `S` could be one of several different cases and may not be the one we are accessing, so we may not be able to `get` a value at all! The `set` function here also has a signature of `A => S` rather than `A => S => S`, indicating that we don't need any prior state to set a new state. An `A` is a subtype of `S` so an `A` _is_ an `S`. We can illustrate this with `Either`:

```scala mdoc:silent
def left[A, B]: Prism[Either[A, B], A] =
  Prism(s => s match { case Left(a) => Some(a); case _ => None }, a => Left(a))
```

We can "zoom into" one the `Left` cases of the `Either` using `accessCase`. The resulting `Ref` will have type `ZRef[Nothing, Unit, A, A]`, indicating that the `get` operation can potentially fail with the `Unit` value if the value in the original reference is not a `Left`, but the `set` operation can never fail.

```scala mdoc:silent
for {
  ref   <- Ref.make[Either[List[String], Int]](Left(Nil))
  view  =  ref.accessCase(left)
  _     <- view.update("fail" :: _)
  value <- ref.get
} yield value
```

## Accessing A Single Element in a Collection

An `Optional[S, A]` allows accessing a single element of type `A` in a collection of type `S`. An `Optional` is like a `Lens` that can fail to `get` a value, and its ` get` and `set` functions are defined as:

```scala mdoc:silent
object Optional {
  def apply[S, A](get: S => Option[A], set: A => S => S): Optional[S, A] = ???
}
```

Given a collection of type `S`, we can `get` a value of type `A`, but the value may not exist if the collection does not contain the field we are accessing. The `set` operation requires both an `A` value and an original collection `S` and allows setting the `A` value in the collection, or returning the original collection unchanged if the position we are accessing does not exist. An example of this would be accessing an element at a given index in a `Vector`.

```scala mdoc:silent
def index[A](n: Int): Optional[Vector[A], A] =
  Optional(
    s => if (s.isDefinedAt(n)) Some(s(n)) else None,
    a => s => if (s.isDefinedAt(n)) s.updated(n, a) else s
  )
```

Then we can use `accessField` to "zoom into" the element at the specified index. The type of the `ZRef` returned will be `ZRef[Nothing, Unit, A, A]`, indicating that the `get` operation can fail if the `Vector` is not defined at the index we are accessing. The `set` operation cannot fail, but could return the original `Vector` unchanged if the index we are accessing does not exist.

```scala mdoc:silent
for {
  ref   <- Ref.make(Vector(1, 2, 3))
  view  =  ref.accessField(index(2))
  _     <- view.set(4)
  value <- ref.get
} yield value
```

## Accessing Multiple Elements in a Collection

A `Traversal[S, A]` allows accessing elements of type `A` of a collection of type `S`. As with other optics, a `Traversal` is defined in terms of `get` and `set` functions with different signatures.

```scala mdoc:silent
object Traversal {
  def apply[S, A](get: S => List[A], set: List[A] => S => Option[S]): Traversal[S, A] = ???
}
```

Given a collection of type `S` we can always `get` a (potentially empty) collection of elements of type `A`. Given a collection of elements of type `A` and a collection of type `S`, we can try to `set` the `A` values, but this could fail if the elements we are trying to set do not match the "shape" of the collection. To illustrate, we can define a way to access a slice of a `Vector`:

```scala mdoc:silent
def slice[A](from: Int, until: Int): Traversal[Vector[A], A] = {
  Traversal(
    s => s.slice(from, until).toList,
    as => s => {
      val n = ((until min s.length) - from) max 0
      if (as.length < n) None else Some(s.patch(from, as.take(n), n))
    }
  )
}
```

With this, we can "zoom into" matching elements of a `Vector[A]` use `accessElements`. The resulting `ZRef` will have type `ZRef[Unit, Nothing, List[A], List[A]]`. The `set` operation could potentially fail with the `Unit` value if the number of elements set is less than the size of the slice. The `get` operation can never fail, but could return no elements.

```scala mdoc:silent
def negate(as: List[Int]): List[Int] =
  for (a <- as) yield -a

for {
  ref   <- Ref.make(Vector(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))
  view  =  ref.accessElements(slice(3, 6))
  _     <- view.update(negate)
  value <- ref.get
} yield value
```