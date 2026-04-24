# Serialization and Deserialization

> Zio-kafka deserializes incoming data, and deserializes outgoing data (both keys and values) from byte arrays to any
other type and back. This works by providing a key and value `Deserializer` while constructing a `Consumer`,
and a key and value `Serializer` while constructing the `Producer`.

Zio-kafka deserializes incoming data, and deserializes outgoing data (both keys and values) from byte arrays to any
other type and back. This works by providing a key and value `Deserializer` while constructing a `Consumer`,
and a key and value `Serializer` while constructing the `Producer`.

A `Serde` combines a `Deserializer` and a `Serializer`. Common serdes are provided in the `Serdes` object, e.g.
`Serdes.byteArray`, `Serdes.string` and `Serdes.long`. A serde can be converted to other serdes, or you can create a
custom serde by implementing the `Serde` trait directly.

This document contains:

- Handling failures in a serde
- How to create a custom serde
- How to create and use a custom serde that wraps invalid data
- How to do deserialization in the consumer stream

## Handling failures in a serde

Ideally, a serde can not fail serializing and deserializing. This is for example the case with the provided
`Serdes.byteArray` and `Serdes.string`. This is not the case for any serde that needs to handle invalid input,
(for example `Serdes.long`), or a serde that needs to do a remote lookup.

By default, a consumer stream will fail if it encounters a deserialization error in the serde. Unfortunately, the
resulting failure might not clearly indicate that the cause is in the serde.

There are 2 solutions for improving this:

- Wrap the result of the serde in a `Try` with the `Serde.asTry` method.
- Use `Serdes.byteArray`, put the deserialization code in the consumer stream, or do serialization before handing the
  data to zio-kafka. This way you can handle failures any way you want.

Both approaches are discussed further below.

## Custom Data Type Serdes

Serializers and deserializers for custom data types can be created from scratch, or by converting existing
serdes. For example, to create a serde for an `Instant` from a serde for a `Long`:

```scala

val instantSerde: Serde[Any, Instant] =
  Serdes.long.inmap(java.time.Instant.ofEpochMilli)(_.toEpochMilli)
```

To handle missing data (an empty key or value), you can use the `Serde.asOption` transformer. For example:
`Serdes.string.asOption`. This results in a `None` if the key or value is empty, and in a `Some(string)` otherwise.

## Custom serdes that wraps invalid data

Any `Deserializer[A]` for a given type `A` can be converted into  a `Deserializer[Try[A]]` where deserialization
failures are converted to a `Failure` using the `asTry` method. (Method `asTry` is also available on `Serde`.)

Below is an example of skipping records that fail to deserialize. The offset is passed downstream to be committed.

```scala

val keySerde = Serdes.string
val valueSerde = Serdes.string.asTry   // <-- using `.asTry`

Consumer.make(consumerSettings).flatMap { consumer =>
  consumer
    .plainStream(Subscription.topics("topic150"), keySerde, valueSerde)
    .mapZIO { record => // ⚠️ see section about `mapZIO` below!
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
    .aggregateAsync(Consumer.offsetBatches)
    .mapZIO(_.commit)
    .runDrain
}
```

## Deserialization in the consumer stream

In this approach we provide zio-kafka with the `Serdes.byteArray` serde (which is a pass-through serde) and do the
deserialization in the consumer stream. The deserialization can be done with regular ZIO operators.

This approach provides more freedom at the cost of having to write more code. It also allows for optimizations such as
operating on chunks of records (see [avoiding chunk-breakers](avoiding-chunk-breakers.md)), and more contextual failure
handling.

Here is an example:

```scala

def deserialize(value: Array[Byte]): ZIO[Any, Throwable, Message] = ???

Consumer.make(consumerSettings).flatMap { consumer =>
  consumer
    .plainStream(Subscription.topics("topic150"), Serde.byteArray, Serde.byteArray)
    .mapZIO { record => // ⚠️ see section about `mapZIO` below!
      val value: Array[Byte] = record.record.value()
      val offset: Offset = record.offset

      deserialize(value)
        // possible action to take if deserialization fails:
        .recoverWith(_ => someEffect(value))
        .flatMap(processMessage)
        .as(offset)
    }
    .aggregateAsync(Consumer.offsetBatches)
    .mapZIO(_.commit)
    .runDrain
}
```

## A warning about `mapZIO`

Moved to [avoiding chunk-breakers](avoiding-chunk-breakers.md).
