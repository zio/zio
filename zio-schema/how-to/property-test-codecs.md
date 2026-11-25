# How to Property-Test Codecs with DeriveGen

> Write a ZIO Test spec that round-trips every model through JSON and Protobuf codecs using generators derived by DeriveGen.

In this guide, we write a ZIO Test spec that encodes and decodes every model of a small order system with the JSON and Protobuf codecs, using random values that `DeriveGen.gen` generates from each model's schema. By the end, you have a spec where covering a new model takes one line, plus hand-written generators for the cases where the derived ones are not enough.

We start from the models and their schemas, write one reusable round-trip check, run it over every model, and then replace the derived generators where a property needs valid values or where `DeriveGen.gen` cannot generate a type at all.

## The Problem

A codec round trip, `decode(encode(value)) == Right(value)`, is the cheapest test that catches a broken codec: a renamed field, a sealed-trait case that encodes but does not decode, or an `Option` that comes back as a different value. To be useful, it needs many different values of every model, including empty collections, `None`, and every case of every sealed trait.

Writing those values by hand means one generator per type, and each one repeats the structure of its case class:

```scala
import java.util.UUID

import zio.test.{Gen, Sized}

final case class Customer(id: UUID, name: String, email: Option[String])
final case class LineItem(sku: String, quantity: Int, priceCents: Long)
final case class Order(id: Long, customer: Customer, items: List[LineItem])

val customerGen: Gen[Sized, Customer] =
  for {
    id    <- Gen.uuid
    name  <- Gen.string
    email <- Gen.option(Gen.string)
  } yield Customer(id, name, email)

val lineItemGen: Gen[Sized, LineItem] =
  for {
    sku        <- Gen.string
    quantity   <- Gen.int
    priceCents <- Gen.long
  } yield LineItem(sku, quantity, priceCents)

val orderGen: Gen[Sized, Order] =
  for {
    id       <- Gen.long
    customer <- customerGen
    items    <- Gen.listOf(lineItemGen)
  } yield Order(id, customer, items)
```

Every new field means editing a generator, and a generator that falls behind its case class still compiles when the new field has a default value, so the new field is never tested. A sealed trait needs one more generator per case, and forgetting a case leaves it untested without any warning.

The models already have schemas, and a schema describes the same structure. `DeriveGen.gen` turns a `Schema[A]` into a `Gen[Sized, A]`, so the generators stay in step with the models.

## Prerequisites

We need the derivation module, the two codecs under test, and `zio-schema-zio-test`, which provides `DeriveGen` and brings in ZIO Test. ZIO Test's sbt runner, `zio-test-sbt`, runs the specs:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-schema-derivation" % "1.9.2",
  "dev.zio" %% "zio-schema-json"       % "1.9.2",
  "dev.zio" %% "zio-schema-protobuf"   % "1.9.2",
  "dev.zio" %% "zio-schema-zio-test"   % "1.9.2" % Test,
  "dev.zio" %% "zio-test-sbt"          % zioVersion  % Test
)
```

Here `zioVersion` is the ZIO version your project already uses. The guide assumes you know how `DeriveGen.gen` works on a single type; [Derivation of ZIO Test Generators](../derivations/zio-test-gen-derivation.md) introduces it.

## Defining the Models

The order system has four models: a `Customer` with an optional email, a `LineItem` whose quantity must be positive, a `Payment` sealed trait with two cases, and an `Order` that nests the others together with a list and a map. Each one keeps its derived schema in its companion object, where `DeriveGen.gen` and the codecs find it:

```scala
import java.util.UUID

import zio.schema.{DeriveGen, DeriveSchema, Schema}
import zio.schema.annotation.validate
import zio.schema.validation.Validation
import zio.test.{Gen, Sized}

final case class Customer(id: UUID, name: String, email: Option[String])

object Customer {
  implicit val schema: Schema[Customer] = DeriveSchema.gen[Customer]
}

final case class LineItem(
  sku: String,
  @validate(Validation.greaterThan(0)) quantity: Int,
  priceCents: Long
)

object LineItem {
  implicit val schema: Schema[LineItem] = DeriveSchema.gen[LineItem]
}

sealed trait Payment

object Payment {
  final case class Card(last4: String, expiryMonth: Int) extends Payment
  final case class Invoice(dueInDays: Int)              extends Payment

