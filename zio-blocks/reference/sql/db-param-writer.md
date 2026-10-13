# DbParamWriter

> Reference for DbParamWriter, the interface for binding typed Scala values to SQL prepared-statement parameters.

`DbParamWriter` is a trait for binding typed Scala values to the `?` placeholders in a SQL prepared statement. It provides methods to set parameters by their 1-based index (following JDBC convention).

Application code does not use `DbParamWriter` directly — the framework calls it internally when executing queries through `Frag` or `Repo`. It is the write-side counterpart to `DbResultReader`, which reads values from result sets.

## Core API

```scala
trait DbParamWriter {
  // Numeric types
  def setInt(index: Int, value: Int): Unit
  def setLong(index: Int, value: Long): Unit
  def setDouble(index: Int, value: Double): Unit
  def setFloat(index: Int, value: Float): Unit
  def setShort(index: Int, value: Short): Unit
  def setByte(index: Int, value: Byte): Unit
  def setBigDecimal(index: Int, value: java.math.BigDecimal): Unit

  // Text and binary
  def setString(index: Int, value: String): Unit
  def setBytes(index: Int, value: Array[Byte]): Unit

  // Date and time
  def setLocalDate(index: Int, value: java.time.LocalDate): Unit
  def setLocalDateTime(index: Int, value: java.time.LocalDateTime): Unit
  def setLocalTime(index: Int, value: java.time.LocalTime): Unit
  def setInstant(index: Int, value: java.time.Instant): Unit
  def setDuration(index: Int, value: java.time.Duration): Unit

  // Other types
  def setBoolean(index: Int, value: Boolean): Unit
  def setUUID(index: Int, value: java.util.UUID): Unit
  def setArray(index: Int, elementType: String, elements: IndexedSeq[Any]): Unit

  // Null handling
  def setNull(index: Int, sqlType: Int): Unit
}
```

## Usage

You access `DbParamWriter` through `DbPreparedStatement.paramWriter` when using raw prepared statements in a `connect` block:

```scala
import zio.blocks.sql._

val transactor: Transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

transactor.connect {
  val con  = summon[DbCon].connection
  val stmt = con.prepareStatement("INSERT INTO users (name, age) VALUES (?, ?)")
  
  stmt.paramWriter.setString(1, "Alice")  // First ?
  stmt.paramWriter.setInt(2, 30)          // Second ?
  
  stmt.executeUpdate()
  stmt.close()
}
```

Note that indices are 1-based, not 0-based.

## How It Works

When you use higher-level operations like `Frag.query` or `Repo.insert`, `DbCodec` internally calls `DbParamWriter` methods to bind your Scala values to SQL parameters. You see this trait only when dropping down to raw prepared statements for advanced use cases like stored procedures or batch operations.

See [DbConnection](./db-connection.md) for examples of raw prepared-statement usage, and [DbCodec](./db-codec.md) for how parameter binding integrates with the codec system.
