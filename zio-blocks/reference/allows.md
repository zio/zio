# Allows

> `Allows[A, S]` is a compile-time capability token that proves, at the call site, that type `A` satisfies the structural grammar `S`. A capability token is a compile-time phantom proof value — it carries no runtime data and exists solely to pass evidence through the type system that a structural constraint has been satisfied.

`Allows[A, S]` is a compile-time capability token that proves, at the call site, that type `A` satisfies the structural grammar `S`. A capability token is a compile-time phantom proof value — it carries no runtime data and exists solely to pass evidence through the type system that a structural constraint has been satisfied.

`Allows` does **not** require or use `Schema[A]`. It inspects the Scala type structure of `A` directly at compile time, using nothing but the Scala type system. Any `Schema[A]` that appears alongside `Allows` in examples is the library author's own separate constraint — it is not imposed by `Allows` itself.

```scala
sealed abstract class Allows[A, S <: Allows.Structural]
```

## Overview

The gap `Allows` fills is **structural preconditions** at the call site, at compile time, with precise error messages. Structural preconditions are constraints on the shape of a type's fields (e.g., "all fields must be scalars"), unlike runtime checks which happen during execution and produce exceptions or errors.

## Motivation

ZIO Blocks gives library authors a powerful way to build data-oriented DSLs. A library can accept `A: Schema` and use the schema at runtime to serialize, deserialize, query, or transform values of `A`. A data-oriented DSL is a generic API built around a data description (Schema) rather than a fixed interface, allowing one function to serialize, validate, or transform any conforming type. Many generic functions have **structural preconditions** that don't require a schema.

Consider these real-world scenarios:

- A CSV serializer requires flat records of scalars — nested records should fail at the call site, not deep inside the serializer.
- An RDBMS layer cannot handle nested records as column values — the error should name the problematic field.
- An event bus expects a sealed trait of flat record cases — violations should be caught before publishing.
- A JSON document store allows arbitrarily nested records but not `DynamicValue` leaves — the schema validation should be precise. DynamicValue is the schema-less escape hatch that can hold arbitrary data — a DynamicValue leaf bypasses compile-time checking entirely, making it impossible for the compiler to enforce any structural grammar.

Without `Allows`, these constraints can only be checked at runtime, producing confusing errors deep inside library internals. With `Allows[A, S]`, the constraint is verified at the **call site**, at compile time, with precise, path-aware error messages and concrete fix suggestions.

## The Upper Bound Semantics

`Allows[A, S]` is an upper bound. A type `A` that uses only a strict subset of what `S` permits also satisfies it — just as `A <: Foo` does not require that `A` uses every method of `Foo`. Upper bound semantics is the right choice because a lower bound would require using every shape (impractical), exact matching would require naming every shape used (too rigid), whereas upper bound says "your type may use any of these shapes" — a permission, not a mandate.

```scala

// Both satisfy Record[Primitive | Optional[Primitive]] — the upper bound

case class UserRow(name: String, age: Int)
// UserRow satisfies the grammar: all fields are Primitive

case class UserRowOpt(name: String, age: Int, email: Option[String])
// UserRowOpt also satisfies the grammar: all fields are Primitive or Optional[Primitive]

val ev1: Allows[UserRow, Record[Primitive | Optional[Primitive]]] = implicitly
val ev2: Allows[UserRowOpt, Record[Primitive | Optional[Primitive]]] = implicitly
```

## Creating Instances

`Allows[A, S]` is not instantiated directly. Instead, you summon an evidence value at the point where you need the constraint. The macro automatically verifies the constraint at compile time.

<Tabs groupId="scala-version" defaultValue="scala2">
  <TabItem value="scala2" label="Scala 2">

```scala

def toJson[A](doc: A)(implicit ev: Allows[A, Record[Primitive]]): String = ???

// Or summon at the call site:
val evidence = implicitly[Allows[Int, Primitive]]
```

  </TabItem>
  <TabItem value="scala3" label="Scala 3">

```scala

def toJson[A](doc: A)(using Allows[A, Record[Primitive]]): String = ???

// Calling the function:
case class Person(name: String, age: Int)
val json = toJson(Person("Alice", 30))  // Compiles if Person satisfies Record[Primitive]
```

  </TabItem>
</Tabs>

The constraint is checked once, at the call site. If the type `A` does not satisfy `S`, you get a compile-time error with a precise message showing exactly which field violates the grammar.

## Grammar Nodes

All grammar nodes extend `Allows.Structural`.

| Node | Matches |
|---|---|
| `Primitive` | **Any** scalar — catch-all for all 30 Schema 2 primitive types |
| `Primitive.Boolean` | `scala.Boolean` only |
| `Primitive.Int` | `scala.Int` only |
| `Primitive.Long` | `scala.Long` only |
| `Primitive.Double` | `scala.Double` only |
| `Primitive.Float` | `scala.Float` only |
| `Primitive.String` | `java.lang.String` only |
| `Primitive.BigDecimal` | `scala.BigDecimal` only |
| `Primitive.BigInt` | `scala.BigInt` only |
| `Primitive.Unit` | `scala.Unit` only |
| `Primitive.Byte` | `scala.Byte` only |
| `Primitive.Short` | `scala.Short` only |
| `Primitive.Char` | `scala.Char` only |
| `Primitive.UUID` | `java.util.UUID` only |
| `Primitive.Currency` | `java.util.Currency` only |
| `Primitive.Instant` / `LocalDate` / `LocalDateTime` / … | Each specific `java.time.*` type |
| | |
| `Record[A]` | A case class / product type whose every field satisfies `A`. Vacuously true for zero-field records. Sealed traits and enums are **automatically unwrapped**: each case is checked individually, so no `Variant` node is needed. |
| `Sequence[A]` | Any collection (`List`, `Vector`, `Set`, `Array`, `Chunk`, …) whose element type satisfies `A` |
| `Map[K, V]` | `Map`, `HashMap`, … whose key satisfies `K` and value satisfies `V` |
| `Optional[A]` | `Option[X]` where the inner type `X` satisfies `A` |
| `Wrapped[A]` | A ZIO Prelude `Newtype`/`Subtype` wrapper whose underlying type satisfies `A` |
| | |
| `Self` | Recursive self-reference back to the entire enclosing `Allows[A, S]` grammar |
| `Dynamic` | `DynamicValue` — the schema-less escape hatch |
| `IsType[A]` | Exact nominal type match: satisfied only when the checked type is exactly `A` (`=:=`) |
| `` `\|` `` | Union of two grammar nodes: `A \| B`. In Scala 2 write `` A `\|` B `` in infix position. |

Every specific `Primitive.Xxx` node also satisfies the top-level `Primitive` node (which matches any of the 30 primitive types). This means a type annotated with `Primitive.Int` is valid wherever `Primitive` or `Primitive | Primitive.Long` is required.

