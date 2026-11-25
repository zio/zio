# Customize Field Names and Discriminators

> Your BSON documents have a naming scheme that differs from Scala's, such as `snake_case` keys for a camel-case case class. Or you need to read a sealed trait that another system writes with a `kind` field, or with a type name you do not control. This guide shows how to change those names without changing the case classes.

## Problem

## Rename One Field

To change the BSON key of a single field, put `bsonField` on the field. The annotation overrides every other naming rule for that field:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
import zio.bson.magnolia._

final case class Order(orderId: String, @bsonField("total_cents") total: Long)

object Order {
  implicit val codec: BsonCodec[Order] = DeriveBsonCodec.derive
}
```

```scala
println(BsonEncoder[Order].toBsonValue(Order("A-1", 1250L)))
// {"orderId": "A-1", "total_cents": 1250}
```

The key `orderId` keeps its name, because only `total` has an annotation.

## Rename All Fields

To change every field name in a type at once, set `fieldNameMapping` in a configuration. The next example writes `orderId` as `order_id` and reads it back:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
import zio.bson.magnolia._
import zio.bson.magnolia.BsonCodecConfiguration.toSnakeCase

final case class Order(orderId: String, customerName: String)

def orderCodec(config: BsonCodecConfiguration): BsonCodec[Order] = {
  implicit val configuration: BsonCodecConfiguration = config
  DeriveBsonCodec.derive
}

val snakeOrder: BsonCodec[Order] =
  orderCodec(BsonCodecConfiguration(fieldNameMapping = toSnakeCase))
```

```scala
println(snakeOrder.encoder.toBsonValue(Order("A-1", "Ada")))
// {"order_id": "A-1", "customer_name": "Ada"}
println(snakeOrder.decoder.fromBsonValue(doc("order_id" -> str("A-2"), "customer_name" -> str("Grace"))))
// Right(Order(A-2,Grace))
```

The mapping runs on each field name, so an annotation on a single field still wins over it. Other mapping functions are listed in [BsonCodecConfiguration](./bson-codec-configuration.md#name-mappers).

## Choose the Discriminator Field for a Sealed Trait

A sealed trait is written as a wrapper by default: the subtype name is the only key. To write the subtype name as a field in the same document, set `sumTypeHandling` to `DiscriminatorField`. The next example uses the field name `kind`:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
import zio.bson.magnolia._
import zio.bson.magnolia.BsonCodecConfiguration
import zio.bson.magnolia.BsonCodecConfiguration.SumTypeHandling

sealed trait Payment

object Payment {
  final case class Card(last4: String)    extends Payment
  final case class Transfer(iban: String) extends Payment

  implicit val config: BsonCodecConfiguration =
    BsonCodecConfiguration(sumTypeHandling = SumTypeHandling.DiscriminatorField("kind"))

  implicit val codec: BsonCodec[Payment] = DeriveBsonCodec.derive
}
```

```scala
println(BsonEncoder[Payment].toBsonValue(Payment.Card("4242")))
// {"last4": "4242", "kind": "Card"}
println(BsonDecoder[Payment].fromBsonValue(doc("kind" -> str("Transfer"), "iban" -> str("DE00"))))
// Right(Transfer(DE00))
```

## Rename a Subtype

The discriminator value is the subtype's class name, unless you change it. `bsonHint` sets a different name for one subtype. The next example writes the `Transfer` subtype as `bank_transfer`:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
import zio.bson.magnolia._
import zio.bson.magnolia.BsonCodecConfiguration.SumTypeHandling

@bsonDiscriminator("type")
sealed trait Event

object Event {
  final case class Login(user: String) extends Event

  @bsonHint("bank_transfer")
  final case class Transfer(amount: Long) extends Event

  implicit val codec: BsonCodec[Event] = DeriveBsonCodec.derive
}
```

```scala
println(BsonEncoder[Event].toBsonValue(Event.Transfer(500L)))
// {"amount": 500, "type": "bank_transfer"}
println(BsonDecoder[Event].fromBsonValue(doc("type" -> str("Login"), "user" -> str("ada"))))
// Right(Login(ada))
```

The `@bsonDiscriminator` annotation on the trait sets the field name to `type`. It overrides `sumTypeHandling` for this trait only.

## Related Pages

- [BsonCodecConfiguration](./bson-codec-configuration.md) lists every option and annotation.
- [Use zio-bson with the MongoDB Driver](./how-to-use-with-mongodb-driver.md) shows how a derived codec is used with a database.
