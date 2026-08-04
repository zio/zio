# Transactor

> Reference for Transactor and JdbcTransactor: the sql module's entry point for connection lifecycle and transaction management.

`Transactor` is the entry point for all SQL execution in the `sql` module. It declares two methods that acquire a JDBC connection, execute a provided body, and guarantee the connection is closed on return — whether the body succeeds or throws:

- **`Transactor#connect`** — supplies a `DbCon` implicit context for non-transactional queries; auto-commit remains at its default state.
- **`Transactor#transact`** — supplies a `DbTx` implicit context with auto-commit disabled; commits on success and rolls back on any exception.

`DbTx` extends `DbCon`, so every `Frag` execution method and every `Repo` CRUD operation that requires `DbCon` also works inside `transact`. The module's other core types — `Frag`, `Table`, and `Repo` — all depend on the context provided by `Transactor` to reach the database.

The structural shape of the trait is:

```scala
trait Transactor {
  def connect[A](f: DbCon ?=> A): A
  def transact[A](f: DbTx ?=> A): A
}
```

`JdbcTransactor` is the concrete JDBC-backed implementation. Its companion object provides factory methods for all common connection strategies:

```scala
class JdbcTransactor(
  connectionFactory: () => java.sql.Connection,
  val dialect: SqlDialect,
  val sqlLogger: SqlLogger
) extends Transactor

object JdbcTransactor {
  def fromDataSource(dataSource: javax.sql.DataSource, dialect: SqlDialect): JdbcTransactor
  def fromUrl(url: String, dialect: SqlDialect): JdbcTransactor
  def fromUrl(url: String, user: String, password: String, dialect: SqlDialect): JdbcTransactor
  def postgres(dataSource: javax.sql.DataSource): JdbcTransactor
  def sqlite(dataSource: javax.sql.DataSource): JdbcTransactor
}
```

## Usage

The following block demonstrates the complete workflow: create a transactor, open a transactional scope, and execute both `Repo` operations and raw `sql"..."` fragments inside it:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

implicit val userCodec: DbCodec[User] = User.schema.deriving(DbCodecDeriver).derive

val repo       = Repo.derived[User, Int]("users", "id", _.id)
val transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

// transact: auto-commit disabled; commits on success, rolls back on exception
transactor.transact {
  repo.table.createTable(summon[DbTx].dialect).update
  repo.insert(User(1, "Alice", "alice@example.com"))
  repo.insert(User(2, "Bob", "bob@example.com"))

  // Raw fragment composes freely with Repo operations inside the same scope
  val active: List[User] =
    sql"SELECT id, name, email FROM users WHERE id > ${0}".query[User]
}

// connect: auto-commit unchanged; no transaction overhead for pure reads
val users: List[User] = transactor.connect {
  repo.all
}
```

## Construction / Creating Instances

We can create a `JdbcTransactor` from a `DataSource`, from a JDBC URL, or using database-specific convenience factories. In every case the third constructor parameter `sqlLogger` defaults to `SqlLogger.noop`, so it can be omitted unless query logging is required.

### `JdbcTransactor.fromDataSource` — Create from a DataSource

`JdbcTransactor.fromDataSource` is the recommended factory for production use. It calls `dataSource.getConnection` once per `connect` or `transact` invocation, so connection pooling is fully controlled by the `DataSource` implementation (for example, HikariCP or c3p0):

```scala
object JdbcTransactor {
  def fromDataSource(dataSource: javax.sql.DataSource, dialect: SqlDialect): JdbcTransactor
}
```

Pass the `DataSource` and the `SqlDialect` constant that matches your database. The following shows a typical setup where the data source is provided externally:

```scala
import zio.blocks.sql._

val dataSource: javax.sql.DataSource = ???
val transactor: JdbcTransactor =
  JdbcTransactor.fromDataSource(dataSource, SqlDialect.PostgreSQL)
```

### `JdbcTransactor.fromUrl` — Create from a JDBC URL

`JdbcTransactor.fromUrl` creates a transactor that opens each connection via `DriverManager.getConnection`. Two overloads are available: one without credentials and one that accepts a username and password:

```scala
object JdbcTransactor {
  def fromUrl(url: String, dialect: SqlDialect): JdbcTransactor
  def fromUrl(url: String, user: String, password: String, dialect: SqlDialect): JdbcTransactor
}
```

Use the credential-free overload for databases that embed authentication in the URL (for example, SQLite in-memory) or when the JDBC driver handles authentication separately:

```scala
import zio.blocks.sql._

// Without credentials — useful for SQLite or URL-embedded auth
val sqlite: JdbcTransactor =
  JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
// sqlite: JdbcTransactor = zio.blocks.sql.JdbcTransactor@6bc883b
```

Use the three-argument overload when the database requires a username and password supplied separately:

```scala
import zio.blocks.sql._

