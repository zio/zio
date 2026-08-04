# DbCodec

> Reference for DbCodec[A], the foundational bidirectional codec between Scala values and database columns in the sql module.

`DbCodec[A]` is a bidirectional codec between a Scala value of type `A` and one or more database columns. Every read-side operation — fetching rows from a result set — and every write-side operation — binding parameters to a prepared statement — flows through a `DbCodec`. It is the foundational type in the `sql` module: [`Frag`](./frag.md) uses it to decode query results, [`Table`](./table.md) carries it as column metadata, and [`Repo`](./repo.md) relies on it to map entity rows to and from the database.

Key properties:
- **Bidirectional** — the same type handles both encoding (write) and decoding (read), keeping the two directions in sync.
- **Multi-column** — a single codec spans any number of database columns; a case class codec produces one column per field.
- **Label-based and positional** — `readValue` supports order-independent decoding by column label or fast 1-based positional access per the JDBC convention.
- **Schema-driven** — `DbCodec.derived` and `DbCodecDeriver` produce codecs automatically from a `Schema[A]` at compile time, with no runtime reflection.
- **Null-safe** — `Option[A]` and `Maybe[A]` codecs handle SQL `NULL` transparently; non-optional types throw `IllegalStateException` on unexpected `NULL`, surfacing schema mismatches immediately rather than silently coercing.

## Core API

```scala
import zio.blocks.sql.{DbResultReader, DbParamWriter, DbValue}
import zio.blocks.schema.derive.DerivationBuilder

trait DbCodec[A] {
  // Column inspection
  def columns: IndexedSeq[String]
  def columnCount: Int

  // Read operations
  def readValue(reader: DbResultReader, startIndex: Int): A
  def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): A

  // Write operations
  def writeValue(writer: DbParamWriter, startIndex: Int, value: A): Unit
  def toDbValues(value: A): IndexedSeq[DbValue]

  // Transformation
  def transform[B](read: A => B)(write: B => A): DbCodec[B]
}

object DbCodec {
  // Automatic derivation from Schema[A]
  inline given derived[A]: DbCodec[A]
  inline given derivedOpaque[A]: DbCodec[A]

  // Customize derivation
  inline def builder[A]: DerivationBuilder[DbCodec, A]
  inline def derivedWith[A](
    configure: DerivationBuilder[DbCodec, A] => DerivationBuilder[DbCodec, A]
  ): DbCodec[A]

  // Retrieval
  def apply[A](implicit codec: DbCodec[A]): DbCodec[A]

  // Built-in instances
  // given instances for: Int, Long, String, Boolean, Double, Float, Short, Byte,
  //                      BigDecimal, Instant, UUID, ...
}
```

Codecs for JSON/JSONB columns, type conversions, and specialized encoding strategies are also available through additional companion object methods and instances.

## Usage

The following example shows the core lifecycle of a `DbCodec`: deriving one automatically, inspecting its column metadata, encoding a value, handling nullable columns, and adapting the codec to a newtype:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

// Derive a codec automatically — field names map to snake_case columns by default
case class User(id: Int, name: String, email: Option[String]) derives DbCodec

val codec = DbCodec[User]
// codec: DbCodec[User] = zio.blocks.sql.DbCodecDeriver$$anon$20@55869d8

codec.columns
// res1: IndexedSeq[String] = Vector("id", "name", "email")
codec.columnCount
// res2: Int = 3

// Encode a value for use as SQL parameters
val params = codec.toDbValues(User(1, "Alice", Some("alice@example.com")))
// params: IndexedSeq[DbValue] = Vector(
//   DbInt(1),
//   DbString("Alice"),
//   DbString("alice@example.com")
// )

// None encodes as SQL NULL
val nullParams = codec.toDbValues(User(2, "Bob", None))
// nullParams: IndexedSeq[DbValue] = Vector(DbInt(2), DbString("Bob"), DbNull)

// Adapt any codec to a newtype with transform — no full Schema needed
case class UserId(value: Int)
val userIdCodec: DbCodec[UserId] = DbCodec[Int].transform(UserId(_))(_.value)
// userIdCodec: DbCodec[UserId] = zio.blocks.sql.DbCodec$$anon$1@2f13d28a

