# Equivalence

> An `Equivalence[A, B]` describes an equivalence relationship between two types `A` and `B`.

An `Equivalence[A, B]` describes an equivalence relationship between two types `A` and `B`.

An equivalence relationship is defined in terms of two functions `to` and `from` that convert a value of type `A` to a value of type `B` and vice versa.

```scala
case class Equivalence[A, B](to: A => B, from: B => A)
```

To be a valid `Equivalence` the functions `to` and `from` must satisfy an identity law, which says that for any value of type `A`, if we transform it to a `B` using `to` and then back using `from` we get the same value. The same property must also apply for the reverse.

Simple examples of an equivalence relationship would be between an `List[A]` and a `Chunk[A]`. We can transform any `List` into a `Chunk` using the `Chunk.fromIterable` operator, and we can transform any `Chunk` into a `List` using the `toList` operator:

```scala

def listChunkEquivalence[A]: Equivalence[List[A], Chunk[A]] =
  Equivalence(Chunk.fromIterable, _.toList)
```

This essentially represents the fact that `List` and `Chunk` contain the same information. They represent it in different ways internally and have different performance characteristics but both of them model zero or more values of some type `A`.

We can see from the example above that the order of describing `List` and `Chunk` in the equivalence relationship was somewhat arbitrary. We could just as well have described this as an equivalence between `Chunk[A]` and `List[A]`.

The `Equivalence` data type lets us express that through the `flip` operator.

```scala
def chunkListEquivalence[A]: Equivalence[Chunk[A], List[A]] =
  listChunkEquivalence.flip
```

In addition to being able to reverse equivalence relationships we can also compose them. If `A` is equivalent to `B` and `B` is equivalent to `C` then `A` is equivalent to `C`.

We can express this using the `andThen` operator on `Equivalence` or its symbolic alias `>>>`.

To demonstrate this let's define another equivalence relationship `Vector[A]` and `List[A]`.

```scala
def vectorListEquivalence[A]: Equivalence[Vector[A], List[A]] =
  Equivalence(_.toList, _.toVector)
```

Given these two equivalence relationships we can then define an equivalence relationship between a `Vector[A]` and a `Chunk[A]`.

```scala
def vectorChunkEquivalence[A]: Equivalence[Vector[A], Chunk[A]] =
  vectorListEquivalence.andThen(listChunkEquivalence)
```

This is not a particularly interesting example because we could have easily converted directly from `Vector` to `Chunk` in a more performant way without going through `List`. But we can imagine that if `A`, `B`, and `C` were equivalent but different representations of some more complex data type, being able to build up these conversions incrementally could be quite useful.

Beyond this there are not a lot of operators on the `Equivalence` data type itself. The main power of this data type is the ability to express the fact that two types are equivalent and capture this as a data type that we can test and reuse.

In particular, if we have what we believe to be an equivalence relationship between two types `A` and `B` ZIO Prelude makes it easy for us to test this.

```scala

object EquivalenceSpec extends ZIOSpecDefault {

  def spec = suite("EquivalenceSpec") {
    test("chunkListEquivalence") {
      implicit val equivalence = listChunkEquivalence[Int]
      val listGen = Gen.listOf(Gen.int)
      val chunkGen = Gen.chunkOf(Gen.int)
      checkAllLaws(EquivalenceLaws)(listGen, chunkGen)
    }
  }
}
```

ZIO Test will generate a large number of `A` and `B` values and check that the identity law holds. This can be helpful to catch corner cases where we think two things are equivalent, but they are really not.

With these equivalence relationships in hand we can then convert between different representations of our data in a straightforward and principled way. This is helpful when we need to implement higher level logic that needs to rely on this equivalence relationship existing between certain data types.