// With credentials — typical for PostgreSQL or MySQL
val postgres: JdbcTransactor =
  JdbcTransactor.fromUrl(
    "jdbc:postgresql://localhost/mydb",
    "alice",
    "secret",
    SqlDialect.PostgreSQL
  )
// postgres: JdbcTransactor = zio.blocks.sql.JdbcTransactor@40cda9e
```

:::caution
`fromUrl` opens a new physical connection for every `connect` or `transact` call and does no pooling. For applications with concurrent workloads, prefer `fromDataSource` backed by a pooling `DataSource`.
:::

### `JdbcTransactor.postgres` — PostgreSQL convenience factory

`JdbcTransactor.postgres` is shorthand for `fromDataSource(dataSource, SqlDialect.PostgreSQL)`. Use it to reduce boilerplate when working exclusively with PostgreSQL:

```scala
object JdbcTransactor {
  def postgres(dataSource: javax.sql.DataSource): JdbcTransactor
}
```

The factory fixes the dialect to `SqlDialect.PostgreSQL` so DDL type names, parameter placeholders, and dialect-specific SQL all render correctly for PostgreSQL:

```scala
import zio.blocks.sql._

val pgDataSource: javax.sql.DataSource = ???
val transactor: JdbcTransactor = JdbcTransactor.postgres(pgDataSource)
```

### `JdbcTransactor.sqlite` — SQLite convenience factory

`JdbcTransactor.sqlite` is shorthand for `fromDataSource(dataSource, SqlDialect.SQLite)`. Use it for SQLite databases where the `DataSource` is already available:

```scala
object JdbcTransactor {
  def sqlite(dataSource: javax.sql.DataSource): JdbcTransactor
}
```

This factory is useful when you wire the `DataSource` through a dependency-injection layer and want to keep dialect selection close to the data source definition rather than at each call site:

```scala
import zio.blocks.sql._

val sqDataSource: javax.sql.DataSource = ???
val transactor: JdbcTransactor = JdbcTransactor.sqlite(sqDataSource)
```

## Core Operations

`Transactor` exposes exactly two public methods. Together they cover the two modes of database access: non-transactional connections for reads and transactional connections for writes.

### Connection Management

`Transactor#connect` is the lightweight entry point for database access without a transaction. It provides a `DbCon` context, which is the implicit argument required by every `Frag` extension method and every `Repo` CRUD operation.

It acquires a connection from the underlying factory, wraps it in a `JdbcConnection`, synthesizes a `DbCon` given value, and executes the provided body. The connection is closed in a `finally` block, so it is released whether the body returns normally or throws:

```scala
trait Transactor {
  def connect[A](f: DbCon ?=> A): A
}
```

Because `DbTx extends DbCon`, a block that compiles under `connect` will also compile under `transact` — so we can promote non-transactional code to transactional scope without changing the body. The following example runs a read query inside `connect` without incurring transaction overhead:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

implicit val userCodec: DbCodec[User] = User.schema.deriving(DbCodecDeriver).derive

val repo       = Repo.derived[User, Int]("users", "id", _.id)
val transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

// The body receives DbCon as a given — no explicit passing required
val users: List[User] = transactor.connect {
  repo.all
}
```

:::caution
Inside `connect`, auto-commit is left at its default state (typically `true` for JDBC). Any DML statement executed here is committed immediately. If you need atomic multi-statement writes, use `transact` instead.
:::

### Transaction Management

`Transactor#transact` builds on `connect` by adding full transaction semantics: it disables auto-commit before executing the body, commits when the body returns normally, and rolls back when the body throws. The connection is always closed after commit or rollback.

It acquires a connection, sets `autoCommit = false`, synthesizes a `DbTx` given value, and runs the body. On success it calls `conn.commit()`. On any exception it calls `conn.rollback()`, adds any rollback failure as a suppressed exception, and rethrows the original. The connection is closed in the `finally` block regardless of outcome:

```scala
trait Transactor {
  def transact[A](f: DbTx ?=> A): A
}
```

Because `DbTx extends DbCon`, the `DbTx` given satisfies any `DbCon ?=>` requirement inside the block. We can mix `Repo` CRUD calls with raw `sql"..."` fragments freely:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema
import zio.blocks.maybe.Maybe

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

implicit val userCodec: DbCodec[User] = User.schema.deriving(DbCodecDeriver).derive
// userCodec: DbCodec[User] = zio.blocks.sql.DbCodecDeriver$$anon$20@72550983

val repo       = Repo.derived[User, Int]("users", "id", _.id)
// repo: Repo[User, Int] = zio.blocks.sql.Repo$DerivedRepo@144fc885
val transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
// transactor: JdbcTransactor = zio.blocks.sql.JdbcTransactor@12877e41