userIdCodec.columns
// res3: IndexedSeq[String] = Vector("value")
userIdCodec.toDbValues(UserId(42))
// res4: IndexedSeq[DbValue] = Vector(DbInt(42))
```

## Construction / Creating Instances

We can obtain a `DbCodec[A]` in several ways: automatic schema derivation (the most common path), structured derivation with field-level overrides, JSONB wrapping for complex types, opaque-type support, and manual composition.

### `DbCodec.derived` — Automatic schema-driven derivation

`DbCodec.derived` is a Scala 3 `inline given` that produces a `DbCodec[A]` by internally deriving `Schema[A]` and running it through `DbCodecDeriver`. Because it derives `Schema` itself, no explicit `Schema` needs to be in scope. It also enables the Scala 3 `derives` clause.

```scala
object DbCodec {
  inline given derived[A]: DbCodec[A]
}
```

The two most common spellings are the `derives` clause on the case class and explicit summoning:

```scala
import zio.blocks.sql._

// Option 1: derives clause
case class Product(sku: String, price: BigDecimal, inStock: Boolean) derives DbCodec

// Option 2: explicit given
case class Category(id: Int, name: String)
given DbCodec[Category] = DbCodec.derived

// Columns follow the default SqlNameMapper (SnakeCase):
// Product → "sku", "price", "in_stock"
// Category → "id", "name"
```

`DbCodec.derived` delegates to `DbCodecDeriver`, which handles primitives, case classes, enums, sealed traits, `Option`/`Maybe` fields, and JSONB-encoded complex fields. Enum and sealed-trait variants serialize to their name as a `String` column unless annotated with `@Modifier.rename`.

:::tip
For case classes with fields that need custom codecs or name overrides, prefer `DbCodec.derivedWith` so you can supply those overrides through the `DerivationBuilder` API.
:::

### `DbCodec.derivedWith` — Derivation with field-level overrides

`DbCodec.derivedWith` derives a `DbCodec[A]` and applies caller-supplied overrides before finalizing the codec. This is the right choice when a specific field needs a custom codec — for example, a JSON-encoded value type or a field stored in a non-default format.

```scala
object DbCodec {
  inline def derivedWith[A](
    configure: DerivationBuilder[DbCodec, A] => DerivationBuilder[DbCodec, A]
  ): DbCodec[A]
}
```

The configure function receives a `DerivationBuilder[DbCodec, A]` and returns a modified one. We call `DerivationBuilder#instance` to attach a custom `DbCodec` for a specific field, identified by the enclosing type's `TypeId` and the field name:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema
import zio.blocks.typeid.TypeId

case class Tags(values: List[String])
object Tags { implicit val schema: Schema[Tags] = Schema.derived }

case class Product(id: Int, tags: Tags)
object Product { implicit val schema: Schema[Product] = Schema.derived }

// tags is stored as a JSON string in the "tags" column
val tagsCodec: DbCodec[Tags] =
  DbCodec[String].transform(json => Tags(json.split(",").toList))(_.values.mkString(","))
// tagsCodec: DbCodec[Tags] = zio.blocks.sql.DbCodec$$anon$1@3592645d

val productCodec: DbCodec[Product] =
  DbCodec.derivedWith[Product](
    _.instance(TypeId.of[Product], "tags", tagsCodec)
  )
// productCodec: DbCodec[Product] = zio.blocks.sql.DbCodecDeriver$$anon$20@73120731

