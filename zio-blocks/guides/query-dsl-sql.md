# Query DSL with Reified Optics — Part 2: SQL Generation

> In this guide, we will build a SQL query generator that translates ZIO Blocks' `SchemaExpr` expression trees into SQL WHERE clauses, SELECT statements, and parameterized queries. By the end, you will have an interpreter that takes any `SchemaExpr`-based query and produces executable SQL, covering comparisons, boolean logic, arithmetic, string operations, nested structures, and safe parameterization.

In this guide, we will build a SQL query generator that translates ZIO Blocks' `SchemaExpr` expression trees into SQL WHERE clauses, SELECT statements, and parameterized queries. By the end, you will have an interpreter that takes any `SchemaExpr`-based query and produces executable SQL, covering comparisons, boolean logic, arithmetic, string operations, nested structures, and safe parameterization.

This is Part 2 of the Query DSL series. [Part 1](./query-dsl-reified-optics.md) covered building query expressions with reified optics. Here, we interpret those expressions as SQL.

**What we'll cover:**

- Interpreting `SchemaExpr` as a sealed AST via pattern matching
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

Since `SchemaExpr` is a sealed trait, we can write a single interpreter that translates *any* query expression into SQL. Write the interpreter once, and every query you build with the Part 1 DSL automatically gets a SQL translation.

## Prerequisites

This guide builds on [Part 1: Expressions](./query-dsl-reified-optics.md). You should be comfortable building `SchemaExpr` values with optic operators (`===`, `>`, `&&`, etc.).

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "0.0.33"
```

```scala

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

## The SchemaExpr AST

Before we build the interpreter, let's understand the structure we are interpreting. `SchemaExpr` is a sealed trait with these cases:

```
SchemaExpr[A, B]
├── Literal[S, A](value, schema)                              -- a constant value
├── Optic[A, B](optic)                                        -- a field reference
├── StringRegexMatch[A](regex, string)                       -- regex pattern matching
├── StringLength[A](string)                                  -- string length calculation
├── UnaryOp[A, B]                                            -- abstract trait for unary operations
│   └── Not[A](expr)                                         -- boolean negation
└── BinaryOp[A, B, C]                                        -- abstract trait for binary operations
    ├── Relational[A, B](left, right, operator)              -- comparison operations
    ├── Logical[A](left, right, operator)                    -- boolean operations
    ├── Arithmetic[S, A](left, right, operator, isNumeric)   -- numeric operations
    └── StringConcat[A](left, right)                         -- string concatenation

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

Each case carries enough information to produce SQL: `Optic` nodes carry field paths, `Literal` nodes carry values, and operator nodes carry the operation type. Our interpreter walks this tree and emits SQL fragments.

## Extracting Column Names from Optics

The first challenge is turning a reified optic into a SQL column name. Every `Optic[S, A]` has a `toDynamic` method that returns a `DynamicOptic` -- a sequence of path nodes. For a lens like `Product.price`, the path is `[Field("price")]`. We extract the field name from the last `Field` node:

```scala
def columnName(optic: zio.blocks.schema.Optic[?, ?]): String = {
  val nodes = optic.toDynamic.nodes
  nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")
}
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

Literal values need proper SQL formatting -- strings must be quoted, booleans converted to SQL syntax:

```scala
def sqlLiteral(value: Any): String = value match {
  case s: String     => s"'${s.replace("'", "''")}'"
  case b: Boolean    => if (b) "TRUE" else "FALSE"
  case n: Number     => n.toString
  case other         => other.toString
}
```

## Building the SQL Interpreter

Now we build the core interpreter. It pattern-matches on each `SchemaExpr` case and produces a SQL string:

```scala
def toSql[A, B](expr: SchemaExpr[A, B]): String = expr match {

  // Field reference → column name
  case SchemaExpr.Optic(optic) =>
    columnName(optic)

  // Constant value → SQL literal
  case SchemaExpr.Literal(value, _) =>
    sqlLiteral(value)

  // Comparison operators → SQL relational operators
  case SchemaExpr.Relational(left, right, op) =>
    val sqlOp = op match {
      case SchemaExpr.RelationalOperator.Equal              => "="
      case SchemaExpr.RelationalOperator.NotEqual           => "<>"
      case SchemaExpr.RelationalOperator.LessThan           => "<"
      case SchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
      case SchemaExpr.RelationalOperator.GreaterThan        => ">"
      case SchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
    }
    s"(${toSql(left)} $sqlOp ${toSql(right)})"

  // Boolean operators → AND / OR
  case SchemaExpr.Logical(left, right, op) =>
    val sqlOp = op match {
      case SchemaExpr.LogicalOperator.And => "AND"
      case SchemaExpr.LogicalOperator.Or  => "OR"
    }
    s"(${toSql(left)} $sqlOp ${toSql(right)})"

  // Negation → NOT
  case SchemaExpr.Not(inner) =>
    s"NOT (${toSql(inner)})"

  // Arithmetic → SQL math operators
  case SchemaExpr.Arithmetic(left, right, op, _) =>
    val sqlOp = op match {
      case SchemaExpr.ArithmeticOperator.Add      => "+"
      case SchemaExpr.ArithmeticOperator.Subtract => "-"
      case SchemaExpr.ArithmeticOperator.Multiply => "*"
    }
    s"(${toSql(left)} $sqlOp ${toSql(right)})"

  // String concatenation → CONCAT()
  case SchemaExpr.StringConcat(left, right) =>
    s"CONCAT(${toSql(left)}, ${toSql(right)})"

  // Regex match → column LIKE pattern (simplified)
  case SchemaExpr.StringRegexMatch(regex, string) =>
    s"(${toSql(string)} LIKE ${toSql(regex)})"

  // String length → LENGTH()
  case SchemaExpr.StringLength(string) =>
    s"LENGTH(${toSql(string)})"
}
```

