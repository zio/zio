# Encode a Type You Do Not Own

> Write a JSON encoder, decoder, or codec for a class from a library you cannot change, and place it where the compiler finds it.

Goal: use a class from another library as a field in your case class, even though that library gives it no JSON support and you cannot edit its source.

ZIO JSON already covers common library types such as `java.util.UUID`, `java.time.Instant`, and `BigDecimal` (see [Built-in Instances](../reference/built-in-instances.md)). For anything else you supply the instances yourself. The example below uses a `Money` class that stands in for a type from a library. The `vendor` object plays the role of that library:

```scala
import zio.json._

object vendor {
  final class Money(val cents: Long, val currency: String) {
    override def toString: String = s"Money($cents, $currency)"
  }

  object Money {
    def apply(cents: Long, currency: String): Money = new Money(cents, currency)
  }
}

import vendor.Money
```

`Money` is not a case class, so derivation cannot see inside it. You must say how it looks in JSON. Here it will be one string, such as `"1250 USD"`.

## 1. Write the conversion functions

Two small functions turn a `Money` into text and back. The parser returns an `Either`, so it can say why the text is bad:

```scala
def formatMoney(m: Money): String = s"${m.cents} ${m.currency}"

def parseMoney(s: String): Either[String, Money] =
  s.split(" ") match {
    case Array(amount, currency) =>
      amount.toLongOption.map(Money(_, currency)).toRight(s"invalid amount: $amount")
    case _ =>
      Left("expected '<cents> <currency>'")
  }
```

## 2. Build the encoder with contramap

`JsonEncoder#contramap` adapts an existing encoder to a new type. You give it a function from your type to a type that already has an encoder. Here that is `Money` to `String`:

```scala
val moneyEncoder: JsonEncoder[Money] = JsonEncoder[String].contramap(formatMoney)
```

## 3. Build the decoder with mapOrFail

`JsonDecoder#mapOrFail` adapts an existing decoder. The function may fail with a message, which becomes a decode error:

```scala
val moneyDecoder: JsonDecoder[Money] = JsonDecoder[String].mapOrFail(parseMoney)
```

Test each half on its own:

```scala
println(moneyEncoder.encodeJson(Money(1250, "USD"), None))
// "1250 USD"

moneyDecoder.decodeJson("\"1250 USD\"")
// res2: Either[String, Money] = Right(value = Money(1250, USD))

moneyDecoder.decodeJson("\"12x USD\"")
// res3: Either[String, Money] = Left(value = "(invalid amount: 12x)")
```

## 4. Put the instances where the compiler finds them

The compiler looks for an implicit `JsonEncoder[Money]` in two places: the scope around the call, and the companions of the types involved. The companion of `Money` belongs to the other library, so you cannot add anything there. You have two options.

The first is the companion of a type you own. Put the instances next to the case class that uses `Money`, and define them before the derived codec:

```scala
final case class Invoice(id: String, total: Money)

object Invoice {
  implicit val encodeMoney: JsonEncoder[Money] = moneyEncoder
  implicit val decodeMoney: JsonDecoder[Money] = moneyDecoder

  implicit val codec: JsonCodec[Invoice] = DeriveJsonCodec.gen[Invoice]
}
```

The derived `Invoice` codec now writes `total` as one string, and a bad amount fails the decode:

```scala
val invoice = Invoice("A-1", Money(1250, "USD"))
// invoice: Invoice = Invoice(id = "A-1", total = Money(1250, USD))

println(invoice.toJson)
// {"id":"A-1","total":"1250 USD"}

"""{"id":"A-1","total":"1250 USD"}""".fromJson[Invoice]
// res5: Either[String, Invoice] = Right(
//   value = Invoice(id = "A-1", total = Money(1250, USD))
// )

"""{"id":"A-1","total":"abc USD"}""".fromJson[Invoice]
// res6: Either[String, Invoice] = Left(value = ".total(invalid amount: abc)")
```

