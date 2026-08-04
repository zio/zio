# SQL Module

> Reference index for the zio-blocks-sql module: schema-driven SQL fragments, bidirectional codecs, CRUD repositories, and transaction management.

The `zio-blocks-sql` module provides type-safe, schema-driven SQL query building and execution on relational databases. Its core types — `DbCodec`, `Frag`, `Table`, `Repo`, `Transactor`, `DbCon`, and `DbTx` — form a layered system: codecs map Scala types to database columns, fragments carry parameterized SQL built via string interpolation, repositories expose CRUD operations derived at compile time from a schema, and a transactor manages the connection lifecycle with automatic commit and rollback.

## Motivation

Relational database access in Scala typically involves one of three trade-offs: 

- Raw JDBC requires manual row mapping and string concatenation (SQL injection risk, tedious boilerplate)
- Reflection-based ORMs hide SQL entirely (opaque at compile time, difficult to optimize)
- Lifted-embedding DSLs like Slick require learning a parallel query language on top of SQL. 

The `zio-blocks-sql` module takes a different path: it uses ZIO Blocks' `Schema` system for compile-time column metadata and familiar string interpolation for SQL text, giving you type safety without losing SQL's expressiveness.

Key advantages of the `zio-blocks-sql` module are:

- **Schema-driven derivation.** `DbCodec`, `Table`, and `Repo` all derive from the same `Schema[A]`, keeping column names, SQL types, and nullability in sync with your Scala data model automatically.
- **No runtime reflection.** Derivation runs at compile time through the `Deriver[DbCodec]` framework, producing zero-overhead codecs with no reflective calls at runtime.
- **Compile-time SQL parameterization.** The `sql"..."` macro interpolator verifies that every interpolated value has a `DbParam[A]` instance at compile time, converts it to a `DbValue`, and binds it to a `?` placeholder — no string concatenation, no SQL injection.
- **Composable fragments.** `Frag` values compose via `Frag#++`, letting you build reusable WHERE clauses, ORDER BY expressions, and pagination helpers that render correctly for any `SqlDialect`.
- **Explicit transaction boundaries.** `Transactor#transact` disables auto-commit, commits on success, and rolls back on any exception — the connection is always closed whether the block succeeds or throws.

## Installation

The core SQL module and the ZIO integration module publish separately. Add the artifacts you need to your build:

```scala
// Core SQL module (cross-built for JVM and Scala.js; the JDBC-backed
// JdbcTransactor implementation itself is JVM-only)
libraryDependencies += "dev.zio" %% "zio-blocks-sql" % "0.0.51"

// ZIO integration (lifts JDBC into ZIO effects; JVM-only)
libraryDependencies += "dev.zio" %% "zio-blocks-sql-zio" % "0.0.51"
```

Both modules require a JDBC driver on the classpath (for example, `org.xerial:sqlite-jdbc` for SQLite or `org.postgresql:postgresql` for PostgreSQL). Swap the JDBC URL and `SqlDialect` constant to switch databases.

## Overview

The module is organized into three groups: core types you interact with directly in application code, supporting types that implement the protocol layer, and a ZIO integration layer that lifts synchronous operations into the effect system.

### Core Types

These seven types form the public API for everyday SQL work:

- **[`DbCodec`](./db-codec.md)** — Converts between Scala values and database columns. Read values from result sets by column label or position; write values as bound parameters. Derived automatically from `Schema[A]`.
- **[`Frag`](./frag.md)** — An immutable SQL fragment built with the `sql"..."` interpolator. Holds literal SQL text and typed parameters separately, preventing SQL injection. Executed via `query`, `queryOne`, `update`, and other extension methods.
- **[`Table`](./table.md)** — Binds a Scala type to a database table: table name, codec, and column metadata. Derived from a `Schema` using naming conventions. Provides DDL generation via `createTable` and `dropTable`.
- **[`Repo`](./repo.md)** — Type-safe CRUD repository providing `all`, `find`, `insert`, `update`, `delete`, and other standard operations. All SQL is pre-built at construction time.
- **[`Transactor`](./transactor.md)** — Entry point for executing SQL. `connect` opens a connection; `transact` adds automatic commit/rollback. `JdbcTransactor` is the JDBC implementation.
- **[`DbCon`](./db-con.md)** — Implicit context available inside a `Transactor` block. Carries the connection, dialect, and logger. Threaded through all SQL operations automatically.
- **[`DbTx`](./db-tx.md)** — A `DbCon` subtype marking transactional scope (inside `Transactor#transact`). Extends `DbCon` so transactional and non-transactional operations compose freely.

