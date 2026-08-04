# SchemaExpr

> `SchemaExpr[A, B]` is a **schema-aware expression** that computes a result of type `B` from an input value of type `A`. It is invariant in both type parameters. The input type `A` must be fully described by a [`Schema`](./schema.md), and the expression is built from [optics](./optics.md), literal values, and operators. The fundamental operations are `eval` and `evalDynamic`.

`SchemaExpr[A, B]` is a **schema-aware expression** that computes a result of type `B` from an input value of type `A`. It is invariant in both type parameters. The input type `A` must be fully described by a [`Schema`](./schema.md), and the expression is built from [optics](./optics.md), literal values, and operators. The fundamental operations are `eval` and `evalDynamic`.

At runtime, `SchemaExpr` is a typed wrapper around `DynamicSchemaExpr`. The typed layer carries the input and output schemas, while the dynamic layer stores the serializable AST. This split is why you will sometimes see both `.dynamic` and `DynamicSchemaExpr` in advanced examples: `SchemaExpr` is the public typed API, and `DynamicSchemaExpr` is the untyped transport/runtime form underneath it.

`SchemaExpr`:
- represents expressions as a reified AST, enabling introspection and serialization
- supports relational (`===`, `>`, `<`, `>=`, `<=`, `!=`), logical (`&&`, `||`, `!`), arithmetic (`+`, `-`, `*`), and string (`concat`, `matches`, `length`) operations
- evaluates to `Either[OpticCheck, Seq[B]]`, handling failures and multi-valued results from traversals
- preserves both input and output schemas at the type level

```scala
final case class SchemaExpr[A, B](
  dynamic: DynamicSchemaExpr,
  inputSchema: Schema[A],
  outputSchema: Schema[B]
) {
  def eval(input: A): Either[OpticCheck, Seq[B]]
  def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]]
}
```

:::tip
For practical walkthroughs of building with `SchemaExpr`, see [Query DSL Part 1: Expressions](../../guides/query-dsl-reified-optics.md), [Part 2: SQL Generation](../../guides/query-dsl-sql.md), and [Part 3: Extending the Expression Language](../../guides/query-dsl-extending.md).
:::

## Motivation

When working with schema-described data, we often need to express computations over that data — comparisons, arithmetic, string operations — in a way that can be both **evaluated at runtime** and **inspected as data**. This is essential for:

1. **Persistence DSLs** — Third-party libraries can translate `SchemaExpr` trees into SQL `WHERE` clauses, NoSQL filters, or other query languages, because the expression structure is reified (not opaque functions).
2. **Validation** — Express constraints like "age must be greater than 18" or "name must match a pattern" as composable, inspectable expressions.
3. **Data Migration** — Define transformation rules that can be analyzed and optimized before execution.

```text
                              SchemaExpr[A, B]
                                     │
          ┌──────────┬───────────────┼───────────────┬──────────────────┐
          │          │               │               │                  │
     Leaf Nodes   Unary Ops     Binary Ops    StringRegexMatch   StringLength
          │          │               │
    ┌─────┴─────┐   Not    ┌────────┼────────┐
  Literal    Optic       Relational Logical  Arithmetic
                                             StringConcat
```

The typical way to build expressions is through the operator syntax on [Optic](./optics.md) values:

```scala
import zio.blocks.schema._

case class Person(name: String, age: Int)

object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived

  val name: Lens[Person, String] = $(_.name)
  val age: Lens[Person, Int]     = $(_.age)
}

// Build expressions using optic operators
val isAdult: SchemaExpr[Person, Boolean]    = Person.age >= 18
val isAlice: SchemaExpr[Person, Boolean]    = Person.name === "Alice"
val combined: SchemaExpr[Person, Boolean]   = isAdult && isAlice

// Evaluate against a value
val alice = Person("Alice", 30)
val result: Either[OpticCheck, Seq[Boolean]] = combined.eval(alice)
// Right(Seq(true))
```

## Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "0.0.51"
```

For cross-platform (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-schema" % "0.0.51"
```

Supported Scala versions: 2.13.x and 3.x.

## Creating Instances

`SchemaExpr` instances are typically created through **operator syntax on optics** rather than by constructing AST nodes directly. Each operator on `Optic[S, A]` returns a `SchemaExpr[S, B]`.

There are three common construction styles:

- optic/operator syntax such as `Person.age >= 18`
- literal expressions such as `SchemaExpr.literal[Person, Int](18)`
- advanced direct construction when you intentionally need to work with the underlying dynamic AST

### Via Relational Operators on Optics

The comparison operators `===`, `>`, `>=`, `<`, `<=`, and `!=` on `Optic[S, A]` create `SchemaExpr.Relational` nodes. Each operator has two overloads — one comparing against a literal value, and one comparing against another optic:

```scala
import zio.blocks.schema._

case class Product(name: String, price: Double, stock: Int)

object Product extends CompanionOptics[Product] {
  implicit val schema: Schema[Product] = Schema.derived

  val name: Lens[Product, String]  = $(_.name)
  val price: Lens[Product, Double] = $(_.price)
  val stock: Lens[Product, Int]    = $(_.stock)
}

// Compare optic against a literal value
val expensive: SchemaExpr[Product, Boolean] = Product.price > 100.0
val inStock: SchemaExpr[Product, Boolean]   = Product.stock > 0
val named: SchemaExpr[Product, Boolean]     = Product.name === "Widget"

// Compare optic against another optic
// (e.g., stock > price — contrived, but shows the syntax)
```

### Via Logical Operators on Optics

The `&&`, `||`, and `!` (unary) operators on boolean-focused optics create `SchemaExpr.Logical` and `SchemaExpr.Not` nodes:

```scala
import zio.blocks.schema._

case class User(name: String, active: Boolean, verified: Boolean)

object User extends CompanionOptics[User] {
  implicit val schema: Schema[User] = Schema.derived

  val name: Lens[User, String]       = $(_.name)
  val active: Lens[User, Boolean]    = $(_.active)
  val verified: Lens[User, Boolean]  = $(_.verified)
}

// Logical operators on boolean optics
val activeAndVerified: SchemaExpr[User, Boolean] = User.active && User.verified
val eitherOne: SchemaExpr[User, Boolean]         = User.active || User.verified
val notActive: SchemaExpr[User, Boolean]         = !User.active
```

### Via Arithmetic Operators on Optics

The `+`, `-`, and `*` operators on numeric-focused optics create `SchemaExpr.Arithmetic` nodes. These require an implicit `IsNumeric[A]` instance, which is provided for `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigInt`, and `BigDecimal`:

```scala
import zio.blocks.schema._

case class Order(quantity: Int, unitPrice: Double)

object Order extends CompanionOptics[Order] {
  implicit val schema: Schema[Order] = Schema.derived

  val quantity : Lens[Order, Int]     = $(_.quantity)
  val unitPrice: Lens[Order, Double]  = $(_.unitPrice)
}

// Arithmetic on optic values
val doubled   : SchemaExpr[Order, Int]    = Order.quantity * 2
val discounted: SchemaExpr[Order, Double] = Order.unitPrice - 5.0
val increased : SchemaExpr[Order, Int]    = Order.quantity + 1
```

### Via String Operators on Optics

The `concat`, `matches`, and `length` methods on string-focused optics create `SchemaExpr.StringConcat`, `SchemaExpr.StringRegexMatch`, and `SchemaExpr.StringLength` nodes:

```scala
import zio.blocks.schema._

case class Email(address: String, subject: String)

object Email extends CompanionOptics[Email] {
  implicit val schema: Schema[Email] = Schema.derived

  val address: Lens[Email, String] = $(_.address)
  val subject: Lens[Email, String] = $(_.subject)
}

// String operations
val withDomain: SchemaExpr[Email, String]   = Email.address.concat("@example.com")
val isValid   : SchemaExpr[Email, Boolean]  = Email.address.matches("^[^@]+@[^@]+$")
val subjectLen: SchemaExpr[Email, Int]      = Email.subject.length
```

### Via Logical Operators on SchemaExpr

Boolean-typed `SchemaExpr` values can be combined with `&&` and `||`:

```scala
import zio.blocks.schema._

case class Person(name: String, age: Int)

object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived

  val name: Lens[Person, String] = $(_.name)
  val age: Lens[Person, Int]     = $(_.age)
}

// Compose expressions with && and ||
val isAdult = Person.age >= 18
val isAlice = Person.name === "Alice"

val adultAlice  : SchemaExpr[Person, Boolean] = isAdult && isAlice
val adultOrAlice: SchemaExpr[Person, Boolean] = isAdult || isAlice
```

### Via Direct AST Construction

