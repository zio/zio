# How ZIO JSON Works

> Why ZIO JSON is fast and safe: a reader-based decoder, compile-time derivation without runtime reflection, and early exit on bad input.

This page explains the ideas behind ZIO JSON. You do not need any of it to use the library. It helps when you wonder why the API looks the way it does, or why a decode failed the way it did.

## Decoding Reads Straight Into Your Types

Most JSON libraries parse the whole text into a tree of JSON nodes first, then convert the tree into your types. ZIO JSON skips the tree for normal decoding. A decoder reads characters from the input and builds your values as it goes.

The input is wrapped in a small reader. The reader hands out one character at a time and can step back by one character (`retract`). Stepping back matters for numbers: a number has no end marker, so the decoder only learns it has finished when it reads the next character. It then pushes that character back for the next step to see.

Several readers exist for different inputs:

- `FastStringReader` for a `String` or other `CharSequence`.
- `Utf8ChunkReader` for UTF-8 bytes in a `Chunk[Byte]`, read without first copying them into a `String`.
- `WithRetractReader` for any `java.io.Reader`, which is how streaming input is handled.

They live in `zio.json.internal`, and you normally reach them through `fromJson` and the decoding methods on `JsonDecoder`. `FastStringReader` and `Utf8ChunkReader` are private to the library. `RetractReader` is the public type that `unsafeDecode` receives, and `WithRetractReader` is public, so a hand-written decoder can be called directly on any `java.io.Reader`, for example in a test. See [Writing a Decoder by Hand](../reference/json-decoder.md#writing-a-decoder-by-hand).

The `Json` tree type does exist, and you can decode to it. See the [JSON AST reference](../reference/json-ast.md). It is a choice you make, not a step the library forces on you.

## No Reflection at Runtime

A `JsonEncoder` or `JsonDecoder` for a case class or sealed trait is plain code. That code is written for you at compile time, when you call `DeriveJsonCodec.gen` (or write `derives` on Scala 3). What the generated code does is controlled by [annotations](../reference/annotations.md) and by [`JsonCodecConfiguration`](../reference/json-codec-configuration.md), both read at compile time.

- On Scala 2, `DeriveJsonEncoder.gen`, `DeriveJsonDecoder.gen` and `DeriveJsonCodec.gen` are macros. The encoder and decoder derivation is built on the Magnolia library, which reads the shape of your type during compilation. The codec derivation is a macro that builds on the other two.
- On Scala 3, derivation uses native Scala 3 macros together with the compiler-provided `Mirror` of your type.

Neither route uses Shapeless. After compilation, the derived instance does not inspect classes, field names, or annotations by reflection. It reads the fields it was generated to read.

This has two visible effects:

1. A type that has no codec and cannot be derived is a compile error. Duplicate field names, aliases or case names are the exception: they compile, and are reported with an `AssertionError` when the codec is first initialised.
2. Derivation is done once when your code is compiled, not on every call.

## Nested Types and Subtypes

A derived codec needs a codec for every type it contains. When one is missing, derivation can often fill the gap itself:

- The subtypes of a sealed trait that have no codec of their own are derived together with the trait, on Scala 2 and Scala 3. You derive the codec of the trait, and its case classes and case objects come with it.
- On Scala 3, a case class field whose type has no codec is derived too, if it can be. On Scala 2, every field type needs a codec in implicit scope, and a missing one fails to compile with `magnolia: could not find zio.json.DeriveJsonEncoder.Typeclass[...]`.

A type derived this way is derived again at each place it is used, inside the outer codec, with the configuration in scope at the outer derivation call. Two outer codecs with different configurations can therefore write the same nested type differently. An instance that already exists in implicit scope is never re-derived: it is used as it is, with the configuration it was built with. Give a nested type its own codec when it must look the same everywhere, or when you also encode or decode it on its own. [JsonCodecConfiguration](../reference/json-codec-configuration.md#passing-a-custom-configuration) shows both cases.

## Failing Early

A decoder reports a problem by throwing a small exception that has no stack trace. Building a stack trace is slow, and a flood of bad requests would make a server pay that cost again and again. The exception carries a short list of path steps instead, such as `.address.city`, followed by a message.

`fromJson` and `decodeJson` catch that exception at the top and turn it into a `Left` with the rendered path. That is why every error looks like `.address.city(...)` and why `fromJson` returns a `Left` instead of throwing. The JVM streaming methods are the exception: they fail the effect with a `java.lang.Exception` whose message is the same rendered path. [Errors](../reference/errors.md) describes the format.

Decoding stops at the first problem. It does not collect a list of errors, and it does not read the rest of the payload. This is the *early exit* strategy: a malformed or hostile payload is rejected as soon as it is known to be bad.

## Performance and Security Goals

Reading directly into your types allocates less and does less work than building a tree first. Early exit keeps a bad payload from costing more than the part that was read. The two ideas support each other.

Measurements and attack scenarios are covered in their own pages:

- [Performance](./performance.md) has the benchmarks.
- [Security](./security.md) walks through denial-of-service attacks and how the library resists them.

## Helpful Errors and Simple Patterns

The error path follows the `jq` query style, so you can paste a path into a JSON tool to find the spot. The design relies on the well-known typeclass pattern, where `JsonEncoder[A]` and `JsonDecoder[A]` are values you pass implicitly. To define your own, see [Build an Encoder or Decoder from an Existing One](../how-to/manual-instances.md).
