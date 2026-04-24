# Covariant

> `Covariant[F]` describes a parameterized type `F[A]` that potentially produces but never consumes `A` values.

`Covariant[F]` describes a parameterized type `F[A]` that potentially produces but never consumes `A` values.

Its signature is:

```scala
trait Invariant[F[_]] {
  def invmap[A, B](f: A <=> B): F[A] <=> F[B]
}

trait Covariant[F[+_]] extends Invariant[F] {
  def map[A, B](f: A => B): F[A] => F[B]
  final def invmap[A, B](f: A <=> B): F[A] <=> F[B] =
    Equivalence(map(f.to), map(f.from))
}

type <=>[A, B] = Equivalence[A, B]

case class Equivalence[A, B](to: A => B, from: B => A)
```

The `map` operator says we can "lift" a function `A => B` to a function `F[A] => F[B]`. If we import `zio.prelude._` we can use the `map` operator to transform an `F[A]` into an `F[B]` with a function `A => B`.

The law is that the lifting of this function can transform `A` values into `B` values but cannot otherwise change the structure of `F`, so using `map` with the identity function is an identity and separately using `map` with two functions is the same as doing it with the composition of those functions.

```scala
fa.map(identity) === fa
fa.map(f).map(g) === fa.map(f.andThen(g))
```

Data types that are covariant may either contain zero or more existing `A` values, such as a `Chunk`, or potentially produce zero or more `A` values at some point in the future, such as a `ZIO`.

In the definition of `Covariant` above you may notice that a `+` appears before the `_`. This tells the Scala compiler that the parameterized type is covariant with respect to this type parameter.

This improves type inference because it allows the Scala compiler to automatically widen an `F[A]` to an `F[B]` if `A` is a subtype of `B` because a data type that outputs `A` values also outputs `B` values by definition. It also allows the Scala compiler to check for us that types for which we define a `Covariant` instance really are covariant with respect to their type parameters.

Other functional programming libraries do not take advantage of Scala's support for variance here and so have to resort to a `widen` operator that essentially amounts to mapping with the identity function.

Often data types that appear to be invariant are actually versions of more polymorphic data types that are covariant with respect to one or more of their type parameters. Generalizing these data types can lead to improved API design and allow defining additional operators.

Let's see these ideas in action with the `JsonCodec` data type.

```scala
trait JsonCodec[A] {
  def decode(json: String): Either[String, A]
  def encode(a: A): String
}
```

The `JsonCodec` data type is naturally invariant in the `A` type parameter because `A` appears as both an input to `encode` and an output from `decode`. As a result, the `A` type parameter appears in the definition of `JsonCodec` without a `+` or `-`.

Let's try adding a `+` before the `A` type parameter to indicate that `JsonCodec` is covariant with respect to this type parameter.

```scala
trait JsonCodec[+A] {
  def decode(json: String): Either[String, A]
  def encode(a: A): String
}
// error: covariant type A occurs in contravariant position in type A of value a
//   def encode(a: A): String
//              ^^^^
```

This code does not compile!

The Scala compiler helpfully informs us that the covariant type `A` appears in contravariant position in the `encode` operator. Translating slightly, the Scala compiler is telling us that we are not honoring the guarantee of `A` being covariant that `A` is an output but never an input because `A` appears as an input to `encode`.

Whenever we have to make a type invariant because `A` is both an input and an output we should ask ourselves whether it is possible to split that up. We can do that either by using different type parameters for the input and output or by refactoring to create separate interfaces where `A` only appears as an input or output.

Here we will take the second approach and split the `JsonCodec` up into a `JsonDecoder` and a `JsonEncoder`.

```scala
trait JsonDecoder[+A] {
  def decode(json: String): Either[String, A]
}

trait JsonEncoder[-A] {
  def encode(a: A): String
}

trait JsonCodec[A] extends JsonDecoder[A] with JsonEncoder[A]
```

Now `A` only appears as an output of the `JsonEncoder` and only appears as an input of `JsonDecoder` and we can make `A` covariant by using the `+` before the type parameter. We can also make `JsonEncoder` contravariant, but we will defer discussion of that to the section on the `Contravariant` functional abstraction.

With the `JsonDecoder` and `JsonEncoder` separated out, we can now define a `Covariant` instance for the `JsonDecoder`.

```scala
trait JsonDecoder[+A] { self =>
  def decode(json: String): Either[String, A]
  def map[B](f: A => B): JsonDecoder[B] =
    new JsonDecoder[B] {
      def decode(json: String): Either[String, B] =
        self.decode(json).map(f)
    }
}

object JsonDecoder {
  implicit val JsonDecoderCovariant: Covariant[JsonDecoder] =
    new Covariant[JsonDecoder] {
      def map[A, B](f: A => B): JsonDecoder[A] => JsonDecoder[B] =
        jsonDecoder => jsonDecoder.map(f)
    }
}
```

In addition to being able to define a `Covariant` instance, we have improved our API design by recognizing that our definition of `JsonCodec` was actually combining two things that are conceptually distinct. Now users can work with just a `Decoder` if all they are doing is reading data and it is easier to transform a decoder because we only need to provide a function and not an equivalence relationship.

The `map` operator is extremely useful and you are probably familiar with it from a variety of data types in the Scala standard library and ZIO. However, the `map` operator is already defined directly on most data types that support it.

As a result, the `Covariant` abstraction tends to be useful in two ways.

First, defining a `Covariant` instance for your own data type allows it to work with other operators in ZIO Prelude that require a data type to be covariant.

There are only a few operators that are defined for a data type that only has a `Covariant` instance but there are many more that we will learn about later that require a `Covariant` instance as well as some other instance. So defining an instance of `Covariant`, as well as whatever other functional abstractions are defined for your date type, is a good practice so that you can use any of these operators for your data type if you need to.

Second, `Covariant` can be useful if you are writing your own generic code since many generic operators you may want to define will require a `Covariant` instance. In particular, having a `Covariant` instance as well as an instance of one of the abstractions that describe ways to combine parameterized types allow many interesting operators to be defined.
