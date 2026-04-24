# Combinators

> The `combinators` module provides compile-time typeclasses for composing and decomposing values in type-safe ways. Each module focuses on a specific domain: tuples, Either types, and union types.

The `combinators` module provides compile-time typeclasses for composing and decomposing values in type-safe ways. Each module focuses on a specific domain: tuples, Either types, and union types.

## Overview

The combinators module consists of three core modules:

- **Tuples** - Tuple composition with automatic flattening and separation
- **Eithers** - Either canonicalization to left-nested form
- **Unions** - Union type operations (Scala 3 only)

Each module provides:
- A unified typeclass (e.g., `Tuples.Tuples[L, R]`) that provides both `combine` and `separate` operations
- A convenience method `combine` (the `separate` operation is available on the typeclass instance)

All typeclasses are derived automatically via compile-time resolution and provide zero-cost abstractions.

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-combinators" % "<version>"
```

For cross-platform projects (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-combinators" % "<version>"
```

Supported platforms:
- **Tuples, Eithers**: JVM, Scala.js (Scala 2.13 and 3.x)
- **Unions**: JVM, Scala.js (Scala 3 only)

## Tuples

The `Tuples` module combines values into flat tuples and separates them back.

### combine

`Tuples.Tuples[L, R]` combines two values into a flattened tuple.

```scala

// Basic combination
val result1: (Int, String) = Tuples.combine(1, "hello")

// Tuple flattening
val result2: (Int, String, Boolean) = Tuples.combine((1, "hello"), true)

// Deep flattening (Scala 3)
val result3: (Int, String, Boolean, Double) = Tuples.combine((1, "hello"), (true, 3.14))
```

#### Identity Handling

Unit and EmptyTuple values are automatically eliminated:

```scala

// Unit on left - returns right value
val result1: Int = Tuples.combine((), 42)

// Unit on right - returns left value
val result2: String = Tuples.combine("hello", ())

// EmptyTuple identity (Scala 3)
val result3: String = Tuples.combine(EmptyTuple, "world")
```

#### Tuple Flattening

Nested tuples are automatically flattened:

```scala

// Tuple + value flattens to larger tuple
val result1: (Int, String, Boolean) = Tuples.combine((1, "a"), true)

// Tuple + tuple concatenates (Scala 3 - recursive flattening)
val result2: (Int, String, Boolean, Double) = Tuples.combine((1, "a"), (true, 3.14))

// Scala 2 - flattens left tuple only
val result3: (Int, String, (Boolean, Double)) = Tuples.combine((1, "a"), (true, 3.14))
```

### separate

`separate` is accessed via the unified typeclass instance and splits a tuple into its init (all but last) and last element.

```scala

// 2-tuple separation
val t2 = summon[Tuples.Tuples[Int, String]]  // Scala 3
// or: implicitly[Tuples.Tuples[Int, String]]  // Scala 2
val (left1, right1): (Int, String) = t2.separate((1, "hello"))
// left1 = 1, right1 = "hello"

// 3-tuple separation
val t3 = summon[Tuples.Tuples[(Int, String), Boolean]]
val (left2, right2): ((Int, String), Boolean) = t3.separate((1, "hello", true))
// left2 = (1, "hello"), right2 = true

// 4-tuple separation
val t4 = summon[Tuples.Tuples[(Int, String, Boolean), Double]]
val (left3, right3): ((Int, String, Boolean), Double) = t4.separate((1, "hello", true, 3.14))
// left3 = (1, "hello", true), right3 = 3.14

```
### Type-Level Operations

The output type is computed at compile time via the `Out` type member:

```scala

// Access the combiner with explicit output type
val combiner: Tuples.Tuples.WithOut[Int, String, (Int, String)] = 
  summon[Tuples.Tuples[Int, String]]

// Access with explicit types
val instance: Tuples.Tuples.WithOut[Int, String, (Int, String)] = 
  summon[Tuples.Tuples[Int, String]]
```

### Scala 2 vs Scala 3 Differences

| Feature | Scala 2.13 | Scala 3.x |
|---------|------------|-----------|
| Maximum tuple arity | 22 | Unlimited |
| Tuple flattening | Left tuple only | Recursive both sides |
| EmptyTuple identity | Not available | Supported |

## Eithers

The `Eithers` module canonicalizes Either types to left-nested form and separates them.

### combine

`Eithers.Eithers[L, R]` transforms an `Either[L, R]` into its left-nested canonical form.

