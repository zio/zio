# CSV Codec Module

> `zio-blocks-schema-csv` is a **schema-driven CSV codec module** for serializing and deserializing Scala types to and from CSV format. It provides RFC 4180-compliant parsing and generation with zero dependencies and support for 27 primitive types plus flat record (case class) types. Core types: `CsvCodec`, `CsvConfig`, `CsvError`, `CsvReader`, `CsvWriter`, `CsvFormat`.

`zio-blocks-schema-csv` is a **schema-driven CSV codec module** for serializing and deserializing Scala types to and from CSV format. It provides RFC 4180-compliant parsing and generation with zero dependencies and support for 27 primitive types plus flat record (case class) types. Core types: `CsvCodec`, `CsvConfig`, `CsvError`, `CsvReader`, `CsvWriter`, `CsvFormat`.

The main public API is `CsvCodec[A]`, which extends `TextCodec[A]` and provides CSV-specific header support:

```scala
import java.nio.CharBuffer
import zio.blocks.schema.SchemaError

// API signature (conceptual - simplified for clarity)
trait CsvCodec[A] {
  def headerNames: IndexedSeq[String]
  def encode(value: A, output: CharBuffer): Unit
  def decode(input: CharBuffer): Either[SchemaError, A]
}
```

## Motivation

CSV is the de facto standard for tabular data exchange across systems, but parsing and serialization are often error-prone when done manually. `zio-blocks-schema-csv` eliminates this friction by deriving codec instances directly from your Scala types using ZIO Schema. You describe your data shape once, and the module handles:
- RFC 4180-compliant parsing and generation with proper quote escaping
- Precise error reporting with row/column locations
- Zero-overhead errors (no stack traces) optimized for CSV stream processing
- Cross-platform support (JVM and Scala.js)

Rather than writing custom parsers or relying on string-based configuration, you work with strongly-typed schemas that the compiler validates.

## Installation

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-blocks-schema-csv" % "0.0.51"
```

For Scala.js, use `%%%` instead of `%%`:

```sbt
libraryDependencies += "dev.zio" %%% "zio-blocks-schema-csv" % "0.0.51"
```

Supported Scala versions: 2.13.x and 3.x

## Introduction

The module provides a complete pipeline for CSV codec derivation and usage:

1. **Define your type** — Any Scala type with a `Schema` instance
2. **Derive a codec** — Use `CsvFormat` to obtain a `CsvCodec[A]`
3. **Parse or serialize** — Call `CsvCodec#decode` or `CsvCodec#encode`
4. **Handle errors** — Catch `CsvError` with row/column location information

The derivation process is automatic for supported types (all 27 primitives and flat records). Unsupported types (sealed traits, sequences, maps) are rejected at derivation time with clear error messages.

## How They Work Together

The CSV codec pipeline flows through these layers:

```
1. User defines Schema[A] for their type
                 ↓
2. Schema.derive(CsvFormat) creates CsvCodec[A]
                 ↓
3. CsvCodecDeriver converts Schema to codec implementation
   - For primitives: type-specific encoders/decoders
   - For records: field-by-field composition
                 ↓
4. CsvCodec#encode writes values to CSV rows (via CsvWriter)
   CsvCodec#decode reads CSV rows to values (via CsvReader)
                 ↓
5. CsvReader/CsvWriter handle RFC 4180 quote escaping
   CsvConfig customizes delimiters, terminators, quoting
                 ↓
6. CsvError reports ParseError or TypeError with location
```

**Typical workflow:**

A user type flows through the derivation and encoding pipeline as follows:

```
User type (e.g., case class Person)
    ↓
Schema.derived (automatic via macro)
    ↓
.derive(CsvFormat) → CsvCodec[Person]
    ↓
Use codec.headerNames for column names
    ↓
Use codec.encode(person, buffer) to serialize
Use codec.decode(buffer) to deserialize
    ↓
Handle CsvError.ParseError or TypeError on failure
```

## Common Patterns

This section shows 4 practical patterns for working with CSV codecs in real-world scenarios.

### Pattern 1: Derive and Use a Simple Codec

To derive and use a CSV codec for a record type:

