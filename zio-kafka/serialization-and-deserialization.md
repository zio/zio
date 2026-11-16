# Serialization and Deserialization

> Kafka stores keys and values as plain byte arrays. Zio-kafka converts them to and from your own types with three types from the `zio.kafka.serde` package: a `Serializer` turns a value into bytes when producing, a `Deserializer` turns bytes into a value when consuming, and a `Serde` is both at once. All three are effectful: they return a ZIO effect, so a conversion can fail with a `Throwable`, use the ZIO environment, or call another service, such as a schema registry.

The core types have this shape:

```scala
import org.apache.kafka.common.header.Headers
import zio.RIO

trait Serializer[-R, -A] {
  def serialize(topic: String, headers: Headers, value: A): RIO[R, Array[Byte]]
}

trait Deserializer[-R, +A] {
  def deserialize(topic: String, headers: Headers, data: Array[Byte]): RIO[R, A]
}

trait Serde[-R, A] extends Deserializer[R, A] with Serializer[R, A]
```

The type parameter `R` is the ZIO environment the conversion needs; it is `Any` for every built-in serde. `A` is the value type. Because `Serde` extends both traits, a `Serde` can be passed wherever a `Serializer` or a `Deserializer` is expected, which is why most code only ever names `Serde.string`, `Serde.long` and friends.

## How They Work Together

Serdes are not part of the `Consumer` or `Producer` themselves. You pass a key and a value deserializer (or serializer) to every call that reads (or writes) records, so one consumer or producer can handle topics with different formats.

A typical application uses them like this:

1. Pick a built-in serde such as `Serde.string`, or build one: from functions, from an existing Kafka serde, or by transforming another serde.
2. Pass a key and a value deserializer to a [consumer](consumer.md) stream method: `Consumer#plainStream`, `Consumer#partitionedStream`, `Consumer#partitionedAssignmentStream`, their `WithControl` variants, or `Consumer#consumeWith` (and `Consumer.consumeWith`). Zio-kafka reads every record from Kafka as raw bytes and runs your deserializers on each record inside the stream, with `CommittableRecord#deserializeWith`. A failed deserialization fails the stream.
3. Pass a key and a value serializer to a [producer](producer.md) method: `Producer#produce`, `Producer#produceAsync`, `Producer#produceChunk`, `Producer#produceChunkAsync`, or the `Producer#produceAll` pipeline. In a [transaction](transactions.md), `Transaction#produce`, `Transaction#produceChunk` and `Transaction#produceChunkBatch` take serializers in the same way. Serialization runs in the calling fiber before the record is handed to the producer. A failed serialization fails the call, and nothing is sent; for the chunk methods, all records are serialized first, so one failure means no record of the chunk is sent.

The diagram shows how the types relate and where they are used:

```
              Serializer[-R, -A]               Deserializer[-R, +A]
                      ^                                  ^
                      |             extends              |
                      +-------- Serde[-R, A] ------------+

 Producer#produce*(record, keySerializer, valueSerializer)
   key: K, value: V --Serializer#serialize(topic, headers, _)--> Array[Byte] --> Kafka

 Consumer#plainStream(subscription, keyDeserializer, valueDeserializer)
   Kafka --> Array[Byte] --Deserializer#deserialize(topic, headers, _)--> key: K, value: V
```

The producer methods that take a `ProducerRecord[Array[Byte], Array[Byte]]` have no serializer parameters; they send the bytes as they are. `Producer#produceChunkAsyncWithFailures` exists only in this byte-array form, because serializers could fail for each record independently.

`Serializer#serialize` and `Deserializer#deserialize` both receive the record's topic and its Kafka `Headers`. A serializer may read the headers or add to them, and a deserializer may use them to decide how to decode the bytes, for example from a content-type header. The built-in serdes ignore both the topic and the headers. [Record Headers](record-headers.md#header-aware-serializers-and-deserializers) shows a header-aware serde.

## Built-in Serdes

The `Serde` companion object provides ready-made serdes for common types: `Serde.string`, `Serde.int`, `Serde.long`, `Serde.short`, `Serde.float`, `Serde.double`, their option variants `Serde.intOption`, `Serde.longOption`, `Serde.shortOption`, `Serde.floatOption` and `Serde.doubleOption`, and `Serde.uuid`, `Serde.bytes`, `Serde.byteBuffer` and `Serde.byteArray`. All of them have environment `Any` and, except for `Serde.byteArray`, delegate to a serializer and a deserializer from Kafka's `org.apache.kafka.common.serialization` package. For their types, see the [`Serde` API docs](https://javadoc.io/doc/dev.zio/zio-kafka_2.13/latest/zio/kafka/serde/Serde$.html).

