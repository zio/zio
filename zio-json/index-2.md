# How-to Guides

> How-to guides answer one question each: "How do I do X?" Each guide starts from a goal and shows the code that reaches it. If you are new to ZIO JSON, start with the [Getting Started](../getting-started.md) tutorial first.

## Encoding and decoding

- [Control the JSON a type encodes to](encoding.md): rename or drop fields, write wrapper types as bare values, reshape output and tag sealed traits.
- [Decode JSON whose shape differs from your case class](decoding.md): rename keys, decode through an intermediate class, or read a `Json` tree when the JSON does not match your fields.
- [Build an encoder or decoder from an existing one](manual-instances.md): reuse instances with `map`, `mapOrFail`, `contramap` and `transform`.
- [Encode a type you do not own](third-party-types.md): write a codec for a class from a library you cannot change.
- [Use your own type as a Map key](custom-map-keys.md): write `JsonFieldEncoder` and `JsonFieldDecoder` instances for dates, wrapper types and enumerations used as map keys.
- [Write a fast encoder or decoder by hand](low-level-codecs.md): implement `unsafeEncode` and `unsafeDecode` for hot paths or JSON shapes derivation cannot produce.
- [Validate data while decoding](validate-on-decode.md): reject bad values during decoding.

## Shaping the JSON

- [Field names](field-names.md): rename fields, use snake_case or kebab-case, and add aliases.
- [Sealed traits](sealed-traits.md): choose how a sum type is written and read, with type hints or a discriminator field.
- [Optional, default and null fields](optional-and-default-fields.md): control missing fields, default values and nulls.

## Deriving codecs

- [Derive codecs on Scala 3](scala-3-derivation.md): use `derives`, a `given` configuration, enums, union types, generic classes and opaque types.
- [Derive codecs for generic types](generic-types.md): type-parameterised case classes and sealed traits, including recursive ones, on Scala 2 and 3.

## Working with the JSON tree and YAML

- [Read and edit JSON of unknown shape](edit-json-with-cursors.md): navigate, update, delete and merge parts of a `Json` value with cursors.
- [Load a YAML configuration file](load-yaml-config.md): decode YAML into case classes and write case classes back as YAML.

## Working with data at scale

- [Process large JSON as a stream](streaming.md): read and write large JSON arrays and JSON Lines files without loading them into memory.
- [Catch unintended changes to your JSON with golden tests](golden-testing.md): pin the encoded format in a test and review every change.

## Developer tools

- [Generate case classes from sample JSON](generate-case-classes.md): print case class definitions and codecs inferred from a JSON sample.

For every annotation, see [Annotations](../reference/annotations.md); for every derivation option, see [JsonCodecConfiguration](../reference/json-codec-configuration.md). The [Reference](../reference/index.md) section covers the rest of the API.
