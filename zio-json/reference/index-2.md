# Type Classes

> The ZIO JSON type classes: JsonEncoder, JsonDecoder, JsonCodec and the map-key type classes JsonFieldEncoder and JsonFieldDecoder, how they relate, and where instances come from.

ZIO JSON is built on a few *type classes*: a trait with one type parameter, such as `JsonEncoder[A]`, plus values (instances) of that trait for each type you use. The compiler finds the instance for a type through implicit search, so `toJson` and `fromJson` work for any type that has one.

| Type class                  | What an instance does                              | Page                                                         |
|-----------------------------|----------------------------------------------------|--------------------------------------------------------------|
| `JsonEncoder[A]`            | Writes an `A` as JSON                              | [JsonEncoder](../json-encoder.md)                            |
| `JsonDecoder[A]`            | Reads JSON into an `A`, or fails with an error     | [JsonDecoder](../json-decoder.md)                            |
| `JsonCodec[A]`              | An encoder and a decoder for `A` in one value      | [JsonCodec](../json-codec.md)                                |
| `JsonFieldEncoder[A]`       | Writes an `A` as a JSON object key                 | [JsonFieldEncoder and JsonFieldDecoder](../json-field-codecs.md) |
| `JsonFieldDecoder[A]`       | Reads a JSON object key into an `A`                | [JsonFieldEncoder and JsonFieldDecoder](../json-field-codecs.md) |

Two more pages belong to this group: [Built-in Instances](../built-in-instances.md) lists every type that has instances out of the box, and [Errors](../errors.md) describes what a failed decode returns.

## How They Relate

A `JsonCodec[A]` holds a `JsonEncoder[A]` and a `JsonDecoder[A]`. When a codec is in implicit scope, the compiler also finds the encoder and the decoder through it, so one codec is enough for both directions. Each type class has an `apply` method that returns the instance in scope:

```scala
import zio.json._

final case class Point(x: Int, y: Int)

object Point {
  implicit val codec: JsonCodec[Point] = DeriveJsonCodec.gen[Point]
}

val encoder: JsonEncoder[Point] = JsonEncoder[Point]
// encoder: JsonEncoder[Point] = zio.json.DeriveJsonEncoder$$anon$10@a9cee31
val decoder: JsonDecoder[Point] = JsonDecoder[Point]
// decoder: JsonDecoder[Point] = zio.json.DeriveJsonDecoder$$anon$1@3d100eea

println(encoder.encodeJson(Point(1, 2), None))
// {"x":1,"y":2}

decoder.decodeJson("""{"x":1,"y":2}""")
// res2: Either[String, Point] = Right(value = Point(x = 1, y = 2))
```

`JsonFieldEncoder` and `JsonFieldDecoder` are separate, because a JSON object key is always a string. The `Map` encoders and decoders use them for the keys and a `JsonEncoder` or `JsonDecoder` for the values:

```scala
println(Map(1 -> Point(1, 2)).toJson)
// {"1":{"x":1,"y":2}}
```

## Where Instances Come From

The compiler looks for an instance in four places, and the reference page of each type class describes them in detail:

- **Built in.** The companion objects hold instances for the standard types: primitives, collections, `Option`, `Either`, `java.time` and more. See [Built-in Instances](../built-in-instances.md).
- **Derived.** `DeriveJsonEncoder.gen`, `DeriveJsonDecoder.gen` and `DeriveJsonCodec.gen`, or `derives` on Scala 3, write instances for case classes and sealed traits. See [Derivation](../derivation/index.md).
- **Built from another instance.** `contramap` on an encoder, `map` and `mapOrFail` on a decoder, and `transform` on a codec adapt an existing instance to a new type.
- **Written by hand.** For full control or speed, implement `unsafeEncode` or `unsafeDecode` directly. See "Writing an Encoder by Hand" on [JsonEncoder](../json-encoder.md#writing-an-encoder-by-hand) and "Writing a Decoder by Hand" on [JsonDecoder](../json-decoder.md#writing-a-decoder-by-hand).

Put an instance for your own type in that type's companion object. The compiler searches there without an import.