productCodec.columns
// res7: IndexedSeq[String] = Vector("id", "tags")
productCodec.columnCount
// res8: Int = 2
```

### `DbCodec.jsonb` — JSONB column codec

`DbCodec.jsonb` creates a `DbCodec[A]` that stores and retrieves a value of type `A` as a JSON string in a single database column. Two overloads are available: one using an implicit `JsonSchemaCodec[A]` for the encode/decode pair, and one accepting explicit functions.

```scala
object DbCodec {
  def jsonb[A](using jsonCodec: JsonSchemaCodec[A]): DbCodec[A]
  def jsonb[A](encode: A => String, decode: String => A): DbCodec[A]
}
```

The first overload requires a `JsonSchemaCodec[A]` (aliased from `zio.blocks.schema.json.JsonCodec`) in implicit scope:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema
import zio.blocks.schema.json.{JsonCodec => JsonSchemaCodec, JsonCodecDeriver}

case class Address(street: String, city: String)
object Address {
  implicit val schema: Schema[Address]             = Schema.derived
  implicit val jsonCodec: JsonSchemaCodec[Address] = schema.deriving(JsonCodecDeriver).derive
}

// Address is stored as a JSON string in a single TEXT/JSONB column
val codec: DbCodec[Address] = DbCodec.jsonb[Address]
// codec: DbCodec[Address] = zio.blocks.sql.DbCodec$$anon$1@68d986d1

codec.columns
// res10: IndexedSeq[String] = Vector("value")
codec.toDbValues(Address("Main St", "NYC"))
// res11: IndexedSeq[DbValue] = Vector(
//   DbString("{\"street\":\"Main St\",\"city\":\"NYC\"}")
// )
```

Use the two-argument overload when you supply custom encode/decode logic instead of relying on `JsonSchemaCodec`:

```scala
import zio.blocks.sql._

case class Point(x: Double, y: Double)

// Custom JSON encoding using a hand-rolled format
val pointCodec: DbCodec[Point] = DbCodec.jsonb[Point](
  p => s"${p.x},${p.y}",
  s => { val parts = s.split(","); Point(parts(0).toDouble, parts(1).toDouble) }
)
// pointCodec: DbCodec[Point] = zio.blocks.sql.DbCodec$$anon$1@1144ae3c

pointCodec.toDbValues(Point(1.0, 2.0))
// res12: IndexedSeq[DbValue] = Vector(DbString("1.0,2.0"))
```

### `DbCodec.jsonbOption` — Nullable JSONB column codec

`DbCodec.jsonbOption` creates a `DbCodec[Option[A]]` that stores `Some(a)` as a JSON string and `None` as SQL `NULL`. Like `jsonb`, it has an implicit `JsonSchemaCodec[A]` overload and a two-argument overload:

```scala
object DbCodec {
  def jsonbOption[A](using jsonCodec: JsonSchemaCodec[A]): DbCodec[Option[A]]
  def jsonbOption[A](encode: A => String, decode: String => A): DbCodec[Option[A]]
}
```

The codec delegates to `DbCodec[Option[String]]` and applies the JSON encode/decode on the inner `String`, so `NULL` detection uses the underlying `Option[String]` codec's standard null handling:

```scala
import zio.blocks.sql._
import zio.blocks.schema.json.{JsonCodec => JsonSchemaCodec}

// Assume JsonSchemaCodec[Address] is in scope from the previous example
val nullableCodec: DbCodec[Option[Address]] = DbCodec.jsonbOption[Address]
// nullableCodec: DbCodec[Option[Address]] = zio.blocks.sql.DbCodec$$anon$1@4c7b464f

nullableCodec.toDbValues(Some(Address("Elm St", "LA")))
// res13: IndexedSeq[DbValue] = Vector(
//   DbString("{\"street\":\"Elm St\",\"city\":\"LA\"}")
// )

nullableCodec.toDbValues(None)
// res14: IndexedSeq[DbValue] = Vector(DbNull)
```

### `DbCodec.derivedOpaque` — Opaque type derivation

`DbCodec.derivedOpaque` is a lower-priority `inline given` that produces a `DbCodec[A]` for Scala 3 opaque types by reusing the codec of the underlying type. The compiler selects it automatically when `A` is an opaque type and no explicit `DbCodec[A]` is in scope.

```scala
object DbCodec {
  inline given derivedOpaque[A]: DbCodec[A]
}
```

For the decode direction the opaque type's companion `apply` is called. For the encode direction, if the opaque type is declared as a subtype of its underlying type (`opaque type T <: U = U`), the value is used directly; otherwise the companion must expose an `unwrap` method:

```scala
import zio.blocks.sql._

opaque type ProductId <: String = String
object ProductId {
  def apply(value: String): ProductId = value
}

// DbCodec[ProductId] is resolved automatically — no explicit given needed
val codec = DbCodec[ProductId]
// codec: DbCodec[ProductId] = zio.blocks.sql.DbCodec$$anon$6@3261707e
codec.columns
// res16: IndexedSeq[String] = Vector("value")
```