transactor.transact {
  repo.table.createTable(summon[DbTx].dialect).update
  repo.insert(User(1, "Alice", "alice@example.com"))

  // If this throws, the INSERT above is rolled back
  val existing: Maybe[User] = repo.find(1)
  existing
}
// res7: Maybe[User] = User(
//   id = 1,
//   name = "Alice",
//   email = "alice@example.com"
// )
```

:::caution
If `commit` itself throws after a successful body, the transaction is rolled back and the commit exception propagates. The body's return value is discarded in that case.
:::

## JdbcTransactor

`JdbcTransactor` is the only concrete implementation of `Transactor` in the `sql` module. It holds three constructor parameters: `connectionFactory`, `dialect`, and `sqlLogger`. The factory methods on its companion object cover the most common configurations; the primary constructor is available for custom setups such as test harnesses that inject a pre-existing connection:

```
Transactor  (trait — shared, cross-platform)
    └── JdbcTransactor  (class — JVM only, JDBC-backed)
```

You can subclass `JdbcTransactor` to override `connect` or `transact` — for example, to reuse a single shared connection across multiple calls in a test suite without closing it between calls, as the `TransactorSpec` test helper does internally.

## Transactor vs TransactorZIO

`Transactor` and `JdbcTransactor` are synchronous and suitable for code that does not use the ZIO effect system. `TransactorZIO`, provided by the separate `zio-blocks-sql-zio` artifact, wraps a `JdbcTransactor` and exposes two execution models for ZIO applications:

| Aspect                   | `Transactor` / `JdbcTransactor`                | `TransactorZIO` (sql-zio module)                                                                  |
|--------------------------|------------------------------------------------|---------------------------------------------------------------------------------------------------|
| **Return type**          | `A` (synchronous, blocking)                    | `Task[A]` (or `ZIO[R, E \| Throwable, A]` for ZIO bodies)                                         |
| **Thread model**         | Blocks the calling thread                      | `connect`/`transact` run on the blocking thread pool via `ZIO.attemptBlocking`                    |
| **Interruption safety**  | None — the body runs to completion             | `connectZIO`/`transactZIO` use `ZIO.acquireRelease`; connection closes even on fiber interruption |
| **ZIO dependency**       | None — zero ZIO dependency                     | Requires ZIO runtime                                                                              |
| **Dependency injection** | Manual construction                            | `TransactorZIO.layer` provides a `ZLayer`                                                         |
| **When to use**          | Synchronous imperative code, scripts, or tests | ZIO-based applications where effects compose across the entire call stack                         |

The following diagram shows how the two types relate:

```
JdbcTransactor  ──────────── wraps ───────────►  TransactorZIO
     │                                                  │
     │ connect  { DbCon ?=> A }: A                      │ connect  { DbCon ?=> A }:    Task[A]
     │ transact { DbTx  ?=> A }: A                      │ transact { DbTx  ?=> A }:    Task[A]
     │                                                  │ connectZIO  { DbCon ?=> ZIO[R,E,A] }
     │                                                  │ transactZIO { DbTx  ?=> ZIO[R,E,A] }
     │                                                  │
     └── (no ZIO dep) ────────────────────────── (ZIO runtime required) ──┘
```

Choose `JdbcTransactor` when the codebase is synchronous or when ZIO is not on the classpath. Choose `TransactorZIO` when you want connections bracketed by ZIO's `acquireRelease` so they survive fiber interruption, or when you need `ZLayer`-based dependency injection.

## Advanced Usage: Composing the Context

`DbCon` carries three members — `connection`, `dialect`, and `logger` — and is passed implicitly through every SQL operation. This means application-level helper methods can declare `(using DbCon)` and they are automatically satisfied anywhere inside `connect` or `transact` with no explicit argument passing. The same pattern composes across multiple layers:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

implicit val userCodec: DbCodec[User] = User.schema.deriving(DbCodecDeriver).derive
// userCodec: DbCodec[User] = zio.blocks.sql.DbCodecDeriver$$anon$20@460334f6

val repo       = Repo.derived[User, Int]("users", "id", _.id)
// repo: Repo[User, Int] = zio.blocks.sql.Repo$DerivedRepo@25787e5
val transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
// transactor: JdbcTransactor = zio.blocks.sql.JdbcTransactor@7fefa5d1

// Helper that composes two Repo calls — requires only DbCon, not Transactor
def upsertUser(user: User)(using DbCon): Unit = {
  if (repo.exists(user.id)) repo.update(user)
  else repo.insert(user)
}

// The Transactor provides the DbCon; upsertUser picks it up automatically
transactor.transact {
  repo.table.createTable(summon[DbTx].dialect).update
  upsertUser(User(1, "Alice", "alice@example.com"))
  upsertUser(User(1, "Alice Smith", "alice.smith@example.com"))
  repo.find(1)
}
// res9: Maybe[User] = User(
//   id = 1,
//   name = "Alice Smith",
//   email = "alice.smith@example.com"
// )
```

Because `DbTx extends DbCon`, `upsertUser` works unchanged inside both `connect` and `transact`. This lets us design helper functions against the minimal context they need and promote them to transactional scope at the call site without modifying their signatures.
