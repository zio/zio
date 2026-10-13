# DbTx

> Reference for DbTx, the SQL module's transactional scope marker that extends DbCon with commit-on-success and rollback-on-failure semantics.

`DbTx` is the transactional connection context supplied by `Transactor#transact`. It extends `DbCon` (so every `Frag`/`Repo` operation that needs `DbCon` also accepts `DbTx`) and adds savepoint-based nested transaction support. You never construct a `DbTx` directly; the `Transactor` creates one and supplies it as a given context to the block passed to `transact`. The connection is always closed when the outermost block exits, whether it commits, rolls back, or throws.

Key properties:
- **Transactional context** — A `DbTx` value in scope guarantees the underlying JDBC connection has auto-commit disabled.
- **Commit-on-success / rollback-on-failure** — The outermost `Transactor.transact` commits on normal return and rolls back on any uncaught exception (with suppressed rollback failures).
- **Savepoint-based nesting** — Inner blocks reuse the same connection via SQL savepoints (`SAVEPOINT` / `RELEASE SAVEPOINT` / `ROLLBACK TO SAVEPOINT`).
- **`transact(isolation, readOnly)`** — The two-arg `Transactor.transact` overload sets isolation level and `readOnly` before disabling auto-commit; `DbTx` nested blocks inherit those settings on the same connection.

The structural declaration of `DbTx` is:

```scala
trait DbTx extends DbCon {
  def savepoint(name: String): Unit
  def release(name: String): Unit
  def rollbackTo(name: String): Unit
  def currentDepth: Int
  private[sql] def currentDepth_=(depth: Int): Unit

  // inherited from DbCon
  def connection: DbConnection
  def dialect: SqlDialect
  def logger: SqlLogger
}
```

## Usage

The following example opens a transaction via `Transactor#transact`, accesses all three context members, and combines a `Repo` CRUD operation with a hand-written `Frag` query — both of which accept `DbTx` transparently in place of `DbCon`:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

val repo = Repo.derived[User, Int]("users", "id", _.id)
// repo: Repo[User, Int] = zio.blocks.sql.Repo$DerivedRepo@789742a3
val tx   = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
// tx: JdbcTransactor = zio.blocks.sql.JdbcTransactor@5276be33

// On normal return: transaction commits and connection closes.
// On any exception: transaction rolls back, then the exception propagates.
tx.transact {
  // All three context members are accessible via summon[DbTx]
  val conn: DbConnection = summon[DbTx].connection // managed JDBC connection — do not close manually
  val d:    SqlDialect   = summon[DbTx].dialect
  val log:  SqlLogger    = summon[DbTx].logger

  // Repo and Frag operations accept DbTx because DbTx extends DbCon
  repo.table.createTable(summon[DbTx].dialect).update
  repo.insert(User(1, "Alice", "alice@example.com"))
  repo.insert(User(2, "Bob",   "bob@example.com"))

  val all:    List[User] = repo.all
  val custom: List[User] =
    sql"SELECT id, name, email FROM users WHERE name LIKE ${"A%"}".query[User]

  (all, custom)
}
// res1: Tuple2[List[User], List[User]] = (
//   List(
//     User(id = 1, name = "Alice", email = "alice@example.com"),
//     User(id = 2, name = "Bob", email = "bob@example.com")
//   ),
//   List(User(id = 1, name = "Alice", email = "alice@example.com"))
// )
```

## Nested Transactions via Savepoints

Nested transactions reuse the same underlying JDBC connection via SQL savepoints. The `DbTx` given in scope exposes an extension `transact` and the `transactNested` helpers:

```scala
import zio.blocks.sql._

val transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

// Savepoint-based nesting — same connection, isolated rollback
transactor.transact {
  sql"INSERT INTO t VALUES (1)".update

  // Inner block runs inside SAVEPOINT zib_tx_1
  summon[DbTx].transact {
    sql"INSERT INTO t VALUES (2)".update
  }

  // Equivalent using the `using` helper
  // DbTx.transactNested { sql"INSERT INTO t VALUES (3)".update }
  // transactNested { sql"INSERT INTO t VALUES (4)".update }
}
```

Savepoint names are `zib_tx_1 .. zib_tx_N` where `N` is the nesting depth tracked in `currentDepth`. On success the savepoint is released via `RELEASE SAVEPOINT`; on failure it is rolled back via `ROLLBACK TO SAVEPOINT` and the exception is rethrown (with any rollback failure added as suppressed). Depth is decremented in `finally`, so sibling nested blocks reuse the same name sequence without leaking savepoints. `savepoint`/`release`/`rollbackTo` are also available directly for manual control and validate identifiers via `SqlIdentifier` to prevent injection.

:::caution
Only the outermost `Transactor.transact` issues a real `COMMIT`/`ROLLBACK`. Inner `summon[DbTx].transact` blocks are savepoint-scoped — outer commit still decides the final persistence of all work, including inner blocks that succeeded.
:::
