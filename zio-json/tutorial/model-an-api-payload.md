# Tutorial: Model a Real API Payload

> Model an order webhook end to end: snake_case fields, a type discriminator, defaults, custom map keys, BigDecimal money, Instant timestamps and a golden test.

Welcome to the second ZIO JSON tutorial! It continues where [Getting Started](../getting-started.md) ends. You already know how to derive a codec, encode a value with `toJson` and decode one with `fromJson`. Here we use those skills on a payload that looks like the ones real services send, and we make the model match the JSON exactly, instead of making the JSON match the model.

Our example is an online shop that sends us webhook events about orders. A *webhook* is an HTTP request that another service sends to our server when something happens, with a JSON body describing the event. We do not control the format of that body, so our codecs must follow it.

## Learning Objectives

By the end of this tutorial, you will know how to:

- Rename every field of a case class to `snake_case` with one annotation
- Model a family of events that share a `type` field
- Use `BigDecimal` for money, `Instant` for timestamps, and defaults and `Option` for fields that may be missing
- Use your own type as a `Map` key
- Turn a decode error into a response instead of an exception
- Pin the JSON format with a golden test, so a later change cannot break it unnoticed

We work through these steps:

1. Give Product IDs Their Own Type
2. Model the Events
3. Decode the Sample Payload
4. Handle a Decode Error
5. Encode a Response
6. Pin the Format With a Golden Test

We recommend reading from top to bottom, because each step uses the code of the steps before it.

## The Payload

The shop sends three kinds of events. This is an `order_placed` event, the one we decode first:

```json
{
  "type": "order_placed",
  "order_id": "ord-1001",
  "customer_email": "ada@example.com",
  "items": { "sku-42": 2, "sku-7": 1 },
  "total": 59.90,
  "placed_at": "2026-03-14T09:26:53Z",
  "source": "web"
}
```

Notice four things about it. The field names use `snake_case`, while Scala uses `camelCase`. The `type` field says which kind of event this is. The `items` object maps product IDs to quantities. And the shop sends a `source` field that we do not need. The other two events, `order_shipped` and `order_cancelled`, follow the same pattern.

## 1. Give Product IDs Their Own Type

A product ID is a string, but not every string is a product ID. Let's give it a type of its own, so the compiler keeps it apart from order IDs and email addresses. The shop's product IDs always start with `sku-`, so we check that while decoding.

The `items` object uses product IDs as keys. A JSON key is always a string, and ZIO JSON reads and writes keys with two small type classes, `JsonFieldEncoder` and `JsonFieldDecoder`. We define both in the companion object of `ProductId`:

```scala
import zio.json._
import java.time.Instant

final case class ProductId(value: String)

object ProductId {
  private def parse(s: String): Either[String, ProductId] =
    if (s.startsWith("sku-")) Right(ProductId(s)) else Left(s"not a product id: $s")

  implicit val keyEncoder: JsonFieldEncoder[ProductId] =
    JsonFieldEncoder.string.contramap(_.value)

  implicit val keyDecoder: JsonFieldDecoder[ProductId] =
    JsonFieldDecoder.string.mapOrFail(parse)
}
```

The code above works like this:

- `import java.time.Instant` brings in the timestamp type we use in the next step.
- `ProductId.parse` accepts a string only when it starts with `sku-`, and returns a `Left` with a message otherwise.
- `JsonFieldEncoder.string.contramap(_.value)` reuses the encoder for `String` keys: it unwraps the `ProductId` first, then writes the string.
- `JsonFieldDecoder.string.mapOrFail(parse)` reads the key as a string, then runs `ProductId.parse`, so a key that is not a product ID fails decoding.

Let's try a map with these keys in both directions:

```scala
println(Map(ProductId("sku-42") -> 2, ProductId("sku-7") -> 1).toJson)
// {"sku-42":2,"sku-7":1}

"""{"sku-42":2,"oops":1}""".fromJson[Map[ProductId, Int]]
// res2: Either[String, Map[ProductId, Int]] = Left(
//   value = ".oops(not a product id: oops)"
// )
```

Notice that the error path names the bad key, `oops`, and is followed by our message. The [custom map keys guide](../how-to/custom-map-keys.md) shows more kinds of key types.

## 2. Model the Events

Now we model the three events as a sealed trait, so a single codec can read any of them. Three annotations make the JSON match the shop's format:

- `@jsonDiscriminator("type")` on the trait stores the kind of event in a `type` field inside the object, instead of the default wrapper object `{"OrderPlaced":{...}}` that you saw in Getting Started.
- `@jsonHintNames(SnakeCase)` on the trait turns each case name into `snake_case`, so `OrderPlaced` is written as `order_placed`.
- `@jsonMemberNames(SnakeCase)` on each case class turns every field name into `snake_case`, so `orderId` is written as `order_id`.

