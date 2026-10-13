# sql(table) Checked Interpolation

> > **Note**: Checked form is `StringContext(...).sql(table, ...)(...)`; no `sqlChecked` alias (removed pre-1.0).

`sql` with tables gives you compile-time typo protection for hand-written SQL: it validates table and column identifiers against the `Table[?]` values you hand it, with did-you-mean suggestions. Without tables it still validates quotes/parentheses. The escape hatch `SqlLiteral` lets you splice unchecked raw SQL when needed. It is lint-grade, not a full SQL parser — zero runtime overhead when unused.

## What it does

The `sql` interpolator composes two compile-time checks:

1. Quote/paren validation from `SqlValidator` (unclosed quotes, unbalanced parentheses) — always.
2. Identifier checking via `SqlIdentifierChecker` when tables are provided: tokenizes literal parts respecting `'...'` / `"..."` and `__HOLE__` placeholders, subtracts the SQL keyword/function allowlist (~160 entries, case-insensitive), tracks aliases introduced via `AS alias`, and requires every remaining identifier to be a known table name or column.

Unknown identifiers emit compile-time errors via `report.error` (up to 5 diagnostics), each message containing the identifier and a Levenshtein `<=2` suggestion when close.

## Usage without and with Table

Tables are derived from `Schema` as elsewhere:

```scala
import zio.blocks.schema._
import zio.blocks.sql._

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

case class Order(id: Int, userId: Int, amount: Double)
object Order { implicit val schema: Schema[Order] = Schema.derived }

val usersTable  = Table.derived[User]   // name "user"
val ordersTable = Table.derived[Order]  // name "order"
```

Without tables, only quote/paren checks run (identifier checking skipped):

```scala
val plain: Frag = sql"SELECT * FROM users WHERE email = ${"a@b.com"}"
```

With tables, identifiers are checked. Pass the relevant tables before the interpolated args — the macro extracts table/column names from their case class fields at compile time:

```scala
val q1: Frag = StringContext("SELECT * FROM user WHERE email = ", "").sql(usersTable)("a@b.com")
val q2: Frag = StringContext("SELECT user.id, order.amount FROM user JOIN order ON user.id = order.user_id").sql(usersTable, ordersTable)()
val q3: Frag = StringContext("SELECT u.id FROM user AS u WHERE u.email = ", "").sql(usersTable)("x")
```

Positive examples compile and render identically to the plain `sql` form:

```scala
q1.sql(SqlDialect.PostgreSQL)
// res0: String = "SELECT * FROM user WHERE email = ?"
q2.sql(SqlDialect.PostgreSQL)
// res1: String = "SELECT user.id, order.amount FROM user JOIN order ON user.id = order.user_id"
q3.sql(SqlDialect.PostgreSQL)
// res2: String = "SELECT u.id FROM user AS u WHERE u.email = ?"
```

Parameters are handled identically regardless of checking — any `DbParam[T]` (or `DbValue`) can be interpolated and becomes a `?` placeholder:

```scala
val email: String = "alice@example.com"
val frag: Frag = StringContext("SELECT * FROM user WHERE email = ", " AND id = ", "").sql(usersTable)(email, 42)
```

## Did-you-mean example

Typos fail at compile time with a suggestion (only when tables are provided):

```scala
StringContext("SELECT * FROM usre").sql(usersTable)()
// error:
// Unknown identifier 'usre' at position 14; did you mean 'user'?
// StringContext("SELECT * FROM usre").sql(usersTable)()
//                                                   ^
```

```
// error: Unknown identifier 'usre' at position 14; did you mean 'user'?
```

```scala
StringContext("SELECT emial FROM user").sql(usersTable)()
// error:
// Unknown identifier 'emial' at position 7; did you mean 'email'?
// StringContext("SELECT emial FROM user").sql(usersTable)()
//                                                       ^
```

```
// error: Unknown identifier 'emial' at position 7; did you mean 'email'?
```

```scala
StringContext("SELECT * FROM user WHERE badcol = 1").sql(usersTable)()
// error:
// Unknown identifier 'badcol' at position 25
// StringContext("SELECT * FROM user WHERE badcol = 1").sql(usersTable)()
//                                                                    ^
```

```
// error: Unknown identifier 'badcol' at position 28
```

Up to 5 diagnostics are reported per interpolator invocation; fix them iteratively.

## Limits — lint-grade, not a parser

The checked `sql(tables)` is intentionally lightweight:

- **No expression typing.** It does not type-check `WHERE` expressions or enforce that `amount > "foo"` is ill-typed.
- **Subquery internals out of scope.** Identifiers inside nested `SELECT` subqueries are checked as flat tokens; column scoping across subqueries is not modeled.
- **Aliases trusted.** Once an alias is introduced via `AS alias` (case-insensitive, quoted aliases supported), further uses of that alias and `alias.column` are trusted without verifying the column belongs to the aliased table.
- **Allowlist coverage.** ~160 SQL keywords, types, and common functions (SELECT/FROM/WHERE/JOIN/COUNT/SUM/AVG/COALESCE/CASE/etc.) are allowlisted case-insensitively. Uncommon dialect-specific functions may need the escape hatch.
- **Keyword-named tables.** Tables named like `order`/`group` overlap the keyword allowlist; they are trusted as keywords unless passed as a `Table`, so ensure such tables are included in the `Table[?]` arguments — known tables/columns are checked before the allowlist, so `order` is recognized when it is in `knownTables`.
- **Table extraction.** Column names are derived from case class fields via `SqlNameMapper.SnakeCase`. Custom renames (`@Modifier.rename`) or complex `Table("name", codec, cols)` constructions are not fully reflected — prefer `Table.derived` for checked queries. In particular, `Table.name` from `@Modifier.config("sql.table_name")` is only reflected in the checker when the `Table` is constructed with an explicit name literal (e.g. `Table.derived[User]("my_table")` or `Table("my_table", ...)`); otherwise the macro falls back to `SnakeCase(typeName)` which may mismatch the runtime name — see `SqlMacros.addTableMeta` limitation.
- **String literals respected.** Identifiers inside `'...'` are never flagged; `"quoted identifiers"` are validated as identifiers.

If a valid query is flagged, use the escape hatch below.

## Escape hatch: `SqlLiteral`

> **Warning**: `SqlLiteral` is spliced verbatim without escaping — never construct it from untrusted input (user data, request params). Use `DbParam`/`?` placeholders for values; reserve `SqlLiteral` for trusted, dialect-specific SQL.

When you need dynamic SQL or a dialect-specific construct that the lint cannot model, use `SqlLiteral` — unchecked raw SQL.

Standalone unchecked fragment (no validation, no `Table` needed):

```scala
val raw: Frag = SqlLiteral("SELECT MY_CUSTOM_FUNC(id) FROM user").toFrag
// also: SqlLiteral.frag("SELECT ...")
```

Splicing raw SQL inside the `sql` interpolator as a verbatim fragment (not a `?` parameter):

```scala
val qRaw: Frag = sql"SELECT ${SqlLiteral("MY_CUSTOM_FUNC(id)")} FROM user"
val qMixed: Frag = sql"SELECT ${SqlLiteral("MY_FUNC()")}, email FROM user WHERE id = ${42}"
```

`Frag` values are also spliced verbatim: `sql"SELECT * FROM (${myFrag}) WHERE id = ${id}"`.

Spliced `SqlLiteral`/`Frag` content is not identifier-checked; the surrounding literal parts still are (when tables are provided). Interpolated values that are not `SqlLiteral`/`Frag` always become `?` placeholders, so dialect-specific SQL must be spliced as `SqlLiteral`/`Frag`, not as a bound parameter.

```scala
qRaw.sql(SqlDialect.PostgreSQL)
// res6: String = "SELECT MY_CUSTOM_FUNC(id) FROM user"
qMixed.sql(SqlDialect.PostgreSQL)
// res7: String = "SELECT MY_FUNC(), email FROM user WHERE id = ?"
```

Prefer checked `sql(tables)` for all hand-written queries; reserve `SqlLiteral` for genuinely dynamic or dialect-specific cases.

## Comparison

| Form | Quote/paren check | Identifier check | Needs `Table` | Use when |
|------|-------------------|------------------|---------------|----------|
| `sql"..."` (no tables) | yes | no | no | Default, no schema coupling |
| `StringContext(...).sql(table)(...)` | yes | yes (first 5) | yes | Hand-written SQL you want typo-protected |
| `SqlLiteral("...")` / spliced `SqlLiteral` / `Frag` | no | no | no | Escape hatch for dynamic/dialect SQL |

Neither form changes `Frag` rendering; all produce the same `Frag(parts, params)` structure and `sql(dialect)` output.

## See also

- `SqlIdentifierChecker` — pure checker core and `DefaultAllowlist`
- `SqlValidator` — quote/paren validation
- `SqlLiteral` — unchecked raw SQL holder
- `Table.derived` — table derivation and `TableNamingPolicy`
