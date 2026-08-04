# SqlLogger

> Reference for SqlLogger, the callback interface for observing SQL execution.

`SqlLogger` is a callback interface that reports SQL execution events. Every statement calls either `onSuccess` or `onError` with details about the execution: rendered SQL, bound parameters, duration, and row count.

Key points:

- **Synchronous** — Callbacks run on the JDBC thread immediately after statement completion. Keep implementations fast; use a background thread for heavy I/O.
- **SqlLogger.noop** — Pre-built no-op instance that discards all events. Default when no custom logger is configured.

## Core API

```scala
trait SqlLogger {
  def onSuccess(event: SqlLogger.SuccessEvent): Unit
  def onError(event: SqlLogger.ErrorEvent): Unit
}
```

## Usage

Implement the trait to observe SQL execution:

```scala
import zio.blocks.sql.SqlLogger

val myLogger: SqlLogger = new SqlLogger {
  def onSuccess(event: SqlLogger.SuccessEvent): Unit =
    println(s"OK (${event.duration.toMillis}ms, ${event.rowCount} rows): ${event.sql}")

  def onError(event: SqlLogger.ErrorEvent): Unit =
    println(s"FAIL: ${event.error.getMessage}")
}
// myLogger: SqlLogger = repl.MdocSession$MdocApp0$$anon$1@1b9203b8
```

Pass the logger when creating a `JdbcTransactor`:

```scala
import zio.blocks.sql._

val dataSource: javax.sql.DataSource = ???
val myLogger: SqlLogger = SqlLogger.noop

val tx = new JdbcTransactor(() => dataSource.getConnection(), SqlDialect.PostgreSQL, myLogger)
```

Use the predefined no-op logger when you don't need logging:

```scala
import zio.blocks.sql._

val dataSource: javax.sql.DataSource = ???
val tx: JdbcTransactor = JdbcTransactor.fromDataSource(dataSource, SqlDialect.PostgreSQL)
```