For advanced use cases, you can construct `SchemaExpr` values directly. In most application code, prefer optic/operator syntax or `SchemaExpr.literal` because they preserve the typed surface and are easier to read.

```scala
import zio.blocks.schema._

case class Item(price: Int)

object Item extends CompanionOptics[Item] {
  implicit val schema: Schema[Item] = Schema.derived

  val price: Lens[Item, Int] = $(_.price)
}

// Construct via factory methods
val lit: SchemaExpr[Item, Int] = SchemaExpr.literal[Item, Int](42)
val opticExpr: SchemaExpr[Item, Int] = SchemaExpr.optic[Item, Int](Item.price.toDynamic, Item.schema)
val comparison: SchemaExpr[Item, Boolean] = SchemaExpr.relational(
  opticExpr,
  lit,
  SchemaExpr.RelationalOperator.GreaterThan
)
```

`SchemaExpr.literal` is the normal way to inject constants into an expression tree. It produces a typed `SchemaExpr[S, A]`, while storing the value internally as a `DynamicSchemaExpr.Literal`. That is usually what you want in user code, including migration builders.

If you need the raw dynamic form for serialization, transport, or custom interpreters, use `.dynamic` on an existing `SchemaExpr` rather than constructing `DynamicSchemaExpr` directly unless you are working on expression internals.

## Core Operations

### Evaluation

#### `eval`

Evaluates the expression against an input value, returning the typed result. The result is a `Seq[B]` because traversal-based expressions can produce multiple values.

```scala
trait SchemaExprLike[A, B] {
  def eval(input: A): Either[OpticCheck, Seq[B]]
}
```

```scala
import zio.blocks.schema._

case class Person(name: String, age: Int)

object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived

  val age: Lens[Person, Int] = $(_.age)
}

val isAdult = Person.age >= 18

val alice = Person("Alice", 30)
val result = isAdult.eval(alice)
// Right(List(true))

val bob = Person("Bob", 12)
val result2 = isAdult.eval(bob)
// Right(List(false))
```

:::note
When an expression wraps a `Traversal` optic, `eval` returns multiple values — one per element in the traversed collection. For `Lens`-based expressions, the result is always a single-element `Seq`.
:::

#### `evalDynamic`

Like `eval`, but converts the result to [`DynamicValue`](./dynamic-value.md) instances. This is useful for serialization or when working with schema-agnostic code.

```scala
trait SchemaExprLike[A, B] {
  def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]]
}
```

In the following example we are evaluating a simple optic expression to extract the `name` field from a `Person` and retrieving it as a `DynamicValue`:

```scala
import zio.blocks.schema._

case class Person(name: String, age: Int)

object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived

  val name: Lens[Person, String] = $(_.name)
}

val nameExpr = SchemaExpr.optic[Person, String](Person.name.toDynamic, Person.schema)
val result = nameExpr.evalDynamic(Person("Alice", 30))
// Right(List(DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
```

### Logical Combination

#### `&&`

Combines two boolean-typed expressions with logical AND. Both operands must produce `Boolean` results.

`&&` and `||` are provided via `SchemaExpr.BooleanOps`, an implicit class in the `SchemaExpr` companion, rather than as direct methods on `SchemaExpr`. This lets downstream libraries supply their own `&&`/`||` overloads (e.g. returning a DDB- or SQL-specific expression type) by bringing in their own implicit class via an explicit import, which takes priority over the companion-scope implicit in Scala 2 and 3.

```scala
// In SchemaExpr companion:
implicit final class BooleanOps[A, B](val self: SchemaExpr[A, B]) extends AnyVal {
  def and(that: SchemaExpr[A, Boolean])(implicit ev: B <:< Boolean): SchemaExpr[A, Boolean]
  def &&(that: SchemaExpr[A, Boolean])(implicit ev: B <:< Boolean): SchemaExpr[A, Boolean]
}
```

In the following example we are evaluating a combined expression that checks if a `Person` is an adult (age >= 18) and has the name "Alice". The result of evaluating this expression against a `Person` instance will be `true` if both conditions are met, and `false` otherwise:

```scala
import zio.blocks.schema._

case class Person(name: String, age: Int)

object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived

  val name: Lens[Person, String] = $(_.name)
  val age: Lens[Person, Int]     = $(_.age)
}

val isAdultAlice = (Person.age >= 18) && (Person.name === "Alice")
val result = isAdultAlice.eval(Person("Alice", 30))
// Right(List(true))
```

