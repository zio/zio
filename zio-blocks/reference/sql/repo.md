# Repo

> Reference for Repo[E, ID]: a type-safe CRUD repository in the sql module that pre-generates all SQL at construction time from a Schema-derived Table.

`Repo[E, ID]` is a type-safe CRUD repository that provides `all`, `find`, `insert`, `update`, `delete`, and related operations for entities of type `E` identified by a primary key of type `ID`. In the `sql` module's layered architecture, `Repo` sits above `Table[E]` (which it wraps) and below the `Transactor` (which supplies the `DbCon` or `DbTx` context each operation requires). All SQL — `SELECT`, `INSERT`, `UPDATE`, and `DELETE` — is assembled from the `Table`'s column metadata at construction time; individual calls do only parameter binding.

Its primary constructor and public fields have this structure:

```scala
class Repo[E, ID](
  val table: Table[E],
  val idColumn: String,
  val idCodec: DbCodec[ID],
  val getId: E => ID
) {
  // ... all the CRUD methods are defined here ...
}
```

## Motivation

Writing CRUD SQL by hand requires keeping column names, result-set positions, and prepared-statement parameter counts in sync with your Scala types — a maintenance burden that grows with every added field or renamed column. `Repo` eliminates that burden by deriving all standard CRUD statements from the same `Schema[E]` that already describes your domain type: `Repo.derived` inspects the schema's field names, types, and `@Modifier` annotations once at construction time and stores the resulting parameterized fragments for reuse.

When the standard operations — select-all, select-by-id, insert, update, delete — cover your needs, `Repo` means zero SQL maintenance. When you need custom queries (filtered selects, joins, aggregations), the `sql"..."` interpolator and `Frag` type compose freely alongside `Repo` inside the same `Transactor#connect` or `Transactor#transact` block, so you never have to choose between a repository pattern and hand-written SQL.

## Usage

The example below shows the full lifecycle: deriving a repository from a `Schema`, setting up the table, writing and reading entities, and performing both bulk and single-entity operations — all inside one `transact` block:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema
import zio.blocks.maybe.Maybe

case class User(id: Int, name: String, email: String)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

// All SQL is generated here, once, from User's Schema.
val repo = Repo.derived[User, Int]("users", "id", _.id)
// repo: Repo[User, Int] = zio.blocks.sql.Repo$DerivedRepo@16e9cebd
val tx   = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
// tx: JdbcTransactor = zio.blocks.sql.JdbcTransactor@aae0228

tx.transact {
  repo.table.createTable(summon[DbTx].dialect).update // CREATE TABLE IF NOT EXISTS users …

  repo.insert(User(1, "Alice", "alice@example.com"))
  repo.insert(User(2, "Bob",   "bob@example.com"))

  val allUsers: List[User] = repo.all          // SELECT id, name, email FROM users
  val one: Maybe[User]     = repo.find(1)      // SELECT … WHERE id = ?
  val exists: Boolean      = repo.exists(99)   // SELECT … WHERE id = ?
  val total: Long          = repo.count        // SELECT COUNT(*) FROM users

  repo.update(User(1, "Alice Smith", "alice.smith@example.com"))
  repo.delete(2)

  // Single multi-row VALUES (…),(…) statement; returns the supplied IDs
  val ids: Seq[Int] = repo.insertAll(Seq(User(3, "Carol", "carol@example.com")))

  // JDBC addBatch/executeBatch; returns total row count
  val n: Int = repo.insertBatch(List(User(4, "Dave", "dave@example.com")))

  (allUsers, one, exists, total, ids, n)
}
// res1: Tuple6[List[User], Maybe[User], Boolean, Long, Seq[Int], Int] = (
//   List(
//     User(id = 1, name = "Alice", email = "alice@example.com"),
//     User(id = 2, name = "Bob", email = "bob@example.com")
//   ),
//   User(id = 1, name = "Alice", email = "alice@example.com"),
//   false,
//   2L,
//   List(3),
//   1
// )
```

Operations inside `transact` run under auto-commit disabled; on success the block commits and closes the connection, on any exception it rolls back.

## Construction / Creating Instances

`Repo` exposes four factory methods, three of which auto-derive the underlying `Table[E]` from an implicit `Schema[E]`. The `Repo.derived` overloads differ only in how much naming information is supplied; `Repo.apply` accepts a fully pre-built `Table[E]` for cases where you have already constructed or derived a `Table` manually.

### `Repo.derived` — Derive with ID column and getter

This overload derives the `Table[E]` from the implicit `Schema[E]` and uses the caller-supplied `idColumn` name and `getId` getter. The table name is computed by applying the default singular-snake-case policy to the type name — for example, `User` becomes `"user"` and `BlogPost` becomes `"blog_post"`.

```scala
object Repo {
  def derived[E, ID](idColumn: String, getId: E => ID)(using schema: Schema[E], idCodec: DbCodec[ID]): Repo[E, ID]
}
```

A `DbCodec[ID]` instance is resolved automatically for all primitive ID types (`Int`, `Long`, `String`, `UUID`, and others) without any additional imports. The typical usage looks like this:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class Article(id: Int, title: String, body: String)
object Article {
  implicit val schema: Schema[Article] = Schema.derived
}

val repo = Repo.derived[Article, Int]("id", _.id)
// repo: Repo[Article, Int] = zio.blocks.sql.Repo$DerivedRepo@2f907618
repo.table.name
// res3: String = "article"
repo.table.codec.columns
// res4: IndexedSeq[String] = Vector("id", "title", "body")
```

