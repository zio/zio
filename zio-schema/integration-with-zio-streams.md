# How to Encode and Decode Streams

> Turn a ZStream of values into bytes and back with ZIO Schema codecs, with framing for binary formats.

This guide shows how to turn a `ZStream` of values into a stream of bytes and back with ZIO Schema codecs, for example to write records to a file, a socket, or a message queue. It covers the JSON codec, which streams directly, and the binary codecs, which need the messages kept apart.

Every `BinaryCodec[A]` has two stream methods besides `BinaryCodec#encode` and `BinaryCodec#decode`. `BinaryCodec#streamEncoder` is a `ZPipeline[Any, Nothing, A, Byte]` that turns values into bytes, and `BinaryCodec#streamDecoder` is a `ZPipeline[Any, DecodeError, Byte, A]` that turns bytes back into values. A `ZPipeline` is a reusable stream transformer that we attach to a stream with `ZStream#via`. The [Codec Core Types](reference/codec-core.md) page describes the traits.

## Choose a Strategy for Your Codec

A stream of bytes has no built-in boundaries between values: the bytes of two values can arrive in one chunk, and the bytes of one value can be split across chunks. A stream decoder must find where each value ends, which is called framing. The codecs differ in how they handle it:

| Codec               | `BinaryCodec#streamEncoder` output              | `BinaryCodec#streamDecoder` input       | How to stream                               |
| ------------------- | ----------------------------------------------- | --------------------------------------- | ------------------------------------------- |
| JSON                | One value per line, or one JSON array           | Any chunking for newline-delimited JSON | Use the pipelines directly                  |
| Protobuf, Avro, XML | Encodings concatenated with no separator        | Exactly one value per chunk             | Encode per message, or add your own framing |
| MessagePack, Thrift | Cumulative: element `n` repeats elements 1..`n` | Exactly one value per chunk             | Encode per message, or add your own framing |
| BSON                | No stream pipelines                             | No stream pipelines                     | Use zio-bson or the MongoDB driver          |

Only the JSON codec streams several values reliably through its pipelines. For the binary formats, the sections below show two patterns that work with every codec.

## Prerequisites

The examples use the JSON and Protobuf modules, which bring ZIO Streams with them:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-schema-json"     % "1.9.2",
  "dev.zio" %% "zio-schema-protobuf" % "1.9.2"
)
```

For other formats, add their modules as listed on the [Introduction to ZIO Schema Codecs](derivations/codecs/index.md#available-codecs) page.

## Stream JSON Values

The JSON codec writes newline-delimited JSON by default: each value on its own line. Its decoder tracks brackets and strings to find where each value ends, so the bytes can arrive in chunks of any size.

To set up the examples, we define a model, derive a JSON codec, and add a helper that runs an effect and returns its result:

```scala
import zio._
import zio.schema.codec.{BinaryCodec, DecodeError, JsonCodec}
import zio.schema.{DeriveSchema, Schema}
import zio.stream._

final case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}

val people: Chunk[Person] = Chunk(Person("John", 42), Person("Jane", 34))

val jsonCodec: BinaryCodec[Person] = JsonCodec.schemaBasedBinaryCodec[Person]

def runNow[E, A](effect: ZIO[Any, E, A]): A =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
  }
```

To encode, we run the stream of people through the encoder pipeline and collect the bytes:

```scala
val jsonBytes: Chunk[Byte] =
  runNow(ZStream.fromChunk(people).via(jsonCodec.streamEncoder).runCollect)
```

The bytes hold one JSON document per line:

```scala
println(new String(jsonBytes.toArray, "UTF-8"))
// {"name":"John","age":42}
// {"name":"Jane","age":34}
```

To decode, we feed the bytes back in chunks of 5 bytes, so that every value is split across several chunks, and the decoder still returns both people:

```scala
runNow(ZStream.fromChunk(jsonBytes).rechunk(5).via(jsonCodec.streamDecoder).runCollect)
// res1: Chunk[Person] = IndexedSeq(
//   Person(name = "John", age = 42),
//   Person(name = "Jane", age = 34)
// )
```

## Stream a JSON Array

Some consumers expect one JSON array instead of one value per line. Setting `treatStreamsAsArrays = true` in `JsonCodec.Configuration` makes the encoder write a single array, and the decoder read the elements of one:

```scala
val arrayCodec: BinaryCodec[Person] =
  JsonCodec.schemaBasedBinaryCodec[Person](JsonCodec.Configuration(treatStreamsAsArrays = true))

val arrayBytes: Chunk[Byte] =
  runNow(ZStream.fromChunk(people).via(arrayCodec.streamEncoder).runCollect)
```

The output is one array, and decoding it gives the people back:

```scala
println(new String(arrayBytes.toArray, "UTF-8"))
// [{"name":"John","age":42},{"name":"Jane","age":34}]

runNow(ZStream.fromChunk(arrayBytes).via(arrayCodec.streamDecoder).runCollect)
// res3: Chunk[Person] = IndexedSeq(
//   Person(name = "John", age = 42),
//   Person(name = "Jane", age = 34)
// )
```

:::caution
With `treatStreamsAsArrays = true`, the stream decoder dies with an `ArrayIndexOutOfBoundsException` when the array arrives split across chunks of more than one byte, as it usually does when read from a socket or a file. Unless the whole array arrives in one chunk, collect the bytes first (for example with `ZStream#runCollect`) and decode them as one chunk, or use the default newline-delimited format, whose decoder handles any chunking.
:::

