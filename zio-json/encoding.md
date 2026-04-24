# Encoding

> Assume we want to encode this case class

## Automatic Derivation

Assume we want to encode this case class

```scala
case class Banana(curvature: Double)
```

To produce JSON from our data we define a `JsonEncoder` like this:

```scala

object Banana {
  implicit val encoder: JsonEncoder[Banana] =
    DeriveJsonEncoder.gen[Banana]
}
```

```scala
Banana(0.5).toJson
// res0: String = "{\"curvature\":0.5}"
```

### ADTs

Say we extend our data model to include more data types

```scala
sealed trait Fruit

case class Banana(curvature: Double) extends Fruit
case class Apple (poison: Boolean)   extends Fruit
```

we can generate the encoder for the entire `sealed` family

```scala

object Fruit {
  implicit val encoder: JsonEncoder[Fruit] =
    DeriveJsonEncoder.gen[Fruit]
}
```

```scala
val apple: Fruit = Apple(poison = false)
// apple: Fruit = Apple(poison = false)
apple.toJson
// res2: String = "{\"Apple\":{\"poison\":false}}"
```

Almost all of the standard library data types are supported as fields on the case class, and it is easy to add support if one is missing.

### Sealed families and enums for Scala 3
Sealed families where all members are only objects, or a Scala 3 enum with all cases parameterless are interpreted as enumerations and will encode 1:1 with their value-names.
```scala
enum Foo derives JsonEncoder:
  case Bar
  case Baz
  case Qux
```
or
```scala
sealed trait Foo derives JsonEncoder
object Foo:
  case object Bar extends Foo
  case object Baz extends Foo
  case object Qux extends Foo
```

## Manual instances

Sometimes it is easier to reuse an existing `JsonEncoder` rather than generate a new one. This can be accomplished using convenience methods on the `JsonEncoder` typeclass to *derive* new decoders:

```scala
trait JsonEncoder[A] {
  def contramap[B](f: B => A): JsonEncoder[B]
  ...
}
```

### `.contramap`

We can use `contramap` from an already existing encoder:

```scala

case class FruitCount(value: Int)

object FruitCount {
  implicit val encoder: JsonEncoder[FruitCount] =
    JsonEncoder[Int].contramap(_.value)
}

FruitCount(3).toJson
// res3: String = "3"
```
