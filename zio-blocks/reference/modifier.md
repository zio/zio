# Modifier

> `Modifier` is a sealed trait that provides a mechanism to attach metadata and configuration to schema elements. Modifiers serve as annotations for record fields, variant cases, and reflect values, enabling format-specific customization without polluting domain types.

`Modifier` is a sealed trait that provides a mechanism to attach metadata and configuration to schema elements. Modifiers serve as annotations for record fields, variant cases, and reflect values, enabling format-specific customization without polluting domain types.

Modifiers are designed to be **pure data** values that can be serialized, making them ideal for runtime introspection and cross-process schema exchange. When deriving schemas, modifiers are collected and attached to the corresponding fields or types, allowing codecs to read and interpret them accordingly. They are extended with `StaticAnnotation` to also support annotation syntax:

```scala
sealed trait Modifier extends StaticAnnotation
object Modifier {
  sealed trait Term    extends Modifier
  // ... term modifiers (transient, rename, alias, config) ...
  sealed trait Reflect extends Modifier
  // ... reflect modifiers (config) ...
}
```

Modifiers can be applied in two ways:

1. **Programmatic API**: Using the `Schema#modifier` and `Schema#modifiers` methods to attach modifiers to the entire schema or, for field-level modifiers, attach them to specific fields using optics when deriving codecs. This approach keeps your domain types clean and allows you to separate schema configuration from your data model:

```scala

// Clean domain type - zero dependencies
case class User(
  id: String,
  name: String,
  cache: Map[String, String] = Map.empty
)

// Modifiers applied separately to schema and codecs
object User extends CompanionOptics[User] {
  implicit val schema: Schema[User] = Schema
    .derived[User]
    .modifier(Modifier.config("db.table-name", "users"))

   implicit val jsonCodec: JsonCodec[User] =
     schema
       .deriving(JsonCodecDeriver)
       .modifier(User.name, Modifier.rename("username"))
       .modifier(User.cache, Modifier.transient())
       .derive

  lazy val id   : Lens[User, String]              = $(_.id)
  lazy val name : Lens[User, String]              = $(_.name)
  lazy val cache: Lens[User, Map[String, String]] = $(_.cache)
}
```

In the above example, we derived a JSON codec for `User` and applied the `rename` and `transient` modifiers to the `name` and `cache` fields respectively, while keeping the domain type free of any schema-related annotations. Now when encoding a `User` to JSON, the `name` field will be serialized as `username`, and the `cache` field will be omitted. During decoding, the codec will look for `username` in the input JSON and populate the `name` field accordingly:

```scala
val user = User(
  id = "123",
  name = "Alice",
  cache = Map("lastLogin" -> "2024-06-01T12:00:00Z")
)
val json: String = User.jsonCodec.encodeToString(user)
println(json)
// Prints: {"id":"123","username":"Alice"}
val decodedUser: Either[SchemaError, User] = User.jsonCodec.decode(json)
println(decodedUser)
// Prints: Right(User(123,Alice,Map()))
```

Please note that when deriving codecs, you can access these modifiers programmatically, allowing you to build custom logic based on the presence of certain modifiers. For example, your SQL codec could check for the presence of `db.table-name` in the schema modifiers to determine which table to read from or write to.

2. **Annotation Syntax**: Using the `@` syntax to annotate fields and cases directly in your case classes and sealed traits. These annotations are processed during schema derivation to attach the corresponding modifiers to the schema elements. At runtime, you can access these modifiers through the `Reflect` structure of the schema.

```scala

@Modifier.config("db.table-name", "users")
case class User(
  id: String,
  @Modifier.rename("username") name: String,
  @Modifier.transient() cache: Map[String, String] = Map.empty
)

object User extends CompanionOptics[User] {
  implicit val schema: Schema[User] =
    Schema.derived[User]

   implicit val jsonCodec: JsonCodec[User] =
     schema
       .derive(JsonCodecDeriver)
}
```

In this example, we applied the same modifiers as in the programmatic example, but using annotation syntax directly on the case class fields. Let's try encoding and decoding a `User` instance:

```scala
val user = User(
  id = "123",
  name = "Alice",
  cache = Map("lastLogin" -> "2024-06-01T12:00:00Z")
)

val json: String = User.jsonCodec.encodeToString(user)
println(json)
// Prints: {"id":"123","username":"Alice"}
val decodedUser: Either[SchemaError, User] = User.jsonCodec.decode(json)
println(decodedUser)
// Prints: Right(User(123,Alice,Map()))
```

## Modifier Hierarchy

Modifiers are organized into two main categories:

1. **Term modifiers** - annotate record fields or variant cases (the data structure elements)
2. **Reflect modifiers** - annotate schemas/reflect values themselves (the metadata about types)

```
Modifier
 ├── Modifier.Term     (annotates record fields and variant cases)
 │    ├── transient()      : exclude from serialization
 │    ├── rename(name)     : change serialized name
 │    ├── alias(name)      : add alternative name
 │    └── config(key, val) : attach key-value metadata
 └── Modifier.Reflect  (annotates reflect values / types)
      └── config(key, val) : attach key-value metadata
```

As you can see, `config` is the only modifier that extends both `Term` and `Reflect`, allowing it to be used on both fields and types.

## Term Modifiers