### Supporting Types

These types implement the protocol and derivation layers and are rarely used directly in application code:

- **[`DbValue`](./db-value.md)** — Sealed ADT representing typed database values. Used internally by `DbCodec` and `Frag` to hold parameters before binding.
- **[`DbParam`](./db-param.md)** — Typeclass converting Scala values to `DbValue` for the `sql"..."` interpolator. Instances provided for all common types, `Option[A]`, and `Maybe[A]`.
- **[`SqlDialect`](./sql-dialect.md)** — Encodes database-specific SQL: type names for DDL and parameter placeholder tokens. `PostgreSQL` and `SQLite` are built-in.
- **[`SqlLogger`](./sql-logger.md)** — Hook for observing query execution. Receives SQL, parameters, duration, and row count on success or error.
- **[`SqlNameMapper`](./sql-name-mapper.md)** — Maps Scala field names to SQL column names. `SnakeCase` (default converts `camelCase` to `snake_case`), `Identity`, or `Custom` function.
- **[`TableMetadata`](./table-metadata.md)** — Derives column metadata from a `Schema`: names, types, and nullability. Used by `Table.derived` to populate table structure.
- **[`Ddl`](./ddl.md)** — Generates `CREATE TABLE IF NOT EXISTS` and `DROP TABLE IF EXISTS` fragments from column definitions.
- **[`DbConnection`](./db-connection.md)** — Abstraction over JDBC `Connection`. Prepares statements, controls transactions, and manages lifecycle.
- **[`DbResultReader`](./db-result-reader.md)** — Reads column values from result sets by label or 1-based position. Supports null detection via `wasNull`. Used by `DbCodec`.
- **[`DbParamWriter`](./db-param-writer.md)** — Binds parameter values to prepared statements. Covers all primitive, temporal, and UUID types. Used by `DbCodec`.
- **[`DbCodecDeriver`](./db-codec-deriver.md)** — Schema-driven codec derivation engine. Converts `Schema[A]` to `DbCodec[A]`, handling primitives, records, options, and JSONB types.
- **[`TransactorZIO`](./transactor-zio.md)** — ZIO integration for `Transactor`. Runs SQL effects on the blocking thread pool with proper resource cleanup and interrupt handling. Includes `ZLayer` support.

## How They Work Together

Five interconnected flows define how the module's types cooperate: codec derivation, SQL assembly, execution context, entity mapping, and transaction lifecycle. Understanding these flows shows why each type exists and how to compose them.

The typical workflow proceeds in this order:

1. Define a `Schema[A]` for your domain type (usually with `Schema.derived`).
2. Call `Repo.derived` (or `Table.derived` + `Repo.apply`) to produce a `Repo[E, ID]` — all SQL is pre-built here.
3. Create a `JdbcTransactor` from a JDBC URL or `DataSource`.
4. Open a connection with `Transactor#connect` (for reads) or `Transactor#transact` (for writes), which supplies a `DbCon` or `DbTx` context.
5. Inside the context block, call `Repo` methods or execute `sql"..."` fragments directly.

The following diagram shows the data-flow relationships between types:

```
                        Schema[A]
                            │
           ┌────────────────┼──────────────────┐
           │                │                  │
           ▼                ▼                  ▼
    DbCodecDeriver    TableMetadata      deriveTableName
           │           .columnsFor()    (TableNamingPolicy
           ▼                │            + SqlNameMapper)
      DbCodec[A] ◄──────────┘                  │
           │                                   │
           └───────────────────────────────────┘
                            │
                    Table[A](name, codec, columnsMeta)
                            │
                    Repo[E, ID]                   sql"..." → Frag
                    ─ all / find                   ─ frag.query[A]
                    ─ insert / update / delete     ─ frag.update
                            │                             │
                    ┌───────┴─────────────────────────────┘
                    │
                    ▼
              Transactor
              ├─ .connect  { ... }    (non-transactional, DbCon in scope)
              └─ .transact { ... }    (commit / rollback, DbTx in scope)
                                │
                         DbCon / DbTx
                   ┌────────────┼────────────┐
                   ▼            ▼            ▼
            DbConnection    SqlDialect   SqlLogger
            (JDBC wrapper) (PostgreSQL  (observability)
                            | SQLite)
```

The `sql"..."` interpolator is defined as an extension on `StringContext`. At compile time the macro verifies that every interpolated expression has a `DbParam[A]` instance, converts it to a `DbValue`, and assembles a `Frag` with literal SQL in `parts` and the typed values in `params`. The `Frag` carries no dialect-specific rendering until `Frag#sql(dialect)` is called, so the same fragment works with PostgreSQL and SQLite unchanged.

A complete end-to-end example showing schema definition, table setup, repository construction, and mixed repository/fragment queries follows:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema
import zio.blocks.maybe.Maybe

case class User(id: Int, name: String, email: String)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

// 1. Create transactor and repository
val tx   = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
// tx: JdbcTransactor = zio.blocks.sql.JdbcTransactor@3fe7357a
val repo = Repo.derived[User, Int]("users", "id", _.id)
// repo: Repo[User, Int] = zio.blocks.sql.Repo$DerivedRepo@4206125b

// 2. Set up schema and run a transactional workflow
tx.transact {
  // Create table using derived DDL
  repo.table.createTable(summon[DbTx].dialect).update

  // Insert via repository (uses pre-built INSERT fragment)
  repo.insert(User(1, "Alice", "alice@example.com"))
  repo.insert(User(2, "Bob", "bob@example.com"))

  // Read via repository
  val alice: Maybe[User] = repo.find(1)

  // Read via raw SQL fragment — composes freely with repo operations
  val aUsers: List[User] =
    sql"SELECT id, name, email FROM users WHERE name LIKE ${"A%"}".query[User]

  // Update and delete
  repo.update(User(1, "Alice Smith", "alice.smith@example.com"))
  repo.delete(2)

  (alice, aUsers)
}
// res1: Tuple2[Maybe[User], List[User]] = (
//   User(id = 1, name = "Alice", email = "alice@example.com"),
//   List(User(id = 1, name = "Alice", email = "alice@example.com"))
// )
```

Inside `transact`, auto-commit is disabled. If any call throws, the entire block rolls back and the exception propagates. On normal return, the transaction commits and the connection closes.

## Common Patterns

The module is designed around a small set of patterns that appear throughout most application code. Recognizing these patterns makes it easy to choose the right approach for each situation.

### Schema-Driven Derivation

`DbCodec`, `Table`, and `Repo` all derive from a single `Schema[A]`. Derivation respects `@Modifier` annotations — `@Modifier.rename` overrides a column name, `@Modifier.transient` excludes a field from the codec, and `@Modifier.config("sql.table_name", "my_table")` overrides the table name. This means your Scala type definition is the single source of truth for column names, types, and nullability:

```scala
import zio.blocks.sql._
import zio.blocks.schema.{Schema, Modifier}

case class BlogPost(
  @Modifier.rename("post_id") id: Int,
  title: String,
  @Modifier.transient authorHandle: String = ""  // excluded from SQL
)
object BlogPost {
  implicit val schema: Schema[BlogPost] = Schema.derived
}

