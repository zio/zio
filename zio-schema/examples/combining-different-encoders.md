# Combining Different Encoders

> Move one data model between JSON and Protobuf, for single values and for streams, using codecs derived from one schema.

Because every codec is derived from the same `Schema`, one data model can move between formats without any conversion code. This example takes a value on a round trip through JSON and back, then through Protobuf and back, first for a single value and then for a stream of values. It also shows how to convert JSON input straight into Protobuf bytes.

## Prerequisites

The example uses the JSON and Protobuf modules:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-schema-json"     % "1.9.2",
  "dev.zio" %% "zio-schema-protobuf" % "1.9.2"
)
```

## Define the Model and the Codecs

We start with a case class, derive its schema once, and derive a JSON codec and a Protobuf codec from that schema:

```scala
import zio._
import zio.schema.codec.{BinaryCodec, DecodeError, JsonCodec, ProtobufCodec}
import zio.schema.{DeriveSchema, Schema}
import zio.stream.ZStream

final case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}

val jsonCodec: BinaryCodec[Person]     = JsonCodec.schemaBasedBinaryCodec[Person]
val protobufCodec: BinaryCodec[Person] = ProtobufCodec.protobufCodec[Person]
```

Both codecs have the same type, `BinaryCodec[Person]`, so code that works with one works with the other.

## Round-Trip a Single Value

`BinaryCodec#encode` returns bytes, and `BinaryCodec#decode` returns an `Either[DecodeError, Person]`, so the two round trips chain in a for-comprehension:

```scala
val michelle = Person("Michelle", 32)
// michelle: Person = Person(name = "Michelle", age = 32)

val roundTrip: Either[DecodeError, Person] =
  for {
    fromJson     <- jsonCodec.decode(jsonCodec.encode(michelle))
    fromProtobuf <- protobufCodec.decode(protobufCodec.encode(fromJson))
  } yield fromProtobuf
// roundTrip: Either[DecodeError, Person] = Right(
//   value = Person(name = "Michelle", age = 32)
// )

roundTrip == Right(michelle)
// res0: Boolean = true
```

## Convert JSON to Protobuf

The same chaining converts data between formats. To turn a JSON payload into Protobuf bytes, decode it with the JSON codec and encode the result with the Protobuf codec:

```scala
def jsonToProtobuf(json: Chunk[Byte]): Either[DecodeError, Chunk[Byte]] =
  jsonCodec.decode(json).map(protobufCodec.encode)

val incoming: Chunk[Byte] = Chunk.fromArray("""{"name":"Michelle","age":32}""".getBytes("UTF-8"))
```

Decoding the converted bytes with the Protobuf codec gives the person back:

```scala
jsonToProtobuf(incoming).flatMap(protobufCodec.decode)
// res1: Either[DecodeError, Person] = Right(
//   value = Person(name = "Michelle", age = 32)
// )
```

## Round-Trip a Stream

For a stream of values, the JSON codec's `BinaryCodec#streamEncoder` and `BinaryCodec#streamDecoder` pipelines keep the values apart. The Protobuf codec's stream decoder reads each chunk as one message, so the stream encodes and decodes each value on its own with `ZStream#map` and `ZStream#mapZIO`. The following program prints each step and checks that the people survive both round trips:

```scala
object CombiningExample extends ZIOAppDefault {

  val people: Chunk[Person] = Chunk(Person("Michelle", 32), Person("John", 42))

  def run: ZIO[Any, DecodeError, Unit] =
    for {
      result <- ZStream
                  .fromChunk(people)
                  .tap(person => ZIO.debug(s"input object is: $person"))
                  .via(jsonCodec.streamEncoder)
                  .via(jsonCodec.streamDecoder)
                  .tap(person => ZIO.debug(s"object after json roundtrip: $person"))
                  .map(protobufCodec.encode)
                  .mapZIO(bytes => ZIO.fromEither(protobufCodec.decode(bytes)))
                  .tap(person => ZIO.debug(s"person after protobuf roundtrip: $person"))
                  .runCollect
      _      <- ZIO.debug(s"are the old people the new people? ${result == people}")
    } yield ()
}
```

The [How to Encode and Decode Streams](../integration-with-zio-streams.md) guide explains why the Protobuf step works per value, and shows a framing scheme for sending many Protobuf values over a plain byte stream.