With those annotations in place, the whole model fits in one block:

```scala
@jsonDiscriminator("type")
@jsonHintNames(SnakeCase)
sealed trait OrderEvent {
  def orderId: String
}

object OrderEvent {
  @jsonMemberNames(SnakeCase)
  final case class OrderPlaced(
    orderId: String,
    customerEmail: String,
    items: Map[ProductId, Int],
    total: BigDecimal,
    currency: String = "EUR",
    placedAt: Instant,
    note: Option[String] = None
  ) extends OrderEvent

  @jsonMemberNames(SnakeCase)
  final case class OrderShipped(
    orderId: String,
    carrier: String,
    trackingNumber: Option[String],
    shippedAt: Instant
  ) extends OrderEvent

  @jsonMemberNames(SnakeCase)
  final case class OrderCancelled(orderId: String, reason: String) extends OrderEvent

  implicit val codec: JsonCodec[OrderEvent] = DeriveJsonCodec.gen[OrderEvent]
}
```

Here is what each part of the model does:

- `def orderId: String` on the trait lets us read the order ID of any event without matching on its kind. Each case class implements it with its `orderId` field.
- `items: Map[ProductId, Int]` uses the key codecs from step 1.
- `total: BigDecimal` holds money. A `Double` stores numbers in binary and cannot hold `59.90` exactly, while a `BigDecimal` keeps the decimal digits as they were sent, including the trailing zero.
- `placedAt: Instant` and `shippedAt: Instant` read timestamps written in the ISO-8601 format, such as `2026-03-14T09:26:53Z`. ZIO JSON has a built-in codec for `Instant` and the other `java.time` types.
- `currency: String = "EUR"` has a default value, which the decoder uses when the field is missing.
- `note: Option[String] = None` and `trackingNumber: Option[String]` may be missing. A missing field decodes as `None`, and the encoder leaves a `None` out.
- The single `DeriveJsonCodec.gen[OrderEvent]` derives the codec of the trait together with the codecs of its three cases.

Let's encode an event to see the result:

```scala
val placed: OrderEvent = OrderEvent.OrderPlaced(
  orderId = "ord-1001",
  customerEmail = "ada@example.com",
  items = Map(ProductId("sku-42") -> 2),
  total = BigDecimal("59.90"),
  placedAt = Instant.parse("2026-03-14T09:26:53Z")
)
// placed: OrderEvent = OrderPlaced(
//   orderId = "ord-1001",
//   customerEmail = "ada@example.com",
//   items = Map(ProductId(value = "sku-42") -> 2),
//   total = 59.90,
//   currency = "EUR",
//   placedAt = 2026-03-14T09:26:53Z,
//   note = None
// )

println(placed.toJsonPretty)
// {
//   "type" : "order_placed",
//   "order_id" : "ord-1001",
//   "customer_email" : "ada@example.com",
//   "items" : {
//     "sku-42" : 2
//   },
//   "total" : 59.90,
//   "currency" : "EUR",
//   "placed_at" : "2026-03-14T09:26:53Z"
// }
```

Notice that `type` comes first, every field name is in `snake_case`, the total keeps its trailing zero, and `note` is missing because it is `None`. We typed `placed` as `OrderEvent` on purpose: the codec belongs to the trait, so we encode and decode through the trait.

## 3. Decode the Sample Payload

Now let's decode the payload from the start of this tutorial:

```scala
val payload =
  """{
    |  "type": "order_placed",
    |  "order_id": "ord-1001",
    |  "customer_email": "ada@example.com",
    |  "items": { "sku-42": 2, "sku-7": 1 },
    |  "total": 59.90,
    |  "placed_at": "2026-03-14T09:26:53Z",
    |  "source": "web"
    |}""".stripMargin
// payload: String = """{
//   "type": "order_placed",
//   "order_id": "ord-1001",
//   "customer_email": "ada@example.com",
//   "items": { "sku-42": 2, "sku-7": 1 },
//   "total": 59.90,
//   "placed_at": "2026-03-14T09:26:53Z",
//   "source": "web"
// }"""

payload.fromJson[OrderEvent]
// res4: Either[String, OrderEvent] = Right(
//   value = OrderPlaced(
//     orderId = "ord-1001",
//     customerEmail = "ada@example.com",
//     items = Map(
//       ProductId(value = "sku-42") -> 2,
//       ProductId(value = "sku-7") -> 1
//     ),
//     total = 59.90,
//     currency = "EUR",
//     placedAt = 2026-03-14T09:26:53Z,
//     note = None
//   )
// )
```