## Stream Values of a Binary Format

The stream decoders of the Protobuf, Avro, MessagePack, Thrift, and XML codecs decode each upstream chunk as exactly one message, and their encoders write no length or separator between messages.

:::caution
Do not connect `BinaryCodec#streamEncoder` to `BinaryCodec#streamDecoder` for these codecs when the stream has more than one value. The decoder reads the whole chunk as one message, so values are merged, dropped, or fail to decode. The MessagePack and Thrift stream encoders also repeat every earlier element in each element's output. Use one of the patterns below instead.
:::

To see the problem, we derive a Protobuf codec:

```scala
import zio.schema.codec.ProtobufCodec

val protobufCodec: BinaryCodec[Person] = ProtobufCodec.protobufCodec[Person]
```

Sending two people through its stream pipelines shows what goes wrong:

```scala
runNow(
  ZStream.fromChunk(people).via(protobufCodec.streamEncoder).via(protobufCodec.streamDecoder).runCollect.either
)
// res4: Either[DecodeError, Chunk[Person]] = Right(
//   value = IndexedSeq(Person(name = "Jane", age = 34))
// )
```

Two people go in, but the decoder treats the two encodings as one message, so they do not come back as two values.

### One Value per Message

When the transport already keeps messages apart, such as Kafka records, WebSocket frames, or rows in a table, encode each value into its own `Chunk[Byte]` with `BinaryCodec#encode`, and decode each message with `BinaryCodec#decode`:

```scala
val messages: Chunk[Chunk[Byte]] =
  runNow(ZStream.fromChunk(people).map(protobufCodec.encode).runCollect)
```

Each message decodes on its own, so both people come back:

```scala
runNow(ZStream.fromChunk(messages).mapZIO(message => ZIO.fromEither(protobufCodec.decode(message))).runCollect)
// res5: Chunk[Person] = IndexedSeq(
//   Person(name = "John", age = 42),
//   Person(name = "Jane", age = 34)
// )
```

This works for every binary codec, including MessagePack and Thrift, because `BinaryCodec#encode` starts from an empty buffer for each value.

### Length-Prefixed Framing

When the transport is a plain byte stream, such as a file or a TCP socket, add the framing yourself. A common scheme writes the length of each message as a 4-byte integer in front of it. The following helpers write and read that format for any `BinaryCodec`:

```scala
import java.nio.ByteBuffer

def writeFramed[A](codec: BinaryCodec[A]): ZPipeline[Any, Nothing, A, Byte] =
  ZPipeline.mapChunks[A, Byte](_.flatMap { value =>
    val message = codec.encode(value)
    Chunk.fromArray(ByteBuffer.allocate(4).putInt(message.size).array()) ++ message
  })

def splitFrames(buffer: Chunk[Byte], frames: Chunk[Chunk[Byte]]): (Chunk[Byte], Chunk[Chunk[Byte]]) =
  if (buffer.size < 4) (buffer, frames)
  else {
    val size = ByteBuffer.wrap(buffer.take(4).toArray).getInt
    if (buffer.size < 4 + size) (buffer, frames)
    else splitFrames(buffer.drop(4 + size), frames :+ buffer.slice(4, 4 + size))
  }

def readFramed[A](codec: BinaryCodec[A], bytes: ZStream[Any, Nothing, Byte]): ZStream[Any, DecodeError, A] =
  bytes.chunks
    .mapAccum(Chunk.empty[Byte])((buffer, chunk) => splitFrames(buffer ++ chunk, Chunk.empty))
    .flattenChunks
    .mapZIO(message => ZIO.fromEither(codec.decode(message)))
```

`writeFramed` encodes each value on its own and puts its length in front of it. `readFramed` keeps the bytes that do not yet form a whole message in a buffer, emits each complete message, and decodes it. Bytes left over at the end of the stream, from a truncated last message, are dropped.

With framing in place, we encode the people into one byte stream:

```scala
val framedBytes: Chunk[Byte] =
  runNow(ZStream.fromChunk(people).via(writeFramed(protobufCodec)).runCollect)
```

Reading the bytes back in chunks of 3 bytes still gives both people, because the decoder waits for each whole message:

```scala
runNow(readFramed(protobufCodec, ZStream.fromChunk(framedBytes).rechunk(3)).runCollect)
// res6: Chunk[Person] = IndexedSeq(
//   Person(name = "John", age = 42),
//   Person(name = "Jane", age = 34)
// )
```

The same helpers work with the Avro, MessagePack, Thrift, and XML codecs, because they call only `BinaryCodec#encode` and `BinaryCodec#decode`.

## Where to Go Next

- [Introduction to ZIO Schema Codecs](derivations/codecs/index.md) compares the codecs and the annotations they support.
- [JSON Codecs](derivations/codecs/json.md) describes `treatStreamsAsArrays` and the other configuration fields.
- [Write a Custom Codec](how-to/custom-codec.md) shows how to implement `Codec#streamEncoder` and `Codec#streamDecoder` for your own format.
