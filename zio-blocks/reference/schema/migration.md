# Migration

> `Migration[A, B]` is ZIO Blocks Schema's typed API for evolving data from one schema version to another. It wraps a fully serializable [`DynamicMigration`](#dynamicmigration) core with typed source and target schemas, giving you a builder-based workflow for adding fields, dropping fields, renaming fields, changing types, migrating nested values, transforming collections and maps, and composing migrations.

`Migration[A, B]` is ZIO Blocks Schema's typed API for evolving data from one schema version to another. It wraps a fully serializable [`DynamicMigration`](#dynamicmigration) core with typed source and target schemas, giving you a builder-based workflow for adding fields, dropping fields, renaming fields, changing types, migrating nested values, transforming collections and maps, and composing migrations.

## Overview

The migration system is split into three layers:

- **`Migration[A, B]`** — typed migration between source type `A` and target type `B`
- **`DynamicMigration`** — pure runtime representation built from serializable actions
- **`MigrationBuilder[A, B, Changeset]`** — macro-validated builder that accumulates actions and checks that the target shape is fully handled before `build`

```
Migration[A, B]
  ├── sourceSchema: Schema[A]
  ├── targetSchema: Schema[B]
  └── dynamicMigration: DynamicMigration
         └── actions: Chunk[MigrationAction]
                ├── AddField / DropField / RenameField
                ├── TransformField / ChangeFieldType
                ├── MandateField / OptionalizeField
                ├── RenameCase / TransformCase
                ├── MigrateField
                ├── TransformElements / TransformKeys / TransformValues
                └── Irreversible
```

`Migration` is the user-facing entry point. `DynamicMigration` is the transport-friendly representation you can inspect, serialize, or apply dynamically.

## Creating a Migration

The normal entry point is `Migration.newBuilder[A, B]`:

```scala
import zio.blocks.schema._
import zio.blocks.schema.migration._

case class PersonV1(name: String)
case class PersonV2(name: String, age: Int)

object PersonV1 {
  implicit val schema: Schema[PersonV1] = Schema.derived
}

object PersonV2 {
  implicit val schema: Schema[PersonV2] = Schema.derived
}

val migration: Migration[PersonV1, PersonV2] =
  Migration
    .newBuilder[PersonV1, PersonV2]
    .addField(_.age, SchemaExpr.literal(0))
    .build
```

Applying the migration converts the source value to [`DynamicValue`](./dynamic-value.md), applies the dynamic actions, then converts the result back into the target type:

```scala
import zio.blocks.schema._
import zio.blocks.schema.migration._

case class PersonV1(name: String)
case class PersonV2(name: String, age: Int)

object PersonV1 {
  implicit val schema: Schema[PersonV1] = Schema.derived
}

object PersonV2 {
  implicit val schema: Schema[PersonV2] = Schema.derived
}

val migration = Migration
  .newBuilder[PersonV1, PersonV2]
  .addField(_.age, SchemaExpr.literal(0))
  .build

val result = migration(PersonV1("Alice"))
// Right(PersonV2("Alice", 0))
```

## Core Operations

### `Migration#apply`

Transforms a typed value:

```scala
def apply(value: A): Either[SchemaError, B]
```

### `Migration#reverse`

Returns the **structural reverse** of the migration:

```scala
def reverse: Migration[B, A]
```

This is best-effort at runtime. Actions that lose information (for example `TransformField`, `ChangeFieldType`, `OptionalizeField`, or collection-wide transforms) reverse to `Irreversible`, which causes reverse execution to fail with a descriptive error instead of silently fabricating data.

### Composition

Migrations compose with `++` or `andThen`:

```scala
import zio.blocks.schema._
import zio.blocks.schema.migration._

case class PersonV1(name: String)
case class PersonV2(name: String, age: Int)
case class PersonV3(name: String, age: Int, city: String)

object PersonV1 { implicit val schema: Schema[PersonV1] = Schema.derived }
object PersonV2 { implicit val schema: Schema[PersonV2] = Schema.derived }
object PersonV3 { implicit val schema: Schema[PersonV3] = Schema.derived }

val migration = Migration
  .newBuilder[PersonV1, PersonV2]
  .addField(_.age, SchemaExpr.literal(0))
  .build

val addCity = Migration
  .newBuilder[PersonV2, PersonV3]
  .addField(_.city, SchemaExpr.literal(""))
  .build

val combined: Migration[PersonV1, PersonV3] = migration ++ addCity
```

## Builder Operations

`MigrationBuilder` uses selector expressions such as `_.field`, `_.nested.field`, `_.items.each`, and `_.data.eachValue`. The builder's third type parameter, `Changeset`, tracks which operations have already been applied so `build` can validate completeness.

### Record operations

- `addField(_.target, default)`
- `dropField(_.source, defaultForReverse)`
- `renameField(_.from, _.to)`
- `transformField(_.from, _.to, expr)`
- `mandateField(_.optionalSource, _.target, default)`
- `optionalizeField(_.source, _.optionalTarget)`
- `changeFieldType(_.source, expr)`

```scala
import zio.blocks.schema._
import zio.blocks.schema.migration._

case class UserV1(firstName: String, nickname: Option[String], age: Int)
case class UserV2(fullName: String, nickname: String, age: String)

object UserV1 {
  implicit val schema: Schema[UserV1] = Schema.derived
}

object UserV2 {
  implicit val schema: Schema[UserV2] = Schema.derived
}

val userMigration = Migration
  .newBuilder[UserV1, UserV2]
  .renameField(_.firstName, _.fullName)
  .mandateField(_.nickname, _.nickname, SchemaExpr.literal("anonymous"))
  .changeFieldType(_.age, SchemaExpr.literal("30"))
  .build
```

