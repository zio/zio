# Combinators

> The `combinators` module provides compile-time typeclasses for composing and decomposing values in type-safe ways. Each module focuses on a specific domain: tuples, choices, concatenation widening, Either types, and union types.

## Overview

The combinators module consists of five core modules:

- **Tuples** - Tuple composition with automatic flattening and separation
- **Choices** - Cross-version branch construction and elimination over `|`
- **Concat** - Scala-2-only union-aware widening for sequential composition
- **Eithers** - Either canonicalization to left-nested form
- **Unions** - Union type operations (Scala 3 only)

Each module provides:
- A unified typeclass (e.g., `Tuples.Tuples[L, R]`) that provides both `Tuples.Tuples#combine` and `Tuples.Tuples#separate` operations
- A convenience function like `Tuples.combine` (the `Tuples#separate` operation is available on the typeclass instance)

All typeclasses are derived automatically via compile-time resolution and provide zero-cost abstractions.

## Installation

Add the following to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-blocks-combinators" % "0.0.51"
```

For cross-platform projects (Scala.js):

```sbt
libraryDependencies += "dev.zio" %%% "zio-blocks-combinators" % "0.0.51"
```

Supported platforms:
- **Tuples, Choices, Eithers**: JVM, Scala.js (Scala 2.13 and 3.x)
- **Concat**: Scala 2.13 only
- **Unions**: JVM, Scala.js (Scala 3 only)

## Motivation

Building type-safe, composable systems requires managing values and types at both runtime and compile time. The combinators module solves three distinct problems that arise in complex Scala applications:

### The Tuple Nesting Problem

When building up results step-by-step—aggregating function parameters, accumulating intermediate results, or constructing compound values—you often end up with deeply nested tuples:

```scala
// Manual nesting is tedious and error-prone
val step1 = (1, "a")
// step1: Tuple2[Int, String] = (1, "a")
val step2 = (step1, true)
// step2: Tuple2[Tuple2[Int, String], Boolean] = ((1, "a"), true)
val step3 = (step2, 3.14)
// step3: Tuple2[Tuple2[Tuple2[Int, String], Boolean], Double] = (
//   ((1, "a"), true),
//   3.14
// )
val step4 = (step3, 'x') 
// step4: Tuple2[Tuple2[Tuple2[Tuple2[Int, String], Boolean], Double], Char] = (
//   (((1, "a"), true), 3.14),
//   'x'
// )
```

This creates two problems:
1. **Ergonomic burden**: Consumers of compound values must destructure deeply nested structures
2. **Inconsistency**: Different code paths produce different tuple shapes, making composition fragile

The `Tuples` combinator automatically flattens these structures, producing clean, predictable tuples at each step.

### The Either Canonicalization Problem

Error handling often involves composing multiple error types through Either chains. Without systematic canonicalization, Either types nest unpredictably:

```scala
// Inconsistent nesting across code paths
val result1: Either[E1, V] = Left(e1)
val result2: Either[E1, Either[E2, V]] = Right(Left(e2))
val result3: Either[Either[E1, E2], V] = Right(Right(v))
// Each path has a different structure!
```

This causes problems when:
1. **Serializing error types** for schemas (each variant has a different shape)
2. **Accumulating errors** (inconsistent nesting makes aggregation complex)
3. **Pattern matching** (must handle multiple nesting patterns)

The `Eithers` combinator canonicalizes all Either types to a uniform left-nested form, ensuring systematic error composition.

### The Scala 3 Union Type Gap

Scala 3 introduces native union types (`A | B`) that are more idiomatic than `Either[A, B]`. However, existing code, libraries, and serialization infrastructure are built around `Either`. When adopting Scala 3, you face a choice:

1. Stick with `Either` for compatibility (missing idiomatic Scala 3 syntax)
2. Switch to union types (breaking compatibility with Either-based code)
3. Maintain two parallel type systems (duplication and cognitive overhead)

The `Unions` combinator bridges this gap, enabling bidirectional conversion between `Either[L, R]` and `L | R` with zero runtime overhead. Use union types idiomatically in your APIs while maintaining Either compatibility at serialization boundaries.

## Quick Example

Here is how to combine multiple values and canonicalize error types:

```scala
import zio.blocks.combinators.{Tuples, Eithers}