:::caution
The `idColumn` string must exactly match a column name in the derived table (after `SqlNameMapper` applies, so `camelCase` fields become `snake_case`). A mismatch throws `IllegalArgumentException` (via `require`) at construction time — not at query time.
:::

### `Repo.derived` — Derive with table name, ID column, and getter

This overload is identical to the previous one except that it accepts an explicit `tableName`, bypassing the naming policy entirely. Use it when the default snake-case convention does not match the actual database table name — for example, when migrating from a legacy schema.

```scala
object Repo {
  def derived[E, ID](tableName: String, idColumn: String, getId: E => ID)(using schema: Schema[E], idCodec: DbCodec[ID]): Repo[E, ID]
}
```

The supplied `tableName` is used verbatim (after SQL identifier validation) in all generated statements:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class OrderLine(lineId: Int, qty: Int, unitPrice: BigDecimal)
object OrderLine {
  implicit val schema: Schema[OrderLine] = Schema.derived
}

// Overrides the default "order_line" table name with the legacy one
val repo = Repo.derived[OrderLine, Int]("tbl_order_lines", "line_id", _.lineId)
// repo: Repo[OrderLine, Int] = zio.blocks.sql.Repo$DerivedRepo@2bc241ca
repo.table.name
// res6: String = "tbl_order_lines"
```

### `Repo.derived` — Fully automatic derivation

When called without any term arguments, `Repo.derived` requires an additional implicit `Schema[ID]` and locates the ID field in `E` using a four-priority rule, trying each in order until one matches:

1. A field annotated `@Modifier.id` whose type matches `ID`.
2. The unique field (among all fields) whose type matches `ID`, if there is exactly one.
3. A field literally named `"id"` whose type matches `ID`.
4. A field named `<entity>Id` (for example, `userId` for entity `User`) whose type matches `ID`.

The ID column name respects that field's `@Modifier.rename` annotation if present, or applies `SqlNameMapper.SnakeCase` to the field name otherwise. The table name uses the default naming policy.

```scala
object Repo {
  def derived[E, ID](using schema: Schema[E], idSchema: Schema[ID], idCodec: DbCodec[ID]): Repo[E, ID]
}
```

This is the most concise form when the entity type has exactly one field of the `ID` type:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class Tag(id: Long, label: String)
object Tag {
  implicit val schema: Schema[Tag] = Schema.derived
}

// Inspects Tag's Schema, finds the unique Long field "id", maps it to column "id"
val repo = Repo.derived[Tag, Long]
// repo: Repo[Tag, Long] = zio.blocks.sql.Repo$DerivedRepo@589393f1
repo.idColumn
// res8: String = "id"
```

