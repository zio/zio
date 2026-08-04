# Ddl

> Reference for Ddl, the helper singleton that generates CREATE TABLE and DROP TABLE SQL fragments.

`Ddl` is a helper object that generates DDL (Data Definition Language) SQL fragments for creating and dropping tables. `ColumnDef` is a simple data class pairing a column name with a SQL type string and a nullability flag.

Normally you don't call `Ddl` directly — `Table#createTable` and `Table#dropTable` use it internally. You may need `Ddl` directly when building custom DDL tooling.

## Core API

```scala
object Ddl {
  def createTable(tableName: String, columns: IndexedSeq[ColumnDef]): Frag
  def dropTable(tableName: String): Frag
}

final case class ColumnDef(name: String, sqlType: String, nullable: Boolean)
```

## Usage

Create a `ColumnDef` for each column, then pass them to `Ddl.createTable`:

```scala
import zio.blocks.sql._

val transactor: Transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
// transactor: Transactor = zio.blocks.sql.JdbcTransactor@1bf2e7e

val columns = IndexedSeq(
  ColumnDef("id",         "INTEGER", nullable = false),
  ColumnDef("name",       "TEXT",    nullable = false),
  ColumnDef("created_at", "TEXT",    nullable = true)
)
// columns: IndexedSeq[ColumnDef] = Vector(
//   ColumnDef(name = "id", sqlType = "INTEGER", nullable = false),
//   ColumnDef(name = "name", sqlType = "TEXT", nullable = false),
//   ColumnDef(name = "created_at", sqlType = "TEXT", nullable = true)
// )

val createFrag = Ddl.createTable("users", columns)
// createFrag: Frag = Frag(
//   parts = Vector(
//     """CREATE TABLE IF NOT EXISTS users (
//   id INTEGER NOT NULL,
//   name TEXT NOT NULL,
//   created_at TEXT
// )"""
//   ),
//   params = Vector()
// )
val dropFrag   = Ddl.dropTable("users")
// dropFrag: Frag = Frag(
//   parts = Vector("DROP TABLE IF EXISTS users"),
//   params = Vector()
// )

// Execute the fragments
transactor.transact {
  createFrag.update
  // ... do work ...
  dropFrag.update
}
// res1: Int = 0
```

## How It Works

`Ddl` is typically used by `Table#createTable`, which derives `ColumnDef` from schema metadata:

1. `Table.derived[A]` builds a schema.
2. `Table#createTable(dialect)` converts schema columns to `ColumnDef` using `dialect.typeName`.
3. `Ddl.createTable` receives the `ColumnDef` list and generates the SQL fragment.

You call `Ddl` directly only when you need custom DDL that doesn't fit the `Table` abstraction.

See [Table](./table.md) for the high-level DDL API.
