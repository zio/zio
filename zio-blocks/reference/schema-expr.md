# SchemaExpr

> `SchemaExpr[A, +B]` is a **schema-aware expression** that computes a result of type `B` from an input value of type `A`. The input type `A` must be fully described by a [`Schema`](./schema.md), and the expression is built from [optics](./optics.md), literal values, and operators. The fundamental operations are `eval` and `evalDynamic`.

`SchemaExpr[A, +B]` is a **schema-aware expression** that computes a result of type `B` from an input value of type `A`. The input type `A` must be fully described by a [`Schema`](./schema.md), and the expression is built from [optics](./optics.md), literal values, and operators. The fundamental operations are `eval` and `evalDynamic`.

`SchemaExpr`:
- represents expressions as a reified AST, enabling introspection and serialization
- supports relational (`===`, `>`, `<`, `>=`, `<=`, `!=`), logical (`&&`, `||`, `!`), arithmetic (`+`, `-`, `*`), and string (`concat`, `matches`, `length`) operations
- evaluates to `Either[OpticCheck, Seq[B]]`, handling failures and multi-valued results from traversals
- is covariant in `B`, the output type

```scala
sealed trait SchemaExpr[A, +B] {
  def eval(input: A): Either[OpticCheck, Seq[B]]
  def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]]
}
```

:::tip
For practical walkthroughs of building with `SchemaExpr`, see [Query DSL Part 1: Expressions](../guides/query-dsl-reified-optics.md), [Part 2: SQL Generation](../guides/query-dsl-sql.md), and [Part 3: Extending the Expression Language](../guides/query-dsl-extending.md).
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
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "0.0.33"
```

For cross-platform (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-schema" % "0.0.33"
```

Supported Scala versions: 2.13.x and 3.x.

## Creating Instances

`SchemaExpr` instances are typically created through **operator syntax on optics** rather than by constructing AST nodes directly. Each operator on `Optic[S, A]` returns a `SchemaExpr[S, B]`.

### Via Relational Operators on Optics

The comparison operators `===`, `>`, `>=`, `<`, `<=`, and `!=` on `Optic[S, A]` create `SchemaExpr.Relational` nodes. Each operator has two overloads — one comparing against a literal value, and one comparing against another optic:

```scala

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

For advanced use cases, we can construct `SchemaExpr` nodes directly:

```scala

case class Item(price: Int)

object Item extends CompanionOptics[Item] {
  implicit val schema: Schema[Item] = Schema.derived

  val price: Lens[Item, Int] = $(_.price)
}

// Construct AST nodes directly
val lit: SchemaExpr[Item, Int] = new SchemaExpr.Literal(42, Schema[Int])
val opticExpr: SchemaExpr[Item, Int] = new SchemaExpr.Optic(Item.price)
val comparison: SchemaExpr[Item, Boolean] = new SchemaExpr.Relational(
  opticExpr,
  lit,
  SchemaExpr.RelationalOperator.GreaterThan
)
```

## Core Operations

### Evaluation

#### `eval`

Evaluates the expression against an input value, returning the typed result. The result is a `Seq[B]` because traversal-based expressions can produce multiple values.

```scala
trait SchemaExpr[A, +B] {
  def eval(input: A): Either[OpticCheck, Seq[B]]
}
```

```scala

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
trait SchemaExpr[A, +B] {
  def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]]
}
```

In the following example we are evaluating a simple optic expression to extract the `name` field from a `Person` and retrieving it as a `DynamicValue`:

```scala

case class Person(name: String, age: Int)

object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived

  val name: Lens[Person, String] = $(_.name)
}

val nameExpr = new SchemaExpr.Optic(Person.name)
val result = nameExpr.evalDynamic(Person("Alice", 30))
// Right(List(DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
```

### Logical Combination

#### `&&`

Combines two boolean-typed expressions with logical AND. Both operands must produce `Boolean` results.

```scala
trait SchemaExpr[A, +B] {
  def &&[B2](that: SchemaExpr[A, B2])(implicit ev: B <:< Boolean, ev2: B2 =:= Boolean): SchemaExpr[A, Boolean]
}
```

In the following example we are evaluating a combined expression that checks if a `Person` is an adult (age >= 18) and has the name "Alice". The result of evaluating this expression against a `Person` instance will be `true` if both conditions are met, and `false` otherwise:

```scala

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
trait SchemaExpr[A, +B] {
  def ||[B2](that: SchemaExpr[A, B2])(implicit ev: B <:< Boolean, ev2: B2 =:= Boolean): SchemaExpr[A, Boolean]
}
```

In the following example we are evaluating a combined expression that checks if a `Person` is an adult (age >= 18) or has the name "Alice". The result of evaluating this expression against a `Person` instance will be `true` if either condition is met, and `false` only if both conditions are not met:

```scala

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

