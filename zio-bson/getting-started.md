# Getting Started with ZIO BSON

> This page walks through the first program: declare a codec for a case class, convert values to and from `BsonValue`, and plug the codec into the MongoDB driver. To add the dependencies, see [Installation](./index.md#installation).

All the following code snippets assume that these imports have been declared:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
```

## Declaring Codecs

To get a codec for a case class or a sealed trait, derive it with `DeriveBsonCodec.derive`:

```scala
import zio.bson.magnolia._

case class Banana(curvature: Double)

object Banana {
  implicit val codec: BsonCodec[Banana] = DeriveBsonCodec.derive
}
```

## Converting to BsonValue

To use values in `Filter` or `Update` expressions, convert them to `BsonValue` with `toBsonValue`:

```scala
"str".toBsonValue

Banana(0.2).toBsonValue

import org.bson._

def method[T: BsonEncoder](value: T): BsonDocument = doc("key" -> value.toBsonValue)
```

## Creating a CodecProvider

To get a `CodecProvider` for a `BsonCodec`, use `zioBsonCodecProvider`:

```scala
val codecProvider = zioBsonCodecProvider[Banana]
```

## Parsing a BsonValue

In general, a `CodecProvider` parses a case class without an intermediate `BsonValue`. A `BsonValue` can still be parsed with `as`:

```scala
import org.bson._

val bsonVal: BsonValue = doc("curvature" -> double(0.2))
// bsonVal: BsonValue = {"curvature": 0.2}

bsonVal.as[Banana]
// res2: Either[String, Banana] = Right(value = Banana(curvature = 0.2))
```

## Errors

Bad BSON produces an error with the path and contextual information. To see one, decode a `BINARY` value where a `Double` is expected:

```scala
doc("curvature" -> Array[Byte](1, 2, 3).toBsonValue).as[Banana]
// res3: Either[String, Banana] = Left(
//   value = ".curvature: Expected BSON type Double, but got BINARY."
// )
```

## Configuration

To configure derivation, use annotations. A sealed trait is encoded as a document with one key per subtype by default, and the annotations rename the discriminator, the subtype and the field:

```scala
sealed trait Fruit

object Fruit {
  case class Pear(curvature: Double) extends Fruit
  case class Apple(poison: Boolean)  extends Fruit

  implicit val codec: BsonCodec[Fruit] = DeriveBsonCodec.derive
}

@bsonDiscriminator("$type")
sealed trait FruitConfigured

object FruitConfigured {
  case class Pear(curvature: Double) extends FruitConfigured

  @bsonHint("custom_type_name")
  case class Apple(@bsonField("is_poisoned") poison: Boolean) extends FruitConfigured

  implicit val codec: BsonCodec[FruitConfigured] = DeriveBsonCodec.derive
}
```

Next, decode a document written in the default shape and one written in the configured shape:

```scala
doc("Pear" -> doc("curvature" -> double(0.5))).as[Fruit]
// res4: Either[String, Fruit] = Right(value = Pear(curvature = 0.5))

doc("$type" -> str("custom_type_name"), "is_poisoned" -> bool(true)).as[FruitConfigured]
// res5: Either[String, FruitConfigured] = Right(value = Apple(poison = true))
```

## Next Steps

See [Type Mappings](./types.md) for the supported Scala and Java types, [Configuration and Annotations](./bson-codec-configuration.md) for every setting, and the [tutorial](./tutorial-store-and-load.md) for a complete program.