Term modifiers annotate record fields and variant cases. They are used to control how individual fields or cases are serialized and deserialized, as well as to attach additional metadata that can be interpreted by codecs or other tools.

### transient

The `transient` modifier marks a field as transient, meaning it will be excluded from serialization. This is useful for computed fields, caches, or sensitive data that shouldn't be persisted.

### rename

The `rename` modifier changes the serialized name of a field or variant case. This is useful when the field name in your Scala code differs from the expected name in the serialized format.

```scala

case class Person(
  @Modifier.rename("user_name") name: String,
  @Modifier.rename("user_age") age: Int
)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}
```

You can also use `rename` on variant cases to customize the discriminator value:

```scala

sealed trait PaymentMethod

object PaymentMethod {
  @Modifier.rename("credit_card")
  case class CreditCard(number: String, cvv: String) extends PaymentMethod

  @Modifier.rename("bank_transfer")
  case class BankTransfer(iban: String) extends PaymentMethod

  implicit val schema: Schema[PaymentMethod] = Schema.derived
}
```

### alias

The `alias` modifier provides an alternative name for a term during decoding. This is useful for supporting multiple names during schema evolution or data migration.

```scala

case class MyClass(
  @Modifier.rename("NewName")
  @Modifier.alias("OldName")
  @Modifier.alias("LegacyName")
  value: String
)

object MyClass {
  implicit val schema: Schema[MyClass] = Schema.derived
}
```

With this configuration:
- **Encoding** always uses the `rename` value: `"NewName"`
- **Decoding** accepts any of: `"NewName"`, `"OldName"`, or `"LegacyName"`

This pattern is particularly useful when migrating data formats without breaking compatibility with existing data.

### config

The `config` modifier attaches arbitrary key-value metadata to a term (record fields or variant cases) or a type itself. The convention for keys is `<format>.<property>`, allowing format-specific configuration.

```scala

case class Event(
  @Modifier.config("protobuf.field-id", "1") id: Long,
  @Modifier.config("protobuf.field-id", "2") name: String
)

object Event {
  implicit val schema: Schema[Event] = Schema.derived
}
```

The `config` modifier extends both `Term` and `Reflect`, making it usable on both fields and types. We will discuss using `config` on types in the reflect modifiers section below.

## Reflect Modifiers

Reflect modifiers annotate reflect values (types themselves). Currently, only `config` is a reflect modifier.

### config

You can attach configuration to the type itself using the `Schema#modifier` method:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
    .modifier(Modifier.config("db.table-name", "person_table"))
    .modifier(Modifier.config("schema.version", "v2"))
}
```

Or add multiple modifiers at once:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
    .modifiers(
      Seq(
        Modifier.config("db.table-name", "person_table"),
        Modifier.config("schema.version", "v2")
      )
    )
}
```

Or annotate the case class directly:

```scala

@Modifier.config("db.table-name", "person_table")
@Modifier.config("schema.version", "v2")
case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}
```

## Programmatic Modifier Access

You can access modifiers programmatically through the `Reflect` structure:

```scala

case class Person(
  @Modifier.rename("full_name") name: String,
  @Modifier.transient cache: String = ""
)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Access field modifiers through the reflect
val reflect = Schema[Person].reflect
reflect match {
  case record: Reflect.Record[_, _] =>
    record.fields.foreach { field =>
      println(s"Field: ${field.name}")
      println(s"Modifiers: ${field.modifiers}")
    }
  case _ => ()
}
```

## Built-in Schema Support

All modifier types have built-in `Schema` instances, enabling them to be serialized and deserialized:

```scala

// Schema instances for individual modifiers
Schema[Modifier.transient]
Schema[Modifier.rename]
Schema[Modifier.alias]
Schema[Modifier.config]

// Schema instances for modifier traits
Schema[Modifier.Term]
Schema[Modifier.Reflect]
Schema[Modifier]
```

This means you can serialize modifiers as part of your schema metadata, allowing you to persist and exchange schema information with full modifier details.

[//]: # (## Format Support)
[//]: # ()
[//]: # (Different serialization formats interpret modifiers according to their semantics.)
[//]: # ()
[//]: # (TODO: Add a table comparing how each modifier is supported across formats like JSON, BSON, Avro, Protobuf, etc. For example:)
[//]: # (| Modifier    | JSON                 | BSON                 | Avro              | Protobuf          |)
[//]: # (|-------------|----------------------|----------------------|-------------------|-------------------|)
[//]: # (| `transient` | Field omitted        | Field omitted        | Field omitted     | Field omitted     |)
[//]: # (| `rename`    | Custom field name    | Custom field name    | Custom field name | Custom field name |)
[//]: # (| `alias`     | Accepts alternatives | Accepts alternatives | -                 | -                 |)
[//]: # (| `config`    | Format-specific      | Format-specific      | Format-specific   | Format-specific   |)

## Best Practices

1. **Use `rename` for external APIs**: When integrating with external systems that use different naming conventions (snake_case vs camelCase), use `rename` to match the expected format.

2. **Use `alias` for migrations**: When evolving your data model, add `alias` modifiers to support reading old data while writing with new names.

3. **Use `transient` sparingly**: Only mark fields as transient when they are truly derived or temporary. Remember that transient fields need default values.

4. **Use namespaced keys for `config`**: Follow the `<format>.<property>` convention to avoid conflicts between different formats or tools.