// Aggregate three values into a flattened tuple
val username: String = "alice"
// username: String = "alice"
val userId: Int = 42
// userId: Int = 42
val email: String = "alice@example.com"
// email: String = "alice@example.com"
val userTuple: Tuple3[String, Int, String] = Tuples.combine(username, Tuples.combine(userId, email))
// userTuple: Tuple3[String, Int, String] = ("alice", 42, "alice@example.com")

// Canonicalize nested Either types to left-nested form
val validationError: Either[String, Either[String, Boolean]] = Right(Left("invalid email"))
// validationError: Either[String, Either[String, Boolean]] = Right(
//   Left("invalid email")
// )
val canonical      : Either[Either[String, String], Boolean] = Eithers.combine(validationError)
// canonical: Either[Either[String, String], Boolean] = Left(
//   Right("invalid email")
// )
```

## Concat (Scala 2 Only)

`Concat` is the Scala-2-only witness used by APIs such as `Stream.++` / `Stream.concat` to preserve Scala 3-style union behavior without introducing a separate operator.

Its rules are:

- same type => keep that type
- subtype + supertype => keep the supertype
- siblings with a unique meaningful common supertype => keep that supertype (e.g. `Dog` and `Cat` under sealed `Animal` collapse to `Animal`)
- otherwise (no shared meaningful supertype) => widen to `Either[L, R]` (the Scala 2 encoding of `L | R`)

For example, Scala 2 infers witnesses equivalent to these shapes:

```scala
Concat.Concat.WithOut[Int, Int, Int]
Concat.Concat.WithOut[Dog, Animal, Animal]
Concat.Concat.WithOut[Dog, Cat, Animal]
Concat.Concat.WithOut[String, Int, Either[String, Int]]
```

A common supertype is "meaningful" when it is something other than the noise types Scala 2's LUB inference produces by default (`Any`, `AnyRef`, `AnyVal`, `Object`, `Product`, `Serializable`, `java.io.Serializable`, `Comparable`). If filtering those parents leaves exactly one candidate, it becomes the result type — and the witness is identity-like, so callers such as `Stream.concat` reuse values bare without wrapping. Zero or multiple meaningful parents fall through to `Either`.

Unlike `Choices`, `Concat` is not usually called directly at runtime. It exists mainly so shared Scala-2 APIs can infer the same public result types that Scala 3 expresses with native unions.

## Tuples

The `Tuples` module combines values into flat tuples and separates them back.

### combine

To combine two values into a flattened tuple:

```scala
import zio.blocks.combinators.Tuples

val result1 = Tuples.combine(1, "hello")                 // (1, "hello")
val result2 = Tuples.combine((1, "hello"), true)         // (1, "hello", true)
val result3 = Tuples.combine((1, "hello"), (true, 3.14)) // (1, "hello", true, 3.14)j
```

#### Identity Handling

Unit and EmptyTuple values are automatically eliminated:

```scala
import zio.blocks.combinators.Tuples

Tuples.combine((), 42)              // 42
Tuples.combine("hello", ())          // "hello"
Tuples.combine(EmptyTuple, "world")  // "world"
```

#### Tuple Flattening

Nested tuples are automatically flattened:

```scala
import zio.blocks.combinators.Tuples

Tuples.combine((1, "a"), true)          // (1, "a", true)
Tuples.combine((1, "a"), (true, 3.14))  // (1, "a", true, 3.14)
```

### separate

To split a tuple into its init (all but last) and last element, access `Tuples#separate` via the unified typeclass instance:

```scala
import zio.blocks.combinators.Tuples

val t2 = summon[Tuples.Tuples[Int, String]]
t2.separate((1, "hello")) // ((1), "hello")

val t3 = summon[Tuples.Tuples[(Int, String), Boolean]]
t3.separate((1, "hello", true)) // ((1, "hello"), true)

val t4 = summon[Tuples.Tuples[(Int, String, Boolean), Double]]
t4.separate((1, "hello", true, 3.14)) // ((1, "hello", true), 3.14)
```

When building recursive data structures like path codecs, `separate` decomposes combined tuples to process each segment independently:

```scala
import zio.blocks.combinators.Tuples

// Simulating recursive path encoding: a codec combines left and right path segments
case class PathSegment(name: String, value: String)

def encodePathSegment(combined: (String, String)): PathSegment = {
  val tuples = summon[Tuples.Tuples[String, String]]
  val (left, right) = tuples.separate(combined)
  PathSegment(left, right)
}

// Decompose a 3-element path into segments for recursive encoding
val path: (String, String, String) = ("users", "123", "profile")
val tuples3 = summon[Tuples.Tuples[(String, String), String]]
val (prefix, suffix) = tuples3.separate(path)
// prefix = ("users", "123"), suffix = "profile"
```

