# Automatic Schema Derivation

> Automatic schema derivation is the process of generating schema definitions for data types automatically, without the need to manually write them. It allows us to generate the schema for a data type based on its structure and annotations.

Instead of manually specifying the schema for each data type, we can rely on automatic schema derivation to generate the schema for us. This approach can save time and reduce the potential for errors, especially when dealing with complex data models.

Derivation happens at compile time: macros on Scala 2 and inline methods on Scala 3 analyze the structure of the data type and its fields, including their names, types, and annotations. They then generate the corresponding schema definition based on this analysis. No runtime reflection is involved.

## Installation

ZIO Schema provides schema derivation in the `zio-schema-derivation` module. In order to use automatic schema derivation, we need to add the following line to our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-derivation" % "1.9.2"
```

Every codec module, such as `zio-schema-json`, already depends on this module. On Scala 2, the macros also need `scala-reflect` at compile time; the [Installation](index.md#installation) section shows how to add it. On Scala 3, the artifacts are compiled with Scala 3.9, so the project needs Scala 3.9 or later.

## Deriving Schemas

The following domain models are the ones that [Manual Schema Construction](manual-schema-construction.md) builds by hand. With automatic derivation, each companion object calls `DeriveSchema.gen`:

```scala
import zio.schema._

final case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}

sealed trait PaymentMethod

object PaymentMethod {

  final case class CreditCard(
      number: String,
      expirationMonth: Int,
      expirationYear: Int
  ) extends PaymentMethod

  final case class WireTransfer(accountNumber: String, bankCode: String)
      extends PaymentMethod

  implicit val schema: Schema[PaymentMethod] =
    DeriveSchema.gen[PaymentMethod]
}

final case class Customer(person: Person, paymentMethod: PaymentMethod)

object Customer {
  implicit val schema: Schema[Customer] = DeriveSchema.gen[Customer]
}
```

When `DeriveSchema.gen[Customer]` meets the `person` field, it uses the implicit `Schema[Person]` from the companion object instead of deriving a new one. On Scala 3, `derives Schema` and `Schema.derived` are also available; [Schema Derivation on Scala 2 and Scala 3](derivations/derivation-scala-versions.md) compares the forms.

Any codec can now use these schemas. The following round trip uses the Protobuf codec from the `zio-schema-protobuf` module:

```scala
import zio.Chunk
import zio.schema.codec.{BinaryCodec, DecodeError, ProtobufCodec}

val customer =
  Customer(
    person = Person("John Doe", 42),
    paymentMethod = PaymentMethod.CreditCard("1000100010001000", 6, 2024)
  )

val customerCodec: BinaryCodec[Customer] =
  ProtobufCodec.protobufCodec[Customer]

val encodedCustomer: Chunk[Byte] = customerCodec.encode(customer)

val decodedCustomer: Either[DecodeError, Customer] =
  customerCodec.decode(encodedCustomer)
```

Decoding the encoded bytes gives back the original customer:

```scala
decodedCustomer == Right(customer)
// res0: Boolean = true
```

## Supported Types

`DeriveSchema.gen` derives a schema for these shapes:

- Case classes and case objects. A case class with more than 22 fields becomes a `GenericRecord` wrapped in a transformation.
- Sealed traits and sealed abstract classes, including sealed hierarchies nested inside other sealed traits. Every case must be a case class, a case object, or a nested sealed trait. A sum type with more than 22 cases becomes an `EnumN`.
- Recursive and mutually recursive types.
- Generic case classes and sealed traits. The type arguments are recorded in a `genericTypeInfo` annotation.
- On Scala 3 only: `enum` definitions, union types, and `derives Schema`. [How to Use ZIO Schema with Scala 3](how-to/scala-3.md) shows each of them, and why opaque types need a hand-written schema.

Field types are resolved the same way: derivation uses an implicit schema if one is in scope, and otherwise derives one for case classes, sealed traits, and the standard collections. When no case of a sealed trait or enum has fields, as when every case is a case object, derivation adds the `simpleEnum` annotation, so codecs such as JSON encode the cases as plain strings.

Derivation also copies the annotations of the type and its fields into the schema. [Schema Annotations](schema-annotations.md) lists the annotations that codecs read.

## Default Values

A constructor parameter with a default value gets a `fieldDefaultValue` annotation that holds that default. This makes the field optional when decoding: if the input leaves the field out, the decoder uses the default instead of failing.

The following `Settings` type has defaults for both fields:

```scala
import zio.schema.codec.JsonCodec

final case class Settings(theme: String = "light", fontSize: Int = 12)

object Settings {
  implicit val schema: Schema[Settings] = DeriveSchema.gen[Settings]
}
```

Decoding an empty JSON object fills in both defaults, and a field that is present overrides its default:

```scala
JsonCodec.jsonCodec(Settings.schema).decodeJson("{}")
// res1: Either[String, Settings] = Right(
//   value = Settings(theme = "light", fontSize = 12)
// )

JsonCodec.jsonCodec(Settings.schema).decodeJson("""{"fontSize":16}""")
// res2: Either[String, Settings] = Right(
//   value = Settings(theme = "light", fontSize = 16)
// )
```

The default is computed once, when the schema is built, so every decoded value shares it. A default such as `java.time.Instant.now()` therefore gives every decoded value the same instant. A field marked with `@transientField` must have a default value (a constructor default or `@fieldDefaultValue`), or derivation fails at compile time.

## Case Classes With More Than 22 Fields

Derivation supports case classes with any number of fields. Beyond 22 fields, the schema is a `GenericRecord` that maps field names to values, wrapped in a transformation to and from the case class.

:::caution
On Scala 2, `DeriveSchema.gen` drops the `@validate` annotations of a case class with more than 22 fields, so `Schema.validate` reports no errors for those fields. Scala 3 keeps them. On Scala 2, move the validated fields into a nested case class with 22 fields or fewer. `Schema.validate` checks the rules of nested case classes too.
:::

## See Also

- [Schema Derivation on Scala 2 and Scala 3](derivations/derivation-scala-versions.md) explains the differences between the two compilers.
- [Write a Custom Deriver](how-to/custom-deriver.md) derives your own type classes from a schema.
- [Schema Annotations](schema-annotations.md) customizes field names, defaults, and encodings.