The mapping from `SchemaExpr` to SQL is direct:

| SchemaExpr Case     | SQL Output                           |
|---------------------|--------------------------------------|
| `Optic(optic)`      | Column name from `toDynamic`         |
| `Literal(v, _)`     | SQL literal (`'text'`, `42`, `TRUE`) |
| `Relational(_, _, op)` | `=`, `<>`, `<`, `>`, `<=`, `>=`   |
| `Logical(_, _, op)` | `AND`, `OR`                          |
| `Not(expr)`         | `NOT (...)`                          |
| `Arithmetic(_, _, op, _)` | `+`, `-`, `*`                  |
| `StringConcat`      | `CONCAT(a, b)`                       |
| `StringRegexMatch`  | `LIKE` (pattern matching)            |
| `StringLength`      | `LENGTH(col)`                        |

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

def toParameterized[A, B](expr: SchemaExpr[A, B]): SqlQuery = expr match {

  case SchemaExpr.Optic(optic) =>
    SqlQuery(columnName(optic), Nil)

  case SchemaExpr.Literal(value, _) =>
    SqlQuery("?", List(value))

  case SchemaExpr.Relational(left, right, op) =>
    val l = toParameterized(left)
    val r = toParameterized(right)
    val sqlOp = op match {
      case SchemaExpr.RelationalOperator.Equal              => "="
      case SchemaExpr.RelationalOperator.NotEqual           => "<>"
      case SchemaExpr.RelationalOperator.LessThan           => "<"
      case SchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
      case SchemaExpr.RelationalOperator.GreaterThan        => ">"
      case SchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
    }
    SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)

  case SchemaExpr.Logical(left, right, op) =>
    val l = toParameterized(left)
    val r = toParameterized(right)
    val sqlOp = op match {
      case SchemaExpr.LogicalOperator.And => "AND"
      case SchemaExpr.LogicalOperator.Or  => "OR"
    }
    SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)

  case SchemaExpr.Not(inner) =>
    val i = toParameterized(inner)
    SqlQuery(s"NOT (${i.sql})", i.params)

  case SchemaExpr.Arithmetic(left, right, op, _) =>
    val l = toParameterized(left)
    val r = toParameterized(right)
    val sqlOp = op match {
      case SchemaExpr.ArithmeticOperator.Add      => "+"
      case SchemaExpr.ArithmeticOperator.Subtract => "-"
      case SchemaExpr.ArithmeticOperator.Multiply => "*"
    }
    SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)

  case SchemaExpr.StringConcat(left, right) =>
    val l = toParameterized(left)
    val r = toParameterized(right)
    SqlQuery(s"CONCAT(${l.sql}, ${r.sql})", l.params ++ r.params)

  case SchemaExpr.StringRegexMatch(regex, string) =>
    val s = toParameterized(string)
    val r = toParameterized(regex)
    SqlQuery(s"(${s.sql} LIKE ${r.sql})", s.params ++ r.params)

  case SchemaExpr.StringLength(string) =>
    val s = toParameterized(string)
    SqlQuery(s"LENGTH(${s.sql})", s.params)
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

def sqlLiteral(value: Any): String = value match {
  case s: String  => s"'${s.replace("'", "''")}'"
  case b: Boolean => if (b) "TRUE" else "FALSE"
  case n: Number  => n.toString
  case other      => other.toString
}

