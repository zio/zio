# What's New Since 1.7.5

> This site documents ZIO Schema 1.9.1. The releases from 1.8.0 through 1.9.0 add one new module, raise the minimum Scala 3 version, and change the behavior of several codecs and derivation macros; 1.9.1 only updates dependencies. Most of the commits in that range update dependencies or fix the build, so this page lists only the changes that affect how your code behaves. If you upgrade from 1.7.5, read the behavior changes below before you rely on error handling in your decoders.

## Overview

## New XML Codec Module

Version 1.8.4 adds the `zio-schema-xml` module, which derives XML codecs from a schema. The module builds for the JVM, Scala.js, and Scala Native. For setup, configuration, and the `@xmlAttribute` and `@xmlNamespace` annotations, read [XML Codecs](./derivations/codecs/xml.md).

## Scala 3 Projects Need Scala 3.9

Version 1.9.0 compiles the Scala 3 artifacts with Scala 3.9.0, so a Scala 3 project that uses ZIO Schema 1.9.0 or later needs Scala 3.9 or later. An older Scala 3 compiler cannot read these artifacts. Scala 2.12 and 2.13 projects are not affected. [How to Use ZIO Schema with Scala 3](./how-to/scala-3.md) covers Scala 3 derivation.

## Behavior Changes

Several decoders return a `Left` value on malformed input, where earlier versions throw an exception. Code that wraps these decoders in a `try` block should match on the `Left` case instead. The list also includes two fixes that change what encoding and validation produce:

- Version 1.8.6 makes the JSON codec return `Left` when a `NonEmptyChunk`, `NonEmptySet`, or `NonEmptyMap` field is missing from the input. With the default configuration, a missing `Set` or `Map` field still decodes as an empty collection.
- Version 1.8.2 makes the JSON, Avro, BSON, and MessagePack codecs return `Left` when they decode an empty `NonEmptyChunk` or `NonEmptySet`.
- Version 1.8.1 fixes a `ClassCastException` that the JSON codec threw when it encoded a hierarchical enum.
- Version 1.8.2 makes `Validation.email` accept addresses whose local part starts with a digit, `_`, `+`, or `-`, and top-level domains of 2 to 63 characters instead of 2 to 4.

The following example shows the `Left` case for a missing `NonEmptyChunk` field:

```scala
import zio.NonEmptyChunk
import zio.schema.codec.JsonCodec
import zio.schema.{DeriveSchema, Schema}

final case class Tags(labels: NonEmptyChunk[String])

object Tags {
  implicit val schema: Schema[Tags] = DeriveSchema.gen
}
```

Decoding a payload with no `labels` field produces a `Left`, not an exception:

```scala
println(JsonCodec.jsonCodec(Tags.schema).decodeJson("""{}""").toString)
// Left(.labels(missing))
```

## Derivation Fixes

The `DeriveSchema` macro generates corrected schemas in the cases below.

- Version 1.8.0 fixes `genericTypeInfo` for abstract type parameters.
- Version 1.8.2 handles sealed traits nested inside other sealed traits.
- Version 1.8.4 applies the `simpleEnum` annotation only at the leaf level of a Scala 3 sealed hierarchy.
- Version 1.8.4 handles the overloaded `apply` method that case classes with 23 or more fields generate.

## Format Fixes

These fixes affect the output of specific codecs.

- Version 1.8.3 stops the Avro codec from throwing a `StackOverflowError` when it encodes a recursive sealed trait.
- Version 1.8.3 lets the BSON decoder read a `GenericRecord` that omits optional fields.
- Version 1.8.2 stops the BSON encoder from wrapping `Fallback.Left` and `Fallback.Right` values in an array.
- Version 1.8.2 lets the BSON encoder use a `Transform(Primitive)` schema as a map key.

## Documentation Added

Some pages document features that exist in 1.7.5 but were never covered on this site. [Schema Annotations](./schema-annotations.md) covers the annotations in `zio.schema.annotation`, such as `@fieldName`, `@discriminatorName`, and `@rejectExtraFields`. The [JSON Codecs](./derivations/codecs/json.md) page now has a Configuration section that describes `JsonCodec.Configuration`.