## Core Operations

`Allows[A, S]` is a **proof token**, not an ordinary value. It carries zero public methods that you call directly. Instead, you use it in three ways:

1. **As a constraint in function signatures** — Declare `Allows[A, S]` as an implicit/using parameter to require that callers pass only types satisfying the grammar.
2. **To summon evidence** — Use `implicitly[Allows[A, S]]` (Scala 2) or `summon[Allows[A, S]]` (Scala 3) at a call site to check the constraint and get an error message if it fails.
3. **In type aliases** — Define type aliases like `type FlatRecord = Allows[_, Record[Primitive | Optional[Primitive]]]` to name constraints and reuse them across functions.

The macro that powers `Allows` checks the constraint **at compile time** and emits nothing but a reference to a single private singleton at runtime, so there is zero per-call-site overhead.

## Specific Primitives

The `Primitive` parent class is the catch-all: it accepts any of the 30 Schema 2 primitive types. For stricter control — such as when the target serialisation format only supports a subset — use the specific subtype nodes in `Allows.Primitive`:

```scala

// Only JSON-representable scalars (no UUID, Char, java.time.*)
type JsonPrimitive =
  Primitive.Boolean | Primitive.Int | Primitive.Long |
  Primitive.Double  | Primitive.String | Primitive.BigDecimal |
  Primitive.BigInt  | Primitive.Unit

def toJson[A](doc: A)(using Allows[A, Record[JsonPrimitive | Self]]): String = ???

// Only numeric types
type Numeric = Primitive.Int | Primitive.Long | Primitive.Double | Primitive.Float |
               Primitive.BigInt | Primitive.BigDecimal

def aggregate[A](data: A)(using Allows[A, Record[Numeric]]): Double = ???
```

A type annotated with `Primitive.Int` satisfies `Primitive` (the catch-all) because `Primitive.Int extends Primitive`:

```scala

val ev: Allows[Int, Primitive] = implicitly  // Primitive (catch-all) — ✓
val sp: Allows[Int, Primitive.Int] = implicitly  // Primitive.Int (specific) — ✓
```

### JSON Document Store Example

JSON's primitive value set is `null | boolean | number | string`. Types such as `UUID`, `Char`, and all `java.time.*` types have no native JSON representation and must be encoded as strings at the application layer. Using `JsonPrimitive` instead of the catch-all `Primitive` enforces this at compile time.

A JSON document grammar is straightforward: a JSON value is either a record (JSON object) or a sequence (JSON array), and `Self` handles all nesting:

```scala

type JsonPrimitive =
  Primitive.Boolean | Primitive.Int | Primitive.Long | Primitive.Double |
  Primitive.String  | Primitive.BigDecimal | Primitive.BigInt | Primitive.Unit

type Json = Record[JsonPrimitive | Self] | Sequence[JsonPrimitive | Self]

def toJson[A](doc: A)(using Allows[A, Json]): String = ???
```

`Self` recurses back to `Json` at every nested position, so `List[String]` satisfies `Sequence[JsonPrimitive | Self]` (String is JsonPrimitive), `List[Author]` satisfies it too (Author satisfies `Record[JsonPrimitive | Self]` via Self), and top-level arrays work directly.

A type with a UUID or Instant field fails at compile time with this error:

```
[error] Schema shape violation at WithUUID.id: found Primitive(java.util.UUID),
        required Primitive.Boolean | Primitive.Int | ... | Primitive.String | ...
        UUID is not a JSON-native type — encode it as Primitive.String.
```

## Union Syntax

Union types express "or" in the grammar.

<Tabs groupId="scala-version" defaultValue="scala2">
  <TabItem value="scala2" label="Scala 2">

Uses the infix operator `` Primitive `|` Optional[Primitive] `` from `Allows`:

```scala

def writeCsv[A](rows: Seq[A])(implicit
  ev: Allows[A, Record[Primitive | Optional[Primitive]]]
): Unit = ???
```

  </TabItem>
  <TabItem value="scala3" label="Scala 3">

Uses native union type syntax:

```scala

def writeCsv[A](rows: Seq[A])(using
  Allows[A, Record[Primitive | Optional[Primitive]]]
): Unit = ???
```

  </TabItem>
</Tabs>

Both spellings compile and produce the same semantic behavior. The grammar is identical — the only difference is how the union type is expressed.

## Use Cases

### Flat Record (CSV, RDBMS Row)

```scala

// Flat record: only primitives and optional primitives allowed
def writeCsv[A: Schema](rows: Seq[A])(using
  Allows[A, Record[Primitive | Optional[Primitive]]]
): Unit = ???

// RDBMS INSERT: primitives, optional primitives, or string-keyed maps (JSONB)
def insert[A: Schema](value: A)(using
  Allows[A, Record[Primitive | Optional[Primitive] | Allows.Map[Primitive, Primitive]]]
): String = ???
```

If a user passes a type with nested records, they get a precise compile-time error like this:

```
[error] Schema shape violation at UserWithAddress.address: found Record(Address),
        required Primitive | Optional[Primitive] | Map[Primitive, Primitive]
```

### Event Bus / Message Broker

Published events are typically sealed traits of flat record cases. No `Variant` node is needed — sealed traits are automatically unwrapped:

```scala

// DomainEvent is a sealed trait; its cases must each satisfy Record[Primitive | Sequence[Primitive]]
def publish[A: Schema](event: A)(using
  Allows[A, Record[Primitive | Optional[Primitive] | Sequence[Primitive]]]
): Unit = ???
```

If a case of the sealed trait has a nested record field, the error names that case and field like this:

```
[error] Schema shape violation at DomainEvent.OrderPlaced.items.<element>:
        found Record(OrderItem), required Primitive | Optional[Primitive] | Sequence[Primitive]
```

### JSON Document Store (Recursive)

A document store accepts arbitrarily nested records but not `DynamicValue` leaves. The `Self` node expresses the recursive grammar:

```scala

type JsonDocument =
  Record[Primitive | Self | Optional[Primitive | Self] | Sequence[Primitive | Self] | Allows.Map[Primitive, Primitive | Self]]

def toJson[A: Schema](doc: A)(using Allows[A, JsonDocument]): String = ???
```

This grammar allows:
- `case class Author(name: String, email: String)` — Record[Primitive] ✓
- `case class Book(title: String, author: Author, tags: List[String])` — Record with Self-nested record and Sequence[Primitive] ✓
- `case class Category(name: String, subcategories: List[Category])` — recursive ✓

But rejects:
- `case class Bad(name: String, payload: DynamicValue)` — DynamicValue is not in the grammar ✗

### GraphQL / Tree Structures (Self)

