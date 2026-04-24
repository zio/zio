# Query DSL with Reified Optics — Part 1: Expressions

> In this guide, we will build a type-safe query DSL for filtering, comparing, and computing over domain data using ZIO Blocks' reified optics and schema expressions. By the end, you will have a composable query language that works on any schema-equipped data type, supporting equality checks, comparisons, boolean logic, arithmetic, and string operations.

In this guide, we will build a type-safe query DSL for filtering, comparing, and computing over domain data using ZIO Blocks' reified optics and schema expressions. By the end, you will have a composable query language that works on any schema-equipped data type, supporting equality checks, comparisons, boolean logic, arithmetic, and string operations.

We'll take an incremental approach: starting with simple field-level equality checks, then adding comparison operators, boolean combinators, arithmetic expressions, and string operations until we have a complete, expressive query DSL.

**What we'll cover:**

- Defining domain types with schemas and optics
- Building equality and comparison queries using `===`, `>`, `<`, `>=`, `<=`
- Combining queries with `&&` and `||`
- Using arithmetic operators (`+`, `-`, `*`) in expressions
- Working with string operations (`matches`, `concat`, `length`)
- Querying through nested structures and collections
- Evaluating queries against data

## The Problem

When you need to query or filter collections of structured data in Scala, you typically write ad-hoc predicate functions:

```scala
case class Product(name: String, price: Double, category: String, inStock: Boolean)

val products: List[Product] = loadProducts()

// Filtering with ad-hoc predicates
val results = products.filter(p =>
  p.category == "Electronics" && p.price < 500.0 && p.inStock
)
```

This works, but the predicate `p => p.category == "Electronics" && p.price < 500.0 && p.inStock` is an opaque function. You cannot inspect it, serialize it, translate it to SQL, send it to a remote service, or optimize it. It is a black box.

If you need to build a query builder for a database, a filter language for an API, or a rule engine, you need queries as **data** -- inspectable, composable, serializable expression trees. Building these by hand means defining an AST, writing an evaluator, and maintaining type safety across all operators -- a significant amount of boilerplate for every domain type.

In this guide, we'll solve this by using ZIO Blocks' `SchemaExpr` and reified optics, which give us composable, type-safe query expressions for free, derived directly from your data model's schema.

## Prerequisites

Add the ZIO Blocks Schema dependency:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "0.0.33"
```

```scala

```

This guide assumes familiarity with ZIO Blocks schemas and basic optics. See the [Schema](../reference/schema.md) and [Optics](../reference/optics.md) reference pages for background.

## Defining Your Domain

We'll build a product catalog query DSL. First, define the domain types with schemas and optics:

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

Three things are required for each queryable type:

1. **`Schema.derived`** -- captures the type's structure at runtime
2. **`CompanionOptics[T]`** -- provides the `optic` macro for deriving lenses
3. **Named lenses** for each field you want to query on

Each lens is a *reified* field accessor: unlike `_.price`, the lens `Product.price` is a first-class value that carries the field name, the source schema, and the focus schema. This metadata is what makes the query DSL possible.

## Equality and Comparison Queries

Every optic in ZIO Blocks has built-in operators that create `SchemaExpr` values -- expression trees representing queries:

```scala
// Equality check
val isElectronics: SchemaExpr[Product, Boolean] =
  Product.category === "Electronics"

// Greater-than comparison
val expensiveItems: SchemaExpr[Product, Boolean] =
  Product.price > 100.0

// Less-than-or-equal
val budgetFriendly: SchemaExpr[Product, Boolean] =
  Product.price <= 50.0

// Comparison against a literal
val highRated: SchemaExpr[Product, Boolean] =
  Product.rating >= 4