#### `||`

Combines two boolean-typed expressions with logical OR.

```scala
// In SchemaExpr companion:
implicit final class BooleanOps[A, B](val self: SchemaExpr[A, B]) extends AnyVal {
  def or(that: SchemaExpr[A, Boolean])(implicit ev: B <:< Boolean): SchemaExpr[A, Boolean]
  def ||(that: SchemaExpr[A, Boolean])(implicit ev: B <:< Boolean): SchemaExpr[A, Boolean]
}
```

In the following example we are evaluating a combined expression that checks if a `Person` is an adult (age >= 18) or has the name "Alice". The result of evaluating this expression against a `Person` instance will be `true` if either condition is met, and `false` only if both conditions are not met:

```scala
import zio.blocks.schema._

case class Person(name: String, age: Int)

object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived

  val name: Lens[Person, String] = $(_.name)
  val age: Lens[Person, Int]     = $(_.age)
}

val isAdultOrAlice = (Person.age >= 18) || (Person.name === "Alice")
val result = isAdultOrAlice.eval(Person("Alice", 12))
// Right(List(true)) — Alice, even though not adult
```

## Structure

`SchemaExpr` is a typed wrapper. The actual expression AST lives in `DynamicSchemaExpr`, which is a sealed trait of serializable expression nodes. `SchemaExpr` adds the input and output schemas needed to convert between typed values and [`DynamicValue`](./dynamic-value.md).

### `DynamicSchemaExpr` leaf nodes

#### `DynamicSchemaExpr.Literal`

A constant value represented directly as a `DynamicValue`.

```scala
object DynamicSchemaExpr {
  final case class Literal(value: DynamicValue, schema: Schema[_]) extends DynamicSchemaExpr
}
```

#### `DynamicSchemaExpr.Select`

Selects values from the input using a [`DynamicOptic`](./dynamic-optic.md).

```scala
object DynamicSchemaExpr {
  final case class Select(path: DynamicOptic) extends DynamicSchemaExpr
}
```

`SchemaExpr.optic(...)` and optic operator syntax eventually produce `DynamicSchemaExpr.Select` nodes.

### Unary operations

#### `DynamicSchemaExpr.Not`

Negates a boolean expression.

```scala
object DynamicSchemaExpr {
  final case class Not(expr: DynamicSchemaExpr) extends DynamicSchemaExpr
}
```

Created via the `!` (unary negation) operator on boolean optics:

```scala
import zio.blocks.schema._

case class User(active: Boolean)

object User extends CompanionOptics[User] {
  implicit val schema: Schema[User] = Schema.derived

  val active: Lens[User, Boolean] = $(_.active)
}

val inactive: SchemaExpr[User, Boolean] = !User.active

val result = inactive.eval(User(active = true))
// Right(List(false))
```

### Binary operations

`Relational`, `Logical`, `Arithmetic`, `Bitwise`, and the string binary operations are all represented as `DynamicSchemaExpr` nodes holding child expressions.

#### `DynamicSchemaExpr.Relational`

Compares two expressions using a `RelationalOperator`. Returns a boolean result.

```scala
object DynamicSchemaExpr {
  final case class Relational(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: RelationalOperator
  ) extends DynamicSchemaExpr
}
```

The available `RelationalOperator` values are:

| Operator             | Optic Syntax | Description           |
|----------------------|--------------|-----------------------|
| `Equal`              | `===`        | Equality check        |
| `NotEqual`           | `!=`         | Inequality check      |
| `LessThan`           | `<`          | Less than             |
| `LessThanOrEqual`    | `<=`         | Less than or equal    |
| `GreaterThan`        | `>`          | Greater than          |
| `GreaterThanOrEqual` | `>=`         | Greater than or equal |

:::note
Equality and inequality (`===`, `!=`) compare values directly. Ordering operators (`<`, `<=`, `>`, `>=`) compare via `DynamicValue` ordering internally.
:::

#### `DynamicSchemaExpr.Logical`

Combines two boolean expressions with a `LogicalOperator`.

```scala
object DynamicSchemaExpr {
  final case class Logical(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: LogicalOperator
  ) extends DynamicSchemaExpr
}
```

The available `LogicalOperator` values are:

| Operator | Syntax | Description         |
|----------|--------|---------------------|
| `And`    | `&&`   | Logical conjunction |
| `Or`     | `\|\|` | Logical disjunction |

