# Schema Migration

> With ZIO Schema, we can automatically migrate data from one version of a schema to another. As software evolves, we often need to add, change or remove old fields. `Schema#migrate` and `Schema#coerce` help migrate data from the old schema to the new one:

## Automatic Migration

```scala
sealed trait Schema[A] {
  def migrate[B](newSchema: Schema[B]): Either[String, A => scala.util.Either[String, B]]

  def coerce[B](newSchema: Schema[B]): Either[String, Schema[B]]
}
```

The `Schema#migrate` method compares the structure of the two schemas and derives the migration steps between them. When the two structures can be matched, it returns a `Right` with a function that converts values of the old type into values of the new type. It returns a `Left` only when a part of the old structure cannot be mapped to the new one at all, for example a record that becomes a primitive. `Schema#coerce` calls `Schema#migrate` in both directions and returns a schema for the new type that reads and writes through the old one.

The conversion function turns the old value into a [`DynamicValue`](dynamic-data-representation.md), applies the steps, and reads the result with the new schema. The following example removes a field and makes another field optional:

```scala
import zio.schema._

case class UserV1(name: String, age: Int, email: String)

object UserV1 {
  implicit val schema: Schema[UserV1] = DeriveSchema.gen[UserV1]
}

case class UserV2(name: String, age: Option[Int])

object UserV2 {
  implicit val schema: Schema[UserV2] = DeriveSchema.gen[UserV2]
}
```

Migrating a `UserV1` drops `email` and wraps `age` in `Some`:

```scala
UserV1.schema.migrate(UserV2.schema).map(migrateUser => migrateUser(UserV1("John", 42, "john@example.com")))
// res0: Either[String, Either[String, UserV2]] = Right(
//   value = Right(value = UserV2(name = "John", age = Some(value = 42)))
// )
```

The `zio.schema.syntax` object also provides `MigrationOps#migrate` as a method on any value that has an implicit schema, as in `UserV1("John", 42, "john@example.com").migrate[UserV2]`.

:::caution
`Schema#migrate` and `Schema#coerce` return `Right` even for changes that the library cannot apply to values. Adding a field, or changing the type of a primitive field (for example from `Int` to `Double`), produces a step that fails on every value, so the returned function always returns `Left`. The following migration adds a `role` field:

```scala
case class UserV3(name: String, age: Option[Int], role: String)

object UserV3 {
  implicit val schema: Schema[UserV3] = DeriveSchema.gen[UserV3]
}

UserV2.schema.migrate(UserV3.schema).map(migrateUser => migrateUser(UserV2("John", Some(42))))
// res1: Either[String, Either[String, UserV3]] = Right(
//   value = Left(
//     value = "Cannot add node at path role: No default value is available"
//   )
// )
```

The outer `Right` says that the migration was derived, and the inner `Left` says that applying it failed. The [Migration Operations](../reference/migration-operations.md) page lists which steps apply to values and which always fail. For an added field, give the field a default value in the new case class, for example `role: String = "member"`, and decode the old data with a codec, which fills in the default, or write the conversion by hand.
:::

## Manual Migration

Because a `DynamicValue` carries its type information inside the data, we can migrate it by applying a sequence of migration steps with `DynamicValue#transform`:

```scala
sealed trait DynamicValue {
  def transform(transforms: Chunk[Migration]): Either[String, DynamicValue]
}
```

[Running Several Steps](../reference/migration-operations.md#running-several-steps) describes the order in which `DynamicValue#transform` applies the steps and how it handles a failing step.

The `Migration` is a sealed trait with several subtypes. The [Migration Operations](../reference/migration-operations.md) page describes each of them:

```scala
sealed trait Migration
object Migration {
  final case class AddNode(override val path: NodePath, node: MetaSchema) extends Migration

  final case class DeleteNode(override val path: NodePath) extends Migration

  final case class AddCase(override val path: NodePath, node: MetaSchema) extends Migration

  // ...
}
```

With the `Migration` steps we describe the change and then apply it to a `DynamicValue`. The following example deletes the `age` field from a `Person1` value and reads the result as a `Person2`:

```scala
import zio.Chunk
import zio.schema.meta.Migration.DeleteNode
import zio.schema.meta.{Migration, NodePath}
import zio.schema.{DeriveSchema, Schema}

case class Person1(name: String, age: Int)

object Person1 {
  implicit val schema: Schema[Person1] = DeriveSchema.gen
}

case class Person2(name: String)

object Person2 {
  implicit val schema: Schema[Person2] = DeriveSchema.gen
}

val person1: Person1 = Person1("John Doe", 42)

val migrations: Chunk[Migration] = Chunk(DeleteNode(NodePath.root / "age"))

val person2 = DeriveSchema
  .gen[Person1]
  .toDynamic(person1)
  .transform(migrations)
  .flatMap(_.toTypedValue[Person2])
  
assert(person2 == Right(Person2("John Doe")))
```

## Deriving Migrations

ZIO Schema provides a way to derive migrations automatically using the `Migration.derive` operation, which is what `Schema#migrate` uses:

```scala
object Migration {
  def derive(from: MetaSchema, to: MetaSchema): Either[String, Chunk[Migration]]
}
```

It takes two `MetaSchema` values, the old and the new schema, and returns a `Chunk[Migration]` that describes the migration steps. The following example derives the steps between two versions of a `Person` model and prints them:

```scala
import zio.schema.meta._

case class Person1(name: String, age: Int, language: String, height: Int)

object Person1 {
  implicit val schema: Schema[Person1] = DeriveSchema.gen
}

case class Person2(
    name: String,
    role: String,
    language: Set[String],
    height: Double
)

object Person2 {
  implicit val schema: Schema[Person2] = DeriveSchema.gen
}

val migrations = Migration.derive(
  MetaSchema.fromSchema(Person1.schema),
  MetaSchema.fromSchema(Person2.schema)
)
// migrations: Either[String, zio.Chunk[Migration]] = Right(
//   value = IndexedSeq(
//     IncrementDimensions(path = IndexedSeq("language", "item"), n = 1),
//     ChangeType(path = IndexedSeq("height"), value = double),
//     AddNode(
//       path = IndexedSeq("role"),
//       node = Value(
//         valueType = string,
//         path = IndexedSeq("role"),
//         optional = false
//       )
//     ),
//     DeleteNode(path = IndexedSeq("age"))
//   )
// )

println(migrations)
// Right(Chunk(IncrementDimensions(Chunk(language,item),1),ChangeType(Chunk(height),double),AddNode(Chunk(role),string),DeleteNode(Chunk(age))))
```

The output lists the steps that turn the old structure into the new one. Here, `language` changes from a `String` to a `Set[String]`, so the migration adds a dimension to it. The `height` field changes type, `role` is added, and `age` is removed. Deriving these steps succeeds, but applying them to a `Person1` value fails, because the `ChangeType` step for `height` and the `AddNode` step for `role` cannot be applied, as described in the caution above.