```

The full set of comparison operators:

| Operator | Meaning                |
|----------|------------------------|
| `===`    | Equal to               |
| `!=`     | Not equal to           |
| `>`      | Greater than           |
| `>=`     | Greater than or equal  |
| `<`      | Less than              |
| `<=`     | Less than or equal     |

Each operator works in two forms:
- **Optic vs. literal**: `Product.price > 100.0` -- compare a field to a value
- **Optic vs. optic**: `Product.rating === Product.rating` -- compare two fields

## Evaluating Queries

A `SchemaExpr[A, Boolean]` is a predicate over `A`. Evaluate it with `.eval`:

```scala
val laptop = Product("Laptop", 999.99, "Electronics", true, 5)
val pen    = Product("Pen", 2.50, "Office", true, 3)
```

```scala
isElectronics.eval(laptop)
// res0: Either[OpticCheck, Seq[Boolean]] = Right(IndexedSeq(true))
isElectronics.eval(pen)
// res1: Either[OpticCheck, Seq[Boolean]] = Right(IndexedSeq(false))

expensiveItems.eval(laptop)
// res2: Either[OpticCheck, Seq[Boolean]] = Right(IndexedSeq(true))
expensiveItems.eval(pen)
// res3: Either[OpticCheck, Seq[Boolean]] = Right(IndexedSeq(false))
```

The result type is `Either[OpticCheck, Seq[Boolean]]`:
- `Right(Seq(true))` or `Right(Seq(false))` for successful evaluation
- `Left(opticCheck)` if the optic path fails (e.g., a prism encounters the wrong variant case)

For lenses, evaluation always succeeds because lenses always resolve.

## Combining Queries with Boolean Logic

Combine queries with `&&` (and), `||` (or), and `!` (not):

```scala
// AND: electronics under $500
val affordableElectronics: SchemaExpr[Product, Boolean] =
  (Product.category === "Electronics") && (Product.price < 500.0)

// OR: either cheap or highly rated
val goodDeal: SchemaExpr[Product, Boolean] =
  (Product.price < 10.0) || (Product.rating >= 5)

// NOT: items that are out of stock
val outOfStock: SchemaExpr[Product, Boolean] =
  !Product.inStock
```

```scala
affordableElectronics.eval(laptop)
// res4: Either[OpticCheck, Seq[Boolean]] = Right(IndexedSeq(false))
goodDeal.eval(laptop)
// res5: Either[OpticCheck, Seq[Boolean]] = Right(IndexedSeq(true))
goodDeal.eval(pen)
// res6: Either[OpticCheck, Seq[Boolean]] = Right(IndexedSeq(true))
outOfStock.eval(laptop)
// res7: Either[OpticCheck, Seq[Boolean]] = Right(IndexedSeq(false))
```

Boolean combinators also work on `SchemaExpr` values, not just optics. This means you can build complex compound queries:

```scala
val complexQuery: SchemaExpr[Product, Boolean] =
  ((Product.category === "Electronics") && (Product.price < 500.0)) ||
  ((Product.category === "Office") && (Product.rating >= 4))
```

```scala
complexQuery.eval(laptop)
// res8: Either[OpticCheck, Seq[Boolean]] = Right(IndexedSeq(false))
complexQuery.eval(pen)
// res9: Either[OpticCheck, Seq[Boolean]] = Right(IndexedSeq(false))
```

## Arithmetic Expressions

Optics on numeric fields support `+`, `-`, and `*`:

```scala
val discountedPrice: SchemaExpr[Product, Double] =
  Product.price * 0.9

val priceWithTax: SchemaExpr[Product, Double] =
  Product.price * 1.08
```

```scala
discountedPrice.eval(laptop)
// res10: Either[OpticCheck, Seq[Double]] = Right(IndexedSeq(899.991))
priceWithTax.eval(pen)
// res11: Either[OpticCheck, Seq[Double]] = Right(IndexedSeq(2.7))
```

Arithmetic operators are available for all numeric types: `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigInt`, and `BigDecimal`.

## String Operations

Optics on `String` fields provide string-specific operations:

```scala
// Regex matching
val startsWithL: SchemaExpr[Product, Boolean] =
  Product.name.matches("L.*")

// String concatenation
val labeledName: SchemaExpr[Product, String] =
  Product.name.concat(" [SALE]")

// String length
val nameLength: SchemaExpr[Product, Int] =
  Product.name.length
```

```scala
startsWithL.eval(laptop)
// res12: Either[OpticCheck, Seq[Boolean]] = Right(IndexedSeq(true))
startsWithL.eval(pen)
// res13: Either[OpticCheck, Seq[Boolean]] = Right(IndexedSeq(false))

