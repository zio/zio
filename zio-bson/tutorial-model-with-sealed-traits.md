# Tutorial: Model a Domain with Sealed Traits

> In this tutorial, we will model a small payment domain with a sealed trait, derive a codec for it, and look at the BSON that each case produces. Then we will read documents back, including a bad one, and change the encoding with a discriminator field.

By the end of this tutorial, you will know how a sealed trait becomes a BSON document, how to read it back, and how to control the shape of the result. You do not need a MongoDB server. If you have not derived a codec before, start with [Store and Load a Case Class](./tutorial-store-and-load.md).

## Step 1: Define the Domain

A payment is either a card payment or a bank transfer. We model it as a sealed trait with one case class per kind, and we derive the codec in the companion object of the trait:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
import zio.bson.magnolia._

sealed trait Payment

object Payment {
  final case class Card(last4: String, cents: Long) extends Payment
  final case class Transfer(iban: String)           extends Payment

  implicit val codec: BsonCodec[Payment] = DeriveBsonCodec.derive
}
```

## Step 2: Encode Each Case

To see what the database would store, we convert two payments with `toBsonValue`:

```scala
val card: Payment     = Payment.Card("4242", 1999L)
// card: Payment = Card(last4 = "4242", cents = 1999L)
val transfer: Payment = Payment.Transfer("DE89370400440532013000")
// transfer: Payment = Transfer(iban = "DE89370400440532013000")

println(card.toBsonValue)
// {"Card": {"last4": "4242", "cents": 1999}}
println(transfer.toBsonValue)
// {"Transfer": {"iban": "DE89370400440532013000"}}
```

By default, each document has a single key, the name of the case class, and the value holds its fields. This is the `WrapperWithClassNameField` encoding.

## Step 3: Decode a Document

To read a document back, we use `as` with the type of the trait. The decoder picks the case class from the key:

```scala
doc("Transfer" -> doc("iban" -> str("NL91ABNA0417164300"))).as[Payment]
// res2: Either[String, Payment] = Right(
//   value = Transfer(iban = "NL91ABNA0417164300")
// )
```

Next, we try a document whose key names no case class of the trait:

```scala
doc("Cash" -> doc("amount" -> int(5))).as[Payment]
// res3: Either[String, Payment] = Left(
//   value = ".Cash: Invalid disambiguator Cash."
// )
```

The error says which key is not a known case, so a typo in stored data is easy to find.

## Step 4: Use a Discriminator Field

Many applications keep the case name inside the document as a field instead. To do that, we annotate the sealed trait with `bsonDiscriminator`:

```scala
@bsonDiscriminator("type")
sealed trait Refund

object Refund {
  final case class Full(orderId: String)                  extends Refund
  final case class Partial(orderId: String, cents: Long)  extends Refund

  implicit val codec: BsonCodec[Refund] = DeriveBsonCodec.derive
}
```

The case class fields and the `type` field now share one document:

```scala
val refund: Refund = Refund.Partial("o-1", 250L)
// refund: Refund = Partial(orderId = "o-1", cents = 250L)

println(refund.toBsonValue)
// {"orderId": "o-1", "cents": 250, "type": "Partial"}

println(refund.toBsonValue.as[Refund])
// Right(Partial(o-1,250))
```

## What We Learned

A sealed trait is encoded with one document per value, either keyed by the case name or tagged with a discriminator field. Decoding mirrors encoding, and a document that matches no case produces an error. To rename cases and fields, or to change the encoding for the whole codebase, see [Customize Field Names and Discriminators](./how-to-customize-field-names-and-discriminators.md) and [Configuration and Annotations](./bson-codec-configuration.md).
