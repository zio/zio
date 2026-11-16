# Record Headers

> A Kafka record carries an optional list of **headers** next to its key and value. Each header has a `String` key and an `Array[Byte]` value (which may be `null`). Headers keep the order in which they are added, and several headers can share a key. Applications use them for metadata that does not belong in the payload, such as a content type, a schema version, a trace id or the name of the producing service.

Zio-kafka has no header type of its own. It uses the Kafka client's `org.apache.kafka.common.header.Headers` and `Header` interfaces directly, and passes them along at every step:

- When producing, the headers of a `ProducerRecord` are written with the record.
- When consuming, `CommittableRecord#record` returns the Kafka `ConsumerRecord`, whose `headers()` method returns the headers.
- Every `Serializer` and `Deserializer` receives the record's headers as a parameter.

## Adding Headers When Producing

A `ProducerRecord` always has a mutable `Headers` collection, so the simplest way to add a header is `Headers#add` on `ProducerRecord#headers`. To produce a record with two headers:

```scala
import zio._
import zio.kafka.producer._
import zio.kafka.serde._
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }
import java.nio.charset.StandardCharsets.UTF_8

def produceOrder(producer: Producer): Task[RecordMetadata] = {
  val record = new ProducerRecord("orders", "order-1", "created")
  record.headers().add("source", "web-shop".getBytes(UTF_8))
  record.headers().add("trace-id", "4bf92f3577b34da6".getBytes(UTF_8))
  producer.produce(record, Serde.string, Serde.string)
}
```

