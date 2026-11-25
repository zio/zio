# JsonFieldEncoder and JsonFieldDecoder

> Reference for the map-key type classes JsonFieldEncoder and JsonFieldDecoder: built-in key types, contramap, map and mapOrFail, and how map keys behave.

`JsonFieldEncoder[A]` and `JsonFieldDecoder[A]` are the type classes that turn a map key of type `A` into a JSON object key and back.
A JSON object key is always a string, so a `Map[K, V]` can be written as a JSON object only when there is a way to turn each `K` into a `String`, and read back only when there is a way to turn the `String` into a `K`.
`JsonFieldEncoder[K]` and `JsonFieldDecoder[K]` are those two ways.
The values of the map still use the ordinary [`JsonEncoder`](json-encoder.md) and [`JsonDecoder`](json-decoder.md).

Both traits have one abstract method, and the combinators build new instances from existing ones:

```scala
trait JsonFieldEncoder[-A] {
  def unsafeEncodeField(in: A): String
  def contramap[B](f: B => A): JsonFieldEncoder[B]
}

trait JsonFieldDecoder[+A] {
  def unsafeDecodeField(trace: List[JsonError], in: String): A
  def map[B](f: A => B): JsonFieldDecoder[B]
  def mapOrFail[B](f: A => Either[String, B]): JsonFieldDecoder[B]
}
```

`JsonFieldEncoder` is contravariant in `A`, so a `JsonFieldEncoder[String]` also serves any subtype of `String`.
`JsonFieldDecoder` is covariant in `A`.
Both live in the `zio.json` package.

## Usage

Built-in key types need nothing extra, and a key of your own type needs one `JsonFieldEncoder` and one `JsonFieldDecoder`, usually built from the `String` instances:

```scala
import zio.json._

final case class ProductId(value: String)

object ProductId {
  implicit val fieldEncoder: JsonFieldEncoder[ProductId] =
    JsonFieldEncoder[String].contramap(_.value)
  implicit val fieldDecoder: JsonFieldDecoder[ProductId] =
    JsonFieldDecoder[String].map(ProductId(_))
}

Map(1 -> "one", 2 -> "two").toJson
// res1: String = "{\"1\":\"one\",\"2\":\"two\"}"

"""{"1":"one","2":"two"}""".fromJson[Map[Int, String]]
// res2: Either[String, Map[Int, String]] = Right(
//   value = Map(1 -> "one", 2 -> "two")
// )

Map(ProductId("p-1") -> 3).toJson
// res3: String = "{\"p-1\":3}"

"""{"p-1":3}""".fromJson[Map[ProductId, Int]]
// res4: Either[String, Map[ProductId, Int]] = Right(
//   value = Map(ProductId(value = "p-1") -> 3)
// )
```

## When the Compiler Needs One

The encoders and decoders for these types take a `JsonFieldEncoder[K]` or a `JsonFieldDecoder[K]` for the key type `K`:

| Type                         | Notes                                                                                   |
|------------------------------|-----------------------------------------------------------------------------------------|
| `Map[K, V]`                  | Encoder, decoder and `JsonCodec`.                                                       |
| `immutable.HashMap[K, V]`    | Encoder, decoder and `JsonCodec`.                                                       |
| `immutable.ListMap[K, V]`    | Encoder, decoder and `JsonCodec`. Keeps the key order.                                  |
| `collection.SortedMap[K, V]` | Encoder, decoder and `JsonCodec`. The decoder and the codec also need an `Ordering[K]`. |
| `mutable.Map[K, V]`          | Encoder and decoder.                                                                    |
| `Chunk[(K, V)]`              | `JsonEncoder.keyValueChunk` and `JsonDecoder.keyValueChunk`, which are not implicit.    |

A derived case class with a `Map` field needs the key instances for that field too.
When the key instance is missing, the compiler reports the missing `JsonEncoder` or `JsonDecoder` for the whole map, not the missing key instance, which can be confusing:

```scala
import java.time.LocalDate

Map(LocalDate.of(2024, 1, 1) -> 1).toJson
// error: could not find implicit value for parameter encoder: zio.json.JsonEncoder[scala.collection.immutable.Map[java.time.LocalDate,Int]]
// Map(LocalDate.of(2024, 1, 1) -> 1).toJson
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
```