The decoder uses the `type` field to pick `OrderPlaced`. It fills in `currency` with the default `"EUR"` and `note` with `None`, because the payload has neither field. It also skips the `source` field, which our model does not have. Skipping unknown fields is the default, and it lets the shop add new fields without breaking our server. [How Strict Is the Decoder](../explanation/decoder-strictness.md) explains what else the decoder tolerates.

The other events decode with the same codec. This `order_shipped` event has no tracking number yet:

```scala
"""{"type":"order_shipped","order_id":"ord-1001","carrier":"DHL","shipped_at":"2026-03-15T08:00:00Z"}""".fromJson[OrderEvent]
// res5: Either[String, OrderEvent] = Right(
//   value = OrderShipped(
//     orderId = "ord-1001",
//     carrier = "DHL",
//     trackingNumber = None,
//     shippedAt = 2026-03-15T08:00:00Z
//   )
// )
```

## 4. Handle a Decode Error

Real webhooks sometimes carry data our model does not accept. As in Getting Started, a bad payload gives a `Left` with a message, never an exception. Let's look at four typical mistakes.

A kind of event that our model does not know:

```scala
"""{"type":"order_refunded","order_id":"ord-1001"}""".fromJson[OrderEvent]
// res6: Either[String, OrderEvent] = Left(value = "(invalid disambiguator)")
```

A timestamp that is not in the ISO-8601 format:

```scala
"""{"type":"order_placed","order_id":"ord-1001","customer_email":"ada@example.com","items":{},"total":10,"placed_at":"yesterday"}""".fromJson[OrderEvent]
// res7: Either[String, OrderEvent] = Left(
//   value = "(order_placed).placed_at(expected an Instant)"
// )
```

A product ID that our key decoder rejects:

```scala
"""{"type":"order_placed","order_id":"ord-1001","customer_email":"ada@example.com","items":{"42":1},"total":10,"placed_at":"2026-03-14T09:26:53Z"}""".fromJson[OrderEvent]
// res8: Either[String, OrderEvent] = Left(
//   value = "(order_placed).items.42(not a product id: 42)"
// )
```

A required field that is missing:

```scala
"""{"type":"order_placed","order_id":"ord-1001","items":{},"total":10,"placed_at":"2026-03-14T09:26:53Z"}""".fromJson[OrderEvent]
// res9: Either[String, OrderEvent] = Left(
//   value = "(order_placed).customer_email(missing)"
// )
```

Each message starts with the kind of event in parentheses, such as `(order_placed)`, then gives the path to the bad field, then says what went wrong. Notice that the path uses the JSON names, `placed_at` and `customer_email`, so you can match it against the payload directly. An unknown `type` value gives `(invalid disambiguator)`, which does not repeat the value, so log the payload as well when you report it.

## 5. Encode a Response

Our server answers each webhook with a small JSON body that says whether we accepted the event. The shop expects `snake_case` here too:

```scala
@jsonMemberNames(SnakeCase)
final case class WebhookAck(accepted: Boolean, orderId: Option[String], error: Option[String] = None)

object WebhookAck {
  implicit val codec: JsonCodec[WebhookAck] = DeriveJsonCodec.gen[WebhookAck]
}

def respond(body: String): WebhookAck =
  body.fromJson[OrderEvent] match {
    case Right(event) => WebhookAck(accepted = true, orderId = Some(event.orderId))
    case Left(error)  => WebhookAck(accepted = false, orderId = None, error = Some(error))
  }
```

The code above works like this:

- `WebhookAck` is a plain case class. It only needs to be encoded, but a `JsonCodec` is just as easy to derive and lets tests decode it too.
- `respond` decodes the request body and matches on the result. A `Right` gives an event, and `event.orderId` works for every kind of event thanks to the `def` on the trait.
- A `Left` becomes a rejected acknowledgement that carries the decode error.

Let's respond to the good payload and to a bad one:

```scala
println(respond(payload).toJson)
// {"accepted":true,"order_id":"ord-1001"}

println(respond("""{"type":"order_refunded","order_id":"ord-1001"}""").toJson)
// {"accepted":false,"error":"(invalid disambiguator)"}
```

Notice that `order_id` is in `snake_case`, and that the `None` fields are left out of each response.

## 6. Pin the Format With a Golden Test

Our codecs now match the shop's format. The danger is a later change that alters the JSON by accident: renaming a case class renames its `type` value, and removing an annotation changes every field name. The code still compiles, and decoding our own output still works, so ordinary tests stay green while the shop's requests start to fail.

