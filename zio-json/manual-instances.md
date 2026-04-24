# Manual Instances

> Sometimes it is easier to reuse an existing `JsonDecoder` rather than generate a new one. This can be accomplished using convenience methods on the `JsonDecoder` typeclass to *derive* new decoders:

Sometimes it is easier to reuse an existing `JsonDecoder` rather than generate a new one. This can be accomplished using convenience methods on the `JsonDecoder` typeclass to *derive* new decoders:

```scala
trait JsonDecoder[A] {
  def map[B](f: A => B): JsonDecoder[B]
  def mapOrFail[B](f: A => Either[String, B]): JsonDecoder[B]
  ...
}
```

Similarly, we can reuse an existing `JsonEncoder`

```scala
trait JsonEncoder[A] {
  def contramap[B](f: B => A): JsonEncoder[B]
  ...
}
```

### `.map`

We can `.map` from another `JsonDecoder` in cases where the conversion will always succeed. This is very useful if we have a `case class` that simply wraps another thing and shares the same expected JSON.

For example, say we want to model the count of fruit with a `case class` to provide us with additional type safety in our business logic (this pattern is known as a *newtype*).

```scala
case class FruitCount(value: Int)
```

but this would cause us to expect JSON of the form

```json
{"value":1}
```

wheres we really expect the raw number. We can derive a decoder from `JsonDecoder[Int]` and `.map` the result into a `FruitCount`

```scala
object FruitCount {
  implicit val decoder: JsonDecoder[FruitCount] = JsonDecoder[Int].map(FruitCount(_))
}
```

and now the `JsonDecoder` for `FruitCount` just expects a raw `Int`.

Every time we use a `.map` to create a `JsonDecoder` we can usually create a `JsonEncoder` with `.contramap`

```scala
object FruitCount {
  ...
  implicit val encoder: JsonEncoder[FruitCount] = JsonEncoder[Int].contramap(_.value)
}
```

Another use case is if we want to encode a `case class` as an array of values, rather than an object with named fields. Such an encoding is very efficient because the messages are smaller and require less processing, but are very strict schemas that cannot be upgraded.

```scala
case class Things(s: String, i: Int, b: Boolean)
object Things {
  implicit val decoder: JsonDecoder[Things] =
    JsonDecoder[(String, Int, Boolean)].map { case (p1, p2, p3) => Things(p1, p2, p3) }
}
```

which parses the following JSON

```json
["hello",1,true]
```

### `.mapOrFail`

We can use `.mapOrFail` to take the result of another `JsonDecoder` and try to convert it into our custom data type, failing with a message if there is an error.

Say we are using the [`refined`](https://github.com/fthomas/refined) library to ensure that a `Person` data type only holds a non-empty string in its `name` field

```scala

case class Person(name: String Refined NonEmpty)

object Person {
  implicit val decoder: JsonDecoder[Person] = DeriveJsonDecoder.gen
}
```

we will get a compiletime error because there is no `JsonDecoder[String Refined NonEmpty]`.

However, we can derive one by requesting the `JsonDecoder[String]` and calling `.mapOrFail`, supplying the constructor for our special `String Refined NonEmpty` type

```scala
implicit val decodeName: JsonDecoder[String Refined NonEmpty] =
  JsonDecoder[String].mapOrFail(refined.refineV[NonEmpty](_))
```

Now the code compiles.

In fact, we do not need to provide `decodeName` for each `Refined` data type; `zio-json` comes with support out of the box, see the Integrations section below.