## Subtypes

`SchemaExpr` is a sealed trait with a rich set of case classes representing different expression nodes. These form an **expression AST** that can be inspected, serialized, or translated to other query languages.

### Leaf Nodes

#### `SchemaExpr.Literal`

A constant value that ignores the input and always produces the same result.

```scala
object SchemaExpr {
  case class Literal[S, A](value: A, schema: Schema[A]) extends SchemaExpr[S, A]
}
```

The `Literal#eval` always returns `Right(Seq(value))` regardless of the input. The `Literal#schema` parameter enables conversion to `DynamicValue` via `evalDynamic`.

#### `SchemaExpr.Optic`

Wraps an [`Optic[A, B]`](./optics.md) to extract values from the input. The behavior depends on the optic type:

```scala
object SchemaExpr {
  case class Optic[A, B](optic: zio.blocks.schema.Optic[A, B]) extends SchemaExpr[A, B]
}
```

| Optic Type  | `SchemaExpr.Optic#eval` Behavior                                                      |
|-------------|---------------------------------------------------------------------------------------|
| `Lens`      | Always succeeds with a single value                                                   |
| `Prism`     | Succeeds if the input matches the expected case; otherwise returns `Left(OpticCheck)` |
| `Optional`  | Succeeds if the value is present; otherwise returns `Left(OpticCheck)`                |
| `Traversal` | Returns all elements; returns `Left(OpticCheck)` if the collection is empty           |

### Unary Operations

#### `SchemaExpr.Not`

Negates a boolean expression.

```scala
object SchemaExpr {
  case class Not[A](expr: SchemaExpr[A, Boolean]) extends UnaryOp[A, Boolean](expr)
}
```

Created via the `!` (unary negation) operator on boolean optics:

```scala

case class User(active: Boolean)

object User extends CompanionOptics[User] {
  implicit val schema: Schema[User] = Schema.derived

  val active: Lens[User, Boolean] = $(_.active)
}

val inactive: SchemaExpr[User, Boolean] = !User.active

val result = inactive.eval(User(active = true))
// Right(List(false))
```

### Binary Operations