## Eithers

The `Eithers` module canonicalizes Either types to left-nested form and separates them.

### combine

To transform an `Either[L, R]` into its left-nested canonical form:

```scala
import zio.blocks.combinators.Eithers

// Atomic Either - unchanged
Eithers.combine(Left(42): Either[Int, String])
// res5: Either[Int, String] = Left(42)

// Right-nested Either - reassociates to left-nested
val input2 = Right(Right(true)): Either[Int, Either[String, Boolean]]
// input2: Either[Int, Either[String, Boolean]] = Right(Right(true))
Eithers.combine(input2)
// res6: Either[Either[Int, String], Boolean] = Right(true)

// Left(42) becomes Left(Left(42))
val input3 = Left(42): Either[Int, Either[String, Boolean]]
// input3: Either[Int, Either[String, Boolean]] = Left(42)
Eithers.combine(input3)
// res7: Either[Either[Int, String], Boolean] = Left(Left(42))
```

#### Canonical Form

The canonical form is always left-nested:

```
Right-nested input:          Left-nested output:
Either[A, Either[B, C]]  =>  Either[Either[A, B], C]
Either[A, Either[B, Either[C, D]]]  =>  Either[Either[Either[A, B], C], D]
```

This transformation preserves values while reassociating the structure:
- `Left(a)` → `Left(Left(a))`
- `Right(Left(b))` → `Left(Right(b))`
- `Right(Right(c))` → `Right(c)`

### separate

`Eithers#separate` is accessed via the unified typeclass instance and reverses the canonicalization performed by `combine`. Together, they form a round-trip: canonicalizing to left-nested form and then separating back to the original structure:

```scala
import zio.blocks.combinators.Eithers

val e = summon[Eithers.Eithers[Int, String]]
// e: Eithers[Int, String] {
  type Out >: Either[Int, String] <: Either[Int, String]
} = zio.blocks.combinators.Eithers$Eithers$AtomicInstance@65d7b306
val input = Left(42): Either[Int, String]
// input: Either[Int, String] = Left(42)
e.separate(e.combine(input))
// res8: Either[Int, String] = Left(42)
```

Use `separate` to decompose a canonical Either back to its original structure when you need to handle different error types differently:

```scala
import zio.blocks.combinators.Eithers

sealed trait ValidationError
case class FieldError(field: String) extends ValidationError
case class FormatError(message: String) extends ValidationError

// You have a right-nested Either from multiple validation steps
val input: Either[FieldError, Either[FormatError, String]] = Right(Left(FormatError("invalid date")))

val eithers = summon[Eithers.Eithers[FieldError, Either[FormatError, String]]]
```

Canonicalize to left-nested form for uniform processing, then reverse it to extract the original error types:

```scala
// Original form: Either[FieldError, Either[FormatError, String]]
input
// res9: Either[FieldError, Either[FormatError, String]] = Right(
//   Left(FormatError("invalid date"))
// )

// Canonicalize to left-nested form for uniform processing
val canonicalized = eithers.combine(input)
// canonicalized: Either[Either[FieldError, FormatError], String] = Left(
//   Right(FormatError("invalid date"))
// )

// Reverse canonicalization to extract the original error types
val original = eithers.separate(canonicalized)
// original: Either[FieldError, Either[FormatError, String]] = Right(
//   Left(FormatError("invalid date"))
// )

// Back to original form
original
// res10: Either[FieldError, Either[FormatError, String]] = Right(
//   Left(FormatError("invalid date"))
// )
```

Handle each error type independently:

```scala
// Handle each error type independently
original match {
  case Left(fieldErr: FieldError) => println(s"Field validation failed: ${fieldErr.field}")
  case Right(Left(formatErr: FormatError)) => println(s"Format error: ${formatErr.message}")
  case Right(Right(value)) => println(s"Valid: $value")
}
```

## Unions (Scala 3 Only)

The `Unions` module converts between Either types and Scala 3 union types.

### combine

`Unions.Unions[L, R]` converts an `Either[L, R]` to a union type `L | R`:

```scala
import zio.blocks.combinators.Unions

val either1 = Left(42): Either[Int, String]
// either1: Either[Int, String] = Left(42)
Unions.combine(either1)
// res12: Int | String = 42

val either2 = Right("hello"): Either[Int, String]
// either2: Either[Int, String] = Right("hello")
Unions.combine(either2)
// res13: Int | String = "hello"
```

### separate

`Unions#separate` is accessed via the unified typeclass instance and discriminates a union type back to Either:

```scala
import zio.blocks.combinators.Unions

val u = summon[Unions.Unions.WithOut[Int, String, Int | String]]
// u: Unions[Int, String] {
  type Out >: Int | String <: Int | String
} = zio.blocks.combinators.Unions$Unions$UnionInstance@64a090cd
u.separate(42: Int | String)
// res14: Either[Int, String] = Left(42)
u.separate("hello": Int | String)
// res15: Either[Int, String] = Right("hello")
```
### Same-Type Rejection

Union types collapse same types (`A | A` = `A`), making them ambiguous. The separator rejects overlapping types at compile time:

```scala
import zio.blocks.combinators.Unions

// Compile error: Union types must contain unique types
// val u = summon[Unions.Unions.WithOut[Int, Int, Int | Int]]

// Use Either for same-type alternation instead:
import zio.blocks.combinators.Eithers
val either: Either[Int, Int] = Left(1)  // Distinguishable via Left/Right
```

### Type Erasure Caveat

Union discrimination relies on runtime type tests, which are fragile for erased types:

```scala
import scala.collection.immutable.List

// Problematic: List[Int] and List[String] erase to List
val problematicValue: List[Int] | List[String] = List(1, 2, 3)
// Runtime cannot distinguish List[Int] from List[String]

// Safe: Use distinct concrete types
val value: Int | String = 42  // Works reliably
```

## Generic Usage Patterns

The combinators module supports both Scala 2's implicit parameters and Scala 3's context parameters. Here are idiomatic usage patterns for each:

  

**Scala 2**

To combine multiple values using implicit typeclass resolution:

```scala
import zio.blocks.combinators.Tuples

def combineAll[A, B, C](a: A, b: B, c: C)(
  implicit ab: Tuples.Tuples[A, B],
           abc: Tuples.Tuples[ab.Out, C]
): abc.Out = {
  val step1 = ab.combine(a, b)
  abc.combine(step1, c)
}

val result = combineAll(1, "hello", true)
// result: (Int, String, Boolean)
```

  
  

**Scala 3**

To combine multiple values using context parameters:

```scala
import zio.blocks.combinators.Tuples

def combineAll[A, B, C](a: A, b: B, c: C)(using
  ab: Tuples.Tuples[A, B],
  abc: Tuples.Tuples[ab.Out, C]
): abc.Out =
  val step1 = ab.combine(a, b)
  abc.combine(step1, c)

val result = combineAll(1, "hello", true)
// result: (Int, String, Boolean)
```

  

### Path-Dependent Types

The `Out`, `Left`, and `Right` type members are path-dependent:

```scala
import zio.blocks.combinators.Tuples

def process[L, R](l: L, r: R)(using t: Tuples.Tuples[L, R]): (L, R) =
  t.separate(t.combine(l, r))

val result: (Int, String) = process(1, "hello")
```

## Integration Points

The combinator types integrate with other ZIO Blocks modules through systematic composition:

**Schema Evolution**: The `Eithers` canonicalization strategy directly supports schema sum type encoding. When deriving schemas for sealed trait hierarchies, the combinator ensures all Either encodings use the same left-nested form, enabling consistent serialization across schema variants.

**Error Handling**: `Eithers` provides a foundation for systematic error composition. Libraries building polymorphic error types can leverage canonicalization to ensure uniform error nesting, preventing subtle bugs from inconsistent Either structure.

**Scala 3 APIs**: The `Unions` type enables idiomatic Scala 3 DSLs and API designs that use native union syntax. Gateway types that convert between union-based and Either-based representations (e.g., for serialization compatibility) can use `Unions` for zero-cost interop.

**Tuple-Based Builders**: The `Tuples` module supports builder patterns and accumulator-based APIs that need to combine heterogeneous values step-by-step. By flattening automatically, it eliminates the ergonomic burden of manual nesting, making fluent builder chains natural.

## Scala 2 vs Scala 3: Compatibility and Differences

The combinators module works across Scala 2.13 and Scala 3.x with **full source compatibility**. Write your code once; it compiles on both versions. However, certain features are version-specific due to language capabilities:

### Tuples: Version Differences

**Scala 2.13 Limitations:**
- Maximum arity of 22 (the standard library tuple limit)
- Tuple flattening only works when the left argument is a tuple (right argument cannot be recursively flattened)
- No `EmptyTuple` type (use `Unit` as identity instead)

**Scala 3.x Enhancements:**
- Unlimited arity (tuples are truly variable-length)
- Recursive flattening on both sides: `Tuples.combine((1, "a"), (true, 3.14))` flattens both tuples into a 4-tuple
- `EmptyTuple` as a first-class type with proper identity semantics

**Example: The difference in practice**

  

**Scala 2.13**

In Scala 2.13, combining two tuples on the right side fails to compile:

```scala
import zio.blocks.combinators.Tuples

// ERROR: right side not flattened
val result = Tuples.combine((1, 2), (3, 4))  // Type mismatch
```

  
  

**Scala 3.x**

In Scala 3.x, recursive flattening on both sides works seamlessly:

```scala
import zio.blocks.combinators.Tuples

// OK: both sides flattened
val result = Tuples.combine((1, 2), (3, 4))  // (1, 2, 3, 4)
```

  

### Eithers: Full Cross-Version Support

`Eithers` canonicalization works identically on Scala 2.13 and 3.x. No version-specific behavior. Use with confidence across versions—canonicalization is deterministic.

### Unions: Scala 3 Only

`Unions` requires Scala 3 because:
- Union types (`A | B`) are a Scala 3 language feature
- Runtime type tests (via `TypeTest`) are only available in Scala 3
- Scala 2 has no native union syntax

For Scala 2.13 codebases, use `Either` directly or `Eithers` canonicalization instead.

### Feature Matrix

| Feature                        | Scala 2.13 | Scala 3.x | Notes                         |
|--------------------------------|------------|-----------|-------------------------------|
| **Tuples.combine**             | ✅          | ✅         | Left-only flattening in 2.13  |
| **Tuples.separate**            | ✅          | ✅         | Works identically on both     |
| **Eithers.combine**            | ✅          | ✅         | No differences                |
| **Eithers.separate**           | ✅          | ✅         | No differences                |
| **Choices.left/right/separate**| ✅          | ✅         | Scala 2 uses `Either` alias   |
| **Unions.combine**             | ❌          | ✅         | Requires Scala 3              |
| **Unions.separate**            | ❌          | ✅         | Requires Scala 3              |
| **EmptyTuple as identity**     | ❌          | ✅         | Use `Unit` in Scala 2         |
| **Unlimited tuple arity**      | ❌          | ✅         | Limited to 22 in Scala 2      |
| **Recursive tuple flattening** | ❌          | ✅         | Right side not flattened in 2 |

### Migration Path from Scala 2 to 3

When adopting Scala 3, no changes are required for existing `Tuples` and `Eithers` code. Your code continues to work without modification. However, you can take advantage of new capabilities:

1. **Adopt `EmptyTuple` idiom**: Use `EmptyTuple` instead of `Unit` when combining with `Tuples` in Scala 3 for consistency with modern tuple syntax. Note that `Unit` remains fully supported and valid—`EmptyTuple` is a stylistic enhancement, not a replacement.
2. **Simplify tuple builders**: Leverage recursive flattening on both sides to remove manual nesting. In Scala 3, `Tuples.combine((1, "a"), (true, 3.14))` automatically flattens to `(1, "a", true, 3.14)`.
3. **Adopt `Choices` in shared code**: Use `Choices.left`, `Choices.right`, and `Choices.separate` when you want a single `|`-based API shape to compile on both Scala 2 and Scala 3.
4. **Adopt `Unions` in Scala 3-only code**: Replace `Either` with union types in new Scala 3-only code for idiomatic syntax using the `Unions` combinator.
5. **Gradual adoption**: Use `Choices` in cross-version modules and `Unions` in Scala-3-only modules. Convert between them at module boundaries using `Unions.combine` and `Unions.separate` as needed.

## See Also

- [Schema](./schema/schema.md) — The Schema module uses `Eithers` canonicalization for encoding sealed trait hierarchies and sum types with consistent Either nesting.
- **HTTP Model Schema** — When extracting multiple typed query parameters or headers in the HTTP Model schema module, `Eithers` provides systematic composition of error types for uniform error handling.
