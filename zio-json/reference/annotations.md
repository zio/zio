# Annotations

> Reference table of all ZIO JSON derivation annotations: what each applies to, its arguments, and where to learn more.

ZIO JSON reads annotations on case classes, fields and sealed traits when it derives a codec with `DeriveJsonCodec.gen`, `DeriveJsonEncoder.gen` or `DeriveJsonDecoder.gen`. All of them live in the `zio.json` package, so `import zio.json._` brings them in. The table lists each annotation, what it can be placed on, and its arguments.

| Annotation                      | Applies to                                | Argument                                             | What it does                                                                        | How-to                                                                                                                 |
|---------------------------------|-------------------------------------------|------------------------------------------------------|-------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| `@jsonField`                    | Field                                     | `name: String`                                       | Sets the JSON name of one field. Wins over `@jsonMemberNames`.                      | [Field names](../how-to/field-names.md#1-rename-one-field-with-jsonfield)                                              |
| `@jsonAliases`                  | Field                                     | `alias: String, aliases: String*`                    | Adds names the decoder also accepts for the field. No effect on encoding.           | [Field names](../how-to/field-names.md#5-read-old-names-with-jsonaliases)                                              |
| `@jsonMemberNames`              | Case class                                | `format: JsonMemberFormat`                           | Applies a name format to every field, for encoding and decoding.                    | [Field names](../how-to/field-names.md#2-rename-all-fields-with-jsonmembernames)                                       |
| `@jsonHint`                     | Subtype of a sealed family                | `name: String`                                       | Sets the type hint of the subtype. The default is the short class name.             | [Sealed traits](../how-to/sealed-traits.md#1-change-a-type-hint-with-jsonhint)                                         |
| `@jsonHintNames`                | Sealed trait or class                     | `format: JsonMemberFormat`                           | Applies a name format to every type hint of the family.                             | [Sealed traits](../how-to/sealed-traits.md#2-transform-all-hints-with-jsonhintnames)                                   |
| `@jsonDiscriminator`            | Sealed trait or class                     | `name: String`                                       | Writes the type hint as a field inside the object instead of a wrapper key.         | [Sealed traits](../how-to/sealed-traits.md#3-use-a-discriminator-field-with-jsondiscriminator)                         |
| `@jsonNoExtraFields`            | Case class                                | None                                                 | Makes decoding fail when the JSON has a field the class does not have.              | [Optional fields](../how-to/optional-and-default-fields.md#4-reject-unexpected-fields-with-jsonnoextrafields)          |
| `@jsonExplicitNull`             | Case class or field                       | None                                                 | Writes `None` as `null` instead of leaving the field out.                           | [Optional fields](../how-to/optional-and-default-fields.md#2-write-nulls-with-jsonexplicitnull)                        |
| `@jsonExplicitEmptyCollections` | Case class or field                       | `encoding: Boolean = true, decoding: Boolean = true` | Sets whether empty collections are written and required. On a field, encoding only. | [Optional fields](../how-to/optional-and-default-fields.md#3-omit-empty-collections-with-jsonexplicitemptycollections) |
| `@jsonExclude`                  | Field                                     | None                                                 | Leaves the field out of the encoded JSON. The decoder still reads it.               | [Optional fields](../how-to/optional-and-default-fields.md#5-keep-a-field-out-of-the-json-with-jsonexclude)            |
| `@jsonDerive`                   | Case class or sealed trait (Scala 2 only) | `config: JsonDeriveConfig = JsonDeriveConfig.Codec`  | Generates the codec in the companion object at compile time.                        | [zio-json-macros](./zio-json-macros.md)                                                                                |

Most of these annotations have a program-wide form in `JsonCodecConfiguration`. The how-to page of each annotation names it.

## Member Formats

`@jsonMemberNames` and `@jsonHintNames` take a `JsonMemberFormat`. The built-in formats are `SnakeCase`, `KebabCase`, `PascalCase`, `CamelCase`, `IdentityFormat` and `CustomCase(f: String => String)`.

`ziojson_03.SnakeCase` and `ziojson_03.KebabCase` are the legacy formats of zio-json 0.3.0. They also put a separator before a digit, so `abc123Def` becomes `abc_123_def`. Use them only to keep JSON that was written by that version readable.

## jsonDerive

`@jsonDerive` is different from the other annotations: it does not change how a codec behaves, it writes the codec for you. It is a Scala 2 macro annotation from the separate `zio-json-macros` module, and it needs `-Ymacro-annotations` on Scala 2.13 or the macro paradise plugin on Scala 2.12. Placed on a case class or a sealed trait, it adds an `implicit val codecFor<Type>` (or `encode<Type>` / `decode<Type>`, depending on its `JsonDeriveConfig` argument) to the companion object. The other annotations on the class still apply to the generated codec. On Scala 3, use a `derives` clause instead.

See [@jsonDerive Macro Annotation](./zio-json-macros.md) for installation, the generated member names, generic classes and the limitations.

## See Also

- [Field names](../how-to/field-names.md) covers `@jsonField`, `@jsonAliases` and `@jsonMemberNames`.
- [Sealed traits](../how-to/sealed-traits.md) covers `@jsonHint`, `@jsonHintNames` and `@jsonDiscriminator`.
- [Optional, default and null fields](../how-to/optional-and-default-fields.md) covers `@jsonNoExtraFields`, `@jsonExplicitNull`, `@jsonExplicitEmptyCollections` and `@jsonExclude`.
- [@jsonDerive Macro Annotation](./zio-json-macros.md) covers `@jsonDerive`.
- [JsonCodecConfiguration](./json-codec-configuration.md) sets the program-wide defaults that these annotations override per type.
