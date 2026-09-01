# Maybe

> `Maybe[A]` is a **low-allocation alternative to `Option[A]`** that uses `null` to represent the absence of a value. It is an opaque type alias for `A | Null`, allowing you to write nullable-like code with the safety and ergonomics of an Option-style API. Core types: `Maybe[A]`.

Here's the type definition and basic construction:

```scala
opaque type Maybe[+A] = A | Null

val present: Maybe[Int] = Maybe.present(42)
val absent: Maybe[Int]  = Maybe.absent
```

## Motivation

When working with optional values, you face a choice: `Option[A]` provides type safety and functional composition but allocates a wrapper object for every value. `Maybe[A]` provides an alternative with different trade-offs depending on your Scala version.

**On Scala 3:** `Maybe[A]` eliminates allocation overhead by leveraging union types and null semantics—every value is either the unwrapped value itself or `null`. This is ideal for performance-critical code where allocations impact throughput or latency. The type is an opaque alias for `A | Null`, giving you a dedicated API (`map`, `flatMap`, `filter`, etc.) with zero runtime wrapper overhead—just null checks.

**On Scala 2.13:** `Maybe[A]` is implemented as a sealed trait (`Present[A]` | `Absent`). Present values allocate a wrapper, so the allocation savings versus `Option` are less pronounced. However, the unified API and interoperability benefits still apply.

### Why Maybe over Option?

- **Zero allocation**: Every `Maybe` is either the value itself or `null`—no wrapper objects
- **Familiar API**: All your favorite `Option` combinators (`map`, `flatMap`, `fold`, etc.)
- **Type safety**: The opaque type prevents accidentally mixing nullable and non-nullable values
- **Interoperable**: Seamless conversion to/from `Option` with `toOption` and `Maybe#fromOption`

## Installation

Add the `zio-blocks-maybe` module to your build:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-maybe" % "0.0.51"
```

For Scala.js:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-maybe" % "0.0.51"
```

Supported Scala versions: 2.13.x and 3.x

## Overview

`Maybe[A]` provides a complete set of operations for working with optional values:

- **Constructors**: `Maybe.apply`, `Maybe.present`, `Maybe.absent`, `Maybe.fromOption`
- **Predicates**: `Maybe#isAbsent`, `isPresent`, `isEmpty`, `isDefined`, `nonEmpty`
- **Access**: `get`, `Maybe#getOrElse`, `orElse`, `orNull`
- **Transformations**: `map`, `flatMap`, `flatten`
- **Filtering**: `filter`, `filterNot`, `collect`
- **Logical**: `exists`, `forall`, `contains`
- **Conversions**: `toOption`, `toList`, `toSeq`, `iterator`, `toRight`, `toLeft`
- **Composition**: `Maybe#zip`, `unzip`, `unzip3`, `fold`, `foreach`

## How It Works

The workflow is straightforward: create a `Maybe` from a value or `None`, transform and filter it using functional operations, extract the result with safe accessors, and fall back to defaults when needed:

```
Value ─→ Maybe.apply (wrap) ─→ map / flatMap (transform) 
  ↓                                    ↓
[null]  ←─────── isAbsent (test) ← filter / collect (refine)
  ↓                                    ↓
absent ─→ orElse (fallback) ─→ get / getOrElse (extract)
```

### Typical Data Flow

1. **Create** a `Maybe` using `Maybe.apply`, `Maybe.present`, `Maybe.fromOption`, or explicitly with `Maybe.absent` for no value
2. **Transform** values using `map` or `flatMap` — operations on absent values short-circuit and remain absent
3. **Filter** with `filter` or `collect` to refine the value or produce absence based on a predicate
4. **Compose** with other `Maybe` values using `Maybe#zip` or flatMap chains to build complex workflows
5. **Extract** the result using `get` (throws on absence), `Maybe#getOrElse` (provides a default), `toOption` (convert to `Option`), or `fold` (handle both branches)

## Common Patterns

Here are key patterns for working effectively with `Maybe`:

### Present and Absent States

Every `Maybe` is either present (holds a non-null value) or absent (is `null`). Test the state with predicates:

```scala
import zio.blocks.maybe._

val value: Maybe[Int] = Maybe.present(42)

if (value.isPresent) {
  println(value.get) // Safe: we know it's present
} else {
  println("No value")
}
```

### Safe Extraction with Defaults

Use `Maybe#getOrElse` to provide a fallback when the value is absent, avoiding the exception risk of `get`:

```scala
import zio.blocks.maybe._

val userId: Maybe[String] = Maybe.absent
val name = userId.getOrElse("anonymous")
```

### Chaining Transformations

Combine `map` and `flatMap` to thread operations through optional values. Absence propagates automatically:

```scala
import zio.blocks.maybe._

val userId: Maybe[Int] = Maybe.present(123)

val greeting = userId
  .map(id => s"User $id")
  .map(msg => s"Hello, $msg!")

println(greeting) // Maybe[String] containing "Hello, User 123!"
```

### Filtering with Predicates

Use `filter` to keep a value only if it satisfies a condition, or `collect` with a partial function for both filtering and transformation:

```scala
import zio.blocks.maybe._

val age: Maybe[Int] = Maybe.present(25)

val isAdult = age.filter(_ >= 18) // Maybe[Int] containing 25
val isChild = age.filter(_ < 18)  // Maybe[Int] absent
```

### Combining Multiple Maybes

Use `Maybe#zip` to combine two `Maybe` values into a tuple, or chain multiple operations with `flatMap`:

```scala
import zio.blocks.maybe._

val firstName: Maybe[String] = Maybe.present("Alice")
val lastName: Maybe[String]  = Maybe.present("Smith")

val fullName = firstName.zip(lastName).map { case (f, l) => s"$f $l" }
```

### Converting to and from Option

Interoperate seamlessly with `Option` using `toOption` and `Maybe#fromOption`:

```scala
import zio.blocks.maybe._

val opt: Option[String] = Some("value")
val m: Maybe[String]    = Maybe.fromOption(opt)
val backToOpt           = m.toOption
```

### Handling Errors with Either

Convert a `Maybe` to an `Either` to propagate errors in fail-fast computations:

```scala
import zio.blocks.maybe._

val value: Maybe[Int] = Maybe.present(10)

val result: Either[String, Int] = value.toRight("Value not found")
```

## Integration Points

`Maybe[A]` integrates with the broader Scala and ZIO ecosystem:

- **Option interoperability**: Convert bidirectionally with `toOption` and `Maybe#fromOption`; a `Conversion[Option[A], Maybe[A]]` is provided for implicit conversions
- **Schema support**: Schema codecs use private unsafe methods (`unsafeIsAbsent`, `unsafeGet`, `unsafeWrap`) for efficient serialization and deserialization
- **For-comprehensions**: Supports `withFilter` for guard clauses in for-expressions
- **Collections**: Convert to `List`, `Seq`, or `Iterator` for bulk operations
- **Pattern matching**: Works naturally in match expressions, though testing with predicates is more common

### Example: For-Comprehension with Guards

Combine multiple `Maybe` values with filter guards:

```scala
import zio.blocks.maybe._

val maybeX: Maybe[Int] = Maybe.present(5)
val maybeY: Maybe[Int] = Maybe.present(10)

val result = for {
  x <- maybeX
  y <- maybeY
  if x + y > 10
} yield x + y

println(result) // Maybe[Int] containing 15
```

## Operations Reference

All `Maybe` operations are organized by category. Each subsection documents a group of related methods with examples.

### Constructors

Create `Maybe` values using these factory methods:

#### Maybe.apply

Wraps a value in `Maybe`, treating `null` as `Maybe.absent`:

```scala
import zio.blocks.maybe._

val present = Maybe(42)                  // Maybe[Int] containing 42
val absent: Maybe[String] = Maybe(null)  // Maybe[String] absent
```

#### Maybe.present

Explicitly wraps a non-null value:

```scala
import zio.blocks.maybe._

val value: Maybe[Int] = Maybe.present(100)
```

#### Maybe.absent

Creates an absent value for any type:

```scala
import zio.blocks.maybe._

val empty: Maybe[String] = Maybe.absent
```

#### Maybe.empty

Alias for `Maybe.absent`:

```scala
import zio.blocks.maybe._

val empty: Maybe[Double] = Maybe.empty
```

#### Maybe.fromOption

Converts an `Option` to `Maybe`:

```scala
import zio.blocks.maybe._

val fromSome = Maybe.fromOption(Some(5))  // Maybe[Int] containing 5
val fromNone = Maybe.fromOption(None)     // Maybe[Nothing] absent
```

### State Testing

Test whether a `Maybe` is present or absent:

#### isPresent, isDefined, nonEmpty

All three are equivalent—return true if the value is non-null:

```scala
import zio.blocks.maybe._

val value: Maybe[Int] = Maybe.present(42)
println(value.isPresent)  // true
println(value.isDefined)  // true
println(value.nonEmpty)   // true
```

#### isAbsent, isEmpty

Both return true if the value is `null`:

```scala
import zio.blocks.maybe._

val empty: Maybe[Int] = Maybe.absent
println(empty.isAbsent)  // true
println(empty.isEmpty)   // true
```

### Access

Retrieve the value or provide a fallback:

#### get

Unwraps the value, throwing `NoSuchElementException` if absent:

```scala
import zio.blocks.maybe._

val value: Maybe[Int] = Maybe.present(10)
println(value.get)  // 10

try {
  Maybe.absent[Int].get
} catch {
  case e: NoSuchElementException => println(e.getMessage)  // "Maybe.absent.get"
}
```

#### getOrElse

Returns the value if present, or evaluates the default:

```scala
import zio.blocks.maybe._

val value: Maybe[Int]    = Maybe.absent
val withDefault: Int     = value.getOrElse(99)
println(withDefault)  // 99
```

#### orElse

Returns the current `Maybe` if present, or another `Maybe`:

```scala
import zio.blocks.maybe._

val first: Maybe[Int]  = Maybe.absent
val second: Maybe[Int] = Maybe.present(42)
val result = first.orElse(second)
println(result.get)  // 42
```

#### orNull

Converts to nullable, returning the value or `null`:

```scala
import zio.blocks.maybe._

val value: Maybe[String] = Maybe.absent
val nullable: String = value.orNull
println(nullable)  // null
```

### Transformations

Transform values or short-circuit on absence:

#### map

Applies a function if present, remains absent otherwise:

```scala
import zio.blocks.maybe._

val value: Maybe[Int]      = Maybe.present(5)
val doubled: Maybe[Int]    = value.map(_ * 2)
val absent: Maybe[String]  = Maybe.absent.map(_ => "never runs")
println(doubled.get)  // 10
println(absent.isAbsent)  // true
```

#### flatMap

Chains operations that return `Maybe`, flattening the result:

```scala
import zio.blocks.maybe._

def safeDivide(a: Int, b: Int): Maybe[Int] =
  if (b == 0) Maybe.absent else Maybe(a / b)

val result = Maybe.present(10).flatMap(safeDivide(_, 2))
println(result.get)  // 5

val divideByZero = Maybe.present(10).flatMap(safeDivide(_, 0))
println(divideByZero.isAbsent)  // true
```

#### flatten

Unwraps a nested `Maybe`:

```scala
import zio.blocks.maybe._

val nested: Maybe[Maybe[Int]] = Maybe.present(Maybe.present(42))
val flat: Maybe[Int]          = nested.flatten
println(flat.get)  // 42
```

### Filtering

Keep or discard values based on predicates:

#### filter

Keeps the value only if the predicate is true:

```scala
import zio.blocks.maybe._

val value: Maybe[Int] = Maybe.present(10)
val even = value.filter(_ % 2 == 0)  // Present: 10
val odd  = value.filter(_ % 2 != 0)  // Absent
println(even.get)  // 10
println(odd.isAbsent)  // true
```

#### filterNot

Inverse of `filter`—keeps the value if the predicate is false:

```scala
import zio.blocks.maybe._

val value: Maybe[Int]   = Maybe.present(5)
val notOdd: Maybe[Int]  = value.filterNot(_ % 2 != 0)
println(notOdd.isAbsent)  // true
```

#### collect

Combines filtering with transformation using a partial function:

```scala
import zio.blocks.maybe._

val value: Maybe[Int] = Maybe.present(8)
val result = value.collect { case n if n % 2 == 0 => s"even: $n" }
println(result.get)  // "even: 8"

val odd: Maybe[Int]     = Maybe.present(7)
val noMatch: Maybe[String] = odd.collect { case n if n % 2 == 0 => s"even: $n" }
println(noMatch.isAbsent)  // true
```

### Logical Predicates

Test conditions without extracting the value:

#### contains

Returns true if the value equals the given element:

```scala
import zio.blocks.maybe._

val value: Maybe[Int] = Maybe.present(42)
println(value.contains(42))  // true
println(value.contains(41))  // false
println(Maybe.absent[Int].contains(42))  // false
```

#### exists

Returns true if the value satisfies the predicate:

```scala
import zio.blocks.maybe._

val value: Maybe[Int] = Maybe.present(10)
println(value.exists(_ > 5))   // true
println(value.exists(_ > 20))  // false
println(Maybe.absent[Int].exists(_ => true))  // false
```

#### forall

Returns true if the value satisfies the predicate, or if absent (vacuous truth):

```scala
import zio.blocks.maybe._

val value: Maybe[Int] = Maybe.present(10)
println(value.forall(_ > 5))   // true
println(value.forall(_ > 20))  // false
println(Maybe.absent[Int].forall(_ => false))  // true (absent = vacuously true)
```

### Iteration and Conversion

Convert `Maybe` to other types or iterate its contents:

#### foreach

Executes a side effect if present:

```scala
import zio.blocks.maybe._

val value: Maybe[Int] = Maybe.present(42)
value.foreach(x => println(s"Value: $x"))

Maybe.absent[Int].foreach(_ => println("not called"))
```

#### toOption

Converts to `Option[A]`:

```scala
import zio.blocks.maybe._

val value: Maybe[Int]     = Maybe.present(7)
val some: Option[Int]     = value.toOption
val noneVal: Option[Int]  = Maybe.absent[Int].toOption
println(some)  // Some(7)
println(noneVal)  // None
```

#### toList

Converts to `List[A]`:

```scala
import zio.blocks.maybe._

val value: Maybe[Int]   = Maybe.present(5)
val list: List[Int]     = value.toList
val emptyList: List[Int] = Maybe.absent[Int].toList
println(list)  // List(5)
println(emptyList)  // List()
```

#### toSeq

Converts to `Seq[A]`:

```scala
import zio.blocks.maybe._

val value: Maybe[String] = Maybe.present("hello")
val seq: Seq[String]     = value.toSeq
println(seq)  // Seq("hello")
```

#### iterator

Creates an iterator over the value:

```scala
import zio.blocks.maybe._

val value: Maybe[Int] = Maybe.present(42)
val it = value.iterator
println(it.toList)  // List(42)

Maybe.absent[Int].iterator.toList  // List()
```

### Either Conversion

Convert `Maybe` to `Either` for error handling:

#### toRight

Converts to `Either[X, A]` with a left error value:

```scala
import zio.blocks.maybe._

val value: Maybe[Int] = Maybe.present(10)
val right: Either[String, Int] = value.toRight("not found")
println(right)  // Right(10)

val absent: Maybe[Int]         = Maybe.absent
val left: Either[String, Int]  = absent.toRight("not found")
println(left)  // Left("not found")
```

#### toLeft

Converts to `Either[A, X]` with a right success value:

```scala
import zio.blocks.maybe._

val value: Maybe[String] = Maybe.present("error")
val left: Either[String, Unit] = value.toLeft(())
println(left)  // Left("error")

val absent: Maybe[String]      = Maybe.absent
val right: Either[String, Unit] = absent.toLeft(())
println(right)  // Right(())
```

### Composition

Combine multiple `Maybe` values:

#### zip

Combines two `Maybe` values into a tuple:

```scala
import zio.blocks.maybe._

val x: Maybe[Int]    = Maybe.present(1)
val y: Maybe[String] = Maybe.present("a")
val tuple = x.zip(y)
println(tuple.get)  // (1, "a")

val absent: Maybe[String] = Maybe.absent
val zipped = x.zip(absent)
println(zipped.isAbsent)  // true
```

#### unzip

Splits a `Maybe[(A, B)]` into a tuple of `Maybe[A]` and `Maybe[B]`:

```scala
import zio.blocks.maybe._

val tuple: Maybe[(Int, String)] = Maybe.present((42, "answer"))
val (x, y) = tuple.unzip
println(x.get)  // 42
println(y.get)  // "answer"

val absent: Maybe[(Int, String)] = Maybe.absent
val (ax, ay) = absent.unzip
println(ax.isAbsent && ay.isAbsent)  // true
```

#### unzip3

Splits a `Maybe[(A, B, C)]` into three `Maybe` values:

```scala
import zio.blocks.maybe._

val triple: Maybe[(Int, String, Double)] = Maybe.present((1, "one", 1.0))
val (a, b, c) = triple.unzip3
println(a.get)  // 1
println(b.get)  // "one"
println(c.get)  // 1.0
```

### Folding

Reduce a `Maybe` to a value by handling both branches:

#### fold

Applies one of two functions based on presence:

```scala
import zio.blocks.maybe._

val value: Maybe[Int] = Maybe.present(10)
val result = value.fold(-1)((x: Int) => x * 2)
println(result)  // 20

val absent: Maybe[Int] = Maybe.absent
val fallback = absent.fold(-1)((x: Int) => x * 2)
println(fallback)  // -1
```

### For-Comprehensions

Use `withFilter` to support guard clauses:

#### withFilter

Enables guarded for-expressions:

```scala
import zio.blocks.maybe._

val x: Maybe[Int] = Maybe.present(5)
val y: Maybe[Int] = Maybe.present(15)

val result = for {
  a <- x
  b <- y
  if a + b > 15
} yield a + b

println(result.get)  // 20
```

## Running Examples

End-to-end workflows combining multiple `Maybe` operations:

### Example: Parsing and Transforming User Input

Build a pipeline that parses user input, validates it, and transforms the result:

```scala
import zio.blocks.maybe._

case class User(id: Int, name: String, age: Int)

def parseId(input: String): Maybe[Int] =
  Maybe.fromOption(input.toIntOption)

def validateAge(age: Int): Maybe[Int] =
  Maybe.absent[Int].orElse(
    if (age >= 0 && age <= 150) Maybe.present(age) else Maybe.absent
  )

val input = "42"
val processedAge = parseId(input)
  .flatMap(id => Maybe.present(User(id, "Alice", 30)))
  .map(_.age)
  .flatMap(validateAge)

println(processedAge.getOrElse(-1))  // 30
```

### Example: Chaining Optional Database Results

Work with nullable database results, filtering and transforming as needed:

```scala
import zio.blocks.maybe._

case class Product(id: Int, name: String, price: Double, inStock: Boolean)

def findProduct(id: Int): Maybe[Product] =
  if (id > 0) Maybe.present(Product(id, s"Product $id", 99.99, true))
  else Maybe.absent

def applyDiscount(product: Product, percent: Int): Maybe[Product] =
  if (percent >= 0 && percent <= 100)
    Maybe.present(product.copy(price = product.price * (1 - percent / 100.0)))
  else Maybe.absent

val discountedProduct = findProduct(123)
  .filter(_.inStock)
  .flatMap(applyDiscount(_, 20))
  .map(p => s"${p.name} now costs $$${p.price}")

println(discountedProduct.getOrElse("Product not available"))
```

### Example: Combining Multiple Maybes with Fallbacks

Handle scenarios where multiple optional values need to be combined:

```scala
import zio.blocks.maybe._

case class Config(host: Maybe[String], port: Maybe[Int], timeout: Maybe[Long])

def buildConnection(config: Config): String = {
  val host = config.host.getOrElse("localhost")
  val port = config.port.getOrElse(8080)
  val timeout = config.timeout.getOrElse(5000L)
  s"Connection to $host:$port with timeout $timeout ms"
}

val config = Config(
  host = Maybe.present("api.example.com"),
  port = Maybe.absent,
  timeout = Maybe.present(10000L)
)

println(buildConnection(config))
```

### Example: Error Handling with Either Conversion

Convert `Maybe` to `Either` for composable error handling in result types:

```scala
import zio.blocks.maybe._

case class Request(id: Maybe[String], method: Maybe[String])

def validateRequest(req: Request): Either[String, (String, String)] = {
  for {
    id <- req.id.toRight("Missing request ID")
    method <- req.method.toRight("Missing HTTP method")
  } yield (id, method)
}

val validReq = Request(
  id = Maybe.present("req-123"),
  method = Maybe.present("GET")
)

val invalidReq = Request(
  id = Maybe.absent,
  method = Maybe.present("POST")
)

println(validateRequest(validReq))  // Right((req-123, GET))
println(validateRequest(invalidReq))  // Left(Missing request ID)
```

### Example: Building a Computation Pipeline

Chain multiple transformations with fallback at each step:

```scala
import zio.blocks.maybe._

def getUser(id: Int): Maybe[String] =
  if (id > 0) Maybe.present(s"user_$id") else Maybe.absent

def getUserEmail(username: String): Maybe[String] =
  if (username.nonEmpty) Maybe.present(s"$username@example.com") else Maybe.absent

def getUserProfile(id: Int): Maybe[String] = {
  val username = getUser(id)
  username
    .flatMap(getUserEmail)
    .map(email => s"Profile for $email")
    .orElse(Maybe.present("Guest user"))
}

println(getUserProfile(42))  // Profile for user_42@example.com
println(getUserProfile(-1))  // Guest user
```
