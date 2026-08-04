# DbConnection

> The JDBC Connection abstraction in the sql module

`DbConnection` is a `trait` that extends `AutoCloseable` and abstracts over a JDBC `java.sql.Connection`. It exposes the minimum surface needed to prepare statements, control transaction boundaries, and query connection state. 

It is part of a three-tier abstraction:
- `DbConnection` manages the connection itself
- `DbPreparedStatement` executes statements with parameters
- `DbResultSet` reads result rows

## Core API

Here is the core API which `DbConnection` exposes:

```scala
trait DbConnection extends AutoCloseable {
  // Statement preparation
  def prepareStatement(sql: String): DbPreparedStatement
  def prepareStatementReturningKeys(sql: String): DbPreparedStatement

  // Transaction control
  def setAutoCommit(autoCommit: Boolean): Unit
  def getAutoCommit: Boolean
  def commit(): Unit
  def rollback(): Unit

  // Connection state
  def isClosed: Boolean
  def close(): Unit
}
```

## Creating Instances

`DbConnection` has no public constructor. Application code never instantiates it. `Transactor#connect` and `Transactor#transact` create the concrete `JdbcConnection` internally and make it available through `DbCon#connection` inside the block.

For testing, implement the trait directly with a mock or use a lightweight in-memory JDBC driver such as SQLite's `:memory:` database.

:::caution
Never call `con.close()` inside a `connect` or `transact` block. The `Transactor` closes the connection after the block returns; closing it early will cause the rest of the block to fail with a "connection already closed" JDBC error.
:::

## Statement Preparation

### `prepareStatement` — Prepare a SQL statement for execution

`prepareStatement(sql: String): DbPreparedStatement` asks the JDBC driver to compile and cache the SQL string and returns a `DbPreparedStatement` that can have parameters bound to it before execution. The `sql` string must use `?` placeholders for parameters.

```scala
import zio.blocks.sql._

val transactor: Transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

// Inside a connect block:
transactor.connect {
  val con  = summon[DbCon].connection
  val stmt = con.prepareStatement("INSERT INTO tags (name) VALUES (?)")
  stmt.paramWriter.setString(1, "scala")
  val rowCount = stmt.executeUpdate()
  stmt.close()
  rowCount
}
```

### `prepareStatementReturningKeys` — Prepare an insert that returns generated keys

`prepareStatementReturningKeys(sql: String): DbPreparedStatement` prepares a statement with the JDBC `RETURN_GENERATED_KEYS` hint, enabling `executeUpdateReturningKeys` to return an auto-generated primary key. Use this for `INSERT` statements on tables with a database-generated `SERIAL` or `AUTOINCREMENT` primary key.

```scala
import zio.blocks.sql._

val transactor: Transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

// Inside a connect block:
transactor.connect {
  val con  = summon[DbCon].connection
  val stmt = con.prepareStatementReturningKeys("INSERT INTO orders (amount) VALUES (?)")
  stmt.paramWriter.setBigDecimal(1, new java.math.BigDecimal("99.99"))
  val keyRs  = stmt.executeUpdateReturningKeys()
  val genKey = if (keyRs.next()) keyRs.reader.getLong(1) else -1L
  keyRs.close()
  stmt.close()
  genKey
}
```

## Transaction Control

`setAutoCommit`, `getAutoCommit`, `commit`, and `rollback` manage the connection's transactional state.

`setAutoCommit(autoCommit: Boolean)` switches the connection between auto-commit and manual-commit mode. `getAutoCommit: Boolean` queries the current mode. `commit()` commits the current transaction; `rollback()` rolls it back. These methods wrap the corresponding `java.sql.Connection` calls.

`Transactor#transact` calls `setAutoCommit(false)` before the block and calls `commit()` on success or `rollback()` on exception — so within a `transact` block, you should not call these methods yourself. Use them directly only in a `connect` block where you need fine-grained control over transaction boundaries.

```scala
import zio.blocks.sql._

val transactor: Transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

// Manual transaction management inside a connect block (uncommon)
transactor.connect {
  val con = summon[DbCon].connection
  con.setAutoCommit(false)
  try {
    // ... execute statements ...
    con.commit()
  } catch {
    case t: Throwable =>
      con.rollback()
      throw t
  }
}
```

## Connection State

### `isClosed` — Check whether the connection is still open

`isClosed: Boolean` returns `true` if the connection has already been closed. Inside a `connect` or `transact` block the connection is always open; this method is primarily useful in test code that verifies the `Transactor` closes connections correctly.

```scala
import zio.blocks.sql._

val transactor: Transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
// transactor: Transactor = zio.blocks.sql.JdbcTransactor@7d102738

var capturedCon: DbConnection = null
// capturedCon: DbConnection = null

transactor.connect {
  capturedCon = summon[DbCon].connection
  capturedCon.isClosed // still open inside the block
}
// res4: Boolean = false

capturedCon.isClosed // closed after the block returned
// res5: Boolean = true
```

### `close` — Close the connection

`close(): Unit` closes the connection and releases the underlying JDBC resources. Normally the `Transactor` calls this automatically after the `connect` or `transact` block completes. Calling it manually inside a block will cause subsequent operations to fail with a "connection already closed" error.
