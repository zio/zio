# Evolve a Data Model Safely

> Learn to change a case class that is stored as JSON without breaking old data: defaults, optional fields, aliases, removals, and migrations.

Welcome to the second ZIO Schema tutorial! It is for Scala developers who have finished [Your First Schema](first-schema.md), so you already know how `DeriveSchema.gen` builds a schema and how `JsonCodec` turns a value into JSON. You don't need any prior experience with schema evolution to follow along, and the project setup from the first tutorial, the core module plus `zio-schema-json`, covers every example.

## Learning Objectives

By the end of this tutorial, you will understand:

- Why JSON written by an old version of a case class still decodes after we add a field with a default value
- How an `Option` field and the `@fieldNameAliases` annotation keep old JSON readable when a field becomes optional or changes its name
- Why removing a field is safe for data that still contains it
- How `Schema#migrate` and `DynamicValue#transform` convert values between versions, and which changes they cannot apply

We'll learn these concepts through seven sections:

1. Storing Version 1 as JSON
2. Adding a Field with a Default Value
3. Making a Field Optional
4. Renaming a Field
5. Removing a Field
6. Migrating Values with `Schema#migrate`
7. Writing Migration Steps by Hand

We recommend reading from top to bottom, because each section builds on the previous one.

## Background

Data outlives code. A customer record written to a database or a message queue today is read again months later, often by a newer version of the program with a different case class. Each time we change the case class, we have to answer one question: can the new code still read the data that the old code wrote?

ZIO Schema gives two tools for this. The first is the codec: the schema tells the JSON decoder which fields may be missing, which old names to accept, and which values to fill in, so most changes need no conversion code at all. The second is migration: ZIO Schema compares two schemas and converts a value of the old type into a value of the new type. We start with the codec, because it handles the common changes, and finish with migration.

## 1. Storing Version 1 as JSON

Our running example is a `Customer` record. Every version gets its own name, such as `CustomerV1` and `CustomerV2`, so that all versions can live side by side on this page. In a real project there is only one `Customer` class, and it changes over time.

We define the first version and two small helpers that turn a value into a JSON string and back:

```scala
import zio.Chunk
import zio.schema._
import zio.schema.codec.JsonCodec

final case class CustomerV1(id: Int, name: String, email: String, fax: String)

object CustomerV1 {
  implicit val schema: Schema[CustomerV1] = DeriveSchema.gen[CustomerV1]
}

def toJson[A](value: A)(implicit schema: Schema[A]): String =
  JsonCodec.jsonEncoder(schema).encodeJson(value, None).toString

def fromJson[A](json: String)(implicit schema: Schema[A]): Either[String, A] =
  JsonCodec.jsonDecoder(schema).decodeJson(json)
```

The code above does the following:

- `CustomerV1` is the model as it was first shipped, with four required fields.
- `DeriveSchema.gen[CustomerV1]` derives the schema, exactly as in the first tutorial.
- `toJson` asks `JsonCodec.jsonEncoder` for an encoder built from the schema and writes the value as a compact JSON string.
- `fromJson` asks `JsonCodec.jsonDecoder` for a decoder and returns either the decoded value or an error message.

Next, we store one customer. The string `storedV1` plays the role of a row in a database that was written by version 1 of our program:

```scala
val adaV1    = CustomerV1(1, "Ada", "ada@example.com", "555-0100")
val storedV1 = toJson(adaV1)
```

Let's look at what was stored:

```scala
println(storedV1)
// {"id":1,"name":"Ada","email":"ada@example.com","fax":"555-0100"}
```

For the rest of the tutorial, `storedV1` never changes. Every new version of the case class must be able to read it.

## 2. Adding a Field with a Default Value

The business now wants to track loyalty points. We add a `loyaltyPoints` field and give it a Scala default value of `0`:

```scala
final case class CustomerV2(id: Int, name: String, email: String, fax: String, loyaltyPoints: Int = 0)

object CustomerV2 {
  implicit val schema: Schema[CustomerV2] = DeriveSchema.gen[CustomerV2]
}
```

`DeriveSchema.gen` notices the default value and records it in the schema as a `@fieldDefaultValue(0)` annotation on the field. The JSON decoder reads that annotation and fills in `0` whenever the field is missing. Let's decode the old JSON with the new type:

```scala
fromJson[CustomerV2](storedV1)
// res1: Either[String, CustomerV2] = Right(
//   value = CustomerV2(
//     id = 1,
//     name = "Ada",
//     email = "ada@example.com",
//     fax = "555-0100",
//     loyaltyPoints = 0
//   )
// )
```

