# Lazy

> The `Lazy[A]` data type represents a deferred computation that produces a value of type `A`. Unlike Scala's built-in `lazy val`, ZIO Blocks' `Lazy` provides a powerful, monadic abstraction with explicit error handling, memoization, and stack-safe evaluation through trampolining:

The `Lazy[A]` data type represents a deferred computation that produces a value of type `A`. Unlike Scala's built-in `lazy val`, ZIO Blocks' `Lazy` provides a powerful, monadic abstraction with explicit error handling, memoization, and stack-safe evaluation through trampolining:

```scala
sealed trait Lazy[+A] {
  def force: A
  def isEvaluated: Boolean
  def map[B](f: A => B): Lazy[B]
  def flatMap[B](f: A => Lazy[B]): Lazy[B]
  // ... more operations
}

object Lazy {
  def apply[A](expression: => A): Lazy[A]
  def fail(throwable: Throwable): Lazy[Nothing]
  // ... more constructors
}
```

Once a `Lazy` computation is evaluated, the result is cached and `isEvaluated` becomes `true`. Subsequent calls to `force` return the cached value without re-executing the computation, as long as the computed result is not `null`.

Note: the current implementation uses `null` internally as the sentinel for “not evaluated”. If a `Lazy` computation legitimately returns `null`, it will be recomputed on every `force` call and `isEvaluated` will remain `false`. To benefit from memoization, prefer non-null results (for example, use `Option[A]` instead of returning `null`).
The `force` method uses trampolining (an explicit stack) to evaluate deeply nested `Lazy` computations without risking stack overflow.

## Why Lazy Exists?

During type-class derivation, instances for nested types must be created before they are used. `Lazy` allows the derivation machinery to build a structure of deferred computations, resolving them only when the final instance is forced.

When deriving a type-class instance for a complex type, the derivation machinery needs to:

1. **Traverse the schema tree**: Visit each node (records, variants, sequences, etc.)
2. **Create instances for nested types**: Before creating an instance for a parent type, instances for child types must exist
3. **Handle recursion**: For recursive types, the instance for the recursive reference must be deferred

Here's a simplified view of how `Lazy` enables this:

```scala
// In DerivationBuilder
def transformRecord[A](
  fields: IndexedSeq[Term[F, A, ?]],
  metadata: F[BindingType.Record, A],
  // ...
): Lazy[Reflect.Record[G, A]] = Lazy {
  // Get instances for field types (may trigger evaluation of other Lazy values)
  val fieldInstances = fields.map { field =>
    D.instance(field.value.metadata)  // Returns Lazy[TC[FieldType]]
  }

  // Create the record instance
  val instance = deriver.deriveRecord(fields, /* ... */)

  new Reflect.Record(fields, ..., new BindingInstance(metadata, instance), ...)
}
```

The `BindingInstance` class wraps both a `Binding` and a `Lazy[TC[A]]`:

```scala
final case class BindingInstance[TC[_], T, A](
  binding: Binding[T, A],
  instance: Lazy[TC[A]]
)
```

This allows the derivation to build a complete tree of `Lazy` instances, which are only forced when the final type-class instance is needed.

## Creating Lazy Values

### Basic Construction

Create a `Lazy` value by passing a by-name expression to `Lazy.apply`:

```scala

// The expression is NOT evaluated here
val lazyInt: Lazy[Int] = Lazy {
  println("Computing...")
  42
}

println(lazyInt.isEvaluated)  // false

// Now the expression is evaluated
val result = lazyInt.force    // prints "Computing..."
println(result)               // 42
println(lazyInt.isEvaluated)  // true

// Subsequent calls return the cached value
val result2 = lazyInt.force   // does NOT print "Computing..."
println(result2)              // 42
```

### Creating Failed Lazy Values

Use `Lazy.fail` to create a `Lazy` that will throw an exception when forced:

```scala
val failingLazy: Lazy[Int] = 
  Lazy.fail(new RuntimeException("Something went wrong"))
```

## Transforming Lazy Values

`Lazy` is a monad, supporting `map`, `flatMap`, and other familiar operations.