A *golden test* guards against this. It encodes a set of sample values, stores the JSON in a file the first time it runs, and fails whenever the output differs from the stored file later. The `zio-json-golden` module provides it for [ZIO Test](https://zio.dev/reference/test/).

Add the module to `build.sbt`. It brings ZIO Test with it, so the only other line tells sbt to run ZIO Test specs:

```sbt
libraryDependencies += "dev.zio" %% "zio-json-golden" % "1.1.3" % Test

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
```

Then write the test in `src/test/scala`. We list one sample of each event, so the stored file is small and easy to review:

```scala
import zio.test._
import zio.json.golden._

object OrderEventGoldenSpec extends ZIOSpecDefault {

  val samples: List[OrderEvent] = List(
    OrderEvent.OrderPlaced(
      orderId = "ord-1001",
      customerEmail = "ada@example.com",
      items = Map(ProductId("sku-42") -> 2, ProductId("sku-7") -> 1),
      total = BigDecimal("59.90"),
      placedAt = Instant.parse("2026-03-14T09:26:53Z")
    ),
    OrderEvent.OrderShipped("ord-1001", "DHL", Some("JD0123"), Instant.parse("2026-03-15T08:00:00Z")),
    OrderEvent.OrderCancelled("ord-1002", "customer request")
  )

  implicit val config: GoldenConfiguration =
    GoldenConfiguration.default.copy(sampleSize = samples.size)

  def spec = suite("OrderEventGoldenSpec")(
    goldenTest(Gen.fromIterable(samples))
  )
}
```

The test works like this:

- `samples` holds fixed values. A golden test needs the same samples on every run, so we avoid random or clock-based values.
- `Gen.fromIterable(samples)` turns the list into a ZIO Test generator, which is what `goldenTest` expects.
- `GoldenConfiguration.default.copy(sampleSize = samples.size)` takes exactly our three samples instead of the default 20.
- `goldenTest` encodes the samples with the `OrderEvent` codec and compares them with the file `src/test/resources/golden/OrderEvent.json`.

The first run has no stored file yet. It writes `OrderEvent_new.json` and fails with a message asking you to accept it. Review the file, remove the `_new` suffix, and run the test again. Our file looks like this, and the test now passes:

```json
{
  "samples" : [
    {
      "type" : "order_placed",
      "order_id" : "ord-1001",
      "customer_email" : "ada@example.com",
      "items" : {
        "sku-42" : 2,
        "sku-7" : 1
      },
      "total" : 59.90,
      "currency" : "EUR",
      "placed_at" : "2026-03-14T09:26:53Z"
    },
    {
      "type" : "order_shipped",
      "order_id" : "ord-1001",
      "carrier" : "DHL",
      "tracking_number" : "JD0123",
      "shipped_at" : "2026-03-15T08:00:00Z"
    },
    {
      "type" : "order_cancelled",
      "order_id" : "ord-1002",
      "reason" : "customer request"
    }
  ]
}
```

Try changing `@jsonHintNames(SnakeCase)` to `@jsonHintNames(KebabCase)` and running the test again. It fails, shows that `"order_placed"` became `"order-placed"`, and writes the new output to `OrderEvent_changed.json` so you can compare. That is exactly the kind of change the shop would notice first if the test did not. Commit the golden file together with the code.

## What You've Learned

In this tutorial, you learned how to:

- Rename all fields of a case class with `@jsonMemberNames(SnakeCase)`, and all case names with `@jsonHintNames(SnakeCase)`
- Model a family of events with a `type` field using `@jsonDiscriminator`
- Use `BigDecimal` for money and `Instant` for timestamps, and handle missing fields with defaults and `Option`
- Write key codecs so your own type can be a `Map` key, with validation
- Turn a decode error into a response, and read the path in the error message
- Pin the JSON format with a golden test

You can now model a third-party JSON format end to end and keep it stable as your code changes.

## Where to Go Next

- **Need other names or rules?** [Field Names](../how-to/field-names.md) covers single renames and old names with `@jsonAliases`, and [Work With Sealed Traits](../how-to/sealed-traits.md) covers other hint formats.
- **Fields that can be `null` or empty?** [Optional, Default and Null Fields](../how-to/optional-and-default-fields.md) explains every rule for missing, `null` and empty values.
- **Rules beyond types?** [Validate on Decode](../how-to/validate-on-decode.md) shows how to check values and fields while decoding.
- **More about golden tests?** [Golden Testing API](../reference/golden-testing.md) covers the configuration and the file lifecycle.
- **Want to see every shape a sum type can take?** [JSON Shapes and Round Trips](../explanation/wire-format.md) has the full table and the values that do not survive a round trip.
- **Looking for an API?** [JsonCodecConfiguration](../reference/json-codec-configuration.md) lists every derivation option, and [Built-in Instances](../reference/built-in-instances.md) lists every type that has a codec out of the box.
