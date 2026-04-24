# BindingResolver

> `BindingResolver` is the **read-only interface for looking up bindings by type identity** during schema rebinding. Given a type `A`, a resolver searches its internal storage and returns either the matching `Binding` or `None`.

`BindingResolver` is the **read-only interface for looking up bindings by type identity** during schema rebinding. Given a type `A`, a resolver searches its internal storage and returns either the matching `Binding` or `None`.

```scala
trait BindingResolver {
  def resolveRecord[A](implicit typeId: TypeId[A]): Option[Binding.Record[A]]
  def resolveVariant[A](implicit typeId: TypeId[A]): Option[Binding.Variant[A]]
  def resolvePrimitive[A](implicit typeId: TypeId[A]): Option[Binding.Primitive[A]]
  def resolveWrapper[A](implicit typeId: TypeId[A]): Option[Binding.Wrapper[A, _]]
  def resolveDynamic(implicit typeId: TypeId[DynamicValue]): Option[Binding.Dynamic]
  def resolveSeq[X](implicit typeId: TypeId[X], u: UnapplySeq[X]): Option[Binding.Seq[u.C, u.A]]
  def resolveSeqFor[C[_], A](typeId: TypeId[C[A]]): Option[Binding.Seq[C, A]]
  def resolveMap[X](implicit typeId: TypeId[X], u: UnapplyMap[X]): Option[Binding.Map[u.M, u.K, u.V]]
  def resolveMapFor[M[_, _], K, V](typeId: TypeId[M[K, V]]): Option[Binding.Map[M, K, V]]
  final def ++(that: BindingResolver): BindingResolver
}
```

`BindingResolver`:

- Uses `TypeId` as the lookup key for all resolution methods.
- Stores sequence and map bindings by their *unapplied type constructor*, so one `List` binding covers `List[Int]`, `List[String]`, and any other element type.
- Composes via `BindingResolver#++` with left-biased precedence: the left resolver is tried first, the right serves as fallback.

## Motivation

ZIO Blocks separates schema **structure** from schema **behavior**:

- A `Reflect.Unbound[A]` carries only structural metadata—field names, type names, documentation. It contains no Scala functions and is fully serializable.
- A `Reflect.Bound[A]` pairs each structural node with its `Binding`, enabling actual construction and deconstruction of values.

This separation powers a key workflow: serialize a schema as a `DynamicSchema` (unbound), transmit or store it, then **rebind** it on the other side using a `BindingResolver` to recover a fully operational `Schema[A]`.

```
  Schema[Person]
       │  toDynamicSchema
       ▼
  DynamicSchema          ◄── serializable, no Scala functions
  (Reflect.Unbound[_])
       │  rebind[Person](resolver)
       ▼
  Schema[Person]         ◄── operational, can encode and decode
  (Reflect.Bound[Person])
```

The `BindingResolver` is what bridges the final step: it supplies the `Binding` for each node in the unbound reflect tree.

A minimal end-to-end example:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}

val dynamic: DynamicSchema = Schema[Person].toDynamicSchema

val resolver: BindingResolver =
  BindingResolver.empty.bind(Binding.of[Person]) ++ BindingResolver.defaults

val rebound: Schema[Person] = dynamic.rebind[Person](resolver)
```

## Predefined Resolvers

ZIO Blocks ships three ready-made resolvers. We almost always compose them with `BindingResolver#++` rather than using them in isolation.

### `BindingResolver.empty`

`BindingResolver.empty` is an empty `Registry` with no bindings. It is the starting point for building a custom registry with `Registry#bind`:

```scala

val empty: BindingResolver.Registry = BindingResolver.empty
```

### `BindingResolver.defaults`

`BindingResolver.defaults` is a pre-populated `Registry` covering all primitive types, `java.time` types, `java.util.UUID`, `java.util.Currency`, `DynamicValue`, common sequence types (`List`, `Vector`, `Set`, `IndexedSeq`, `Seq`, `Chunk`), and `Map`. In practice we place it at the right end of a `BindingResolver#++` chain so custom bindings can override it when needed.

The types covered by `defaults` include:

| Category    | Types                                                                                                                                                                                                       |
|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Primitives  | `Unit`, `Boolean`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `Char`, `String`, `BigInt`, `BigDecimal`                                                                                             |
| `java.time` | `DayOfWeek`, `Duration`, `Instant`, `LocalDate`, `LocalDateTime`, `LocalTime`, `Month`, `MonthDay`, `OffsetDateTime`, `OffsetTime`, `Period`, `Year`, `YearMonth`, `ZoneId`, `ZoneOffset`, `ZonedDateTime` |
| `java.util` | `Currency`, `UUID`                                                                                                                                                                                          |
| Dynamic     | `DynamicValue`                                                                                                                                                                                              |
| Sequences   | `List`, `Vector`, `Set`, `IndexedSeq`, `Seq`, `Chunk`                                                                                                                                                      |
| Maps        | `Map`                                                                                                                                                                                                       |

```scala

val defaults: BindingResolver.Registry = BindingResolver.defaults

defaults.resolvePrimitive[Int]               // Some(...)
defaults.resolvePrimitive[java.time.Instant] // Some(...)
defaults.resolveSeq[List[Int]]               // Some(...)
defaults.resolveMap[Map[String, Int]]        // Some(...)
```

### `BindingResolver.reflection` (JVM only)

`BindingResolver.reflection` derives `Binding.Record` instances at runtime using Java reflection for case classes. Derived bindings are cached per `TypeId` in a `ConcurrentHashMap`, so the reflection cost is paid only once per type.

On Scala.js, `BindingResolver.reflection` is a no-op resolver that returns `None` for every query.

```scala

case class Order(id: Long, item: String, quantity: Int)

object Order {
  implicit val schema: Schema[Order] = Schema.derived[Order]
}

// reflection handles the record; defaults handles Long, String, Int
val resolver: BindingResolver = BindingResolver.reflection ++ BindingResolver.defaults

val rebound: Schema[Order] = Schema[Order].toDynamicSchema.rebind[Order](resolver)
```

:::warning
`BindingResolver.reflection` only derives `Binding.Record` for case classes. It returns `None` for primitives, variants, wrappers, sequences, and maps. Always compose it with `BindingResolver.defaults` to cover those types.
:::

## Building a Registry

`BindingResolver.Registry` is an immutable, map-backed resolver. Every `Registry#bind` call returns a **new** `Registry` with the binding added; the original registry is unchanged.

### `Registry#bind` for proper types

The unified `Registry#bind` method accepts any proper binding—`Record`, `Variant`, `Primitive`, `Wrapper`, or `Dynamic`—and dispatches to the correct internal storage slot automatically. We typically call `Binding.of[A]` to derive the right binding at compile time:

```scala

sealed trait Color
case object Red  extends Color
case object Blue extends Color

object Color {
  implicit val schema: Schema[Color] = Schema.derived[Color]
}

case class Palette(primary: Color, name: String)

object Palette {
  implicit val schema: Schema[Palette] = Schema.derived[Palette]
}

val registry: BindingResolver.Registry =
  BindingResolver.empty
    .bind(Binding.of[Color])    // derives Binding.Variant[Color]
    .bind(Binding.of[Palette])  // derives Binding.Record[Palette]
```

`Binding.of[A]` is a compile-time macro that selects the appropriate binding kind based on the type of `A`:

- Case class → `Binding.Record`
- Sealed trait or enum → `Binding.Variant`
- Scalar type (`Int`, `String`, …) → `Binding.Primitive`
- Single-field wrapper with a smart constructor → `Binding.Wrapper`

:::warning
Passing a `Binding.Seq` or `Binding.Map` to the unified `Registry#bind` method throws `IllegalArgumentException` at runtime. Use the specialized overloads shown below for collection types.
:::

### `Registry#bind` for sequence types

Sequence bindings are keyed by their unapplied type constructor. We supply `[C[_]]` as the explicit type parameter so the compiler uses the constructor—not a specific applied type—as the lookup key:

```scala

val registry: BindingResolver.Registry =
  BindingResolver.empty.bind[List](Binding.Seq.list[Nothing])

// One binding resolves any element type
registry.resolveSeq[List[Int]]    // Some(...)
registry.resolveSeq[List[String]] // Some(...)
```

### `Registry#bind` for map types

Map bindings follow the same pattern with `[M[_, _]]` as the type parameter:

```scala

val registry: BindingResolver.Registry =
  BindingResolver.empty.bind[Map](Binding.Map.map[Nothing, Nothing])

registry.resolveMap[Map[String, Int]] // Some(...)
registry.resolveMap[Map[Int, String]] // Some(...)
```

## Combining Resolvers