:::caution
`Repo.derived` (fully auto) throws `IllegalArgumentException` at runtime if `E` is not a case class, if no field matches any of the four priorities, or if a priority level itself is ambiguous — multiple `@Modifier.id`-annotated fields of type `ID`, or multiple type-matching fields when none of them is named `"id"` or `<entity>Id`. When any ambiguity is possible, annotate the intended field with `@Modifier.id`, or prefer the explicit overload that names the ID column and getter directly.
:::

### `Repo.apply` — Construct from an explicit Table

`Repo.apply` is the base constructor. It accepts a fully built `Table[E]` together with the ID column name, codec, and getter. Use it when you already have a `Table` — for example, one obtained from `Table.derived` — and want precise control over all four components.

```scala
object Repo {
  def apply[E, ID](table: Table[E], idColumn: String, idCodec: DbCodec[ID], getId: E => ID): Repo[E, ID]
}
```

Combining `Table.derived` with `Repo.apply` gives access to DDL generation via `createTable` and `dropTable` while also enabling full CRUD:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class Category(categoryId: Int, name: String)
object Category {
  implicit val schema: Schema[Category] = Schema.derived
}

val table = Table.derived[Category]
val repo  = Repo(table, "category_id", DbCodec[Int], _.categoryId)

given DbCon = ???

repo.table.createTable(summon[DbCon].dialect).update  // DDL from Table
repo.all                                              // pre-built SELECT from Repo
```

## Core Operations

Every `Repo` method requires a `DbCon` (or `DbTx`) given in scope, which is supplied by the enclosing `Transactor#connect` or `Transactor#transact` block. Using `DbTx` (from `Transactor#transact`) means all writes in a block are committed atomically or rolled back together on exception.

### Read Operations

The read operations query the database without modifying it. `Repo#all`, `Repo#findAll`, and `Repo#find` decode and return entity values; `Repo#exists` and `Repo#count` return summary information without decoding full rows.

#### `all` — Retrieve all rows

`Repo#all` executes `SELECT <columns> FROM <table>` and decodes every result-set row into an `E` using the entity's `DbCodec`, returning all rows as a `List[E]` in database-native order.

```scala
class Repo[E, ID] {
  def all(using con: DbCon): List[E]
}
```

Inside a `connect` or `transact` block, the call requires no arguments:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val repo = Repo.derived[User, Int]("users", "id", _.id)
given DbCon = ???

// Inside a Transactor#connect or #transact block:
val users: List[User] = repo.all
```

:::caution
`all` loads the entire table into memory. For large tables, complement `Repo` with a custom `Frag` query that includes `LIMIT` and `OFFSET` clauses.
:::

#### `findAll` — Retrieve rows by a set of primary keys

`Repo#findAll` executes `SELECT <columns> FROM <table> WHERE <idColumn> IN (...)` for the given IDs and decodes every matching row into an `E`. It returns an empty `List` immediately, without executing any SQL, when `ids` is empty.

```scala
class Repo[E, ID] {
  def findAll(ids: Iterable[ID])(using con: DbCon): List[E]
}
```

Use it to batch-fetch a known set of rows in a single round-trip instead of calling `find` in a loop:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val repo = Repo.derived[User, Int]("users", "id", _.id)
given DbCon = ???

val users: List[User] = repo.findAll(List(1, 2, 3))
```

#### `find` — Find a row by primary key

`Repo#find` executes `SELECT <columns> FROM <table> WHERE <idColumn> = ?`, binding the ID through `idCodec`. It returns `Maybe.absent` if no row with the given key exists, or `Maybe(entity)` if a row is found.

```scala
class Repo[E, ID] {
  def find(id: ID)(using con: DbCon): Maybe[E]
}
```

The ID value is bound as a parameterized `?` — no string formatting or concatenation:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema
import zio.blocks.maybe.Maybe

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val repo = Repo.derived[User, Int]("users", "id", _.id)
given DbCon = ???

val alice: Maybe[User] = repo.find(1)
```

#### `exists` — Check whether a row exists

`Repo#exists` returns `true` when a row with the given primary key exists in the table. It delegates to `find` and tests whether the result is defined, running a single parameterized `SELECT`.

