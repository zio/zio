# DbCon

> Reference for DbCon, the implicit execution context in the sql module carrying a DbConnection, SqlDialect, and SqlLogger through SQL operations.

`DbCon` is the implicit execution context that threads database connection state through every SQL operation in the `sql` module. It is a plain trait with three abstract members — `connection`, `dialect`, and `logger` — and carries no type parameters:

```scala
trait DbCon {
  def connection: DbConnection
  def dialect: SqlDialect
  def logger: SqlLogger
}
```

Application code never constructs a `DbCon` directly. Instead, `Transactor#connect` and `Transactor#transact` each create one internally and supply it as a Scala 3 context parameter (`?=>`) to the block they receive. 

All `Frag` execution methods (`query`, `queryOne`, `queryLimit`, `update`, `updateReturningKeys`) and all `Repo` CRUD methods (`all`, `find`, `insert`, `update`, `delete`, and friends) require an implicit `DbCon` in scope — so any code that runs inside a `Transactor#connect` or `Transactor#transact` block automatically has everything it needs to execute SQL.

Key properties:
- **Implicit context type** — carries the active database session without explicit parameter threading.
- **Three concrete members** — `connection` (the active `DbConnection`), `dialect` (the `SqlDialect`), and `logger` (the `SqlLogger`).
- **Extended by `DbTx`** — `DbTx` is a subtype of `DbCon` that marks transactional scope; any method that accepts `DbCon` also accepts `DbTx`.
- **Lifetime managed by `Transactor`** — the connection is closed when the enclosing `connect` or `transact` block returns, whether it succeeds or throws.

## Usage

The following example shows how `DbCon` is obtained from a `Transactor`, how the context is propagated into helper methods, and how the three members can be accessed when needed:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

val tx: Transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
// tx: Transactor = zio.blocks.sql.JdbcTransactor@4c1ffa3c

// DbCon is supplied automatically by connect — no explicit argument needed
tx.connect {
  // Frag execution picks up the given DbCon implicitly
  Frag.literal("CREATE TABLE users (id INTEGER NOT NULL, name TEXT NOT NULL)").update
  sql"INSERT INTO users (id, name) VALUES (${1}, ${"Alice"})".update
  val users: List[User] = sql"SELECT id, name FROM users".query[User]
  // Access the context members directly when needed
  val dialectName: String = summon[DbCon].dialect.toString
  (users, dialectName)
}
// res1: Tuple2[List[User], String] = (
//   List(User(id = 1, name = "Alice")),
//   "SQLite"
// )
```

## Entry Points

`DbCon` is never instantiated by application code. A `Transactor` creates and supplies the instance automatically. There are two entry points depending on whether transactional behaviour is needed.

### `Transactor#connect` — Non-transactional connection scope

`Transactor#connect` acquires a JDBC connection from the underlying source, wraps it in a `DbCon`, and calls the provided context function. The connection is closed when the block returns, whether it completes normally or throws. Auto-commit remains at the driver default (enabled for most JDBC drivers), so each statement issued inside the block commits independently.

The signature from `Transactor` is:

```scala
trait Transactor {
  def connect[A](f: DbCon ?=> A): A
}
```

The following example shows a non-transactional read that uses the supplied `DbCon` to execute a query:

```scala
import zio.blocks.sql._

val tx: Transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

// The `DbCon` binding is supplied by `connect` — no manual construction
val names: List[String] = tx.connect {
  sql"SELECT name FROM users ORDER BY name".query[String]
}
```

:::caution
Do not capture the `DbCon` value or the `DbConnection` it contains and use them outside the `connect` block. The connection is closed when the block returns, and any statement prepared or executed on it afterward will throw.
:::

### `Transactor#transact` — Transactional connection scope

`Transactor#transact` acquires a connection, disables auto-commit, and supplies a `DbTx` context — a subtype of `DbCon` — to the block. On normal return the transaction commits; on any thrown exception it rolls back. The connection is closed after commit or rollback. Because `DbTx extends DbCon`, all `Frag` and `Repo` methods that accept `DbCon` work unchanged inside `transact`.

The signature from `Transactor` is:

```scala
trait Transactor {
  def transact[A](f: DbTx ?=> A): A
}
```

The following example shows a transactional write that inserts two rows atomically — if the second insert throws, the first is rolled back:

```scala
import zio.blocks.sql._

val tx: Transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

tx.transact {
  // Both inserts commit together; an exception rolls back both
  sql"INSERT INTO users (id, name) VALUES (${1}, ${"Alice"})".update
  sql"INSERT INTO users (id, name) VALUES (${2}, ${"Bob"})".update
}
```

## Core Operations

`DbCon` exposes three read-only members. They are rarely accessed directly in application code — the `Transactor` configures them at construction time and `Frag` / `Repo` consume them implicitly — but they become useful when integrating with lower-level JDBC abstractions, logging frameworks, or custom dialect rendering.

### Context Access

The three context-access members return the `DbConnection`, `SqlDialect`, and `SqlLogger` held by the current `DbCon` instance.

#### `DbCon#connection` — Underlying JDBC-abstraction connection

`DbCon#connection` returns the `DbConnection` wrapping the active JDBC `java.sql.Connection`. `DbConnection` exposes `prepareStatement`, `prepareStatementReturningKeys`, transaction-control methods, and `close`. It is consumed internally by every `Frag` and `Repo` operation that executes a statement. Access it directly only when you need to drop below the `Frag` layer to a raw prepared statement.