:::caution
`DbCodec.derivedOpaque` is a Scala 3-only macro. The `sql` module requires Scala 3.
:::

### `DbCodec.dbCodecFromAs` — Codec derivation via `As` conversion

`DbCodec.dbCodecFromAs` is a `given` that derives `DbCodec[B]` from `DbCodec[A]` and an `As[A, B]` conversion. It enables opaque types and newtype wrappers to receive a `DbCodec` automatically when their underlying type already has one and an `As[A, B]` instance is provided:

```scala
object DbCodec {
  given dbCodecFromAs[A, B](using conv: As[A, B], base: DbCodec[A]): DbCodec[B]
}
```

`As[A, B]` (from `zio.blocks.schema`) represents a validated conversion from `A` to `B` and from `B` back to `A`. The derived codec applies `As#into` on decode and `As#from` on encode; if either conversion returns a `Left`, an `IllegalStateException` is thrown at runtime:

```scala
import zio.blocks.sql._
import zio.blocks.schema.As

// Suppose As[String, EmailAddress] is defined and EmailAddress wraps String
// DbCodec[EmailAddress] is then resolved automatically — no explicit given needed
// val emailCodec = DbCodec[EmailAddress]
```

For types without an `As` instance, use `DbCodec[A].transform` instead.

### `DbCodec.apply` — Summoning an instance

`DbCodec.apply` summons an implicitly available `DbCodec[A]` from the current scope. It is the standard way to access a codec without writing `implicitly` or `summon`:

```scala
object DbCodec {
  def apply[A](implicit codec: DbCodec[A]): DbCodec[A]
}
```

We use `DbCodec.apply` whenever we need a codec value without knowing its derivation path:

```scala
import zio.blocks.sql._

case class Order(id: Long, status: String) derives DbCodec

// Summon the derived codec
val codec: DbCodec[Order] = DbCodec[Order]
// codec: DbCodec[Order] = zio.blocks.sql.DbCodecDeriver$$anon$20@678700e1
codec.columns
// res19: IndexedSeq[String] = Vector("id", "status")
```

### `DbCodec.builder` — Derivation builder

`DbCodec.builder[A]` returns a `DerivationBuilder[DbCodec, A]` pre-seeded with the derived schema for `A`. Use it when you need to attach multiple field-level overrides before calling `.derive` to finalize the codec, giving you full control over the build process:

```scala
object DbCodec {
  inline def builder[A]: DerivationBuilder[DbCodec, A]
}
```

`DerivationBuilder` exposes `instance` to attach custom codecs for individual fields and `derive` to produce the final codec. `DbCodec.derivedWith` is a one-liner wrapper around `builder`:

```scala
import zio.blocks.sql._
import zio.blocks.typeid.TypeId

case class Order(id: Long, tags: List[String], metadata: Map[String, String])

// Build manually — equivalent to derivedWith but explicit
val codec: DbCodec[Order] =
  DbCodec
    .builder[Order]
    .instance(TypeId.of[Order], "tags", DbCodec[String].transform(_.split(",").toList)(_.mkString(",")))
    .derive
```

## Predefined Instances

`DbCodec` provides `given` instances for all primitive and common JVM types. Each occupies a single column named `"value"`:

| Scala Type             | Given Name         | `DbValue` variant            | Notes                                              |
|------------------------|--------------------|------------------------------|----------------------------------------------------|
| `Int`                  | `intCodec`         | `DbValue.DbInt`              |                                                    |
| `Long`                 | `longCodec`        | `DbValue.DbLong`             |                                                    |
| `String`               | `stringCodec`      | `DbValue.DbString`           |                                                    |
| `Boolean`              | `booleanCodec`     | `DbValue.DbBoolean`          |                                                    |
| `Double`               | `doubleCodec`      | `DbValue.DbDouble`           |                                                    |
| `Float`                | `floatCodec`       | `DbValue.DbFloat`            |                                                    |
| `Short`                | `shortCodec`       | `DbValue.DbShort`            |                                                    |
| `Byte`                 | `byteCodec`        | `DbValue.DbByte`             |                                                    |
| `BigDecimal`           | `bigDecimalCodec`  | `DbValue.DbBigDecimal`       | Throws on SQL `NULL`; use `Option[BigDecimal]` for nullable columns. |
| `java.time.Instant`    | `instantCodec`     | `DbValue.DbInstant`          |                                                    |
| `Option[A]`            | `optionCodec`      | inner or `DbValue.DbNull`    | Requires a `DbCodec[A]`; single-column inner only. |
| `Maybe[A]`             | `maybeCodec`       | inner or `DbValue.DbNull`    | Requires a `DbCodec[A]`; single-column inner only. |

