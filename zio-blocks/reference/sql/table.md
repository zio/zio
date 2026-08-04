# Table

> Reference page for Table[A], the entity-to-table metadata binding in the sql module for schema-driven JDBC mapping and DDL generation.

`Table[A]` is the metadata binding between a Scala type `A` and a specific database table in the `sql` module. It holds the table name, a `DbCodec[A]` for reading and writing rows, and an `IndexedSeq[ColumnMeta]` describing each column's name, SQL type representative, and nullability. `Table` provides both type-safe column access and dialect-aware DDL generation without any ORM runtime or session lifecycle.

The structural shape of `Table` is:

```scala
final case class Table[A](name: String, codec: DbCodec[A], columnsMeta: IndexedSeq[ColumnMeta]) {
  def columns: IndexedSeq[String] = ???
  
  def createTable(dialect: SqlDialect): Frag = ???
  def dropTable: Frag = ???
}

object Table {
  def derived[A](implicit schema: Schema[A]): Table[A] = ???
  def derived[A](tableName: String)(implicit schema: Schema[A]): Table[A] = ???
  def derived[A](namingPolicy: TableNamingPolicy)(implicit schema: Schema[A]): Table[A] = ???
}
```

## Usage

The following example illustrates the core workflow: derive a table from a schema-equipped case class, inspect its column names, generate `CREATE TABLE` DDL, and finally generate `DROP TABLE` DDL:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

// Derive the table binding — columns come from the schema; "user" is a
// reserved word in PostgreSQL, so the table name is overridden explicitly
val table = Table.derived[User]("users")
// table: Table[User] = Table(
//   name = "users",
//   codec = zio.blocks.sql.DbCodecDeriver$$anon$20@178274d,
//   columnsMeta = Vector(
//     ColumnMeta(name = "id", dbValue = DbInt(0), nullable = false),
//     ColumnMeta(name = "name", dbValue = DbString(""), nullable = false),
//     ColumnMeta(name = "email", dbValue = DbString(""), nullable = false)
//   )
// )
table.name
// res1: String = "users"
table.columns
// res2: IndexedSeq[String] = Vector("id", "name", "email")