The old JSON has no `loyaltyPoints` field, and the decoder fills in the default. To see why the default matters, here is the same change without one:

```scala
final case class CustomerNoDefault(id: Int, name: String, email: String, fax: String, loyaltyPoints: Int)

object CustomerNoDefault {
  implicit val schema: Schema[CustomerNoDefault] = DeriveSchema.gen[CustomerNoDefault]
}
```

Decoding the same stored JSON into this type fails:

```scala
fromJson[CustomerNoDefault](storedV1)
// res2: Either[String, CustomerNoDefault] = Left(
//   value = ".loyaltyPoints(missing)"
// )
```

The error names the missing field. The rule to remember is simple: when you add a field, give it a default value, and old data keeps decoding.

## 3. Making a Field Optional

Some new customers sign up without an email address. We change `email` from `String` to `Option[String]`:

```scala
final case class CustomerV3(id: Int, name: String, email: Option[String], fax: String, loyaltyPoints: Int = 0)

object CustomerV3 {
  implicit val schema: Schema[CustomerV3] = DeriveSchema.gen[CustomerV3]
}
```

The old JSON still decodes, and the email that it holds becomes a `Some`. A document without an `email` field also decodes, and the field becomes `None`:

```scala
fromJson[CustomerV3](storedV1)
// res3: Either[String, CustomerV3] = Right(
//   value = CustomerV3(
//     id = 1,
//     name = "Ada",
//     email = Some(value = "ada@example.com"),
//     fax = "555-0100",
//     loyaltyPoints = 0
//   )
// )

fromJson[CustomerV3]("""{"id":2,"name":"Grace","fax":"555-0199"}""")
// res4: Either[String, CustomerV3] = Right(
//   value = CustomerV3(
//     id = 2,
//     name = "Grace",
//     email = None,
//     fax = "555-0199",
//     loyaltyPoints = 0
//   )
// )
```

The first result wraps the stored email in `Some`. The second shows that the JSON decoder treats a missing `Option` field as `None`, so new data that omits the field is accepted too.

:::tip
When a field should stay a plain type such as `String`, annotate it with `@optionalField` instead of changing its type. A missing field then decodes to the default value of the field's schema, which is the empty string for a `String`. The [Schema Annotations](../schema-annotations.md) page lists this and the other annotations that the codecs read.
:::

## 4. Renaming a Field

The team decides that `name` is too vague and renames it to `fullName`. If we only rename the Scala field, the decoder looks for a `fullName` key, which the stored JSON does not have. The `@fieldNameAliases` annotation lists other names that the decoder accepts for the same field:

```scala
import zio.schema.annotation.fieldNameAliases

final case class CustomerV4(
  id: Int,
  @fieldNameAliases("name") fullName: String,
  email: Option[String],
  fax: String,
  loyaltyPoints: Int = 0
)

object CustomerV4 {
  implicit val schema: Schema[CustomerV4] = DeriveSchema.gen[CustomerV4]
}
```

The stored JSON uses the old key, `name`, and decodes into `fullName`:

```scala
fromJson[CustomerV4](storedV1)
// res5: Either[String, CustomerV4] = Right(
//   value = CustomerV4(
//     id = 1,
//     fullName = "Ada",
//     email = Some(value = "ada@example.com"),
//     fax = "555-0100",
//     loyaltyPoints = 0
//   )
// )
```

Aliases only affect decoding. When we encode a `CustomerV4`, the encoder writes the new name:

```scala
println(toJson(CustomerV4(3, "Alan", Some("alan@example.com"), "555-0142", 10)))
// {"id":3,"fullName":"Alan","email":"alan@example.com","fax":"555-0142","loyaltyPoints":10}
```

Notice the direction of this guarantee: new code reads both old and new JSON, but a program that still uses `CustomerV1` cannot read the new `fullName` key. Roll out the readers before the writers.

## 5. Removing a Field

Nobody sends faxes any more, so we drop the `fax` field:

```scala
final case class CustomerV5(
  id: Int,
  @fieldNameAliases("name") fullName: String,
  email: Option[String],
  loyaltyPoints: Int = 0
)

object CustomerV5 {
  implicit val schema: Schema[CustomerV5] = DeriveSchema.gen[CustomerV5]
}
```

The stored JSON still has a `fax` key. By default, the JSON decoder skips keys that the type does not declare, so the old data decodes without any extra work:

```scala
fromJson[CustomerV5](storedV1)
// res7: Either[String, CustomerV5] = Right(
//   value = CustomerV5(
//     id = 1,
//     fullName = "Ada",
//     email = Some(value = "ada@example.com"),
//     loyaltyPoints = 0
//   )
// )
```

That's it for the codec! The one JSON string written by version 1 decodes into all five versions. The schema told the decoder which fields to fill in, which old names to accept, and which keys to ignore. If a type must refuse unknown keys instead, the `@rejectExtraFields` annotation turns skipping off, and then removing a field breaks old data.

## 6. Migrating Values with `Schema#migrate`

The codec helps when old data arrives as JSON. Sometimes we instead hold a typed value of an old version in memory, for example one decoded by an old library or taken from a cache, and we want a value of the new version. `Schema#migrate` compares the old schema with the new one, works out the steps between them, and returns a function that converts values:

```scala
sealed trait Schema[A] {
  def migrate[B](newSchema: Schema[B]): Either[String, A => Either[String, B]]
}
```

The outer `Either` says whether ZIO Schema can compare the two schemas. The inner `Either`, returned by the function, says whether converting one particular value succeeds. Let's migrate a `CustomerV2` to a `CustomerV3`, which makes `email` optional:

```scala
val adaV2 = CustomerV2(1, "Ada", "ada@example.com", "555-0100", 25)
```

We derive the function and apply it in one expression:

```scala
CustomerV2.schema.migrate(CustomerV3.schema).map(migrate => migrate(adaV2))
// res8: Either[String, Either[String, CustomerV3]] = Right(
//   value = Right(
//     value = CustomerV3(
//       id = 1,
//       name = "Ada",
//       email = Some(value = "ada@example.com"),
//       fax = "555-0100",
//       loyaltyPoints = 25
//     )
//   )
// )
```

Both layers are `Right`, and the email is now wrapped in `Some`. Removing a field works the same way. Here we migrate a `CustomerV4` to a `CustomerV5`, which drops `fax`:

```scala
val adaV4 = CustomerV4(1, "Ada", Some("ada@example.com"), "555-0100", 25)
// adaV4: CustomerV4 = CustomerV4(
//   id = 1,
//   fullName = "Ada",
//   email = Some(value = "ada@example.com"),
//   fax = "555-0100",
//   loyaltyPoints = 25
// )

CustomerV4.schema.migrate(CustomerV5.schema).map(migrate => migrate(adaV4))
// res9: Either[String, Either[String, CustomerV5]] = Right(
//   value = Right(
//     value = CustomerV5(
//       id = 1,
//       fullName = "Ada",
//       email = Some(value = "ada@example.com"),
//       loyaltyPoints = 25
//     )
//   )
// )
```

Now let's try the change from section 2, which adds `loyaltyPoints`:

```scala
CustomerV1.schema.migrate(CustomerV2.schema).map(migrate => migrate(adaV1))
// res10: Either[String, Either[String, CustomerV2]] = Right(
//   value = Left(
//     value = "Cannot add node at path loyaltyPoints: No default value is available"
//   )
// )
```

The outer `Right` says that the migration was derived, but the inner `Left` says that applying it failed. The derived step that adds `loyaltyPoints` has no value to put in the new field, and it does not read the Scala default value.

:::caution
`Schema#migrate` returns `Right` for a target schema with a new field, but the returned function fails for every value with "No default value is available". A rename looks like a field that is removed plus a field that is added, so migrating `CustomerV3` to `CustomerV4` fails in the same way. For new and renamed fields, rely on default values and aliases in the codec, as in sections 2 and 4, or write the conversion by hand. The changes that `Schema#migrate` applies successfully are removing a field, making a field optional, and making an optional field required, which fails for a value that is `None`.
:::

## 7. Writing Migration Steps by Hand

`Schema#migrate` works in three stages: it turns the old value into a `DynamicValue`, applies a list of `Migration` steps to it, and decodes the result with the new schema. A `DynamicValue` is a tree that holds the value's data together with its field names, so steps can rewrite it without knowing the Scala type. The [Working with Dynamic Data](dynamic-data.md) tutorial explores it in detail.

We can run the same stages ourselves. Each step names the node it changes with a `NodePath`, built from `NodePath.root` and field names. To drop the `fax` field by hand:

```scala
import zio.schema.meta.{Migration, NodePath}

val fax = NodePath.root / "fax"
// fax: NodePath = IndexedSeq("fax")

CustomerV4.schema
  .toDynamic(adaV4)
  .transform(Chunk(Migration.DeleteNode(fax)))
  .flatMap(CustomerV5.schema.fromDynamic)
// res11: Either[String, CustomerV5] = Right(
//   value = CustomerV5(
//     id = 1,
//     fullName = "Ada",
//     email = Some(value = "ada@example.com"),
//     loyaltyPoints = 25
//   )
// )
```

The code above does the following:

- `NodePath.root / "fax"` points at the `fax` field of the top-level record.
- `Schema#toDynamic` turns the typed `CustomerV4` into a `DynamicValue`.
- `DynamicValue#transform` applies the steps in the `Chunk`; `Migration.DeleteNode` removes the node at the path.
- `Schema#fromDynamic` reads the result as a `CustomerV5`.

When a migration has several steps, their order matters. `DynamicValue#transform` applies the steps from the last one in the `Chunk` to the first one. In the next example, `Migration.Optional` wraps `email` in `Some` and `Migration.Require` unwraps it again. The first call fails, because `Require` runs first, on an `email` that is not optional yet. The second call lists the same steps the other way round and succeeds:

```scala
val email      = NodePath.root / "email"
// email: NodePath = IndexedSeq("email")
val dynamicAda = CustomerV2.schema.toDynamic(adaV2)
// dynamicAda: DynamicValue = Record(
//   id = Nominal(
//     packageName = IndexedSeq("repl"),
//     objectNames = IndexedSeq("MdocSession", "MdocApp"),
//     typeName = "CustomerV2"
//   ),
//   values = ListMap(
//     "id" -> Primitive(value = 1, standardType = int),
//     "name" -> Primitive(value = "Ada", standardType = string),
//     "email" -> Primitive(value = "ada@example.com", standardType = string),
//     "fax" -> Primitive(value = "555-0100", standardType = string),
//     "loyaltyPoints" -> Primitive(value = 25, standardType = int)
//   )
// )

dynamicAda.transform(Chunk(Migration.Optional(email), Migration.Require(email)))
// res12: Either[String, DynamicValue] = Left(
//   value = "Failed to require leaf at path email: Expected optional value at lead"
// )

dynamicAda.transform(Chunk(Migration.Require(email), Migration.Optional(email))).isRight
// res13: Boolean = true
```

Finally, some steps exist but fail on every value. `Migration.ChangeType` describes a new primitive type for a field, such as `Int` to `Long`, and it always fails:

```scala
dynamicAda.transform(Chunk(Migration.ChangeType(NodePath.root / "id", StandardType.LongType)))
// res14: Either[String, DynamicValue] = Left(
//   value = "Cannot change type of node at path id: No type conversion is available"
// )
```

:::caution
`DynamicValue#transform` runs the steps last-first, so list them in the reverse of the order they should apply. `Migration.ChangeType` and `Migration.AddNode` fail for every value, so a change of field type or an added field needs a conversion written by hand, such as a plain function from the old case class to the new one.
:::

## What You've Learned

In this tutorial, you learned:

- Why a Scala default value, which `DeriveSchema.gen` records as `@fieldDefaultValue`, keeps old JSON decoding after we add a field
- How an `Option` field accepts both old data and new data that omits the field
- How `@fieldNameAliases` keeps the old name readable after a rename, while the encoder writes the new name
- Why the JSON decoder ignores fields that the type no longer declares
- How `Schema#migrate` and `DynamicValue#transform` convert values between versions, which steps work, and why steps run last-first

You can now change a stored model one safe step at a time. The next step is to look at the `DynamicValue` tree that migrations work on.

## Where to Go Next

- **Continue the tutorial series:** [Working with Dynamic Data](dynamic-data.md), the third tutorial, shows how to inspect and change the `DynamicValue` tree that migrations work on.
- **See every migration step:** [Migration Operations](../reference/migration-operations.md) describes each `Migration` case.
- **Learn how steps are derived:** [Schema Migration](../operations/schema-migration.md) explains `Schema#migrate`, `Schema#coerce`, and `Migration.derive`.
- **Look up the annotations:** [Schema Annotations](../schema-annotations.md) covers `@fieldNameAliases`, `@optionalField`, `@fieldDefaultValue`, and `@rejectExtraFields`.
- **Read about dynamic values:** the [DynamicValue](../operations/dynamic-data-representation.md) reference lists every case and operation.
