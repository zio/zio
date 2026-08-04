# Query DSL with Reified Optics — Part 3: Extending the Expression Language

> In this guide, we will extend the ZIO Blocks query DSL with an expression language that goes beyond what `SchemaExpr` provides out of the box. By the end, you will have an `Expr` ADT that adds SQL-specific predicates (`IN`, `BETWEEN`, `IS NULL`, `LIKE`), type-safe aggregate functions (`COUNT`, `SUM`, `AVG`), and conditional expressions (`CASE WHEN`) — all composable with the built-in `SchemaExpr` operators from Parts 1 and 2.

In this guide, we will extend the ZIO Blocks query DSL with an expression language that goes beyond what `SchemaExpr` provides out of the box. By the end, you will have an `Expr` ADT that adds SQL-specific predicates (`IN`, `BETWEEN`, `IS NULL`, `LIKE`), type-safe aggregate functions (`COUNT`, `SUM`, `AVG`), and conditional expressions (`CASE WHEN`) — all composable with the built-in `SchemaExpr` operators from Parts 1 and 2.

This is Part 3 of the Query DSL series. [Part 1](./query-dsl-reified-optics.md) covered building query expressions, and [Part 2](./query-dsl-sql.md) covered translating them to SQL. Here, we handle the cases where the built-in expression language is not enough.

**What we'll cover:**

- Why `SchemaExpr` is deliberately closed and what that means for extension
- Designing an `Expr` ADT that is a superset of `SchemaExpr`
- Translating `SchemaExpr` into `Expr` via `fromSchemaExpr`
- Adding SQL-specific predicates: `IN`, `BETWEEN`, `IS NULL`, `LIKE`
- Writing bridge extension methods for seamless `SchemaExpr` + `Expr` composition
- Building a single unified SQL interpreter
- Adding type-safe aggregate functions and `CASE WHEN` for advanced SQL generation

## The Problem

The built-in `SchemaExpr` operators cover the fundamentals: equality, comparisons, boolean logic, arithmetic, and basic string operations. But real-world SQL requires more. Consider these common queries:

```sql
-- Membership test
SELECT * FROM products WHERE category IN ('Electronics', 'Books', 'Toys')

-- Range check
SELECT * FROM products WHERE price BETWEEN 10.0 AND 100.0

-- Null handling
SELECT * FROM products WHERE description IS NULL

-- Pattern matching with SQL wildcards
SELECT * FROM products WHERE name LIKE 'Lap%'

-- Aggregation
SELECT category, COUNT(*), AVG(price)
FROM products GROUP BY category HAVING COUNT(*) > 2

-- Conditional logic
SELECT name, CASE WHEN price > 100 THEN 'expensive' ELSE 'cheap' END AS tier
FROM products
```

None of these can be expressed with `SchemaExpr` alone. You could generate the SQL strings manually, but then you lose composability — you can no longer mix these operations with the type-safe `SchemaExpr` predicates from Parts 1 and 2.

`SchemaExpr` wraps a `DynamicSchemaExpr` sealed trait that you cannot add new cases to. The public API is `SchemaExpr`; `DynamicSchemaExpr` is the raw serializable AST exposed through `.dynamic`. Instead of extending `DynamicSchemaExpr` directly, we define an `Expr` ADT that is a superset — it includes equivalent nodes for everything `SchemaExpr` can express, plus our custom SQL-specific operations. A `fromSchemaExpr` function translates `SchemaExpr` values into `Expr` by reading the underlying `DynamicSchemaExpr`, enabling seamless interoperability with a single unified interpreter.

## Prerequisites

This guide builds on [Part 1: Expressions](./query-dsl-reified-optics.md) and [Part 2: SQL Generation](./query-dsl-sql.md). You should be comfortable building `SchemaExpr` values and translating them to SQL.

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "0.0.51"
```

```scala
import zio.blocks.schema._
```

## Domain Setup

We reuse the product catalog domain from the earlier guides:

```scala
case class Product(
  name: String,
  price: Double,
  category: String,
  inStock: Boolean,
  rating: Int
)

object Product extends CompanionOptics[Product] {
  implicit val schema: Schema[Product] = Schema.derived

  val name: Lens[Product, String]     = optic(_.name)
  val price: Lens[Product, Double]    = optic(_.price)
  val category: Lens[Product, String] = optic(_.category)
  val inStock: Lens[Product, Boolean] = optic(_.inStock)
  val rating: Lens[Product, Int]      = optic(_.rating)
}
```

## Designing the Expr ADT

The key insight is the **translation pattern**: keep your public extension API typed, then provide a `fromSchemaExpr` function that lifts any built-in `SchemaExpr` into your extended ADT. Ordinary application code stays on `SchemaExpr`, `Optic`, and typed constructors. Only the interpreter internals need to inspect `.dynamic`.

```
  Built-in API                               Your extension layer
┌───────────────────────────────┐       ┌───────────────────────────────────────┐
│  SchemaExpr[S, A]             │       │  Expr[S, A]                           │
│  built with optics/operators  │──────▶│  ├── Builtin(schemaExpr)              │
└───────────────────────────────┘       │  ├── Column(optic)                    │
         fromSchemaExpr ────────────────│  ├── Lit(value, schema)               │
                                        │  ├── In(expr, values, schema)         │
                                        │  ├── Between(expr, low, high, schema) │
                                        │  ├── IsNull(expr)                     │
                                        │  ├── Like(expr, pattern)              │
                                        │  ├── Agg(function, expr)              │
                                        │  └── CaseWhen(branches, else)         │
                                        └───────────────────────────────────────┘
