# Query DSL with Reified Optics — Part 2: SQL Generation

> In this guide, we will build a SQL query generator that translates ZIO Blocks' `SchemaExpr` expression trees into SQL WHERE clauses, SELECT statements, and parameterized queries. By the end, you will have an interpreter that takes any `SchemaExpr`-based query and produces executable SQL, covering comparisons, boolean logic, arithmetic, string operations, nested structures, and safe parameterization.

This is Part 2 of the Query DSL series. [Part 1](./query-dsl-reified-optics.md) covered building query expressions with reified optics. Here, we interpret those expressions as SQL.

**What we'll cover:**

- Interpreting `SchemaExpr` while keeping the typed API at the boundary
- Extracting column names from optic paths using `DynamicOptic`
- Translating relational, logical, arithmetic, and string operations to SQL
- Building complete `SELECT ... FROM ... WHERE ...` statements
- Generating parameterized queries for SQL injection safety
- Handling nested structures with table-qualified column names

## The Problem

In Part 1, we built composable query expressions as data -- `SchemaExpr` values that can be inspected, combined, and evaluated in-memory. But in real applications, data lives in databases. You need to translate those same queries into SQL.

The naive approach is to write SQL strings by hand for every query:

```scala
// Manual SQL for each query variant
def findProducts(category: Option[String], maxPrice: Option[Double], inStock: Option[Boolean]): String = {
  val conditions = List.newBuilder[String]
  category.foreach(c => conditions += s"category = '$c'")      // SQL injection!
  maxPrice.foreach(p => conditions += s"price < $p")
  inStock.foreach(s => conditions += s"in_stock = $s")
  val where = conditions.result().mkString(" AND ")
  s"SELECT * FROM products" + (if (where.nonEmpty) s" WHERE $where" else "")
}
```

This is fragile, repetitive, and vulnerable to SQL injection. Every new query shape requires new string-building code. The query logic is duplicated -- once as a `SchemaExpr` for in-memory filtering, and again as hand-written SQL for the database.

`SchemaExpr` is the user-facing query API. Internally it wraps a `DynamicSchemaExpr` — a sealed trait whose cases represent the full expression AST. That means we can write a single interpreter that accepts `SchemaExpr`, then crosses into the dynamic AST internally to translate *any* query expression into SQL. Write the interpreter once, and every query you build with the Part 1 DSL automatically gets a SQL translation.

## Prerequisites

This guide builds on [Part 1: Expressions](./query-dsl-reified-optics.md). You should be comfortable building `SchemaExpr` values with optic operators (`===`, `>`, `&&`, etc.).

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "0.0.56"
```

```scala
import zio.blocks.schema._
```

## Domain Setup

We reuse the product catalog domain from Part 1:

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

  val name: Lens[Product, String]      = optic(_.name)
  val price: Lens[Product, Double]     = optic(_.price)
  val category: Lens[Product, String]  = optic(_.category)
  val inStock: Lens[Product, Boolean]  = optic(_.inStock)
  val rating: Lens[Product, Int]       = optic(_.rating)
}
```

## The SchemaExpr API

Before we build the interpreter, keep the API boundary in mind: application code builds `SchemaExpr[A, B]` values, while interpreter code may inspect the underlying `DynamicSchemaExpr` through `.dynamic`.

```
SchemaExpr[A, B]                        -- user-facing, typed API
└── .dynamic: DynamicSchemaExpr         -- interpreter/runtime boundary
    ├── Select(path: DynamicOptic)      -- field reference
    ├── Literal(value: DynamicValue, schema: Schema[_])  -- constant value
    ├── Relational(left, right, op)     -- comparisons
    ├── Logical(left, right, op)        -- boolean operators
    ├── Not(expr)                       -- negation
    ├── Arithmetic(left, right, op, _)  -- numeric operators
    ├── StringConcat(left, right)       -- string concatenation
    ├── StringRegexMatch(regex, string) -- pattern matching
    └── StringLength(string)            -- string length
```

Most users never need to construct `DynamicSchemaExpr` directly. The normal workflow is:

1. Build a typed `SchemaExpr` with optics and operators.
2. Pass that `SchemaExpr` to your interpreter.
3. Let the interpreter read `.dynamic` internally.

The dynamic cases are still worth understanding because they are what your interpreter will pattern-match on:

```
RelationalOperator
├── LessThan
├── GreaterThan
├── LessThanOrEqual
├── GreaterThanOrEqual
├── Equal
└── NotEqual

LogicalOperator
├── And
└── Or

ArithmeticOperator
├── Add
├── Subtract
└── Multiply
```

Each dynamic case carries enough information to produce SQL: `Select` nodes carry field paths, `Literal` nodes carry values, and operator nodes carry the operation type. Our interpreter walks this tree and emits SQL fragments.

## Extracting Column Names from Optics

The first challenge is turning a reified optic into a SQL column name. Every `Optic[S, A]` has a `toDynamic` method that returns a `DynamicOptic` -- a sequence of path nodes. For a lens like `Product.price`, the path is `[Field("price")]`. We extract the field name from the last `Field` node:

```scala
def columnName(optic: zio.blocks.schema.Optic[?, ?]): String = {
  val nodes = optic.toDynamic.nodes
  nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")
}

def columnName(path: DynamicOptic): String =
  path.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")
```

This converts the optic path to a column name. For a simple field like `Product.price`, it produces `"price"`. For a nested path, it joins field names with underscores (we will refine this for table-qualified names later).

