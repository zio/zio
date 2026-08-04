# DbCodecDeriver

> Reference for DbCodecDeriver, the schema-driven derivation engine that converts a Schema[A] into a DbCodec[A] in the sql module.

`DbCodecDeriver` is a schema-driven derivation engine that converts a `Schema[A]` into a `DbCodec[A]`. 

The companion object `DbCodecDeriver` is itself an instance of the class constructed with the default `SqlNameMapper.SnakeCase`. `DbCodec.derived`, `DbCodec.builder`, `DbCodec.derivedWith`, and `Table.derived` all delegate to this deriver; application code rarely constructs or calls it directly.

Key properties:
- **Schema-driven** — derivation is entirely data-driven from the `Schema`'s `Reflect` tree; no separate macro or annotation processor is involved beyond `Schema.derived`.
- **Configurable naming** — the `columnNameMapper: SqlNameMapper` constructor parameter controls how Scala field names become SQL column names; the default is `SnakeCase`.
- **Annotation-aware** — `@Modifier.transient` fields are skipped; `@Modifier.rename` overrides the mapper for individual fields; `@Modifier.config("sql.inline","true")` flattens a nested record into the parent's column list.
- **JSONB fallback** — non-inline nested records, sequences, maps, and dynamic values fall back to a single `TEXT` / `JSONB` column backed by `DbCodec.jsonb`.

The structural shape of `DbCodecDeriver` is:

```scala
class DbCodecDeriver(columnNameMapper: SqlNameMapper = SqlNameMapper.SnakeCase)
    extends Deriver[DbCodec]

object DbCodecDeriver extends DbCodecDeriver(SqlNameMapper.SnakeCase) {
  def withColumnNameMapper(mapper: SqlNameMapper): DbCodecDeriver
}
```

## Usage

The following example shows the three most common ways to reach `DbCodecDeriver`: through the `derives` clause on a case class, through `DbCodec.derived` (equivalent), and through `DbCodecDeriver.withColumnNameMapper` when a non-default naming strategy is needed.

```scala
import zio.blocks.sql.{DbCodec, DbCodecDeriver, SqlNameMapper}
import zio.blocks.schema.Schema

// 1. Derives clause — most concise
case class User(userId: Int, fullName: String) derives DbCodec
DbCodec[User].columns
// res0: IndexedSeq[String] = Vector("user_id", "full_name")

// 2. DbCodec.derived — equivalent, explicit
case class Event(eventId: Long, eventType: String)
object Event { implicit val schema: Schema[Event] = Schema.derived }
val eventCodec = DbCodec.derived[Event]
// eventCodec: DbCodec[Event] = zio.blocks.sql.DbCodecDeriver$$anon$20@66f22c33
eventCodec.columns
// res1: IndexedSeq[String] = Vector("event_id", "event_type")

// 3. withColumnNameMapper — use Identity when your DB already uses camelCase
case class Widget(widgetId: Int, widgetName: String)
object Widget { implicit val schema: Schema[Widget] = Schema.derived }

val identityDeriver = DbCodecDeriver.withColumnNameMapper(SqlNameMapper.Identity)
// identityDeriver: DbCodecDeriver = zio.blocks.sql.DbCodecDeriver@33803e98
val widgetCodec     = Widget.schema.deriving(identityDeriver).derive
// widgetCodec: DbCodec[Widget] = zio.blocks.sql.DbCodecDeriver$$anon$20@35131fba
widgetCodec.columns
// res2: IndexedSeq[String] = Vector("widgetId", "widgetName")
```

## See Also

For the naming strategy that `DbCodecDeriver` applies, see [SqlNameMapper](./sql-name-mapper.md). For the `DbCodec` type that derivation produces, see [DbCodec](./db-codec.md). For `Table.derived` and DDL generation, see [Table](./table.md).
