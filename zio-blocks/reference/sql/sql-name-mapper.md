# SqlNameMapper

> Reference for SqlNameMapper, the interface for mapping Scala field names to SQL column names.

`SqlNameMapper` converts Scala field names to SQL column names. Three implementations are built in: `SnakeCase` (default, converts `firstName` to `first_name`), `Identity` (no change), and `Custom` (arbitrary function).

## Core API

The simplified structural shape of `SqlNameMapper` is:

```scala
sealed trait SqlNameMapper extends (String => String)

object SqlNameMapper {
  case object SnakeCase                        extends SqlNameMapper
  case object Identity                         extends SqlNameMapper
  final case class Custom(f: String => String) extends SqlNameMapper
}
```

## Usage

Call a mapper like a function:

```scala
import zio.blocks.sql.SqlNameMapper

SqlNameMapper.SnakeCase("firstName")
// res1: String = "first_name"
SqlNameMapper.SnakeCase("userID")
// res2: String = "user_id"
SqlNameMapper.Identity("firstName")
// res3: String = "firstName"

val upper = SqlNameMapper.Custom(_.toUpperCase)
// upper: Custom = Custom(
//   repl.MdocSession$MdocApp0$$Lambda$20339/0x00007f2996f5b000@1850c853
// )
upper("firstName")
// res4: String = "FIRSTNAME"
```

Use a custom mapper when deriving codecs:

```scala
import zio.blocks.sql.{DbCodec, DbCodecDeriver, SqlNameMapper}
import zio.blocks.schema.Schema

case class Order(orderId: Int, totalAmount: BigDecimal)
object Order { given schema: Schema[Order] = Schema.derived }

// Use UPPERCASE column names instead of snake_case
val upperDeriver = DbCodecDeriver.withColumnNameMapper(SqlNameMapper.Custom(_.toUpperCase))
// upperDeriver: DbCodecDeriver = zio.blocks.sql.DbCodecDeriver@646cca72
val codec: DbCodec[Order] = Order.schema.deriving(upperDeriver).derive
// codec: DbCodec[Order] = zio.blocks.sql.DbCodecDeriver$$anon$20@596dacd
codec.columns
// res6: IndexedSeq[String] = Vector("ORDERID", "TOTALAMOUNT")
```

## Key Points

For full control over individual field names, use `@Modifier.rename("column_name")` on specific fields without changing the global mapper. It takes precedence over any `SqlNameMapper` in use.
