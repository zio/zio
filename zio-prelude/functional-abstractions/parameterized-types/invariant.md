# Invariant

> `Invariant[F]` describes a parameterized type `F[A]` that potentially both consumes and produces `A` values.

`Invariant[F]` describes a parameterized type `F[A]` that potentially both consumes and produces `A` values.

Its signature is:

```scala
trait Invariant[F[_]] {
  def invmap[A, B](f: A <=> B): F[A] <=> F[B]
}

type <=>[A, B] = Equivalence[A, B]

case class Equivalence[A, B](to: A => B, from: B => A)
```

The `invmap` operator says we can "lift" an equivalence relationship between `A` and `B` into an equivalence relationship between `F[A]` and `F[B]`. An equivalence relationship says we can transform back and forth between `A` and `B` values without losing any information and is described by the `Equivalence` data type in ZIO Prelude.

If we import `zio.prelude._` we can use the `invmap` operator in infix form to transform an `F[A]` into an `F[B]` with an equivalence relationship `A <=> B`.

The law is that the lifting of this equivalence relationship can transform `A` and `B` values but cannot otherwise change the structure of `F`, so using `invmap` with the identity equivalence relationship is an identity and separately using `invmap` with two equivalence relationships is the same as doing it with the composition of those equivalence relationships.

```scala
fa.invmap(Equivalence.identity) === fa
fa.invmap(f).invmap(g) === fa.invmap(f.andThen(g))
```

An example of a data type that is invariant is a JSON codec.

```scala
trait JsonCodec[A] {
  def decode(json: String): Either[String, A]
  def encode(a: A): String
}
```

Notice that `A` appears in both the output type of the `decode` operator and the input type of the `encode` operator. This makes `JsonCodec` naturally invariant with respect to the `A` type parameter.

Let's try implementing an `invmap` operator for our `JsonCodec` type.

```scala

trait JsonCodec[A] { self =>

  def decode(json: String): Either[String, A]

  def encode(a: A): String

  def invmap[B](f: A <=> B): JsonCodec[B] =
    new JsonCodec[B] {
      def decode(json: String): Either[String, B] =
        self.decode(json).map(f.to)
      def encode(b: B): String =
        self.encode(f.from(b))
    }
}

object JsonCode {
  implicit val JsonCodecInvariant: Invariant[JsonCodec] =
    new Invariant[JsonCodec] {
      def invmap[A, B](f: A <=> B): JsonCodec[A] <=> JsonCodec[B] =
        Equivalence(_.invmap(f), _.invmap(f.flip))
    }
}
```

The implementation of `invmap` was quite simple and follows a pattern we can always use when implementing `invmap` for our own data types.

For every operator that outputs `A` values, we can output `B` values instead by taking the `A` values and transforming them to `B` values with the equivalence relationship. For example, in our implementation of `decode` we decode a string to produce an `Either[String, A]` with the original codec and then map over the `Either[String, A]` with the equivalence relationship to produce an `Either[String, B]`.

Similarly, for every operator that accepts `A` values as an input, we can accept `B` values instead and then simply transform them into `A` values with the equivalence relationship. So in our implementation of `encode` we accept `B` values to be encoded, transform them into `A` values with the equivalence relationship, and then feed them to the `encode` operator of the original codec to get a JSON string.

The operators may vary and we may need to do slightly different things to transform the inputs or outputs like mapping over the `Either` in the `JsonCodec` example, but this basic pattern applies to implementing the `invmap` operator for all invariant types.

Other data types that are invariant are the `Set` data type in the Scala standard library and the `Ref`, `Queue`, and `Hub` data types in `ZIO`.

The `Invariant` data type itself describes very limited structure. It just says that this parameterized type potentially consumes and produces `A` values, which are conceptually the only things it could do with `A` values.

As a result, the `invmap` operator itself is not very powerful. It only lets us go back and forth between `F[A]` and `F[B]` values when there is an equivalence relationship between `A` and `B`, which basically means that `A` and `B` contain the same information.

However, `Invariant` can be useful when we do have an equivalence relationship between two data types to "lift" that equivalence relationship to the level of the parameterized type.

For example, if we have two different versions of our data model it could be generally useful to define an `Equivalence` between those different versions of our data model so we could translate back and forth between different versions of the data model. With that, we could also use `Invariant` to generate a `JsonCodec` for the new data model given a `JsonCodec` for the old data model, or vice versa.
