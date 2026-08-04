# TableMetadata

> Reference for TableMetadata, the utility for deriving column metadata from a Schema.

`TableMetadata` derives column metadata from a `Schema`. It returns a list of `ColumnMeta` instances, each describing a column's name, type, and nullability. `TableNamingPolicy` controls how Scala type names become table names.

## Core API

```scala
object TableMetadata {
  def columnsFor[A](
    schema: Schema[A],
    columnNameMapper: SqlNameMapper = SqlNameMapper.SnakeCase
  ): IndexedSeq[ColumnMeta]
}

final case class ColumnMeta(name: String, dbValue: DbValue, nullable: Boolean)

sealed trait TableNamingPolicy {
  def defaultName(typeName: String): String
}

object TableNamingPolicy {
  case object Singular                         extends TableNamingPolicy
  case object Plural                           extends TableNamingPolicy
  final case class Custom(f: String => String) extends TableNamingPolicy
}
```

## Usage

Derive column metadata from a schema:

```scala
import zio.blocks.sql.{TableMetadata, SqlDialect}
import zio.blocks.schema.Schema

case class Product(id: Int, name: String, price: Option[BigDecimal])
object Product { given schema: Schema[Product] = Schema.derived }

val cols = TableMetadata.columnsFor(Product.schema)
// cols: IndexedSeq[ColumnMeta] = Vector(
//   ColumnMeta(name = "id", dbValue = DbInt(0), nullable = false),
//   ColumnMeta(name = "name", dbValue = DbString(""), nullable = false),
//   ColumnMeta(name = "price", dbValue = DbBigDecimal(0), nullable = true)
// )
cols
// res1: IndexedSeq[ColumnMeta] = Vector(
//   ColumnMeta(name = "id", dbValue = DbInt(0), nullable = false),
//   ColumnMeta(name = "name", dbValue = DbString(""), nullable = false),
//   ColumnMeta(name = "price", dbValue = DbBigDecimal(0), nullable = true)
// )

cols.map(_.name)
// res2: IndexedSeq[String] = Vector("id", "name", "price")
cols.map(_.nullable)
// res3: IndexedSeq[Boolean] = Vector(false, false, true)
```

Use the column metadata to get DDL types:

```scala
cols.map(col => SqlDialect.PostgreSQL.typeName(col.dbValue))
// res4: IndexedSeq[String] = Vector("INTEGER", "TEXT", "NUMERIC")
```

Control table naming when deriving a Table:

```scala
import zio.blocks.sql.{Table, TableNamingPolicy}

// Singular table name (default)
val table1 = Table.derived[Product]
// table1: Table[Product] = Table(
//   name = "product",
//   codec = zio.blocks.sql.DbCodecDeriver$$anon$20@40f5588b,
//   columnsMeta = Vector(
//     ColumnMeta(name = "id", dbValue = DbInt(0), nullable = false),
//     ColumnMeta(name = "name", dbValue = DbString(""), nullable = false),
//     ColumnMeta(name = "price", dbValue = DbBigDecimal(0), nullable = true)
//   )
// )
table1.name
// res5: String = "product"

// Plural table name
val table2 = Table.derived[Product](TableNamingPolicy.Plural)
// table2: Table[Product] = Table(
//   name = "products",
//   codec = zio.blocks.sql.DbCodecDeriver$$anon$20@13590a4,
//   columnsMeta = Vector(
//     ColumnMeta(name = "id", dbValue = DbInt(0), nullable = false),
//     ColumnMeta(name = "name", dbValue = DbString(""), nullable = false),
//     ColumnMeta(name = "price", dbValue = DbBigDecimal(0), nullable = true)
//   )
// )
table2.name
// res6: String = "products"

// Custom naming
val table3 = Table.derived[Product](TableNamingPolicy.Custom("t_" + _))
// table3: Table[Product] = Table(
//   name = "t_Product",
//   codec = zio.blocks.sql.DbCodecDeriver$$anon$20@2fa45824,
//   columnsMeta = Vector(
//     ColumnMeta(name = "id", dbValue = DbInt(0), nullable = false),
//     ColumnMeta(name = "name", dbValue = DbString(""), nullable = false),
//     ColumnMeta(name = "price", dbValue = DbBigDecimal(0), nullable = true)
//   )
// )
table3.name
// res7: String = "t_Product"
```

## Key Points

**`columnsFor`** — Walks a schema's structure and returns metadata for each column, respecting `@Modifier.transient` (skip field), `@Modifier.rename` (override name), and `Option[A]` / `Maybe[A]` (mark nullable).

**ColumnMeta** — Holds the column name, a representative `DbValue` for type inference, and a nullable flag. The actual value in `dbValue` doesn't matter—only its variant is used by `SqlDialect#typeName`.

**TableNamingPolicy** — Controls default table naming: `Singular` converts `"UserAccount"` to `"user_account"`, `Plural` to `"user_accounts"`, `Custom(f)` applies function `f` directly.

## How It Works

`Table.derived` calls `columnsFor` to extract column metadata from the schema. For each `ColumnMeta`, the dialect's `typeName` method is called to get the SQL type string. The metadata tracks which columns are nullable based on `Option[A]` or `Maybe[A]` types. Fields annotated with `@Modifier.rename` use their explicit name instead of the mapper.

For how Table uses this metadata, see [Table](./table.md). For DDL generation, see [Ddl](./ddl.md).