```scala
import zio.blocks.schema._
import zio.blocks.schema.csv._
import java.nio.CharBuffer

case class Person(name: String, age: Int, email: String)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Person.schema.derive(CsvFormat)
val person = Person("Alice", 30, "alice@example.com")
val buffer = CharBuffer.allocate(256)
codec.encode(person, buffer)
buffer.flip()
val encoded = buffer.toString
```

### Pattern 2: Customize CSV Format with CsvConfig

When you need tab-separated values (TSV) or a different delimiter, pass a custom `CsvConfig` to the derivation or directly to encoding/decoding methods.

To use a tab-separated format instead of comma-separated:

```scala
import zio.blocks.schema._
import zio.blocks.schema.csv._

case class Record(id: Int, value: String)

object Record {
  implicit val schema: Schema[Record] = Schema.derived
  // Note: Schema#derive accepts only a single format argument
  // TSV configuration would be set through the format's configuration API
  val tsvCodec = schema.derive(CsvFormat) // See CsvConfig for available options
}
```

### Pattern 3: Handle CSV Errors with Location Information

CSV errors report the exact row and column where parsing or type conversion failed, allowing you to give users precise feedback.

To handle parsing errors in a user-friendly way:

```scala
import zio.blocks.schema._
import zio.blocks.schema.csv._
import java.nio.CharBuffer

case class Employee(id: Int, name: String, salary: BigDecimal)

object Employee {
  implicit val schema: Schema[Employee] = Schema.derived
}

val codec = Employee.schema.derive(CsvFormat)
val csvData = "id,name,salary\n1,Alice,invalid\n"

// Attempt to parse the CSV
val result = codec.decode(CharBuffer.wrap(csvData))

result match {
  case Right(employee) => println(s"Parsed: $employee")
  case Left(error) =>
    // CsvError is wrapped in SchemaError
    println(s"Parse error: ${error.getMessage}")
}
```

### Pattern 4: Working with Headers

Access the derived header names and use them to construct CSV output or validate input.

To get the column headers for a record type:

```scala
import zio.blocks.schema._
import zio.blocks.schema.csv._

case class Product(sku: String, name: String, price: Double)

object Product {
  implicit val schema: Schema[Product] = Schema.derived
}

val codec = Product.schema.derive(CsvFormat)
val headers = codec.headerNames
// headers: IndexedSeq[String] = Vector("sku", "name", "price")
```

## CsvCodec[A]

Abstract codec for encoding and decoding values to and from CSV format. Extends `TextCodec[A]` and provides CSV-specific header support.

### Construction

Codecs are derived automatically from `Schema[A]` using `CsvFormat`:

```scala
import zio.blocks.schema._
import zio.blocks.schema.csv._

case class User(id: Int, username: String)

object User {
  implicit val schema: Schema[User] = Schema.derived
}

val codec: CsvCodec[User] = User.schema.derive(CsvFormat)
```

### Header Names

To access the CSV column headers derived from a record type:

```scala
import zio.blocks.schema._
import zio.blocks.schema.csv._

case class Book(title: String, author: String, isbn: String)

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

val codec = Book.schema.derive(CsvFormat)
val headers: IndexedSeq[String] = codec.headerNames
```

### Encoding Values

To serialize a value to CSV format in a `CharBuffer`:

```scala
import zio.blocks.schema._
import zio.blocks.schema.csv._
import java.nio.CharBuffer

case class Item(name: String, quantity: Int)

object Item {
  implicit val schema: Schema[Item] = Schema.derived
}

val codec = Item.schema.derive(CsvFormat)
val item = Item("Widget", 42)
val buffer = CharBuffer.allocate(512)
codec.encode(item, buffer)
buffer.flip()
val csvLine = buffer.toString
```

### Decoding Values

To parse CSV data from a `CharBuffer` into a value:

```scala
import zio.blocks.schema._
import zio.blocks.schema.csv._
import java.nio.CharBuffer

case class Sales(product: String, amount: BigDecimal)

object Sales {
  implicit val schema: Schema[Sales] = Schema.derived
}

val codec = Sales.schema.derive(CsvFormat)
val csvData = "product,amount\nWidget,99.99\n"
val result: Either[SchemaError, Sales] = codec.decode(CharBuffer.wrap(csvData))
```

