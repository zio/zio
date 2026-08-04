# Frag

> Reference for Frag, the SQL fragment type with safe parameterization and JDBC execution.

`Frag` is an immutable SQL fragment — a piece of SQL text with typed parameter values kept safely separate from the literal SQL. The `sql"..."` string interpolator builds fragments by checking at compile time that every interpolated expression can be bound as a parameter. Fragments compose with `++` and execute through methods like `query`, `update`, and `queryOne`.

`Frag` is safe from SQL injection because parameter values never appear in the SQL string — they are stored separately and bound to `?` placeholders at execution time.

## Core API

```scala
final case class Frag(parts: IndexedSeq[String], params: IndexedSeq[DbValue]) {
  def ++(other: Frag): Frag
  def sql(dialect: SqlDialect): String
  def queryParams: IndexedSeq[DbValue]
  def isEmpty: Boolean
}

object Frag {
  val empty: Frag
  def literal(sqlStr: String): Frag
  def sequence(frags: Frag*): Frag
  def values[A](rows: Seq[A])(using codec: DbCodec[A]): Frag

  extension (frag: Frag) {
    def query[A](using DbCon, DbCodec[A]): List[A]
    def queryOne[A](using DbCon, DbCodec[A]): Maybe[A]
    def queryLimit[A](limit: Int)(using DbCon, DbCodec[A]): List[A]
    def update(using DbCon): Int
    def updateReturningKeys[A](using DbCon, DbCodec[A]): List[A]
  }
}
```

## Usage

Build fragments with the `sql"..."` interpolator and execute them inside a `Transactor` block:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema
import zio.blocks.maybe.Maybe

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val tx: Transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

val userId  = 42
val active  = true

tx.connect {
  // Query — returns all matching rows
  val users: List[User] = 
    sql"SELECT id, name, email FROM users WHERE id > $userId AND active = $active".query[User]
  
  // QueryOne — returns at most one row
  val one: Maybe[User] = 
    sql"SELECT id, name, email FROM users WHERE id = ${1}".queryOne[User]
  
  // Update — returns affected row count
  val deleted: Int = 
    sql"DELETE FROM users WHERE active = ${false}".update
  
  // UpdateReturningKeys — returns generated keys after INSERT
  val keys: List[Long] = 
    sql"INSERT INTO users (name, email) VALUES (${"Alice"}, ${"alice@example.com"})".updateReturningKeys[Long]
}
```

## Building Fragments

**`sql"..."` interpolator** — The primary way to build fragments. Compile-time checked: every interpolated value must have a `DbParam` instance (provided for all common Scala and Java types).

```scala
import zio.blocks.sql._

val userId = 42
// userId: Int = 42
val frag = sql"SELECT * FROM users WHERE id = $userId"
// frag: Frag = Frag(
//   parts = ArraySeq("SELECT * FROM users WHERE id = ", ""),
//   params = Vector(DbInt(42))
// )
frag.params
// res2: IndexedSeq[DbValue] = Vector(DbInt(42))
```

**`Frag.literal`** — Wrap static SQL text (no parameters):

```scala
val query = sql"SELECT * FROM users" ++ Frag.literal(" ORDER BY name")
// query: Frag = Frag(
//   parts = ArraySeq("SELECT * FROM users ORDER BY name"),
//   params = Vector()
// )
query.sql(SqlDialect.SQLite)
// res3: String = "SELECT * FROM users ORDER BY name"
```

**`Frag.values`** — Build a multi-row INSERT VALUES clause:

```scala
import zio.blocks.sql._

case class Product(name: String, price: BigDecimal) derives DbCodec