`transformField` and `changeFieldType` evaluate their `SchemaExpr` against the currently focused field value, not the entire root record. In practice, that means the expression should be written in terms of the value being replaced.

### Nested migration with `migrateField`

When a nested value has its own migration, use `migrateField`:

```scala
import zio.blocks.schema._
import zio.blocks.schema.migration._

case class AddressV1(street: String, city: String)
case class AddressV2(street: String, city: String, zip: String)
case class PersonWithAddressV1(name: String, address: AddressV1)
case class PersonWithAddressV2(name: String, address: AddressV2)

object AddressV1 { implicit val schema: Schema[AddressV1] = Schema.derived }
object AddressV2 { implicit val schema: Schema[AddressV2] = Schema.derived }
object PersonWithAddressV1 { implicit val schema: Schema[PersonWithAddressV1] = Schema.derived }
object PersonWithAddressV2 { implicit val schema: Schema[PersonWithAddressV2] = Schema.derived }

val addressMigration = Migration
  .newBuilder[AddressV1, AddressV2]
  .addField(_.zip, SchemaExpr.literal("00000"))
  .build

val personMigration = Migration
  .newBuilder[PersonWithAddressV1, PersonWithAddressV2]
  .migrateField(_.address, addressMigration)
  .build
```

There is no `transformNested` builder method in the current API. Nested structural changes are expressed by building a migration for the nested type and applying it with `migrateField`.

### Enum and collection operations

- `renameCase(from, to)`
- `transformCase(caseName)(...)`
- `transformElements(_.items, expr)`
- `transformKeys(_.data, expr)`
- `transformValues(_.data, expr)`

Collection and map transforms are map-like operations. The `SchemaExpr` is evaluated once per matched value:

- `transformElements` evaluates against each collection element
- `transformKeys` evaluates against each map key
- `transformValues` evaluates against each map value

For example, `transformElements(_.scores, expr)` runs `expr` separately for every element in `scores`, and `transformValues(_.metadata, expr)` runs it separately for every map value.

## `build`

```scala
def build(using ev: MigrationComplete[A, B, Changeset]): Migration[A, B]
```

`build` runs macro validation. It checks that every field needed to transform `A` into `B` is either:

- auto-mapped by identical source/target structure, or
- explicitly handled by one of the builder operations

If the migration is incomplete, the code fails to compile.

This validated `build` step is the public completion path for `MigrationBuilder`. If you need unvalidated or dynamically assembled migrations, work at the `DynamicMigration` layer and wrap it with `Migration.fromDynamic`.

## `DynamicMigration`

`DynamicMigration` is the untyped runtime representation:

```scala
final case class DynamicMigration(actions: Chunk[MigrationAction]) {
  def apply(value: DynamicValue): Either[SchemaError, DynamicValue]
  def ++(that: DynamicMigration): DynamicMigration
  def andThen(that: DynamicMigration): DynamicMigration
  def reverse: DynamicMigration
  def isEmpty: Boolean
  def size: Int
}
```

Use it when you need to inspect, serialize, or apply migrations without keeping the original Scala types around.

## `MigrationAction`

`MigrationAction` is the serializable ADT used by `DynamicMigration`.

| Action | Purpose | Reverse behavior |
|---|---|---|
| `AddField` | add a field with a default expression | `DropField` |
| `DropField` | remove a field | `AddField` using stored reverse default |
| `RenameField` | rename a record field | structural inverse rename |
| `TransformField` | replace a field by evaluating an expression against the current field value | `Irreversible` |
| `MandateField` | convert `Option[A]` to `A` with default for `None` | `OptionalizeField` |
| `OptionalizeField` | wrap a value in `Some` | `Irreversible` |
| `ChangeFieldType` | replace a field with a converted value computed from the current field value | `Irreversible` |
| `RenameCase` | rename enum case | inverse rename |
| `TransformCase` | run actions inside a specific case | reverse inner actions in reverse order |
| `MigrateField` | apply a nested `DynamicMigration` to a field | reverse nested migration |
| `TransformElements` | replace each collection element by evaluating the expression on that element | `Irreversible` |
| `TransformKeys` | replace each map key by evaluating the expression on that key | `Irreversible` |
| `TransformValues` | replace each map value by evaluating the expression on that value | `Irreversible` |
| `Irreversible` | explicit non-invertible sentinel | itself |

## Errors

Migration execution returns [`SchemaError`](./schema-error.md), with migration-specific kinds including:

- path not found
- type mismatch
- missing default
- transform failure
- field/case not found
- invalid value
- mandate failure

Errors carry a [`DynamicOptic`](./dynamic-optic.md) path so failures can be traced to the precise field or nested location that failed.

## Relationship to Other Schema-Evolution APIs

`Into` and `As` derive structural conversions automatically. `Migration` is lower-level and more explicit:

- use [`Into`](./schema-evolution/into.md) for one-way derived schema evolution
- use [`As`](./schema-evolution/as.md) for bidirectional derived evolution
- use `Migration` when you need explicit, inspectable, serializable transformation steps

:::tip
If you want a practical migration walkthrough rather than a reference, start with [`MigrationSpec`](https://github.com/zio/zio-blocks/blob/main/schema/shared/src/test/scala/zio/blocks/schema/migration/MigrationSpec.scala) and [`DynamicMigrationSpec`](https://github.com/zio/zio-blocks/blob/main/schema/shared/src/test/scala/zio/blocks/schema/migration/DynamicMigrationSpec.scala) in the repository until a dedicated migration guide lands.
:::