```scala
columnName(Product.price)
// res0: String = "price"
columnName(Product.name)
// res1: String = "name"
columnName(Product.category)
// res2: String = "category"
```

## Translating Literals to SQL

Once we cross the interpreter boundary, literal values appear as `DynamicValue`. We need a function to format them as SQL:

```scala
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
```

## Building the SQL Interpreter

Now we build the core interpreter. The public entry point accepts `SchemaExpr`; the internal helper does the `DynamicSchemaExpr` pattern matching:

```scala
def toSql[A, B](expr: SchemaExpr[A, B]): String = toSqlDynamic(expr.dynamic)

private def toSqlDynamic(expr: DynamicSchemaExpr): String = expr match {

  // Field reference → column name
  case DynamicSchemaExpr.Select(path) =>
    columnName(path)

  // Constant value → SQL literal
  case DynamicSchemaExpr.Literal(value, _) =>
    sqlLiteralDV(value)

  // Comparison operators → SQL relational operators
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

  // Boolean operators → AND / OR
  case DynamicSchemaExpr.Logical(left, right, op) =>
    val sqlOp = op match {
      case DynamicSchemaExpr.LogicalOperator.And => "AND"
      case DynamicSchemaExpr.LogicalOperator.Or  => "OR"
    }
    s"(${toSqlDynamic(left)} $sqlOp ${toSqlDynamic(right)})"

  // Negation → NOT
  case DynamicSchemaExpr.Not(inner) =>
    s"NOT (${toSqlDynamic(inner)})"

  // Arithmetic → SQL math operators
  case DynamicSchemaExpr.Arithmetic(left, right, op, _) =>
    val sqlOp = op match {
      case DynamicSchemaExpr.ArithmeticOperator.Add      => "+"
      case DynamicSchemaExpr.ArithmeticOperator.Subtract => "-"
      case DynamicSchemaExpr.ArithmeticOperator.Multiply => "*"
      case _                                             => "?"
    }
    s"(${toSqlDynamic(left)} $sqlOp ${toSqlDynamic(right)})"

  // String concatenation → CONCAT()
  case DynamicSchemaExpr.StringConcat(left, right) =>
    s"CONCAT(${toSqlDynamic(left)}, ${toSqlDynamic(right)})"

  // Regex match → column LIKE pattern (simplified)
  case DynamicSchemaExpr.StringRegexMatch(regex, string) =>
    s"(${toSqlDynamic(string)} LIKE ${toSqlDynamic(regex)})"

  // String length → LENGTH()
  case DynamicSchemaExpr.StringLength(string) =>
    s"LENGTH(${toSqlDynamic(string)})"

  case _ => "?"
}
```

The mapping from `DynamicSchemaExpr` to SQL is direct, but that dynamic matching stays inside the interpreter implementation:

| DynamicSchemaExpr Case | SQL Output                           |
|------------------------|--------------------------------------|
| `Select(path)`         | Column name from `DynamicOptic`      |
| `Literal(value, schema)` | SQL literal (`'text'`, `42`, `TRUE`) |
| `Relational(_, _, op)` | `=`, `<>`, `<`, `>`, `<=`, `>=`      |
| `Logical(_, _, op)`    | `AND`, `OR`                          |
| `Not(expr)`            | `NOT (...)`                          |
| `Arithmetic(_, _, op, _)` | `+`, `-`, `*`                     |
| `StringConcat`         | `CONCAT(a, b)`                       |
| `StringRegexMatch`     | `LIKE` (pattern matching)            |
| `StringLength`         | `LENGTH(col)`                        |

## Generating SQL from Queries

Now we can translate any query expression into a SQL WHERE clause. Let's try it with the queries from Part 1:

```scala
val isElectronics = Product.category === "Electronics"
val expensiveItems = Product.price > 100.0
val highRated = Product.rating >= 4
```

```scala
toSql(isElectronics)
// res3: String = "(category = 'Electronics')"
toSql(expensiveItems)
// res4: String = "(price > 100.0)"
toSql(highRated)
// res5: String = "(rating >= 4)"
```

## Compound Queries

Boolean combinators translate to `AND`, `OR`, and `NOT`:

```scala
val affordableElectronics =
  (Product.category === "Electronics") && (Product.price < 500.0)

val goodDeal =
  (Product.price < 10.0) || (Product.rating >= 5)

val outOfStock = !Product.inStock
```

```scala
toSql(affordableElectronics)
// res6: String = "((category = 'Electronics') AND (price < 500.0))"
toSql(goodDeal)
// res7: String = "((price < 10.0) OR (rating >= 5))"
toSql(outOfStock)
// res8: String = "NOT (inStock)"
```

Complex nested queries compose naturally:

```scala
val complexQuery =
  ((Product.category === "Electronics") && (Product.price < 500.0)) ||
  ((Product.category === "Office") && (Product.rating >= 4))
```

```scala
toSql(complexQuery)
// res9: String = "(((category = 'Electronics') AND (price < 500.0)) OR ((category = 'Office') AND (rating >= 4)))"
```

## Arithmetic in SQL

Arithmetic expressions translate directly to SQL math:

```scala
val discountedPrice = Product.price * 0.9
val priceWithTax = Product.price * 1.08
```

```scala
toSql(discountedPrice)
// res10: String = "(price * 0.9)"
toSql(priceWithTax)
// res11: String = "(price * 1.08)"
```

