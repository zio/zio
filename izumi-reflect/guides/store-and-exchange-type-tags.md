# Store and Exchange Type Tags

> Save izumi-reflect type tags as text, read them back in another process, and handle format versions safely.

In this guide, we save the full type of a value next to the value itself, so that another process, or the same program after a restart, can read it back and check it against the type it expects. We start by turning a type tag into text, then read it back and compare it, and finish with the version check that keeps old readers from misreading tags written by newer code.

## The Problem

Persisted data often needs a record of its type: an event store keeps the type of each event, a message bus labels each message, a cache stores values of different types side by side. The obvious label is the class name, but the JVM class of a value has lost its type arguments, and it is often an implementation class rather than the type the program works with.

For example, consider labelling stored values with their class names:

```scala
def classLabel(value: Any): String = value.getClass.getName

classLabel(List(1, 2, 3))
// res0: String = "scala.collection.immutable.$colon$colon"
classLabel(List("a", "b"))
// res1: String = "scala.collection.immutable.$colon$colon"
classLabel(Map("retries" -> 3))
// res2: String = "scala.collection.immutable.Map$Map1"
```

Both lists get the label of the list cell class, `::`, and the type arguments `Int` and `String` are gone. A reader can't tell a `List[Int]` from a `List[String]`, so a wrong cast only fails later, far from the cause. A `LightTypeTag` describes the full type, and it can be written out and read back.

## Prerequisites

Add `izumi-reflect` to your build:

```scala
libraryDependencies += "dev.zio" %% "izumi-reflect" % "3.0.12-SNAPSHOT"
```

