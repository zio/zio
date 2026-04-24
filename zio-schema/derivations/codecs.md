# Introduction to ZIO Schema Codecs

> Once we generate a schema for a type, we can derive a codec for that type.

Once we generate a schema for a type, we can derive a codec for that type.

A codec is a utility that can encode/decode a value of some type `A` to/from some format (e.g. binary format, JSON, etc.)

## Codec

Unlike codecs in other libraries, a codec in ZIO Schema has no type parameter:

```scala
trait Codec {
  def encoder[A](schema: Schema[A]): ZTransducer[Any, Nothing, A, Byte]
  def decoder[A](schema: Schema[A]): ZTransducer[Any, String, Byte, A]

  def encode[A](schema: Schema[A]): A => Chunk[Byte]
  def decode[A](schema: Schema[A]): Chunk[Byte] => Either[String, A]
}
```

The `Codec` trait has two basic methods:

- `encode[A]`: Given a `Schema[A]` it is capable of generating an `Encoder[A]` ( `A => Chunk[Byte]`) for any Schema.
- `decode[A]`: Given a `Schema[A]` it is capable of generating a `Decoder[A]` ( `Chunk[Byte] => Either[String, A]`) for any Schema.

## Binary Codecs

The binary codecs are codecs that can encode/decode a value of some type `A` to/from binary format (e.g. `Chunk[Byte]`).  In ZIO Schema, by having a `BinaryCodec[A]` instance, other than being able to encode/decode a value of type `A` to/from binary format, we can also encode/decode a stream of values of type `A` to/from a stream of binary format.

```scala

trait Decoder[Whole, Element, +A] {
  def decode(whole: Whole): Either[DecodeError, A]
  def streamDecoder: ZPipeline[Any, DecodeError, Element, A]
}

trait Encoder[Whole, Element, -A] {
  def encode(value: A): Whole
  def streamEncoder: ZPipeline[Any, Nothing, A, Element]
}

trait Codec[Whole, Element, A] extends Encoder[Whole, Element, A] with Decoder[Whole, Element, A]

trait BinaryCodec[A] extends Codec[Chunk[Byte], Byte, A]
```

To make it simpler, we can think of a `BinaryCodec[A]` as the following trait:

```scala

trait BinaryCodec[A] {
  def encode(value: A): Chunk[Byte]
  def decode(whole: Chunk[Byte]): Either[DecodeError, A]

  def streamEncoder: ZPipeline[Any, Nothing, A, Byte]
  def streamDecoder: ZPipeline[Any, DecodeError, Byte, A]
}
```

Example of possible codecs are:

- CSV Codec
- JSON Codec (already available)
- Apache Avro Codec (in progress)
- Apache Thrift Codec (in progress)
- XML Codec
- YAML Codec
- Protobuf Codec (already available)
- QueryString Codec
- etc.
