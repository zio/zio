# DbResultReader

> Reference for DbResultReader, the interface for reading typed column values from a SQL result set.

`DbResultReader` is a trait for reading typed column values from a database result set. It provides methods to access columns by either their 1-based index (following JDBC convention) or by column label.

Application code does not use `DbResultReader` directly — the framework creates it automatically when a `Frag` is executed. It is the read-side counterpart to `DbParamWriter`, which writes values to prepared statements.

## Core API

The core API reads numeric, text, binary, boolean, temporal, and UUID values, plus column metadata and NULL status. `getArray` also exists but defaults to throwing `UnsupportedOperationException` in the shared trait; only backends that override it (the JVM implementation does) actually support SQL array access:

```scala
trait DbResultReader {
  // Numeric types
  def getInt(index: Int): Int
  def getInt(label: String): Int
  def getLong(index: Int): Long
  def getLong(label: String): Long
  def getDouble(index: Int): Double
  def getDouble(label: String): Double
  def getFloat(index: Int): Float
  def getFloat(label: String): Float
  def getShort(index: Int): Short
  def getShort(label: String): Short
  def getByte(index: Int): Byte
  def getByte(label: String): Byte
  def getBigDecimal(index: Int): java.math.BigDecimal
  def getBigDecimal(label: String): java.math.BigDecimal

  // Text and binary
  def getString(index: Int): String
  def getString(label: String): String
  def getBytes(index: Int): Array[Byte]
  def getBytes(label: String): Array[Byte]

  // Boolean
  def getBoolean(index: Int): Boolean
  def getBoolean(label: String): Boolean

  // Date and time
  def getLocalDate(index: Int): java.time.LocalDate
  def getLocalDate(label: String): java.time.LocalDate
  def getLocalDateTime(index: Int): java.time.LocalDateTime
  def getLocalDateTime(label: String): java.time.LocalDateTime
  def getLocalTime(index: Int): java.time.LocalTime
  def getLocalTime(label: String): java.time.LocalTime
  def getInstant(index: Int): java.time.Instant
  def getInstant(label: String): java.time.Instant
  def getDuration(index: Int): java.time.Duration
  def getDuration(label: String): java.time.Duration

  // Other types
  def getUUID(index: Int): java.util.UUID
  def getUUID(label: String): java.util.UUID
  def getArray(index: Int): java.sql.Array   // default: throws UnsupportedOperationException
  def getArray(label: String): java.sql.Array // default: throws UnsupportedOperationException

  // Metadata and NULL detection
  def columnLabel(index: Int): String
  def hasColumn(label: String): Boolean
  def wasNull: Boolean
}
```

## Usage

You access `DbResultReader` through `DbResultSet.reader` after calling `executeQuery` on a prepared statement:

```scala
import zio.blocks.sql._

val transactor: Transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

transactor.connect {
  val con = summon[DbCon].connection
  val stmt = con.prepareStatement("SELECT id, name, age FROM users")
  val rs = stmt.executeQuery()
  
  if (rs.next()) {
    // Read by column label
    val id: Long    = rs.reader.getLong("id")
    val name: String = rs.reader.getString("name")
    val age: Int    = rs.reader.getInt("age")
  }
  
  rs.close()
  stmt.close()
}
```

## NULL Handling

Numeric and boolean getters return zero-like defaults (`0`, `0L`, `false`) for SQL `NULL`. String, decimal, and temporal getters return `null`. After any `get*` call, check `wasNull` to detect `NULL`:

```scala
import zio.blocks.sql._

val transactor: Transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

transactor.connect {
  val con = summon[DbCon].connection
  val stmt = con.prepareStatement("SELECT bio FROM users LIMIT 1")
  val rs = stmt.executeQuery()
  
  if (rs.next()) {
    val bio: String = rs.reader.getString("bio")
    val bioOption: Option[String] = if (rs.reader.wasNull) None else Some(bio)
  }
  
  rs.close()
  stmt.close()
}
```

:::caution
Always call `wasNull` immediately after the `get*` call whose NULL status you want to check. Do not call another `get*` method in between, or `wasNull` will reflect the wrong result.
:::

## How It Works

When you use high-level operations like `Frag.query` or `Repo.find`, `DbCodec` internally calls `DbResultReader` methods to decode result rows. You only see this trait directly when dropping down to raw prepared statements for advanced use cases.

Label-based access (preferred) lets you ignore column order:

```scala
import zio.blocks.sql._

val reader: DbResultReader = ???
val name = reader.getString("name")  // Works regardless of SELECT order
```

Index-based access (1-based per JDBC) is faster but requires stable column order:

```scala
import zio.blocks.sql._

val reader: DbResultReader = ???
val name = reader.getString(2)  // Assumes name is the 2nd column
```