## String Operations in SQL

String operations map to SQL string functions:

```scala
// Regex match → LIKE
val startsWithL = Product.name.matches("L%")

// Concatenation → CONCAT()
val labeledName = Product.name.concat(" [SALE]")

// String length → LENGTH()
val nameLength = Product.name.length
```

```scala
toSql(startsWithL)
// res12: String = "(name LIKE 'L%')"
toSql(labeledName)
// res13: String = "CONCAT(name, ' [SALE]')"
toSql(nameLength)
// res14: String = "LENGTH(name)"
```

:::tip
The `matches` operator uses regex syntax in the `SchemaExpr` evaluator, but SQL's `LIKE` uses `%` and `_` wildcards. When building queries intended for SQL, use SQL-style patterns (`L%` instead of `L.*`). If you need full regex support, replace the `LIKE` translation with your database's regex function (e.g., `REGEXP` in MySQL, `~` in PostgreSQL).
:::

## Building Complete SELECT Statements

With the `toSql` interpreter, building complete SQL statements is straightforward:

```scala
def select(table: String, predicate: SchemaExpr[?, Boolean]): String =
  s"SELECT * FROM $table WHERE ${toSql(predicate)}"

def selectColumns(table: String, columns: List[String], predicate: SchemaExpr[?, Boolean]): String =
  s"SELECT ${columns.mkString(", ")} FROM $table WHERE ${toSql(predicate)}"

def selectWithLimit(
  table: String,
  predicate: SchemaExpr[?, Boolean],
  orderBy: Option[String] = None,
  limit: Option[Int] = None
): String = {
  val base = s"SELECT * FROM $table WHERE ${toSql(predicate)}"
  val ordered = orderBy.fold(base)(col => s"$base ORDER BY $col")
  limit.fold(ordered)(n => s"$ordered LIMIT $n")
}
```

```scala
val query = (Product.category === "Electronics") && (Product.inStock === true) && (Product.price < 500.0)
```

```scala
select("products", query)
// res15: String = "SELECT * FROM products WHERE (((category = 'Electronics') AND (inStock = TRUE)) AND (price < 500.0))"

selectColumns("products", List("name", "price"), query)
// res16: String = "SELECT name, price FROM products WHERE (((category = 'Electronics') AND (inStock = TRUE)) AND (price < 500.0))"

selectWithLimit("products", query, orderBy = Some("price ASC"), limit = Some(10))
// res17: String = "SELECT * FROM products WHERE (((category = 'Electronics') AND (inStock = TRUE)) AND (price < 500.0)) ORDER BY price ASC LIMIT 10"
```

## Parameterized Queries

The `toSql` function above inlines literal values directly into the SQL string. For production use, you need parameterized queries to prevent SQL injection. We modify the interpreter to collect parameters separately:

```scala
case class SqlQuery(sql: String, params: List[Any])

def toParameterized[A, B](expr: SchemaExpr[A, B]): SqlQuery = toParameterizedDynamic(expr.dynamic)

private def toParameterizedDynamic(expr: DynamicSchemaExpr): SqlQuery = expr match {

  case DynamicSchemaExpr.Select(path) =>
    SqlQuery(columnName(path), Nil)

  case DynamicSchemaExpr.Literal(value, _) =>
    val param = value match {
      case DynamicValue.Primitive(pv) => pv match {
        case PrimitiveValue.String(s)     => s
        case PrimitiveValue.Boolean(b)    => b
        case PrimitiveValue.Int(n)        => n
        case PrimitiveValue.Long(n)       => n
        case PrimitiveValue.Double(n)     => n
        case PrimitiveValue.Float(n)      => n
        case PrimitiveValue.Short(n)      => n
        case PrimitiveValue.Byte(n)       => n
        case PrimitiveValue.BigInt(n)     => n
        case PrimitiveValue.BigDecimal(n) => n
        case PrimitiveValue.Char(c)       => c
        case other                        => other.toString
      }
      case other => other.toString
    }
    SqlQuery("?", List(param))

  case DynamicSchemaExpr.Relational(left, right, op) =>
    val l = toParameterizedDynamic(left); val r = toParameterizedDynamic(right)
    val sqlOp = op match {
      case DynamicSchemaExpr.RelationalOperator.Equal              => "="
      case DynamicSchemaExpr.RelationalOperator.NotEqual           => "<>"
      case DynamicSchemaExpr.RelationalOperator.LessThan           => "<"
      case DynamicSchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
      case DynamicSchemaExpr.RelationalOperator.GreaterThan        => ">"
      case DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
    }
    SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)

  case DynamicSchemaExpr.Logical(left, right, op) =>
    val l = toParameterizedDynamic(left); val r = toParameterizedDynamic(right)
    val sqlOp = op match {
      case DynamicSchemaExpr.LogicalOperator.And => "AND"
      case DynamicSchemaExpr.LogicalOperator.Or  => "OR"
    }
    SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)

  case DynamicSchemaExpr.Not(inner) =>
    val i = toParameterizedDynamic(inner)
    SqlQuery(s"NOT (${i.sql})", i.params)

  case DynamicSchemaExpr.Arithmetic(left, right, op, _) =>
    val l = toParameterizedDynamic(left); val r = toParameterizedDynamic(right)
    val sqlOp = op match {
      case DynamicSchemaExpr.ArithmeticOperator.Add      => "+"
      case DynamicSchemaExpr.ArithmeticOperator.Subtract => "-"
      case DynamicSchemaExpr.ArithmeticOperator.Multiply => "*"
      case _                                             => "?"
    }
    SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)

  case DynamicSchemaExpr.StringConcat(left, right) =>
    val l = toParameterizedDynamic(left); val r = toParameterizedDynamic(right)
    SqlQuery(s"CONCAT(${l.sql}, ${r.sql})", l.params ++ r.params)

  case DynamicSchemaExpr.StringRegexMatch(regex, string) =>
    val s = toParameterizedDynamic(string); val r = toParameterizedDynamic(regex)
    SqlQuery(s"(${s.sql} LIKE ${r.sql})", s.params ++ r.params)

  case DynamicSchemaExpr.StringLength(string) =>
    val s = toParameterizedDynamic(string)
    SqlQuery(s"LENGTH(${s.sql})", s.params)

  case _ => SqlQuery("?", Nil)
}
```

