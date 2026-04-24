# Contravariant

> `Contravariant` describes a parameterized type `F[A]` that potentially consumes but never produces `A` values.

`Contravariant` describes a parameterized type `F[A]` that potentially consumes but never produces `A` values.

Its signature is:

```scala
trait Invariant[F[_]] {
  def invmap[A, B](f: A <=> B): F[A] <=> F[B]
}

trait Contravariant[F[-_]] extends Invariant[F] {
  def contramap[A, B](f: B => A): F[A] => F[B]
  final def invmap[A, B](f: A <=> B): F[A] <=> F[B] =
    Equivalence(contramap(f.from), contramap(f.to))
}

type <=>[A, B] = Equivalence[A, B]

case class Equivalence[A, B](to: A => B, from: B => A)
```

The `contramap` operator says we can lift a function `B => A` to a function `F[A]` to `F[B]`. If we import `zio.prelude._` we can use `contramap` to transform a `F[A]` to a `F[B]` with a function `B => A`.

Notice that the arrows go in the opposite direction here. To transform a `F[A]` into a `F[B]` with the `contramap` operator we provide a function `B => A` rather than a function `A => B` like with the `map` operator. 

This can be a little counterintuitive because we are used to primarily working with data types that produce values and transforming their outputs. We will build a better sense for the `contramap` operator later in this section.

The other thing to notice here is the `-` that appears in brackets in the definition of `Contravariant`. This tells the Scala compiler that `F` is contravariant with respect to the `A` type parameter.

Doing this improves type inference because the Scala compiler knows that if `A` is a subtype of `B` then an `F[B]` is a subtype of an `F[A]`, since an `F[B]` can accept `B` inputs and every `A` is a `B`. It also allows the compiler to check that `A` really does only appear as an input to `F`.

Other functional programming libraries don't take advantage of contravariance here and so have to define a `narrow` operator, which essentially forces users to do this type casting manually.

The law is that the lifting of the function `f` in `contramap` can transform `B` values into `A` values but cannot otherwise change the structure of `F`, so using `contramap` with the identity function is an identity and separately using `contrmap` with two functions is the same as doing it with the composition of those functions.

```scala
fa.contramap(identity) === fa
fa.contramap(f).contramap(g) === fa.contramap(f.compose(g)))
```

Examples of data types that are contravariant include functions with respect to their inputs, `ZIO` with respect to its environment type, and `ZSink` with respect to its input type.

To get a better sense of what it means for a data type to be contravariant let's look at a `JSONCodec`.

```scala
trait JsonCodec[A] {
  def decode(json: String): Either[String, A]
  def encode(a: A): String
}
```

This data type doesn't have either a `+` or a `-` before the `A` type parameter, indicating that it is invariant with respect to the `A` type parameter. If we try to make `JsonCodec` contravariant by adding a `-` before the `A` type parameter we get a compilation error telling us that `A` appears in covariant position in the `decode` operator.

This is accurate because `A` does indeed appear as an output of the `decode` operator whereas it is only supposed to appear as an input to a contravariant type. To fix this we need to break the `JsonCodec` up into separate `JsonDecoder` and `JsonEncoder` types that are covariant and contravariant respectively.

```scala
trait JsonDecoder[+A] {
  def decode(json: String): Either[String, A]
}

trait JsonEncoder[-A] {
  def encode(a: A): String
}

trait JsonCodec[A] extends JsonDecoder[A] with JsonEncoder[A]
```

Now we can define a `Contravariant` instance for the `JsonEncoder`.

```scala
trait JsonEncoder[-A] { self =>
  def encode(a: A): String
  def contramap[B](f: B => A): JsonEncoder[B] =
    new JsonEncoder[B] {
      def encode(b: B): String =
        self.encode(f(b))
    }
}

object JsonEncoder {
  implicit val JsonEncoderContravariant: Contravariant[JsonEncoder] =
    new Contravariant[JsonEncoder] {
      def contramap[A, B](f: B => A): JsonEncoder[A] => JsonEncoder[B] =
        jsonEncoder => jsonEncoder.contramap(f)
    }
}
```

Let's think about what this means.

A `JsonEncoder` is something that knows how to encode values of type `A`. It says we can give it any value of type `A` and it will encode it.

The `contramap` operator says if we have a function `B => A` we can transform a `JsonEncode[A]` into a `JsonEncoder[B]`. We can do that by taking any `B` values and transforming them into `A` values with the function `f` before sending them to the original encoder.

The pattern works the same way for any contravariant type. The `contramap` operator lets us adapt the inputs to the data type with a function, for example transforming a sink that writes bytes to a file to a sink that writes strings to a file by providing a function to transform strings to bytes.

While we may be less familiar with it, the `contramap` operator is quite useful for working with contravariant types and lets us "work backwards" from the input type we need to the input type we have. So it can be useful to implement a `Contravariant` instance for our own data types just for that.

In addition, some operators in ZIO Prelude are only defined on data types that have a `Contravariant` instance along with instances of one or more other functional abstractions. So it is useful to define instances of all functional abstractions that are applicable for your own data types so that you can use these operators when you need them.

Finally, if you are writing your own generic code in terms of the abstractions in ZIO Prelude a `Contravariant` instance can be important to define certain classes of operators.
