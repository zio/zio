# Serialization

> Store typed keys and values in RocksDB by composing Serializer, Deserializer and Serde values, and handle decoding failures with DeserializeError.

RocksDB stores keys and values as raw bytes, so every `RocksDB` operation takes and returns `Array[Byte]`. In this guide we build encoders and decoders for domain types out of small, composable pieces: `Serializer`, `Deserializer` and `Serde`. By the end, you will have a typed `RocksDB.put` and `RocksDB.get` for a case class that converts to bytes and back without hand-written buffer code.

We start with the primitive encoders that ship with the library, then compose them into larger ones, handle decoding failures, and finally connect the result to a database.

## The Problem

Without help, every call site converts its own types to bytes and back. Fixed-width numbers need a `ByteBuffer`, and a record with several fields needs the reader to know the exact layout the writer used:

```scala
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8

def encodeUser(id: Long, name: String): Array[Byte] = {
  val nameBytes = name.getBytes(UTF_8)
  val buf       = ByteBuffer.allocate(8 + nameBytes.length)
  buf.putLong(id)
  buf.put(nameBytes)
  buf.array()
}

def decodeUser(bytes: Array[Byte]): (Long, String) = {
  val buf  = ByteBuffer.wrap(bytes)
  val id   = buf.getLong()
  val rest = new Array[Byte](bytes.length - 8)
  buf.get(rest)
  (id, new String(rest, UTF_8))
}
```

This code is easy to get subtly wrong: a truncated array throws a `BufferUnderflowException` instead of a typed failure, the layout is duplicated across two functions, and nothing makes the writer and the reader agree. ZIO RocksDB replaces this with encoders and decoders that are values, which can be combined and reused.

## The Building Blocks

A `Serializer[R, A]` turns an `A` into `Bytes`, where `Bytes` is an alias for `Chunk[Byte]`. A `Deserializer[R, A]` does the reverse, and a `Serde[R, A]` is both at once:

```scala
trait Serializer[-R, -A] {
  def apply(a: A): URIO[R, Bytes]
}

trait Deserializer[-R, +A] {
  def decode(bytes: Bytes): ZIO[R, DeserializeError, Result[A]]
}

trait Serde[-R, A] extends Serializer[R, A] with Deserializer[R, A]
```

Encoding cannot fail, so `Serializer#apply` returns a `URIO`. Decoding can fail with a `DeserializeError`. A decoder does not need to consume all of its input: it returns a `Result`, which pairs the decoded `value` with the `remainder` bytes it did not use. This is what allows decoders to be chained, because the next decoder starts from the remainder of the previous one.

The `R` type parameter is the environment an encoder or decoder needs. Every built-in one uses `Any`, so none of them requires a service.

To run effects in the examples below, we define a small helper that runs a `ZIO` value to completion:

```scala
import zio._
import zio.rocksdb._

def run[E, A](effect: ZIO[Any, E, A]): A =
  Unsafe.unsafe(implicit u => Runtime.default.unsafe.run(effect).getOrThrowFiberFailure())
```

## Encoding Primitives

The `Serializer` companion object provides encoders for `Boolean`, `Byte`, `Char`, `Double`, `Float`, `Int`, `Long` and `Short`, and the `Deserializer` companion object provides the matching decoders. Numbers are written in big-endian order with their natural fixed width.

To encode an `Int` and decode it back:

```scala
val encoded = run(Serializer.int(42))
// encoded: Bytes = Chunk.ByteArray(0, 0, 0, 42)

run(Deserializer.int.decode(encoded))
// res1: Result[Int] = Result(42, Chunk.Empty())
```