Now literals become `?` placeholders, with the actual values collected in a parameter list:

```scala
val q = (Product.category === "Electronics") && (Product.price < 500.0) && (Product.rating >= 4)
val paramQuery = toParameterized(q)
```

```scala
paramQuery.sql
// res18: String = "(((category = ?) AND (price < ?)) AND (rating >= ?))"
paramQuery.params
// res19: List[Any] = List("Electronics", 500.0, 4)
```

You can use this with JDBC's `PreparedStatement`:

```scala
val ps = connection.prepareStatement(s"SELECT * FROM products WHERE ${paramQuery.sql}")
paramQuery.params.zipWithIndex.foreach { case (value, idx) =>
  value match {
    case s: String  => ps.setString(idx + 1, s)
    case d: Double  => ps.setDouble(idx + 1, d)
    case i: Int     => ps.setInt(idx + 1, i)
    case b: Boolean => ps.setBoolean(idx + 1, b)
    case l: Long    => ps.setLong(idx + 1, l)
  }
}
val rs = ps.executeQuery()
```

:::warning
Always use parameterized queries for user-supplied values. The inline `toSql` function is suitable for logging and debugging, but use `toParameterized` for actual database execution.
:::

## Nested Structures and Table-Qualified Columns

When domain types have nested structures, optic paths contain multiple `Field` nodes. For SQL, these often map to JOIN-based queries with table-qualified column names.

```scala
import zio.blocks.schema._

case class Address(city: String, country: String)
object Address {
  implicit val schema: Schema[Address] = Schema.derived
}

case class Seller(name: String, address: Address, rating: Double)
object Seller extends CompanionOptics[Seller] {
  implicit val schema: Schema[Seller] = Schema.derived

  val name: Lens[Seller, String]       = optic(_.name)
  val rating: Lens[Seller, Double]     = optic(_.rating)
  val city: Lens[Seller, String]       = optic(_.address.city)
  val country: Lens[Seller, String]    = optic(_.address.country)
}
```

The lens `Seller.city` has the path `[Field("address"), Field("city")]`. We can translate multi-segment paths into table-qualified column names:

```scala
def qualifiedColumnName(optic: zio.blocks.schema.Optic[?, ?]): String = {
  val fields = optic.toDynamic.nodes.collect {
    case f: DynamicOptic.Node.Field => f.name
  }
  // Single field: use as-is. Multiple fields: table.column convention
  if (fields.length <= 1) fields.mkString
  else s"${fields.init.mkString("_")}.${fields.last}"
}
```

```scala
qualifiedColumnName(Seller.name)
// res21: String = "name"
qualifiedColumnName(Seller.city)
// res22: String = "address.city"
qualifiedColumnName(Seller.country)
// res23: String = "address.country"
```

This produces `address.city` for nested fields, which maps naturally to a SQL JOIN:

```sql
SELECT sellers.*, address.city, address.country
FROM sellers
JOIN addresses AS address ON sellers.id = address.seller_id
WHERE address.city = 'Berlin' AND sellers.rating >= 4.0
```

To generate full JOIN queries, you would extend the interpreter to inspect the optic paths, detect multi-segment paths, and emit appropriate JOIN clauses. The path structure from `DynamicOptic` gives you all the information needed.

## Putting It Together

Here is a complete, self-contained example that defines a domain, builds queries, and generates both inline SQL and parameterized queries:

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

  val name: Lens[Product, String]      = optic(_.name)
  val price: Lens[Product, Double]     = optic(_.price)
  val category: Lens[Product, String]  = optic(_.category)
  val inStock: Lens[Product, Boolean]  = optic(_.inStock)
  val rating: Lens[Product, Int]       = optic(_.rating)
}

// --- SQL Interpreter ---

def columnName(optic: zio.blocks.schema.Optic[?, ?]): String =
  optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

def columnName(path: DynamicOptic): String =
  path.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

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

def toSql[A, B](expr: SchemaExpr[A, B]): String = toSqlDynamic(expr.dynamic)