```

The `Expr` ADT keeps the public surface typed. It can wrap any built-in `SchemaExpr` through `Builtin`, and it adds SQL-specific nodes on top. The interpreter is still free to inspect `schemaExpr.dynamic` internally when it needs to translate the built-in pieces.

Here is the full `Expr` ADT with its supporting types:

```scala
sealed trait Expr[S, A]

object Expr {

  // --- Typed public nodes ---
  final case class Builtin[S, A](schemaExpr: SchemaExpr[S, A]) extends Expr[S, A]
  final case class Column[S, A](optic: Optic[S, A]) extends Expr[S, A]
  final case class Lit[S, A](value: A, schema: Schema[A]) extends Expr[S, A]

  // Relational
  final case class Relational[S, A](left: Expr[S, A], right: Expr[S, A], op: RelOp) extends Expr[S, Boolean]

  // Logical
  final case class And[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Or[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Not[S](expr: Expr[S, Boolean]) extends Expr[S, Boolean]

  // Arithmetic
  final case class Arithmetic[S, A](left: Expr[S, A], right: Expr[S, A], op: ArithOp) extends Expr[S, A]

  // String
  final case class StringConcat[S](left: Expr[S, String], right: Expr[S, String]) extends Expr[S, String]
  final case class StringRegexMatch[S](regex: Expr[S, String], string: Expr[S, String]) extends Expr[S, Boolean]
  final case class StringLength[S](string: Expr[S, String]) extends Expr[S, Int]

  // --- SQL-specific extensions (no SchemaExpr equivalents) ---
  final case class In[S, A](expr: Expr[S, A], values: List[A], schema: Schema[A]) extends Expr[S, Boolean]
  final case class Between[S, A](expr: Expr[S, A], low: A, high: A, schema: Schema[A]) extends Expr[S, Boolean]
  final case class IsNull[S, A](expr: Expr[S, A]) extends Expr[S, Boolean]
  final case class Like[S](expr: Expr[S, String], pattern: String) extends Expr[S, Boolean]

  // --- Aggregates ---
  final case class Agg[S, A, B](function: AggFunction[A, B], expr: Expr[S, A]) extends Expr[S, B]

  // --- Conditional ---
  final case class CaseWhen[S, A](
    branches: List[(Expr[S, Boolean], Expr[S, A])],
    otherwise: Option[Expr[S, A]]
  ) extends Expr[S, A]

  // --- Factory methods ---
  def col[S, A](optic: Optic[S, A]): Expr[S, A] = Column(optic)
  def lit[S, A](value: A)(implicit schema: Schema[A]): Expr[S, A] = Lit(value, schema)

  def count[S, A](expr: Expr[S, A]): Expr[S, Long]   = Agg(AggFunction.Count(), expr)
  def sum[S](expr: Expr[S, Double]): Expr[S, Double]  = Agg(AggFunction.Sum, expr)
  def avg[S](expr: Expr[S, Double]): Expr[S, Double]  = Agg(AggFunction.Avg, expr)
  def min[S, A](expr: Expr[S, A]): Expr[S, A]         = Agg(AggFunction.Min(), expr)
  def max[S, A](expr: Expr[S, A]): Expr[S, A]         = Agg(AggFunction.Max(), expr)

  def caseWhen[S, A](branches: (Expr[S, Boolean], Expr[S, A])*): CaseWhenBuilder[S, A] =
    CaseWhenBuilder(branches.toList)

  case class CaseWhenBuilder[S, A](branches: List[(Expr[S, Boolean], Expr[S, A])]) {
    def otherwise(value: Expr[S, A]): Expr[S, A] = CaseWhen(branches, Some(value))
    def end: Expr[S, A] = CaseWhen(branches, None)
  }

  // --- Translation from SchemaExpr ---
  def fromSchemaExpr[S, A](se: SchemaExpr[S, A]): Expr[S, A] = Builtin(se)
}

// --- Operators ---

sealed trait RelOp
object RelOp {
  case object Equal              extends RelOp
  case object NotEqual           extends RelOp
  case object LessThan           extends RelOp
  case object LessThanOrEqual    extends RelOp
  case object GreaterThan        extends RelOp
  case object GreaterThanOrEqual extends RelOp
}

sealed trait ArithOp
object ArithOp {
  case object Add      extends ArithOp
  case object Subtract extends ArithOp
  case object Multiply extends ArithOp
}

// Typed aggregate functions
sealed trait AggFunction[A, B] {
  def name: String
}
object AggFunction {
  case class Count[A]() extends AggFunction[A, Long]         { val name = "COUNT" }
  case object Sum       extends AggFunction[Double, Double]  { val name = "SUM" }
  case object Avg       extends AggFunction[Double, Double]  { val name = "AVG" }
  case class Min[A]()   extends AggFunction[A, A]            { val name = "MIN" }
  case class Max[A]()   extends AggFunction[A, A]            { val name = "MAX" }
}
```

Here are some keynotes on the design:

- **Type-safe aggregates** — `AggFunction[A, B]` encodes the return type: `COUNT` returns `Long`, `SUM`/`AVG` return `Double`, `MIN`/`MAX` preserve the input type.
- **Typed public constructors** — `Column` stores an `Optic` and `Lit` stores a typed value plus its `Schema`, so extension code stays on the same public abstractions as ordinary `SchemaExpr` code.
- **`fromSchemaExpr`** — lifts a built-in `SchemaExpr` into the extended ADT. The dynamic representation is only consulted later by the interpreter.

## Extension Methods

To make the new operations feel natural, we define implicit classes on `Optic`, `Expr`, and `SchemaExpr`. The bridge implicit class on `SchemaExpr` auto-translates at the boundary via `fromSchemaExpr`, so `SchemaExpr` and `Expr` values compose seamlessly with `&&` and `||`:

```scala
implicit final class OpticExprOps[S, A](private val optic: Optic[S, A]) {
  def in(values: A*)(implicit schema: Schema[A]): Expr[S, Boolean]           = Expr.In(Expr.col(optic), values.toList, schema)
  def between(low: A, high: A)(implicit schema: Schema[A]): Expr[S, Boolean] = Expr.Between(Expr.col(optic), low, high, schema)
  def isNull: Expr[S, Boolean]                   = Expr.IsNull(Expr.col(optic))
  def isNotNull: Expr[S, Boolean]                = Expr.Not(Expr.IsNull(Expr.col(optic)))
}

implicit final class StringOpticExprOps[S](private val optic: Optic[S, String]) {
  def like(pattern: String): Expr[S, Boolean] = Expr.Like(Expr.col(optic), pattern)
}

// Boolean combinators — accept both Expr and SchemaExpr on the right
implicit final class ExprBooleanOps[S](private val self: Expr[S, Boolean]) {
  def &&(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.And(self, other)
  def &&(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.And(self, Expr.fromSchemaExpr(other))
  def ||(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.Or(self, other)
  def ||(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.Or(self, Expr.fromSchemaExpr(other))
  def unary_! : Expr[S, Boolean]                          = Expr.Not(self)
}

// Bridge: SchemaExpr on the left, Expr on the right
implicit final class SchemaExprBooleanBridge[S](private val self: SchemaExpr[S, Boolean]) {
  def &&(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.And(Expr.fromSchemaExpr(self), other)
  def ||(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.Or(Expr.fromSchemaExpr(self), other)
  def toExpr: Expr[S, Boolean] = Expr.fromSchemaExpr(self)
}
```

The bridge implicit classes are the key to ergonomic composition. When you write `Product.category.in("Electronics") && (Product.rating >= 4)`, the `&&` on `Expr[S, Boolean]` sees a `SchemaExpr[S, Boolean]` on the right and auto-translates it. Similarly, `(Product.rating >= 4) && Product.category.in("Electronics")` uses the `SchemaExpr` bridge to translate the left side. No explicit `.toExpr` is needed in most cases.

:::tip
The `.toExpr` method is still available for cases where you need to explicitly lift a `SchemaExpr[S, Boolean]` — for example, when building `CASE WHEN` branch conditions.
:::

## The Unified SQL Interpreter

With the `Expr` ADT, we write a single interpreter that handles all cases directly. Typed extension nodes stay typed; built-in `SchemaExpr` fragments delegate to a helper that reads `.dynamic` internally:

```scala
def columnName(optic: zio.blocks.schema.Optic[_, _]): String =
  optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

def columnName(path: DynamicOptic): String =
  path.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

def sqlLiteral[A](value: A, schema: Schema[A]): String = {
  val dv = schema.toDynamicValue(value)
  dv match {
    case p: DynamicValue.Primitive => p.value match {
      case _: PrimitiveValue.String  => s"'${value.toString.replace("'", "''")}'"
      case b: PrimitiveValue.Boolean => if (b.value) "TRUE" else "FALSE"
      case _                         => value.toString
    }
    case _ => value.toString
  }
}

def sqlLiteralDV(dv: DynamicValue): String = dv match {
  case DynamicValue.Primitive(pv) =>
    pv match {
      case PrimitiveValue.String(s)  => s"'${s.replace("'", "''")}'"
      case PrimitiveValue.Boolean(b) => if (b) "TRUE" else "FALSE"
      case PrimitiveValue.Int(n)     => n.toString
      case PrimitiveValue.Long(n)    => n.toString
      case PrimitiveValue.Double(n)  => n.toString
      case PrimitiveValue.Float(n)   => n.toString
      case PrimitiveValue.Short(n)   => n.toString
      case PrimitiveValue.Byte(n)    => n.toString
      case other                     => other.toString
    }
  case other => other.toString
}

def exprToSql[S, A](expr: Expr[S, A]): String = expr match {
  case Expr.Builtin(schemaExpr) => schemaExprToSql(schemaExpr)
  case Expr.Column(optic)       => columnName(optic)
  case Expr.Lit(value, schema)  => sqlLiteral(value, schema)

  case Expr.Relational(left, right, op) =>
    val sqlOp = op match {
      case RelOp.Equal              => "="
      case RelOp.NotEqual           => "<>"
      case RelOp.LessThan           => "<"
      case RelOp.LessThanOrEqual    => "<="
      case RelOp.GreaterThan        => ">"
      case RelOp.GreaterThanOrEqual => ">="
    }
    s"(${exprToSql(left)} $sqlOp ${exprToSql(right)})"

  case Expr.And(l, r) => s"(${exprToSql(l)} AND ${exprToSql(r)})"
  case Expr.Or(l, r)  => s"(${exprToSql(l)} OR ${exprToSql(r)})"
  case Expr.Not(e)    => s"NOT (${exprToSql(e)})"

  case Expr.Arithmetic(left, right, op) =>
    val sqlOp = op match {
      case ArithOp.Add      => "+"
      case ArithOp.Subtract => "-"
      case ArithOp.Multiply => "*"
    }
    s"(${exprToSql(left)} $sqlOp ${exprToSql(right)})"

  case Expr.StringConcat(l, r)          => s"CONCAT(${exprToSql(l)}, ${exprToSql(r)})"
  case Expr.StringRegexMatch(regex, s)  => s"(${exprToSql(s)} LIKE ${exprToSql(regex)})"
  case Expr.StringLength(s)             => s"LENGTH(${exprToSql(s)})"

  // SQL-specific
  case Expr.In(e, values, schema) =>
    s"${exprToSql(e)} IN (${values.map(v => sqlLiteral(v, schema)).mkString(", ")})"
  case Expr.Between(e, low, high, schema) =>
    s"(${exprToSql(e)} BETWEEN ${sqlLiteral(low, schema)} AND ${sqlLiteral(high, schema)})"
  case Expr.IsNull(e)          => s"${exprToSql(e)} IS NULL"
  case Expr.Like(e, pattern)   => s"${exprToSql(e)} LIKE '${pattern.replace("'", "''")}'"

  // Aggregates
  case Expr.Agg(func, e) => s"${func.name}(${exprToSql(e)})"

  // CASE WHEN
  case Expr.CaseWhen(branches, otherwise) =>
    val cases = branches.map { case (cond, value) =>
      s"WHEN ${exprToSql(cond)} THEN ${exprToSql(value)}"
    }.mkString(" ")
    val elseClause = otherwise.map(e => s" ELSE ${exprToSql(e)}").getOrElse("")
    s"CASE $cases$elseClause END"
}

def schemaExprToSql[S, A](expr: SchemaExpr[S, A]): String =
  toSqlDynamic(expr.dynamic)

def toSqlDynamic(expr: DynamicSchemaExpr): String = expr match {
  case DynamicSchemaExpr.Select(path)   => columnName(path)
  case DynamicSchemaExpr.Literal(value, _) => sqlLiteralDV(value)

  case DynamicSchemaExpr.Relational(left, right, op) =>
    val sqlOp = op match {
      case DynamicSchemaExpr.RelationalOperator.Equal              => "="
      case DynamicSchemaExpr.RelationalOperator.NotEqual           => "<>"
      case DynamicSchemaExpr.RelationalOperator.LessThan           => "<"
      case DynamicSchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
      case DynamicSchemaExpr.RelationalOperator.GreaterThan        => ">"
      case DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
    }
    s"(${toSqlDynamic(left)} $sqlOp ${toSqlDynamic(right)})"

  case DynamicSchemaExpr.Logical(left, right, op) =>
    val sqlOp = op match {
      case DynamicSchemaExpr.LogicalOperator.And => "AND"
      case DynamicSchemaExpr.LogicalOperator.Or  => "OR"
    }
    s"(${toSqlDynamic(left)} $sqlOp ${toSqlDynamic(right)})"

  case DynamicSchemaExpr.Not(inner) =>
    s"NOT (${toSqlDynamic(inner)})"

  case DynamicSchemaExpr.Arithmetic(left, right, op, _) =>
    val sqlOp = op match {
      case DynamicSchemaExpr.ArithmeticOperator.Add      => "+"
      case DynamicSchemaExpr.ArithmeticOperator.Subtract => "-"
      case DynamicSchemaExpr.ArithmeticOperator.Multiply => "*"
      case _                                             => "?"
    }
    s"(${toSqlDynamic(left)} $sqlOp ${toSqlDynamic(right)})"

  case DynamicSchemaExpr.StringConcat(left, right) =>
    s"CONCAT(${toSqlDynamic(left)}, ${toSqlDynamic(right)})"

  case DynamicSchemaExpr.StringRegexMatch(regex, string) =>
    s"(${toSqlDynamic(string)} LIKE ${toSqlDynamic(regex)})"

  case DynamicSchemaExpr.StringLength(string) =>
    s"LENGTH(${toSqlDynamic(string)})"

  case _ => "?"
}
```

The typed `sqlLiteral[A](value, schema)` uses the `Schema` carried by `Lit`, `In`, and `Between` to format values correctly — strings get quoted, booleans become `TRUE`/`FALSE`, numbers stay as-is. `sqlLiteralDV` is only needed inside `toSqlDynamic`, where built-in `SchemaExpr` nodes have already crossed into the dynamic representation.

## SQL-Specific Predicates

With the ADT, extensions, and interpreter in place, the new operators work directly on optics:

```scala
exprToSql(Product.category.in("Electronics", "Books", "Toys"))
// res0: String = "category IN ('Electronics', 'Books', 'Toys')"

exprToSql(Product.price.between(10.0, 100.0))
// res1: String = "(price BETWEEN 10.0 AND 100.0)"

exprToSql(Product.name.isNull)
// res2: String = "name IS NULL"

exprToSql(Product.name.isNotNull)
// res3: String = "NOT (name IS NULL)"

exprToSql(Product.name.like("Lap%"))
// res4: String = "name LIKE 'Lap%'"
```

Each extension method on `Optic` returns an `Expr` node. The interpreter handles it and produces the corresponding SQL fragment.

## Composing with SchemaExpr

The bridge implicit classes handle the translation automatically. You can freely mix `SchemaExpr` predicates (from `===`, `>`, etc.) with `Expr` predicates (from `.in`, `.between`, etc.) using `&&` and `||`:

```scala
// SchemaExpr values from built-in operators
val highRated: SchemaExpr[Product, Boolean] = Product.rating >= 4

// Expr values from extension methods
val inCategory: Expr[Product, Boolean] = Product.category.in("Electronics", "Books")
val priceRange: Expr[Product, Boolean] = Product.price.between(10.0, 500.0)

// Seamless composition — bridge auto-translates at the boundary
val combined: Expr[Product, Boolean] =
  inCategory && priceRange && highRated
```

```scala
exprToSql(combined)
// res5: String = "((category IN ('Electronics', 'Books') AND (price BETWEEN 10.0 AND 500.0)) AND (rating >= 4))"
```

The `&&` between `priceRange` (an `Expr`) and `highRated` (a `SchemaExpr`) triggers the overloaded `&&` that accepts `SchemaExpr` on the right. It calls `fromSchemaExpr` internally, so no explicit `.toExpr` is needed.

You can also start from a `SchemaExpr` on the left — the bridge implicit class handles it:

```scala
val query: Expr[Product, Boolean] =
  Product.category.in("Electronics", "Books") &&
  Product.price.between(10.0, 500.0) &&
  (Product.rating >= 4) &&
  Product.name.like("M%")
```

```scala
exprToSql(query)
// res6: String = "(((category IN ('Electronics', 'Books') AND (price BETWEEN 10.0 AND 500.0)) AND (rating >= 4)) AND name LIKE 'M%')"
```

A helper function to generate full SELECT statements from `Expr` predicates:

```scala
def selectWhere(table: String, predicate: Expr[_, Boolean]): String =
  s"SELECT * FROM $table WHERE ${exprToSql(predicate)}"
```

```scala
selectWhere("products", query)
// res7: String = "SELECT * FROM products WHERE (((category IN ('Electronics', 'Books') AND (price BETWEEN 10.0 AND 500.0)) AND (rating >= 4)) AND name LIKE 'M%')"
```

## Aggregate Expressions

The `Agg` node wraps any column expression with a type-safe aggregate function. The return type reflects SQL semantics: `COUNT` returns `Expr[S, Long]`, `SUM`/`AVG` return `Expr[S, Double]`, and `MIN`/`MAX` preserve the input type:

```scala
exprToSql(Expr.count(Expr.col(Product.name)))
// res8: String = "COUNT(name)"

exprToSql(Expr.avg(Expr.col(Product.price)))
// res9: String = "AVG(price)"

exprToSql(Expr.max(Expr.col(Product.rating)))
// res10: String = "MAX(rating)"
```

Aggregates compose with the rest of the ADT. Build a `GROUP BY` query by combining aggregate SQL fragments with a select builder:

```scala
def selectGroupBy(
  table: String,
  columns: List[String],
  groupBy: List[String],
  having: Option[String] = None
): String = {
  val base = s"SELECT ${columns.mkString(", ")} FROM $table GROUP BY ${groupBy.mkString(", ")}"
  having.fold(base)(h => s"$base HAVING $h")
}
```

```scala
selectGroupBy(
  "products",
  columns = List(
    "category",
    s"${exprToSql(Expr.count(Expr.col(Product.name)))} AS product_count",
    s"${exprToSql(Expr.avg(Expr.col(Product.price)))} AS avg_price"
  ),
  groupBy = List("category"),
  having = Some(s"${exprToSql(Expr.count(Expr.col(Product.name)))} > 2")
)
// res11: String = "SELECT category, COUNT(name) AS product_count, AVG(price) AS avg_price FROM products GROUP BY category HAVING COUNT(name) > 2"
```

## CASE WHEN Expressions

The `CaseWhen` node represents SQL's conditional expression. Use the `Expr.caseWhen` builder with `(condition -> result)` pairs and an optional `.otherwise` clause:

```scala
val priceLabel: Expr[Product, String] = Expr.caseWhen[Product, String](
  (Product.price > 100.0).toExpr  -> Expr.lit[Product, String]("expensive"),
  (Product.price > 10.0).toExpr   -> Expr.lit[Product, String]("moderate")
).otherwise(Expr.lit[Product, String]("cheap"))
```

```scala
exprToSql(priceLabel)
// res12: String = "CASE WHEN (price > 100.0) THEN 'expensive' WHEN (price > 10.0) THEN 'moderate' ELSE 'cheap' END"
```

`CASE WHEN` is useful for computed columns in SELECT lists:

```scala
val stockStatus: Expr[Product, String] = Expr.caseWhen[Product, String](
  (Product.inStock === true).toExpr -> Expr.lit[Product, String]("available")
).otherwise(Expr.lit[Product, String]("out of stock"))
```

```scala
val selectSql = s"SELECT name, price, ${exprToSql(priceLabel)} AS tier, ${exprToSql(stockStatus)} AS status FROM products"
// selectSql: String = "SELECT name, price, CASE WHEN (price > 100.0) THEN 'expensive' WHEN (price > 10.0) THEN 'moderate' ELSE 'cheap' END AS tier, CASE WHEN (inStock = TRUE) THEN 'available' ELSE 'out of stock' END AS status FROM products"
println(selectSql)
// SELECT name, price, CASE WHEN (price > 100.0) THEN 'expensive' WHEN (price > 10.0) THEN 'moderate' ELSE 'cheap' END AS tier, CASE WHEN (inStock = TRUE) THEN 'available' ELSE 'out of stock' END AS status FROM products
```

## Putting It Together

Here is a complete, self-contained example that defines the independent expression ADT, lifts built-in `SchemaExpr` values into it, and generates advanced SQL:

```scala
import zio.blocks.schema._

// --- Domain ---

case class Product(
  name: String,
  price: Double,
  category: String,
  inStock: Boolean,
  rating: Int
)

object Product extends CompanionOptics[Product] {
  implicit val schema: Schema[Product] = Schema.derived

  val name: Lens[Product, String]     = optic(_.name)
  val price: Lens[Product, Double]    = optic(_.price)
  val category: Lens[Product, String] = optic(_.category)
  val inStock: Lens[Product, Boolean] = optic(_.inStock)
  val rating: Lens[Product, Int]      = optic(_.rating)
}

// --- Independent Expr ADT ---

sealed trait Expr[S, A]

object Expr {
  final case class Builtin[S, A](schemaExpr: SchemaExpr[S, A]) extends Expr[S, A]
  final case class Column[S, A](optic: Optic[S, A]) extends Expr[S, A]
  final case class Lit[S, A](value: A, schema: Schema[A]) extends Expr[S, A]

  final case class Relational[S, A](left: Expr[S, A], right: Expr[S, A], op: RelOp) extends Expr[S, Boolean]
  final case class And[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Or[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Not[S](expr: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Arithmetic[S, A](left: Expr[S, A], right: Expr[S, A], op: ArithOp) extends Expr[S, A]
  final case class StringConcat[S](left: Expr[S, String], right: Expr[S, String]) extends Expr[S, String]
  final case class StringRegexMatch[S](regex: Expr[S, String], string: Expr[S, String]) extends Expr[S, Boolean]
  final case class StringLength[S](string: Expr[S, String]) extends Expr[S, Int]

  final case class In[S, A](expr: Expr[S, A], values: List[A], schema: Schema[A]) extends Expr[S, Boolean]
  final case class Between[S, A](expr: Expr[S, A], low: A, high: A, schema: Schema[A]) extends Expr[S, Boolean]
  final case class IsNull[S, A](expr: Expr[S, A]) extends Expr[S, Boolean]
  final case class Like[S](expr: Expr[S, String], pattern: String) extends Expr[S, Boolean]

  final case class Agg[S, A, B](function: AggFunction[A, B], expr: Expr[S, A]) extends Expr[S, B]
  final case class CaseWhen[S, A](
    branches: List[(Expr[S, Boolean], Expr[S, A])],
    otherwise: Option[Expr[S, A]]
  ) extends Expr[S, A]

  def col[S, A](optic: Optic[S, A]): Expr[S, A] = Column(optic)
  def lit[S, A](value: A)(implicit schema: Schema[A]): Expr[S, A] = Lit(value, schema)
  def count[S, A](expr: Expr[S, A]): Expr[S, Long]   = Agg(AggFunction.Count(), expr)
  def sum[S](expr: Expr[S, Double]): Expr[S, Double]  = Agg(AggFunction.Sum, expr)
  def avg[S](expr: Expr[S, Double]): Expr[S, Double]  = Agg(AggFunction.Avg, expr)
  def min[S, A](expr: Expr[S, A]): Expr[S, A]         = Agg(AggFunction.Min(), expr)
  def max[S, A](expr: Expr[S, A]): Expr[S, A]         = Agg(AggFunction.Max(), expr)

  def caseWhen[S, A](branches: (Expr[S, Boolean], Expr[S, A])*): CaseWhenBuilder[S, A] =
    CaseWhenBuilder(branches.toList)

  case class CaseWhenBuilder[S, A](branches: List[(Expr[S, Boolean], Expr[S, A])]) {
    def otherwise(value: Expr[S, A]): Expr[S, A] = CaseWhen(branches, Some(value))
    def end: Expr[S, A] = CaseWhen(branches, None)
  }

  def fromSchemaExpr[S, A](se: SchemaExpr[S, A]): Expr[S, A] = Builtin(se)
}

sealed trait RelOp
object RelOp {
  case object Equal              extends RelOp
  case object NotEqual           extends RelOp
  case object LessThan           extends RelOp
  case object LessThanOrEqual    extends RelOp
  case object GreaterThan        extends RelOp
  case object GreaterThanOrEqual extends RelOp
}

sealed trait ArithOp
object ArithOp {
  case object Add      extends ArithOp
  case object Subtract extends ArithOp
  case object Multiply extends ArithOp
}

sealed trait AggFunction[A, B] { def name: String }
object AggFunction {
  case class Count[A]() extends AggFunction[A, Long]        { val name = "COUNT" }
  case object Sum       extends AggFunction[Double, Double]  { val name = "SUM" }
  case object Avg       extends AggFunction[Double, Double]  { val name = "AVG" }
  case class Min[A]()   extends AggFunction[A, A]            { val name = "MIN" }
  case class Max[A]()   extends AggFunction[A, A]            { val name = "MAX" }
}

// --- Extension methods with bridge ---

implicit final class OpticExprOps[S, A](private val optic: Optic[S, A]) {
  def in(values: A*)(implicit schema: Schema[A]): Expr[S, Boolean]           = Expr.In(Expr.col(optic), values.toList, schema)
  def between(low: A, high: A)(implicit schema: Schema[A]): Expr[S, Boolean] = Expr.Between(Expr.col(optic), low, high, schema)
  def isNull: Expr[S, Boolean]                   = Expr.IsNull(Expr.col(optic))
  def isNotNull: Expr[S, Boolean]                = Expr.Not(Expr.IsNull(Expr.col(optic)))
}

implicit final class StringOpticExprOps[S](private val optic: Optic[S, String]) {
  def like(pattern: String): Expr[S, Boolean] = Expr.Like(Expr.col(optic), pattern)
}

implicit final class ExprBooleanOps[S](private val self: Expr[S, Boolean]) {
  def &&(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.And(self, other)
  def &&(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.And(self, Expr.fromSchemaExpr(other))
  def ||(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.Or(self, other)
  def ||(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.Or(self, Expr.fromSchemaExpr(other))
  def unary_! : Expr[S, Boolean]                          = Expr.Not(self)
}

implicit final class SchemaExprBooleanBridge[S](private val self: SchemaExpr[S, Boolean]) {
  def &&(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.And(Expr.fromSchemaExpr(self), other)
  def ||(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.Or(Expr.fromSchemaExpr(self), other)
  def toExpr: Expr[S, Boolean] = Expr.fromSchemaExpr(self)
}

// --- SQL rendering ---

def columnName(optic: zio.blocks.schema.Optic[_, _]): String =
  optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

def columnName(path: DynamicOptic): String =
  path.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

def sqlLiteral[A](value: A, schema: Schema[A]): String = {
  val dv = schema.toDynamicValue(value)
  dv match {
    case p: DynamicValue.Primitive => p.value match {
      case _: PrimitiveValue.String  => s"'${value.toString.replace("'", "''")}'"
      case b: PrimitiveValue.Boolean => if (b.value) "TRUE" else "FALSE"
      case _                         => value.toString
    }
    case _ => value.toString
  }
}

def sqlLiteralDV(dv: DynamicValue): String = dv match {
  case DynamicValue.Primitive(pv) =>
    pv match {
      case PrimitiveValue.String(s)  => s"'${s.replace("'", "''")}'"
      case PrimitiveValue.Boolean(b) => if (b) "TRUE" else "FALSE"
      case PrimitiveValue.Int(n)     => n.toString
      case PrimitiveValue.Long(n)    => n.toString
      case PrimitiveValue.Double(n)  => n.toString
      case PrimitiveValue.Float(n)   => n.toString
      case PrimitiveValue.Short(n)   => n.toString
      case PrimitiveValue.Byte(n)    => n.toString
      case other                     => other.toString
    }
  case other => other.toString
}

def exprToSql[S, A](expr: Expr[S, A]): String = expr match {
  case Expr.Builtin(schemaExpr) => schemaExprToSql(schemaExpr)
  case Expr.Column(optic)       => columnName(optic)
  case Expr.Lit(value, schema)  => sqlLiteral(value, schema)
  case Expr.Relational(left, right, op) =>
    val sqlOp = op match {
      case RelOp.Equal => "="; case RelOp.NotEqual => "<>"
      case RelOp.LessThan => "<"; case RelOp.LessThanOrEqual => "<="
      case RelOp.GreaterThan => ">"; case RelOp.GreaterThanOrEqual => ">="
    }
    s"(${exprToSql(left)} $sqlOp ${exprToSql(right)})"
  case Expr.And(l, r)                   => s"(${exprToSql(l)} AND ${exprToSql(r)})"
  case Expr.Or(l, r)                    => s"(${exprToSql(l)} OR ${exprToSql(r)})"
  case Expr.Not(e)                      => s"NOT (${exprToSql(e)})"
  case Expr.Arithmetic(left, right, op) =>
    val sqlOp = op match {
      case ArithOp.Add => "+"; case ArithOp.Subtract => "-"; case ArithOp.Multiply => "*"
    }
    s"(${exprToSql(left)} $sqlOp ${exprToSql(right)})"
  case Expr.StringConcat(l, r)         => s"CONCAT(${exprToSql(l)}, ${exprToSql(r)})"
  case Expr.StringRegexMatch(regex, s) => s"(${exprToSql(s)} LIKE ${exprToSql(regex)})"
  case Expr.StringLength(s)            => s"LENGTH(${exprToSql(s)})"
  case Expr.In(e, values, schema) =>
    s"${exprToSql(e)} IN (${values.map(v => sqlLiteral(v, schema)).mkString(", ")})"
  case Expr.Between(e, low, high, schema) =>
    s"(${exprToSql(e)} BETWEEN ${sqlLiteral(low, schema)} AND ${sqlLiteral(high, schema)})"
  case Expr.IsNull(e)        => s"${exprToSql(e)} IS NULL"
  case Expr.Like(e, pattern) => s"${exprToSql(e)} LIKE '${pattern.replace("'", "''")}'"
  case Expr.Agg(func, e)     => s"${func.name}(${exprToSql(e)})"
  case Expr.CaseWhen(branches, otherwise) =>
    val cases = branches.map { case (cond, value) =>
      s"WHEN ${exprToSql(cond)} THEN ${exprToSql(value)}"
    }.mkString(" ")
    val elseClause = otherwise.map(e => s" ELSE ${exprToSql(e)}").getOrElse("")
    s"CASE $cases$elseClause END"
}

def schemaExprToSql[S, A](expr: SchemaExpr[S, A]): String =
  toSqlDynamic(expr.dynamic)

def toSqlDynamic(expr: DynamicSchemaExpr): String = expr match {
  case DynamicSchemaExpr.Select(path)   => columnName(path)
  case DynamicSchemaExpr.Literal(value, _) => sqlLiteralDV(value)
  case DynamicSchemaExpr.Relational(left, right, op) =>
    val sqlOp = op match {
      case DynamicSchemaExpr.RelationalOperator.Equal              => "="
      case DynamicSchemaExpr.RelationalOperator.NotEqual           => "<>"
      case DynamicSchemaExpr.RelationalOperator.LessThan           => "<"
      case DynamicSchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
      case DynamicSchemaExpr.RelationalOperator.GreaterThan        => ">"
      case DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
    }
    s"(${toSqlDynamic(left)} $sqlOp ${toSqlDynamic(right)})"
  case DynamicSchemaExpr.Logical(left, right, op) =>
    val sqlOp = op match {
      case DynamicSchemaExpr.LogicalOperator.And => "AND"
      case DynamicSchemaExpr.LogicalOperator.Or  => "OR"
    }
    s"(${toSqlDynamic(left)} $sqlOp ${toSqlDynamic(right)})"
  case DynamicSchemaExpr.Not(inner) =>
    s"NOT (${toSqlDynamic(inner)})"
  case DynamicSchemaExpr.Arithmetic(left, right, op, _) =>
    val sqlOp = op match {
      case DynamicSchemaExpr.ArithmeticOperator.Add      => "+"
      case DynamicSchemaExpr.ArithmeticOperator.Subtract => "-"
      case DynamicSchemaExpr.ArithmeticOperator.Multiply => "*"
      case _                                             => "?"
    }
    s"(${toSqlDynamic(left)} $sqlOp ${toSqlDynamic(right)})"
  case DynamicSchemaExpr.StringConcat(left, right) =>
    s"CONCAT(${toSqlDynamic(left)}, ${toSqlDynamic(right)})"
  case DynamicSchemaExpr.StringRegexMatch(regex, string) =>
    s"(${toSqlDynamic(string)} LIKE ${toSqlDynamic(regex)})"
  case DynamicSchemaExpr.StringLength(string) =>
    s"LENGTH(${toSqlDynamic(string)})"
  case _ => "?"
}

// --- Usage ---

// 1. SQL-specific predicates — seamless composition
val q1 = Product.category.in("Electronics", "Books") &&
  Product.price.between(10.0, 500.0) &&
  (Product.rating >= 4) &&
  Product.name.like("M%")

println(s"SELECT * FROM products WHERE ${exprToSql(q1)}")

// 2. Type-safe aggregation
val countExpr: Expr[Product, Long]   = Expr.count(Expr.col(Product.name))
val avgExpr: Expr[Product, Double]   = Expr.avg(Expr.col(Product.price))
val countSql = exprToSql(countExpr)
val avgSql   = exprToSql(avgExpr)
println(s"SELECT category, $countSql AS cnt, $avgSql AS avg_price FROM products GROUP BY category HAVING $countSql > 2")

// 3. CASE WHEN
val tier = Expr.caseWhen[Product, String](
  (Product.price > 100.0).toExpr -> Expr.lit[Product, String]("expensive"),
  (Product.price > 10.0).toExpr  -> Expr.lit[Product, String]("moderate")
).otherwise(Expr.lit[Product, String]("cheap"))

println(s"SELECT name, price, ${exprToSql(tier)} AS tier FROM products")
```

## Going Further

- **[Part 1: Expressions](./query-dsl-reified-optics.md)** -- Building query expressions with reified optics
- **[Part 2: SQL Generation](./query-dsl-sql.md)** -- Translating built-in expressions to SQL
- **[Part 4: A Fluent SQL Builder](./query-dsl-fluent-builder.md)** -- Type-safe SELECT, UPDATE, INSERT, DELETE with seamless condition mixing
- **[SchemaExpr Reference](../reference/schema/schema-expr.md)** -- Full API coverage of expression types
- **[Optics Reference](../reference/schema/optics.md)** -- Lens, Prism, Optional, and Traversal

The translation pattern shown here extends to any domain where `SchemaExpr` falls short. The same approach works for MongoDB operators (`$in`, `$exists`, `$elemMatch`), Elasticsearch queries (`terms`, `range`, `exists`), or GraphQL filters. Define an independent ADT, provide a `fromSchemaExpr` lift for built-in expressions, add your domain-specific nodes, and let your interpreter inspect `.dynamic` only inside its internal translation helpers.