  implicit val schema: Schema[Payment] = DeriveSchema.gen[Payment]
}

final case class Order(
  id: Long,
  customer: Customer,
  items: List[LineItem],
  tags: Map[String, String],
  payment: Payment
)

object Order {
  implicit val schema: Schema[Order] = DeriveSchema.gen[Order]
}
```

The `@validate` rule on `LineItem#quantity` does not matter for the round trip, but it matters later, when a property needs valid orders.

## Writing One Round-Trip Check

The check itself does not depend on the model or the format. It takes a `BinaryCodec[A]` and a generator, and asserts that every generated value survives `BinaryCodec#encode` and then `BinaryCodec#decode`. A second helper builds one suite per model, with one test per codec:

```scala
import zio.ZIO
import zio.schema.codec.{BinaryCodec, JsonCodec, ProtobufCodec}
import zio.test._

def roundTrip[A](codec: BinaryCodec[A], gen: Gen[Sized, A]): ZIO[Sized, Nothing, TestResult] =
  check(gen) { value =>
    assertTrue(codec.decode(codec.encode(value)) == Right(value))
  }

def roundTrips[A](label: String, gen: Gen[Sized, A])(implicit schema: Schema[A]): Spec[Sized, Nothing] = {
  val json: BinaryCodec[A]     = JsonCodec.schemaBasedBinaryCodec[A]
  val protobuf: BinaryCodec[A] = ProtobufCodec.protobufCodec[A]

  suite(label)(
    test("JSON")(roundTrip(json, gen)),
    test("Protobuf")(roundTrip(protobuf, gen))
  )
}
```

`roundTrips` takes the generator as an argument instead of deriving it, so the same helper works later with hand-written generators. Both codecs are built from the implicit `Schema[A]`, the same schema `DeriveGen.gen` reads. To test another format, such as MessagePack or Avro, we add one more `test` line to `roundTrips`.

## Round-Tripping Every Model

With the helper in place, the spec lists each model once and passes `DeriveGen.gen` for its generator:

```scala
import zio.Scope

object CodecRoundTripSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("codec round trips")(
      roundTrips("Customer", DeriveGen.gen[Customer]),
      roundTrips("LineItem", DeriveGen.gen[LineItem]),
      roundTrips("Payment", DeriveGen.gen[Payment]),
      roundTrips("Order", DeriveGen.gen[Order])
    )
}
```

Each `check` runs the property with many generated values, 200 by default. The `Payment` generator picks a case at random for every sample, so the samples include both `Card` and `Invoice`, and the `Order` generator reuses the generators of the nested models. When a value fails, ZIO Test shrinks it and reports the smallest failing input it finds, which usually points at the single field the codec mishandles.

Testing the nested models on their own as well as inside `Order` keeps failures readable: a broken `Payment` encoding fails the small `Payment` suite with a small counterexample, not only the `Order` suite with a large one.

## Generating Values That Pass Validation

A derived generator follows the shape of the schema, not its `@validate` rules, so `DeriveGen.gen[LineItem]` produces zero and negative quantities. That is fine for a round trip, because the codecs read and write invalid values too, but a property about business logic usually needs valid input.

`DeriveGen` has no hook to override the generator of one field, so we write a generator for the type that needs different values, and build the enclosing type with a `for` comprehension that uses derived generators for everything else:

```scala
val validLineItem: Gen[Sized, LineItem] =
  for {
    sku        <- Gen.alphaNumericStringBounded(3, 12)
    quantity   <- Gen.int(1, 100)
    priceCents <- Gen.long(0L, 1000000L)
  } yield LineItem(sku, quantity, priceCents)

val validOrder: Gen[Sized, Order] =
  for {
    id       <- Gen.long
    customer <- DeriveGen.gen[Customer]
    items    <- Gen.listOfBounded(1, 20)(validLineItem)
    tags     <- DeriveGen.gen[Map[String, String]]
    payment  <- DeriveGen.gen[Payment]
  } yield Order(id, customer, items, tags, payment)
```

`validOrder` also fixes the collection sizes: `Gen.listOfBounded` generates between 1 and 20 line items, while the derived generator only ever produces zero or two.

When the rules reject only a small share of the values, a shorter option is to keep the derived generator and drop the invalid samples with `Gen#filter` and `Schema.validate`, which returns the failed checks as a `Chunk[ValidationError]`:

```scala
val filteredOrder: Gen[Sized, Order] =
  DeriveGen.gen[Order].filter(order => Schema.validate(order).isEmpty)
```

Both generators plug into properties the same way the derived ones do. The following spec checks a business rule that holds only for valid orders, and reuses `roundTrips` with the custom generator:

```scala
import zio.Scope

def totalUnits(order: Order): Long =
  order.items.map(_.quantity.toLong).sum

object OrderRulesSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("orders")(
      test("every line item contributes at least one unit") {
        check(validOrder) { order =>
          assertTrue(totalUnits(order) >= order.items.size.toLong)
        }
      },
      test("filtered orders pass validation") {
        check(filteredOrder) { order =>
          assertTrue(Schema.validate(order).isEmpty)
        }
      },
      roundTrips("valid Order", validOrder)
    )
}
```

With `DeriveGen.gen[Order]` instead of `validOrder`, the first test fails as soon as a sample contains a line item with a quantity below one.

:::tip
Prefer a hand-written generator over `Gen#filter` when most random values break a rule, such as a string that must match a regular expression. A filter discards every rejected sample and draws again, so a strict rule makes the property slow.
:::

## Covering Types DeriveGen Cannot Generate

`DeriveGen.gen` has no generator for non-empty collections, so a model with a `NonEmptyChunk` field needs a hand-written generator. `Gen.chunkOf1` generates `NonEmptyChunk` values, and the remaining fields can still come from `DeriveGen.gen`:

```scala
import zio.NonEmptyChunk

final case class Shipment(orderIds: NonEmptyChunk[Long], carrier: String)

object Shipment {
  implicit val schema: Schema[Shipment] = DeriveSchema.gen[Shipment]
}

val shipmentGen: Gen[Sized, Shipment] =
  for {
    orderIds <- Gen.chunkOf1(Gen.long)
    carrier  <- DeriveGen.gen[String]
  } yield Shipment(orderIds, carrier)
```

The round trip then uses `roundTrip` directly with the codec and the hand-written generator:

```scala
import zio.Scope

object ShipmentRoundTripSpec extends ZIOSpecDefault {
  val json: BinaryCodec[Shipment] = JsonCodec.schemaBasedBinaryCodec[Shipment]

  def spec: Spec[TestEnvironment with Scope, Any] =
    test("Shipment survives a JSON round trip")(roundTrip(json, shipmentGen))
}
```

Passing `DeriveGen.gen[Shipment]` instead makes the test fail with an exception as soon as `check` samples the generator, as the next section describes.

## Knowing What the Derived Generator Covers

A passing round trip only covers the values the generator produces. The [Limitations](../derivations/zio-test-gen-derivation.md#limitations) section of the `DeriveGen` page lists the gaps of the derived generator. This guide already works around two of them: `validLineItem` replaces the derived generator because it ignores `@validate` rules, and `Gen.listOfBounded` replaces the derived sequences and maps, which always have zero or two elements whatever the `Sized` setting. Two more gaps decide whether a spec runs at all:

:::caution
`DeriveGen.gen` throws an exception with the message "Missing handler for generating value of schema ..." for a schema that is, or contains, a `NonEmptyChunk`, a `NonEmptyMap`, or a `Fallback`. For a field of one of these types, the exception comes when `check` samples the generator, so the test fails instead of running. Write the generator for that type by hand, as `shipmentGen` does.

A `Transform` schema, such as one built with `Schema#transformOrFail`, generates a value of the underlying type and then applies the transformation, and a sample whose transformation fails is dropped. A transformation that rejects most inputs makes the property slow, and one that rejects every input leaves `check` without a single sample: ZIO Test logs a warning that the generator did not produce any test cases, and the test may hang. Give such types a hand-written generator that produces only accepted values.
:::

## Going Further

The [Derivation of ZIO Test Generators](../derivations/zio-test-gen-derivation.md) page describes `DeriveGen.gen` on its own, and [Validating Types](../operations/validating-types.md) covers the `@validate` rules and `Schema.validate`. The [JSON](../derivations/codecs/json.md) and [Protobuf](../derivations/codecs/protobuf.md) codec pages describe how each format encodes options, sealed traits, and maps, which helps when a round trip fails on one format and passes on the other.