private def toSqlDynamic(expr: DynamicSchemaExpr): String = expr match {
  case DynamicSchemaExpr.Select(path)              => columnName(path)
  case DynamicSchemaExpr.Literal(value, _)         => sqlLiteralDV(value)
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
  case DynamicSchemaExpr.Not(inner)                      => s"NOT (${toSqlDynamic(inner)})"
  case DynamicSchemaExpr.Arithmetic(left, right, op, _) =>
    val sqlOp = op match {
      case DynamicSchemaExpr.ArithmeticOperator.Add      => "+"
      case DynamicSchemaExpr.ArithmeticOperator.Subtract => "-"
      case DynamicSchemaExpr.ArithmeticOperator.Multiply => "*"
      case _                                             => "?"
    }
    s"(${toSqlDynamic(left)} $sqlOp ${toSqlDynamic(right)})"
  case DynamicSchemaExpr.StringConcat(left, right)       => s"CONCAT(${toSqlDynamic(left)}, ${toSqlDynamic(right)})"
  case DynamicSchemaExpr.StringRegexMatch(regex, string) => s"(${toSqlDynamic(string)} LIKE ${toSqlDynamic(regex)})"
  case DynamicSchemaExpr.StringLength(string)            => s"LENGTH(${toSqlDynamic(string)})"
  case _                                                 => "?"
}

// --- Parameterized queries ---

case class SqlQuery(sql: String, params: List[Any])

def toParameterized[A, B](expr: SchemaExpr[A, B]): SqlQuery = toParameterizedDynamic(expr.dynamic)

private def toParameterizedDynamic(expr: DynamicSchemaExpr): SqlQuery = expr match {
  case DynamicSchemaExpr.Select(path)   => SqlQuery(columnName(path), Nil)
  case DynamicSchemaExpr.Literal(value, _) =>
    val param = value match {
      case DynamicValue.Primitive(pv) => pv match {
        case PrimitiveValue.String(s)     => s
        case PrimitiveValue.Boolean(b)    => b
        case PrimitiveValue.Int(n)        => n
        case PrimitiveValue.Long(n)       => n
        case PrimitiveValue.Double(n)     => n
        case PrimitiveValue.Float(n)      => n
        case PrimitiveValue.Short(n)      => n
        case PrimitiveValue.Byte(n)       => n
        case PrimitiveValue.BigInt(n)     => n
        case PrimitiveValue.BigDecimal(n) => n
        case PrimitiveValue.Char(c)       => c
        case other                        => other.toString
      }
      case other => other.toString
    }
    SqlQuery("?", List(param))
  case DynamicSchemaExpr.Relational(left, right, op) =>
    val l = toParameterizedDynamic(left); val r = toParameterizedDynamic(right)
    val sqlOp = op match {
      case DynamicSchemaExpr.RelationalOperator.Equal              => "="
      case DynamicSchemaExpr.RelationalOperator.NotEqual           => "<>"
      case DynamicSchemaExpr.RelationalOperator.LessThan           => "<"
      case DynamicSchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
      case DynamicSchemaExpr.RelationalOperator.GreaterThan        => ">"
      case DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
    }
    SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)
  case DynamicSchemaExpr.Logical(left, right, op) =>
    val l = toParameterizedDynamic(left); val r = toParameterizedDynamic(right)
    val sqlOp = op match {
      case DynamicSchemaExpr.LogicalOperator.And => "AND"
      case DynamicSchemaExpr.LogicalOperator.Or  => "OR"
    }
    SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)
  case DynamicSchemaExpr.Not(inner) =>
    val i = toParameterizedDynamic(inner)
    SqlQuery(s"NOT (${i.sql})", i.params)
  case DynamicSchemaExpr.Arithmetic(left, right, op, _) =>
    val l = toParameterizedDynamic(left); val r = toParameterizedDynamic(right)
    val sqlOp = op match {
      case DynamicSchemaExpr.ArithmeticOperator.Add      => "+"
      case DynamicSchemaExpr.ArithmeticOperator.Subtract => "-"
      case DynamicSchemaExpr.ArithmeticOperator.Multiply => "*"
      case _                                             => "?"
    }
    SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)
  case DynamicSchemaExpr.StringConcat(left, right) =>
    val l = toParameterizedDynamic(left); val r = toParameterizedDynamic(right)
    SqlQuery(s"CONCAT(${l.sql}, ${r.sql})", l.params ++ r.params)
  case DynamicSchemaExpr.StringRegexMatch(regex, string) =>
    val s = toParameterizedDynamic(string); val r = toParameterizedDynamic(regex)
    SqlQuery(s"(${s.sql} LIKE ${r.sql})", s.params ++ r.params)
  case DynamicSchemaExpr.StringLength(string) =>
    val s = toParameterizedDynamic(string)
    SqlQuery(s"LENGTH(${s.sql})", s.params)
  case _ => SqlQuery("?", Nil)
}

// --- Complete SELECT builder ---

def select(table: String, predicate: SchemaExpr[?, Boolean]): String =
  s"SELECT * FROM $table WHERE ${toSql(predicate)}"

// --- Usage ---

val query =
  (Product.category === "Electronics") &&
  (Product.inStock === true) &&
  (Product.price < 500.0) &&
  (Product.rating >= 4)

// Inline SQL for debugging
println(select("products", query))
// SELECT * FROM products WHERE (((category = 'Electronics') AND (inStock = TRUE)) AND (price < 500.0)) AND (rating >= 4))

// Parameterized SQL for execution
val pq = toParameterized(query)
println(s"SQL:    ${pq.sql}")
println(s"Params: ${pq.params}")
// SQL:    (((category = ?) AND (inStock = ?)) AND (price < ?)) AND (rating >= ?))
// Params: List(Electronics, true, 500.0, 4)