### map

Transform the value inside a `Lazy` without forcing it:

```scala
val lazyInt: Lazy[Int] = Lazy(42)
val lazyString: Lazy[String] = lazyInt.map(_.toString)

println(lazyString.force)       // "42"
```

### flatMap

Chain `Lazy` computations together:

```scala
val lazyA: Lazy[Int] = Lazy(10)
val lazyB: Lazy[Int] = Lazy(20)

val lazySum: Lazy[Int] = lazyA.flatMap(a => lazyB.map(b => a + b))

println(lazySum.force)  // 30
```

Using for-comprehension syntax:

```scala
val result: Lazy[String] = for {
  x <- Lazy(10)
  y <- Lazy(20)
  z <- Lazy(30)
} yield s"Sum: ${x + y + z}"

println(result.force)  // "Sum: 60"
```

### as

Replace the value with a constant, discarding the original:

```scala
val lazy42: Lazy[Int] = Lazy(42)
val lazyHello: Lazy[String] = lazy42.as("Hello")

println(lazyHello.force)  // "Hello"
```

### unit

Discard the value, keeping only the side effects:

```scala
val lazyWithSideEffect: Lazy[Int] = Lazy {
  println("Side effect!")
  42
}

val lazyUnit: Lazy[Unit] = lazyWithSideEffect.unit
lazyUnit.force  // prints "Side effect!", returns ()
```

### flatten
 
Flatten a nested `Lazy[Lazy[A]]` into `Lazy[A]`:

```scala
val nested: Lazy[Lazy[Int]] = Lazy(Lazy(42))
val flat: Lazy[Int] = nested.flatten

println(flat.force)  // 42
```

### zip

Combine two `Lazy` values into a tuple:

```scala
val lazyA: Lazy[Int] = Lazy(1)
val lazyB: Lazy[String] = Lazy("hello")

val lazyPair: Lazy[(Int, String)] = lazyA.zip(lazyB)

println(lazyPair.force)  // (1, "hello")
```

## Error Handling

### catchAll

Recover from errors by providing an alternative `Lazy`:

```scala
val failing: Lazy[Int] = Lazy(throw new RuntimeException("oops"))
val recovered: Lazy[Int] = failing.catchAll(_ => Lazy(0))

println(recovered.force)  // 0
```

The error handler receives the thrown exception:

```scala
val failing: Lazy[Int] = Lazy(throw new RuntimeException("specific error"))
val handled: Lazy[Int] = failing.catchAll { error =>
  println(s"Caught: ${error.getMessage}")
  Lazy(-1)
}

println(handled.force)  // prints "Caught: specific error", returns -1
```

### ensuring

Run a finalizer regardless of success or failure:

```scala
var resourceClosed = false

val computation: Lazy[Int] = Lazy {
  42
}.ensuring(Lazy {
  resourceClosed = true
})

println(computation.force)  // 42
println(resourceClosed)     // true
```

The finalizer runs even when the main computation fails:

```scala
var finalizerRan = false

val failing: Lazy[Int] = Lazy[Int] {
  throw new RuntimeException("error")
}.ensuring(Lazy {
  finalizerRan = true
})

try {
  failing.force
} catch {
  case _: RuntimeException => ()
}

println(finalizerRan)  // true
```

## Working with Collections

### collectAll

Convert an `IndexedSeq[Lazy[A]]` into a `Lazy[IndexedSeq[A]]`:

```scala
val lazies: IndexedSeq[Lazy[Int]] = IndexedSeq(Lazy(1), Lazy(2), Lazy(3))
val collected: Lazy[IndexedSeq[Int]] = Lazy.collectAll(lazies)

println(collected.force)  // IndexedSeq(1, 2, 3)
```

### foreach

Apply a function that returns `Lazy` to each element of a collection:

```scala
val numbers: IndexedSeq[Int] = IndexedSeq(1, 2, 3)
val doubled: Lazy[IndexedSeq[Int]] = Lazy.foreach(numbers)(n => Lazy(n * 2))

println(doubled.force)  // IndexedSeq(2, 4, 6)
```

