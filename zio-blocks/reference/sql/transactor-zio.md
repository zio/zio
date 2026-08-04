# TransactorZIO

> Reference for TransactorZIO: the ZIO-integrated JDBC transactor with blocking wrappers and interrupt-safe effect-aware connection management.

`TransactorZIO` is the ZIO-integrated database transactor from the `zio-blocks-sql-zio` module. It wraps a [`JdbcTransactor`](./transactor.md) — the synchronous JDBC transactor from the core `sql` module — and lifts its connection and transaction lifecycle management into the ZIO effect system. The module targets Scala 3 and runs on JVM only.

:::info
This page is a detailed reference for the `TransactorZIO` class specifically. If you only need `ZLayer`-based dependency injection for the plain synchronous `Transactor` (via `JdbcTransactor.postgresLayer` / `sqliteLayer`), see the [SQL — ZIO Integration](../sql-zio.md) guide instead — it covers both integration styles and when to reach for each.
:::

`TransactorZIO` exposes two pairs of methods. The **blocking wrappers** (`connect`, `transact`) run a synchronous body on ZIO's blocking thread pool via `ZIO.attemptBlocking` and return `Task[A]`. The **effect-aware methods** (`connectZIO`, `transactZIO`) accept a body that itself returns a `ZIO[R, E, A]`, bracket the JDBC connection with `ZIO.acquireRelease`, and return `ZIO[R, E | Throwable, A]` — guaranteeing connection cleanup even under fiber interruption.

- **Immutable** — `TransactorZIO` holds no mutable state; each `connect` or `transact` invocation opens a fresh connection via the provided `connectionFactory`.
- **Interrupt-safe** — `connectZIO` and `transactZIO` close the underlying JDBC connection even when the enclosing fiber is interrupted, because they use `ZIO.acquireRelease` internally.
- **Thread-safe** — the transactor is safe to share across fibers; each invocation manages its own connection.
- **JDBC-based** — available on JVM only; Scala 3 is required.

The structural shape of the class and its companion is:

```scala
class TransactorZIO(
  connectionFactory: () => Connection,
  val dialect: SqlDialect,
  val logger: SqlLogger = SqlLogger.noop
) {
  def connect[A](f: DbCon ?=> A): Task[A]
  def transact[A](f: DbTx ?=> A): Task[A]
  def connectZIO[R, E, A](f: DbCon ?=> ZIO[R, E, A]): ZIO[R, E | Throwable, A]
  def transactZIO[R, E, A](f: DbTx ?=> ZIO[R, E, A]): ZIO[R, E | Throwable, A]
}

object TransactorZIO {
  def fromDataSource(dataSource: javax.sql.DataSource, dialect: SqlDialect): TransactorZIO
  def fromUrl(url: String, dialect: SqlDialect): TransactorZIO
  def fromUrl(url: String, user: String, password: String, dialect: SqlDialect): TransactorZIO
  def layer(url: String, dialect: SqlDialect): ZLayer[Any, Nothing, TransactorZIO]
}
```

## Usage

The following example demonstrates all four execution modes — a read with `connect`, an atomic write with `transact`, an effect-aware connection with `connectZIO`, and a full transactional ZIO pipeline with `transactZIO`:

```scala
import zio._
import zio.blocks.sql._
import zio.blocks.sql.zio.TransactorZIO
import zio.blocks.schema.Schema
import zio.blocks.maybe.Maybe

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }
implicit val userCodec: DbCodec[User] = User.schema.deriving(DbCodecDeriver).derive

val repo = Repo.derived[User, Int]("users", "id", _.id)
val tx   = TransactorZIO.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

// Synchronous read — runs on blocking thread pool, no transaction overhead
val readAll: Task[List[User]] = tx.connect {
  repo.all
}

// Atomic write — commits on success, rolls back on any failure
val insertUser: Task[Int] = tx.transact {
  repo.insert(User(1, "Alice", "alice@example.com"))
}

// Effect-aware connection — body returns ZIO; connection closes even on interruption
// connectZIO/transactZIO only wrap connection open/close in attemptBlocking, so the
// body itself must wrap each blocking JDBC call explicitly.
val effectRead: ZIO[Any, Throwable, List[User]] = tx.connectZIO {
  ZIO.attemptBlocking(repo.all)
}

// Effect-aware transaction — commits on success, rolls back on failure, interrupt-safe
val effectWrite: ZIO[Any, Throwable, Maybe[User]] = tx.transactZIO {
  for {
    _    <- ZIO.attemptBlocking(repo.insert(User(2, "Bob", "bob@example.com")))
    user <- ZIO.attemptBlocking(repo.find(2))
  } yield user
}

// ZLayer for wiring TransactorZIO through dependency injection
val transactorLayer: ZLayer[Any, Nothing, TransactorZIO] =
  TransactorZIO.layer("jdbc:sqlite::memory:", SqlDialect.SQLite)
```

## Installation

Add the `zio-blocks-sql-zio` artifact to your build. It depends on `zio-blocks-sql`, so you do not need to declare both:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-sql-zio" % "0.0.51"
```

The artifact is JVM-only and requires Scala 3.

## Construction / Creating Instances

The `TransactorZIO` companion provides four factory methods. We use `fromDataSource` for production deployments where a connection pool is already in place, `fromUrl` for prototyping or testing, and `layer` when wiring through ZIO's dependency-injection system.

### `TransactorZIO.fromDataSource` — Create from a DataSource

`TransactorZIO.fromDataSource` is the recommended factory for production use. It calls `dataSource.getConnection()` once per `connect` or `transact` invocation, delegating all pooling concerns to the `DataSource` implementation — for example, HikariCP or c3p0:

```scala
object TransactorZIO {
  def fromDataSource(dataSource: javax.sql.DataSource, dialect: SqlDialect): TransactorZIO
}
```

The following shows how to wrap a pre-existing `DataSource` with a PostgreSQL dialect:

```scala
import zio.blocks.sql._
import zio.blocks.sql.zio.TransactorZIO

val dataSource: javax.sql.DataSource = ???
val tx: TransactorZIO =
  TransactorZIO.fromDataSource(dataSource, SqlDialect.PostgreSQL)
```

### `TransactorZIO.fromUrl` — Create from a JDBC URL

`TransactorZIO.fromUrl` creates a transactor that opens each connection via `java.sql.DriverManager.getConnection`. The overload without credentials is convenient for databases that embed authentication in the URL or for local testing:

```scala
object TransactorZIO {
  def fromUrl(url: String, dialect: SqlDialect): TransactorZIO
}
```

The following creates an in-memory SQLite transactor, a common pattern in unit tests:

```scala
import zio.blocks.sql._
import zio.blocks.sql.zio.TransactorZIO

val tx: TransactorZIO =
  TransactorZIO.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
```

:::caution
`fromUrl` opens a new physical connection for every `connect` or `transact` call and does no pooling. For applications with concurrent request workloads, prefer `fromDataSource` backed by a pooling `DataSource`.
:::

### `TransactorZIO.fromUrl` (with credentials) — Create from a JDBC URL with username and password

Use the credential-bearing overload when the database requires a separate username and password rather than URL-embedded authentication:

```scala
object TransactorZIO {
  def fromUrl(url: String, user: String, password: String, dialect: SqlDialect): TransactorZIO
}
```

The following creates a PostgreSQL transactor with explicit credentials:

```scala
import zio.blocks.sql._
import zio.blocks.sql.zio.TransactorZIO