All primitive codecs set their single column name to `"value"`. When a primitive codec is used as part of a record derivation, `DbCodecDeriver` replaces the column name with the field name (after applying the `SqlNameMapper`).

## Core Operations

The five abstract and one final method on `DbCodec` divide into four operational groups: column metadata inspection, decoding from a result set, encoding to prepared-statement parameters, and transformation.

### Column Metadata

The column metadata methods expose the names and count of columns a codec spans. They are used by `Table`, `Repo`, and `Frag` to build SQL `SELECT`, `INSERT`, and `UPDATE` clauses without any per-call string assembly.

#### `columns` — Ordered column names

`DbCodec#columns` returns the ordered `IndexedSeq[String]` of database column names for this codec. The sequence matches the order in which `readValue` and `writeValue` consume and produce values.

```scala
trait DbCodec[A] {
  def columns: IndexedSeq[String]
}
```

For a case class codec produced by `DbCodec.derived`, each field maps to one column name after the `SqlNameMapper` (default: `SnakeCase`). Annotating a field with `@Modifier.rename("custom_name")` overrides the mapped name:

```scala
import zio.blocks.sql._
import zio.blocks.schema.{Schema, Modifier}

case class BlogPost(
  @Modifier.rename("post_id") id: Int,
  authorName: String
) derives DbCodec

DbCodec[BlogPost].columns
// res22: IndexedSeq[String] = Vector("post_id", "author_name")
```

#### `columnCount` — Number of columns

`DbCodec#columnCount` returns the number of columns this codec spans. It is derived from `columns.size` and provided as a concrete method:

```scala
trait DbCodec[A] {
  def columnCount: Int = columns.size
}
```

We use `columnCount` to validate multi-column usage and to calculate offsets when composing codecs. For all primitive codecs, `columnCount` is `1`. For a case class, it equals the number of non-transient fields (fields annotated with `@Modifier.transient()` are excluded):

```scala
import zio.blocks.sql._
import zio.blocks.schema.{Schema, Modifier}

case class Event(name: String, @Modifier.transient() internalFlag: Boolean = false) derives DbCodec

DbCodec[Event].columnCount // "internalFlag" is excluded
// res24: Int = 1
```

### Reading / Decoding

The two `readValue` overloads decode a Scala value from a `DbResultReader`, which abstracts over a JDBC `ResultSet`. Query execution in `Frag` prefers the label-based overload so that result column order can differ from codec column order.

#### `readValue` — Positional read

`DbCodec#readValue(reader, startIndex)` reads a value of type `A` from the result reader starting at the given 1-based column index. For a multi-column codec, it reads `columnCount` consecutive columns beginning at `startIndex`. Internally this overload delegates to the label-based overload by calling `DbResultReader#columnLabel` for each offset.

```scala
trait DbCodec[A] {
  def readValue(reader: DbResultReader, startIndex: Int): A
}
```

The default implementation converts positional access to label-based access automatically, so implementing only the label-based overload is sufficient when writing a custom `DbCodec`:

```scala
import zio.blocks.sql._

// For illustration: a custom single-column String codec
val uppercaseCodec: DbCodec[String] = new DbCodec[String] {
  val columns: IndexedSeq[String] = IndexedSeq("value")
  def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): String =
    reader.getString(columnLabels.head).toUpperCase
  def writeValue(writer: DbParamWriter, startIndex: Int, value: String): Unit =
    writer.setString(startIndex, value)
  def toDbValues(value: String): IndexedSeq[DbValue] =
    IndexedSeq(DbValue.DbString(value))
}

// positional overload works automatically
// uppercaseCodec.readValue(reader, 1) → delegates to label-based via columnLabel(1)
```