---

## CsvConfig

Configuration for CSV parsing and generation, controlling delimiters, quoting, line termination, and header handling.

### Overview

`CsvConfig` is a case class with sensible RFC 4180 defaults. Customize it when you need different delimiters (e.g., tabs), custom line endings, or different quoting behavior.

### Defaults and Presets

The standard RFC 4180 CSV format:

```scala
import zio.blocks.schema.csv._

val config: CsvConfig = CsvConfig.default
// CsvConfig(',', '"', "\r\n", hasHeader = true, nullValue = "")
```

A tab-separated values (TSV) preset with tabs as delimiters:

```scala
import zio.blocks.schema.csv._

val tsvConfig: CsvConfig = CsvConfig.tsv
// CsvConfig('\t', '"', "\r\n", hasHeader = true, nullValue = "")
```

### Configuration Fields

- **`delimiter`** (default: `','`) — Character separating fields in a row
- **`quoteChar`** (default: `'"'`) — Character to quote fields containing delimiters or newlines; escapes quotes within quoted fields by doubling
- **`lineTerminator`** (default: `"\r\n"`) — Sequence terminating each row (RFC 4180 standard is CRLF)
- **`hasHeader`** (default: `true`) — Whether the first row contains column headers
- **`nullValue`** (default: `""`) — String representation for null values

### Custom Configuration

To use a pipe-delimited format:

```scala
import zio.blocks.schema.csv._

val customConfig = CsvConfig(
  delimiter = '|',
  quoteChar = '"',
  lineTerminator = "\n",
  hasHeader = true,
  nullValue = "NULL"
)
```

---

## CsvError

Sealed abstract class representing errors during CSV parsing and type conversion. Designed for high-performance CSV processing with zero-overhead exceptions (no stack traces).

### Error Hierarchy

All CSV errors track row and column information (both 1-based) for precise error reporting.

**ParseError** — Occurs when CSV format is invalid:

```scala
import zio.blocks.schema.csv._

val parseError = CsvError.ParseError("Unclosed quoted field", row = 2, column = 15)
```

**TypeError** — Occurs when a field cannot be converted to the expected type:

```scala
import zio.blocks.schema.csv._

val typeError = CsvError.TypeError(
  "Invalid integer: abc",
  row = 3,
  column = 5,
  fieldName = "age"
)
```

### Error Information

Both error types provide detailed context:

```scala
import zio.blocks.schema.csv._

val error = CsvError.ParseError("Unexpected character after closing quote", row = 1, column = 10)
val message: String = error.getMessage
val row: Int = error.row
val column: Int = error.column
```

---

## CsvReader

Low-level CSV row parser implementing RFC 4180 with a state machine approach. Provides stateless utility methods for parsing individual rows, headers, and complete documents.

### Parsing a Single Row

To parse one CSV row starting at a given offset in the input:

```scala
import zio.blocks.schema.csv._

val input = "Alice,30,alice@example.com\nBob,25,bob@example.com\n"
val config = CsvConfig.default

val result: Either[CsvError, (IndexedSeq[String], Int)] =
  CsvReader.readRow(input, offset = 0, config)

result match {
  case Right((fields, nextOffset)) =>
    println(s"Fields: $fields")
    println(s"Next offset: $nextOffset")
  case Left(err) =>
    println(s"Parse error: ${err.getMessage}")
}
```

### Parsing the Header Row

To parse just the first row as column headers:

```scala
import zio.blocks.schema.csv._

val input = "name,age,email\nAlice,30,alice@example.com\n"
val config = CsvConfig.default

val result: Either[CsvError, (IndexedSeq[String], Int)] =
  CsvReader.readHeader(input, config)
```

### Parsing Complete CSV

To parse a complete CSV document (header and all data rows):

