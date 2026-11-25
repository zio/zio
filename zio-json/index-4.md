# Reference

> The ZIO JSON reference: the type classes, derivation and its options, the JSON AST, and the YAML, streaming and golden-testing APIs, with every method, option and default.

The reference describes everything ZIO JSON provides: every type, method, annotation and option, with its signature, its default and what it does. Use it when you know what you want to call and need the exact details.

The other sections answer other questions. [Getting Started](../getting-started.md) and the tutorials teach the library step by step. The [How-to Guides](../how-to/index.md) solve one task each. The [Concepts](../explanation/index.md) pages explain why ZIO JSON works the way it does.

## Type Classes

The core of the library. An encoder turns a value into JSON, a decoder reads it back, and a codec is both.

- [Type Classes](type-classes/index.md): how the type classes relate, and where instances come from.
- [JsonEncoder](json-encoder.md): turns a Scala value into JSON. Covers `toJson`, `contramap`, the combinators and writing an encoder by hand.
- [JsonDecoder](json-decoder.md): reads JSON into a Scala value. Covers `fromJson`, `map`, `mapOrFail`, the combinators, missing fields and writing a decoder by hand.
- [JsonCodec](json-codec.md): an encoder and a decoder packed together, and how to transform and combine codecs.
- [JsonFieldEncoder and JsonFieldDecoder](json-field-codecs.md): the type classes for `Map` keys and the key types supported out of the box.
- [Built-in Instances](built-in-instances.md): every type ZIO JSON encodes and decodes out of the box, with its JSON shape and how lenient its decoder is.
- [Errors](errors.md): what a failed decode returns, the common messages, and how error paths are written.

## Derivation

How ZIO JSON writes codecs for your own case classes and sealed traits, and the tools that change what it writes.

- [Derivation](derivation/index.md): how derivation starts on Scala 2 and Scala 3, and which tool to use for which change.
- [JsonCodecConfiguration](json-codec-configuration.md): the options that apply to every type derived where the configuration is in scope.
- [Annotations](annotations.md): the annotations that change one type or one field.
- [@jsonDerive Macro Annotation](zio-json-macros.md): the Scala 2 annotation that writes the derived codec into the companion object.

## Data and Modules

The JSON tree, and the APIs of the optional modules. The [Installation](../index.md#installation) and [Modules](../index.md#modules) sections of the Introduction page show how to add each module.

- [JSON AST](json-ast.md): the `Json` type, cursors, and reading, transforming, combining and folding JSON trees.
- [YAML](yaml.md): reading and writing YAML with your JSON codecs, the options, and the decoding limits.
- [Streaming API](streaming.md): the JVM-only ZIO Streams encoders, decoders, pipelines and file helpers, and how they report errors.
- [Golden Testing API](golden-testing.md): `goldenTest`, `GoldenConfiguration`, the golden file lifecycle and format, and how the module root is found.