:::caution
`startIndex` is 1-based per the JDBC convention. Passing `0` will cause an out-of-bounds error in the underlying `ResultSet`.
:::

#### `readValue` — Label-based read

`DbCodec#readValue(reader, columnLabels)` reads a value of type `A` by looking up each column by label, allowing the result set's column order to differ from the codec's column order. `Frag#query` always calls this overload, passing the labels derived from the query's `SELECT` list.

```scala
trait DbCodec[A] {
  def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): A
}
```

The caller must supply exactly `columnCount` labels in the logical order matching the codec's `columns` sequence. In practice, `Frag` constructs this sequence automatically from the query result metadata:

```scala
import zio.blocks.sql._

case class User(id: Int, name: String) derives DbCodec

val codec = DbCodec[User]

// Calling with explicit labels — useful for custom result processing
// codec.readValue(reader, IndexedSeq("id", "name")) → User(...)
```

### Writing / Encoding

The two encoding methods convert a Scala value into database parameters: `writeValue` binds values directly to a `DbParamWriter` (a prepared statement), while `toDbValues` converts them to the typed `DbValue` ADT for inspection, testing, and logging.

#### `writeValue` — Bind to a prepared statement

`DbCodec#writeValue` writes a value of type `A` to a `DbParamWriter` starting at the given 1-based parameter index. For a multi-column codec, it writes exactly `columnCount` consecutive parameters beginning at `startIndex`.

```scala
trait DbCodec[A] {
  def writeValue(writer: DbParamWriter, startIndex: Int, value: A): Unit
}
```

`Frag` and `Repo` call this method to bind parameters when executing `INSERT` and `UPDATE` statements. For `None` / `Maybe.absent`, the codec calls `DbParamWriter#setNull` with `java.sql.Types.NULL`:

```scala
import zio.blocks.sql._

case class Point(x: Double, y: Double) derives DbCodec

val codec = DbCodec[Point]
// codec.writeValue(writer, 1, Point(3.0, 4.0))
// → writer.setDouble(1, 3.0); writer.setDouble(2, 4.0)
```

:::caution
`startIndex` is 1-based. The codec writes exactly `columnCount` parameters, so if you compose two codecs at offsets `i` and `i + codec.columnCount`, the second start index must be adjusted accordingly.
:::

#### `toDbValues` — Convert to `DbValue` representation

`DbCodec#toDbValues` converts a value of type `A` into an `IndexedSeq[DbValue]`, one element per column. The result is parallel to `columns` — `toDbValues(v)(i)` corresponds to `columns(i)`.

```scala
trait DbCodec[A] {
  def toDbValues(value: A): IndexedSeq[DbValue]
}
```

`toDbValues` is used by `Frag` and `Repo` to inspect or log parameters before binding, and in tests to assert encoding behavior without a real database connection:

```scala
import zio.blocks.sql._

case class Item(id: Int, name: String, price: Option[BigDecimal]) derives DbCodec

val codec = DbCodec[Item]
// codec: DbCodec[Item] = zio.blocks.sql.DbCodecDeriver$$anon$20@1ffebf02

codec.toDbValues(Item(1, "Widget", Some(BigDecimal("9.99"))))
// res29: IndexedSeq[DbValue] = Vector(
//   DbInt(1),
//   DbString("Widget"),
//   DbBigDecimal(9.99)
// )

codec.toDbValues(Item(2, "Gadget", None))
// res30: IndexedSeq[DbValue] = Vector(DbInt(2), DbString("Gadget"), DbNull)
```

### Transformations

#### `transform` — Map a codec to a new type

`DbCodec#transform` returns a new `DbCodec[B]` by mapping the read direction with `read: A => B` and the write direction with `write: B => A`. The resulting codec shares the same `columns` as the original and is the lightest way to create a codec for a newtype or value wrapper without defining a full `Schema`:

```scala
trait DbCodec[A] {
  final def transform[B](read: A => B)(write: B => A): DbCodec[B]
}
```

Both `read` and `write` must be total functions; any exception they throw propagates to the caller. The transformed codec delegates all column metadata and read/write operations to the inner codec after applying the conversions:

```scala
import zio.blocks.sql._

case class ProductId(value: String)

// Adapt the String codec to ProductId without a Schema
val productIdCodec: DbCodec[ProductId] =
  DbCodec[String].transform(ProductId(_))(_.value)
// productIdCodec: DbCodec[ProductId] = zio.blocks.sql.DbCodec$$anon$1@95d8a43

productIdCodec.columns
// res32: IndexedSeq[String] = Vector("value")
productIdCodec.toDbValues(ProductId("abc-1"))
// res33: IndexedSeq[DbValue] = Vector(DbString("abc-1"))
```

`DbCodec#transform` is also the engine behind `DbCodec.jsonb`, `DbCodec.jsonbOption`, and `DbCodec.dbCodecFromAs` — each of those constructors builds on top of an existing primitive or composite codec and applies `transform` to attach custom encode/decode logic.

## Supporting Types

The two interfaces that `DbCodec` depends on for its read and write operations are `DbResultReader` and `DbParamWriter`. Both abstract over the JDBC layer so the `sql` module's `shared` source compiles on Scala.js as well as the JVM, and so custom backends can substitute their own implementations without touching codec logic.

```
DbCodec[A]
   │  reads via         writes via
   ▼                    ▼
DbResultReader       DbParamWriter
   │                    │
JdbcResultSetReader  JdbcParamWriter
   │                    │
java.sql.ResultSet   java.sql.PreparedStatement
```

### `DbResultReader` — Result set abstraction

`DbResultReader` is the interface through which `DbCodec#readValue` reads column values from a query result. It supports both label-based access (e.g., `DbResultReader#getString("name")`) and 1-based positional access (e.g., `DbResultReader#getInt(1)`), plus `DbResultReader#wasNull` to detect SQL `NULL` after any `get*` call:

```scala
trait DbResultReader {
  def getInt(index: Int): Int
  def getInt(label: String): Int
  def getString(index: Int): String
  def getString(label: String): String
  def getBoolean(label: String): Boolean
  def getBigDecimal(label: String): java.math.BigDecimal
  def getInstant(label: String): java.time.Instant
  // ... and all other column types
  def columnLabel(index: Int): String
  def hasColumn(label: String): Boolean
  def wasNull: Boolean
}
```

`DbResultReader` is rarely used directly in application code. `Frag#query` wraps the JDBC `ResultSet` in a `JdbcResultSetReader` and passes it to the appropriate `DbCodec#readValue` call automatically.

### `DbParamWriter` — Prepared statement abstraction

`DbParamWriter` is the interface through which `DbCodec#writeValue` binds column values to a prepared statement. It follows the JDBC convention of 1-based parameter indexes and includes `setNull` for writing SQL `NULL`:

```scala
trait DbParamWriter {
  def setInt(index: Int, value: Int): Unit
  def setString(index: Int, value: String): Unit
  def setBoolean(index: Int, value: Boolean): Unit
  def setBigDecimal(index: Int, value: java.math.BigDecimal): Unit
  def setInstant(index: Int, value: java.time.Instant): Unit
  // ... and all other parameter types
  def setNull(index: Int, sqlType: Int): Unit
}
```

Like `DbResultReader`, `DbParamWriter` is not used directly in application code. `Frag#update` and `Repo` CRUD methods create a `JdbcParamWriter` wrapping a `java.sql.PreparedStatement` and pass it to `DbCodec#writeValue` internally.

## Integration

`DbCodec` sits at the center of the `sql` module's layered architecture. The diagram below shows how it connects to its neighbours:

```
Schema[A]
    │
    ▼  (DbCodecDeriver)
DbCodec[A] ◄────────────────────────────────┐
    │                                        │
    ├──► Table[A]                            │
    │         └──► Repo[E, ID]               │
    │                   │                    │
    │              CRUD methods              │
    │                   │                    │
    └──► Frag ──────────┘                    │
              (sql"..." interpolator)        │
                   │                         │
         Transactor#connect/transact         │
                   │                         │
             DbCon / DbTx                    │
            /         \                      │
  DbResultReader   DbParamWriter ────────────┘
  (readValue)      (writeValue)
```