Zio-kafka never calls `configure` on these Kafka classes, so they use Kafka's defaults; for example, `Serde.string` and `Serde.uuid` always use UTF-8. To use another configuration, wrap a Kafka serde yourself, as shown in [From Kafka Serdes](#from-kafka-serdes). `Serde.byteArray` returns its input unchanged and cannot fail. The other serdes catch exceptions from the Kafka code and turn them into failures, such as a `Serde.int` deserializer that receives anything other than 4 bytes.

The `Serializer` and `Deserializer` companion objects contain the same set of serdes (`Serializer.string`, `Deserializer.long`, and so on), also typed as `Serde`. They are distinct instances from the ones on `Serde`, though, and only `Serde.byteArray` enables the consumer's byte-array shortcut described in [Deserialization in the Consumer Stream](#deserialization-in-the-consumer-stream), so prefer the `Serde` versions.

Serdes need no broker, so we can call them directly. For the examples on this page, we use a small helper that runs an effect and returns its result:

```scala
import zio._
import zio.kafka.serde._
import org.apache.kafka.common.header.internals.RecordHeaders

def unsafeRun[A](effect: Task[A]): A =
  Unsafe.unsafe { implicit u =>
    Runtime.default.unsafe.run(effect).getOrThrow()
  }
```

`Serde.int` writes an `Int` as 4 big-endian bytes and reads it back:

```scala
val intBytes = unsafeRun(Serde.int.serialize("numbers", new RecordHeaders, 258))
// intBytes: Array[Byte] = Array(0, 0, 1, 2)

unsafeRun(Serde.int.deserialize("numbers", new RecordHeaders, intBytes))
// res0: Int = 258
```

## Creating Serdes