val repo = Repo.derived[BlogPost, Int]("post_id", _.id)
// repo: Repo[BlogPost, Int] = zio.blocks.sql.Repo$DerivedRepo@243d4111
repo.table.name
// res3: String = "blog_post"
repo.table.codec.columns
// res4: IndexedSeq[String] = Vector("post_id", "title")
```

### Implicit Context Threading

Every SQL operation — `Frag` execution and `Repo` CRUD — requires an implicit `DbCon` (or `DbTx`) in scope. The context carries the connection, dialect, and logger, but you never pass it explicitly. Calling code inside `Transactor#connect` or `Transactor#transact` automatically has the context available, and helper methods can propagate it with a `using` parameter:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class Product(id: Int, name: String, price: BigDecimal)
object Product { implicit val schema: Schema[Product] = Schema.derived }

def cheapProducts(maxPrice: BigDecimal)(using DbCon): List[Product] =
  sql"SELECT id, name, price FROM product WHERE price < $maxPrice".query[Product]

val tx = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
tx.connect {
  // `cheapProducts` picks up the DbCon automatically
  val items = cheapProducts(BigDecimal("9.99"))
}
```

### Multi-Column Codecs

A single `DbCodec[A]` can span multiple database columns. When you use a multi-column type directly in an `sql"..."` expression, the `fromDbCodec` `DbParam` instance throws at runtime because it cannot collapse multiple values into a single `?` placeholder. Instead, use `Frag.values` for multi-row inserts or write the columns explicitly in the fragment:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class Point(x: Double, y: Double)
object Point { implicit val schema: Schema[Point] = Schema.derived }

// Multi-row insert using Frag.values — one (?, ?) tuple per row
val points = List(Point(1.0, 2.0), Point(3.0, 4.0))
// points: List[Point] = List(Point(x = 1.0, y = 2.0), Point(x = 3.0, y = 4.0))
val frag   = Frag.literal("INSERT INTO point (x, y) VALUES ") ++ Frag.values(points)
// frag: Frag = Frag(
//   parts = Vector("INSERT INTO point (x, y) VALUES (", ", ", "), (", ", ", ")"),
//   params = Vector(DbDouble(1.0), DbDouble(2.0), DbDouble(3.0), DbDouble(4.0))
// )
frag.sql(SqlDialect.SQLite)
// res7: String = "INSERT INTO point (x, y) VALUES (?, ?), (?, ?)"
```

### Type-Safe SQL Parameterization

The `sql"..."` interpolator accepts any Scala value for which a `DbParam[A]` exists. The macro checks this at compile time and binds the value to a `?` placeholder, preventing SQL injection regardless of the value's content. All standard scalar types have built-in instances, `Option[A]` binds to `NULL` or the inner value, and custom types with a `DbCodec[A]` automatically gain a `DbParam[A]`:

```scala
import zio.blocks.sql._

val userId: Int             = 42
// userId: Int = 42
val namePattern: String     = "%alice%"
// namePattern: String = "%alice%"
val active: Option[Boolean] = Some(true)
// active: Option[Boolean] = Some(true)

// All three are compile-time safe — no string concatenation
val frag =
  sql"SELECT * FROM users WHERE id = $userId AND name LIKE $namePattern AND active = $active"
// frag: Frag = Frag(
//   parts = ArraySeq(
//     "SELECT * FROM users WHERE id = ",
//     " AND name LIKE ",
//     " AND active = ",
//     ""
//   ),
//   params = Vector(DbInt(42), DbString("%alice%"), DbBoolean(true))
// )
frag.sql(SqlDialect.SQLite)
// res9: String = "SELECT * FROM users WHERE id = ? AND name LIKE ? AND active = ?"
frag.params
// res10: IndexedSeq[DbValue] = Vector(
//   DbInt(42),
//   DbString("%alice%"),
//   DbBoolean(true)
// )
```

### JSONB Serialization for Complex Types