The decoded `Result` holds the value `42` and an empty remainder, because the decoder consumed all four bytes. Decoding fails with a `DeserializeError` when the input is malformed, which we cover in [Handling Decoding Errors](#handling-decoding-errors).

:::note
RocksDB compares keys byte by byte as unsigned values. A big-endian encoding of a non-negative `Int` or `Long` therefore sorts in numeric order, which makes these encoders a good fit for keys that are scanned in order. Negative numbers sort after all positive numbers, because their first byte is greater than or equal to `0x80`.
:::

## Encoding Strings and Raw Bytes

`Serializer.string` encodes a `String` as UTF-8, and `Serializer.bytes` and `Serializer.byteArray` pass bytes through unchanged. On the decoding side, `Deserializer.bytes` and `Deserializer.byteArray` hand back everything that is left in the input, and there is no ready-made `Deserializer` for `String`. To get one, map the raw bytes:

```scala
import java.nio.charset.StandardCharsets.UTF_8

val stringDeserializer: Deserializer[Any, String] =
  Deserializer.bytes.map(bytes => new String(bytes.toArray, UTF_8))
```

This decoder consumes the entire input, so it is only correct for a value that stands alone or sits last in a composite. A string in the middle of a record needs a length prefix, which we add in [Composing Encoders for a Record](#composing-encoders-for-a-record).

Using the encoder and the decoder together:

```scala
run(Serializer.string("hello").flatMap(stringDeserializer.decode))
// res2: Result[String] = Result("hello", Chunk.Empty())
```

## Composing Encoders for a Record

We model the record we want to store as a case class:

```scala
final case class User(id: Long, name: String, active: Boolean)
```

To build a serializer for `User` out of serializers for its fields, there are three combinators on `Serializer`:

- `Serializer#contramap` adapts a serializer to another type by converting the input first.
- `Serializer#divide` pairs two serializers into a serializer of tuples that concatenates their output.
- `Serializer#divideWith` is `Serializer#divide` combined with a function that splits a value of another type into the two parts.

A string must carry its own length when it is followed by other fields, so we write a length-prefixed string serializer first:

```scala
val lengthPrefixedString: Serializer[Any, String] =
  Serializer[Any, String] { s =>
    for {
      body   <- Serializer.string(s)
      length <- Serializer.int(body.length)
    } yield length ++ body
  }
```

The serializer for `User` splits the record into its fields and concatenates the encoded pieces in order:

```scala
val userSerializer: Serializer[Any, User] =
  Serializer.long
    .divide(lengthPrefixedString)
    .divide(Serializer.boolean)
    .contramap(u => ((u.id, u.name), u.active))
```

Decoders compose the same way. `Deserializer#zip` runs two decoders one after the other, feeding the remainder of the first into the second, and `Deserializer#map` converts the decoded value. `Deserializer#flatMap` lets a later decoder depend on an earlier value, which is what a length prefix needs:

```scala
val lengthPrefixedStringDeserializer: Deserializer[Any, String] =
  Deserializer.int.flatMap { length =>
    Deserializer[Any, Bytes] { bytes =>
      val (head, tail) = bytes.splitAt(length)
      if (head.length < length) ZIO.fail(DeserializeError.TooShort(bytes.length, length))
      else ZIO.succeed(Result(head, tail))
    }.map(body => new String(body.toArray, UTF_8))
  }

val userDeserializer: Deserializer[Any, User] =
  Deserializer.long
    .zip(lengthPrefixedStringDeserializer)
    .zip(Deserializer.boolean)
    .map { case ((id, name), active) => User(id, name, active) }
```

A round trip shows that the two sides agree on the layout. The encoded value is 8 bytes for the id, 4 for the name length, 3 for the name and 1 for the flag:

```scala
val userBytes = run(userSerializer(User(7L, "Ada", true)))
// userBytes: Bytes = Chunk.Concat(
//   0,
//   0,
//   0,
//   0,
//   0,
//   0,
//   0,
//   7,
//   0,
//   0,
//   0,
//   3,
//   65,
//   100,
//   97,
//   1
// )

userBytes.length
// res3: Int = 16

run(userDeserializer.decode(userBytes))
// res4: Result[User] = Result(User(7L, "Ada", true), Chunk.Empty())
```

Tuples have shortcuts. `Serializer.tuple2` through `Serializer.tuple22` take the element serializers as implicit parameters and produce a serializer of the tuple, and `Deserializer.tuple2` through `Deserializer.tuple22` do the same for decoders.

## Optional and Alternative Values

`Serializer.option` and `Serializer.either` encode `Option` and `Either` values, but neither writes a marker for which case was chosen. `Serializer.option` writes no bytes for `None` and the inner encoding for `Some`, and `Serializer.either` writes the encoding of whichever side is present. The same holds for `Serializer#choose` and `Serializer#chooseWith`.

Because the bytes carry no tag, the library provides no decoder for them. When you need to store an optional value, write the tag yourself, as in this serializer and deserializer pair for `Option[Long]`:

```scala
val optionalLong: Serde[Any, Option[Long]] =
  Serde(
    Serializer[Any, Option[Long]] {
      case None    => Serializer.boolean(false)
      case Some(l) => Serializer.boolean(true).zipWith(Serializer.long(l))(_ ++ _)
    },
    Deserializer.boolean.flatMap {
      case true  => Deserializer.long.map(Option(_))
      case false => Deserializer[Any, Option[Long]](bytes => ZIO.succeed(Result(None, bytes)))
    }
  )
```

Both `None` and `Some` round trip:

```scala
List(Option.empty[Long], Some(5L)).map { value =>
  run(optionalLong(value).flatMap(optionalLong.decode)).value
}
// res5: List[Option[Long]] = List(None, Some(5L))
```

## Encoding Collections

For collections of fixed-width primitives, `Serializer.ints[List]`, `Serializer.longs`, `Serializer.doubles` and the matching methods for `Char`, `Float` and `Short` write the elements back to back. For other element types, `Serializer.list`, `Serializer.vector` and `Serializer.chunk` take an element serializer, and `Serializer.map` takes a key and a value serializer:

```scala
val scores = Map(1 -> 10.toShort, 2 -> 20.toShort)
// scores: Map[Int, Short] = Map(1 -> 10, 2 -> 20)

val scoresBytes = run(Serializer.map(Serializer.int, Serializer.short)(scores))
// scoresBytes: Bytes = Chunk.Concat(0, 0, 0, 1, 0, 10, 0, 0, 0, 2, 0, 20)

scoresBytes.length
// res6: Int = 12
```

Decoding a collection is a two-step construction. `Deserializer.chunk` decodes elements one after another until the input runs out, and `Deserializer.list`, `Deserializer.vector`, `Deserializer.set` and `Deserializer.map` convert the resulting `Chunk`:

```scala
val scoresDeserializer: Deserializer[Any, Map[Int, Short]] =
  Deserializer.map(Deserializer.chunk(Deserializer.int.zip(Deserializer.short)))
```

Decoding the bytes produced above returns the original map:

```scala
run(scoresDeserializer.decode(scoresBytes))
// res7: Result[Map[Int, Short]] = Result(Map(1 -> 10, 2 -> 20), Chunk.Empty())
```

:::warning
A collection has no length prefix, so `Deserializer.chunk` stops at the first element that does not fit in the remaining bytes and returns the leftover bytes as the `remainder`: decoding the bytes of two `Int` values followed by two extra bytes yields both integers and a two-byte remainder. Use it for collections of fixed-width elements or for a collection stored last in a value. For variable-width elements such as strings, prefix each element with its length, as in `lengthPrefixedString` above.
:::

## Handling Decoding Errors

Decoders fail with a `DeserializeError`, a sealed class with a `message` and three cases. `DeserializeError.TooShort` reports that the input has fewer bytes than the decoder needs, `DeserializeError.UnexpectedByte` reports a byte outside the accepted set, and `DeserializeError.ChunkError` wraps a `Throwable` raised while decoding. The library's own decoders never produce `ChunkError`; it exists for custom decoders that call code which can throw.

Decoding four bytes of input with a decoder that needs eight fails with `TooShort`, and a boolean decoder rejects any byte other than 0 or 1 with `UnexpectedByte`:

```scala
run(Deserializer.long.decode(Chunk[Byte](0, 0, 0, 1)).either)
// res8: Either[DeserializeError, Result[Long]] = Left(TooShort(4, 8))

run(Deserializer.boolean.decode(Chunk[Byte](2)).either)
// res9: Either[DeserializeError, Result[Boolean]] = Left(
//   UnexpectedByte(2, List(0, 1))
// )
```

Because the error type is `DeserializeError` rather than `Throwable`, a corrupt value can be handled separately from a storage failure, for example by skipping the entry or reporting it. `DeserializeError` is not a `Throwable`, so a decoding failure cannot be raised into a `Task` without wrapping it, as we do next.

## Reading and Writing Typed Values

A `Serde` bundles a serializer and a deserializer for the same type, so that a single value can be passed around. `Serde.apply` builds one from the two halves:

```scala
val userSerde: Serde[Any, User] = Serde(userSerializer, userDeserializer)
```

To connect it to a database, we write a typed `putUser` and a typed `getUser` on top of the byte-level `RocksDB` operations. The `getUser` function turns a decoding failure into a `Throwable` so that it fits the `Task` returned by `RocksDB`:

```scala
import zio._
import zio.rocksdb._

final case class User(id: Long, name: String, active: Boolean)

def userSerde: Serde[Any, User] = ???

def putUser(user: User): ZIO[RocksDB, Throwable, Unit] =
  for {
    key   <- Serializer.long(user.id)
    value <- userSerde(user)
    _     <- RocksDB.put(key.toArray, value.toArray)
  } yield ()

def getUser(id: Long): ZIO[RocksDB, Throwable, Option[User]] =
  for {
    key   <- Serializer.long(id)
    found <- RocksDB.get(key.toArray)
    user <- ZIO.foreach(found) { bytes =>
             userSerde
               .decode(Chunk.fromArray(bytes))
               .mapBoth(error => new RuntimeException(error.message), _.value)
           }
  } yield user
```

The same pattern applies inside a [transaction](./transactions.md) and for the keys and values of a [scan](./iterators.md), where each `(Array[Byte], Array[Byte])` pair is decoded with the same `Serde`.