The guide uses `LightTypeTag#serialize` and `LightTypeTag.parse`, described in [LightTypeTag](../reference/light-type-tag.md#serialization). All examples use these imports:

```scala
import izumi.reflect.Tag
import izumi.reflect.macrortti.LightTypeTag

import java.nio.charset.StandardCharsets.ISO_8859_1
import java.util.Base64
```

## Turning a Tag into Text

`LightTypeTag#serialize` returns a `LightTypeTag.Serialized`, a case class of four fields: a hash code, the structure of the type, the inheritance data used by subtype checks, and the format version. To serialize the tag of a type:

```scala
val serialized: LightTypeTag.Serialized = Tag[Map[String, List[Int]]].tag.serialize()
```

The two strings hold binary data: each character stands for one byte, in the ISO-8859-1 encoding, and many are control characters. Text columns, JSON and log lines can mangle them, so encode each string as Base64 before storing it. We keep the four fields in a small record of our own, which is what the storage layer sees:

```scala
final case class StoredType(hash: Int, ref: String, databases: String, version: Int)

def toBase64(binary: String): String =
  Base64.getEncoder.encodeToString(binary.getBytes(ISO_8859_1))

def store(tag: LightTypeTag): StoredType = {
  val s = tag.serialize()
  StoredType(s.hash, toBase64(s.ref), toBase64(s.databases), s.version)
}
```

The stored record contains only printable text and two numbers. To store the type of a `Map[String, List[Int]]` and look at the result:

```scala
val stored = store(Tag[Map[String, List[Int]]].tag)
// stored: StoredType = StoredType(
//   hash = -472871146,
//   ref = "AQEBHnNjYWxhLmNvbGxlY3Rpb24uaW1tdXRhYmxlLk1hcAIABAABEGphdmEubGFuZy5TdHJpbmcBAQAAAQEBH3NjYWxhLmNvbGxlY3Rpb24uaW1tdXRhYmxlLkxpc3QBAAQAAQlzY2FsYS5JbnQBAQIBAgE=",
//   databases = "AAYEAAEQamF2YS5sYW5nLlN0cmluZwEBBQEBARRqYXZhLmxhbmcuQ29tcGFyYWJsZQEABAABkAIBAQABBAABFmphdmEubGFuZy5DaGFyU2VxdWVuY2UBAQQAARRqYXZhLmlvLlNlcmlhbGl6YWJsZQEBBAABHGphdmEubGFuZy5jb25zdGFudC5Db25zdGFibGUBAQQAAR9qYXZhLmxhbmcuY29uc3RhbnQuQ29uc3RhbnREZXNjAQEDAQEAAAABAQEBH3NjYWxhLmNvbGxlY3Rpb24uaW1tdXRhYmxlLkxpc3QBAAQAAwAAAQEBAgEbAwEBAAAAAQEBAR1zY2FsYS5jb2xsZWN0aW9uLkl0ZXJhYmxlT25jZQEABAADAAABAQECAQMBAQAAAAEBAQEgc2NhbGEuY29sbGVjdGlvbi5JdGVyYWJsZU9uY2VPcHMDAAQAAwAAAQEBAgADAQEAAAEBAQEBkAgBAAQAAwABAQEBAgECAAEBAZAIAQAEAAMAAAEBAQIBAgEDAQEAAAABAQEBGnNjYWxhLmNvbGxlY3Rpb24uTGluZWFyU2VxAQAEAAMAAAEBAQIBAwEBAAAAAQEBAQ9zY2FsYS5GdW5jdGlvbjECAAQAAQlzY2FsYS5JbnQBAQEABAADAAABAQECAQMBAQAAAAEBAQEUc2NhbGEuY29sbGVjdGlvbi5TZXEBAAQAAwAAAQEBAgEEAAEsc2NhbGEuY29sbGVjdGlvbi5nZW5lcmljLkRlZmF1bHRTZXJpYWxpemFibGUBAQMBAQAAAAEBAQEXc2NhbGEuY29sbGVjdGlvbi5TZXFPcHMDAAQAAwAAAQEBAgADAQEAAAEBAQEBkAgBAAQAAwABAQEBAgECAAEBAZAIAQAEAAMAAAEBAQIBAgEDAQEAAAABAQEBHHNjYWxhLmNvbGxlY3Rpb24uQWJzdHJhY3RTZXEBAAQAAwAAAQEBAgEDAQEAAAABAQEBFXNjYWxhLlBhcnRpYWxGdW5jdGlvbgIABAABkA0BAQEABAADAAABAQECAQMBAQAAAAEBAQEjc2NhbGEuY29sbGVjdGlvbi5pbW11dGFibGUuSXRlcmFibGUBAAQAAwAAAQEBAgEDAQEAAAABAQEBJHNjYWxhLmNvbGxlY3Rpb24uaW1tdXRhYmxlLkxpbmVhclNlcQEABAADAAABAQEC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...
```

<details>
  <summary>Runnable version, with the serialization and the stored record in one program: <code>Step3TurningIntoTextExample.scala</code></summary>

```scala title="izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/storeandexchangetypetags/Step3TurningIntoTextExample.scala" showLineNumbers
//> using scala 3.3.8
//> using dep dev.zio::izumi-reflect:3.0.11
//> using file StoredTypes.scala
// Run from this folder:  scala-cli run Step3TurningIntoTextExample.scala
// Or from the repository root:  sbt "izumi-reflect-examples/runMain izumi.reflect.examples.storeandexchangetypetags.Step3TurningIntoTextExample"

package izumi.reflect.examples.storeandexchangetypetags

import izumi.reflect.Tag
import izumi.reflect.macrortti.LightTypeTag
import StoredTypes.store

/**
  * Store and Exchange Type Tags ? Turning a Tag into Text
  *
  * Serializes a tag and stores it as a record of Base64 text and two numbers.
  */
object Step3TurningIntoTextExample {
  def main(args: Array[String]): Unit = {
    val serialized: LightTypeTag.Serialized = Tag[Map[String, List[Int]]].tag.serialize()
    println(s"serialized.version: ${serialized.version}")

    val stored = store(Tag[Map[String, List[Int]]].tag)
    println(s"stored: $stored")
  }
}
```

</details>

## Reading a Tag Back

To read a tag back, decode the two strings and pass the four fields to `LightTypeTag.parse`. The result is a `LightTypeTag` like any other, equal to the tag of the same type created by the compiler:

```scala
def fromBase64(text: String): String =
  new String(Base64.getDecoder.decode(text), ISO_8859_1)

def load(stored: StoredType): LightTypeTag =
  LightTypeTag.parse(LightTypeTag.Serialized(stored.hash, fromBase64(stored.ref), fromBase64(stored.databases), stored.version))
```

To read the stored tag back and compare it with the tag the reader expects:

```scala
val loaded = load(stored)
// loaded: LightTypeTag = Map[=String,+List[+Int]]

loaded
// res3: LightTypeTag = Map[=String,+List[+Int]]
loaded == Tag[Map[String, List[Int]]].tag
// res4: Boolean = true
loaded == Tag[Map[String, List[String]]].tag
// res5: Boolean = false
```

The loaded tag keeps the inheritance data, so subtype checks work too. A reader that accepts any `Map[String, Iterable[Int]]` can check the stored type with `LightTypeTag#<:<`:

```scala
loaded <:< Tag[Map[String, Iterable[Int]]].tag
// res6: Boolean = true
```

<details>
  <summary>Runnable version, with the record stored in "Turning a Tag into Text": <code>Step4ReadingBackExample.scala</code></summary>

```scala title="izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/storeandexchangetypetags/Step4ReadingBackExample.scala" showLineNumbers
//> using scala 3.3.8
//> using dep dev.zio::izumi-reflect:3.0.11
//> using file StoredTypes.scala
// Run from this folder:  scala-cli run Step4ReadingBackExample.scala
// Or from the repository root:  sbt "izumi-reflect-examples/runMain izumi.reflect.examples.storeandexchangetypetags.Step4ReadingBackExample"

package izumi.reflect.examples.storeandexchangetypetags

import izumi.reflect.Tag
import StoredTypes.{load, store}

/**
  * Store and Exchange Type Tags ? Reading a Tag Back
  *
  * Reads the stored record of "Turning a Tag into Text" back into a LightTypeTag and compares it.
  */
object Step4ReadingBackExample {
  def main(args: Array[String]): Unit = {
    // Setup from "Turning a Tag into Text": the type of a Map[String, List[Int]], stored as text
    val stored = store(Tag[Map[String, List[Int]]].tag)

    val loaded = load(stored)

    println(s"loaded: $loaded")
    println(s"loaded == Tag[Map[String, List[Int]]].tag: ${loaded == Tag[Map[String, List[Int]]].tag}")
    println(s"loaded == Tag[Map[String, List[String]]].tag: ${loaded == Tag[Map[String, List[String]]].tag}")
    println(s"loaded <:< Tag[Map[String, Iterable[Int]]].tag: ${loaded <:< Tag[Map[String, Iterable[Int]]].tag}")
  }
}
```

</details>

## Checking Stored Values Against an Expected Type

With tags that survive storage, a reader can refuse values whose stored type does not match what it expects, instead of casting blindly. An envelope keeps the value next to its stored type, and the reader compares before it casts:

```scala
final case class Envelope(payload: Any, payloadType: StoredType)

def seal[A: Tag](payload: A): Envelope = Envelope(payload, store(Tag[A].tag))

def open[A: Tag](envelope: Envelope): Either[String, A] = {
  val actual = load(envelope.payloadType)
  if (actual <:< Tag[A].tag) Right(envelope.payload.asInstanceOf[A])
  else Left(s"expected ${Tag[A].tag}, but the envelope holds $actual")
}
```

To seal a `List[Int]` and open it as a matching and as a non-matching type:

```scala
val envelope = seal(List(1, 2, 3))
// envelope: Envelope = Envelope(
//   payload = List(1, 2, 3),
//   payloadType = StoredType(
//     hash = -1818167878,
//     ref = "AQEBH3NjYWxhLmNvbGxlY3Rpb24uaW1tdXRhYmxlLkxpc3QBAAQAAQlzY2FsYS5JbnQBAQIB",
//     databases = "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...

open[Seq[Int]](envelope)
// res7: Either[String, Seq[Int]] = Right(List(1, 2, 3))
open[List[String]](envelope)
// res8: Either[String, List[String]] = Left(
//   "expected List[+String], but the envelope holds List[+Int]"
// )
```

In a real system, the payload is serialized too, for example as JSON. The stored type then decides which decoder to use, and the check stops a reader from decoding a payload with the wrong one.

<details>
  <summary>Runnable version, with `store` and `load` from the previous sections: <code>Step5EnvelopeExample.scala</code></summary>

```scala title="izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/storeandexchangetypetags/Step5EnvelopeExample.scala" showLineNumbers
//> using scala 3.3.8
//> using dep dev.zio::izumi-reflect:3.0.11
//> using file StoredTypes.scala
// Run from this folder:  scala-cli run Step5EnvelopeExample.scala
// Or from the repository root:  sbt "izumi-reflect-examples/runMain izumi.reflect.examples.storeandexchangetypetags.Step5EnvelopeExample"

package izumi.reflect.examples.storeandexchangetypetags

import izumi.reflect.Tag
import StoredTypes.{load, store}

/**
  * Store and Exchange Type Tags ? Checking Stored Values Against an Expected Type
  *
  * Seals a value with its stored type and opens it as a matching and a non-matching type, using store and load from the previous sections.
  */
object Step5EnvelopeExample {
  final case class Envelope(payload: Any, payloadType: StoredType)

  def seal[A: Tag](payload: A): Envelope = Envelope(payload, store(Tag[A].tag))

  def open[A: Tag](envelope: Envelope): Either[String, A] = {
    val actual = load(envelope.payloadType)
    if (actual <:< Tag[A].tag) Right(envelope.payload.asInstanceOf[A])
    else Left(s"expected ${Tag[A].tag}, but the envelope holds $actual")
  }

  def main(args: Array[String]): Unit = {
    val envelope = seal(List(1, 2, 3))

    println(s"open[Seq[Int]]: ${open[Seq[Int]](envelope)}")
    println(s"open[List[String]]: ${open[List[String]](envelope)}")
  }
}
```

</details>

## Handling Format Versions

The `version` field is the binary format version of the two strings. `LightTypeTag.parse` reads tags written by the same or an older version of `izumi-reflect`. A tag written by a newer version can't be read: `LightTypeTag.parse` throws a `java.lang.LinkageError` that asks for a newer `izumi-reflect` jar. The version the library on the classpath writes is `LightTypeTag.currentBinaryFormatVersion`.

`LinkageError` counts as a fatal error in Scala, so `scala.util.Try` does not catch it. Catch it explicitly where tags from other processes are read:

```scala
def loadChecked(stored: StoredType): Either[String, LightTypeTag] =
  try Right(load(stored))
  catch {
    case e: LinkageError => Left(s"cannot read type tag: ${e.getMessage.linesIterator.next()}")
  }
```

To see what happens with a tag from a future version, we change the version of the stored record:

```scala
LightTypeTag.currentBinaryFormatVersion
// res9: Int = 30

loadChecked(stored)
// res10: Either[String, LightTypeTag] = Right(Map[=String,+List[+Int]])
loadChecked(stored.copy(version = LightTypeTag.currentBinaryFormatVersion + 1))
// res11: Either[String, LightTypeTag] = Left(
//   "cannot read type tag: Couldn't parse a `LightTypeTag` version=31 generated by a newer version of `izumi-reflect`,"
// )
```

When services of different versions exchange tags, upgrade `izumi-reflect` in the readers before the writers. A reader on a newer version reads every older tag; a reader on an older version fails on tags from a newer one.

:::note
Tags from an older format are read without their stored hash code, so the first comparisons are a little slower. Tags written by the current format compare by their serialized strings first, which is fast.
:::

<details>
  <summary>Runnable version, with the record stored in "Turning a Tag into Text": <code>Step6FormatVersionsExample.scala</code></summary>

```scala title="izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/storeandexchangetypetags/Step6FormatVersionsExample.scala" showLineNumbers
//> using scala 3.3.8
//> using dep dev.zio::izumi-reflect:3.0.11
//> using file StoredTypes.scala
// Run from this folder:  scala-cli run Step6FormatVersionsExample.scala
// Or from the repository root:  sbt "izumi-reflect-examples/runMain izumi.reflect.examples.storeandexchangetypetags.Step6FormatVersionsExample"

package izumi.reflect.examples.storeandexchangetypetags

import izumi.reflect.Tag
import izumi.reflect.macrortti.LightTypeTag
import StoredTypes.{load, store}

/**
  * Store and Exchange Type Tags ? Handling Format Versions
  *
  * Reads a stored tag with an explicit LinkageError check, and shows the error for a tag from a newer format version.
  */
object Step6FormatVersionsExample {
  def loadChecked(stored: StoredType): Either[String, LightTypeTag] =
    try Right(load(stored))
    catch {
      case e: LinkageError => Left(s"cannot read type tag: ${e.getMessage.linesIterator.next()}")
    }

  def main(args: Array[String]): Unit = {
    // Setup from "Turning a Tag into Text": the type of a Map[String, List[Int]], stored as text
    val stored = store(Tag[Map[String, List[Int]]].tag)

    println(s"currentBinaryFormatVersion: ${LightTypeTag.currentBinaryFormatVersion}")
    println(s"loadChecked(stored): ${loadChecked(stored)}")
    println(s"loadChecked(newer version): ${loadChecked(stored.copy(version = LightTypeTag.currentBinaryFormatVersion + 1))}")
  }
}
```

</details>

## Running the Examples

All runnable code from this guide is in [`izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/storeandexchangetypetags/`](https://github.com/zio/izumi-reflect/tree/develop/izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/storeandexchangetypetags). Run any file from that folder with `scala-cli run <File>.scala`, or from the repository root with `sbt "izumi-reflect-examples/runMain izumi.reflect.examples.storeandexchangetypetags.<ObjectName>"`.

## Going Further

The stored type works well as a key for a registry of decoders, or as the type label of events in an event store. For more details:

- [LightTypeTag](../reference/light-type-tag.md#serialization) covers `LightTypeTag#serialize`, `LightTypeTag.parse` and the Java serialization support of tags.
- [Build a Type-Safe Heterogeneous Map](heterogeneous-map.md) shows exact and supertype lookups by tag.
- [Debug a Wrong Tag or Subtype Check](debug-a-wrong-tag.md) helps when a stored type unexpectedly does not match.