The fix is to provide a `JsonFieldEncoder[LocalDate]`, as shown in [Use Your Own Type as a Map Key](../how-to/custom-map-keys.md).

## Predefined Instances

The companion objects provide instances for four key types only:

| Key type         | `JsonFieldEncoder` | `JsonFieldDecoder` | Key decoding                                                                              |
|------------------|--------------------|--------------------|-------------------------------------------------------------------------------------------|
| `String`         | `string`           | `string`           | The key as it is.                                                                         |
| `Int`            | `int`              | `int`              | `String#toInt`. A key that is not an `Int` fails with `Invalid Int: <key>`.               |
| `Long`           | `long`             | `long`             | `String#toLong`. A key that is not a `Long` fails with `Invalid Long: <key>`.             |
| `java.util.UUID` | `uuid`             | `uuid`             | The same parser as the `UUID` value decoder. An invalid key fails with `expected a UUID`. |

Every other key type, including `Short`, `Boolean`, `LocalDate`, enumerations and wrapper types, needs an instance of your own.
The `Invalid Int` and `Invalid Long` messages cut keys longer than 50 characters off with `...`.

## Scala 3: Keys That Are Subtypes of `String`

On Scala 3, `JsonFieldDecoder` has one more, low-priority instance: any type `T <: String` that has a `JsonDecoder[T]` gets a `JsonFieldDecoder[T]`.
It puts the key between quotes and decodes the result with that `JsonDecoder`, as if the key were a JSON string value. The key is not escaped again first, so a key that contains `"` or `\` does not decode correctly.
The encoding side needs nothing extra, because `JsonFieldEncoder[String]` already serves every subtype of `String`.
A union of string literals, which has a built-in `JsonDecoder` on Scala 3, therefore works as a key type with no extra code:

```scala
type Env = "dev" | "prod"

final case class Replicas(byEnv: Map[Env, Int]) derives JsonCodec
```

`Replicas(Map("dev" -> 1, "prod" -> 3))` encodes to `{"byEnv":{"dev":1,"prod":3}}` and decodes back, while the key `test` fails with `.byEnv.test(expected one of: dev, prod)`.

This instance is Scala 3 only.
On Scala 2, `JsonFieldDecoder` has no instance beyond the four in the table.

## Encoding Keys

A `JsonFieldEncoder` turns a key into the string that is written, quoted and escaped, as the JSON object key.

`JsonFieldEncoder#unsafeEncodeField` is the one abstract method.
`JsonFieldEncoder#contramap` builds an encoder for `B` from an encoder for `A` and a function `B => A`.
`JsonFieldEncoder.apply` returns the instance in implicit scope:

```scala
trait JsonFieldEncoder[-A] {
  def unsafeEncodeField(in: A): String
  def contramap[B](f: B => A): JsonFieldEncoder[B]
}

object JsonFieldEncoder {
  def apply[A](implicit a: JsonFieldEncoder[A]): JsonFieldEncoder[A]
}
```

`JsonFieldEncoder#contramap` is the usual way to build one, and calling `JsonFieldEncoder#unsafeEncodeField` directly shows the string it produces:

```scala
import zio.json._

final case class Sku(code: String)

val skuEncoder: JsonFieldEncoder[Sku] = JsonFieldEncoder[String].contramap(_.code.toUpperCase)
```

The function runs on every key:

```scala
skuEncoder.unsafeEncodeField(Sku("ab-12"))
// res8: String = "AB-12"

JsonFieldEncoder[Long].unsafeEncodeField(42L)
// res9: String = "42"
```

## Decoding Keys

A `JsonFieldDecoder` turns the unescaped key string back into a value, or fails.

`JsonFieldDecoder#unsafeDecodeField` is the one abstract method.
`JsonFieldDecoder#map` transforms the decoded key, and `JsonFieldDecoder#mapOrFail` transforms it with a function that can return a `Left` error message.
`JsonFieldDecoder.apply` returns the instance in implicit scope:

```scala
trait JsonFieldDecoder[+A] {
  def unsafeDecodeField(trace: List[JsonError], in: String): A
  def map[B](f: A => B): JsonFieldDecoder[B]
  def mapOrFail[B](f: A => Either[String, B]): JsonFieldDecoder[B]
}

object JsonFieldDecoder {
  def apply[A](implicit a: JsonFieldDecoder[A]): JsonFieldDecoder[A]
}
```

`unsafeDecodeField` fails by throwing, like `JsonDecoder#unsafeDecode`: it calls `Lexer.error(message, trace)` from `zio.json.internal`.
`trace` already includes the key, so the error renders at the key's path, for example `.theKey(Invalid Int: theKey)`.
`mapOrFail` does this for you, so prefer it over implementing `unsafeDecodeField` directly.

A key decoder that accepts only even numbers:

```scala
val evenKey: JsonFieldDecoder[Int] =
  JsonFieldDecoder[Int].mapOrFail(n => if (n % 2 == 0) Right(n) else Left(s"odd key: $n"))
```

The `Left` becomes the error at the key's path:

```scala
"""{"2":"a","3":"b"}""".fromJson[Map[Int, String]](JsonDecoder.map(evenKey, JsonDecoder[String]))
// res10: Either[String, Map[Int, String]] = Left(value = ".3(odd key: 3)")
```

To use a type of your own as a key, such as an enumeration, a wrapper type or `java.time.LocalDate`, see [Use Your Own Type as a Map Key](../how-to/custom-map-keys.md).

## Behaviour Notes

These details follow from how the map encoders and decoders use the key instances.

- **Duplicate keys: the last one wins.** Decoding into a `Map` builds it entry by entry, so a key that appears twice keeps its last value and no error is reported. Keys that decode to the same value collide the same way: `{"5":1,"05":2}` as `Map[Int, Int]` is `Map(5 -> 2)`. To keep every entry, decode with `JsonDecoder.keyValueChunk` or into a [`Json.Obj`](json-ast.md).
- **`Int` and `Long` keys are parsed with `String#toInt` and `String#toLong`.** These accept a leading `+`, so `{"+5":1}` as `Map[Int, Int]` is `Map(5 -> 1)`, even though `+5` is not a valid JSON number and `"+5".fromJson[Int]` fails.
- **Entries whose value is `None` are dropped when encoding.** The map encoder skips every entry whose value its `JsonEncoder` reports as nothing, so `Map("a" -> None, "b" -> Some(1)).toJson` is `{"b":1}`. Decoding that JSON back gives a map without the `a` key.
- **Keys are written in the map's iteration order.** Use a `ListMap` or a `SortedMap` when the order of the keys in the output matters.

A repeated key and a `None` value show the first and third rules:

```scala
"""{"a":1,"b":2,"a":3}""".fromJson[Map[String, Int]]
// res11: Either[String, Map[String, Int]] = Right(
//   value = Map("a" -> 3, "b" -> 2)
// )

Map("a" -> None, "b" -> Some(1)).toJson
// res12: String = "{\"b\":1}"
```

## Interop Instances

Two interop modules provide key instances:

- **Enumeratum** ([Enumeratum Interop](../interop/enumeratum.md)). `enumeratum.ZioJson.keyEncoder(MyEnum)` and `enumeratum.ZioJson.keyDecoder(MyEnum)` build key instances that use the entry name, and mixing `ZioJsonKeyEnum` into the `Enum` object provides them implicitly. For value enums, `enumeratum.values.ZioJson.keyEncoder` and `enumeratum.values.ZioJson.keyDecoder` use the entry's value, and the `IntZioJsonEnum`, `StringZioJsonEnum` and sibling traits provide them implicitly.
- **Refined** ([Refined Interop](../interop/refined.md)). `encodeFieldRefined` and `decodeFieldRefined` in `zio.json.interop.refined` derive key instances for `A Refined P` from those for `A`. The decoder checks the predicate and fails with refined's message.

## See Also

- [Use Your Own Type as a Map Key](../how-to/custom-map-keys.md) for a step-by-step guide
- [JsonEncoder](json-encoder.md) and [JsonDecoder](json-decoder.md) for the value type classes
- [JsonCodec](json-codec.md) for the codec instances of the map types
- [Errors](errors.md) for how key errors are rendered
- [Built-in Instances](built-in-instances.md) for every type that has a value encoder or decoder