// String operations in SQL
println(toSql(Product.name.matches("L%")))
// (name LIKE 'L%')

// Arithmetic in SQL
println(toSql(Product.price * 0.9))
// (price * 0.9)
```

## Upsert (ON CONFLICT)

Upsert (insert-or-update) combines an `INSERT` with a conflict handler so the
statement is idempotent. When a row with the conflicting key already exists, the
database either skips the insert or updates specified columns.

All identifiers (table name, column names, conflict column) are validated through
`SqlIdentifier.validate` and assignment columns are additionally checked against
`Table.columns`. Invalid or unknown names throw `IllegalArgumentException` at
build time, not at execution time.

### Table-aware builders

The high-level `Upsert` builders accept a `Table[A]` and an entity. The table
provides column names and a codec that extracts `DbValue` parameters.

`Upsert.insertDoNothing` builds `INSERT ... ON CONFLICT ("id") DO NOTHING`:

```scala
import zio.blocks.sql.*
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val table: Table[User] = Table.derived[User]
val user = User(42, "Alice", "alice@example.com")

// INSERT INTO "user" ("id", "name", "email") VALUES (?, ?, ?) ON CONFLICT ("id") DO NOTHING
val frag: Frag = Upsert.insertDoNothing(table, user, conflictColumn = "id")
```

`Upsert.insertDoUpdate` builds `INSERT ... ON CONFLICT ("id") DO UPDATE SET`
for **all** non-conflict columns:

```scala
import zio.blocks.sql.*
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val table: Table[User] = Table.derived[User]
val user = User(42, "Alice", "alice@example.com")

// INSERT INTO "user" ("id", "name", "email") VALUES (?, ?, ?)
//   ON CONFLICT ("id") DO UPDATE SET "name" = ?, "email" = ?
val frag: Frag = Upsert.insertDoUpdate(table, user, conflictColumn = "id")
```

Pass `updateColumns` to restrict which columns are overwritten on conflict:

```scala
import zio.blocks.sql.*
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val table: Table[User] = Table.derived[User]
val user = User(42, "Alice", "alice@example.com")

// Only "name" is updated on conflict; "email" keeps its original value
val frag: Frag = Upsert.insertDoUpdate(table, user, conflictColumn = "id", updateColumns = Seq("name"))
```

### Low-level builders

When you need full control over column names and values (e.g. computed or
transformed data), use the low-level `Upsert.doNothing`, `Upsert.doNothingRaw`,
and `Upsert.doUpdate` builders:

```scala
import zio.blocks.sql.*

// Low-level DO NOTHING with explicit columns and values
val frag1: Frag = Upsert.doNothing(
  tableName   = "users",
  columns     = IndexedSeq("id", "name", "email"),
  values      = IndexedSeq(DbValue.DbInt(1), DbValue.DbString("Bob"), DbValue.DbString("bob@example.com")),
  conflictColumn = "id"
)

// Comma-joined column string variant
val frag2: Frag = Upsert.doNothingRaw(
  tableName   = "users",
  allColumns  = "id, name, email",
  values      = IndexedSeq(DbValue.DbInt(1), DbValue.DbString("Bob"), DbValue.DbString("bob@example.com")),
  conflictColumn = "id"
)

// Low-level DO UPDATE with explicit assignments
val frag3: Frag = Upsert.doUpdate(
  tableName      = "users",
  columns        = IndexedSeq("id", "name", "email"),
  values         = IndexedSeq(DbValue.DbInt(1), DbValue.DbString("Bob"), DbValue.DbString("bob@example.com")),
  conflictColumn = "id",
  assignments    = IndexedSeq("name" -> DbValue.DbString("Bob"), "email" -> DbValue.DbString("bob@example.com"))
)
```

### Suffix builders

To append an `ON CONFLICT` clause to an existing `INSERT` `Frag`, use the suffix
builders:

```scala
import zio.blocks.sql.*

val base: Frag = Frag.literal("INSERT INTO users (id, name) VALUES (?, ?)")

// Append DO NOTHING suffix
val withNothing: Frag = base ++ Upsert.doNothingSuffix(conflictColumn = "id")

// Append DO UPDATE suffix with explicit assignments
val withUpdate: Frag = base ++ Upsert.doUpdateSuffix(
  conflictColumn = "id",
  assignments    = IndexedSeq("name" -> DbValue.DbString("updated"))
)
```

### Repository integration

`Repo` provides `insertOrUpdate` and `insertOrUpdateBatch` as convenience
wrappers that use `Upsert.insertDoUpdate` under the hood. The conflict target is
the repository's validated ID column, and all non-ID columns are overwritten with
the entity's values.

```scala
import zio.blocks.sql.*
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

given DbCon = ???

val table: Table[User] = Table.derived[User]
val repo: Repo[User, Int] = ???

val user = User(42, "Alice", "alice@example.com")

// Single upsert
val affected: Int = repo.insertOrUpdate(user)

// Batch upsert
val users: List[User] = List(user, User(43, "Bob", "bob@example.com"))
val totalAffected: Int = repo.insertOrUpdateBatch(users)
```

These generate SQL like:

```sql
INSERT INTO "user" ("id", "name", "email") VALUES (?, ?, ?)
  ON CONFLICT ("id") DO UPDATE SET "name" = ?, "email" = ?
