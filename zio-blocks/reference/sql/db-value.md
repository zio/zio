# DbValue

> Reference for DbValue, the sealed ADT of typed SQL column values shared by Frag, DbCodec, and SqlDialect in the sql module.

`DbValue` is a sealed ADT representing every typed SQL column value the `sql` module handles, one `case class` per Scala type. `Frag` stores its bound parameters as `IndexedSeq[DbValue]`, `DbCodec#toDbValues` produces them, and `SqlDialect#typeName` maps a `DbValue` to its DDL type string — it is the common typed currency all three share, with no JDBC imports or dependencies of its own.

| Variant           | Scala field type          | PostgreSQL DDL     | SQLite DDL |
|-------------------|----------------------------|---------------------|------------|
| `DbNull`          | *(none)*                   | `NULL`               | `NULL`     |
| `DbInt`           | `Int`                       | `INTEGER`             | `INTEGER`  |
| `DbLong`          | `Long`                      | `BIGINT`              | `INTEGER`  |
| `DbDouble`        | `Double`                    | `DOUBLE PRECISION`    | `REAL`     |
| `DbFloat`         | `Float`                     | `REAL`                | `REAL`     |
| `DbBoolean`       | `Boolean`                   | `BOOLEAN`             | `INTEGER`  |
| `DbString`        | `String`                    | `TEXT`                | `TEXT`     |
| `DbBigDecimal`    | `scala.BigDecimal`          | `NUMERIC`             | `TEXT`     |
| `DbBytes`         | `Array[Byte]`               | `BYTEA`               | `BLOB`     |
| `DbShort`         | `Short`                     | `SMALLINT`            | `INTEGER`  |
| `DbByte`          | `Byte`                      | `SMALLINT`            | `INTEGER`  |
| `DbChar`          | `Char`                      | `CHAR(1)`             | `TEXT`     |
| `DbLocalDate`     | `java.time.LocalDate`       | `DATE`                | `TEXT`     |
| `DbLocalDateTime` | `java.time.LocalDateTime`   | `TIMESTAMP`           | `TEXT`     |
| `DbLocalTime`     | `java.time.LocalTime`       | `TIME`                | `TEXT`     |
| `DbInstant`       | `java.time.Instant`         | `TIMESTAMPTZ`         | `TEXT`     |
| `DbDuration`      | `java.time.Duration`        | `INTERVAL`            | `TEXT`     |
| `DbUUID`          | `java.util.UUID`            | `UUID`                | `TEXT`     |
| `DbArray`         | `(String, IndexedSeq[Any])` | *(not in typeName)*   | *(not in typeName)* |

`DbNull` is what `DbParam[Option[A]]` produces for `None` and `DbParam[Maybe[A]]` produces for `Maybe.absent`; `DbCodec` matches on it to decode nullable columns, throwing `IllegalStateException` if a non-optional codec hits it. `DbArray` is the one variant with two fields (`elementType`, `elements`) instead of `value`, used for collection-typed columns and not currently covered by `SqlDialect#typeName`.

See [Frag](./frag.md), [DbCodec](./db-codec.md), and the [SQL module index](./index.md).