```scala

def graphqlType[A: Schema]()(using
  Allows[A, Record[Primitive | Optional[Self] | Sequence[Self]]]
): String = ???

// Works:
case class TreeNode(value: Int, children: List[TreeNode])
object TreeNode { implicit val schema: Schema[TreeNode] = Schema.derived }
// graphqlType[TreeNode]() — compiles fine
```

## Sequence Subtypes

The `Sequence[A]` node accepts any collection type. When a DSL needs to restrict to a specific kind of collection — for example, a DynamoDB `Set` operation that is only valid on sets, not lists — use the `Sequence` subtypes:

```scala

// Only an immutable List is accepted
val listOnly: Allows[List[Int], Sequence.List[Primitive]] = implicitly

// Only an immutable Set is accepted
val setOnly: Allows[Set[Int], Sequence.Set[Primitive]] = implicitly

// Only a Vector
val vecOnly: Allows[Vector[String], Sequence.Vector[Primitive]] = implicitly

// Only an Array
val arrOnly: Allows[Array[Int], Sequence.Array[Primitive]] = implicitly

// Only a Chunk

val chkOnly: Allows[Chunk[String], Sequence.Chunk[Primitive]] = implicitly
```

Each subtype extends `Sequence[A]`, so a grammar written with the parent `Sequence` still accepts all collection kinds. A grammar written with a subtype rejects other kinds at compile time:

```
// Set[Int] does NOT satisfy Sequence.List[Primitive] — compile error:
[error] Shape violation at Set: found Sequence[scala.Int], required Sequence.List[...]
val bad: Allows[Set[Int], Sequence.List[Primitive]] = implicitly
```

### DynamoDB-style set operations

A DynamoDB grammar can encode the distinction between set types and list types exactly, without any additional runtime proof. We use `Sequence.Set` to narrow the grammar to sets-only operations:

```scala

type N  = Primitive.Int | Primitive.Long | Primitive.Float | Primitive.Double | Primitive.Short
type S  = Primitive.String
type NS = Sequence.Set[N | Wrapped[N]]
type SS = Sequence.Set[S | Wrapped[S]]

// addSet is only callable with a Set — List[Int] or Vector[Int] would fail at compile time
def addSet[A](set: scala.collection.immutable.Set[A])(implicit
  ev: Allows[scala.collection.immutable.Set[A], NS | SS]
): String = "ok"
```

## `IsType[A]`

`IsType[A]` is a nominal type predicate. It is satisfied only when the checked Scala type is exactly `A` (i.e. `checked =:= A`). It is most useful as an element constraint inside `Sequence` subtypes, where it aligns the element type of a collection with a polymorphic method type parameter.

