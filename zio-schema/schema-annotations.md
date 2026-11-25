# Schema Annotations

> Annotations attach extra information to a case class, a field, or a case of a sealed trait, and codecs read that information when they encode or decode a value. A field annotation changes how one field is named or whether it is written at all. A type annotation changes how the whole type is laid out, for example how a sealed trait marks its case. Annotations live in the `zio.schema.annotation` package, and you can apply them in two ways:

## Overview

- Directly on the definition, as in `case class User(@fieldName("user_name") username: String)`.
- With `Schema#annotate`, when you cannot edit the source, as in `User.schema.annotate(rejectExtraFields())`.

Each annotation below shows the JSON that the codec produces. The JSON codec is the main consumer of these annotations; other codecs ignore some of them, as listed in [Annotation Support by Codec](derivations/codecs/index.md#annotation-support-by-codec).

## Renaming Fields and Cases

These annotations change the names that appear in the encoded output. Renaming does not change the Scala names you use in code.

### Field Names

To encode a field under a different name, apply `fieldName` to the field:

```scala
import zio.schema.{DeriveSchema, Schema}
import zio.schema.annotation.fieldName
import zio.schema.codec.JsonCodec

final case class User(@fieldName("user_name") username: String, age: Int)

object User {
  implicit val schema: Schema[User] = DeriveSchema.gen
}
```

Encoding a `User` writes the field under `user_name`:

```scala
println(JsonCodec.jsonCodec(User.schema).encodeJson(User("alice", 30), None).toString)
// {"user_name":"alice","age":30}
```

To accept more than one incoming name when decoding, apply `fieldNameAliases` with the extra names. Encoding still uses the primary name from `fieldName`, and decoding accepts any listed name:

```scala
import zio.schema.{DeriveSchema, Schema}
import zio.schema.annotation.{fieldName, fieldNameAliases}
import zio.schema.codec.JsonCodec

final case class Account(@fieldName("email") @fieldNameAliases("mail", "e_mail") address: String)

object Account {
  implicit val schema: Schema[Account] = DeriveSchema.gen
}
```

Decoding a payload that uses the alias `mail` produces the same value as decoding `email`:

```scala
println(JsonCodec.jsonCodec(Account.schema).decodeJson("""{"mail":"a@example.com"}""").toString)
// Right(Account(a@example.com))
```

### Case Names

Sealed traits encode each case under its case name. Apply `caseName` to a case to change that name:

```scala
import zio.schema.{DeriveSchema, Schema}
import zio.schema.annotation.caseName
import zio.schema.codec.JsonCodec

sealed trait Status

object Status {
  @caseName("ACTIVE") case object Enabled extends Status
  case object Disabled extends Status

  implicit val schema: Schema[Status] = DeriveSchema.gen
}
```

The renamed case uses its new name in the JSON output, and the other case keeps its Scala name:

```scala
println(JsonCodec.jsonCodec(Status.schema).encodeJson(Status.Enabled, None).toString)
// "ACTIVE"
println(JsonCodec.jsonCodec(Status.schema).encodeJson(Status.Disabled, None).toString)
// "Disabled"
```

`caseNameAliases` works like `fieldNameAliases` for cases: it accepts extra names when decoding a case.

## Discriminators

A sealed trait is normally encoded with a discriminator, which is a field that records the case. These annotations change how that field is named or whether it exists.

### Discriminator Name

To store the case under a field with your own name, apply `discriminatorName` to the sealed trait:

```scala
import zio.schema.{DeriveSchema, Schema}
import zio.schema.annotation.discriminatorName
import zio.schema.codec.JsonCodec

@discriminatorName("kind")
sealed trait Shape

object Shape {
  final case class Circle(radius: Double) extends Shape
  final case class Square(side: Double)   extends Shape

  implicit val schema: Schema[Shape] = DeriveSchema.gen
}
```

The discriminator field is `kind`, and it holds the name of the case:

```scala
println(JsonCodec.jsonCodec(Shape.schema).encodeJson(Shape.Circle(1.5), None).toString)
// {"kind":"Circle","radius":1.5}
```

### No Discriminator

To drop the discriminator field entirely, apply `noDiscriminator` to the sealed trait. The codec then encodes each case as its plain value and picks a case by trying each one when decoding:

```scala
import zio.schema.{DeriveSchema, Schema}
import zio.schema.annotation.noDiscriminator
import zio.schema.codec.JsonCodec

@noDiscriminator
sealed trait Reading

object Reading {
  final case class Celsius(value: Double)    extends Reading
  final case class Fahrenheit(value: Double) extends Reading

  implicit val schema: Schema[Reading] = DeriveSchema.gen
}
```

Encoding a case writes only its fields, so the output has no case name:

```scala
println(JsonCodec.jsonCodec(Reading.schema).encodeJson(Reading.Celsius(21.5), None).toString)
// {"value":21.5}
```

Because no field records the case, decoding can match a payload to more than one case. Use `noDiscriminator` only when the cases have distinct shapes.

## Controlling What Is Encoded

These annotations decide whether a field or case is written, and what happens when a field is missing.

### Transient Fields and Cases

To skip a field during encoding, apply `transientField`. The field must have a default value, given as a Scala default parameter or with `fieldDefaultValue`; otherwise `DeriveSchema.gen` fails to compile. When decoding, the field takes that default value:

```scala
import zio.schema.{DeriveSchema, Schema}
import zio.schema.annotation.transientField
import zio.schema.codec.JsonCodec

final case class Session(token: String, @transientField cache: String = "none")

object Session {
  implicit val schema: Schema[Session] = DeriveSchema.gen
}
```

The `cache` field is absent from the output:

```scala
println(JsonCodec.jsonCodec(Session.schema).encodeJson(Session("abc", "warm"), None).toString)
// {"token":"abc"}
```

Decoding the same output fills `cache` with its declared default, `"none"`:

```scala
println(JsonCodec.jsonCodec(Session.schema).decodeJson("""{"token":"abc"}""").toString)
// Right(Session(abc,none))
```

`transientCase` applies the same idea to a whole case of a sealed trait. The codec writes an empty object, `{}`, in place of that case.

### Optional Fields

To let decoding succeed when a field is missing, apply `optionalField`. The codec then fills in the value that the field's schema provides for an absent value, such as `None` for an `Option` or an empty string for a `String`:

```scala
import zio.schema.{DeriveSchema, Schema}
import zio.schema.annotation.optionalField
import zio.schema.codec.JsonCodec

final case class Page(items: List[String], @optionalField nextPage: String)

object Page {
  implicit val schema: Schema[Page] = DeriveSchema.gen
}
```

Decoding a payload that omits `nextPage` succeeds:

```scala
println(JsonCodec.jsonCodec(Page.schema).decodeJson("""{"items":["a","b"]}""").toString)
// Right(Page(List(a, b),))
```

### Default Values

`optionalField` uses the value that the field's schema computes. To choose the value yourself, give the field a Scala default parameter, or apply `fieldDefaultValue`, which takes the value to use when the field is missing. `DeriveSchema.gen` turns each Scala default parameter into a `fieldDefaultValue` annotation, so both forms behave the same. The annotation also makes the field optional, so `optionalField` is not needed with it:

```scala
import zio.schema.{DeriveSchema, Schema}
import zio.schema.annotation.fieldDefaultValue
import zio.schema.codec.JsonCodec

final case class Search(query: String, @fieldDefaultValue("first") page: String)

object Search {
  implicit val schema: Schema[Search] = DeriveSchema.gen
}
```

When the payload omits `page`, the decoded value uses `"first"`:

```scala
println(JsonCodec.jsonCodec(Search.schema).decodeJson("""{"query":"zio"}""").toString)
// Right(Search(zio,first))
```

### Rejecting Extra Fields

By default, decoding ignores fields that the type does not declare. To fail on them instead, apply `rejectExtraFields` to the type:

```scala
import zio.schema.{DeriveSchema, Schema}
import zio.schema.annotation.rejectExtraFields
import zio.schema.codec.JsonCodec

@rejectExtraFields
final case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen
}
```

A payload with an undeclared `email` field now fails to decode:

```scala
println(JsonCodec.jsonCodec(Person.schema).decodeJson("""{"name":"bob","age":4,"email":"x"}""").toString)
// Left((extra field))
```

You can also turn this on for every type at once with `JsonCodec.Configuration(rejectExtraFields = true)`. The JSON codec rejects extra fields when either the annotation or the configuration asks for it, so the annotation can turn rejection on for one type, but it cannot turn it off for a type when the configuration turns it on.

## Other Annotations

The `zio.schema.annotation` package has a few more annotations. Most of them are added by derivation or used by a single codec:

| Annotation             | Purpose                                                                                                                                                            |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `simpleEnum`           | `DeriveSchema.gen` adds it to a sealed trait whose cases have no fields. The JSON codec then encodes each case as a plain string, as in the `Status` example above |
| `genericTypeInfo`      | `DeriveSchema.gen` adds it to a generic type to record the types its type parameters were applied to                                                               |
| `directDynamicMapping` | On a `Schema.Dynamic`, it tells the JSON codec to write a `DynamicValue` record as a plain JSON object instead of its tagged structure                             |
| `recordName`           | Stores an alternative name for a record. No built-in codec reads it                                                                                                |

## Validation and Descriptions

The `@validate` annotation attaches a `Validation` rule to a case class field, and `Schema#validate` checks a value against the rules of its fields. Rules attached to a whole schema with `Schema#validation` are not checked by `Schema#validate`, and a field rule that fails through `Validation.fail` reports no error. [Validation](./operations/validating-types.md) covers what `Schema#validate` checks, and the [Validation reference](operations/validating-types.md) lists the rules.

The `description` annotation attaches documentation text to a type or a field, as in `@description("The user's login name")`. The Scala 3 derivation also adds it automatically from the Scaladoc comment of a type. The built-in codecs do not read it; it is available to your own code through `Schema#annotations`.