When no built-in serde fits, you can create one from scratch: from plain functions, or by wrapping a serializer or deserializer written for the Kafka Java client. To derive a serde from an existing one instead, see [Custom Data Type Serdes](#custom-data-type-serdes).

### From Functions

`Serializer.apply`, `Deserializer.apply` and `Serde.apply` build an instance from functions of the topic, the headers and the data. `Serde.apply` comes in two forms: one takes two functions, the other combines an existing `Deserializer` and `Serializer`:

```scala
object Serializer {
  def apply[R, A](ser: (String, Headers, A) => RIO[R, Array[Byte]]): Serializer[R, A]
}

object Deserializer {
  def apply[R, A](deser: (String, Headers, Array[Byte]) => RIO[R, A]): Deserializer[R, A]
}

object Serde {
  def apply[R, A](deser: (String, Headers, Array[Byte]) => RIO[R, A])(ser: (String, Headers, A) => RIO[R, Array[Byte]]): Serde[R, A]
  def apply[R, A](deser: Deserializer[R, A])(ser: Serializer[R, A]): Serde[R, A]
}
```

The functions signal a failure with a failed effect, so wrap code that can throw in `ZIO.attempt`. To build a serde for a `Point` that is stored as the text `x,y`:

```scala
import zio._
import zio.kafka.serde._
import java.nio.charset.StandardCharsets.UTF_8

final case class Point(x: Int, y: Int)

val pointDeserializer: Deserializer[Any, Point] =
  Deserializer { (_, _, data) =>
    new String(data, UTF_8).split(",") match {
      case Array(x, y) => ZIO.attempt(Point(x.trim.toInt, y.trim.toInt))
      case _           => ZIO.fail(new IllegalArgumentException("A point needs two coordinates"))
    }
  }

val pointSerializer: Serializer[Any, Point] =
  Serializer((_, _, point) => ZIO.succeed(s"${point.x},${point.y}".getBytes(UTF_8)))

val pointSerde: Serde[Any, Point] = Serde(pointDeserializer)(pointSerializer)
```

`Serializer` and `Deserializer` each have a single abstract method, so you can also implement them, or `Serde`, directly with `new Serde[Any, Point] { ... }`, overriding `Serde#serialize` and `Serde#deserialize`.

### From Kafka Serdes

Many serialization formats already have an implementation for the Kafka Java client: a class implementing `org.apache.kafka.common.serialization.Serializer`, `Deserializer` or `Serde`. Third-party serdes, such as the Avro serdes that work with a schema registry, are usually distributed this way. Three constructors turn them into zio-kafka serdes:

```scala
object Serializer {
  def fromKafkaSerializer[A](serializer: KafkaSerializer[A], props: Map[String, AnyRef], isKey: Boolean): Task[Serializer[Any, A]]
}

object Deserializer {
  def fromKafkaDeserializer[A](deserializer: KafkaDeserializer[A], props: Map[String, AnyRef], isKey: Boolean): Task[Deserializer[Any, A]]
}

object Serde {
  def fromKafkaSerde[A](serde: KafkaSerde[A], props: Map[String, AnyRef], isKey: Boolean): Task[Serde[Any, A]]
}
```

Each constructor first calls the Kafka object's `configure(props, isKey)` method, which is why it returns a `Task`: if `configure` throws, the task fails. The `props` map is the configuration the Kafka class expects, the same keys you would give the Java client, for example the URL of a schema registry. `isKey` tells the Kafka class whether it handles keys or values; many classes read different configuration keys for each, so configure a separate instance for keys and for values. Every later call passes the topic, the headers and the data to the Kafka class and turns an exception into a failed effect. A `Serde` built with `Serde.fromKafkaSerde` takes the serializer and the deserializer from the Kafka serde once, when it is created.

Kafka's `StringSerializer` reads its character encoding from `value.serializer.encoding` when `isKey` is `false`. To serialize strings as UTF-16 instead of the default UTF-8:

```scala
import org.apache.kafka.common.serialization.{ IntegerDeserializer, StringSerializer }

unsafeRun(
  Serializer
    .fromKafkaSerializer(new StringSerializer, Map("value.serializer.encoding" -> "UTF-16"), isKey = false)
    .flatMap(_.serialize("greetings", new RecordHeaders, "hi"))
)
// res2: Array[Byte] = Array(-2, -1, 0, 104, 0, 105)
```

A wrapped Kafka class keeps its Java types; Kafka's `IntegerDeserializer` produces a `java.lang.Integer`. To get a Scala `Int`, map the result:

```scala
unsafeRun(
  Deserializer
    .fromKafkaDeserializer(new IntegerDeserializer, Map.empty, isKey = true)
    .flatMap(_.map(_.intValue).deserialize("numbers", new RecordHeaders, Array[Byte](0, 0, 1, 2)))
)
// res3: Int = 258
```

Because the constructors return a `Task`, create the serdes once, before you start consuming or producing, and pass them on. To consume a topic whose keys are UTF-16 and whose values are ISO-8859-1 text, using Kafka's `Serdes.String()` factory, imported as `KafkaSerdes`:

```scala
import zio._
import zio.kafka.consumer._
import zio.kafka.serde._
import org.apache.kafka.common.serialization.{ Serdes => KafkaSerdes }

val consumerSettings = ConsumerSettings(List("localhost:9092")).withGroupId("group")

val program: ZIO[Scope, Throwable, Unit] =
  for {
    keySerde   <- Serde.fromKafkaSerde(KafkaSerdes.String(), Map("key.deserializer.encoding" -> "UTF-16"), isKey = true)
    valueSerde <- Serde.fromKafkaSerde(KafkaSerdes.String(), Map("value.deserializer.encoding" -> "ISO-8859-1"), isKey = false)
    consumer   <- Consumer.make(consumerSettings)
    _ <- consumer
           .plainStream(Subscription.topics("legacy-events"), keySerde, valueSerde)
           .mapChunksZIO(records => ZIO.foreach(records)(record => ZIO.logInfo(record.value).as(record.offset)))
           .aggregateAsync(Consumer.collectOffsets)
           .mapZIO(_.commit)
           .runDrain
  } yield ()
```

Zio-kafka never calls `close` on a wrapped Kafka object. If it holds resources, such as a connection to a schema registry, keep a reference to it and close it yourself, for example with `ZIO.acquireRelease`. If its calls block on network I/O, run it on the blocking thread pool, see [Running on the Blocking Thread Pool](#running-on-the-blocking-thread-pool).

## Custom Data Type Serdes

Serializers and deserializers for custom data types can be created from scratch, as described in [Creating Serdes](#creating-serdes), or by converting existing ones. Converting is usually shorter: you only write the step between your type and a type that already has a serde.

### Transforming Deserializers

A `Deserializer` produces values, so it is transformed in the same direction as the data, from `A` to `B`:

```scala
trait Deserializer[-R, +A] {
  def map[B](f: A => B): Deserializer[R, B]
  def mapZIO[R1 <: R, B](f: A => RIO[R1, B]): Deserializer[R1, B]
}
```

`Deserializer#map` applies a pure function. The function must not throw: an exception thrown inside it is a defect, not a failure, so it kills the consumer stream and `Deserializer#asTry` cannot catch it. For a conversion that can fail, use `Deserializer#mapZIO` and return a failed effect. To parse a count that is stored as text:

```scala
import zio._
import zio.kafka.serde._

val countDeserializer: Deserializer[Any, Int] =
  Serde.string.mapZIO(text => ZIO.attempt(text.trim.toInt))
```

### Transforming Serializers

A `Serializer` consumes values, so it is transformed against the direction of the data: you supply a function from your new type `B` to the type `A` that the serializer already handles:

```scala
trait Serializer[-R, -A] {
  def contramap[B](f: B => A): Serializer[R, B]
  def contramapZIO[R1 <: R, B](f: B => RIO[R1, A]): Serializer[R1, B]
}
```

`Serializer#contramap` takes a pure function, and `Serializer#contramapZIO` an effectful one, for example one that looks up data before serializing. To write an `Instant` as its ISO-8601 text:

```scala
import java.time.Instant

new String(
  unsafeRun(
    Serde.string
      .contramap((instant: Instant) => instant.toString)
      .serialize("events", new RecordHeaders, Instant.EPOCH)
  ),
  "UTF-8"
)
// res6: String = "1970-01-01T00:00:00Z"
```

### Transforming Both Directions

Calling `Deserializer#map` or `Serializer#contramap` on a `Serde` returns only a `Deserializer` or a `Serializer`. To transform a `Serde` and keep both directions, give one function for each direction:

```scala
trait Serde[-R, A] extends Deserializer[R, A] with Serializer[R, A] {
  def inmap[B](f: A => B)(g: B => A): Serde[R, B]
  def inmapZIO[R1 <: R, B](f: A => RIO[R1, B])(g: B => RIO[R1, A]): Serde[R1, B]
}
```

`Serde#inmap` takes pure functions; the same rule as for `Deserializer#map` applies, so `f` must not throw. For example, to create a serde for an `Instant` from a serde for a `Long`:

```scala
import java.time.Instant
import zio.kafka.serde._

val instantSerde: Serde[Any, Instant] =
  Serde.long.inmap(java.time.Instant.ofEpochMilli)(_.toEpochMilli)
```

`Serde#inmapZIO` takes effectful functions, so decoding can fail cleanly. To accept only order ids with the right prefix:

```scala
import zio._
import zio.kafka.serde._

final case class OrderId(value: String)

val orderIdSerde: Serde[Any, OrderId] =
  Serde.string.inmapZIO { text =>
    if (text.startsWith("order-")) ZIO.succeed(OrderId(text))
    else ZIO.fail(new IllegalArgumentException(s"Not an order id: $text"))
  }(orderId => ZIO.succeed(orderId.value))
```

For a complete example of creating a custom serde with ZIO JSON, see the [Producing and Consuming JSON Data](tutorial.md#producing-and-consuming-json-data) section in the tutorial.

## Handling Missing Data

A Kafka key or value can be `null`: many records have no key, and a tombstone record, which marks a key as deleted in a compacted topic, has a `null` value. The plain built-in serdes handle `null` poorly. `Serde.string` and the other reference-type serdes return a `null` value, and the numeric serdes, such as `Serde.long`, silently turn Kafka's `null` into `0` (Scala unboxes a `null` number to zero), so a tombstone or a missing key looks like a real zero.

For numeric keys and values that can be `null`, use the option variants `Serde.intOption`, `Serde.longOption`, `Serde.shortOption`, `Serde.floatOption` and `Serde.doubleOption`. They deserialize `null` to `None` and serialize `None` to `null`:

```scala
unsafeRun(Serde.longOption.deserialize("users", new RecordHeaders, null))
// res9: Option[Long] = None

unsafeRun(Serde.longOption.serialize("users", new RecordHeaders, None))
// res10: Array[Byte] = null
```

For any other serde, use `Serializer#asOption`, `Deserializer#asOption` or `Serde#asOption`; `Serde.longOption` behaves the same as `Serde.long.asOption`:

```scala
trait Serializer[-R, -A] {
  def asOption[A1 <: A]: Serializer[R, Option[A1]]
}

trait Deserializer[-R, +A] {
  def asOption: Deserializer[R, Option[A]]
}

trait Serde[-R, A] extends Deserializer[R, A] with Serializer[R, A] {
  override def asOption: Serde[R, Option[A]]
}
```

For example, `Serde.string.asOption` results in a `None` if the key or value is `null`, and in a `Some(string)` otherwise; the underlying deserializer is not called for `null`. When serializing, `None` is written as `null`. An empty byte array is not `null`, so it still goes through the deserializer:

```scala
unsafeRun(Serde.string.asOption.serialize("users", new RecordHeaders, None))
// res11: Array[Byte] = null

unsafeRun(Serde.long.asOption.deserialize("users", new RecordHeaders, null))
// res12: Option[Long] = None

unsafeRun(Serde.string.asOption.deserialize("users", new RecordHeaders, Array.emptyByteArray))
// res13: Option[String] = Some(value = "")
```

## Handling Failures in a Serde

Ideally, a serde can not fail serializing and deserializing. This is for example the case with the provided `Serde.byteArray` and `Serde.string`. This is not the case for any serde that needs to handle invalid input (for example `Serde.long`), or a serde that needs to do a remote lookup.

By default, a consumer stream fails if it encounters a deserialization error in the serde. The resulting error does not always show that the serde is the cause.

There are three ways to improve this, described in the sections below: fall back to another deserializer with `Deserializer#orElse`, wrap the result of the deserializer in a `Try` with `Deserializer#asTry`, or consume with `Serde.byteArray` and do the deserialization in the consumer stream (or serialize before handing the data to zio-kafka), so that you can handle failures any way you want.

### Falling Back to Another Deserializer

When a topic contains data in more than one format, `Deserializer#orElse` tries a second deserializer when the first one fails:

```scala
trait Deserializer[-R, +A] {
  def orElse[R1 <: R, A1 >: A](alternative: Deserializer[R1, A1]): Deserializer[R1, A1]
}
```

If both deserializers fail, the result fails with the error of the alternative. To read amounts that are stored either as 8-byte numbers or as text:

```scala
unsafeRun(
  Serde.long
    .orElse(Serde.string.mapZIO(text => ZIO.attempt(text.toLong)))
    .deserialize("amounts", new RecordHeaders, "42".getBytes("UTF-8"))
)
// res14: Long = 42L
```

### Custom Serdes That Wrap Invalid Data

Any `Deserializer[R, A]` for a given type `A` can be converted into a `Deserializer[R, Try[A]]`, where deserialization failures are converted to a `Failure`, using the `Deserializer#asTry` method. `Serde#asTry` is also available; it is inherited from `Deserializer` and returns a `Deserializer`, so the result can only be used for consuming:

```scala
trait Deserializer[-R, +A] {
  def asTry: Deserializer[R, Try[A]]
}
```

The deserializer then always succeeds, and the failure becomes part of the value:

```scala
unsafeRun(Serde.int.asTry.deserialize("numbers", new RecordHeaders, Array[Byte](1, 2)))
// res15: util.Try[Int] = Failure(
//   exception = org.apache.kafka.common.errors.SerializationException: Size of data received by IntegerDeserializer is not 4
// )
```

`Deserializer#asTry` converts failures only. A defect, such as an exception thrown inside the function given to `Deserializer#map`, still fails the stream.

Below is an example of skipping records that fail to deserialize. The offset is passed downstream to be committed. The example uses `ZStream#mapZIO` for brevity; see [avoiding chunk-breakers](avoiding-chunk-breakers.md) before using it in production:

```scala
import zio._
import zio.kafka.consumer._
import zio.kafka.serde._
import scala.util.{Try, Success, Failure}

val consumerSettings = ConsumerSettings(List("localhost:9092")).withGroupId("group")
def someEffect(value: String): Task[Unit] = ???

val keySerde = Serde.string
val valueDeserializer = Serde.string.asTry   // <-- using `asTry`

val program: ZIO[Scope, Throwable, Unit] =
  Consumer.make(consumerSettings).flatMap { consumer =>
    consumer
      .plainStream(Subscription.topics("topic150"), keySerde, valueDeserializer)
      .mapZIO { record => // ⚠️ mapZIO is a chunk-breaker, see "Avoiding chunk-breakers"
        val tryValue: Try[String] = record.record.value()
        val offset: Offset = record.offset

        tryValue match {
          case Success(value) =>
            // Action for successful deserialization
            someEffect(value).as(offset)
          case Failure(exception) =>
            // Possibly log the exception or take alternative action
            ZIO.succeed(offset)
        }
      }
      .aggregateAsyncWithin(Consumer.collectOffsets, Schedule.fixed(100.millis))
      .mapZIO(_.commit)
      .runDrain
  }
```

The `Serde` companion object also has an implicit conversion that does the same as `Deserializer#asTry` for code that passes deserializers implicitly:

```scala
object Serde {
  implicit def deserializerWithError[R, A](implicit deser: Deserializer[R, A]): Deserializer[R, Try[A]]
}
```

The zio-kafka consumer methods take their deserializers as explicit arguments, so you rarely need it. It is defined on `Serde`, not on `Deserializer`, so it is not found automatically; import it to use it:

```scala
import zio.kafka.serde._
import zio.kafka.serde.Serde.deserializerWithError
import scala.util.Try

implicit val stringDeserializer: Deserializer[Any, String] = Serde.string

val tryDeserializer: Deserializer[Any, Try[String]] = implicitly[Deserializer[Any, Try[String]]]
```

### Deserialization in the Consumer Stream

In this approach we provide zio-kafka with the `Serde.byteArray` serde (which is a pass-through serde) and do the deserialization in the consumer stream. The deserialization can be done with regular ZIO operators.

This approach provides more freedom at the cost of having to write more code. It also allows for optimizations such as operating on chunks of records (see [avoiding chunk-breakers](avoiding-chunk-breakers.md)), and more contextual failure handling. When both the key and the value deserializer are `Serde.byteArray`, the consumer recognizes that exact instance and skips the deserialization step entirely. Other instances, such as `Deserializer.byteArray`, do not trigger this shortcut.

The following example deserializes each value in the stream and falls back to another effect when deserialization fails:

```scala
import zio._
import zio.kafka.consumer._
import zio.kafka.serde._

final case class Message(text: String)

val consumerSettings = ConsumerSettings(List("localhost:9092")).withGroupId("group")
def deserialize(value: Array[Byte]): ZIO[Any, Throwable, Message] = ???
def someEffect(value: Array[Byte]): ZIO[Any, Throwable, Message] = ???
def processMessage(message: Message): Task[Unit] = ???

val program: ZIO[Scope, Throwable, Unit] =
  Consumer.make(consumerSettings).flatMap { consumer =>
    consumer
      .plainStream(Subscription.topics("topic150"), Serde.byteArray, Serde.byteArray)
      .mapZIO { record => // ⚠️ mapZIO is a chunk-breaker, see "Avoiding chunk-breakers"
        val value: Array[Byte] = record.record.value()
        val offset: Offset = record.offset

        deserialize(value)
          // possible action to take if deserialization fails:
          .catchAll(_ => someEffect(value))
          .flatMap(processMessage)
          .as(offset)
      }
      .aggregateAsyncWithin(Consumer.collectOffsets, Schedule.fixed(100.millis))
      .mapZIO(_.commit)
      .runDrain
  }
```

To apply ordinary deserializers to such a record later in the stream, call `CommittableRecord#deserializeWith(keyDeserializer, valueDeserializer)`. It runs both deserializers with the record's topic and headers and returns a typed `CommittableRecord` with the same offset; see [CommittableRecord](consumer.md#committablerecord).

## Running on the Blocking Thread Pool

The fibers that run serdes share ZIO's default thread pool, which is sized for non-blocking work. A serde that blocks a thread, for example one that calls a remote service synchronously, should run on ZIO's blocking thread pool instead. `Serializer#blocking`, `Deserializer#blocking` and `Serde#blocking` do that:

```scala
trait Serializer[-R, -A] {
  def blocking: Serializer[R, A]
}

trait Deserializer[-R, +A] {
  def blocking: Deserializer[R, A]
}

trait Serde[-R, A] extends Deserializer[R, A] with Serializer[R, A] {
  override def blocking: Serde[R, A]
}
```

The result runs every call of the original inside `ZIO.blocking`. To run a wrapped Kafka serde, whose calls may block on network I/O, on the blocking thread pool:

```scala
import zio._
import zio.kafka.serde._
import org.apache.kafka.common.serialization.{ Serde => KafkaSerde }

def blockingSerde[A](kafkaSerde: KafkaSerde[A], props: Map[String, AnyRef]): Task[Serde[Any, A]] =
  Serde.fromKafkaSerde(kafkaSerde, props, isKey = false).map(_.blocking)
```

## A Warning About `mapZIO`

Moved to [avoiding chunk-breakers](avoiding-chunk-breakers.md).