labeledName.eval(laptop)
// res14: Either[OpticCheck, Seq[String]] = Right(IndexedSeq("Laptop [SALE]"))

nameLength.eval(laptop)
// res15: Either[OpticCheck, Seq[Int]] = Right(IndexedSeq(6))
nameLength.eval(pen)
// res16: Either[OpticCheck, Seq[Int]] = Right(IndexedSeq(3))
```

| String Operation | Signature                          | Description               |
|------------------|------------------------------------|---------------------------|
| `matches`        | `(regex: String) => SchemaExpr[S, Boolean]` | Regex match        |
| `concat`         | `(suffix: String) => SchemaExpr[S, String]` | Append a string    |
| `length`         | `SchemaExpr[S, Int]`               | String length             |

## Dynamic Evaluation

Every `SchemaExpr` can also evaluate to `DynamicValue`, which is useful when you need format-agnostic results (e.g., serializing query results to JSON):

```scala
val priceExpr: SchemaExpr[Product, Double] = Product.price * 0.9
```

```scala
priceExpr.evalDynamic(laptop)
// res17: Either[OpticCheck, Seq[DynamicValue]] = Right(
//   IndexedSeq(Primitive(Double(899.991)))
// )
```

The `evalDynamic` method converts results to `DynamicValue` representations, enabling integration with serialization formats without knowing the concrete type.

## Querying Nested Structures

The real power of reified optics emerges with nested data. Define a richer domain:

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
  // Compose through nested structure directly
  val city: Lens[Seller, String]       = optic(_.address.city)
  val country: Lens[Seller, String]    = optic(_.address.country)
}
```

The `optic(_.address.city)` macro composes a lens from `Seller` to `Address` with a lens from `Address` to `String`, producing a single `Lens[Seller, String]`. Now we can query nested fields as if they were top-level:

```scala
val localSeller: SchemaExpr[Seller, Boolean] =
  (Seller.city === "Berlin") && (Seller.rating >= 4.0)

val seller = Seller("TechShop", Address("Berlin", "Germany"), 4.5)
```

```scala
localSeller.eval(seller)
// res19: Either[OpticCheck, Seq[Boolean]] = Right(IndexedSeq(true))
```

## Querying Through Collections

For collection fields, use traversals to query across all elements:

```scala

case class LineItem(sku: String, price: Double, quantity: Int)
object LineItem {
  implicit val schema: Schema[LineItem] = Schema.derived
}

case class Order(id: String, items: List[LineItem])
object Order extends CompanionOptics[Order] {
  implicit val schema: Schema[Order] = Schema.derived

  val id: Lens[Order, String]                  = optic(_.id)
  val allPrices: Traversal[Order, Double]      = optic(_.items.each.price)
  val allSkus: Traversal[Order, String]        = optic(_.items.each.sku)
  val allQuantities: Traversal[Order, Int]     = optic(_.items.each.quantity)
}
```

Traversals produce `SchemaExpr` values that evaluate to **multiple results**:

```scala
val order = Order("ORD-1", List(
  LineItem("SKU-A", 29.99, 2),
  LineItem("SKU-B", 149.99, 1),
  LineItem("SKU-C", 9.99, 5)
))

// Each element is evaluated independently
val hasExpensiveItem: SchemaExpr[Order, Boolean] =
  Order.allPrices > 100.0
```

```scala
hasExpensiveItem.eval(order)
// res21: Either[OpticCheck, Seq[Boolean]] = Right(List(false, true, false))
```

:::tip
When a traversal-based expression produces multiple results, each element in the sequence corresponds to one focused value from the traversal. This makes it straightforward to check whether *any* or *all* elements satisfy a condition by examining the result sequence.
:::

## Filtering a Collection

With `SchemaExpr` as a reified predicate, you can build a generic filter function that works with any schema-equipped type:

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

def filter[A](items: List[A], predicate: SchemaExpr[A, Boolean]): List[A] =
  items.filter(item =>
    predicate.eval(item) match {
      case Right(results) => results.forall(_ == true)
      case Left(_)        => false
    }
  )