```

`insertOrUpdateBatch` uses a JDBC batch for efficiency, mirroring the pattern of
`insertBatch`. Both return the total affected row count.

## Keyset Pagination

Keyset (cursor) pagination avoids the cost and drift of `OFFSET` by seeking
after the last seen key: `WHERE id > ? ORDER BY id ASC LIMIT n`. The row
identified by the cursor is excluded (`>` not `>=`) so consecutive pages do
not duplicate the boundary row.

`Repo` exposes this directly for primary-key cursors:

```scala
import zio.blocks.sql.*
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

given DbCon = ???

val repo: Repo[User, Int] = ??? // e.g. Repo(table, "id", idCodec, _.id)

val firstPage: List[User]  = repo.pageAfter(cursorId = 0, limit = 20)
val nextPage: List[User]   = repo.pageAfter(cursorId = firstPage.last.id, limit = 20)
// when cursorId == last id, nextPage is empty
```

It renders as:

```sql
SELECT "id", "name", "email" FROM "user" WHERE "id" > ? ORDER BY "id" ASC LIMIT 20
```

where `?` is bound via `idCodec.toDbValues(cursorId)`. `limit` must be `> 0`.

For non-ID orderings or ad-hoc queries, `Frag.keysetAfter` builds the
portable `WHERE col > ? ORDER BY col ASC LIMIT n` fragment without a `Repo`:

```scala
import zio.blocks.sql.*

case class User(id: Int, name: String, email: String)
import zio.blocks.schema.Schema
object User { implicit val schema: Schema[User] = Schema.derived }
val table: Table[User] = Table.derived[User]

// Table-validated: rejects unknown columns
val frag: Frag = Frag.keysetAfter(table, orderCol = "id", lastValue = DbValue.DbInt(42), limit = 20)
// frag.sql(dialect) == " WHERE id > ? ORDER BY id ASC LIMIT 20"

// Without a table: identifier-only validation
val frag2: Frag = Frag.keysetAfter(orderCol = "created_at", lastValue = DbValue.DbLong(1000L), limit = 10)
```

- `Frag.keysetAfter(table, orderCol, lastValue, limit)` validates `orderCol`
  with `SqlIdentifier.validate` and checks membership in `table.columns`;
  unknown columns throw `IllegalArgumentException`.
- `Frag.keysetAfter(orderCol, lastValue, limit)` validates the identifier only.
- `limit` must be `> 0`; single-column cursors only (composite cursors are v2).

Compose with a base `SELECT`:

```scala
import zio.blocks.sql.*
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }
val table: Table[User] = Table.derived[User]

val base     = Frag.literal("SELECT id, name, email FROM user")
val pageFrag = base ++ Frag.keysetAfter(table, "id", DbValue.DbInt(42), 20)
// Rendering the SQL does not require a DbCon:
val sql: String = pageFrag.sql(SqlDialect.SQLite) // SELECT id, name, email FROM user WHERE id > ? ORDER BY id ASC LIMIT 20
// Executing needs givens at the call site:
// given DbCon = ???
// given DbCodec[User] = table.codec
// val rows: List[User] = pageFrag.query[User]
```

## Inspecting SQL

The custom interpreter above produces raw strings useful for debugging. The `sql` module's query IR (`zio.blocks.sql.query.SqlQuery`) provides richer inspection APIs.

### explain(dialect): String

The `explain` method renders the full SQL text with numbered parameter placeholders (`?1`, `?2`, ...) and a trailing comment listing each parameter's position and type:

```scala
import zio.blocks.sql._
import zio.blocks.sql.query.{Rel, SqlQuery => Select}

val userTable = Table.derived[User]
val repoTable = Table.derived[Repo]

val q = Select
  .from(userTable)
  .innerJoin(Rel(repoTable, "owner_id", userTable, "id"))
  .filter(Frag(IndexedSeq("""t0."name" = """, ""), IndexedSeq(DbValue.DbString("alice"))))

println(q.explain(SqlDialect.PostgreSQL))
// SELECT t0."id", t0."name", t1."id", t1."owner_id", t1."name" FROM "user" AS t0 INNER JOIN "repo" AS t1 ON t1."owner_id" = t0."id" WHERE t0."name" = ?1
// -- params: 1:String
```

`explain` renders a single-line SQL string with numbered `?N` placeholders. The `?N`
placeholders correspond one-to-one with the parameter list you can obtain separately via
`toFrag(dialect).params`. This makes `explain` useful for logging and visual debugging without touching a
database.

### Inspecting the query IR

The query itself is a case class, so every part is available programmatically:

```scala
q.source       // Table[User] for "user"
q.joins        // Vector(JoinNode(repoTable, "t1", Inner, <on-frag>))
q.filters      // Vector(Frag(t0."name" = ?, DbString(alice)))
q.groupBy      // Vector.empty
q.orderBy      // Vector.empty
q.limit        // None
q.offset       // None
q.toFrag       // Frag (re-renderable to SQL via frag.sql(dialect))
```

Inspecting the IR directly lets you examine joins, filters, ordering, and limits programmatically. This is useful for building monitoring dashboards, query analyzers, or dynamic query modification layers.

### sql(dialect): String

The query IR also provides a simpler `sql` method that renders `?` placeholders for execution:

```scala
println(q.sql(SqlDialect.PostgreSQL))
// SELECT t0."id", t0."name", t1."id", t1."owner_id", t1."name" FROM "user" AS t0 INNER JOIN "repo" AS t1 ON t1."owner_id" = t0."id" WHERE t0."name" = ?
```

### previewSql() for Migrations

When using `SmallMigrator` or `LargeMigrator` from the `data-migration` module, `previewSql()` returns the full sequence of SQL statements the migrator would execute, without opening any database connection:

```scala
import zio.blocks.data.migration._

