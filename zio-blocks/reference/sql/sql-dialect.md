# SqlDialect

> Reference for SqlDialect, the interface for database-specific SQL type names and parameter placeholders.

`SqlDialect` tells the framework how to render SQL for a specific database. Two dialects are built in: `PostgreSQL` and `SQLite`.

## Core API

```scala
sealed trait SqlDialect {
  def name: String
  def typeName(dbValue: DbValue): String
  def paramPlaceholder(index: Int): String
}

object SqlDialect {
  case object PostgreSQL extends SqlDialect
  case object SQLite     extends SqlDialect
}
```

## Usage

You can specify the dialect when creating your `Transactor`:

```scala
import zio.blocks.sql._

val txPostgres = JdbcTransactor.fromUrl("jdbc:postgresql://...", SqlDialect.PostgreSQL)
val txSqlite   = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
```

The dialect is available through `DbCon#dialect` inside a transaction:

```scala
import zio.blocks.sql._

val tx = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
// tx: JdbcTransactor = zio.blocks.sql.JdbcTransactor@f150688

tx.connect {
  val dialect = summon[DbCon].dialect

  (
    dialect.typeName(DbValue.DbInt(0)),
    dialect.typeName(DbValue.DbUUID(new java.util.UUID(0L, 0L))),
    dialect.paramPlaceholder(1)
  )
}
// res2: Tuple3[String, String, String] = ("INTEGER", "TEXT", "?")
```

## Type Mapping

PostgreSQL and SQLite map Scala types to SQL differently:

| Type       | PostgreSQL    | SQLite    |
|------------|---------------|-----------|
| Long       | `BIGINT`      | `INTEGER` |
| Boolean    | `BOOLEAN`     | `INTEGER` |
| Instant    | `TIMESTAMPTZ` | `TEXT`    |
| LocalDate  | `DATE`        | `TEXT`    |
| UUID       | `UUID`        | `TEXT`    |
| BigDecimal | `NUMERIC`     | `TEXT`    |
| Bytes      | `BYTEA`       | `BLOB`    |

PostgreSQL uses native types; SQLite uses `INTEGER` for all integers and `TEXT` for temporal/UUID types.