## How `force` Works: A Deep Dive

The `force` method is the heart of the `Lazy` data type. It evaluates the deferred computation and returns the result. Understanding how it works is essential for understanding `Lazy`.

### Internal Structure

`Lazy` has three internal components:

1. **`Defer[A](thunk: () => A)`**: Represents a deferred computation. The `thunk` is a function that, when called, produces the value.

2. **`FlatMap[A, B](first: Lazy[A], cont: Cont[A, B])`**: Represents a chained computation where `first` must be evaluated, then its result is passed to the continuation `cont`.

3. **`Cont[A, B](ifSuccess: A => Lazy[B], ifError: Throwable => Lazy[B])`**: A continuation that handles both success and error cases.

Additionally, each `Lazy` instance has mutable fields for memoization:
- `value: Any` - Stores the cached successful result
- `error: Throwable` - Stores the cached exception

### Why Trampolining?

Without trampolining, deeply nested `flatMap` chains would overflow the stack:

```scala
// This would overflow without trampolining
var lazy: Lazy[Int] = Lazy(0)
for (_ <- 1 to 100000) {
  lazy = lazy.flatMap(n => Lazy(n + 1))
}
lazy.force  // Works! Returns 100000
```

The trampolining approach converts recursive calls into an iterative loop with an explicit stack (`List[Cont[Any, Any]]`), consuming constant stack space regardless of nesting depth.

## Comparison with Scala's `lazy val`

| Feature            | `Lazy[A]`                    | Scala `lazy val`             |
|--------------------|------------------------------|------------------------------|
| Monadic operations | Yes (`map`, `flatMap`)       | No                           |
| Error handling     | Yes (`catchAll`, `ensuring`) | No                           |
| Stack safety       | Yes (trampolined `flatMap`)  | N/A (no monadic chaining)    |
| Composable         | Yes                          | Limited                      |

## API Reference

### Constructors

| Method                    | Description                                                         |
|---------------------------|---------------------------------------------------------------------|
| `Lazy(expr: => A)`        | Create a `Lazy` from a by-name expression                          |
| `Lazy.fail(e: Throwable)` | Create a `Lazy` that fails with the given exception                |
| `Lazy.collectAll(values)` | Combine an `IndexedSeq` of `Lazy` into a single `Lazy`             |
| `Lazy.foreach(values)(f)` | Apply a function to each element of an `IndexedSeq`, collecting results |

Note: For other collection types, convert them using `.toIndexedSeq` before calling `Lazy.collectAll` or `Lazy.foreach`.
### Instance Methods

| Method                                       | Description                               |
|----------------------------------------------|-------------------------------------------|
| `force: A`                                          | Evaluate and return the result (memoized) |
| `isEvaluated: Boolean`                              | Check if the value has been computed      |
| `map[B](f: A => B): Lazy[B]`                        | Transform the value                       |
| `flatMap[B](f: A => Lazy[B]): Lazy[B]`              | Chain computations                        |
| `flatten: Lazy[B]`                                  | Flatten nested `Lazy`                     |
| `as[B](b: => B): Lazy[B]`                           | Replace the value with a constant         |
| `unit: Lazy[Unit]`                                  | Discard the value                         |
| `zip[B](that: Lazy[B]): Lazy[(A, B)]`               | Combine with another `Lazy`               |
| `catchAll[B >: A](f: Throwable => Lazy[B]): Lazy[B]` | Handle errors                            |
| `ensuring(finalizer: Lazy[Any]): Lazy[A]`           | Run finalizer on completion               |

### Equality and Hashing

`Lazy` values are compared by forcing both values and comparing the results:

```scala
Lazy(42) == Lazy(42)  // true (forces both)
Lazy(42).hashCode == Lazy(42).hashCode  // true
```

Note: Comparing `Lazy` values will force their evaluation. Also, `hashCode` forces the value and then calls `.hashCode` on it; if a `Lazy` evaluates to `null`, `hashCode` will throw a `NullPointerException`, and repeated equality checks may re-run the thunk because `null` is used internally as the “not yet evaluated” sentinel.