given transactor: Transactor = tx

val migrator = SmallMigrator(
  repoV1 = userRepo,
  repoV2 = userRepoV2,
  migration = userMigration,
  queueTable = "migration_queue",
  batchSize = 100,
  target = TargetStrategy.InPlace
)

val statements: Vector[String] = migrator.previewSql()
// Vector(
//   "CREATE TABLE ...",     -- queue DDL
//   "CREATE TABLE ...",     -- shadow table
//   "CREATE TRIGGER ...",   -- capture triggers
//   "SELECT ...",           -- dequeue template
//   "ALTER TABLE ..."       -- finalize (rename)
// )
```

This gives you a dry run of the migration SQL before any schema changes are applied.

## Compile-time SQL Dumps

The `Dump` object emits SQL files at compile time. When the JVM property `zib.sql.dumpDir` is set, inline macro calls to `Dump.dumpTable` or `Dump.dumpQuery` write `.sql` files to that directory. When the property is absent, the calls become no-ops with zero runtime cost.

### Enabling Dumps

The `Dump` macros read `System.getProperty("zib.sql.dumpDir")` at compile time from the JVM running sbt's compiler. This is a JVM system property, not a scalac flag. Passing it via `scalacOptions` does nothing.

The simplest way to set it is as a JVM flag on the sbt command line:

```bash
sbt -Dzib.sql.dumpDir=target/sql-dumps "++3.8.3; sqlJVM/compile"
```

If you prefer not to type `-D` every time, export it through `SBT_OPTS`:

```bash
SBT_OPTS="-Dzib.sql.dumpDir=target/sql-dumps" sbt compile
```

Both approaches set the property on the sbt process, which is the same JVM that runs the Scala compiler and the macros within it.

### Entry Points

There are two inline macro entry points, all in `zio.blocks.sql.Dump`:

```scala
import zio.blocks.sql._

// Dump a Table's CREATE TABLE DDL (both PostgreSQL and SQLite)
Dump.dumpTable(userTable)

// Dump a query IR's SELECT
Dump.dumpQuery(queryIr)
```

Each call emits one file per dialect (PostgreSQL and SQLite by default).

### Naming Scheme

Files are named `<owner>-<dialect>.sql` where `<owner>` is derived from the enclosing symbol and `<dialect>` is the lowercased dialect name:

```
target/sql-dumps/
  user-postgresql.sql
  user-sqlite.sql
  repo-postgresql.sql
  repo-sqlite.sql
```

For `dumpTable`, the owner comes from the `Table`'s type name. For `dumpQuery`, the macro walks the call site to extract the enclosing method, val name, or argument name. If the macro cannot determine a meaningful name, it falls back to `query`.

### Content-hash Skip

Each dump file is written only when its content differs from the existing file. The macro compares the new bytes against any existing file at the target path. If they match, the write is skipped. This means incremental compilations do not produce noisy diffs or unnecessary filesystem writes.

All files use UTF-8 encoding with a trailing newline.

### Limitations

Compile-time dumps work well for statically constructed queries, but some SQL patterns cannot be dumped:

- **Dynamic `Frag` chains.** Fragments built at runtime from user input, database lookups, or conditional branching are invisible to the macro. Only the static structure known at compile time appears in the dump.
- **Repo internals.** The `Repo` abstraction's generated queries (insert, update, delete, select-by-id) are assembled at runtime from the `DbCodec` and `Table` metadata. `Dump.dumpTable` captures the DDL, but the CRUD queries themselves are not emitted.
- **Phase 2 note.** A future phase may extend `Dump` to cover `Repo`-level CRUD operations and dynamic fragment composition. For now, treat the dump as a DDL and static-query snapshot, not a complete representation of every SQL statement your application will execute.

:::tip
Pair `Dump.dumpTable` with `previewSql()` for a fuller picture: `dumpTable` captures the schema DDL at compile time, while `previewSql` captures the migration SQL at runtime before execution.
:::

## Going Further

- **[Part 1: Expressions](./query-dsl-reified-optics.md)** -- Building query expressions with reified optics
- **[Part 3: Extending the Expression Language](./query-dsl-extending.md)** -- Adding custom operators (IN, BETWEEN, aggregates) beyond SchemaExpr
- **[Part 4: A Fluent SQL Builder](./query-dsl-fluent-builder.md)** -- Type-safe SELECT, UPDATE, INSERT, DELETE with seamless condition mixing
- **[SchemaExpr Reference](../reference/schema/schema-expr.md)** -- Full API coverage of expression types
- **[Optics Reference](../reference/schema/optics.md)** -- Lens, Prism, Optional, and Traversal
- **[DynamicOptic Reference](../reference/schema/dynamic-optic.md)** -- Runtime optic paths for programmatic field extraction

The interpreter pattern shown here extends naturally to other query targets. Because `SchemaExpr` wraps a `DynamicSchemaExpr` sealed trait and `DynamicOptic` carries full path metadata, you can write interpreters for MongoDB filters, Elasticsearch queries, GraphQL filters, or any other query language using the same approach: access `.dynamic`, pattern match on the AST, map operators, and extract field names from optic paths.