The `BindingResolver#++` operator composes two resolvers into a left-biased fallback chain. The left resolver is consulted first; the right is used only when the left returns `None`.

```scala
trait BindingResolver {
  final def ++(that: BindingResolver): BindingResolver
}
```

This makes it straightforward to layer custom bindings over the built-in defaults:

```scala

case class UserId(value: Long)

object UserId {
  implicit val schema: Schema[UserId] =
    Schema[Long].transform(UserId(_), _.value)
}

// Custom bindings shadow matching entries in defaults
val resolver: BindingResolver =
  BindingResolver.empty.bind(Binding.of[UserId]) ++ BindingResolver.defaults
```

`BindingResolver#++` is associative for resolution outcomes: `(a ++ b) ++ c` and `a ++ (b ++ c)` always resolve to the same binding for any type.

## Resolution Methods

All `resolve*` methods return an `Option` and never throw. They return `None` when no binding is registered for the requested type.

### `BindingResolver#resolveRecord`

`BindingResolver#resolveRecord` returns the `Binding.Record` for a product type (case class, tuple, module object):

```scala
trait BindingResolver {
  def resolveRecord[A](implicit typeId: TypeId[A]): Option[Binding.Record[A]]
}
```

The `TypeId[A]` witness is satisfied implicitly; we only need to supply the type parameter:

```scala

case class Point(x: Double, y: Double)

object Point {
  implicit val schema: Schema[Point] = Schema.derived[Point]
}

val registry = BindingResolver.empty.bind(Binding.of[Point])
val binding: Option[Binding.Record[Point]] = registry.resolveRecord[Point]
```

### `BindingResolver#resolveVariant`

`BindingResolver#resolveVariant` returns the `Binding.Variant` for a sum type (sealed trait, Scala 3 enum):

```scala
trait BindingResolver {
  def resolveVariant[A](implicit typeId: TypeId[A]): Option[Binding.Variant[A]]
}
```

```scala

sealed trait Shape
case class Circle(radius: Double)          extends Shape
case class Rectangle(w: Double, h: Double) extends Shape

object Shape {
  implicit val schema: Schema[Shape] = Schema.derived[Shape]
}

val registry = BindingResolver.empty.bind(Binding.of[Shape])
val binding: Option[Binding.Variant[Shape]] = registry.resolveVariant[Shape]
```

### `BindingResolver#resolvePrimitive`

`BindingResolver#resolvePrimitive` returns the `Binding.Primitive` for scalar types such as `Int`, `String`, `java.time.Instant`, and `java.util.UUID`:

```scala
trait BindingResolver {
  def resolvePrimitive[A](implicit typeId: TypeId[A]): Option[Binding.Primitive[A]]
}
```

```scala

val binding: Option[Binding.Primitive[Int]] = BindingResolver.defaults.resolvePrimitive[Int]
```

### `BindingResolver#resolveWrapper`

`BindingResolver#resolveWrapper` returns the `Binding.Wrapper` for newtype patterns—single-field case classes and smart-constructor wrappers. The binding holds a `wrap: B => A` and an `unwrap: A => B` function, converting between the inner type `B` and the outer type `A`:

```scala
trait BindingResolver {
  def resolveWrapper[A](implicit typeId: TypeId[A]): Option[Binding.Wrapper[A, _]]
}
```

```scala

case class Email(value: String)

object Email {
  implicit val schema: Schema[Email] =
    Schema[String].transform(Email(_), _.value)
}

val registry = BindingResolver.empty.bind(Binding.of[Email])
val binding: Option[Binding.Wrapper[Email, _]] = registry.resolveWrapper[Email]
```

### `BindingResolver#resolveDynamic`

`BindingResolver#resolveDynamic` returns the `Binding.Dynamic` singleton, used for `DynamicValue` nodes in the reflect tree. `BindingResolver.defaults` already includes it, so manual registration is rarely needed:

```scala
trait BindingResolver {
  def resolveDynamic(implicit typeId: TypeId[DynamicValue]): Option[Binding.Dynamic]
}
```

```scala

val binding: Option[Binding.Dynamic] = BindingResolver.defaults.resolveDynamic
```

### `BindingResolver#resolveSeq` and `BindingResolver#resolveSeqFor`

`BindingResolver#resolveSeq` uses `UnapplySeq` evidence to decompose an applied type like `List[Int]` into its constructor `List` and element type `Int`, then looks up the binding by constructor. `BindingResolver#resolveSeqFor` is the explicit variant when the constructor and element types are already known as separate parameters:

```scala
trait BindingResolver {
  def resolveSeq[X](implicit typeId: TypeId[X], u: UnapplySeq[X]): Option[Binding.Seq[u.C, u.A]]
  def resolveSeqFor[C[_], A](typeId: TypeId[C[A]]): Option[Binding.Seq[C, A]]
}
```

Because the binding is stored by type constructor, a single registered `List` binding handles any element type:

```scala

val defaults = BindingResolver.defaults

val listIntBinding: Option[Binding.Seq[List, Int]]    = defaults.resolveSeq[List[Int]]
val listStrBinding: Option[Binding.Seq[List, String]] = defaults.resolveSeq[List[String]]

// Explicit form using resolveSeqFor
val explicit: Option[Binding.Seq[List, Int]] =
  defaults.resolveSeqFor[List, Int](TypeId.of[List[Int]])
```

### `BindingResolver#resolveMap` and `BindingResolver#resolveMapFor`

`BindingResolver#resolveMap` and `BindingResolver#resolveMapFor` follow the same pattern as their sequence counterparts, applied to key-value collection types:

```scala
trait BindingResolver {
  def resolveMap[X](implicit typeId: TypeId[X], u: UnapplyMap[X]): Option[Binding.Map[u.M, u.K, u.V]]
  def resolveMapFor[M[_, _], K, V](typeId: TypeId[M[K, V]]): Option[Binding.Map[M, K, V]]
}
```

```scala

val binding: Option[Binding.Map[Map, String, Int]] =
  BindingResolver.defaults.resolveMap[Map[String, Int]]
```

## Registry Inspection

`Registry` exposes several methods to interrogate its state without performing a full resolution. `Registry#contains` checks whether a proper binding (Record, Variant, Primitive, Wrapper, or Dynamic) is registered for type `A`. `Registry#containsSeq` and `Registry#containsMap` check for sequence and map type constructors respectively:

```scala
final class Registry {
  def contains[A](implicit typeId: TypeId[A]): Boolean
  def containsSeq[X](implicit typeId: TypeId[X], u: UnapplySeq[X]): Boolean
  def containsMap[X](implicit typeId: TypeId[X], u: UnapplyMap[X]): Boolean
  def size: Int
  def isEmpty: Boolean
  def nonEmpty: Boolean
}
```

```scala

val registry = BindingResolver.defaults

registry.contains[Int]              // true — primitive binding exists
registry.containsSeq[List[String]]  // true — List constructor is bound
registry.containsMap[Map[Int, Int]] // true — Map constructor is bound
registry.size                       // total number of registered bindings
```

## Integration with `DynamicSchema`

The primary consumer of `BindingResolver` is `DynamicSchema#rebind`. Given an unbound `DynamicSchema`, `DynamicSchema#rebind` walks the `Reflect` tree and queries the resolver for each node's binding, then returns a fully operational `Schema[A]`:

```scala

case class Product(sku: String, price: Double, tags: List[String])

object Product {
  implicit val schema: Schema[Product] = Schema.derived[Product]
}

val dynamic: DynamicSchema = Schema[Product].toDynamicSchema

// The resolver must cover every concrete type that appears in the schema tree
val resolver: BindingResolver =
  BindingResolver.empty.bind(Binding.of[Product]) ++ BindingResolver.defaults

val rebound: Schema[Product] = dynamic.rebind[Product](resolver)
```

When using `BindingResolver.reflection` on the JVM, individual record types do not need to be registered explicitly—the resolver derives their bindings on demand from the class structure:

```scala

case class Address(street: String, city: String, zip: String)

object Address {
  implicit val schema: Schema[Address] = Schema.derived[Address]
}

// No explicit bind(Binding.of[Address]) needed on JVM
val resolver: BindingResolver = BindingResolver.reflection ++ BindingResolver.defaults

val rebound: Schema[Address] = Schema[Address].toDynamicSchema.rebind[Address](resolver)
```

:::warning
If `DynamicSchema#rebind` cannot find a binding for any type present in the unbound schema tree, it throws at runtime. Make sure the resolver covers every concrete type—records, variants, wrappers, primitives, and collections—that appears in the schema.
:::

See [Binding](./binding.md) for details on each binding kind, and [Schema](./schema.md) for the overall structure of the schema system.