val products = List(Product("Widget", BigDecimal("9.99")), Product("Gadget", BigDecimal("24.99")))
// products: List[Product] = List(
//   Product(name = "Widget", price = 9.99),
//   Product(name = "Gadget", price = 24.99)
// )
val insert = Frag.literal("INSERT INTO product (name, price) VALUES ") ++ Frag.values(products)
// insert: Frag = Frag(
//   parts = Vector(
//     "INSERT INTO product (name, price) VALUES (",
//     ", ",
//     "), (",
//     ", ",
//     ")"
//   ),
//   params = Vector(
//     DbString("Widget"),
//     DbBigDecimal(9.99),
//     DbString("Gadget"),
//     DbBigDecimal(24.99)
//   )
// )
insert.sql(SqlDialect.SQLite)
// res5: String = "INSERT INTO product (name, price) VALUES (?, ?), (?, ?)"
```

**`Frag.empty`** — The identity fragment for composition. Useful for optional clauses:

```scala
import zio.blocks.sql._

val hasFilter = true
// hasFilter: Boolean = true
val where = if (hasFilter) sql" WHERE active = ${true}" else Frag.empty
// where: Frag = Frag(
//   parts = ArraySeq(" WHERE active = ", ""),
//   params = Vector(DbBoolean(true))
// )
val query = sql"SELECT * FROM users" ++ where
// query: Frag = Frag(
//   parts = ArraySeq("SELECT * FROM users WHERE active = ", ""),
//   params = Vector(DbBoolean(true))
// )
query.sql(SqlDialect.SQLite)
// res7: String = "SELECT * FROM users WHERE active = ?"
```

**`Frag.sequence`** — Concatenate multiple fragments with no separator:

```scala
import zio.blocks.sql._

val status = "active"
// status: String = "active"
val base   = sql"SELECT * FROM users"
// base: Frag = Frag(
//   parts = ArraySeq("SELECT * FROM users"),
//   params = Vector()
// )
val where  = sql" WHERE status = $status"
// where: Frag = Frag(
//   parts = ArraySeq(" WHERE status = ", ""),
//   params = Vector(DbString("active"))
// )
val order  = Frag.literal(" ORDER BY name")
// order: Frag = Frag(parts = Vector(" ORDER BY name"), params = Vector())
val full   = Frag.sequence(base, where, order)
// full: Frag = Frag(
//   parts = Vector("SELECT * FROM users WHERE status = ", " ORDER BY name"),
//   params = Vector(DbString("active"))
// )
full.sql(SqlDialect.SQLite)
// res9: String = "SELECT * FROM users WHERE status = ? ORDER BY name"
```

## Execution Methods

All execution methods require an implicit `DbCon` (provided by `Transactor#connect` or `Transactor#transact`). They render the fragment to SQL, bind parameters, execute, and log the operation.

**`query[A]`** — Execute SELECT and return all rows:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String)
object User { implicit val schema: Schema[User] = Schema.derived }

given DbCon = ???

val users: List[User] = sql"SELECT id, name FROM users".query[User]
```

**`queryOne[A]`** — Execute SELECT and return at most one row:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema
import zio.blocks.maybe.Maybe

case class User(id: Int, name: String)
object User { implicit val schema: Schema[User] = Schema.derived }

given DbCon = ???

val user: Maybe[User] = sql"SELECT id, name FROM users WHERE id = ${1}".queryOne[User]
```

**`queryLimit[A](n)`** — Execute SELECT and return up to n rows (fetched from Scala side):

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String)
object User { implicit val schema: Schema[User] = Schema.derived }

given DbCon = ???

val page: List[User] = sql"SELECT id, name FROM users ORDER BY name".queryLimit[User](10)
```

**`update`** — Execute INSERT, UPDATE, or DELETE and return affected row count:

```scala
import zio.blocks.sql._

given DbCon = ???

val deleted: Int = sql"DELETE FROM users WHERE inactive = ${true}".update
```

**`updateReturningKeys[A]`** — Execute INSERT and return auto-generated primary key(s):

```scala
import zio.blocks.sql._

given DbCon = ???

val keys: List[Long] = sql"INSERT INTO users (name) VALUES (${"Alice"})".updateReturningKeys[Long]
```