```scala
class Repo[E, ID] {
  def exists(id: ID)(using con: DbCon): Boolean
}
```

Use `exists` when you only need to confirm presence without loading the full entity:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val repo = Repo.derived[User, Int]("users", "id", _.id)
given DbCon = ???

val exists: Boolean = repo.exists(99)
```

#### `count` — Count all rows

`Repo#count` executes `SELECT COUNT(*) FROM <table>` and returns the row count as a `Long`. The result is `0L` when the table is empty.

```scala
class Repo[E, ID] {
  def count(using con: DbCon): Long
}
```

The `Long` result avoids boxing and handles tables with more than `Int.MaxValue` rows correctly:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val repo = Repo.derived[User, Int]("users", "id", _.id)
given DbCon = ???

val total: Long = repo.count
```

### Write Operations

The write operations insert, update, or delete rows in the database. `Repo#insert`, `Repo#insertBatch`, `Repo#insertAll`, `Repo#update`, `Repo#delete`, `Repo#deleteAll`, and `Repo#clear` each return an `Int` row count (`insertAll` returns the inserted IDs instead); `Repo#insertReturning` is the exception and returns the full inserted entity.

#### `insert` — Insert a single entity

`Repo#insert` encodes the entity with `DbCodec[E]` and executes `INSERT INTO <table> (<columns>) VALUES (?, …, ?)`, returning the number of affected rows — normally 1 on success.

```scala
class Repo[E, ID] {
  def insert(entity: E)(using con: DbCon): Int
}
```

Each call uses the pre-built SQL string and binds fresh parameter values from the entity:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val repo = Repo.derived[User, Int]("users", "id", _.id)
given DbCon = ???

val rowsAffected: Int = repo.insert(User(1, "Alice", "alice@example.com"))
```

#### `insertReturning` — Insert and return the inserted entity

`Repo#insertReturning` inserts the entity, retrieves the generated primary key via JDBC's `getGeneratedKeys`, and re-fetches the full row by calling `find` with that key. If the driver returns no generated key, it falls back to `find(getId(entity))`.

```scala
class Repo[E, ID] {
  def insertReturning(entity: E)(using con: DbCon): E
}
```

This method is most useful when the database assigns the primary key — for example, via an auto-increment or sequence column — and the caller needs to read the assigned value back:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val repo = Repo.derived[User, Int]("users", "id", _.id)
given DbCon = ???

// Pass a placeholder ID; the returned entity carries the database-assigned key.
val inserted: User = repo.insertReturning(User(0, "Bob", "bob@example.com"))
```

:::caution
`insertReturning` throws `NoSuchElementException` if the inserted row cannot be found after the insert. This can occur when the JDBC driver returns a key whose type does not match what `idCodec` expects. Verify that `idCodec` matches the database column type before relying on auto-generated keys.
:::

#### `insertBatch` — Batch-insert multiple entities

`Repo#insertBatch` inserts a collection of entities using JDBC batch execution (`addBatch` / `executeBatch`). Sending all parameter sets to the driver in a single round-trip is significantly faster than calling `insert` in a loop for large collections. It returns the total number of affected rows across all batched statements.

```scala
class Repo[E, ID] {
  def insertBatch(entities: Iterable[E])(using con: DbCon): Int
}
```

The method returns 0 immediately when the input is empty, without opening a prepared statement:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val repo = Repo.derived[User, Int]("users", "id", _.id)
given DbCon = ???

val users = List(
  User(1, "Alice", "alice@example.com"),
  User(2, "Bob",   "bob@example.com"),
  User(3, "Carol", "carol@example.com")
)
val rowsAffected: Int = repo.insertBatch(users)
```

`insertBatch` and `insertAll` solve different problems: `insertBatch` uses JDBC's multi-statement batch protocol (one prepared statement, multiple `executeBatch` rounds) and returns a row count; `insertAll` constructs a single multi-row `VALUES (…), (…)` SQL statement (one round-trip) and returns the primary keys in input order. For large collections or when relying on database-generated keys, prefer `insertBatch`. For moderate-sized collections where the caller controls the keys and wants a single SQL statement, prefer `insertAll`.

#### `insertAll` — Multi-row insert returning primary keys

`Repo#insertAll` assembles a single `INSERT INTO <table> (<columns>) VALUES (?, …, ?), …, (?, …, ?)` statement covering all rows and executes it in one database round-trip. It then extracts the primary keys from the input entities via `getId` and returns them in input order.