#### `DynamicSchemaExpr.Arithmetic`

Performs arithmetic on two numeric expressions using an `ArithmeticOperator`. Requires an `IsNumeric[A]` type class instance.

```scala
object DynamicSchemaExpr {
  final case class Arithmetic(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: ArithmeticOperator,
    numericType: NumericTypeTag
  ) extends DynamicSchemaExpr
}
```

The available `ArithmeticOperator` values are:

| Operator   | Optic Syntax | Description    |
|------------|--------------|----------------|
| `Add`      | `+`          | Addition       |
| `Subtract` | `-`          | Subtraction    |
| `Multiply` | `*`          | Multiplication |

Supported numeric types include `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigInt`, and `BigDecimal`.

#### Other string and bitwise operations

`DynamicSchemaExpr` also includes:

- `StringConcat`
- `StringRegexMatch`
- `StringLength`
- `StringSubstring`
- `StringTrim`
- `StringToUpperCase`
- `StringToLowerCase`
- `StringReplace`
- `StringStartsWith`
- `StringEndsWith`
- `StringContains`
- `StringIndexOf`
- `PrimitiveConversion`
- `Bitwise`
- `BitwiseNot`

These are the cases downstream interpreters should consider when translating `SchemaExpr.dynamic` into SQL, filters, or other query languages.

## Error Handling

Expression evaluation returns `Either[OpticCheck, Seq[B]]`. The `Left` case contains an [`OpticCheck`](./optics.md) with detailed diagnostic information about what went wrong — for example, an unexpected case in a prism, an empty collection in a traversal, or a missing key.

```scala
import zio.blocks.schema._

case class Shape(kind: String)

object Shape extends CompanionOptics[Shape] {
  implicit val schema: Schema[Shape] = Schema.derived

  val kind: Lens[Shape, String] = $(_.kind)
}

val expr = Shape.kind === "circle"
val result = expr.eval(Shape("circle"))

result match {
  case Right(values) => println(s"Result: ${values.head}")
  case Left(check)   => println(s"Error: ${check.message}")
}
```

## Advanced Usage: Building Query DSLs

Because `SchemaExpr.dynamic` is a sealed, inspectable AST, third-party libraries can translate expressions into other languages. The public entry point should still accept `SchemaExpr`; only the interpreter internals need to cross into `DynamicSchemaExpr`.

```scala
// Pseudocode — keeps SchemaExpr as the public API
def toSql[A, B](expr: SchemaExpr[A, B]): String =
  toSqlDynamic(expr.dynamic)

def toSqlDynamic(expr: DynamicSchemaExpr): String = expr match {
  case DynamicSchemaExpr.Relational(left, right, op) =>
    s"${toSqlValue(left)} ${opToSql(op)} ${toSqlValue(right)}"
  case DynamicSchemaExpr.Logical(left, right, DynamicSchemaExpr.LogicalOperator.And) =>
    s"(${toSqlDynamic(left)}) AND (${toSqlDynamic(right)})"
  case DynamicSchemaExpr.Logical(left, right, DynamicSchemaExpr.LogicalOperator.Or) =>
    s"(${toSqlDynamic(left)}) OR (${toSqlDynamic(right)})"
  case DynamicSchemaExpr.Not(inner) =>
    s"NOT (${toSqlDynamic(inner)})"
  // ...
}
```

This is the key advantage of reified expressions over plain functions — the same expression can be evaluated locally *and* translated to a remote query language.

## Integration

### Optics

`SchemaExpr` is tightly integrated with [Optics](./optics.md). All operator methods (`===`, `>`, `<`, `>=`, `<=`, `!=`, `&&`, `||`, `!`, `+`, `-`, `*`, `concat`, `matches`, `length`) are defined on `Optic[S, A]` and return `SchemaExpr[S, B]` values. This makes the optic the primary entry point for building expressions.

### Schema

[Schema](./schema.md) provides the type information needed to construct typed `SchemaExpr` values and to convert typed results to and from `DynamicValue` during evaluation.

### DynamicValue

[DynamicValue](./dynamic-value.md) is the output type of `SchemaExpr#evalDynamic`. It provides a schema-less representation that can be serialized, compared, and manipulated uniformly.

### OpticCheck

[OpticCheck](./optics.md) is the error type returned when expression evaluation fails. It provides rich diagnostic information including the optic path, expected vs. actual cases, and the actual value encountered.