```scala

// Atomic Either - unchanged
val result1: Either[Int, String] = Eithers.combine(Left(42): Either[Int, String])

// Right-nested Either - reassociates to left-nested
val input: Either[Int, Either[String, Boolean]] = Right(Right(true))
val result2: Either[Either[Int, String], Boolean] = Eithers.combine(input)
// Right(Right(true)) becomes Right(true)

// Left(42) becomes Left(Left(42))
val input2: Either[Int, Either[String, Boolean]] = Left(42)
val result3: Either[Either[Int, String], Boolean] = Eithers.combine(input2)
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

`separate` is accessed via the unified typeclass instance and peels the rightmost alternative from a canonical Either:

```scala

val e = summon[Eithers.Eithers[Int, String]]
val input: Either[Int, String] = Left(42)
val result: Either[Int, String] = e.separate(e.combine(input))
```

### Use Cases

Eithers canonicalization is useful for:
- **Schema sum type encoding** - Uniform representation of sealed traits
- **Error handling composition** - Combining error types systematically
- **Cross-version compatibility** - Works identically on Scala 2 and 3

## Unions (Scala 3 Only)

The `Unions` module converts between Either types and Scala 3 union types.

### combine

`Unions.Unions[L, R]` converts an `Either[L, R]` to a union type `L | R`:

```scala

val either: Either[Int, String] = Left(42)
val union: Int | String = Unions.combine(either)
// Result: 42 (typed as Int | String)

val either2: Either[Int, String] = Right("hello")
val union2: Int | String = Unions.combine(either2)
// Result: "hello" (typed as Int | String)
```

### separate

`separate` is accessed via the unified typeclass instance and discriminates a union type back to Either:

```scala

val u = summon[Unions.Unions.WithOut[Int, String, Int | String]]
val result: Either[Int, String] = u.separate(42: Int | String)
// Result: Left(42)

val result2: Either[Int, String] = u.separate("hello": Int | String)
// Result: Right("hello")
```
### Same-Type Rejection

Union types collapse same types (`A | A` = `A`), making them ambiguous. The separator rejects overlapping types at compile time:

```scala

// Compile error: Union types must contain unique types
// val u = summon[Unions.Unions.WithOut[Int, Int, Int | Int]]

// Use Either for same-type alternation instead:

val either: Either[Int, Int] = Left(1)  // Distinguishable via Left/Right
```

### Type Erasure Caveat

Union discrimination relies on runtime type tests, which are fragile for erased types:

```scala
// Problematic: List[Int] and List[String] erase to List
val value: List[Int] | List[String] = List(1, 2, 3)
// Runtime cannot distinguish List[Int] from List[String]

// Safe: Use distinct concrete types
val value: Int | String = 42  // Works reliably
```

## Generic Usage Patterns

### With Implicit Parameters (Scala 2)

```scala

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

### With Context Parameters (Scala 3)

```scala

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

def process[L, R](l: L, r: R)(using t: Tuples.Tuples[L, R]): (L, R) =
  t.separate(t.combine(l, r))

val result: (Int, String) = process(1, "hello")
```

### Type Aliases for Clarity

```scala

// Typeclass with known output type
type IntStringTuples = Tuples.Tuples.WithOut[Int, String, (Int, String)]

// Typeclass with known left/right types
type TripleTuples = Tuples.Tuples.WithOut[(Int, String), Boolean, (Int, String, Boolean)]
```

## Performance Characteristics

| Module | Time Complexity | Notes |
|--------|-----------------|-------|
| Tuples.combine | O(1) to O(n) | O(1) for small tuples; O(n) for flattening nested tuples |
| Tuples.separate | O(n) | Splits tuple at size-1 position |
| Eithers.combine | O(d) | d = nesting depth of right-nested Either |
| Eithers.separate | O(d) | Same as combine (delegates to combiner) |
| Unions.combine | O(1) | Direct Either fold |
| Unions.separate | O(1) | Single type test |

All operations are pure and allocation-minimal.

## Cross-Version Summary

| Feature | Scala 2.13 | Scala 3.x |
|---------|------------|-----------|
| Tuples.Tuples | Yes (max 22) | Yes (unlimited) |
| Eithers.Eithers | Yes | Yes |
| Unions.Unions | No | Yes |
| Recursive tuple flattening | No | Yes |
| EmptyTuple handling | No | Yes |