```scala
trait DbCon {
  def connection: DbConnection
}
```

The following example shows how to retrieve the `DbConnection` and use it to execute a raw prepared statement — useful for operations that the `Frag` API does not cover directly:

```scala
import zio.blocks.sql._

val tx: Transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

tx.connect {
  val conn: DbConnection = summon[DbCon].connection
  val ps = conn.prepareStatement("SELECT COUNT(*) FROM users")
  try {
    val rs = ps.executeQuery()
    try { rs.next(); rs.reader.getInt(1) }
    finally rs.close()
  } finally ps.close()
}
```

:::caution
Never call `connection.close()` manually. The `Transactor` closes the connection when the enclosing `connect` or `transact` block finishes, whether it returns normally or throws. Closing the connection early will cause all subsequent statements in the block to fail.
:::

#### `DbCon#dialect` — SQL dialect for fragment rendering

`DbCon#dialect` returns the `SqlDialect` used to render `Frag` values to parameterized SQL strings. Every `Frag` execution method calls `frag.sql(dialect)` internally to produce the driver-specific SQL before binding parameters. The dialect also governs how DDL type names are spelled when generating `CREATE TABLE` statements from `Table#createTable`.

```scala
trait DbCon {
  def dialect: SqlDialect
}
```

The following example shows reading the dialect from a context to render a `Frag` explicitly — for instance, to log the SQL before execution:

```scala
import zio.blocks.sql._

val tx: Transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
// tx: Transactor = zio.blocks.sql.JdbcTransactor@606f8c9a

tx.connect {
  val frag = sql"SELECT id FROM users WHERE id = ${42}"
  frag.sql(summon[DbCon].dialect)
}
// res6: String = "SELECT id FROM users WHERE id = ?"
```

#### `DbCon#logger` — Query execution logger

`DbCon#logger` returns the `SqlLogger` that the `Transactor` was configured with. After each successful statement `SqlLogger#onSuccess` receives the rendered SQL, the parameter list, the execution duration, and the affected-row count. After a failed statement `SqlLogger#onError` receives the same information plus the thrown exception. The default implementation — `SqlLogger.noop` — discards all events; replace it with a custom `SqlLogger` to integrate with your preferred logging framework.

```scala
trait DbCon {
  def logger: SqlLogger
}
```

The following example shows passing a logging `SqlLogger` to `JdbcTransactor` so that every query executed inside `connect` or `transact` is recorded:

```scala
import zio.blocks.sql._

val loggingLogger: SqlLogger = new SqlLogger {
  def onSuccess(event: SqlLogger.SuccessEvent): Unit =
    println(s"OK [${event.duration.toMillis} ms, ${event.rowCount} rows]: ${event.sql}")
  def onError(event: SqlLogger.ErrorEvent): Unit =
    println(s"ERR [${event.duration.toMillis} ms]: ${event.sql} — ${event.error.getMessage}")
}
// loggingLogger: SqlLogger = repl.MdocSession$MdocApp7$$anon$9@1d96b35a

val tx: Transactor =
  new JdbcTransactor(
    () => java.sql.DriverManager.getConnection("jdbc:sqlite::memory:"),
    SqlDialect.SQLite,
    loggingLogger
  )
// tx: Transactor = zio.blocks.sql.JdbcTransactor@7abee0ec

tx.connect {
  // Every Frag execution notifies loggingLogger automatically
  Frag.literal("SELECT 1").query[Int]
}
// OK [0 ms, 1 rows]: SELECT 1
// res8: List[Int] = List(1)
```

## Transactional Scope — `DbTx`

`DbTx` is the only subtype of `DbCon`. It is a marker trait — it adds no new members — whose sole purpose is to distinguish code that runs inside `Transactor#transact` from code that runs inside `Transactor#connect` at the type level:

```scala
trait DbTx extends DbCon
```

Because `DbTx` has no additional members, it exists purely to make the transactional / non-transactional boundary visible in method signatures. A method that requires `DbTx` cannot be called from a `connect` block, but a method that requires `DbCon` can be called from either. This asymmetry is enforced by the compiler: `DbTx <: DbCon` so `DbTx` satisfies a `DbCon` requirement, but a plain `DbCon` does not satisfy a `DbTx` requirement.

The following example illustrates how to write a helper method that is restricted to transactional scope:

```scala
import zio.blocks.sql._

// This helper compiles only inside a `transact` block, never inside `connect`
def insertUser(id: Int, name: String)(using DbTx): Unit = {
  sql"INSERT INTO users (id, name) VALUES ($id, $name)".update
  ()
}

val tx: Transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

tx.transact {
  insertUser(1, "Alice") // OK — DbTx satisfies the `using DbTx` requirement
}

// tx.connect {
//   insertUser(1, "Alice") // Compile error — DbCon does not satisfy `using DbTx`
// }
```

When a helper only reads data and should work in both contexts, declare it with `using DbCon`:

```scala
import zio.blocks.sql._
import zio.blocks.maybe.Maybe

// Usable inside both `connect` and `transact` blocks
def findUser(id: Int)(using DbCon): Maybe[String] =
  sql"SELECT name FROM users WHERE id = $id".queryOne[String]
```
