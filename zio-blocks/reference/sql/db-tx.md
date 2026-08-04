# DbTx

> Reference for DbTx, the SQL module's transactional scope marker that extends DbCon with commit-on-success and rollback-on-failure semantics.

`DbTx` is a marker trait in the `zio-blocks-sql` module that extends `DbCon` to signal a transactional execution scope. It declares no members of its own — its distinct type is what instructs `Transactor#transact` to disable auto-commit, commit the connection on success, and roll back on any thrown exception. You never construct a `DbTx` directly; the `Transactor` creates one and supplies it as a given context to the block passed to `transact`. The connection is always closed when the block exits, whether it commits, rolls back, or throws.

Key properties:
- **Transactional context marker** — A `DbTx` value in scope guarantees the underlying JDBC connection has auto-commit disabled.
- **Commit-on-success semantics** — The `Transactor` commits the connection when the `transact` block returns normally.
- **Rollback-on-failure semantics** — Any uncaught exception causes the `Transactor` to roll back the connection before re-throwing.

The structural declaration of `DbTx` is:

```scala
trait DbTx extends DbCon
```

Every context member that `DbTx` exposes is inherited from `DbCon`, which declares the three fields every SQL operation consumes:

```scala
trait DbCon {
  def connection: DbConnection
  def dialect:    SqlDialect
  def logger:     SqlLogger
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
// repo: Repo[User, Int] = zio.blocks.sql.Repo$DerivedRepo@1b21124
val tx   = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
// tx: JdbcTransactor = zio.blocks.sql.JdbcTransactor@1583ec4c

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