```scala
class Repo[E, ID] {
  def insertAll(rows: Seq[E])(using con: DbCon): Seq[ID]
}
```

Because the generated SQL grows with the number of rows, `insertAll` is best suited for moderate-sized batches where the caller controls the primary keys:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val repo = Repo.derived[User, Int]("users", "id", _.id)
given DbCon = ???

val newUsers = Seq(
  User(10, "Dave", "dave@example.com"),
  User(11, "Eve",  "eve@example.com")
)
// Returns Seq(10, 11) — the IDs extracted from the input entities via getId
val ids: Seq[Int] = repo.insertAll(newUsers)
```

:::caution
`insertAll` throws `IllegalArgumentException` for an empty `Seq`. Always verify the input is non-empty before calling it, or use `insertBatch`, which accepts empty collections and returns 0 without error.
:::

#### `update` — Update an entity's non-ID columns

`Repo#update` executes `UPDATE <table> SET <col1> = ?, …, <colN> = ? WHERE <idColumn> = ?` for all non-ID columns of the entity, identifying the target row by its primary key. It returns the number of affected rows — 0 when no row with that ID exists.

```scala
class Repo[E, ID] {
  def update(entity: E)(using con: DbCon): Int
}
```

Only non-ID columns appear in the `SET` clause; the ID column appears only in the `WHERE` clause, so it is never overwritten:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val repo = Repo.derived[User, Int]("users", "id", _.id)
given DbCon = ???

val rowsAffected: Int = repo.update(User(1, "Alice Smith", "alice.smith@example.com"))
```

:::note
`update` returns 0 when the entity type has only an ID column and no other updatable fields. In that case the generated `SET` clause would be empty, and `Repo` skips the statement entirely.
:::

#### `delete` — Delete by primary key

`Repo#delete` executes `DELETE FROM <table> WHERE <idColumn> = ?`, binding the ID through `idCodec`. It returns the number of deleted rows — 0 if no row with the given ID exists.

```scala
class Repo[E, ID] {
  def delete(id: ID)(using con: DbCon): Int
}
```

The ID value is bound as a parameterized `?`, not interpolated into the SQL string:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val repo = Repo.derived[User, Int]("users", "id", _.id)
given DbCon = ???

val rowsAffected: Int = repo.delete(42)
```

To delete by an entity value rather than a bare ID, extract the key with `getId` first: `repo.delete(repo.getId(user))`.

#### `deleteAll` — Delete rows by a set of primary keys

`Repo#deleteAll` executes `DELETE FROM <table> WHERE <idColumn> IN (...)` for the given IDs in a single round-trip and returns the total number of deleted rows. It returns `0` immediately, without executing any SQL, when `ids` is empty.

```scala
class Repo[E, ID] {
  def deleteAll(ids: Iterable[ID])(using con: DbCon): Int
}
```

Use it to batch-delete a known set of rows instead of calling `delete` in a loop:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val repo = Repo.derived[User, Int]("users", "id", _.id)
given DbCon = ???

val rowsAffected: Int = repo.deleteAll(List(1, 2, 3))
```

#### `clear` — Remove all rows

`Repo#clear` executes `DELETE FROM <table>` without a `WHERE` clause and returns the number of deleted rows.

```scala
class Repo[E, ID] {
  def clear()(using con: DbCon): Int
}
```

Despite the different name, this method issues a `DELETE FROM` statement rather than a SQL `TRUNCATE`, so the operation participates in transactions and the returned row count is exact:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val repo = Repo.derived[User, Int]("users", "id", _.id)
given DbCon = ???

val rowsDeleted: Int = repo.clear()
```

:::note
`DELETE FROM` is transactional and precise but may be slower than `TRUNCATE` for very large tables on databases that support the `TRUNCATE` statement. When performance for bulk deletes is critical, issue a `TRUNCATE` via a custom `Frag` query instead.
:::