val tx: TransactorZIO = TransactorZIO.fromUrl(
  "jdbc:postgresql://localhost/mydb",
  "alice",
  "secret",
  SqlDialect.PostgreSQL
)
```

### `TransactorZIO.layer` — Provide as a ZLayer

`TransactorZIO.layer` wraps `fromUrl` in a `ZLayer` so that `TransactorZIO` can be provided through ZIO's dependency-injection system. The layer type `ZLayer[Any, Nothing, TransactorZIO]` requires no environment and cannot fail at construction time:

```scala
object TransactorZIO {
  def layer(url: String, dialect: SqlDialect): ZLayer[Any, Nothing, TransactorZIO]
}
```

The following shows how to compose the layer with a ZIO program that reads `TransactorZIO` from its environment:

```scala
import zio._
import zio.blocks.sql._
import zio.blocks.sql.zio.TransactorZIO
import zio.blocks.maybe.Maybe

val transactorLayer: ZLayer[Any, Nothing, TransactorZIO] =
  TransactorZIO.layer("jdbc:sqlite::memory:", SqlDialect.SQLite)

val program: ZIO[TransactorZIO, Throwable, Maybe[Int]] =
  ZIO.serviceWithZIO[TransactorZIO](_.connect {
    sql"SELECT 1".queryOne[Int]
  })

val runnable: ZIO[Any, Throwable, Maybe[Int]] =
  program.provideLayer(transactorLayer)
```

When credentials are required or a `DataSource` is already available, construct the transactor with `fromUrl(url, user, password, dialect)` or `fromDataSource(ds, dialect)` and wrap the result in `ZLayer.succeed`.

## Core Operations

`TransactorZIO` exposes two pairs of methods that differ in how they handle the body and the connection lifecycle. The first pair accepts a synchronous body and delegates to the underlying `JdbcTransactor` via `ZIO.attemptBlocking`; the second pair accepts an effect-returning body and manages the connection via `ZIO.acquireRelease` for interrupt-safe cleanup.

### Connection Management

The `connect` method acquires a JDBC connection, runs a synchronous body on ZIO's blocking thread pool, and closes the connection when the body returns. Use it for read-only queries or any non-transactional access that does not require ZIO effects inside the body.

#### `connect` — Execute a synchronous body within a connection

`TransactorZIO#connect` wraps the underlying `JdbcTransactor#connect` call in `ZIO.attemptBlocking`, moving the blocking JDBC work off the ZIO main thread pool. The body receives a [`DbCon`](./db-con.md) Scala 3 context parameter carrying the active `DbConnection`, `SqlDialect`, and `SqlLogger`:

```scala
class TransactorZIO {
  def connect[A](f: DbCon ?=> A): Task[A]
}
```

Inside the block, `DbCon` is available as a given, so every [`Frag`](./frag.md) execution method and every [`Repo`](./repo.md) CRUD operation works without any explicit argument passing:

```scala
import zio._
import zio.blocks.sql._
import zio.blocks.sql.zio.TransactorZIO
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }
implicit val userCodec: DbCodec[User] = User.schema.deriving(DbCodecDeriver).derive

val repo: Repo[User, Int] = Repo.derived[User, Int]("users", "id", _.id)
val tx: TransactorZIO     = TransactorZIO.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

// DbCon is synthesized by connect and threaded into all automatically
val users: Task[List[User]] = tx.connect {
  repo.all
}
```

:::caution
Inside `connect`, auto-commit is left at its driver default (typically `true`). Any DML executed here is committed immediately. Use `transact` when you need atomic multi-statement writes.
:::

### Transaction Management

The `transact` method acquires a JDBC connection, disables auto-commit, runs a synchronous body on ZIO's blocking thread pool, and commits or rolls back before closing the connection. Use it whenever writes must succeed or fail as a single atomic unit.

#### `transact` — Execute a synchronous body within a transaction

`TransactorZIO#transact` wraps the underlying `JdbcTransactor#transact` call in `ZIO.attemptBlocking`. Auto-commit is disabled before the body runs; on a normal return the connection is committed, and on any exception it is rolled back. The connection is always closed in a `finally` block regardless of outcome. The body receives a [`DbTx`](./db-tx.md) Scala 3 context parameter:

```scala
class TransactorZIO {
  def transact[A](f: DbTx ?=> A): Task[A]
}
```

Because `DbTx` extends `DbCon`, any method that works inside `connect` also works inside `transact` without modification — we can promote code to transactional scope at the call site without changing helper signatures:

```scala
import zio._
import zio.blocks.sql._
import zio.blocks.sql.zio.TransactorZIO
import zio.blocks.schema.Schema
import zio.blocks.maybe.Maybe

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }
implicit val userCodec: DbCodec[User] = User.schema.deriving(DbCodecDeriver).derive

val repo: Repo[User, Int] = Repo.derived[User, Int]("users", "id", _.id)
val tx: TransactorZIO     = TransactorZIO.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

// Both statements run in one transaction; failure in either rolls back the whole block
val result: Task[Maybe[User]] = tx.transact {
  repo.insert(User(1, "Alice", "alice@example.com"))
  repo.find(1)
}
```

### Effect-Aware Connection Management

The `connectZIO` method acquires a JDBC connection via `ZIO.acquireRelease`, runs an effect-returning body with `DbCon` in scope, and guarantees the connection is closed when the scope exits — including on fiber interruption. Use it when the body needs to interleave SQL queries with other ZIO operations such as logging, concurrency, or calls to external services.

#### `connectZIO` — Execute an effect-returning body within a connection

`TransactorZIO#connectZIO` accepts a body of type `DbCon ?=> ZIO[R, E, A]` and returns `ZIO[R, E | Throwable, A]`. The error channel widens to `E | Throwable` because the connection-acquisition step — a blocking JDBC call — can throw `Throwable` independently of the body's declared error type:

```scala
class TransactorZIO {
  def connectZIO[R, E, A](f: DbCon ?=> ZIO[R, E, A]): ZIO[R, E | Throwable, A]
}
```

Inside the body, `DbCon` is available as a given. `connectZIO` only wraps the connection open/close steps in `ZIO.attemptBlocking` — the body itself must wrap each blocking JDBC call the same way to avoid blocking a ZIO compute thread:

```scala
import zio._
import zio.blocks.sql._
import zio.blocks.sql.zio.TransactorZIO
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }
implicit val userCodec: DbCodec[User] = User.schema.deriving(DbCodecDeriver).derive

val repo: Repo[User, Int] = Repo.derived[User, Int]("users", "id", _.id)
val tx: TransactorZIO     = TransactorZIO.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

// The body returns a ZIO; the connection remains open for the full duration of the effect
val result: ZIO[Any, Throwable, List[User]] = tx.connectZIO {
  for {
    users <- ZIO.attemptBlocking(repo.all)
    _     <- ZIO.logInfo(s"Fetched ${users.size} users from the database")
  } yield users
}
```

Unlike `connect`, the connection lifecycle here spans the entire ZIO scope. If the fiber is interrupted after the connection is opened but before the body completes, the connection is still released because `ZIO.acquireRelease` guarantees the release action runs regardless of how the scope exits.

### Effect-Aware Transaction Management

The `transactZIO` method brackets a full JDBC transaction with `ZIO.acquireRelease`: it disables auto-commit, runs an effect-returning body with `DbTx` in scope, commits on success, rolls back on failure, and closes the connection. This is the recommended method for transactional ZIO workflows.

#### `transactZIO` — Execute an effect-returning body within a transaction

`TransactorZIO#transactZIO` accepts a body of type `DbTx ?=> ZIO[R, E, A]` and returns `ZIO[R, E | Throwable, A]`. Auto-commit is disabled in the acquire step and restored to its original value in the release step. The body's exit value drives the commit/rollback decision:

```scala
class TransactorZIO {
  def transactZIO[R, E, A](f: DbTx ?=> ZIO[R, E, A]): ZIO[R, E | Throwable, A]
}
```