When a field's type is not directly representable as a single column — such as `List[A]`, `Map[K, V]`, or a sealed trait with multiple variants — `DbCodecDeriver` automatically uses `DbCodec.jsonb[A]` to store and retrieve the value as a JSON-encoded `TEXT` or `JSONB` column. This keeps complex nested data in a single column without requiring a separate table:

```scala
import zio.blocks.sql._

case class Order(id: Int, tags: List[String], metadata: Map[String, String]) derives DbCodec

// `tags` and `metadata` are encoded via DbCodec.jsonb when read/written through Frag/Repo
val codec = DbCodec[Order]
// codec: DbCodec[Order] = zio.blocks.sql.DbCodecDeriver$$anon$20@10cf0d2d
codec.columns
// res12: IndexedSeq[String] = Vector("id", "tags", "metadata")
```

### Optional and Nullable Handling

`Option[A]` and `Maybe[A]` map to a single nullable column. Reading a `NULL` from the database produces `None` or `Maybe.absent`; writing `None` or `Maybe.absent` binds `NULL` to the parameter. For non-optional types, encountering an unexpected `NULL` throws `IllegalStateException` at read time, surfacing schema mismatches immediately rather than silently coercing `NULL` to a default:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class Profile(userId: Int, bio: Option[String], avatarUrl: Option[String])
object Profile { implicit val schema: Schema[Profile] = Schema.derived }

// bio and avatar_url become nullable TEXT columns in the generated DDL
val repo = Repo.derived[Profile, Int]("user_id", _.userId)

given DbCon = ???
// repo.insert(Profile(1, None, None)) binds NULL for both optional columns
repo.insert(Profile(1, None, None))
```

## Integration Points

The `zio-blocks-sql` module sits at the intersection of several other ZIO Blocks modules and integrates with the JVM JDBC ecosystem.

The primary dependency is **`zio-blocks-schema`**. `Schema[A]` (specifically its `Reflect` tree) is the source of all compile-time type metadata: field names, types, nullability, and annotations. `DbCodecDeriver` extends `Deriver[DbCodec]`, the same framework used by JSON, Avro, and other codec modules in the schema module's built-in codec suite. The `@Modifier.rename`, `@Modifier.transient`, and `@Modifier.config` annotations attach SQL-specific configuration to individual fields and types without coupling the domain model to the `zio-blocks-sql` module.

The module also depends on **`zio-blocks-maybe`** for `Maybe[A]` support. `Maybe[A]` has distinct absent and present semantics (unlike `Option` which cannot distinguish `Some(null)` from a genuinely absent value), and both `DbCodec` and `DbParam` provide `Maybe[A]` instances alongside `Option[A]`.

For transparent opaque-type support the module integrates with **`As[A, B]`** from `zio-blocks-schema`. If an opaque type has a `DbCodec` for its underlying type and an `As` conversion, `DbCodec` derives an instance for the opaque type automatically via `dbCodecFromAs` without requiring a hand-written codec.

The JDBC layer is isolated behind three abstractions — `DbConnection`, `DbResultReader`, and `DbParamWriter` — so the `JdbcTransactor` can be replaced by an alternate backend (for example, a WebSQL or node-postgres adapter on Scala.js) without changing any application code that depends only on the shared `zio-blocks-sql` module.

The **`zio-blocks-sql-zio` module** provides `TransactorZIO`, a ZIO-aware wrapper around `JdbcTransactor`. It exposes two execution models: blocking wrappers (`TransactorZIO#connect`, `TransactorZIO#transact`) that run synchronous bodies with `ZIO.attemptBlocking`, and effect-aware methods (`TransactorZIO#connectZIO`, `TransactorZIO#transactZIO`) that bracket connections via `ZIO.acquireRelease` so connections are always released even on fiber interruption. A `ZLayer` constructor (`TransactorZIO.layer`) integrates with ZIO's dependency injection system.

## See Also

- **[SQL-ZIO Integration Reference](../sql-zio.md)** — Reference page for `TransactorZIO` and the ZIO `ZLayer` integration.