// Emit dialect-aware DDL as Frag values
val createSql = table.createTable(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
// createSql: String = """CREATE TABLE IF NOT EXISTS users (
//   id INTEGER NOT NULL,
//   name TEXT NOT NULL,
//   email TEXT NOT NULL
// )"""
val dropSql    = table.dropTable.sql(SqlDialect.PostgreSQL)
// dropSql: String = "DROP TABLE IF EXISTS users"
```

## Construction / Creating Instances

`Table` offers three `derived` factory methods on its companion object and one direct constructor via its primary constructor. All three `derived` overloads require a `Schema[A]` implicit.

### `Table.derived` — Derive using the default naming policy

`Table.derived[A]` inspects the `Schema[A]` implicit and applies `TableNamingPolicy.Singular` to produce the table name. This policy converts `CamelCase` Scala type names to `snake_case` SQL identifiers (for example, `UserProfile` becomes `user_profile`). The table name can be overridden by annotating the type with `@Modifier.config("sql.table_name", "my_table")`.

```scala
object Table {
  def derived[A](implicit schema: Schema[A]): Table[A]
}
```

The following example derives a table for a two-field case class and checks the resulting name and column list:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class BlogPost(title: String, body: String)
object BlogPost {
  implicit val schema: Schema[BlogPost] = Schema.derived
}

val table = Table.derived[BlogPost]
// table: Table[BlogPost] = Table(
//   name = "blog_post",
//   codec = zio.blocks.sql.DbCodecDeriver$$anon$20@56665516,
//   columnsMeta = Vector(
//     ColumnMeta(name = "title", dbValue = DbString(""), nullable = false),
//     ColumnMeta(name = "body", dbValue = DbString(""), nullable = false)
//   )
// )
table.name
// res4: String = "blog_post"
table.columns
// res5: IndexedSeq[String] = Vector("title", "body")
```

### `Table.derived` (with explicit table name) — Bypass naming policy and annotations

`Table.derived[A](tableName: String)` derives a table with the supplied name, ignoring both the `TableNamingPolicy` and any `@Modifier.config("sql.table_name", …)` annotation on the type. The column names and codec are still derived from the schema in the normal way. Use this overload when the desired SQL table name cannot be expressed by any naming policy.

```scala
object Table {
  def derived[A](tableName: String)(implicit schema: Schema[A]): Table[A]
}
```

The following example maps `UserProfile` to a table called `profiles` rather than the default `user_profile`:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class UserProfile(firstName: String, lastName: String)
object UserProfile {
  implicit val schema: Schema[UserProfile] = Schema.derived
}

val table = Table.derived[UserProfile]("profiles")
// table: Table[UserProfile] = Table(
//   name = "profiles",
//   codec = zio.blocks.sql.DbCodecDeriver$$anon$20@33f920a0,
//   columnsMeta = Vector(
//     ColumnMeta(name = "first_name", dbValue = DbString(""), nullable = false),
//     ColumnMeta(name = "last_name", dbValue = DbString(""), nullable = false)
//   )
// )
table.name
// res7: String = "profiles"
table.columns
// res8: IndexedSeq[String] = Vector("first_name", "last_name")
```

:::caution
The table name is validated as a SQL identifier immediately at construction time. Spaces, hyphens, or any character outside `[A-Za-z0-9_]` (with a letter or underscore as the first character) will cause `Table.derived` to throw `IllegalArgumentException`. For example, `Table.derived[UserProfile]("user profile")` throws with the message `Invalid SQL table identifier 'user profile'. Only ASCII letters, digits, and underscores are supported, and the first character must be a letter or underscore.`
:::

### `Table.derived` (with naming policy) — Control table name derivation

`Table.derived[A](namingPolicy: TableNamingPolicy)` derives a table and applies the supplied `TableNamingPolicy` to the type name when computing the table name. Use `TableNamingPolicy.Plural` for pluralized names, `TableNamingPolicy.Singular` (the default) for singular names, or `TableNamingPolicy.Custom(f)` for arbitrary transformations.

```scala
object Table {
  def derived[A](namingPolicy: TableNamingPolicy)(implicit schema: Schema[A]): Table[A]
}
```

The following example uses `TableNamingPolicy.Plural` so that `Category` maps to the table `categories`:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class Category(name: String)
object Category {
  implicit val schema: Schema[Category] = Schema.derived
}

val singular = Table.derived[Category](TableNamingPolicy.Singular)
// singular: Table[Category] = Table(
//   name = "category",
//   codec = zio.blocks.sql.DbCodecDeriver$$anon$20@8cbb675,
//   columnsMeta = Vector(
//     ColumnMeta(name = "name", dbValue = DbString(""), nullable = false)
//   )
// )
singular.name
// res10: String = "category"

val plural = Table.derived[Category](TableNamingPolicy.Plural)
// plural: Table[Category] = Table(
//   name = "categories",
//   codec = zio.blocks.sql.DbCodecDeriver$$anon$20@54cdf256,
//   columnsMeta = Vector(
//     ColumnMeta(name = "name", dbValue = DbString(""), nullable = false)
//   )
// )
plural.name
// res11: String = "categories"

val custom = Table.derived[Category](TableNamingPolicy.Custom(n => s"tbl_$n"))
// custom: Table[Category] = Table(
//   name = "tbl_Category",
//   codec = zio.blocks.sql.DbCodecDeriver$$anon$20@4860b66a,
//   columnsMeta = Vector(
//     ColumnMeta(name = "name", dbValue = DbString(""), nullable = false)
//   )
// )
custom.name
// res12: String = "tbl_Category"
```

### `Table.apply` — Construct directly from codec and column metadata

The primary constructor accepts the table name, a `DbCodec[A]`, and an `IndexedSeq[ColumnMeta]` explicitly. SQL identifier validation runs for the table name and every column name at construction time. Use this constructor when you have a hand-written or externally produced codec rather than a schema-derived one.

```scala
final case class Table[A](name: String, codec: DbCodec[A], columnsMeta: IndexedSeq[ColumnMeta])
```

The following example builds a `Table` manually, supplying a pre-existing `DbCodec` and explicit column metadata:

```scala
import zio.blocks.sql._

case class Tag(id: Int, label: String) derives DbCodec

// columnsMeta must describe the same columns, in the same order, as the codec;
// nullable must match the field's actual optionality (Tag.label is non-optional)
val meta  = IndexedSeq(
  ColumnMeta("id",    DbValue.DbInt(0),    nullable = false),
  ColumnMeta("label", DbValue.DbString(""), nullable = false)
)
// meta: IndexedSeq[ColumnMeta] = Vector(
//   ColumnMeta(name = "id", dbValue = DbInt(0), nullable = false),
//   ColumnMeta(name = "label", dbValue = DbString(""), nullable = false)
// )
val table = Table[Tag]("tag", DbCodec[Tag], meta)
// table: Table[Tag] = Table(
//   name = "tag",
//   codec = zio.blocks.sql.DbCodecDeriver$$anon$20@7d9c62e9,
//   columnsMeta = Vector(
//     ColumnMeta(name = "id", dbValue = DbInt(0), nullable = false),
//     ColumnMeta(name = "label", dbValue = DbString(""), nullable = false)
//   )
// )
table.name
// res14: String = "tag"
table.columns
// res15: IndexedSeq[String] = Vector("id", "label")
```

:::note
When the column `nullable` flag is `true`, the generated `CREATE TABLE` statement omits the `NOT NULL` constraint for that column, allowing the database to store `NULL` in that position. `Table.derived` sets this flag automatically based on whether the corresponding schema field is `Option[A]` or `Maybe[A]`.
:::

## Core Operations

### Element Access

The Element Access category exposes `columns`, which returns the SQL column names carried by the table in codec order.

#### `columns` — Column names in codec order

`Table#columns` returns an `IndexedSeq[String]` of the SQL column names for this table, in the same order as the underlying `DbCodec[A]`. The names are drawn from the validated `columnsMeta` and have already been checked to be legal SQL identifiers at construction time. Access is O(1) since the sequence is built once during construction.

```scala
final case class Table[A](...) {
  def columns: IndexedSeq[String]
}
```

The following example shows `columns` reflecting the snake_case field names derived from the schema:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class OrderLine(productId: Int, quantity: Int, unitPrice: BigDecimal)
object OrderLine {
  implicit val schema: Schema[OrderLine] = Schema.derived
}

val table = Table.derived[OrderLine]
// table: Table[OrderLine] = Table(
//   name = "order_line",
//   codec = zio.blocks.sql.DbCodecDeriver$$anon$20@3d3c4a76,
//   columnsMeta = Vector(
//     ColumnMeta(name = "product_id", dbValue = DbInt(0), nullable = false),
//     ColumnMeta(name = "quantity", dbValue = DbInt(0), nullable = false),
//     ColumnMeta(name = "unit_price", dbValue = DbBigDecimal(0), nullable = false)
//   )
// )
table.columns
// res17: IndexedSeq[String] = Vector("product_id", "quantity", "unit_price")
```

### DDL Generation

The DDL Generation category provides `createTable` and `dropTable`, which produce `Frag` values containing dialect-specific `CREATE TABLE IF NOT EXISTS` and `DROP TABLE IF EXISTS` statements. Both methods delegate to the `Ddl` helper, which constructs a `Frag` with no bound parameters — only literal SQL text.

#### `createTable` — Generate a CREATE TABLE statement

`Table#createTable` accepts a `SqlDialect` and returns a `Frag` whose SQL text is a `CREATE TABLE IF NOT EXISTS` statement. Each column definition uses the dialect's `typeName` method to convert the column's `DbValue` representative to the appropriate SQL type string (for example, `DbValue.DbString` becomes `TEXT` in PostgreSQL and `TEXT` in SQLite; `DbValue.DbInt` becomes `INTEGER` in both). Non-nullable columns carry a `NOT NULL` constraint; nullable columns do not.

```scala
final case class Table[A](...) {
  def createTable(dialect: SqlDialect): Frag
}
```

The following example demonstrates the DDL generated for a record with a mix of column types and an optional field:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class Product(sku: String, price: BigDecimal, stock: Option[Int])
object Product {
  implicit val schema: Schema[Product] = Schema.derived
}

val table    = Table.derived[Product]
// table: Table[Product] = Table(
//   name = "product",
//   codec = zio.blocks.sql.DbCodecDeriver$$anon$20@5f7e746b,
//   columnsMeta = Vector(
//     ColumnMeta(name = "sku", dbValue = DbString(""), nullable = false),
//     ColumnMeta(name = "price", dbValue = DbBigDecimal(0), nullable = false),
//     ColumnMeta(name = "stock", dbValue = DbInt(0), nullable = true)
//   )
// )
val createPg = table.createTable(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
// createPg: String = """CREATE TABLE IF NOT EXISTS product (
//   sku TEXT NOT NULL,
//   price NUMERIC NOT NULL,
//   stock INTEGER
// )"""
val createSq = table.createTable(SqlDialect.SQLite).sql(SqlDialect.SQLite)
// createSq: String = """CREATE TABLE IF NOT EXISTS product (
//   sku TEXT NOT NULL,
//   price TEXT NOT NULL,
//   stock INTEGER
// )"""
```

:::caution
`Table#createTable` only supports column types whose `DbValue` representative maps to a primitive SQL type. Fields whose codec falls back to JSONB serialization (for example, `List[A]` or a sealed trait with multiple variants) will produce a `TEXT` or `JSONB` column — the DDL column type depends on the representative `DbValue` assigned during column metadata derivation, which in those cases is `DbValue.DbString`. Nested records that are flattened into multiple columns are fully supported.
:::

#### `dropTable` — Generate a DROP TABLE statement

`Table#dropTable` returns a `Frag` whose SQL text is `DROP TABLE IF EXISTS <name>`, with no parameters and no dialect argument. Because `DROP TABLE` syntax is uniform across the supported dialects, a single `Frag` is correct for any `SqlDialect`. Render the fragment with `Frag#sql` to obtain the final SQL string.

```scala
final case class Table[A](...) {
  def dropTable: Frag
}
```

The following example shows the drop statement for a table derived from a simple case class:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class Session(token: String, userId: Int)
object Session {
  implicit val schema: Schema[Session] = Schema.derived
}

val table = Table.derived[Session]
// table: Table[Session] = Table(
//   name = "session",
//   codec = zio.blocks.sql.DbCodecDeriver$$anon$20@3fa2a4af,
//   columnsMeta = Vector(
//     ColumnMeta(name = "token", dbValue = DbString(""), nullable = false),
//     ColumnMeta(name = "user_id", dbValue = DbInt(0), nullable = false)
//   )
// )
table.dropTable.sql(SqlDialect.PostgreSQL)
// res20: String = "DROP TABLE IF EXISTS session"
table.dropTable.sql(SqlDialect.SQLite)
// res21: String = "DROP TABLE IF EXISTS session"
```

## Supporting Types

`Table` is a monomorphic final case class with no subtypes of its own, but it depends on two supporting types — `ColumnMeta` and `TableNamingPolicy` — that control how column metadata is captured and how table names are derived.

### `ColumnMeta`

`ColumnMeta` is a final case class that carries the per-column metadata consumed by `Table#createTable` for DDL generation. Each field in a schema-derived type produces exactly one `ColumnMeta` (nested records are flattened; optional fields set `nullable = true`).

```scala
final case class ColumnMeta(name: String, dbValue: DbValue, nullable: Boolean)
```

The three fields serve distinct roles. `name` is the SQL column name after `SqlNameMapper` has been applied and SQL identifier validation has been run. `dbValue` is a representative instance of the column's `DbValue` variant (for example, `DbValue.DbInt(0)` for an `Int` column) — it carries no runtime data and is used only to dispatch to `SqlDialect#typeName` during DDL generation. `nullable` reflects whether the Scala field is `Option[A]` or `Maybe[A]`.

### `TableNamingPolicy`

`TableNamingPolicy` is a sealed trait that controls how a Scala type name is translated into a SQL table name when using `Table.derived`. All three `derived` overloads use a naming policy either explicitly or implicitly.

```scala
sealed trait TableNamingPolicy {
  def defaultName(typeName: String): String
}

object TableNamingPolicy {
  case object Singular                      extends TableNamingPolicy
  case object Plural                        extends TableNamingPolicy
  final case class Custom(f: String => String) extends TableNamingPolicy
}
```

The three variants cover the most common conventions:

- **`Singular`** (the default) — converts the Scala type name to `snake_case` using `SqlNameMapper.SnakeCase`. `UserProfile` becomes `user_profile`, `Category` becomes `category`.
- **`Plural`** — applies the same `snake_case` conversion and then appends a simple English pluralization suffix. `Category` becomes `categories`, `User` becomes `users`, `Quiz` becomes `quizzes`.
- **`Custom(f)`** — applies the function `f` to the type name, giving full control over the mapping. The function receives the raw Scala type name (before any case conversion) and must return a valid SQL identifier.

The `Singular` policy is chosen because most databases treat table names as singular nouns by convention, but `Plural` is equally idiomatic in many teams. Pass the desired policy explicitly to `Table.derived[A](namingPolicy)` when the default does not match your project's convention.

## Comparison

### Slick and Doobie

`Table` takes a narrower scope than lifted-embedding ORMs like Slick and functional query builders like Doobie:

| Concern                  | `Table` (this module)                                        | Slick                                                       | Doobie                                               |
|--------------------------|--------------------------------------------------------------|-------------------------------------------------------------|------------------------------------------------------|
| Schema source of truth   | `Schema[A]` (compile-time derivation)                        | `Table` class extending `TableQuery` (explicit column defs) | Hand-written `Get`/`Put` instances or Doobie macros  |
| Query DSL                | Plain SQL via `sql"..."` interpolator + `Frag` composition   | Lifted Scala expressions compiled to SQL                    | Plain SQL via `sql"..."` interpolator                |
| DDL generation           | `Table#createTable` / `Table#dropTable` return `Frag` values | Via `schema.create` / `schema.drop` (requires lifted query) | Not built-in; usually handled by Flyway or Liquibase |
| Runtime overhead         | Zero — derivation is compile-time; no reflection at runtime  | JVM reflection + query compilation per session              | Minimal; `Get`/`Put` are materialized type classes   |
| Effect system dependency | None (the shared API abstracts over `DbConnection`; no JDBC dependency in shared code) | Slick's `DBIO` monad                                        | Cats `IO` or `Sync[F]`                               |

`Table` does not model relationships, joins, or query projection — those concerns belong to hand-written `sql"..."` fragments and the `Repo` type. When you need rich relational queries, compose `Frag` values manually rather than using a lifted embedding.

### Hibernate JPA

`Table` and Hibernate address the same problem from opposite directions:

| Concern             | `Table` (this module)                                            | Hibernate / JPA                                                         |
|---------------------|------------------------------------------------------------------|-------------------------------------------------------------------------|
| Configuration style | Immutable value derived from `Schema[A]` at compile time         | Annotations on mutable entity classes at runtime                        |
| Session lifecycle   | None — connections managed explicitly by `Transactor`            | `EntityManager`, `Session`, first-level cache, lazy proxies             |
| Lazy loading        | Not supported — all column values are loaded eagerly             | Supported via proxy objects and byte-code instrumentation               |
| SQL control         | Full — every query is a `Frag` of literal SQL + typed parameters | Partial — JPQL / Criteria API abstracts SQL; native SQL as escape hatch |
| DDL generation      | `Table#createTable` returns a `Frag`; you execute it explicitly  | `hbm2ddl.auto` may run DDL automatically at startup                     |
| Scala compatibility | First-class; no mutable beans required                           | Requires JavaBean conventions (default constructor, mutable fields)     |

`Table` never manages entity identity, caching, or lazy associations. It is a thin, transparent layer over JDBC — what you write in `sql"..."` is exactly what the database executes.