You can also pass the headers to the `ProducerRecord` constructor, which accepts any `java.lang.Iterable[Header]` after the topic, partition, timestamp, key and value. Pass `null` for the partition and timestamp to let Kafka choose them. Kafka's own implementations are `RecordHeader` and `RecordHeaders` in the `org.apache.kafka.common.header.internals` package. That package is not part of Kafka's documented public API, but the classes are widely used (zio-kafka's own tests use them). To build the headers first and then the record:

```scala
import zio._
import zio.kafka.producer._
import zio.kafka.serde._
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }
import org.apache.kafka.common.header.internals.RecordHeaders
import java.nio.charset.StandardCharsets.UTF_8

def produceWithHeaders(producer: Producer): Task[RecordMetadata] = {
  val headers = new RecordHeaders()
    .add("source", "web-shop".getBytes(UTF_8))
    .add("schema-version", "2".getBytes(UTF_8))

  val record = new ProducerRecord[String, String]("orders", null, null, "order-1", "created", headers)
  producer.produce(record, Serde.string, Serde.string)
}
```

Headers work the same with every produce method of the [Producer](producer.md), including the chunk variants and `Producer#produceAll`, and with the [transactional producer](transactions.md): `Transaction#produce`, `Transaction#produceChunk` and `Transaction#produceChunkBatch` send records through the same code path.

:::warning
Create a new `ProducerRecord` for every send. When you produce a byte-array record, the Kafka client marks that record's headers read-only, so a later `Headers#add` on it throws an `IllegalStateException`. With the serializer overloads, zio-kafka sends a copy, but a serializer that adds headers changes the record you pass in (see [Header-Aware Serializers and Deserializers](#header-aware-serializers-and-deserializers)).
:::

## Reading Headers When Consuming

A `CommittableRecord` has no header accessor of its own; read them from the wrapped Kafka record with `record.record.headers()`. The `Headers` interface offers `Headers#lastHeader` for the most recent header with a key (or `null` when there is none), `Headers#headers` for all headers with a key, and iteration over all headers. Since both a missing header and a header value can be `null`, wrap them in `Option`. To read a header as a string and to list all headers of a record:

```scala
import zio.kafka.consumer._
import scala.jdk.CollectionConverters._
import java.nio.charset.StandardCharsets.UTF_8

def headerValue[K, V](record: CommittableRecord[K, V], key: String): Option[String] =
  Option(record.record.headers().lastHeader(key))
    .flatMap(header => Option(header.value()))
    .map(bytes => new String(bytes, UTF_8))

def allHeaders[K, V](record: CommittableRecord[K, V]): List[(String, Option[String])] =
  record.record
    .headers()
    .asScala
    .map(header => header.key() -> Option(header.value()).map(new String(_, UTF_8)))
    .toList
```

In a consumer stream, read the headers in an operator that keeps the chunk structure, such as `ZStream#map`. To pair each record's `source` header with its offset for later committing:

```scala
import zio.stream._
import zio.kafka.consumer._
import zio.kafka.serde._
import java.nio.charset.StandardCharsets.UTF_8

def sources(consumer: Consumer): ZStream[Any, Throwable, (String, Offset)] =
  consumer
    .plainStream(Subscription.topics("orders"), Serde.string, Serde.string)
    .map { record =>
      val source = Option(record.record.headers().lastHeader("source"))
        .flatMap(header => Option(header.value()))
        .map(new String(_, UTF_8))
        .getOrElse("unknown")
      (source, record.offset)
    }
```

`Consumer#consumeWith` hands your function a `ConsumerRecord` directly, so there you call `record.headers()`. See [Consumer](consumer.md) for the stream methods and [Offsets and Committing](offsets-and-committing.md) for committing the collected offsets.

## Header-Aware Serializers and Deserializers

Every `Serializer` and `Deserializer` receives the topic and the headers along with the value, so the encoding of a record can depend on its headers. The function-based constructors make this explicit:

```scala
object Serializer {
  def apply[R, A](ser: (String, Headers, A) => RIO[R, Array[Byte]]): Serializer[R, A]
}

object Deserializer {
  def apply[R, A](deser: (String, Headers, Array[Byte]) => RIO[R, A]): Deserializer[R, A]
}
```

A serializer may also add headers. When producing with a serializer, zio-kafka runs the key serializer and then the value serializer on the record's headers, and writes the record with the headers as they are afterwards. The serializer below encodes a `Greeting` as JSON and records that in a `content-type` header. The deserializer reads the header and accepts both the JSON format and an older `name:message` text format, which it assumes when the header is missing:

```scala
import zio._
import zio.json._
import zio.kafka.serde._
import java.nio.charset.StandardCharsets.UTF_8

final case class Greeting(name: String, message: String)

object Greeting {
  implicit val encoder: JsonEncoder[Greeting] = DeriveJsonEncoder.gen[Greeting]
  implicit val decoder: JsonDecoder[Greeting] = DeriveJsonDecoder.gen[Greeting]
}

val ContentTypeHeader = "content-type"

val greetingSerializer: Serializer[Any, Greeting] =
  Serializer { (_, headers, greeting) =>
    ZIO.attempt {
      headers.add(ContentTypeHeader, "application/json".getBytes(UTF_8))
      greeting.toJson.getBytes(UTF_8)
    }
  }

val greetingDeserializer: Deserializer[Any, Greeting] =
  Deserializer { (_, headers, data) =>
    val contentType = Option(headers.lastHeader(ContentTypeHeader))
      .flatMap(header => Option(header.value()))
      .map(new String(_, UTF_8))
    val text = new String(data, UTF_8)

    contentType match {
      case Some("application/json") =>
        ZIO.fromEither(text.fromJson[Greeting]).mapError(new RuntimeException(_))
      case Some("text/plain") | None =>
        text.split(":", 2) match {
          case Array(name, message) => ZIO.succeed(Greeting(name, message))
          case _                    => ZIO.fail(new RuntimeException(s"Invalid greeting: $text"))
        }
      case Some(other) =>
        ZIO.fail(new RuntimeException(s"Unsupported content type: $other"))
    }
  }

val greetingSerde: Serde[Any, Greeting] = Serde(greetingDeserializer)(greetingSerializer)
```

Because the serializer changes the headers of the `ProducerRecord` you pass in, producing the same record object twice adds the header twice; create a new record for every send. The predefined serdes such as `Serde.string` ignore headers. See [Serialization and Deserialization](serialization-and-deserialization.md) for more ways to build serdes.

## Headers in CommittableRecord#deserializeWith

`CommittableRecord#deserializeWith` turns a record of byte arrays into a typed record. It passes the record's headers to both deserializers, and the new record keeps the same headers. This is useful when you consume with `Serde.byteArray` and deserialize inside the stream, where you can handle failures per record. Reusing `greetingDeserializer` from the previous section:

```scala
import zio.stream._
import zio.kafka.consumer._

def greetings(consumer: Consumer): ZStream[Any, Throwable, CommittableRecord[String, Greeting]] =
  consumer
    .plainStream(Subscription.topics("greetings"), Serde.byteArray, Serde.byteArray)
    .mapChunksZIO(records => ZIO.foreach(records)(_.deserializeWith(Serde.string, greetingDeserializer)))
```

The deserializers you pass to `Consumer#plainStream` and the other stream methods go through `CommittableRecord#deserializeWith` as well, so they receive the headers in the same way.