On success the connection is committed; on failure — whether from an error, a defect, or fiber interruption — the transaction is rolled back before the connection is closed. As with `connectZIO`, wrap each blocking JDBC call in the body with `ZIO.attemptBlocking`:

```scala
import zio._
import zio.blocks.sql._
import zio.blocks.sql.zio.TransactorZIO
import zio.blocks.schema.Schema
import zio.blocks.maybe.Maybe

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }
implicit val userCodec: DbCodec[User] = User.schema.deriving(DbCodecDeriver).derive

val repo: Repo[User, Int] = Repo.derived[User, Int]("users", "id", _.id)
val tx: TransactorZIO     = TransactorZIO.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

// The ZIO for-comprehension runs within a single JDBC transaction
val result: ZIO[Any, Throwable, Maybe[User]] = tx.transactZIO {
  for {
    _    <- ZIO.attemptBlocking(repo.insert(User(1, "Alice", "alice@example.com")))
    user <- ZIO.attemptBlocking(repo.find(1))
    _    <- ZIO.logInfo("Insert and fetch completed atomically")
  } yield user
}
```

:::caution
If `commit` itself fails after a successful body, a rollback is attempted. If both `commit` and `rollback` fail, the rollback error is suppressed onto the commit error (via `addSuppressed`), and the commit error is the one that propagates. The body's return value is discarded in either failure case.
:::

## Comparison: `TransactorZIO` vs `JdbcTransactor`

[`JdbcTransactor`](./transactor.md) is the synchronous, non-ZIO transactor from the `sql` module. `TransactorZIO` wraps it and adds a ZIO integration layer on top. The table below summarizes when to use each:

| Aspect                    | `JdbcTransactor` (`sql` module)                      | `TransactorZIO` (`sql-zio` module)                                               |
|---------------------------|------------------------------------------------------|-------------------------------------------------------------------------------------|
| **Return type**           | `A` (synchronous, blocking)                          | `Task[A]` or `ZIO[R, E \| Throwable, A]`                                           |
| **Thread model**          | Blocks the calling thread directly                   | `connect`/`transact` run on ZIO's blocking thread pool via `ZIO.attemptBlocking`   |
| **Interruption safety**   | None — body runs to completion                       | `connectZIO`/`transactZIO` close the connection on fiber interruption               |
| **ZIO dependency**        | None — zero ZIO runtime dependency                   | Requires ZIO runtime                                                                |
| **Dependency injection**  | Manual construction                                  | `TransactorZIO.layer` provides a `ZLayer[Any, Nothing, TransactorZIO]`              |
| **Scala versions**        | Scala 3 only (the `sql` module is cross-built JVM + JS, but `JdbcTransactor` itself is JVM-only) | Scala 3 only (JVM only)                                                             |
| **When to use**           | Synchronous code, scripts, or non-ZIO applications   | ZIO-based applications where effects compose across the entire call stack           |

The following diagram shows how the two types relate within the `sql` and `sql-zio` modules:

```
sql module                             sql-zio module
──────────────────────────────         ───────────────────────────────────────────────────
JdbcTransactor                         TransactorZIO
  connect  { DbCon ?=> A }: A    ◄──     connect  { DbCon ?=> A }:    Task[A]
  transact { DbTx  ?=> A }: A    ◄──     transact { DbTx  ?=> A }:    Task[A]
  (no ZIO dep)                           connectZIO  { DbCon ?=> ZIO[R,E,A] }
                                         transactZIO { DbTx  ?=> ZIO[R,E,A] }
                                         layer(url, dialect): ZLayer[Any, Nothing, TransactorZIO]
```

`TransactorZIO` holds a private `JdbcTransactor` field and delegates the synchronous `connect` and `transact` calls to it, wrapping each in `ZIO.attemptBlocking`. The effect-aware `connectZIO` and `transactZIO` bypass `JdbcTransactor` entirely and manage the connection lifecycle with `ZIO.acquireRelease` directly.