```scala
import zio.blocks.schema.csv._

val csv = "name,age\nAlice,30\nBob,25\n"
val config = CsvConfig.default

val result: Either[CsvError, (IndexedSeq[String], IndexedSeq[IndexedSeq[String]])] =
  CsvReader.readAll(csv, config)

result match {
  case Right((header, rows)) =>
    println(s"Header: $header")
    rows.foreach(row => println(s"Row: $row"))
  case Left(err) =>
    println(s"Parse error: ${err.getMessage}")
}
```

---

## CsvWriter

Low-level CSV row serializer implementing RFC 4180 with proper field escaping.

### Writing a Single Row

To serialize one row of fields to CSV format:

```scala
import zio.blocks.schema.csv._

val fields: IndexedSeq[String] = Vector("Alice", "30", "alice@example.com")
val config = CsvConfig.default

val csvLine: String = CsvWriter.writeRow(fields, config)
// Result: "Alice,30,alice@example.com\r\n"
```

### Writing Headers

To write a header row (semantically identical to `CsvWriter#writeRow` but communicates intent):

```scala
import zio.blocks.schema.csv._

val columnNames: IndexedSeq[String] = Vector("name", "age", "email")
val config = CsvConfig.default

val csvHeader: String = CsvWriter.writeHeader(columnNames, config)
// Result: "name,age,email\r\n"
```

### Writing Complete CSV

To write a complete CSV document with headers and data rows:

```scala
import zio.blocks.schema.csv._

val header: IndexedSeq[String] = Vector("product", "quantity")
val rows: Seq[IndexedSeq[String]] = Seq(
  Vector("Widget", "10"),
  Vector("Gadget", "5"),
  Vector("Gizmo", "8")
)
val config = CsvConfig.default

val csv: String = CsvWriter.writeAll(header, rows, config)
```

### RFC 4180 Escaping

`CsvWriter` automatically escapes fields according to RFC 4180:
- Fields containing the delimiter, quote character, or newlines are wrapped in quotes
- Quote characters within quoted fields are doubled (e.g., `Alice "The Expert" Smith` becomes `"Alice ""The Expert"" Smith"`)
- Fields not requiring escaping are written as-is for readability

---

## CsvCodecDeriver

Implements schema-driven derivation of `CsvCodec[A]` instances. Automatically handles 27 primitive types and flat record types, rejecting unsupported types (sealed traits, sequences, maps) with clear error messages.

### Primitive Type Support

All 27 ZIO Schema primitives are supported:
- Numeric: `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigInt`, `BigDecimal`
- Logical: `Boolean`, `Char`, `String`
- Temporal: `Instant`, `LocalDate`, `LocalDateTime`, `LocalTime`, `Duration`, `Period`, `Year`, `YearMonth`, `MonthDay`, `Month`, `DayOfWeek`, `ZonedDateTime`, `OffsetDateTime`, `OffsetTime`, `ZoneId`, `ZoneOffset`
- Special: `UUID`, `Currency`, `Unit`

### Record Type Support

Flat case classes (records with no variant or sequence fields) are fully supported. Each field becomes a CSV column with the field name as the header:

```scala
import zio.blocks.schema._
import zio.blocks.schema.csv._

case class Address(street: String, city: String, zip: String)

object Address {
  implicit val schema: Schema[Address] = Schema.derived
}

val codec = Address.schema.derive(CsvFormat)
val headers = codec.headerNames // Vector("street", "city", "zip")
```

### Unsupported Types

Attempting to derive a codec for unsupported types (sealed traits, sequences, maps, dynamic types) results in a clear compile-time or derivation-time error. The deriver rejects these to prevent silent failures in CSV processing.

---

## CsvFormat

Integration point with ZIO Schema's format system. Provides `TextFormat[CsvCodec]` to enable `Schema[A].derive(CsvFormat)` for any supported type.

### Using CsvFormat

To derive a CSV codec using the standard format:

```scala
import zio.blocks.schema._
import zio.blocks.schema.csv._

case class Sensor(id: Long, temperature: Double, humidity: Float)

object Sensor {
  implicit val schema: Schema[Sensor] = Schema.derived
}

val codec = Sensor.schema.derive(CsvFormat)
```

`CsvFormat` is a singleton object extending `TextFormat[CsvCodec]` with the MIME type `"text/csv"` and the `CsvCodecDeriver` as its derivation strategy.