The primary motivation (GitHub issue #1172) is DSL methods that must constrain both the container kind and the element type in a single `Allows` expression. Without `IsType`, a separate type class (like `Containable`) is needed to connect `A` in `contains[A]` to the element type of the collection. With `IsType`, the connection is expressed directly in the grammar.

To use `IsType[A]` with a polymorphic `A`, require `IsNominalType[A]` from `zio-blocks-typeid` at the call site. This ensures the macro always sees a concrete type when it evaluates `IsType[A]`:

```scala

// `To` must be a Set whose element type is exactly `A`.
// IsNominalType[A] ensures A is concrete at the call site — an unresolved
// type parameter would fail to produce IsNominalType and the call site
// would not compile.
def contains[To, From, A: IsNominalType](a: A)(implicit
  ev: Allows[To, Sequence.Set[IsType[A]]]
): Boolean = ev.ne(null)

// Compiles: Set[Int] satisfies Sequence.Set[IsType[Int]]
val r1: Boolean = contains[Set[Int], Nothing, Int](42)

// Compiles: Set[String] satisfies Sequence.Set[IsType[String]]
val r2: Boolean = contains[Set[String], Nothing, String]("hello")
```

A mismatch between the element type and `A` is a compile-time error:

```
[error] Shape violation at ...<element>: found Primitive(java.lang.String),
        required IsType[Int]
```

`IsType[A]` can also appear as a standalone constraint, or anywhere a grammar node is accepted:

```scala

// Int satisfies IsType[Int] exactly
val ev: Allows[Int, Allows.IsType[Int]] = implicitly

// List[String] satisfies Sequence[IsType[String]]
val ev2: Allows[List[String], Allows.Sequence[Allows.IsType[String]]] = implicitly
```

## The `Self` Grammar Node

`Self` refers back to the entire enclosing `Allows[A, S]` grammar. It allows the grammar to describe recursive data structures.

**Non-recursive types** satisfy `Self`-containing grammars without issue: if no field ever recurses back to the root type, the `Self` position is never reached, and the constraint is vacuously satisfied.

**Mutual recursion** between two or more distinct types is a compile-time error reported as:

```
[error] Mutually recursive types are not supported by Allows.
        Cycle: Forest -> Tree -> Forest
```

## `Wrapped[A]` and Newtypes

The `Wrapped[A]` node matches ZIO Prelude `Newtype` and `Subtype` wrappers. The underlying type must satisfy `A`. Here's an example:

```scala

// ZIO Prelude Newtype pattern:
object ProductCode extends Newtype[String]
type ProductCode = ProductCode.Type

given Schema[ProductCode] =
  Schema[String].transform(_.asInstanceOf[ProductCode], _.asInstanceOf[String])

// ProductCode satisfies Wrapped[Primitive] — its underlying String is Primitive
val ev: Allows[ProductCode, Wrapped[Primitive]] = implicitly
```

**Scala 3 opaque types** are resolved to their underlying type by the macro (they are transparent), so an opaque alias like this satisfies `Primitive` directly:

```scala
opaque type UserId = java.util.UUID
```

## Sealed Traits and Enums (Auto-Unwrap)

Sealed traits and enums are **automatically unwrapped** by the macro. Whenever a sealed type is encountered at any grammar check position, the macro recursively checks every case against the same grammar. This makes a `Variant` grammar node unnecessary.

```scala

sealed trait Shape
case class Circle(radius: Double)                    extends Shape
case class Rectangle(width: Double, height: Double)  extends Shape
case object Point                                    extends Shape

// No Variant node — Shape is auto-unwrapped, all cases checked against Record[Primitive]
val ev: Allows[Shape, Record[Primitive]] = implicitly
```

Auto-unwrap is recursive: if a case is itself a sealed trait, its cases are unwrapped too, to any depth.

Union branches (`A | B`) work naturally with auto-unwrap: unused branches are fine under `Allows` upper-bound semantics.

## Error Messages

When a type does not satisfy the grammar, the macro reports:

1. **The path** to the violating field: `Order.items.<element>`
2. **What was found**: `Record(OrderItem)`
3. **What was required**: `Primitive | Sequence[Primitive]`
4. **A hint** where applicable

Multiple violations are reported in a single compilation pass — the user sees all problems at once, for example:

```
[error] Schema shape violation at UserWithAddress.address: found Record(Address),
        required Primitive | Optional[Primitive] | Map[Primitive, Primitive]
[error]   Hint: Type 'Address' does not match any allowed shape
```

## Singleton / Zero-Field Records

`Record[A]` is vacuously true for case objects and zero-field records, since there are no fields to violate the constraint:

```scala

case object EmptyEvent
implicit val schema: Schema[EmptyEvent.type] = Schema.derived

val ev: Allows[EmptyEvent.type, Record[Primitive]] = implicitly  // vacuously true
```

## Runtime Cost

`Allows[A, S]` carries **zero runtime overhead**. The macro emits a reference to a single private singleton `Allows.instance` cast to the required type. There is no per-call-site allocation.

## Scala 2 vs Scala 3

| Feature | Scala 2 | Scala 3 |
|---|---|---|
| Union syntax | `` A `\|` B `` infix | `A \| B` native union type |
| Summon syntax | `implicitly[Allows[A, S]]` | `summon[Allows[A, S]]` or `implicitly` |
| Evidence parameter | `(implicit ev: Allows[A, S])` | `(using Allows[A, S])` |
| Opaque type detection | ZIO Prelude only | Scala 3 opaque types + ZIO Prelude + neotype |
| Derivation keyword | `Schema.derived` implicit | `Schema.derived` or `derives Schema` |

Both Scala versions produce the same macro behavior and the same error messages.

## Integration with Schema

`Allows` and `Schema` are complementary but independent:

- **`Schema[A]`** describes what an `A` looks like at runtime — how to serialize, deserialize, introspect, or transform it. It requires explicit derivation and handles the full type signature.
- **`Allows[A, S]`** describes what an `A` *may* look like at compile time — a structural grammar that `A` must satisfy. It requires no schema and uses only the Scala type system.

You can use `Allows` **without** `Schema`:

```scala

// Pure shape constraint, no Schema required
def writeCsv[A](rows: Seq[A])(using Allows[A, Record[Primitive | Optional[Primitive]]]): Unit = ???
```

Or combine them when runtime encoding **and** shape validation are both needed:

```scala

// Shape constraint + runtime encoding
def writeCsv[A: Schema](rows: Seq[A])(using
  Allows[A, Record[Primitive | Optional[Primitive]]]
): Unit = ???
```

When combined, `Allows` enforces the structural guarantee that `Schema` can use — for example, a CSV serializer can assume that every field is a primitive or optional primitive and skip defensive type checks.

See [Schema](./schema.md) for more on runtime encoding and decoding with schemas.

## Running the Examples

All code from this guide is available as runnable examples in the `schema-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

**CSV serializer with flat record compile-time constraints**
([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/comptime/AllowsCsvExample.scala))

```bash
sbt "schema-examples/runMain comptime.AllowsCsvExample"
```

```scala title="schema-examples/src/main/scala/comptime/AllowsCsvExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package comptime

// ---------------------------------------------------------------------------
// CSV serializer example using Allows[A, S] compile-time shape constraints
//
// A CSV row is a flat record: every field must be a primitive scalar or an
// optional primitive (for nullable columns). Nested records, sequences, and
// maps are all rejected at compile time.
// ---------------------------------------------------------------------------

// Compatible: flat record of primitives and optional primitives
case class Employee(name: String, department: String, salary: BigDecimal, active: Boolean)
object Employee { implicit val schema: Schema[Employee] = Schema.derived }

case class SensorReading(sensorId: String, timestamp: Long, value: Double, unit: Option[String])
object SensorReading { implicit val schema: Schema[SensorReading] = Schema.derived }

object CsvSerializer {

  type FlatRow = Primitive | AOptional[Primitive]

  /** Serialize a sequence of flat records to CSV format. */
  def toCsv[A](rows: Seq[A])(implicit schema: Schema[A], ev: Allows[A, Record[FlatRow]]): String = {
    val reflect = schema.reflect.asRecord.get
    val header  = reflect.fields.map(_.name).mkString(",")
    val lines   = rows.map { row =>
      val dv = schema.toDynamicValue(row)
      dv match {
        case DynamicValue.Record(fields) =>
          fields.map { case (_, v) => csvEscape(dvToString(v)) }.mkString(",")
        case _ => ""
      }
    }
    (header +: lines).mkString("\n")
  }

  private def dvToString(dv: DynamicValue): String = dv match {
    case DynamicValue.Primitive(PrimitiveValue.String(s))     => s
    case DynamicValue.Primitive(PrimitiveValue.Boolean(b))    => b.toString
    case DynamicValue.Primitive(PrimitiveValue.Int(n))        => n.toString
    case DynamicValue.Primitive(PrimitiveValue.Long(n))       => n.toString
    case DynamicValue.Primitive(PrimitiveValue.Double(n))     => n.toString
    case DynamicValue.Primitive(PrimitiveValue.Float(n))      => n.toString
    case DynamicValue.Primitive(PrimitiveValue.BigDecimal(n)) => n.toString
    case DynamicValue.Primitive(v)                            => v.toString
    case DynamicValue.Null                                    => ""
    case DynamicValue.Variant(tag, inner) if tag == "Some"    => dvToString(inner)
    case DynamicValue.Variant(tag, _) if tag == "None"        => ""
    case DynamicValue.Record(fields)                          =>
      fields.headOption.map { case (_, v) => dvToString(v) }.getOrElse("")
    case other => other.toString
  }

  private def csvEscape(s: String): String =
    if (s.contains(",") || s.contains("\"") || s.contains("\n"))
      "\"" + s.replace("\"", "\"\"") + "\""
    else s
}

// ---------------------------------------------------------------------------
// Demonstration
// ---------------------------------------------------------------------------

object AllowsCsvExample extends App {

  // Flat records of primitives — compiles fine
  val employees = Seq(
    Employee("Alice", "Engineering", BigDecimal("120000.00"), true),
    Employee("Bob", "Marketing", BigDecimal("95000.50"), true),
    Employee("Carol", "Engineering", BigDecimal("115000.00"), false)
  )

  // CSV output for a flat record of primitives
  show(CsvSerializer.toCsv(employees))

  // Flat record with optional fields — also compiles
  val readings = Seq(
    SensorReading("temp-01", 1709712000L, 23.5, Some("celsius")),
    SensorReading("temp-02", 1709712060L, 72.1, None)
  )

  // Optional fields become empty CSV cells when None
  show(CsvSerializer.toCsv(readings))

  // The following would NOT compile — uncomment to see the error:
  //
  // case class Nested(name: String, address: Address)
  // object Nested { implicit val schema: Schema[Nested] = Schema.derived }
  // CsvSerializer.toCsv(Seq(Nested("Alice", Address("1 Main St", "NY", "10001"))))
  //   [error] Schema shape violation at Nested.address: found Record(Address),
  //           required Primitive | Optional[Primitive]
}
```

**Event bus with sealed trait auto-unwrap and nested hierarchies**
([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/comptime/AllowsEventBusExample.scala))

```bash
sbt "schema-examples/runMain comptime.AllowsEventBusExample"
```

```scala title="schema-examples/src/main/scala/comptime/AllowsEventBusExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package comptime

// ---------------------------------------------------------------------------
// Event bus / message broker example using Allows[A, S]
//
// Published events are typically sealed traits of flat record cases. Sealed
// traits are automatically unwrapped by the Allows macro — each case is
// checked individually against the grammar. No Variant node is needed.
//
// This example also shows nested sealed traits (auto-unwrap is recursive).
// ---------------------------------------------------------------------------

// Domain events — a sealed trait hierarchy
sealed trait AccountEvent
case class AccountOpened(accountId: String, owner: String, initialBalance: BigDecimal) extends AccountEvent
case class FundsDeposited(accountId: String, amount: BigDecimal)                       extends AccountEvent
case class FundsWithdrawn(accountId: String, amount: BigDecimal)                       extends AccountEvent
case class AccountClosed(accountId: String, reason: Option[String])                    extends AccountEvent
object AccountEvent { implicit val schema: Schema[AccountEvent] = Schema.derived }

// Nested sealed trait — InventoryEvent has a sub-hierarchy
sealed trait InventoryEvent
case class ItemAdded(sku: String, quantity: Int)   extends InventoryEvent
case class ItemRemoved(sku: String, quantity: Int) extends InventoryEvent

sealed trait InventoryAlert                      extends InventoryEvent
case class LowStock(sku: String, remaining: Int) extends InventoryAlert
case class OutOfStock(sku: String)               extends InventoryAlert

object InventoryEvent { implicit val schema: Schema[InventoryEvent] = Schema.derived }

// Event with sequence fields (e.g. tags or batch items)
sealed trait BatchEvent
case class BatchImport(batchId: String, itemIds: List[String]) extends BatchEvent
case class BatchComplete(batchId: String, count: Int)          extends BatchEvent
object BatchEvent { implicit val schema: Schema[BatchEvent] = Schema.derived }

object EventBus {

  type EventShape = Primitive | AOptional[Primitive]

  /**
   * Publish a domain event. All cases of the sealed trait must be flat records.
   */
  def publish[A](event: A)(implicit schema: Schema[A], ev: Allows[A, Record[EventShape]]): String = {
    val dv                  = schema.toDynamicValue(event)
    val (typeName, payload) = dv match {
      case DynamicValue.Variant(name, inner) => (name, inner.toJson.toString)
      case _                                 => (schema.reflect.typeId.name, dv.toJson.toString)
    }
    s"PUBLISH topic=${schema.reflect.typeId.name} type=$typeName payload=$payload"
  }

  /**
   * Publish events that may contain sequence fields (e.g. batch operations).
   */
  def publishBatch[A](event: A)(implicit
    schema: Schema[A],
    ev: Allows[A, Record[Primitive | Sequence[Primitive]]]
  ): String = {
    val dv                  = schema.toDynamicValue(event)
    val (typeName, payload) = dv match {
      case DynamicValue.Variant(name, inner) => (name, inner.toJson.toString)
      case _                                 => (schema.reflect.typeId.name, dv.toJson.toString)
    }
    s"PUBLISH topic=${schema.reflect.typeId.name} type=$typeName payload=$payload"
  }
}

// ---------------------------------------------------------------------------
// Demonstration
// ---------------------------------------------------------------------------

object AllowsEventBusExample extends App {

  // Flat sealed trait — all cases are records of primitives/optionals
  show(EventBus.publish[AccountEvent](AccountOpened("acc-001", "Alice", BigDecimal("1000.00"))))
  show(EventBus.publish[AccountEvent](FundsDeposited("acc-001", BigDecimal("500.00"))))
  show(EventBus.publish[AccountEvent](AccountClosed("acc-001", Some("customer request"))))

  // Nested sealed trait — auto-unwrap is recursive
  // InventoryAlert extends InventoryEvent, both are unwrapped
  show(EventBus.publish[InventoryEvent](ItemAdded("SKU-100", 50)))
  show(EventBus.publish[InventoryEvent](LowStock("SKU-100", 3)))
  show(EventBus.publish[InventoryEvent](OutOfStock("SKU-100")))

  // Events with sequence fields use a wider grammar
  show(EventBus.publishBatch[BatchEvent](BatchImport("batch-42", List("item-1", "item-2", "item-3"))))
  show(EventBus.publishBatch[BatchEvent](BatchComplete("batch-42", 3)))
}
```

**GraphQL / tree structures using Self for recursive grammars**
([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/comptime/AllowsGraphQLTreeExample.scala))

```bash
sbt "schema-examples/runMain comptime.AllowsGraphQLTreeExample"
```

```scala title="schema-examples/src/main/scala/comptime/AllowsGraphQLTreeExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package comptime

// ---------------------------------------------------------------------------
// GraphQL / tree structure example using Self for recursive grammars
//
// Self refers back to the entire enclosing Allows[A, S] grammar, allowing
// the constraint to describe recursive data structures like trees, linked
// lists, and nested menus.
//
// Non-recursive types also satisfy Self-containing grammars — the Self
// position is never reached, so the constraint is vacuously satisfied.
// ---------------------------------------------------------------------------

// Recursive tree: children reference the same type
case class TreeNode(value: Int, children: List[TreeNode])
object TreeNode { implicit val schema: Schema[TreeNode] = Schema.derived }

// Recursive category hierarchy (common in e-commerce, CMS, etc.)
case class NavCategory(name: String, slug: String, subcategories: List[NavCategory])
object NavCategory { implicit val schema: Schema[NavCategory] = Schema.derived }

// Linked list via Optional[Self]
case class Chain(label: String, next: Option[Chain])
object Chain { implicit val schema: Schema[Chain] = Schema.derived }

// Non-recursive type — satisfies Self-containing grammars vacuously
case class FlatNode(id: Int, label: String)
object FlatNode { implicit val schema: Schema[FlatNode] = Schema.derived }

object GraphQL {

  type TreeShape = Primitive | Sequence[ASelf] | AOptional[ASelf]

  /** Generate a simplified GraphQL type definition for a recursive type. */
  def graphqlType[A](implicit schema: Schema[A], ev: Allows[A, Record[TreeShape]]): String = {
    val reflect = schema.reflect.asRecord.get
    val fields  = reflect.fields.map { f =>
      s"  ${f.name}: ${gqlType(resolve(f.value), schema.reflect.typeId.name)}"
    }
    s"type ${schema.reflect.typeId.name} {\n${fields.mkString("\n")}\n}"
  }

  /** Unwrap Deferred to get the actual Reflect node. */
  private def resolve(r: Reflect.Bound[_]): Reflect.Bound[_] = r match {
    case d: Reflect.Deferred[_, _] => resolve(d.value.asInstanceOf[Reflect.Bound[_]])
    case other                     => other
  }

  private def gqlType(r: Reflect.Bound[_], selfName: String): String = r match {
    case _: Reflect.Sequence[_, _, _] => s"[$selfName]"
    case p: Reflect.Primitive[_, _]   =>
      p.primitiveType match {
        case PrimitiveType.Int(_)     => "Int"
        case PrimitiveType.Long(_)    => "Int"
        case PrimitiveType.Float(_)   => "Float"
        case PrimitiveType.Double(_)  => "Float"
        case PrimitiveType.String(_)  => "String"
        case PrimitiveType.Boolean(_) => "Boolean"
        case _                        => "String"
      }
    case _ => selfName
  }
}

// ---------------------------------------------------------------------------
// Demonstration
// ---------------------------------------------------------------------------

object AllowsGraphQLTreeExample extends App {

  // Recursive tree with Sequence[Self]
  show(GraphQL.graphqlType[TreeNode])

  // Recursive categories — same grammar, different domain
  show(GraphQL.graphqlType[NavCategory])

  // Linked list via Optional[Self]
  show(GraphQL.graphqlType[Chain])

  // Non-recursive type also satisfies the grammar (vacuously — Self is never reached)
  show(GraphQL.graphqlType[FlatNode])

  // Show that recursive data actually works at runtime
  val tree = TreeNode(
    1,
    List(
      TreeNode(2, List(TreeNode(4, Nil), TreeNode(5, Nil))),
      TreeNode(3, Nil)
    )
  )
  show(Schema[TreeNode].toDynamicValue(tree).toJson.toString)

  val nav = NavCategory(
    "Electronics",
    "electronics",
    List(
      NavCategory("Phones", "phones", Nil),
      NavCategory(
        "Laptops",
        "laptops",
        List(
          NavCategory("Gaming", "gaming", Nil)
        )
      )
    )
  )
  show(Schema[NavCategory].toDynamicValue(nav).toJson.toString)
}
```

**Sealed trait auto-unwrap with nested hierarchies and case objects**
([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/comptime/AllowsSealedTraitExample.scala))

```bash
sbt "schema-examples/runMain comptime.AllowsSealedTraitExample"
```

```scala title="schema-examples/src/main/scala/comptime/AllowsSealedTraitExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package comptime

// ---------------------------------------------------------------------------
// Sealed trait auto-unwrap example
//
// Sealed traits and enums are automatically unwrapped by the Allows macro.
// Each case is checked individually against the grammar — no Variant node
// is needed.
//
// Auto-unwrap is recursive: if a case is itself a sealed trait, its cases
// are unwrapped too, to any depth.
//
// Zero-field records (case objects) are vacuously true for any Record[A]
// constraint.
// ---------------------------------------------------------------------------

// Simple sealed trait with case classes and a case object
sealed trait Shape
case class Circle(radius: Double)                   extends Shape
case class Rectangle(width: Double, height: Double) extends Shape
case object Point                                   extends Shape
object Shape { implicit val schema: Schema[Shape] = Schema.derived }

// Nested sealed trait hierarchy — two levels deep
sealed trait Expr
sealed trait BinaryOp                            extends Expr
case class Add(left: Double, right: Double)      extends BinaryOp
case class Multiply(left: Double, right: Double) extends BinaryOp
case class Literal(value: Double)                extends Expr
case object Zero                                 extends Expr
object Expr { implicit val schema: Schema[Expr] = Schema.derived }

// All-singleton enum (all case objects)
sealed trait Color
case object Red   extends Color
case object Green extends Color
case object Blue  extends Color
object Color { implicit val schema: Schema[Color] = Schema.derived }

object SealedTraitValidator {

  /** Validate that a value's type has a flat record structure. */
  def validate[A](value: A)(implicit schema: Schema[A], ev: Allows[A, Record[Primitive]]): String = {
    val dv = schema.toDynamicValue(value)
    dv match {
      case DynamicValue.Variant(caseName, inner) =>
        s"Valid variant case '$caseName': ${inner.toJson}"
      case DynamicValue.Record(fields) =>
        s"Valid record with ${fields.size} field(s): ${fields.map(_._1).mkString(", ")}"
      case _ =>
        s"Valid: ${dv.toJson}"
    }
  }
}

// ---------------------------------------------------------------------------
// Demonstration
// ---------------------------------------------------------------------------

object AllowsSealedTraitExample extends App {

  // Simple sealed trait — all cases checked against Record[Primitive]
  // Circle: Record(radius: Double) — satisfies Record[Primitive]
  // Rectangle: Record(width: Double, height: Double) — satisfies Record[Primitive]
  // Point: zero-field case object — vacuously true
  show(SealedTraitValidator.validate[Shape](Circle(3.14)))
  show(SealedTraitValidator.validate[Shape](Rectangle(4.0, 5.0)))
  show(SealedTraitValidator.validate[Shape](Point))

  // Nested sealed trait — auto-unwrap is recursive
  // BinaryOp is itself sealed with Add and Multiply
  // All leaf cases have only Double fields — satisfies Record[Primitive]
  show(SealedTraitValidator.validate[Expr](Add(1.0, 2.0)))
  show(SealedTraitValidator.validate[Expr](Multiply(3.0, 4.0)))
  show(SealedTraitValidator.validate[Expr](Literal(42.0)))
  show(SealedTraitValidator.validate[Expr](Zero))

  // All-singleton enum — every case is a zero-field record (vacuously true)
  show(SealedTraitValidator.validate[Color](Red))
  show(SealedTraitValidator.validate[Color](Green))
  show(SealedTraitValidator.validate[Color](Blue))
}
```

**RDBMS library with CREATE TABLE and INSERT using flat record constraints** (compile-only)
([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/comptime/RdbmsExample.scala))

Demonstrates how Allows constraints are verified at compile time — the code below shows valid examples that compile successfully, and includes comments showing which patterns would be rejected:

```scala title="schema-examples/src/main/scala/comptime/RdbmsExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package comptime

// ---------------------------------------------------------------------------
// Realistic RDBMS example using Allows[A, S] compile-time shape constraints
//
// Demonstrates how a library can require that user-supplied types have a
// structure compatible with what a relational database can represent:
//   - Flat records of primitives, optional primitives, or primitive-valued maps
//   - Top-level variants (enum tables) whose cases are flat records
//
// Incompatible types (nested records, sequences of records, etc.) are
// rejected at the call site with a precise compile-time error message.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Domain types — vary from compatible to incompatible
// ---------------------------------------------------------------------------

// Compatible: flat record of primitives and optional primitives
case class UserRow(
  id: java.util.UUID,
  name: String,
  email: Option[String],
  age: Int,
  active: Boolean
)
object UserRow {
  implicit val schema: Schema[UserRow] = Schema.derived
}

// Compatible: flat record with a string-keyed map column (stored as JSON/JSONB)
case class ProductRow(
  id: java.util.UUID,
  name: String,
  price: BigDecimal,
  attributes: scala.collection.immutable.Map[String, String]
)
object ProductRow {
  implicit val schema: Schema[ProductRow] = Schema.derived
}

// Compatible: event table — a variant (sealed trait) of flat record cases
sealed trait DomainEvent
case class UserCreated(id: java.util.UUID, name: String, email: String)               extends DomainEvent
case class UserDeleted(id: java.util.UUID)                                            extends DomainEvent
case class OrderPlaced(id: java.util.UUID, userId: java.util.UUID, total: BigDecimal) extends DomainEvent
object DomainEvent {
  implicit val schema: Schema[DomainEvent] = Schema.derived
}

// Incompatible: contains a nested record (Address is not a primitive)
case class Address(street: String, city: String, zip: String)
object Address { implicit val schema: Schema[Address] = Schema.derived }

case class UserWithAddress(
  id: java.util.UUID,
  name: String,
  address: Address // ← incompatible: nested record
)
object UserWithAddress {
  implicit val schema: Schema[UserWithAddress] = Schema.derived
}

// ---------------------------------------------------------------------------
// Simulated RDBMS library API
//
// The Allows constraint is checked at the CALL SITE — the library author
// writes these signatures once. Users get a compile-time error if their type
// doesn't match, with a message pointing to the exact violating field.
// ---------------------------------------------------------------------------

object Rdbms {

  // A flat record row: primitives, optional primitives, or string-keyed maps
  type FlatRow = Primitive | AOptional[Primitive] | AMap[Primitive, Primitive]

  /** Generate a CREATE TABLE DDL statement for a flat record type. */
  def createTable[A](implicit
    schema: Schema[A],
    ev: Allows[A, Record[FlatRow]]
  ): String = {
    val fields = schema.reflect.asRecord.get.fields
    val cols   = fields.map { f =>
      val tpe = sqlType(f.value)
      s"  ${f.name} $tpe"
    }
    s"CREATE TABLE ${tableName(schema)} (\n${cols.mkString(",\n")}\n)"
  }

  /** Generate an INSERT statement for a single flat record row. */
  def insert[A](value: A)(implicit
    schema: Schema[A],
    ev: Allows[A, Record[FlatRow]]
  ): String = {
    val dv = schema.toDynamicValue(value)
    dv match {
      case DynamicValue.Record(fields) =>
        val cols = fields.map(_._1).mkString(", ")
        val vals = fields.map { case (_, v) => sqlLiteralDv(v) }.mkString(", ")
        s"INSERT INTO ${tableName(schema)} ($cols) VALUES ($vals)"
      case _ => s"INSERT INTO ${tableName(schema)} VALUES (?)"
    }
  }

  /**
   * Insert an event into an event-sourcing table.
   *
   * The type must be a sealed trait / enum whose cases are flat records. No
   * explicit Variant node is needed — sealed traits are auto-unwrapped by the
   * macro.
   */
  def insertEvent[A](event: A)(implicit
    schema: Schema[A],
    ev: Allows[A, Record[FlatRow]]
  ): String = {
    val typeName = schema.toDynamicValue(event) match {
      case DynamicValue.Variant(caseName, _) => caseName
      case _                                 => schema.reflect.typeId.name
    }
    val payload = schema.toDynamicValue(event).toJson.toString
    s"INSERT INTO events (type, payload) VALUES ('$typeName', '$payload')"
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private def tableName[A](schema: Schema[A]): String =
    schema.reflect.modifiers.collectFirst {
      case Modifier.config(k, v) if k == "sql.table_name" => v
    }.getOrElse(schema.reflect.typeId.name.toLowerCase + "s")

  private def sqlLiteralDv(dv: DynamicValue): String = dv match {
    case DynamicValue.Primitive(PrimitiveValue.String(s))  => s"'${s.replace("'", "''")}'"
    case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) => if (b) "TRUE" else "FALSE"
    case DynamicValue.Primitive(v)                         => v.toString
    case DynamicValue.Null                                 => "NULL"
    case other                                             => s"'${other.toString.replace("'", "''")}'"
  }

  private def sqlType(reflect: Reflect.Bound[_]): String = reflect match {
    case p: Reflect.Primitive[_, _] =>
      p.primitiveType match {
        case PrimitiveType.Int(_)           => "INTEGER"
        case PrimitiveType.Long(_)          => "BIGINT"
        case PrimitiveType.String(_)        => "TEXT"
        case PrimitiveType.Boolean(_)       => "BOOLEAN"
        case PrimitiveType.Double(_)        => "DOUBLE PRECISION"
        case PrimitiveType.Float(_)         => "REAL"
        case PrimitiveType.BigDecimal(_)    => "NUMERIC"
        case PrimitiveType.UUID(_)          => "UUID"
        case PrimitiveType.Instant(_)       => "TIMESTAMPTZ"
        case PrimitiveType.LocalDate(_)     => "DATE"
        case PrimitiveType.LocalDateTime(_) => "TIMESTAMP"
        case _                              => "TEXT"
      }
    case _: Reflect.Map[_, _, _, _]   => "JSONB"
    case _: Reflect.Sequence[_, _, _] => "TEXT[]"
    case _                            => "TEXT"
  }
}

// ---------------------------------------------------------------------------
// Demonstration — these all compile
// ---------------------------------------------------------------------------

object RdbmsDemo {

  // Flat rows compile fine
  val createUser: String    = Rdbms.createTable[UserRow]
  val createProduct: String = Rdbms.createTable[ProductRow]
  val insertUser: String    = Rdbms.insert(UserRow(new java.util.UUID(0, 0), "Alice", Some("a@b.com"), 30, true))
  val insertEvent: String   = Rdbms.insertEvent[DomainEvent](UserCreated(new java.util.UUID(0, 0), "Alice", "a@b.com"))

  // The following would NOT compile — uncomment to see the error:
  //
  // val bad = Rdbms.createTable[UserWithAddress]
  //   [error] Schema shape violation at UserWithAddress.address: found Record(Address), required
  //           Primitive | Optional[Primitive] | Map[Primitive, Primitive]
}
```

**JSON document store with specific primitives and recursive Self grammar** (compile-only)
([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/comptime/DocumentStoreExample.scala))

Demonstrates how Allows enforces recursive schema constraints at compile time:

```scala title="schema-examples/src/main/scala/comptime/DocumentStoreExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package comptime

// ---------------------------------------------------------------------------
// Realistic JSON document-store example using Allows[A, S]
//
// JSON has a limited primitive value set:
//   - null    → Unit / Option
//   - boolean → Boolean
//   - number  → Int, Long, Float, Double, BigDecimal, BigInt
//   - string  → String
//
// Notably absent from JSON: Char, Byte, Short, UUID, Currency,
// and ALL java.time.* types. A JSON library should use SPECIFIC primitive
// nodes to reject non-JSON scalars at the call site.
//
// The grammar is simply:
//   type Json = Record[JsonPrimitive | Self] | Sequence[JsonPrimitive | Self]
//
// Self handles all nesting: a field may be a primitive or another Json value.
// Sequences of primitives (List[String]) and sequences of records
// (List[Author]) both satisfy Sequence[JsonPrimitive | Self].
// Optional fields (Option[X]) satisfy Record[...] because Option is recognised
// by the Optional grammar node which falls through to the field constraint.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Domain types
// ---------------------------------------------------------------------------

// Compatible: flat document
case class Author(name: String, email: String)
object Author { implicit val schema: Schema[Author] = Schema.derived }

// Compatible: document with nested documents and sequences
case class BookChapter(title: String, wordCount: Int)
object BookChapter { implicit val schema: Schema[BookChapter] = Schema.derived }

case class Book(
  title: String,
  author: Author,              // nested record — satisfied via Self
  chapters: List[BookChapter], // sequence of records — satisfied via Sequence[Self]
  tags: List[String],          // sequence of primitives — satisfied via Sequence[JsonPrimitive]
  rating: Option[Double]       // optional primitive — satisfied via Optional[JsonPrimitive | Self]
)
object Book { implicit val schema: Schema[Book] = Schema.derived }

// Compatible: recursive document
case class Category(name: String, subcategories: List[Category])
object Category { implicit val schema: Schema[Category] = Schema.derived }

// Compatible: variant of search results (for indexing)
sealed trait SearchResult
case class BookResult(title: String, score: Double)   extends SearchResult
case class AuthorResult(name: String, bookCount: Int) extends SearchResult
object SearchResult { implicit val schema: Schema[SearchResult] = Schema.derived }

// INCOMPATIBLE: UUID is not a JSON-native scalar
case class WithUUID(id: java.util.UUID, name: String)
object WithUUID { implicit val schema: Schema[WithUUID] = Schema.derived }

// INCOMPATIBLE: Instant is not a JSON-native scalar
case class WithTimestamp(name: String, createdAt: java.time.Instant)
object WithTimestamp { implicit val schema: Schema[WithTimestamp] = Schema.derived }

// ---------------------------------------------------------------------------
// Document store library API
// ---------------------------------------------------------------------------

object DocumentStore {

  /**
   * JSON-representable scalar types.
   *
   * Excludes Char, Byte, Short, UUID, Currency, and all java.time.* types —
   * none of these have a native JSON encoding. Authors who need java.time
   * values in JSON should store them as Primitive.String (ISO-8601 etc.).
   */
  type JsonPrimitive =
    Allows.Primitive.Boolean | Allows.Primitive.Int | Allows.Primitive.Long | Allows.Primitive.Float |
      Allows.Primitive.Double | Allows.Primitive.String | Allows.Primitive.BigDecimal | Allows.Primitive.BigInt |
      Allows.Primitive.Unit

  /**
   * A JSON value is either a JSON object (Record) or a JSON array (Sequence).
   * Self recurses back to this same grammar, so nesting works at any depth.
   * Optional covers nullable fields (JSON null / absent key).
   */
  type Json = Record[JsonPrimitive | AOptional[JsonPrimitive | ASelf] | ASelf] | Sequence[JsonPrimitive | ASelf]

  /**
   * Encode a value to its JSON string representation.
   *
   * Accepts both JSON objects (records) and JSON arrays (sequences) at the top
   * level. Fields may be primitives, nested documents, sequences, or optionals
   * — all handled via Self and the JsonPrimitive constraint. Types containing
   * UUID, Instant, Char etc. are rejected at compile time.
   */
  def toJson[A: Schema](doc: A)(implicit ev: Allows[A, Json]): String =
    Schema[A].toDynamicValue(doc).toJson.toString

  /** Serialize to DynamicValue for further processing. */
  def serialize[A: Schema](doc: A)(implicit ev: Allows[A, Json]): DynamicValue =
    Schema[A].toDynamicValue(doc)

  /**
   * Index a search result. The type must be a sealed trait of flat JSON
   * records. No explicit Variant node needed — sealed traits are
   * auto-unwrapped.
   */
  def index[A: Schema](result: A)(implicit
    ev: Allows[A, Record[JsonPrimitive | AOptional[JsonPrimitive | ASelf] | Sequence[JsonPrimitive | ASelf]]]
  ): String = {
    val typeName = Schema[A].toDynamicValue(result) match {
      case DynamicValue.Variant(name, _) => name
      case _                             => Schema[A].reflect.typeId.name
    }
    val payload = Schema[A].toDynamicValue(result).toJson.toString
    s"""{"_type":"$typeName","_doc":$payload}"""
  }
}

// ---------------------------------------------------------------------------
// Demonstration
// ---------------------------------------------------------------------------

object DocumentStoreDemo {

  // JSON objects compile fine
  val bookJson: String = DocumentStore.toJson(
    Book(
      "ZIO Blocks",
      Author("John", "john@example.com"),
      List(BookChapter("Intro", 1000)),
      List("scala", "zio"),
      Some(4.9)
    )
  )

  val categoryJson: String = DocumentStore.toJson(
    Category("Programming", List(Category("Scala", Nil)))
  )

  // JSON arrays also satisfy Json (top-level Sequence)
  val tagListJson: String    = DocumentStore.toJson(List("scala", "zio", "functional"))
  val authorListJson: String = DocumentStore.toJson(List(Author("Alice", "a@b.com"), Author("Bob", "b@b.com")))

  val indexed: String = DocumentStore.index[SearchResult](BookResult("ZIO Blocks", 0.99))

  // The following would NOT compile — uncomment to see the errors:
  //
  // DocumentStore.toJson(WithUUID(new java.util.UUID(0, 0), "Alice"))
  //   [error] Schema shape violation at WithUUID.id: found Primitive(java.util.UUID),
  //           required JsonPrimitive (Boolean | Int | Long | Float | Double | String | ...)
  //   UUID is not a JSON-native type — encode it as Primitive.String.
  //
  // DocumentStore.toJson(WithTimestamp("Alice", java.time.Instant.EPOCH))
  //   [error] Schema shape violation at WithTimestamp.createdAt: found Primitive(java.time.Instant)
  //   Instant is not a JSON-native type — encode it as Primitive.String (ISO-8601).
}
```