`Relational`, `Logical`, `Arithmetic`, and `StringConcat` extend `BinaryOp[A, B, C]`, which provides `left` and `right` sub-expressions. `StringRegexMatch` and `StringLength` extend `SchemaExpr` directly — see [Other Operations](#other-operations) below.

#### `SchemaExpr.Relational`

Compares two expressions using a `RelationalOperator`. Returns a boolean result.

```scala
object SchemaExpr {
  case class Relational[A, B](
    left: SchemaExpr[A, B],
    right: SchemaExpr[A, B],
    operator: RelationalOperator
  ) extends SchemaExpr[A, Boolean]
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

#### `SchemaExpr.Logical`

Combines two boolean expressions with a `LogicalOperator`.

```scala
object SchemaExpr {
  case class Logical[A](
    left: SchemaExpr[A, Boolean],
    right: SchemaExpr[A, Boolean],
    operator: LogicalOperator
  ) extends SchemaExpr[A, Boolean]
}
```

The available `LogicalOperator` values are:

| Operator | Syntax | Description         |
|----------|--------|---------------------|
| `And`    | `&&`   | Logical conjunction |
| `Or`     | `\|\|` | Logical disjunction |

#### `SchemaExpr.Arithmetic`

Performs arithmetic on two numeric expressions using an `ArithmeticOperator`. Requires an `IsNumeric[A]` type class instance.

```scala
object SchemaExpr {
  case class Arithmetic[S, A](
    left: SchemaExpr[S, A],
    right: SchemaExpr[S, A],
    operator: ArithmeticOperator,
    isNumeric: IsNumeric[A]
  ) extends SchemaExpr[S, A]
}
```

The available `ArithmeticOperator` values are:

| Operator   | Optic Syntax | Description    |
|------------|--------------|----------------|
| `Add`      | `+`          | Addition       |
| `Subtract` | `-`          | Subtraction    |
| `Multiply` | `*`          | Multiplication |

Supported numeric types via `IsNumeric`: `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigInt`, `BigDecimal`.

#### `SchemaExpr.StringConcat`

Concatenates two string expressions.

```scala
object SchemaExpr {
  case class StringConcat[A](
    left: SchemaExpr[A, String],
    right: SchemaExpr[A, String]
  ) extends SchemaExpr[A, String]
}
```

Created via the `concat` method on string optics:

```scala

case class Greeting(prefix: String)

object Greeting extends CompanionOptics[Greeting] {
  implicit val schema: Schema[Greeting] = Schema.derived

  val prefix: Lens[Greeting, String] = $(_.prefix)
}

val withName = Greeting.prefix.concat(", World!")
val result = withName.eval(Greeting("Hello"))
// Right(List("Hello, World!"))
```

### Other Operations

`StringRegexMatch` and `StringLength` extend `SchemaExpr` directly rather than through `UnaryOp` or `BinaryOp`.

#### `SchemaExpr.StringRegexMatch`

Tests whether a string matches a regular expression pattern. Despite having two operands (`regex` and `string`), it extends `SchemaExpr[A, Boolean]` directly.

```scala
object SchemaExpr {
  case class StringRegexMatch[A](
    regex: SchemaExpr[A, String],
    string: SchemaExpr[A, String]
  ) extends SchemaExpr[A, Boolean]
}
```

Created via the `matches` method on string optics:

```scala

case class Email(address: String)

object Email extends CompanionOptics[Email] {
  implicit val schema: Schema[Email] = Schema.derived

  val address: Lens[Email, String] = $(_.address)
}

val isValid = Email.address.matches("^[^@]+@[^@]+\\.[^@]+$")
val result = isValid.eval(Email("alice@example.com"))
// Right(List(true))
```

#### `SchemaExpr.StringLength`

Computes the length of a string expression. This is a unary operation but extends `SchemaExpr[A, Int]` directly rather than `UnaryOp`.

```scala
object SchemaExpr {
  case class StringLength[A](
    string: SchemaExpr[A, String]
  ) extends SchemaExpr[A, Int]
}
```

Created via the `length` method on string optics:

```scala

case class Message(body: String)

object Message extends CompanionOptics[Message] {
  implicit val schema: Schema[Message] = Schema.derived

  val body: Lens[Message, String] = $(_.body)
}

val bodyLength = Message.body.length
val result = bodyLength.eval(Message("Hello!"))
// Right(List(6))
```

### Abstract Intermediate Traits

Two sealed traits categorize some expressions by arity:

- **`UnaryOp[A, B]`** — has a single `expr: SchemaExpr[A, B]`. Extended by `Not`.
- **`BinaryOp[A, B, C]`** — has `left: SchemaExpr[A, B]` and `right: SchemaExpr[A, B]`. Extended by `Relational`, `Logical`, `Arithmetic`, and `StringConcat`.

Not all expression nodes use these traits — `StringRegexMatch` and `StringLength` extend `SchemaExpr` directly. These intermediate traits are useful for pattern matching when you need to generically process the expression tree.

## Error Handling

Expression evaluation returns `Either[OpticCheck, Seq[B]]`. The `Left` case contains an [`OpticCheck`](./optics.md) with detailed diagnostic information about what went wrong — for example, an unexpected case in a prism, an empty collection in a traversal, or a missing key.

```scala

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

Because `SchemaExpr` is a sealed, inspectable AST, third-party libraries can pattern-match on the expression tree to translate it into other languages. For example, a database library could translate `SchemaExpr` into SQL:

```scala
// Pseudocode — illustrates the concept
def toSql[A](expr: SchemaExpr[A, Boolean]): String = expr match {
  case SchemaExpr.Relational(left, right, op) =>
    s"${toSqlValue(left)} ${opToSql(op)} ${toSqlValue(right)}"
  case SchemaExpr.Logical(left, right, SchemaExpr.LogicalOperator.And) =>
    s"(${toSql(left)}) AND (${toSql(right)})"
  case SchemaExpr.Logical(left, right, SchemaExpr.LogicalOperator.Or) =>
    s"(${toSql(left)}) OR (${toSql(right)})"
  case SchemaExpr.Not(inner) =>
    s"NOT (${toSql(inner)})"
  // ...
}
```

This is the key advantage of reified expressions over plain functions — the same expression can be evaluated locally *and* translated to a remote query language.

## Integration

### Optics

`SchemaExpr` is tightly integrated with [Optics](./optics.md). All operator methods (`===`, `>`, `<`, `>=`, `<=`, `!=`, `&&`, `||`, `!`, `+`, `-`, `*`, `concat`, `matches`, `length`) are defined on `Optic[S, A]` and return `SchemaExpr[S, B]` values. This makes the optic the primary entry point for building expressions.

### Schema

[Schema](./schema.md) provides the type information needed by `SchemaExpr.Literal` to convert values to `DynamicValue` via `Literal#evalDynamic`. The `IsNumeric` type class (used by `Arithmetic`) also derives from `Schema`.

### DynamicValue

[DynamicValue](./dynamic-value.md) is the output type of `SchemaExpr#evalDynamic`. It provides a schema-less representation that can be serialized, compared, and manipulated uniformly.

### OpticCheck

[OpticCheck](./optics.md) is the error type returned when expression evaluation fails. It provides rich diagnostic information including the optic path, expected vs. actual cases, and the actual value encountered.