The error path `.total` comes from the field that held the bad value, and the text in parentheses is your `Left` message.

The second option is a shared object that other files import. Use it when many of your types hold a `Money` and you do not want to repeat the instances.

## 5. Bundle both halves into a codec

A `JsonCodec[A]` is one value holding an encoder and a decoder. Make one with `JsonCodec(encoder, decoder)`, or start from a codec that exists and call `JsonCodec#transformOrFail`. Its two functions are the parse step, which may fail, and the format step, which cannot. Here the codec goes in a `MoneyCodecs` object that other files can import:

```scala
object MoneyCodecs {
  implicit val moneyCodec: JsonCodec[Money] =
    JsonCodec[String].transformOrFail(parseMoney, formatMoney)
}
```

`JsonCodec(moneyEncoder, moneyDecoder)` gives an equivalent result. Importing the codec makes both the encoder and the decoder available, because a codec in scope also serves as an encoder and a decoder:

```scala
final case class Receipt(number: Int, paid: Money)

object Receipt {
  import MoneyCodecs._

  implicit val codec: JsonCodec[Receipt] = DeriveJsonCodec.gen[Receipt]
}
```

The `Receipt` codec writes `paid` with the imported codec:

```scala
println(Receipt(7, Money(300, "EUR")).toJson)
// {"number":7,"paid":"300 EUR"}
```

## 6. Choose a codec or separate instances

| Choice                | Gives you                    | Use it when                                  |
|-----------------------|------------------------------|----------------------------------------------|
| `JsonEncoder` only    | Writing JSON                 | The type is only sent out, never read        |
| `JsonDecoder` only    | Reading JSON                 | The type is only received, never written     |
| `JsonCodec`           | Both, kept together          | The type is used in both directions          |

A codec keeps the two halves side by side, so a change to the format is made in one place. Separate instances let you supply only the direction you need. Pick one style per type, so that only one instance of each type is in scope.

## 7. Use the type as a map key

A `Map[Money, Int]` needs different instances. JSON object keys are always strings, so a key is written by a `JsonFieldEncoder` and read by a `JsonFieldDecoder`, not by `JsonEncoder` and `JsonDecoder`. They are built the same way, from the `String` instances, with `JsonFieldEncoder#contramap` and `JsonFieldDecoder#mapOrFail`:

```scala
object MoneyKeys {
  implicit val moneyKeyEncoder: JsonFieldEncoder[Money] =
    JsonFieldEncoder[String].contramap(formatMoney)

  implicit val moneyKeyDecoder: JsonFieldDecoder[Money] =
    JsonFieldDecoder[String].mapOrFail(parseMoney)
}
```

With the key instances imported, maps keyed by `Money` encode and decode, and a bad key fails with your message:

```scala
import MoneyKeys._

println(Map(Money(500, "USD") -> 3).toJson)
// {"500 USD":3}

"""{"500 USD":3}""".fromJson[Map[Money, Int]]
// res9: Either[String, Map[Money, Int]] = Right(
//   value = Map(Money(500, USD) -> 3)
// )

"""{"5x USD":3}""".fromJson[Map[Money, Int]]
// res10: Either[String, Map[Money, Int]] = Left(
//   value = ".5x USD(invalid amount: 5x)"
// )
```

See [JsonFieldEncoder and JsonFieldDecoder](../reference/json-field-codecs.md) for the built-in key types, and [Use Your Own Type as a Map Key](custom-map-keys.md) for more key types.

## See also

- [Build an encoder or decoder from an existing one](./manual-instances.md) shows more ways to build instances, such as `JsonDecoder#map` and tuple encodings.
- [JsonEncoder reference](../reference/json-encoder.md) and [JsonDecoder reference](../reference/json-decoder.md) describe `JsonEncoder#contramap`, `JsonDecoder#map` and `JsonDecoder#mapOrFail` in full.
- [JsonCodec reference](../reference/json-codec.md) lists the combinators on `JsonCodec`.