val catalog = List(
  Product("Laptop", 999.99, "Electronics", true, 5),
  Product("Mouse", 29.99, "Electronics", true, 4),
  Product("Pen", 2.50, "Office", true, 3),
  Product("Monitor", 349.99, "Electronics", false, 5),
  Product("Notebook", 5.99, "Office", true, 4)
)

val query = (Product.category === "Electronics") && (Product.inStock === true) && (Product.price < 500.0)
```

```scala
filter(catalog, query).map(_.name)
// res23: List[String] = List("Mouse")
```

The `filter` function knows nothing about `Product` -- it works with any `SchemaExpr[A, Boolean]`. The query is data, not a lambda, so it could be serialized, logged, or translated to a database query.

## Putting It Together

Here is a complete, self-contained example combining all the techniques from this guide:

```scala

// --- Domain ---

case class Address(city: String, country: String)
object Address {
  implicit val schema: Schema[Address] = Schema.derived
}

case class Product(
  name: String,
  price: Double,
  category: String,
  inStock: Boolean,
  rating: Int,
  warehouse: Address
)

object Product extends CompanionOptics[Product] {
  implicit val schema: Schema[Product] = Schema.derived

  val name: Lens[Product, String]      = optic(_.name)
  val price: Lens[Product, Double]     = optic(_.price)
  val category: Lens[Product, String]  = optic(_.category)
  val inStock: Lens[Product, Boolean]  = optic(_.inStock)
  val rating: Lens[Product, Int]       = optic(_.rating)
  val city: Lens[Product, String]      = optic(_.warehouse.city)
}

// --- Generic query filter ---

def filter[A](items: List[A], predicate: SchemaExpr[A, Boolean]): List[A] =
  items.filter(item =>
    predicate.eval(item) match {
      case Right(results) => results.forall(_ == true)
      case Left(_)        => false
    }
  )

// --- Usage ---

val catalog = List(
  Product("Laptop", 999.99, "Electronics", true, 5, Address("Berlin", "Germany")),
  Product("Mouse", 29.99, "Electronics", true, 4, Address("Berlin", "Germany")),
  Product("Pen", 2.50, "Office", true, 3, Address("London", "UK")),
  Product("Monitor", 349.99, "Electronics", false, 5, Address("Berlin", "Germany")),
  Product("Notebook", 5.99, "Office", true, 4, Address("London", "UK"))
)

// Compose a query: in-stock electronics under $500, from Berlin, highly rated
val query =
  (Product.category === "Electronics") &&
  (Product.inStock === true) &&
  (Product.price < 500.0) &&
  (Product.city === "Berlin") &&
  (Product.rating >= 4)

val results = filter(catalog, query)
// results: List(Product("Mouse", 29.99, "Electronics", true, 4, Address("Berlin", "Germany")))

// String operations
val searchQuery = Product.name.matches(".*top$")
val matches = filter(catalog, searchQuery)
// matches: List(Product("Laptop", ...))

// Arithmetic: compute discounted prices
val discounted = Product.price * 0.8
catalog.foreach { p =>
  println(s"${Product.name.get(p)}: ${discounted.eval(p)}")
}
```

## Going Further

- **[Part 2: SQL Generation](./query-dsl-sql.md)** -- Translating query expressions to SQL
- **[Part 3: Extending the Expression Language](./query-dsl-extending.md)** -- Adding custom operators (IN, BETWEEN, aggregates) beyond SchemaExpr
- **[Part 4: A Fluent SQL Builder](./query-dsl-fluent-builder.md)** -- Type-safe SELECT, UPDATE, INSERT, DELETE with seamless condition mixing
- **[Optics Reference](../reference/optics.md)** -- Full API coverage of Lens, Prism, Optional, and Traversal
- **[DynamicOptic Reference](../reference/dynamic-optic.md)** -- Runtime optic paths for programmatic query construction
- **[Schema Reference](../reference/schema.md)** -- Schema derivation and type-level metadata
- **[Path Interpolator](../path-interpolator.md)** -- String-based path construction with `p"..."` syntax

The `SchemaExpr` expression tree is a sealed trait, making it straightforward to write interpreters that translate queries to SQL, MongoDB filters, Elasticsearch queries, or any other target language. Because each optic carries its `DynamicOptic` path (via `toDynamic`), you can extract field names and paths programmatically for these translations.