def toSql[A, B](expr: SchemaExpr[A, B]): String = expr match {
  case SchemaExpr.Optic(optic)                    => columnName(optic)
  case SchemaExpr.Literal(value, _)               => sqlLiteral(value)
  case SchemaExpr.Relational(left, right, op) =>
    val sqlOp = op match {
      case SchemaExpr.RelationalOperator.Equal              => "="
      case SchemaExpr.RelationalOperator.NotEqual           => "<>"
      case SchemaExpr.RelationalOperator.LessThan           => "<"
      case SchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
      case SchemaExpr.RelationalOperator.GreaterThan        => ">"
      case SchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
    }
    s"(${toSql(left)} $sqlOp ${toSql(right)})"
  case SchemaExpr.Logical(left, right, op) =>
    val sqlOp = op match {
      case SchemaExpr.LogicalOperator.And => "AND"
      case SchemaExpr.LogicalOperator.Or  => "OR"
    }
    s"(${toSql(left)} $sqlOp ${toSql(right)})"
  case SchemaExpr.Not(inner)                      => s"NOT (${toSql(inner)})"
  case SchemaExpr.Arithmetic(left, right, op, _) =>
    val sqlOp = op match {
      case SchemaExpr.ArithmeticOperator.Add      => "+"
      case SchemaExpr.ArithmeticOperator.Subtract => "-"
      case SchemaExpr.ArithmeticOperator.Multiply => "*"
    }
    s"(${toSql(left)} $sqlOp ${toSql(right)})"
  case SchemaExpr.StringConcat(left, right)       => s"CONCAT(${toSql(left)}, ${toSql(right)})"
  case SchemaExpr.StringRegexMatch(regex, string) => s"(${toSql(string)} LIKE ${toSql(regex)})"
  case SchemaExpr.StringLength(string)            => s"LENGTH(${toSql(string)})"
}

// --- Parameterized queries ---

case class SqlQuery(sql: String, params: List[Any])

def toParameterized[A, B](expr: SchemaExpr[A, B]): SqlQuery = expr match {
  case SchemaExpr.Optic(optic)      => SqlQuery(columnName(optic), Nil)
  case SchemaExpr.Literal(value, _) => SqlQuery("?", List(value))
  case SchemaExpr.Relational(left, right, op) =>
    val l = toParameterized(left); val r = toParameterized(right)
    val sqlOp = op match {
      case SchemaExpr.RelationalOperator.Equal              => "="
      case SchemaExpr.RelationalOperator.NotEqual           => "<>"
      case SchemaExpr.RelationalOperator.LessThan           => "<"
      case SchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
      case SchemaExpr.RelationalOperator.GreaterThan        => ">"
      case SchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
    }
    SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)
  case SchemaExpr.Logical(left, right, op) =>
    val l = toParameterized(left); val r = toParameterized(right)
    val sqlOp = op match {
      case SchemaExpr.LogicalOperator.And => "AND"
      case SchemaExpr.LogicalOperator.Or  => "OR"
    }
    SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)
  case SchemaExpr.Not(inner) =>
    val i = toParameterized(inner)
    SqlQuery(s"NOT (${i.sql})", i.params)
  case SchemaExpr.Arithmetic(left, right, op, _) =>
    val l = toParameterized(left); val r = toParameterized(right)
    val sqlOp = op match {
      case SchemaExpr.ArithmeticOperator.Add      => "+"
      case SchemaExpr.ArithmeticOperator.Subtract => "-"
      case SchemaExpr.ArithmeticOperator.Multiply => "*"
    }
    SqlQuery(s"(${l.sql} $sqlOp ${r.sql})", l.params ++ r.params)
  case SchemaExpr.StringConcat(left, right) =>
    val l = toParameterized(left); val r = toParameterized(right)
    SqlQuery(s"CONCAT(${l.sql}, ${r.sql})", l.params ++ r.params)
  case SchemaExpr.StringRegexMatch(regex, string) =>
    val s = toParameterized(string); val r = toParameterized(regex)
    SqlQuery(s"(${s.sql} LIKE ${r.sql})", s.params ++ r.params)
  case SchemaExpr.StringLength(string) =>
    val s = toParameterized(string)
    SqlQuery(s"LENGTH(${s.sql})", s.params)
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

## Going Further

- **[Part 1: Expressions](./query-dsl-reified-optics.md)** -- Building query expressions with reified optics
- **[Part 3: Extending the Expression Language](./query-dsl-extending.md)** -- Adding custom operators (IN, BETWEEN, aggregates) beyond SchemaExpr
- **[Part 4: A Fluent SQL Builder](./query-dsl-fluent-builder.md)** -- Type-safe SELECT, UPDATE, INSERT, DELETE with seamless condition mixing
- **[SchemaExpr Reference](../reference/schema-expr.md)** -- Full API coverage of expression types
- **[Optics Reference](../reference/optics.md)** -- Lens, Prism, Optional, and Traversal
- **[DynamicOptic Reference](../reference/dynamic-optic.md)** -- Runtime optic paths for programmatic field extraction

The interpreter pattern shown here extends naturally to other query targets. Because `SchemaExpr` is a sealed trait and `DynamicOptic` carries full path metadata, you can write interpreters for MongoDB filters, Elasticsearch queries, GraphQL filters, or any other query language using the same approach: pattern match on the AST, map operators, and extract field names from optic paths.
