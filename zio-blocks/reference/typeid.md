# TypeId

> `TypeId[A]` represents the identity of a type or type constructor at runtime — it captures complete type metadata (names, type parameters, parent types, annotations, classification) that would otherwise be erased by the JVM and Scala.js. Use `TypeId` when you need to preserve full type information as data for serialization, code generation, registry lookups, or type-safe dispatching.

`TypeId[A]` represents the identity of a type or type constructor at runtime — it captures complete type metadata (names, type parameters, parent types, annotations, classification) that would otherwise be erased by the JVM and Scala.js. Use `TypeId` when you need to preserve full type information as data for serialization, code generation, registry lookups, or type-safe dispatching.

In Scala and the JVM, compile-time type information is erased at runtime. This means generic type parameters, sealed trait variants, and even opaque types become indistinguishable at runtime — `List[Int]` and `List[String]` both look like `List` to the JVM. This erasure makes it nearly impossible to implement universal serializers that work across formats (JSON, YAML, XML, MessagePack), code generators, or schema-driven transformations without losing semantic information. `TypeId` solves this by capturing complete type structure at compile time and making it available as a hashable, inspectable value at runtime.

The `TypeId` trait exposes the type's structure through a rich set of properties and predicates:

```scala
// Simplified — some members shown here are derived from abstract members
sealed trait TypeId[A <: AnyKind] {
  // Abstract members
  def name: String
  def owner: Owner
  def typeParams: List[TypeParam]
  def typeArgs: List[TypeRepr]
  def defKind: TypeDefKind
  def selfType: Option[TypeRepr]       // Self-type annotation, if any
  def aliasedTo: Option[TypeRepr]      // Target type for type aliases
  def representation: Option[TypeRepr] // Underlying type for opaque types
  def annotations: List[Annotation]

  // Derived properties
  final def fullName: String     // owner.asString + "." + name
  final def arity: Int           // typeParams.size
  final def isCaseClass: Boolean
  final def isSealed: Boolean
  final def isAlias: Boolean
  // ... many more derived predicates
}
```

> In Scala 3, `A` is bounded by `AnyKind` to support higher-kinded types. In Scala 2, the bound is omitted (`sealed trait TypeId[A]`).

Derive a `TypeId` for any type using the `TypeId.of` macro and then inspect the type's structure at runtime:

```scala

case class Person(name: String, age: Int)

val id = TypeId.of[Person]
```

```scala
id.name
// res1: String = "Person"
id.fullName
// res2: String = "repl.MdocSession.MdocApp0.Person"
id.isCaseClass
// res3: Boolean = true
```

## Motivation

Standard approaches to preserving type information at runtime — `ClassTag`, `TypeTag` (Scala 2), `TypeTest` (Scala 3) — each have limitations. `ClassTag` loses generic type arguments. `TypeTag` depends on `scala-reflect` and is unavailable on Scala.js. `TypeTest` only answers "is this value an instance of T?" without exposing type structure. None of them distinguish opaque types from their underlying representation.

TypeId takes a different approach: the `TypeId.of` macro captures type metadata at compile time and stores it as a plain, immutable data structure — no runtime reflection, no platform-specific APIs. This makes it suitable as a foundation for cross-platform schema systems, code generators, and type-indexed registries.

## Installation

TypeId is included in the `zio-blocks-typeid` module. Add it to your build:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-typeid" % "@VERSION"
```

For cross-platform (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-typeid" % "@VERSION"
```

Supported Scala versions: 2.13.x and 3.x.

## Creating Instances

There are two approaches to creating `TypeId` values: **automatic derivation** (recommended for normal use) and **manual construction** (for advanced metaprogramming scenarios).

### Automatic Derivation

For most users and most types, automatic derivation via `TypeId.of` or implicit `derived` is the right choice. These macros extract complete type metadata at compile time, handling all type variants correctly.

#### `TypeId.of` — Macro Derivation

The primary way to obtain a `TypeId` is through the `TypeId.of[A]` macro, which extracts complete type metadata at compile time:

```scala
object TypeId {
  inline def of[A <: AnyKind]: TypeId[A]  // Scala 3
  def of[A]: TypeId[A]                     // Scala 2 (macro)
}
```

Derive a TypeId using the macro:

```scala

case class User(id: Long, email: String)
```

```scala
val userId = TypeId.of[User]
// userId: TypeId[User] = User
userId.name
// res5: String = "User"
userId.fullName
// res6: String = "repl.MdocSession.MdocApp4.User"
userId.isCaseClass
// res7: Boolean = true
```

#### `TypeId.derived` — Implicit Derivation

TypeId instances are available implicitly through the `derived` macro. Any function that requires a `TypeId[A]` in implicit scope will have it derived automatically — you never need to pass it manually.

From the user's perspective, the API is:

```scala
object TypeId {
  inline given derived[A <: AnyKind]: TypeId[A]  // Scala 3
  implicit def derived[A]: TypeId[A]              // Scala 2 (macro)
}
```

:::note
In Scala 3, the `[A <: AnyKind]` bound allows derivation for type constructors (e.g., `TypeId[List]`). In Scala 2, the bound is `[A]` and type constructor derivation uses `TypeId[List[_]]` syntax instead.
:::

The most common use case is accepting `TypeId[A]` as an implicit parameter:

```scala

case class User(id: Long, email: String)

def describe[A](implicit typeId: TypeId[A]): String =
  s"${typeId.fullName} is a case class: ${typeId.isCaseClass}"
```

Call the function with the type argument — the TypeId is derived automatically:

```scala
describe[User]
// res9: String = "repl.MdocSession.MdocApp8.User is a case class: true"
describe[Int]
// res10: String = "scala.Int is a case class: false"
```

You can also summon a TypeId explicitly with `implicitly` (Scala 2) or `summon` (Scala 3):

```scala
val userTypeId = implicitly[TypeId[User]]
// userTypeId: TypeId[User] = User
userTypeId.name
// res11: String = "User"
```

When you need the TypeId in a single expression, use `TypeId.of[A]`. For generic functions that accept any `A` and need its TypeId alongside other implicit evidence, use implicit derivation instead.

### Manual Derivation (Smart Constructors)

For advanced use cases — unit testing with synthetic metadata, code generators that create types dynamically, or frameworks that construct TypeIds at runtime — the smart constructor functions allow you to manually assemble TypeIds by specifying their components. These are never needed in normal user code, since `TypeId.of` handles all these cases automatically.

#### `TypeId.nominal` — Nominal Types

**Nominal types** are concrete type definitions: classes, traits, and objects. In contrast to [type aliases](#typeidalias--type-aliases) (which are alternative names for existing types) and [opaque types](#typeidopaque--opaque-types) (which have a hidden representation), nominal types stand as distinct, named types in the type system.

For most end users, you don't need to use `TypeId.nominal` directly. The `TypeId.of` macro automatically derives nominal TypeIds from your actual type definitions at compile time. The `nominal` smart constructor exists for **advanced use cases**: unit testing with synthetic type metadata, code generators that create types dynamically at runtime, or frameworks that assemble TypeIds programmatically. Unless you're in one of these scenarios, `TypeId.of` is the right tool.

If you do need to construct nominal TypeIds manually, the API provides two overloads:

```scala
object TypeId {
  def nominal[A <: AnyKind](name: String, owner: Owner, kind: TypeDefKind): TypeId[A]

  def nominal[A <: AnyKind](
    name: String, owner: Owner,
    typeParams: List[TypeParam] = Nil, typeArgs: List[TypeRepr] = Nil,
    defKind: TypeDefKind = TypeDefKind.Unknown,
    selfType: Option[TypeRepr] = None,
    annotations: List[Annotation] = Nil
  ): TypeId[A]
}
```

#### `TypeId.alias` — Type Aliases

**Type aliases** are alternative names for existing types. For example, `type Age = Int` creates an alias for `Int` so code can read `Age` instead of `Int`. TypeIds for type aliases preserve the distinction from their underlying type through the `aliasedTo` property, enabling alias-aware serialization and schema generation.

For normal use, you don't need `TypeId.alias` directly. When you write a type alias in your code (e.g., `type UserId = String`), the `TypeId.of` macro automatically derives the correct TypeId. The `alias` smart constructor is for **advanced use cases**: unit testing with synthetic alias metadata, code generators that create type aliases dynamically at runtime, or frameworks that normalize or transform type aliases during schema processing. Unless you're building one of these, `TypeId.of` is the right tool.

For testing or code generation, construct an alias TypeId:

```scala
object TypeId {
  def alias[A <: AnyKind](
    name: String, owner: Owner,
    typeParams: List[TypeParam] = Nil,
    aliased: TypeRepr,
    typeArgs: List[TypeRepr] = Nil,
    annotations: List[Annotation] = Nil
  ): TypeId[A]
}
```

```scala

```

```scala
val ageId = TypeId.alias[Any]("Age", Owner.fromPackagePath("com.example"), aliased = TypeRepr.Ref(TypeId.int))
// ageId: TypeId[Any] = Age
ageId.isAlias
// res13: Boolean = true
ageId.aliasedTo
// res14: Option[TypeRepr] = Some(Ref(Int))
```

#### `TypeId.opaque` — Opaque Types

**Opaque types** (a Scala 3 feature) are types that have a distinct compile-time identity but a hidden runtime representation. For example, `opaque type UserId = String` creates a type that is distinct from `String` at compile time, but represents `String` at runtime. TypeId preserves this distinction, unlike standard reflection which erases opaque types to their underlying type — a critical capability for type-safe serialization and validation.

For normal use, you don't need `TypeId.opaque` directly. When you define an opaque type in your code, the `TypeId.of` macro automatically derives the correct TypeId with its representation. The `opaque` smart constructor is for **advanced use cases**: unit testing with synthetic opaque type metadata, code generators that create opaque types dynamically, or frameworks that need to construct type metadata for dynamically-discovered opaque types. Unless you're building one of these, `TypeId.of` is the right tool.

For testing or code generation, construct an opaque TypeId:

```scala
object TypeId {
  def opaque[A <: AnyKind](
    name: String, owner: Owner,
    typeParams: List[TypeParam] = Nil,
    representation: TypeRepr,
    typeArgs: List[TypeRepr] = Nil,
    publicBounds: TypeBounds = TypeBounds.Unbounded,
    annotations: List[Annotation] = Nil
  ): TypeId[A]
}
```

#### `TypeId.applied` — Applied Types

**Applied types** are generic types instantiated with type arguments. For example, `List[Int]` is `List` (the type constructor) applied to `Int` (the type argument), and `Map[String, Int]` is `Map` applied to two type arguments. TypeIds for applied types preserve the type arguments so serializers can generate specialized codecs, validators can type-check values, and code generators can emit correct code.

For normal use, you don't need `TypeId.applied` directly. When you write an applied type in your code (e.g., `List[Int]` or `Map[String, User]`), the `TypeId.of` macro automatically derives the correct TypeId with its type arguments preserved. The `applied` smart constructor is for **advanced use cases**: unit testing with synthetic applied type metadata, code generators that construct type expressions dynamically, or frameworks that need to build type metadata at runtime for dynamically-discovered generic types. Unless you're building one of these, `TypeId.of` is the right tool.

For testing or code generation, construct applied TypeIds by combining a type constructor with type argument expressions:

```scala
object TypeId {
  def applied[A <: AnyKind](typeConstructor: TypeId[?], args: TypeRepr*): TypeId[A]
}
```

## Core Operations

This section documents all public methods on `TypeId` and its companion object, organized by category.

### Identity and Naming

These methods provide the type's name and fully qualified path.

#### `name` — Simple Type Name

Returns the unqualified name of the type:

```scala
sealed trait TypeId[A <: AnyKind] {
  def name: String
}
```

```scala

case class Order(id: String, total: Double)
val orderId = TypeId.of[Order]
```

```scala
orderId.name
// res16: String = "Order"
TypeId.int.name
// res17: String = "Int"
TypeId.list.name
// res18: String = "List"
```

#### `fullName` — Fully Qualified Name

Returns `owner.asString + "." + name`, or just `name` if the owner is root:

```scala
sealed trait TypeId[A <: AnyKind] {
  def fullName: String
}
```

```scala
orderId.fullName
// res19: String = "repl.MdocSession.MdocApp15.Order"
TypeId.int.fullName
// res20: String = "scala.Int"
TypeId.string.fullName
// res21: String = "java.lang.String"
```

#### `owner` — Enclosing Namespace

The `TypeId#owner` method returns the `Owner` — the hierarchical path showing exactly where a type is defined. This includes the complete package chain and any enclosing objects or types. `Owner` solves a critical problem: multiple types can have the same name (e.g., `User` in `com.api` and `User` in `com.admin`), and the owner uniquely distinguishes them by their definition location:

```scala
sealed trait TypeId[A <: AnyKind] {
  def owner: Owner
}
```

When we derive a `TypeId` for a custom type, the owner captures its full hierarchical location. We can then use `TypeId#fullName` to see how the owner combines with the type name:

```scala

case class User(id: Long, name: String)
```

```scala
val userId = TypeId.of[User]
// userId: TypeId[User] = User
userId.name
// res23: String = "User"
// The owner shows where this User is defined
userId.owner.asString
// res24: String = "repl.MdocSession.MdocApp22"
// fullName combines owner and name into a qualified path
userId.fullName
// res25: String = "repl.MdocSession.MdocApp22.User"
```

When we construct types from different packages, their owners differ even though the names are identical. This is essential for registries and serializers that need to distinguish between types with conflicting names:

```scala
// A User from the admin domain
val adminUser = TypeId.nominal[Any]("User", Owner.fromPackagePath("com.admin"), TypeDefKind.Unknown)
// adminUser: TypeId[Any] = User
adminUser.name
// res26: String = "User"
// Notice the owner is different
adminUser.owner.asString
// res27: String = "com.admin"
// So the full names are distinct
adminUser.fullName
// res28: String = "com.admin.User"

// Compare: both have name "User" but different owners
userId.name == adminUser.name
// res29: Boolean = true
userId.owner.asString == adminUser.owner.asString
// res30: Boolean = false
```

This distinction enables type-indexed registries where you can safely store types with identical names from different sources without collision. 

#### `toString` — Idiomatic Scala Rendering

Renders the TypeId as idiomatic Scala syntax using `TypeIdPrinter`:

```scala
TypeId.of[List[Int]].toString
// res31: String = "List[Int]"
TypeId.of[Map[String, Int]].toString
// res32: String = "Map[String, Int]"
```

### Type Parameters and Arguments

Methods for inspecting generic type information.

#### `typeParams` — Formal Type Parameters

The `TypeId#typeParams` method returns the list of formal type parameters declared by a type. This is what makes a type generic. For a type like `Box[+A]`, `typeParams` captures the declaration of `A` — including its name, position in the parameter list, variance (whether it's covariant `+`, contravariant `−`, or invariant), and any bounds:

```scala
sealed trait TypeId[A <: AnyKind] {
  def typeParams: List[TypeParam]
}
```

To see how `TypeId` preserves type parameter information, we define several generic types with different variance patterns. Each demonstrates a different type parameter characteristic:

```scala

sealed trait Container[+A]
case class Box[+A](value: A) extends Container[A]

sealed trait Sink[-T]
case class Logger[-T]() extends Sink[T]

sealed trait Cache[K, +V]
case class LRUCache[K, +V](maxSize: Int) extends Cache[K, V]
```

When we derive `TypeId` for these types, we can inspect their type parameters and see the variance that was declared:

```scala
val boxId = TypeId.of[Box]
// boxId: TypeId[[A >: Nothing <: Any] =>> Box[A]] = Box[+A]
// Box declares [+A], so we see one covariant parameter
boxId.typeParams
// res34: List[TypeParam] = List(
//   TypeParam(
//     name = "A",
//     index = 0,
//     variance = Covariant,
//     bounds = TypeBounds(lower = None, upper = None),
//     kind = Type
//   )
// )
boxId.typeParams.head.variance
// res35: Variance = Covariant
boxId.typeParams.head.name
// res36: String = "A"

val sinkId = TypeId.of[Sink]
// sinkId: TypeId[[T >: Nothing <: Any] =>> Sink[T]] = Sink[-T]
// Sink declares [-T], so we see one contravariant parameter
sinkId.typeParams
// res37: List[TypeParam] = List(
//   TypeParam(
//     name = "T",
//     index = 0,
//     variance = Contravariant,
//     bounds = TypeBounds(lower = None, upper = None),
//     kind = Type
//   )
// )
sinkId.typeParams.head.variance
// res38: Variance = Contravariant

val cacheId = TypeId.of[Cache]
// cacheId: TypeId[[K >: Nothing <: Any, V >: Nothing <: Any] =>> Cache[K, V]] = Cache[K, +V]
// Cache declares [K, +V], so we see two parameters with different variances
cacheId.typeParams
// res39: List[TypeParam] = List(
//   TypeParam(
//     name = "K",
//     index = 0,
//     variance = Invariant,
//     bounds = TypeBounds(lower = None, upper = None),
//     kind = Type
//   ),
//   TypeParam(
//     name = "V",
//     index = 1,
//     variance = Covariant,
//     bounds = TypeBounds(lower = None, upper = None),
//     kind = Type
//   )
// )
cacheId.typeParams.map(p => (p.name, p.variance.symbol))
// res40: List[Tuple2[String, String]] = List(("K", ""), ("V", "+"))
```

#### `TypeParam` — Type Parameter Details

Each element in `TypeId#typeParams` is a `TypeParam` value. A type parameter defines a single formal parameter in a generic type's declaration — its name, position, variance (covariance, contravariance, invariance), bounds, and kind. When you derive a `TypeId` for a generic type like `Box[+A]`, the macro captures each declared parameter as a `TypeParam` so you can inspect them at runtime.

`TypeParam` captures these pieces of information about a type parameter:

```scala
final case class TypeParam(
  name: String,                           // "A", "T", "K", "F"
  index: Int,                             // Position: 0, 1, 2, ...
  variance: Variance = Variance.Invariant, // +, -, or none
  bounds: TypeBounds = TypeBounds.Unbounded, // >: Lower <: Upper
  kind: Kind = Kind.Type                  // *, * -> *, etc.
)
```

To inspect individual fields of a type parameter, we can extract and examine each one:

```scala

sealed trait Functor[F[_]]
```

To inspect individual fields of a type parameter, extract and examine each property:

```scala
val functorId = TypeId.of[Functor]
// functorId: TypeId[[F >: Nothing <: [_$1 >: Nothing <: Any] =>> Any] =>> Functor[F]] = Functor[F]
val paramF = functorId.typeParams.head
// paramF: TypeParam = TypeParam(
//   name = "F",
//   index = 0,
//   variance = Invariant,
//   bounds = TypeBounds(lower = None, upper = None),
//   kind = Type
// )

paramF.name
// res42: String = "F"
paramF.index
// res43: Int = 0
paramF.variance
// res44: Variance = Invariant
paramF.isInvariant
// res45: Boolean = true
paramF.kind
// res46: Kind = Type
paramF.isTypeConstructor
// res47: Boolean = false
```

`TypeParam` provides convenience predicates for checking variance without inspecting the raw `variance` field:

```scala

sealed trait Box[+A]
sealed trait Sink[-T]
sealed trait Cache[K, +V]
```

Using these types, we can check the variance predicates to verify which parameters are covariant, contravariant, or invariant:

```scala
val boxId = TypeId.of[Box]
// boxId: TypeId[[A >: Nothing <: Any] =>> Box[A]] = Box[+A]
boxId.typeParams.head.isCovariant
// res49: Boolean = true

val sinkId = TypeId.of[Sink]
// sinkId: TypeId[[T >: Nothing <: Any] =>> Sink[T]] = Sink[-T]
sinkId.typeParams.head.isContravariant
// res50: Boolean = true

val cacheId = TypeId.of[Cache]
// cacheId: TypeId[[K >: Nothing <: Any, V >: Nothing <: Any] =>> Cache[K, V]] = Cache[K, +V]
val (k, v) = (cacheId.typeParams(0), cacheId.typeParams(1))
// k: TypeParam = TypeParam(
//   name = "K",
//   index = 0,
//   variance = Invariant,
//   bounds = TypeBounds(lower = None, upper = None),
//   kind = Type
// )
// v: TypeParam = TypeParam(
//   name = "V",
//   index = 1,
//   variance = Covariant,
//   bounds = TypeBounds(lower = None, upper = None),
//   kind = Type
// )
k.isInvariant
// res51: Boolean = true
v.isCovariant
// res52: Boolean = true
```

#### `typeArgs` — Applied Type Arguments

**Applied types** are generic types instantiated with concrete type arguments. For example, `List[Int]` is the generic `List` type constructor applied to the `Int` type argument, and `Map[String, Int]` applies two arguments to `Map`. When you derive a `TypeId` for an applied type, the `typeArgs` method returns the concrete type arguments as a list of `TypeRepr` values — allowing you to inspect what types were plugged into the type constructor.

The `typeArgs` method is essential for schema systems and code generators that need to understand the full type structure. For instance, a serializer might need to know that `List[Int]` has `Int` as its element type, or a validator might need to distinguish between `Map[String, Int]` and `Map[String, String]`:

```scala
sealed trait TypeId[A <: AnyKind] {
  def typeArgs: List[TypeRepr]
}
```

`typeArgs` returns a list of `TypeRepr` values. `TypeRepr` is an algebraic data type that represents type expressions in the Scala type system — these can be simple type references (like `Int` or `String`), complex applied types (like `List[String]`), or compound types (like `A & B`). For a detailed breakdown of all `TypeRepr` variants, see [TypeRepr — Type Expressions](#typerepr--type-expressions).

Setup some custom generic types with different type argument patterns:

```scala

case class Pair[A, B](first: A, second: B)
case class Container[T](value: T)
case class Result[E, V](error: Option[E], value: Option[V])
```

Now inspect the type arguments of various applied types:

```scala
// Simple single type argument
val containerIntId = TypeId.of[Container[Int]]
// containerIntId: TypeId[Container[Int]] = Container[Int]
containerIntId.typeArgs
// res54: List[TypeRepr] = List(Ref(Int))

// Multiple type arguments
val pairId = TypeId.of[Pair[String, Double]]
// pairId: TypeId[Pair[String, Double]] = Pair[String, Double]
pairId.typeArgs
// res55: List[TypeRepr] = List(Ref(String), Ref(Double))

// Nested applied types
val resultId = TypeId.of[Result[String, List[Int]]]
// resultId: TypeId[Result[String, List[Int]]] = Result[String, List[Int]]
resultId.typeArgs
// res56: List[TypeRepr] = List(
//   Ref(String),
//   Applied(tycon = Ref(List[+A]), args = List(Ref(Int)))
// )

// Type constructor with no arguments has empty typeArgs
TypeId.of[List].typeArgs
// res57: List[TypeRepr] = List()
```

When you access `typeArgs`, each element is a `TypeRepr` describing that argument. You can inspect them further to understand the structure:

```scala
val mapStringIntId = TypeId.of[Map[String, Int]]
// mapStringIntId: TypeId[Map[String, Int]] = Map[String, Int]
val args = mapStringIntId.typeArgs
// args: List[TypeRepr] = List(Ref(String), Ref(Int))

// First argument: String
args(0)
// res58: TypeRepr = Ref(String)

// Second argument: Int
args(1)
// res59: TypeRepr = Ref(Int)
```

For more complex types, `typeArgs` captures the full structure of the arguments, including unions, intersections, function types, and tuples:

```scala

// Union types (Scala 3)
case class Handler[T](process: T | String)

// Intersection types (Scala 3)
trait Readable { def read(): String }
trait Writable { def write(data: String): Unit }
case class Stream[T](data: T & Readable & Writable)

// Function type arguments
case class Transformer[A, B](f: A => B)

// Tuple type arguments
case class MultiValue[A, B, C](values: (A, B, C))
```

Now inspect the type arguments in these complex types:

```scala
// Union type argument
val handlerStrId = TypeId.of[Handler[Int]]
// handlerStrId: TypeId[Handler[Int]] = Handler[Int]
handlerStrId.typeArgs
// res61: List[TypeRepr] = List(Ref(Int))

// Intersection type argument
val streamId = TypeId.of[Stream[List[String]]]
// streamId: TypeId[Stream[List[String]]] = Stream[List[String]]
streamId.typeArgs
// res62: List[TypeRepr] = List(
//   Applied(tycon = Ref(List[+A]), args = List(Ref(String)))
// )

// Function type as argument
val transformerId = TypeId.of[Transformer[String, Int]]
// transformerId: TypeId[Transformer[String, Int]] = Transformer[String, Int]
transformerId.typeArgs
// res63: List[TypeRepr] = List(Ref(String), Ref(Int))

// Tuple type as argument
val multiValueId = TypeId.of[MultiValue[String, Int, Boolean]]
// multiValueId: TypeId[MultiValue[String, Int, Boolean]] = MultiValue[String, Int, Boolean]
multiValueId.typeArgs
// res64: List[TypeRepr] = List(Ref(String), Ref(Int), Ref(Boolean))
```

#### `arity` — Number of Type Parameters

The **arity** of a type is the number of formal type parameters it declares. A type with arity 0 is fully applied (a "proper type"), while arity > 0 means it's a type constructor that needs to be instantiated with type arguments. Arity is useful for generic programming and type-indexed registries where you need to distinguish between different levels of type abstraction:

```scala
sealed trait TypeId[A <: AnyKind] {
  def arity: Int
}
```

Setup some generic types with different arities:

```scala

case class Single[A](value: A)           // Arity 1
case class Pair[A, B](a: A, b: B)       // Arity 2
case class Triple[A, B, C](a: A, b: B, c: C) // Arity 3
case class Value(x: Int)                 // Arity 0
```

Check the arity of different types:

```scala
TypeId.of[Single].arity
// res66: Int = 1
TypeId.of[Pair].arity
// res67: Int = 2
TypeId.of[Triple].arity
// res68: Int = 3
TypeId.of[Value].arity
// res69: Int = 0

// Applied types have the same arity as their type constructor
TypeId.of[Single[Int]].arity
// res70: Int = 1
TypeId.of[Pair[String, Int]].arity
// res71: Int = 2
```

#### `isProperType` — Has No Type Parameters

A **proper type** (also called a ground type or monomorphic type) is a fully instantiated type with no unresolved type parameters. It's the opposite of a type constructor — you can directly instantiate values of a proper type, whereas a type constructor needs type arguments before it's usable. The `isProperType` predicate returns `true` when `arity == 0`, helping distinguish concrete types from abstract type constructors:

```scala

case class Single[A](value: A)
case class Pair[A, B](a: A, b: B)
case class Value(x: Int)
```

Check which types are proper types:

```scala
// Proper types: fully instantiated, arity == 0
TypeId.of[Value].isProperType
// res73: Boolean = true
TypeId.of[List[Int]].isProperType
// res74: Boolean = false
TypeId.of[Pair[String, Int]].isProperType
// res75: Boolean = false
TypeId.of[Int].isProperType
// res76: Boolean = true

// Type constructors: need type arguments, arity > 0
TypeId.of[Single].isProperType
// res77: Boolean = false
TypeId.of[Pair].isProperType
// res78: Boolean = false
TypeId.of[List].isProperType
// res79: Boolean = false
```

#### `isTypeConstructor` — Has Type Parameters

A **type constructor** is a parameterized type that cannot be instantiated directly — it requires concrete type arguments first. For example, `List` is a type constructor (you can't have a value of type `List`, only `List[Int]` or `List[String]`). The `isTypeConstructor` predicate returns `true` when `arity > 0`, indicating the type needs to be applied with arguments before use. This is useful for generic programming where you work with families of related types:

```scala

case class Single[A](value: A)
case class Pair[A, B](a: A, b: B)
case class Value(x: Int)
```

Identify which types are type constructors:

```scala
// Type constructors: need type arguments, arity > 0
TypeId.of[Single].isTypeConstructor
// res81: Boolean = true
TypeId.of[Pair].isTypeConstructor
// res82: Boolean = true
TypeId.of[List].isTypeConstructor
// res83: Boolean = true
TypeId.of[Map].isTypeConstructor
// res84: Boolean = true

// Proper types: fully instantiated, no type parameters
TypeId.of[Value].isTypeConstructor
// res85: Boolean = false
TypeId.of[List[Int]].isTypeConstructor
// res86: Boolean = true
TypeId.of[Int].isTypeConstructor
// res87: Boolean = false
```

#### `isApplied` — Has Type Arguments

An **applied type** is a generic type that has been instantiated with concrete type arguments. For example, `List[Int]` is an applied type (`List` applied to `Int`), while `List` by itself is a type constructor with no arguments applied. The `isApplied` predicate returns `true` when `typeArgs.nonEmpty`, helping distinguish between abstract type constructors and concrete instantiated types. This is useful for code generators that need to know whether a type is ready for use:

```scala

case class Single[A](value: A)
case class Pair[A, B](a: A, b: B)
```

Check which types are applied:

```scala
// Applied types: have type arguments
TypeId.of[List[Int]].isApplied
// res89: Boolean = true
TypeId.of[Pair[String, Int]].isApplied
// res90: Boolean = true
TypeId.of[Single[Boolean]].isApplied
// res91: Boolean = true
TypeId.of[Map[String, Double]].isApplied
// res92: Boolean = true

// Type constructors: no type arguments applied
TypeId.of[List].isApplied
// res93: Boolean = false
TypeId.of[Single].isApplied
// res94: Boolean = false
TypeId.of[Pair].isApplied
// res95: Boolean = false
TypeId.of[Map].isApplied
// res96: Boolean = false
```

### Type Classification

**Type classification** determines what kind of type definition something is — whether it's a class, trait, object, enum, alias, opaque type, or something else. This is essential for code generators, serializers, and frameworks that need to handle different type categories differently. TypeId provides both a `defKind` property that returns detailed classification information, and convenient predicates (like `isClass`, `isTrait`, `isCaseClass`) for common checks.

#### `defKind` — Type Definition Kind

Returns the `TypeDefKind` classifying this type (class, trait, object, enum, alias, opaque, etc.):

```scala
sealed trait TypeId[A <: AnyKind] {
  def defKind: TypeDefKind
}
```

Define types representing different classifications:

```scala

sealed trait Animal
case class Dog(name: String) extends Animal
case object Sentinel
type UserId = String
opaque type Email = String
enum Color { case Red; case Green; case Blue }
```

Inspect the `defKind` for each type to see how they're classified:

```scala
TypeId.of[Dog].defKind
// res98: TypeDefKind = Class(
//   isFinal = false,
//   isAbstract = false,
//   isCase = true,
//   isValue = false,
//   bases = List(Ref(Animal))
// )
TypeId.of[Animal].defKind
// res99: TypeDefKind = Trait(isSealed = true, bases = List())
TypeId.of[Sentinel.type].defKind
// res100: TypeDefKind = Object(List())
TypeId.of[UserId].defKind
// res101: TypeDefKind = TypeAlias
TypeId.of[Email].defKind
// res102: TypeDefKind = OpaqueType(TypeBounds(lower = None, upper = None))
TypeId.of[Color].defKind
// res103: TypeDefKind = Enum(List(Ref(Enum)))
```

#### Classification Predicates

Each predicate inspects `defKind` for a specific type definition kind. These are convenience methods that save you from pattern matching on `defKind` directly:

| Predicate        | Returns `true` when                                         |
|------------------|-------------------------------------------------------------|
| `isClass`        | `defKind` is `TypeDefKind.Class`                            |
| `isTrait`        | `defKind` is `TypeDefKind.Trait`                            |
| `isObject`       | `defKind` is `TypeDefKind.Object`                           |
| `isEnum`         | `defKind` is `TypeDefKind.Enum`                             |
| `isAlias`        | `defKind` is `TypeDefKind.TypeAlias`                        |
| `isOpaque`       | `defKind` is `TypeDefKind.OpaqueType`                       |
| `isAbstract`     | `defKind` is `TypeDefKind.AbstractType`                     |
| `isSealed`       | `defKind` is `TypeDefKind.Trait(isSealed = true, _)` (sealed traits only) |
| `isCaseClass`    | `defKind` is `TypeDefKind.Class(_, _, isCase = true, _, _)` |
| `isValueClass`   | `defKind` is `TypeDefKind.Class(_, _, _, isValue = true, _)`|

Use the classification predicates to identify type kinds:

```scala
val animalId   = TypeId.of[Animal]
// animalId: TypeId[Animal] = Animal
val dogId      = TypeId.of[Dog]
// dogId: TypeId[Dog] = Dog
val sentinelId = TypeId.of[Sentinel.type]
// sentinelId: TypeId[Sentinel] = Sentinel
val userIdId   = TypeId.of[UserId]
// userIdId: TypeId[UserId] = UserId
val emailId    = TypeId.of[Email]
// emailId: TypeId[Email] = Email
val colorId    = TypeId.of[Color]
// colorId: TypeId[Color] = Color

// Trait classifications
animalId.isTrait
// res104: Boolean = true
animalId.isSealed
// res105: Boolean = true

// Case class
dogId.isCaseClass
// res106: Boolean = true

// Object/Singleton
sentinelId.isObject
// res107: Boolean = true

// Type alias
userIdId.isAlias
// res108: Boolean = true

// Opaque type
emailId.isOpaque
// res109: Boolean = true

// Enum
colorId.isEnum
// res110: Boolean = true
```

:::note
`isSealed` only checks sealed traits. A sealed abstract class or sealed enum will return `false`; use `defKind` directly to inspect those cases by pattern matching on `TypeDefKind.Class(_, isAbstract = true, ...)` or `TypeDefKind.Enum(...)`.
:::

#### Semantic Predicates

**Semantic predicates** check specific semantic properties of the type after normalization, allowing you to identify built-in Scala types like tuples, products, sums, options, and either. These are useful for generic serializers and validators that treat built-in types specially.

**Normalization** resolves type aliases and opaque types to their underlying representations. For example, if you have `type UserId = String`, normalization reveals that the underlying type is `String`. Similarly, an opaque type like `opaque type Email = String` normalizes to `String`. This allows predicates like `isOption` to work correctly even when the type is wrapped in an alias or opaque type — it will look through the wrapper to find the actual semantic type.

| Predicate   | Checks                                                 |
|-------------|--------------------------------------------------------|
| `isTuple`   | Normalized type is `scala.TupleN`                      |
| `isProduct` | Normalized type is `scala.Product` or `scala.ProductN` |
| `isSum`     | Normalized type is named `Either` or `Option`          |
| `isEither`  | Normalized type is `scala.util.Either`                 |
| `isOption`  | Normalized type is `scala.Option`                      |

Check semantic properties with practical examples:

```scala
// Tuples
TypeId.of[(String, Int)].isTuple
// res111: Boolean = true
TypeId.of[(Int, String, Boolean)].isTuple
// res112: Boolean = true

// Options and Either
TypeId.of[Option[String]].isOption
// res113: Boolean = true
TypeId.of[Either[String, Int]].isEither
// res114: Boolean = true

// Products (built-in Scala Product interface, not user case classes)
TypeId.of[Product2[String, Int]].isProduct
// res115: Boolean = true
```

:::note
`isProduct` returns `true` only for Scala's built-in `scala.Product`, `scala.Product1`, etc. -- not for user-defined case classes. Use `isCaseClass` for that.
:::

Understanding the distinction between `isSum`, `isEither`, and `isOption`:

```scala

// For standard library types, use isEither and isOption
TypeId.of[Option[String]].isOption
// res116: Boolean = true
TypeId.of[Option[String]].isSum
// res117: Boolean = true

TypeId.of[Either[String, Int]].isEither
// res118: Boolean = true
TypeId.of[Either[String, Int]].isSum
// res119: Boolean = false
```

[//]: # (:::note)
[//]: # (**Practical guidance:** Always use `isEither` for `scala.util.Either` and `isOption` for `scala.Option`. The `isSum` predicate is rarely needed — it checks for hypothetical types named `"Option"` or `"Either"` placed directly in the `scala` package itself &#40;not in `scala.util` subpackage&#41;, which almost never occurs in real code.)
[//]: # (:::)

### Subtype Relationships

**Subtype relationships** determine the inheritance hierarchy and compatibility between types at runtime. This is essential for type-safe dispatch, generic programming, and validating that a value of one type can be used where another type is expected. TypeId provides methods to check direct and transitive subtyping, supertyping, type equivalence, and inspect the parent types in the hierarchy.

#### `isSubtypeOf` — Check Subtyping

Checks if this type is a subtype of another type. A type is a subtype if it extends or implements the other type, either directly or transitively. This method handles direct inheritance, sealed trait subtypes, enum cases, transitive inheritance chains, and variance-aware subtyping for applied generic types:

```scala
sealed trait TypeId[A <: AnyKind] {
  def isSubtypeOf(other: TypeId[?]): Boolean
}
```

Define a type hierarchy with direct and transitive relationships:

```scala

sealed trait Animal
sealed trait Mammal extends Animal
case class Dog(name: String) extends Mammal
case class Cat(name: String) extends Mammal
case class Fish(species: String) extends Animal
```

Check subtyping relationships:

```scala
val dogId     = TypeId.of[Dog]
// dogId: TypeId[Dog] = Dog
val mammalId  = TypeId.of[Mammal]
// mammalId: TypeId[Mammal] = Mammal
val animalId  = TypeId.of[Animal]
// animalId: TypeId[Animal] = Animal
val fishId    = TypeId.of[Fish]
// fishId: TypeId[Fish] = Fish

// Direct inheritance: Dog extends Mammal
dogId.isSubtypeOf(mammalId)
// res121: Boolean = true

// Transitive inheritance: Dog extends Mammal extends Animal
dogId.isSubtypeOf(animalId)
// res122: Boolean = true

// Not a subtype relationship
dogId.isSubtypeOf(fishId)
// res123: Boolean = false
fishId.isSubtypeOf(mammalId)
// res124: Boolean = false
```

Covariant type constructors preserve subtyping relationships:

```scala
TypeId.of[List[Dog]].isSubtypeOf(TypeId.of[List[Mammal]])
// res125: Boolean = true
TypeId.of[List[Dog]].isSubtypeOf(TypeId.of[List[Animal]])
// res126: Boolean = true
```

**Scala 3 exclusive features:** In Scala 3, `isSubtypeOf` handles advanced type relationships that Scala 2 cannot. These examples show what works in Scala 3:

```scala

// Scala 3: Enum cases
enum Color {
  case Red
  case Green
  case Blue
}

// Scala 3: Union type aliases
type StringOrInt = String | Int

// Scala 3: Intersection type aliases
trait Readable { def read(): String }
trait Writable { def write(data: String): Unit }
type ReadWrite = Readable & Writable
```

In Scala 3, `isSubtypeOf` correctly handles these advanced type cases:

```scala
// Enum cases: Red is a subtype of Color
TypeId.of[Color.Red.type].isSubtypeOf(TypeId.of[Color])
// res128: Boolean = true

// Union type aliases: String is one of the union members
TypeId.of[String].isSubtypeOf(TypeId.of[StringOrInt])
// res129: Boolean = true
TypeId.of[Int].isSubtypeOf(TypeId.of[StringOrInt])
// res130: Boolean = true

// Intersection type aliases: A type implementing both traits is a subtype
val readWriteId = TypeId.of[ReadWrite]
// readWriteId: TypeId[ReadWrite] = ReadWrite
val readableId = TypeId.of[Readable]
// readableId: TypeId[Readable] = Readable
readWriteId.isSubtypeOf(readableId)
// res131: Boolean = true
```

:::note
In Scala 2, `isSubtypeOf` does not handle `EnumCase` subtypes, types aliased to union types, or types aliased to intersection types — only the Scala 3 implementation checks those cases.
:::

#### `isSupertypeOf` — Check Supertyping

The mirror of `isSubtypeOf` — returns `true` if the other type is a subtype of this type. This is useful when you need to check if a type can accept instances of another type, or when validating that a container type can hold values of a more specific type:

```scala
sealed trait TypeId[A <: AnyKind] {
  def isSupertypeOf(other: TypeId[?]): Boolean
}
```

Check supertyping relationships using the same hierarchy:

```scala

sealed trait Animal
sealed trait Mammal extends Animal
case class Dog(name: String) extends Mammal
case class Cat(name: String) extends Mammal
case class Fish(species: String) extends Animal

val dogId     = TypeId.of[Dog]
val mammalId  = TypeId.of[Mammal]
val animalId  = TypeId.of[Animal]
val fishId    = TypeId.of[Fish]
```

Now check supertyping relationships:

```scala
// Mammal is a supertype of Dog (Mammal can hold Dog instances)
mammalId.isSupertypeOf(dogId)
// res133: Boolean = true

// Animal is a supertype of both Dog and Fish (Animal is the most general)
animalId.isSupertypeOf(dogId)
// res134: Boolean = true
animalId.isSupertypeOf(fishId)
// res135: Boolean = true

// Mammal is a supertype of Cat too
mammalId.isSupertypeOf(TypeId.of[Cat])
// res136: Boolean = true

// But Dog is not a supertype of Mammal (can't hold all Mammals as Dogs)
dogId.isSupertypeOf(mammalId)
// res137: Boolean = false

// And Fish is not a supertype of Mammal
fishId.isSupertypeOf(mammalId)
// res138: Boolean = false
```

:::note
**Limitation:** TypeId's subtyping checks currently do not handle contravariance in function types. In type theory, `Mammal => String` should be a supertype of `Dog => String` due to contravariance of input parameters, but `isSupertypeOf` returns `false` for function types with subtype relationships. For practical purposes, rely on `isSupertypeOf` for class and trait hierarchies rather than complex generic type relationships.
:::

#### `isEquivalentTo` — Check Type Equivalence

Returns `true` when two types are structurally equivalent — meaning they are **mutual subtypes** of each other. In other words, both `A.isSubtypeOf(B)` and `B.isSubtypeOf(A)` must be true. Two types are equivalent when they represent the same type through different paths, or when they normalize to the same underlying type (important for type aliases and opaque types):

```scala
sealed trait TypeId[A <: AnyKind] {
  def isEquivalentTo(other: TypeId[?]): Boolean
}
```

Check type equivalence with practical examples:

```scala

sealed trait Animal
sealed trait Mammal extends Animal
case class Dog(name: String) extends Mammal
case class Cat(name: String) extends Mammal

val dogId     = TypeId.of[Dog]
val mammalId  = TypeId.of[Mammal]
val animalId  = TypeId.of[Animal]
val catId     = TypeId.of[Cat]
```

Now check type equivalence:

```scala
// A type is always equivalent to itself
dogId.isEquivalentTo(dogId)
// res140: Boolean = true

// The same type referenced twice is equivalent
val dogId2 = TypeId.of[Dog]
// dogId2: TypeId[Dog] = Dog
dogId.isEquivalentTo(dogId2)
// res141: Boolean = true

// Different types in the hierarchy are NOT equivalent (one-way subtyping only)
dogId.isEquivalentTo(mammalId)
// res142: Boolean = false
mammalId.isEquivalentTo(animalId)
// res143: Boolean = false

// Cat and Dog are different types, even though both extend Mammal
dogId.isEquivalentTo(catId)
// res144: Boolean = false
```

Type aliases normalize to the same type, making them equivalent:

```scala

type UserId = String
type Username = String
```

Both aliases normalize to `String`, so they are equivalent:

```scala
val userIdType = TypeId.of[UserId]
// userIdType: TypeId[UserId] = UserId
val usernameType = TypeId.of[Username]
// usernameType: TypeId[Username] = Username
val stringType = TypeId.of[String]
// stringType: TypeId[String] = String

// Both type aliases are equivalent because they normalize to the same underlying type
userIdType.isEquivalentTo(usernameType)
// res146: Boolean = true

// And both are equivalent to their underlying type
userIdType.isEquivalentTo(stringType)
// res147: Boolean = true
usernameType.isEquivalentTo(stringType)
// res148: Boolean = true
```

#### `parents` — Parent Types

Returns the list of parent type representations as `TypeRepr` values, flattened across the full inheritance hierarchy. Each parent is represented as a `TypeRepr` that captures the parent type, including any type arguments it might have. This is useful for code generators, serializers, and frameworks that need to understand the inheritance structure of a type:

```scala
sealed trait TypeId[A <: AnyKind] {
  def parents: List[TypeRepr]
}
```

```scala

trait Swimmer { def swim(): Unit = () }
trait Flyer   { def fly(): Unit  = () }
trait Duck extends Swimmer with Flyer
case class MallardDuck() extends Duck
```

Parents are flattened across the full hierarchy:

```scala
// Duck extends Swimmer and Flyer directly
TypeId.of[Duck].parents
// res150: List[TypeRepr] = List(Ref(Flyer), Ref(Swimmer))

// MallardDuck extends Duck — parents include Duck, Swimmer, and Flyer
TypeId.of[MallardDuck].parents
// res151: List[TypeRepr] = List(Ref(Duck), Ref(Flyer), Ref(Swimmer))
```

### Metadata

Methods for accessing annotations, self-type, alias target, and opaque representation.

#### `annotations` — Type Annotations

Returns the list of annotations attached to this type at compile time. Each `Annotation` carries the annotation's name and its argument values, making this useful for frameworks that drive behaviour from annotations (e.g. serialization hints, validation rules, or access-control markers):

```scala
sealed trait TypeId[A <: AnyKind] {
  def annotations: List[Annotation]
}
```

```scala

@deprecated("use NewData instead", "2.0")
@transient
case class LegacyData(id: Int, payload: String)
case class Plain(x: Int)
```

A type with annotations reports each annotation by name; an unannotated type returns an empty list:

```scala
// LegacyData has two annotations
TypeId.of[LegacyData].annotations.map(_.name)
// res153: List[String] = List("transient", "deprecated")

// Plain has no annotations
TypeId.of[Plain].annotations
// res154: List[Annotation] = List()
```

#### `selfType` — Self-Type Annotation

Returns `Some(typeRepr)` when the trait declares a self-type (e.g., `trait Foo { self: Bar => ... }`), and `None` otherwise. Self-types express a dependency requirement: a trait that declares `self: Logger =>` can only be mixed into a class that also mixes in `Logger`. This method lets frameworks detect and validate those requirements at runtime:

```scala
sealed trait TypeId[A <: AnyKind] {
  def selfType: Option[TypeRepr]
}
```

```scala

trait Logger { def log(msg: String): Unit }
trait Service { self: Logger => def doWork(): Unit = log("working") }
```

`Service` requires a `Logger` to be mixed in, while `Logger` has no self-type requirement:

```scala
// Service declares a self-type dependency on Logger
TypeId.of[Service].selfType
// res156: Option[TypeRepr] = Some(Logger & Service)

// Logger has no self-type requirement
TypeId.of[Logger].selfType
// res157: Option[TypeRepr] = None
```

#### `aliasedTo` — Alias Target

Returns `Some(typeRepr)` for type aliases pointing to their underlying type, and `None` for nominal and opaque types. This lets you inspect what a type alias expands to without evaluating expressions at runtime:

```scala
sealed trait TypeId[A <: AnyKind] {
  def aliasedTo: Option[TypeRepr]
}
```

```scala

type Age = Int
type Name = String
```

A type alias resolves to its target; a concrete type returns `None`:

```scala
// Age is an alias for Int
TypeId.of[Age].aliasedTo
// res159: Option[TypeRepr] = Some(Ref(Int))

// Name is an alias for String
TypeId.of[Name].aliasedTo
// res160: Option[TypeRepr] = Some(Ref(String))

// Int is a concrete type, not an alias
TypeId.of[Int].aliasedTo
// res161: Option[TypeRepr] = None
```

#### `representation` — Opaque Type Representation

Returns `Some(typeRepr)` for opaque types revealing their underlying representation type, and `None` for all other types. Opaque types hide their implementation behind a new name, but `representation` lets frameworks such as serializers discover what the type is actually stored as:

```scala
sealed trait TypeId[A <: AnyKind] {
  def representation: Option[TypeRepr]
}
```

```scala

opaque type Email = String
opaque type UserId = Int
```

An opaque type exposes its representation; a non-opaque type returns `None`:

```scala
// Email is an opaque type backed by String
TypeId.of[Email].representation
// res163: Option[TypeRepr] = Some(Ref(String))

// UserId is an opaque type backed by Int
TypeId.of[UserId].representation
// res164: Option[TypeRepr] = Some(Ref(Int))

// Int is not opaque
TypeId.of[Int].representation
// res165: Option[TypeRepr] = None
```

### Erasure and Runtime

Methods for type erasure, runtime class lookup, and reflective construction.

#### `erased` — Erase Type Parameter

Erases the phantom type parameter, returning a `TypeId.Erased` (alias for `TypeId[TypeId.Unknown]`). This is useful when you need to store heterogeneous `TypeId` values in a collection or a type-indexed map, where the exact type parameter is unknown or irrelevant at the storage site:

```scala
sealed trait TypeId[A <: AnyKind] {
  def erased: TypeId.Erased
}
```

Different types can be stored together once erased:

```scala
val ids: List[TypeId.Erased] = List(
  TypeId.of[Int].erased,
  TypeId.of[String].erased,
  TypeId.of[Boolean].erased
)
// ids: List[Erased] = List(Int, String, Boolean)

ids.map(_.name)
// res166: List[String] = List("Int", "String", "Boolean")
```

#### `classTag` — Runtime ClassTag

Returns a `ClassTag` for this type. Returns the correct primitive `ClassTag` for Scala primitive types (`Int`, `Long`, `Boolean`, etc.) and `ClassTag.AnyRef` for all reference types. This is useful when you need to create properly-typed arrays or work with generic collections that require implicit `ClassTag` evidence at runtime:

```scala
sealed trait TypeId[A <: AnyKind] {
  lazy val classTag: scala.reflect.ClassTag[?]
}
```

On the JVM, arrays are reified — the element type is part of the array object at runtime, not erased like generics. To create an array of a generic type `T`, Scala requires a `ClassTag[T]` so the runtime knows whether to allocate a primitive array (`int[]`, `double[]`) or an object array (`Object[]`). This matters for memory efficiency: a primitive `int[]` stores 4 bytes per element unboxed, while an `Integer[]` stores heap references plus the cost of boxing each value.

`classTag` returns the correct `ClassTag` for each type:

```scala
// Primitive types have dedicated ClassTags
TypeId.of[Int].classTag
// res167: ClassTag[_ >: Nothing <: Any] = Int
TypeId.of[Double].classTag
// res168: ClassTag[_ >: Nothing <: Any] = Double

// Reference types use ClassTag.AnyRef
TypeId.of[String].classTag
// res169: ClassTag[_ >: Nothing <: Any] = Object
TypeId.of[List[Int]].classTag
// res170: ClassTag[_ >: Nothing <: Any] = Object
```

A concrete use case is a generic storage allocator that creates the right array type from a `TypeId`:

```scala

def makeStorage(size: Int, id: TypeId[?]): Array[?] =
  id.classTag.newArray(size)
```

```scala
// Creates int[] (primitive, unboxed)
makeStorage(100, TypeId.int).getClass.getComponentType
// res172: Class[_ >: Nothing <: <FromJavaObject>] = int

// Creates double[] (primitive, unboxed)
makeStorage(100, TypeId.double).getClass.getComponentType
// res173: Class[_ >: Nothing <: <FromJavaObject>] = double

// Creates Object[] (reference)
makeStorage(100, TypeId.string).getClass.getComponentType
// res174: Class[_ >: Nothing <: <FromJavaObject>] = class java.lang.Object
```

Another use case is detecting primitive types. Without `classTag`, you would need to enumerate every primitive with a chain of `isInstanceOf` checks:

```scala
// Without classTag: every primitive listed explicitly
def isPrimitive(value: Any): Boolean =
  value.isInstanceOf[Int]     ||
  value.isInstanceOf[Long]    ||
  value.isInstanceOf[Float]   ||
  value.isInstanceOf[Double]  ||
  value.isInstanceOf[Boolean] ||
  value.isInstanceOf[Byte]    ||
  value.isInstanceOf[Short]   ||
  value.isInstanceOf[Char]
```

This is fragile: if you forget one primitive (e.g. `Unit`) the check silently breaks. With `classTag` the same question reduces to a single comparison that can never miss a case — `ClassTag.AnyRef` is the universal fallback for every reference type, so anything that is not `AnyRef` must be a primitive:

```scala

def isPrimitive(id: TypeId[?]): Boolean =
  id.classTag != scala.reflect.ClassTag.AnyRef
```

```scala
isPrimitive(TypeId.of[Int])
// res176: Boolean = true
isPrimitive(TypeId.of[Double])
// res177: Boolean = true
isPrimitive(TypeId.of[Boolean])
// res178: Boolean = true
isPrimitive(TypeId.of[String])
// res179: Boolean = false
isPrimitive(TypeId.of[List[Int]])
// res180: Boolean = false
```

:::note
`classTag` returns `ClassTag.AnyRef` for all reference types. For matching or filtering by a specific reference type at runtime, use `clazz` instead.
:::

#### `clazz` — Runtime Class

Returns the runtime `Class[_]` for this type. On the JVM it returns `Some(Class[_])` for nominal and applied types, and `None` for alias and opaque types. On Scala.js it always returns `None` since JVM reflection is unavailable. This is the entry point for reflective operations such as instantiation, field access, or integration with Java libraries:

```scala
sealed trait TypeId[A <: AnyKind] {
  def clazz: Option[Class[?]]
}
```

```scala

type Age = Int
```

```scala
// Nominal and applied types return Some on the JVM
TypeId.of[String].clazz
// res182: Option[Class[_ >: Nothing <: Any]] = Some(class java.lang.String)
TypeId.of[Int].clazz
// res183: Option[Class[_ >: Nothing <: Any]] = Some(int)
TypeId.of[List[Int]].clazz
// res184: Option[Class[_ >: Nothing <: Any]] = Some(
//   class scala.collection.immutable.List
// )

// Alias types return None — the alias has no class of its own
TypeId.of[Age].clazz
// res185: Option[Class[_ >: Nothing <: Any]] = None
```

:::note
On Scala.js, `clazz` always returns `None`. Use `classTag` instead when you need cross-platform runtime type information.
:::

#### `construct` — Reflective Construction

Constructs an instance using the primary constructor on the JVM by passing constructor arguments as a `Chunk[AnyRef]`. Returns `Left` with an error message on Scala.js or when construction fails (wrong argument count, wrong types, or abstract types). Primitive values must be explicitly boxed since the argument type is `AnyRef`:

```scala
sealed trait TypeId[A <: AnyKind] {
  def construct(args: Chunk[AnyRef]): Either[String, Any]
}
```

```scala

// JVM only
case class User(name: String, age: Int)

val userId = TypeId.of[User]
```

```scala
userId.construct(Chunk("Alice", 30: Integer))
// res187: Either[String, Any] = Left(
//   "Cannot construct repl.MdocSession.MdocApp186.User: class not available"
// )
userId.construct(Chunk("Bob"))
// res188: Either[String, Any] = Left(
//   "Cannot construct repl.MdocSession.MdocApp186.User: class not available"
// )
```

##### Collection Types

Collection types accept variadic arguments representing elements. Sequence-like types (`List`, `Vector`, `Set`, `Seq`, `IndexedSeq`, `Array`, `ArraySeq`, `Chunk`) each pass a variadic sequence of elements:

```scala

```

```scala
TypeId.of[List[String]].construct(Chunk("a", "b", "c"))
// res190: Either[String, Any] = Right(List("a", "b", "c"))
TypeId.of[Vector[Int]].construct(Chunk(1: Integer, 2: Integer, 3: Integer))
// res191: Either[String, Any] = Right(Vector(1, 2, 3))
TypeId.of[Set[String]].construct(Chunk("x", "y", "z"))
// res192: Either[String, Any] = Right(Set("x", "y", "z"))
```

Map types pass interleaved key-value pairs and fail on odd argument counts:

```scala
TypeId.of[Map[String, Int]].construct(Chunk("a", 1: Integer, "b", 2: Integer))
// res193: Either[String, Any] = Right(Map("a" -> 1, "b" -> 2))
```

##### Sum Types

Sum types have special calling conventions. `Option` accepts 1 element to construct `Some(value)`, or 0 elements to construct `None`:

```scala
TypeId.of[Option[String]].construct(Chunk("hello"))
// res194: Either[String, Any] = Right(Some("hello"))
TypeId.of[Option[String]].construct(Chunk())
// res195: Either[String, Any] = Right(None)
```

`Either` requires a Boolean flag as the first argument (`true` for `Right`, `false` for `Left`), followed by the value:

```scala
TypeId.of[Either[String, Int]].construct(Chunk(true: java.lang.Boolean, 42: Integer))
// res196: Either[String, Any] = Right(Right(42))
TypeId.of[Either[String, Int]].construct(Chunk(false: java.lang.Boolean, "error"))
// res197: Either[String, Any] = Right(Left("error"))
```

### Normalization and Equality

**Normalization** resolves type aliases and opaque type representations to their underlying concrete types. For example, `type Age = Int` normalizes to `Int`, and chained aliases like `type UserId = NonEmpty; type NonEmpty = List[Int]` both resolve to `List[Int]`. Opaque types such as `opaque type UserId = String` normalize to their representation. Normalization is crucial because multiple syntactic names often refer to the same underlying type, enabling deduplication and caching strategies.

**Structural Equality** compares two TypeIds by their normalized form, treating types with identical underlying structure as equal. Importantly, opaque types preserve their semantic identity even after normalization—`TypeId.of[UserId]` where `opaque type UserId = String` remains distinct from `TypeId.of[String]` for equality purposes, preserving runtime type safety. This distinction enables type-safe registries and validators that respect opaque type boundaries.

These concepts are essential for building type-indexed registries that recognize multiple alias names as referring to the same handler, implementing serialization strategies based on normalized type structure, and enforcing opaque type safety in type-indexed maps where different opaque types wrapping the same base type should have separate validators or handlers.

#### `TypeId.normalize` — Resolve Aliases

Resolves chains of type aliases to the underlying type. For example, `type MyList = List[Int]` normalizes to `List[Int]`:

```scala
object TypeId {
  def normalize(id: TypeId[?]): TypeId[?]
}
```

```scala

type Age = Int
```

```scala
val ageId = TypeId.of[Age]
// ageId: TypeId[Age] = Age
val norm = TypeId.normalize(ageId)
// norm: TypeId[_ >: Nothing <: AnyKind] = Int
norm.fullName
// res199: String = "scala.Int"
```

#### `TypeId.structurallyEqual` — Structural Equality

Checks if two TypeIds are structurally equal after normalization. Semantically equivalent to `==` on TypeId instances; `==` additionally short-circuits on hash mismatch for performance:

```scala
object TypeId {
  def structurallyEqual(a: TypeId[?], b: TypeId[?]): Boolean
}
```

```scala

type UserId = Int
```

```scala
val a = TypeId.of[UserId]
// a: TypeId[UserId] = UserId
val b = TypeId.of[Int]
// b: TypeId[Int] = Int
TypeId.structurallyEqual(a, b)
// res201: Boolean = true
a == b
// res202: Boolean = true
```

#### `TypeId.structuralHash` — Structural Hash Code

Computes a hash code based on the normalized structural representation:

```scala
object TypeId {
  def structuralHash(id: TypeId[?]): Int
}
```

#### `TypeId.unapplied` — Strip Type Arguments

Returns the type constructor by stripping all type arguments. For example, `TypeId.unapplied(TypeId.of[List[Int]])` returns the equivalent of `TypeId.of[List]`:

```scala
object TypeId {
  def unapplied(id: TypeId[?]): TypeId[?]
}
```

```scala
val listInt = TypeId.of[List[Int]]
// listInt: TypeId[List[Int]] = List[Int]
val unapplied = TypeId.unapplied(listInt)
// unapplied: TypeId[_ >: Nothing <: AnyKind] = List[+A]
unapplied.isApplied
// res203: Boolean = false
unapplied.name
// res204: String = "List"
```

### Pattern Matching Extractors

The companion object provides extractors for pattern matching on TypeId classification:

```scala

case class User(id: Long, email: String)
val userId = TypeId.of[User]
```

```scala
userId match {
  case TypeId.Nominal(name, owner, params, defKind, parents) =>
    s"Nominal type '$name' in ${owner.asString}"
  case TypeId.Alias(name, _, _, aliased) =>
    s"Alias '$name'"
  case TypeId.Opaque(name, _, _, repr, _) =>
    s"Opaque '$name'"
}
// res206: String = "Nominal type 'User' in repl.MdocSession.MdocApp205"
```

The extractors are:

| Extractor                                               | Matches                  |
|---------------------------------------------------------|--------------------------|
| `TypeId.Nominal(name, owner, params, defKind, parents)` | Classes, traits, objects |
| `TypeId.Alias(name, owner, params, aliased)`            | Type aliases             |
| `TypeId.Opaque(name, owner, params, repr, bounds)`      | Opaque types             |
| `TypeId.Sealed(name)`                                   | Sealed traits            |
| `TypeId.Enum(name, owner)`                              | Scala 3 enums            |

## TypeDefKind Reference

`TypeDefKind` classifies every type definition. Access it via the `defKind` property documented in [Core Operations](#type-classification).

The `defKind` property (documented in [Core Operations](#type-classification)) returns one of these variants. Use classification predicates like `isCaseClass`, `isSealed`, `isObject` for simple checks.

`TypeDefKind` has these variants:

| Variant                                              | Description                                  |
|------------------------------------------------------|----------------------------------------------|
| `Class(isFinal, isAbstract, isCase, isValue, bases)` | Class definitions                            |
| `Trait(isSealed, bases)`                             | Trait definitions                            |
| `Object(bases)`                                      | Singleton objects                            |
| `Enum(bases)`                                        | Scala 3 enums                                |
| `EnumCase(parentEnum, ordinal, isObjectCase)`        | Enum cases                                   |
| `TypeAlias`                                          | Type aliases (`type Foo = Bar`)              |
| `OpaqueType(publicBounds)`                           | Opaque types                                 |
| `AbstractType`                                       | Abstract type members                        |
| `Unknown`                                            | Unclassified or unresolvable type definition |

## Type Parameters and Generics

When you derive a TypeId for a generic type, the macro captures its type parameters (variance, bounds, kind) and any applied type arguments.

A **raw type constructor** is a generic type without any type arguments filled in. For example, `List` by itself (without `[Int]` or `[String]`) is a raw type constructor. Scala 3 supports deriving TypeIds directly for raw type constructors, but Scala 2 has restrictions due to its type system:

**Scala 3** allows you to work with raw type constructors directly:

```scala
// Scala 3 only
val listId = TypeId.of[List]  // Works: raw type constructor
```

**Scala 2** requires you to use a wildcard placeholder or implicit derivation since raw type constructors are not valid syntax:

```scala
// Scala 2 alternatives
val listId = TypeId.of[List[_]]  // Use wildcard type argument

// Or retrieve via implicit derivation
implicit val derived: TypeId[List[_]] = TypeId.of[List[_]]
```

This distinction matters when you need to capture the type constructor itself (for higher-kinded type operations) rather than concrete types like `List[Int]`.

### Inspecting Type Parameters

Define your own generic types and derive their TypeIds to see how type parameters are captured:

```scala

sealed trait Container[+A]
case class Box[+A](value: A) extends Container[A]

sealed trait Cache[K, +V]
case class LRUCache[K, +V](maxSize: Int) extends Cache[K, V]
```

A single-parameter type constructor:

```scala
val containerId = TypeId.of[Container]
// containerId: TypeId[[A >: Nothing <: Any] =>> Container[A]] = Container[+A]
containerId.typeParams
// res208: List[TypeParam] = List(
//   TypeParam(
//     name = "A",
//     index = 0,
//     variance = Covariant,
//     bounds = TypeBounds(lower = None, upper = None),
//     kind = Type
//   )
// )
containerId.arity
// res209: Int = 1
```

A two-parameter type constructor with mixed variance (invariant `K`, covariant `V`):

```scala
val cacheId = TypeId.of[Cache]
// cacheId: TypeId[[K >: Nothing <: Any, V >: Nothing <: Any] =>> Cache[K, V]] = Cache[K, +V]
cacheId.typeParams
// res210: List[TypeParam] = List(
//   TypeParam(
//     name = "K",
//     index = 0,
//     variance = Invariant,
//     bounds = TypeBounds(lower = None, upper = None),
//     kind = Type
//   ),
//   TypeParam(
//     name = "V",
//     index = 1,
//     variance = Covariant,
//     bounds = TypeBounds(lower = None, upper = None),
//     kind = Type
//   )
// )
cacheId.arity
// res211: Int = 2
```

Each `TypeParam` records the parameter's name, position, variance, bounds, and kind:

```scala
val containerParam = containerId.typeParams.head
// containerParam: TypeParam = TypeParam(
//   name = "A",
//   index = 0,
//   variance = Covariant,
//   bounds = TypeBounds(lower = None, upper = None),
//   kind = Type
// )
containerParam.name
// res212: String = "A"
containerParam.variance
// res213: Variance = Covariant
containerParam.kind
// res214: Kind = Type
containerParam.isCovariant
// res215: Boolean = true
```

```scala
val cacheParams = cacheId.typeParams
// cacheParams: List[TypeParam] = List(
//   TypeParam(
//     name = "K",
//     index = 0,
//     variance = Invariant,
//     bounds = TypeBounds(lower = None, upper = None),
//     kind = Type
//   ),
//   TypeParam(
//     name = "V",
//     index = 1,
//     variance = Covariant,
//     bounds = TypeBounds(lower = None, upper = None),
//     kind = Type
//   )
// )
cacheParams.map(p => (p.name, p.variance))
// res216: List[Tuple2[String, Variance]] = List(
//   ("K", Invariant),
//   ("V", Covariant)
// )
```

### Inspecting Type Arguments

When you derive a TypeId for an *applied* type (a generic type with concrete arguments), the type arguments are captured:

```scala
val boxIntId = TypeId.of[Box[Int]]
// boxIntId: TypeId[Box[Int]] = Box[Int]
boxIntId.typeArgs
// res217: List[TypeRepr] = List(Ref(Int))
boxIntId.isApplied
// res218: Boolean = true
```

```scala
val cacheStringIntId = TypeId.of[LRUCache[String, Int]]
// cacheStringIntId: TypeId[LRUCache[String, Int]] = LRUCache[String, Int]
cacheStringIntId.typeArgs
// res219: List[TypeRepr] = List(Ref(String), Ref(Int))
```

### Variance

**Variance** describes how a type parameter's subtyping relationships are preserved. **Covariant** types (`+`) preserve subtyping (if `B <: A` then `Container[B] <: Container[A]`), **contravariant** types (`-`) reverse it, and **invariant** types preserve neither. For example, `Container[+A]` is covariant—a `Container[String]` can be used where `Container[Any]` is expected. In contrast, `Cache[K, V]` where `K` is invariant means `Cache[String, Int]` cannot substitute for `Cache[Any, Int]` even if `String <: Any`.

Variance matters for type safety, polymorphism, and API design. TypeId captures variance information, enabling runtime inspection and validation:

```scala

sealed trait Container[+A]
sealed trait Cache[K, +V]
```

```scala
val containerParams = TypeId.of[Container].typeParams
// containerParams: List[TypeParam] = List(
//   TypeParam(
//     name = "A",
//     index = 0,
//     variance = Covariant,
//     bounds = TypeBounds(lower = None, upper = None),
//     kind = Type
//   )
// )
containerParams.map(p => (p.name, p.variance))
// res221: List[Tuple2[String, Variance]] = List(("A", Covariant))

val cacheParams = TypeId.of[Cache].typeParams
// cacheParams: List[TypeParam] = List(
//   TypeParam(
//     name = "K",
//     index = 0,
//     variance = Invariant,
//     bounds = TypeBounds(lower = None, upper = None),
//     kind = Type
//   ),
//   TypeParam(
//     name = "V",
//     index = 1,
//     variance = Covariant,
//     bounds = TypeBounds(lower = None, upper = None),
//     kind = Type
//   )
// )
cacheParams.map(p => (p.name, p.variance))
// res222: List[Tuple2[String, Variance]] = List(
//   ("K", Invariant),
//   ("V", Covariant)
// )
```

You can also work with variance values directly:

```scala
Variance.Covariant.symbol
// res223: String = "+"
Variance.Contravariant.symbol
// res224: String = "-"
Variance.Invariant.symbol
// res225: String = ""
Variance.Covariant.flip
// res226: Variance = Contravariant
```

### Kind

**Kind** describes the "type of a type"—it captures whether something is a concrete type or a type constructor, and how many type parameters it requires. A **proper type** like `Int` or `Box[String]` has kind `*` (zero parameters). A **unary type constructor** like `List` has kind `* -> *` (takes one type parameter). A **binary type constructor** like `Map` has kind `* -> * -> *` (takes two). **Higher-kinded types** like `Runnable[F[_]]` have kinds like `(* -> *) -> *` (takes a type constructor as a parameter). Kind information is essential for generic programming, enforcing API contracts, and enabling advanced patterns like monad transformers.

TypeId captures kind information at runtime, allowing you to inspect and validate the structure of types:

```scala

sealed trait Container[+A]
case class Box[+A](value: A) extends Container[A]

sealed trait Cache[K, +V]
case class LRUCache[K, +V](maxSize: Int) extends Cache[K, V]

trait Runnable[F[_]] {
  def run[A](fa: F[A]): A
}
```

A proper type (`*`) — fully concrete with no type parameters:

```scala
val boxIntId = TypeId.of[Box[Int]]
// boxIntId: TypeId[Box[Int]] = Box[Int]
boxIntId.isApplied
// res228: Boolean = true
boxIntId.arity
// res229: Int = 1
```

A unary type constructor (`* -> *`) — takes one type parameter:

```scala
val containerId = TypeId.of[Container]
// containerId: TypeId[[A >: Nothing <: Any] =>> Container[A]] = Container[+A]
containerId.arity
// res230: Int = 1
containerId.typeParams.map(p => (p.name, p.kind))
// res231: List[Tuple2[String, Kind]] = List(("A", Type))
```

A binary type constructor (`* -> * -> *`) — takes two type parameters:

```scala
val cacheId = TypeId.of[Cache]
// cacheId: TypeId[[K >: Nothing <: Any, V >: Nothing <: Any] =>> Cache[K, V]] = Cache[K, +V]
cacheId.arity
// res232: Int = 2
cacheId.typeParams.map(p => (p.name, p.kind))
// res233: List[Tuple2[String, Kind]] = List(("K", Type), ("V", Type))
```

A higher-kinded type (`(* -> *) -> *`) — a type parameter that itself is a type constructor:

```scala
val runnableId = TypeId.of[Runnable]
// runnableId: TypeId[[F >: Nothing <: [_$4 >: Nothing <: Any] =>> Any] =>> Runnable[F]] = Runnable[F]
runnableId.typeParams.head.kind
// res234: Kind = Type
runnableId.typeParams.head.kind.arity
// res235: Int = 0
```

| Kind                      | Notation        | Arity | Examples              |
|---------------------------|-----------------|-------|-----------------------|
| `Kind.Type` / `Kind.Star` | `*`             | 0     | `Int`, `Box[Int]`     |
| `Kind.Star1`              | `* -> *`        | 1     | `Container`, `Option` |
| `Kind.Star2`              | `* -> * -> *`   | 2     | `Cache`, `Either`     |
| `Kind.HigherStar1`        | `(* -> *) -> *` | 1     | `Runnable`            |

## Subtype Relationships

**Subtype relationships** determine if one type is a subtype of another, enabling type-safe polymorphism and dispatch at runtime. This is essential for checking if a value of one type can be used where another type is expected. TypeId handles direct inheritance, transitive inheritance chains, sealed trait cases, and variance-aware subtyping for generic types.

Three key methods work together to express the full range of type relationships:

```scala

sealed trait Animal
sealed trait Mammal extends Animal
case class Dog(name: String) extends Mammal
case class Cat(name: String) extends Mammal
case class Fish(species: String) extends Animal
```

**`isSubtypeOf`** checks if this type is a subtype of another (direct or transitive):

```scala
val dogId     = TypeId.of[Dog]
// dogId: TypeId[Dog] = Dog
val mammalId  = TypeId.of[Mammal]
// mammalId: TypeId[Mammal] = Mammal
val animalId  = TypeId.of[Animal]
// animalId: TypeId[Animal] = Animal
val fishId    = TypeId.of[Fish]
// fishId: TypeId[Fish] = Fish

// Direct inheritance: Dog extends Mammal
dogId.isSubtypeOf(mammalId)
// res237: Boolean = true

// Transitive inheritance: Dog extends Mammal extends Animal
dogId.isSubtypeOf(animalId)
// res238: Boolean = true

// Not a subtype relationship
dogId.isSubtypeOf(fishId)
// res239: Boolean = false
fishId.isSubtypeOf(mammalId)
// res240: Boolean = false
```

**`isSupertypeOf`** is the reverse—checks if this type is a supertype (parent) of another:

```scala
mammalId.isSupertypeOf(dogId)
// res241: Boolean = true
animalId.isSupertypeOf(dogId)
// res242: Boolean = true
dogId.isSupertypeOf(animalId)
// res243: Boolean = false
```

**`isEquivalentTo`** checks if two types are exactly the same:

```scala
dogId.isEquivalentTo(dogId)
// res244: Boolean = true
dogId.isEquivalentTo(mammalId)
// res245: Boolean = false
animalId.isEquivalentTo(animalId)
// res246: Boolean = true
```

**Variance-aware subtyping** for generic types respects covariance and contravariance. Covariant type constructors like `List[+A]` preserve subtyping relationships:

```scala
val listDogId    = TypeId.of[List[Dog]]
// listDogId: TypeId[List[Dog]] = List[Dog]
val listMammalId = TypeId.of[List[Mammal]]
// listMammalId: TypeId[List[Mammal]] = List[Mammal]
val listAnimalId = TypeId.of[List[Animal]]
// listAnimalId: TypeId[List[Animal]] = List[Animal]

listDogId.isSubtypeOf(listMammalId)
// res247: Boolean = true
listDogId.isSubtypeOf(listAnimalId)
// res248: Boolean = true
listMammalId.isSubtypeOf(listAnimalId)
// res249: Boolean = true
```

These methods are essential for building type-safe registries, implementing generic serializers that dispatch based on type hierarchy, and validating API contracts that require specific type relationships.

## Annotations

**Annotations** are metadata attached to types at compile time. TypeId captures them at runtime, making this metadata available for introspection, validation, and dispatch logic. Annotations enable building smart serializers, validators, and code generators that adjust their behavior based on type-level metadata.

TypeId exposes each annotation as an `Annotation` object containing the annotation's type and its arguments. This is essential for frameworks that need to read compile-time metadata (like JPA, validation libraries, or custom serialization frameworks) but want to remain generic and support multiple annotation schemes:

```scala

@transient
case class ImportantData(id: Int, payload: String)

case class Plain(x: Int)
```

Inspect annotations on a type:

```scala
val importantId = TypeId.of[ImportantData]
// importantId: TypeId[ImportantData] = ImportantData
importantId.annotations
// res251: List[Annotation] = List(
//   Annotation(typeId = transient, args = List())
// )
importantId.annotations.map(_.name)
// res252: List[String] = List("transient")

TypeId.of[Plain].annotations
// res253: List[Annotation] = List()
```

Annotations can have arguments and parameters. Create a custom annotation to see how arguments are captured:

```scala

// Custom annotation with parameters
case class ApiEndpoint(version: Int, deprecated: Boolean = false) extends scala.annotation.StaticAnnotation

@ApiEndpoint(version = 2, deprecated = true)
case class UserV2(id: String, name: String)
```

Derive the TypeId and inspect annotation arguments:

```scala
val userV2Id = TypeId.of[UserV2]
// userV2Id: TypeId[UserV2] = UserV2
userV2Id.annotations
// res255: List[Annotation] = List(
//   Annotation(
//     typeId = ApiEndpoint,
//     args = List(
//       Named(name = "version", value = Const(2)),
//       Named(name = "deprecated", value = Const(true))
//     )
//   )
// )
userV2Id.annotations.head.args
// res256: List[AnnotationArg] = List(
//   Named(name = "version", value = Const(2)),
//   Named(name = "deprecated", value = Const(true))
// )
```

Annotations are represented internally as instances of the `Annotation` data class. The `Annotation` contains the annotation's `TypeId` and a list of `AnnotationArg` values representing the arguments:

| Type                                           | Description                                        |
|------------------------------------------------|----------------------------------------------------|
| `Annotation(typeId, args)`                     | An annotation instance with its type and arguments |
| `AnnotationArg.Const(value)`                   | A constant value argument                          |
| `AnnotationArg.Named(name, value)`             | A named parameter                                  |
| `AnnotationArg.ArrayArg(values)`               | An array of arguments                              |
| `AnnotationArg.Nested(annotation)`             | A nested annotation                                |
| `AnnotationArg.ClassOf(tpe)`                   | A `classOf[T]` argument                            |
| `AnnotationArg.EnumValue(enumType, valueName)` | An enum constant                                   |

**Use cases:** Annotations are commonly used to drive serialization strategies, enforce validation rules, mark types for code generation, configure persistence metadata, or enable framework-specific behavior without requiring explicit configuration objects.

## Namespaces and Owners

Every type has an `owner` — the hierarchical path that tells you where the type is defined (its package, enclosing object, or enclosing type). Owner is essential when you need to identify types by their origin, filter schemas by namespace, or build type-indexed registries that respect module boundaries.

When building cross-module systems (middleware, gateways, plugin registries, code generators), you often need to distinguish between types from different packages or modules. For example, you might want to:
- Apply different serialization strategies to types from `com.internal.domain` vs `com.external.api`
- Build a type registry keyed by both type name *and* origin package (to handle name collisions across modules)
- Validate that a deserialized type comes from a trusted package

Owner gives you the tools to make these decisions at runtime.

### Inspecting Owners

When you derive a TypeId, the `owner` property captures where the type is defined:

```scala

case class MyType(x: Int)
```

```scala
val myId = TypeId.of[MyType]
// myId: TypeId[MyType] = MyType
myId.owner
// res258: Owner = Owner(
//   List(Package("repl"), Term("MdocSession"), Type("MdocApp257"))
// )
myId.owner.asString
// res259: String = "repl.MdocSession.MdocApp257"
myId.fullName
// res260: String = "repl.MdocSession.MdocApp257.MyType"
```

Owner provides methods to inspect and navigate the hierarchy:

```scala
myId.owner.parent
// res261: Owner = Owner(List(Package("repl"), Term("MdocSession")))
myId.owner.lastName
// res262: String = "MdocApp257"
myId.owner.isRoot
// res263: Boolean = false
```

### Owner Structure: Packages, Terms, and Types

An Owner is a chain of segments: packages, terms (objects/values), and types. For a type defined as:

```scala
package com.example
object Outer {
  class Inner
}
```

The owner of `Inner` has three segments: `com`, `example` (packages), and `Outer` (term):

```scala

object ExampleModule {
  case class Config(timeout: Int)
}
```

```scala
val configId = TypeId.of[ExampleModule.Config]
// configId: TypeId[Config] = Config
configId.owner.asString
// res265: String = "repl.MdocSession.MdocApp264.ExampleModule"
```

### TermPath

`TermPath` represents paths to term values and is used in TypeRepr expressions for singleton types (like `obj.field.type`). Singleton type information exists at compile time but is **erased at runtime** by the JVM — both `TypeId.of[HttpStatus.OK.type]` and `TypeId.of[HttpStatus.NotFound.type]` resolve to the same underlying `Int` TypeId. TermPath is useful in type representation structures and code generators that need to capture the compile-time singleton distinction for metaprogramming.

When the macro encounters a singleton type (a `TermRef` in Scala's reflection API), it recursively walks the qualifier chain to build the term path and stores it as `TypeRepr.Singleton(path)` for use in type expressions.

Derive TypeIds for singleton values to see them resolve to their underlying type:

```scala

object HttpStatus {
  val OK = 200
  val NotFound = 404
}
```

```scala
val okSingletonId = TypeId.of[HttpStatus.OK.type]
// okSingletonId: TypeId[OK] = Int
okSingletonId.name
// res267: String = "Int"

val notFoundSingletonId = TypeId.of[HttpStatus.NotFound.type]
// notFoundSingletonId: TypeId[NotFound] = Int
notFoundSingletonId.name
// res268: String = "Int"

okSingletonId == notFoundSingletonId
// res269: Boolean = true
```

**When to use TermPath:** In TypeRepr expressions and code generators that need to represent the compile-time path to a value. While singleton types are erased at runtime, TermPath captures this distinction for reflection and metaprogramming scenarios.

## TypeRepr — Type Expressions

`TypeRepr` represents type expressions in the Scala type system. This is fundamentally different from `TypeId`: while `TypeId` identifies a specific type *definition* (like `List` as a class or `Person` as a case class), `TypeRepr` represents how types are *composed and expressed* at runtime — as type arguments, parent types, intersections, unions, functions, and more.

**The Key Distinction:**

A `TypeId` answers the question **"What is this type definition?"** — for example, `TypeId.of[List]` gives you metadata about the `List` class itself. But a `TypeRepr` answers **"How is this type used in context?"** — for example, when you inspect `TypeId.of[List[Int]].typeArgs`, you get a `TypeRepr.Applied(Ref(TypeId.list), List(Ref(TypeId.int)))`, which describes that `List` is applied to the type argument `Int`.

**Practical Examples of the Difference:**

- `TypeId.list` identifies the `List` class definition
- `TypeRepr.Ref(TypeId.list)` is the expression "use List as a type" (standalone)
- `TypeRepr.Applied(TypeId.list, args)` is the expression "List applied to type arguments" (e.g., `List[Int]`)
- `TypeRepr.Function` represents `(A, B) => C` as a first-class type expression (not a method signature)
- `TypeRepr.Union` represents `A | B` without requiring a union type definition to exist

TypeRepr variants like `Intersection`, `Tuple`, `Union`, `TypeLambda`, and `ContextFunction` allow you to represent type expressions that may not have their own `TypeId` definitions — they are *computed expressions* in the type system rather than named definitions.

You encounter `TypeRepr` values when inspecting `typeArgs`, parent types in `defKind`, and alias targets:

```scala

```

When you derive a TypeId for an applied type, the `typeArgs` are `TypeRepr` values representing the type arguments:

```scala
TypeId.of[Int & String].typeArgs
// res271: List[TypeRepr] = List()
TypeId.of[Map[String, Int]].typeArgs
// res272: List[TypeRepr] = List(Ref(String), Ref(Int))
```

Here is a reference of the different `TypeRepr` variants you may encounter when inspecting TypeIds:

| Category     | Variant                                          | Example                                     |
|--------------|--------------------------------------------------|---------------------------------------------|
| **Common**   | `Ref(id)`                                        | `Int`, `String` — reference to a named type |
|              | `ParamRef(param, depth)`                         | `A` — reference to a type parameter         |
|              | `Applied(tycon, args)`                           | `List[Int]` — parameterized type            |
| **Compound** | `Intersection(types)`                            | `A & B` (Scala 3) or `A with B` (Scala 2)   |
|              | `Union(types)`                                   | `A \| B` (Scala 3)                          |
|              | `Tuple(elems)`                                   | `(A, B, C)`, named tuples                   |
|              | `Function(params, result)`                       | `(A, B) => C`                               |
|              | `ContextFunction(params, result)`                | `(A, B) ?=> C` (Scala 3)                    |
| **Special**  | `Singleton(path)`                                | `x.type`                                    |
|              | `ThisType(owner)`                                | `this.type`                                 |
|              | `TypeProjection(qualifier, name)`                | `Outer#Inner`                               |
|              | `TypeSelect(qualifier, name)`                    | `qual.Member`                               |
|              | `Structural(parents, members)`                   | `{ def foo: Int }`                          |
| **Advanced** | `TypeLambda(params, body)`                       | `[X] =>> F[X]`                              |
|              | `Wildcard(bounds)`                               | `?`, `? <: Upper`                           |
|              | `ByName(underlying)`                             | `=> A`                                      |
|              | `Repeated(element)`                              | `A*`                                        |
|              | `Annotated(underlying, annotations)`             | `A @anno`                                   |
|              | `Constant.*`                                     | `42`, `"foo"`, `true` (literal types)       |
| **Builtins** | `AnyType`, `NothingType`, `NullType`, `UnitType` | Special types                               |

## Erased TypeId

For type-indexed collections where the type parameter doesn't matter, erase it:

```scala

```

```scala
val erased: TypeId.Erased = TypeId.of[Int].erased
// erased: TypeId[Unknown] = Int
erased
// res274: TypeId[Unknown] = Int
```

Erased TypeIds are the key to building type-indexed maps:

```scala
val registry: Map[TypeId.Erased, String] = Map(
  TypeId.of[Int].erased    -> "Integer type",
  TypeId.of[String].erased -> "String type"
)
// registry: Map[Erased, String] = Map(
//   Int -> "Integer type",
//   String -> "String type"
// )

registry.get(TypeId.of[Int].erased)
// res275: Option[String] = Some("Integer type")
registry.get(TypeId.of[Double].erased)
// res276: Option[String] = None
```

## Predefined TypeIds

TypeId provides instances for common types:

**Core Interfaces:** `TypeId.charSequence` (`java.lang`), `comparable` (`java.lang`), `serializable` (`java.io`)

**Primitives:** `TypeId.unit`, `boolean`, `byte`, `short`, `int`, `long`, `float`, `double`, `char`, `string`, `bigInt`, `bigDecimal`

**Collections:** `TypeId.option`, `some`, `none`, `list`, `vector`, `set`, `seq`, `indexedSeq`, `map`, `either`, `array`, `arraySeq`, `chunk`

**java.time:** `TypeId.dayOfWeek`, `duration`, `instant`, `localDate`, `localDateTime`, `localTime`, `month`, `monthDay`, `offsetDateTime`, `offsetTime`, `period`, `year`, `yearMonth`, `zoneId`, `zoneOffset`, `zonedDateTime`

**java.util:** `TypeId.currency`, `uuid`

**Scala 3 only:** `TypeId.iarray` — `IArray[T]`, the immutable array type.

## Integration with Schema

TypeId is central to ZIO Blocks' schema system. Every `Reflect` node carries an associated TypeId:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}
```

You can access the TypeId from a schema's reflection:

```scala
val reflect = Schema[Person].reflect
val typeId = reflect.typeId
typeId.name
typeId.isCaseClass
```

### Schema Transformations

TypeId is automatically attached when transforming schemas. The `transform` method takes an implicit `TypeId[B]` parameter, so the TypeId for the target type is resolved at compile time:

```scala
case class Email(value: String)

object Email {
  implicit val schema: Schema[Email] = Schema[String]
    .transform(Email(_), _.value)
    // TypeId[Email] is resolved implicitly — no extra call needed
}
```

### Schema Derivation

The `Deriver` trait receives a `TypeId` for each node in the schema. Methods like `deriveRecord` and `deriveVariant` include a `typeId: TypeId[A]` parameter alongside fields/cases, bindings, documentation, modifiers, and more. This lets you inspect the type's structure, annotations, and relationships when generating code.

For details on the full `Deriver` API and how to implement custom derivers, see the [Type Class Derivation](./type-class-derivation.md) reference.

## Comparison with Alternatives

TypeId occupies a different niche from the reflection and type-tagging mechanisms in the Scala ecosystem:

| Feature                           | `TypeId` | `ClassTag` | `TypeTag` (Scala 2) | `TypeTest` (Scala 3) | `Mirror` (Scala 3) |
|-----------------------------------|----------|------------|---------------------|----------------------|--------------------|
| Preserves generic type args       | Yes      | No         | Yes                 | No                   | No                 |
| Distinguishes opaque types        | Yes      | No         | No                  | No                   | No                 |
| Available on Scala.js             | Yes      | Partial    | No                  | Yes                  | Yes                |
| Cross-version (2 & 3)             | Yes      | Yes        | Scala 2 only        | Scala 3 only         | Scala 3 only       |
| Pure data (no runtime reflection) | Yes      | No         | No                  | No                   | Yes                |
| Captures annotations              | Yes      | No         | Yes                 | No                   | No                 |
| Captures variance & kind          | Yes      | No         | Yes                 | No                   | No                 |
| Subtype relationship checks       | Yes      | No         | Yes                 | Yes                  | No                 |

**When to migrate from `ClassTag`:** If you only need `ClassTag` to create arrays of the correct runtime type, keep using it — TypeId does not replace that functionality. If you are using `ClassTag` to identify or dispatch on types, TypeId provides strictly more information (generics, opaque types, annotations) and works identically on JVM and Scala.js.

**When to migrate from `TypeTag` / `WeakTypeTag`:** These are Scala 2-only, depend on `scala-reflect`, and are not available on Scala.js. TypeId captures comparable metadata (full name, type arguments, variance, annotations) as a pure data structure without runtime reflection, and works across Scala 2, Scala 3, JVM, and Scala.js.

**When to migrate from `TypeTest`:** `TypeTest` is a Scala 3 mechanism for safe pattern matching on types. It answers "is this value an instance of T?" but does not expose type structure, annotations, or generic arguments. Use TypeId when you need to inspect or serialize type metadata, not just test membership.

**When to migrate from `Mirror`:** `Mirror` provides structural information about products and sums for derivation in Scala 3. TypeId complements `Mirror` by adding namespace information (owner/package), annotations, opaque type support, and cross-version compatibility. In ZIO Blocks, the schema derivation system uses TypeId rather than `Mirror`.

## Running the Examples

All code from this guide is available as runnable examples in the `schema-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Basic Usage

Demonstrates deriving TypeIds for case classes, accessing their properties (name, fullName, owner, arity), using predefined TypeIds for built-in types, and implicit derivation:

```scala title="schema-examples/src/main/scala/typeid/TypeIdBasicExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package typeid

/**
 * TypeId Basic Example
 *
 * Demonstrates deriving TypeIds for case classes, accessing their properties,
 * and using predefined TypeIds for built-in types.
 *
 * Run with: sbt "schema-examples/runMain typeid.TypeIdBasicExample"
 */

object TypeIdBasicExample extends App {

  println("═══════════════════════════════════════════════════════════════")
  println("TypeId Basic Example")
  println("═══════════════════════════════════════════════════════════════\n")

  // Derive TypeId for a case class
  case class Person(name: String, age: Int)

  // Simple derivation
  val personId = TypeId.of[Person]

  println("--- Deriving TypeId for Person ---\n")

  // Accessing name and fullName
  println("TypeId.of[Person].name")
  show(personId.name)

  // Accessing fully qualified name
  println("TypeId.of[Person].fullName")
  show(personId.fullName)

  // Checking if it's a case class
  println("TypeId.of[Person].isCaseClass")
  show(personId.isCaseClass)

  // Accessing the owner (package/enclosing type)
  println("TypeId.of[Person].owner")
  show(personId.owner)

  println("\n--- Type Classification ---\n")

  // Check the kind (arity)
  println("TypeId.of[Person].arity")
  show(personId.arity)

  // Check it's a proper type
  println("TypeId.of[Person].isProperType")
  show(personId.isProperType)

  // Check it's not a type constructor
  println("TypeId.of[Person].isTypeConstructor")
  show(personId.isTypeConstructor)

  println("\n--- Predefined TypeIds ---\n")

  // Use predefined TypeIds for built-in types
  println("TypeId.int.fullName")
  show(TypeId.int.fullName)

  println("TypeId.string.fullName")
  show(TypeId.string.fullName)

  println("TypeId.list.name")
  show(TypeId.list.name)

  // List is a type constructor (arity = 1)
  println("TypeId.list.arity")
  show(TypeId.list.arity)

  println("TypeId.map.arity")
  show(TypeId.map.arity)

  println("\n--- Classification Predicates ---\n")

  // Check various classification predicates
  println("TypeId.option.isTypeConstructor")
  show(TypeId.option.isTypeConstructor)

  println("TypeId.either.arity")
  show(TypeId.either.arity)

  println("\n--- Implicit Derivation ---\n")

  // Implicit derivation
  val personIdImplicit: TypeId[Person] = implicitly[TypeId[Person]]

  println("implicitly[TypeId[Person]].name")
  show(personIdImplicit.name)

  println("\n═══════════════════════════════════════════════════════════════")

  show(
    TypeId.of[scala.util.Either].isSum
  )

  show(
    TypeId.of[Option[String]].isOption
  )
  show(
    TypeId.of[Either[String, Int]].isEither
  )

  show(
    TypeId.of[Option[String]].isSum
  )

  show(
    TypeId.of[Either[String, Int]].isSum
  )

  show(
    TypeId.of[(Int, String)].isProduct
  )

  show(
    TypeId.of[Tuple2[Int, String]].isProduct
  )

  show(
    TypeId.of[Product2[Int, String]].isProduct
  )

  show(
    TypeId.of[(Int, String, Boolean)].isTuple
  )

  sealed trait Animal

  sealed trait Mammal extends Animal

  case class Dog(name: String) extends Mammal

  case class Cat(name: String) extends Mammal

  case class Fish(species: String) extends Animal

  val dogId    = TypeId.of[Dog]
  val mammalId = TypeId.of[Mammal]
  val animalId = TypeId.of[Animal]
  val fishId   = TypeId.of[Fish]

  // Direct inheritance: Dog extends Mammal
  dogId.isSubtypeOf(mammalId)

  // Transitive inheritance: Dog extends Mammal extends Animal
  dogId.isSubtypeOf(animalId)

  // Not a subtype relationship
  dogId.isSubtypeOf(fishId)
  fishId.isSubtypeOf(mammalId)

  TypeId.of[List[Dog]].isSubtypeOf(TypeId.of[List[Mammal]])
  TypeId.of[List[Dog]].isSubtypeOf(TypeId.of[List[Animal]])

  show(
    TypeId.of[Mammal => String].isSupertypeOf(TypeId.of[Dog => String])
  )

  // A generic parent type with concrete type arguments
  case class StringList() extends scala.collection.mutable.ListBuffer[String]

  // A type with multiple generic parents
  case class Entry[K, V](key: K, value: V) extends scala.collection.Map[K, V] {
    def iterator = Iterator((key, value))

    def get(k: K) = if (k == key) Some(value) else None

    override def -(key: K): collection.Map[K, V] = ???

    override def -(key1: K, key2: K, keys: K*): collection.Map[K, V] = ???
  }

  val stringListId = TypeId.of[StringList]
  // StringList extends ListBuffer[String] - the type argument is captured
  show(
    stringListId.parents
  )

  val entryId = TypeId.of[Entry[String, Int]]
  // Entry[String, Int] extends Map[String, Int] - type arguments are preserved

  show(
    entryId.parents
  )

  // A trait can extend multiple traits
  trait Swimmer {
    def swim(): Unit = ()
  }

  trait Flyer {
    def fly(): Unit = ()
  }

  trait Duck extends Swimmer with Flyer

  // A case class can extend a trait
  case class MallardDuck() extends Duck

  show(
    TypeId.of[MallardDuck].parents
  )

  def isPrimitive(id: TypeId[?]): Boolean =
    id.classTag != scala.reflect.ClassTag.AnyRef

  show {
    isPrimitive(TypeId.of[Int])
    isPrimitive(TypeId.of[Double])
    isPrimitive(TypeId.of[Boolean])
    isPrimitive(TypeId.of[String])
    isPrimitive(TypeId.of[List[Int]])
  }

  def makeStorage(size: Int, id: TypeId[?]): Array[?] =
    id.classTag.newArray(size)

  show {
    makeStorage(100, TypeId.int).getClass.getComponentType
    makeStorage(100, TypeId.double).getClass.getComponentType
    makeStorage(100, TypeId.string).getClass.getComponentType
  }

}
```

([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/typeid/TypeIdBasicExample.scala))

```bash
sbt "schema-examples/runMain typeid.TypeIdBasicExample"
```

### Subtype Relationships

Demonstrates subtype checking with `isSubtypeOf`, `isSupertypeOf`, and `isEquivalentTo`, including direct inheritance, transitive inheritance, sealed trait cases, and variance-aware subtyping for applied types like `List[Dog] <: List[Animal]`:

```scala title="schema-examples/src/main/scala/typeid/TypeIdSubtypingExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package typeid

/**
 * TypeId Subtyping Example
 *
 * Demonstrates subtype relationships, checking inheritance, and variance-aware
 * subtyping for applied types.
 *
 * Run with: sbt "schema-examples/runMain typeid.TypeIdSubtypingExample"
 */

object TypeIdSubtypingExample extends App {

  println("═══════════════════════════════════════════════════════════════")
  println("TypeId Subtyping Example")
  println("═══════════════════════════════════════════════════════════════\n")

  // Define a sealed trait hierarchy
  sealed trait Animal
  case class Dog(name: String) extends Animal
  case class Cat(name: String) extends Animal
  case object Bird             extends Animal

  println("--- Direct Inheritance ---\n")

  val dogId    = TypeId.of[Dog]
  val animalId = TypeId.of[Animal]

  // Check if Dog is a subtype of Animal
  println("TypeId.of[Dog].isSubtypeOf(TypeId.of[Animal])")
  show(dogId.isSubtypeOf(animalId))

  // Check if Animal is a supertype of Dog
  println("TypeId.of[Animal].isSupertypeOf(TypeId.of[Dog])")
  show(animalId.isSupertypeOf(dogId))

  // Check equivalence
  println("TypeId.of[Dog].isEquivalentTo(TypeId.of[Dog])")
  show(dogId.isEquivalentTo(dogId))

  println("TypeId.of[Dog].isEquivalentTo(TypeId.of[Animal])")
  show(dogId.isEquivalentTo(animalId))

  println("\n--- Transitive Inheritance ---\n")

  val catId = TypeId.of[Cat]

  // Both Dog and Cat are subtypes of Animal
  println("TypeId.of[Cat].isSubtypeOf(TypeId.of[Animal])")
  show(catId.isSubtypeOf(animalId))

  println("TypeId.of[Dog].isSubtypeOf(TypeId.of[Animal]) && TypeId.of[Cat].isSubtypeOf(TypeId.of[Animal])")
  show(dogId.isSubtypeOf(animalId) && catId.isSubtypeOf(animalId))

  println("\n--- Sealed Trait Cases ---\n")

  val birdId = TypeId.of[Bird.type]

  println("TypeId.of[Bird.type].isSubtypeOf(TypeId.of[Animal])")
  show(birdId.isSubtypeOf(animalId))

  println("\n--- Applied Type Subtyping (Covariance) ---\n")

  // List is covariant in its type parameter
  val listDogId    = TypeId.of[List[Dog]]
  val listAnimalId = TypeId.of[List[Animal]]

  // Due to covariance, List[Dog] is a subtype of List[Animal]
  println("TypeId.of[List[Dog]].isSubtypeOf(TypeId.of[List[Animal]])")
  show(listDogId.isSubtypeOf(listAnimalId))

  println("\n--- Applied Type Non-Subtyping (Invariance) ---\n")

  // Array is invariant in its type parameter (on Scala runtime)
  val arrayDogId    = TypeId.of[Array[Dog]]
  val arrayAnimalId = TypeId.of[Array[Animal]]

  println("TypeId.of[Array[Dog]].isSubtypeOf(TypeId.of[Array[Animal]])")
  show(arrayDogId.isSubtypeOf(arrayAnimalId))

  println("\n--- Type Parameters ---\n")

  // Check the type parameters of a generic type
  println("TypeId.of[List[Dog]].typeArgs")
  show(listDogId.typeArgs)

  println("TypeId.of[Map[String, Animal]].typeArgs")
  show(TypeId.of[Map[String, Animal]].typeArgs)

  println("\n═══════════════════════════════════════════════════════════════")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/typeid/TypeIdSubtypingExample.scala))

```bash
sbt "schema-examples/runMain typeid.TypeIdSubtypingExample"
```

### Normalization and Registries

Demonstrates type alias handling, normalization to underlying types, structural equality, and building type-indexed registries using erased TypeIds:

```scala title="schema-examples/src/main/scala/typeid/TypeIdNormalizationExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package typeid

/**
 * TypeId Normalization Example
 *
 * Demonstrates type alias handling, normalization, and type-indexed registries
 * using erased TypeIds.
 *
 * Run with: sbt "schema-examples/runMain typeid.TypeIdNormalizationExample"
 */

object TypeIdNormalizationExample extends App {

  println("═══════════════════════════════════════════════════════════════")
  println("TypeId Normalization Example")
  println("═══════════════════════════════════════════════════════════════\n")

  // Define some type aliases
  type UserId = Long
  type Email  = String
  type Age    = Int

  println("--- Type Aliases ---\n")

  // Create TypeIds for the aliases
  val userIdAlias = TypeId.alias[UserId](
    name = "UserId",
    owner = Owner.Root,
    typeParams = Nil,
    aliased = TypeRepr.Ref(TypeId.long)
  )

  println("userIdAlias.name")
  show(userIdAlias.name)

  println("userIdAlias.fullName")
  show(userIdAlias.fullName)

  println("\n--- Normalization ---\n")

  // Normalize the alias to its underlying type
  val normalized = TypeId.normalize(userIdAlias)

  println("TypeId.normalize(userIdAlias).name")
  show(normalized.name)

  println("TypeId.normalize(userIdAlias).fullName")
  show(normalized.fullName)

  println("\n--- Equality with Normalization ---\n")

  // Structural equality (not considering aliases)
  val anotherUserIdAlias = TypeId.alias[UserId](
    name = "UserId",
    owner = Owner.Root,
    typeParams = Nil,
    aliased = TypeRepr.Ref(TypeId.long)
  )

  println("userIdAlias == anotherUserIdAlias")
  show(userIdAlias == anotherUserIdAlias)

  println("TypeId.normalize(userIdAlias) == TypeId.long")
  show(TypeId.normalize(userIdAlias) == TypeId.long)

  println("\n--- Erased TypeIds for Registries ---\n")

  // Erased TypeIds are useful for type-indexed maps
  val intErased: TypeId.Erased     = TypeId.int.erased
  val stringErased: TypeId.Erased  = TypeId.string.erased
  val listIntErased: TypeId.Erased = TypeId.of[List[Int]].erased

  println("TypeId.int.erased")
  show(intErased)

  println("TypeId.string.erased")
  show(stringErased)

  println("TypeId.of[List[Int]].erased")
  show(listIntErased)

  println("\n--- Type Registry Using Erased TypeIds ---\n")

  // Build a type registry
  val registry: Map[TypeId.Erased, String] = Map(
    TypeId.int.erased           -> "Integer type",
    TypeId.string.erased        -> "String type",
    TypeId.long.erased          -> "Long type",
    TypeId.of[List[Int]].erased -> "List of integers"
  )

  println("registry.get(TypeId.int.erased)")
  show(registry.get(TypeId.int.erased))

  println("registry.get(TypeId.string.erased)")
  show(registry.get(TypeId.string.erased))

  println("registry.get(TypeId.of[List[Int]].erased)")
  show(registry.get(TypeId.of[List[Int]].erased))

  println("\n--- Querying the Registry ---\n")

  // Look up types in the registry
  val intType    = TypeId.of[Int].erased
  val doubleType = TypeId.of[Double].erased

  println("registry.get(intType)")
  show(registry.get(intType))

  println("registry.get(doubleType)")
  show(registry.get(doubleType))

  println("\n═══════════════════════════════════════════════════════════════")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/typeid/TypeIdNormalizationExample.scala))

```bash
sbt "schema-examples/runMain typeid.TypeIdNormalizationExample"
```

### Opaque Types

Demonstrates how TypeId preserves the semantic distinction of opaque types, enabling runtime type safety that pure Scala reflection cannot provide. Shows building type-indexed validator registries keyed by opaque type identity:

```scala title="schema-examples/src/main/scala/typeid/OpaqueTypesExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package typeid

/**
 * Opaque Types Example
 *
 * Demonstrates how TypeId preserves the semantic distinction of opaque types,
 * enabling runtime type safety that pure Scala reflection cannot provide.
 *
 * Opaque types allow you to create distinct types that share the same
 * representation at runtime. TypeId captures this distinction, making it
 * possible to:
 *   - Distinguish between different opaque types wrapping the same base type
 *   - Build type-indexed registries that respect opaque type boundaries
 *   - Implement runtime validators specific to each opaque type
 *
 * Run with: sbt "schema-examples/runMain typeid.OpaqueTypesExample"
 */

object OpaqueTypesExample extends App {

  println("═══════════════════════════════════════════════════════════════")
  println("Opaque Types with TypeId")
  println("═══════════════════════════════════════════════════════════════\n")

  // Define domain-specific opaque types wrapping String
  // In a real application, these would enforce different validation rules
  opaque type UserId       = String
  opaque type Email        = String
  opaque type SessionToken = String

  println("--- Opaque Type Definitions ---\n")
  println("opaque type UserId = String")
  println("opaque type Email = String")
  println("opaque type SessionToken = String\n")

  // Derive TypeIds for each opaque type
  val userIdType       = TypeId.of[UserId]
  val emailType        = TypeId.of[Email]
  val sessionTokenType = TypeId.of[SessionToken]
  val stringType       = TypeId.string

  println("--- TypeId Derivation ---\n")

  println("TypeId.of[UserId].name")
  show(userIdType.name)

  println("TypeId.of[Email].name")
  show(emailType.name)

  println("TypeId.of[SessionToken].name")
  show(sessionTokenType.name)

  println("\n--- Opaque Types vs Base Type ---\n")

  // Key insight: opaque types are distinct from their representation type
  println("TypeId.of[UserId].isEquivalentTo(TypeId.string)")
  show(userIdType.isEquivalentTo(stringType))

  println("TypeId.of[Email].isEquivalentTo(TypeId.string)")
  show(emailType.isEquivalentTo(stringType))

  println("TypeId.of[SessionToken].isEquivalentTo(TypeId.string)")
  show(sessionTokenType.isEquivalentTo(stringType))

  println("\n--- Opaque Types are Distinct from Each Other ---\n")

  println("TypeId.of[UserId].isEquivalentTo(TypeId.of[Email])")
  show(userIdType.isEquivalentTo(emailType))

  println("TypeId.of[Email].isEquivalentTo(TypeId.of[SessionToken])")
  show(emailType.isEquivalentTo(sessionTokenType))

  println("TypeId.of[UserId].isEquivalentTo(TypeId.of[SessionToken])")
  show(userIdType.isEquivalentTo(sessionTokenType))

  println("\n--- Real-World Use Case: Type-Safe Registry ---\n")

  // Define validators for each opaque type
  trait Validator {
    def validate(value: String): Boolean
    def errorMessage: String
  }

  val userIdValidator = new Validator {
    def validate(value: String): Boolean = value.nonEmpty && value.forall(_.isDigit)
    def errorMessage                     = "UserId must be non-empty digits"
  }

  val emailValidator = new Validator {
    def validate(value: String): Boolean = value.contains("@") && value.contains(".")
    def errorMessage                     = "Email must contain @ and ."
  }

  val sessionTokenValidator = new Validator {
    def validate(value: String): Boolean = value.length >= 32
    def errorMessage                     = "SessionToken must be at least 32 characters"
  }

  // Build a type-indexed registry of validators
  // This demonstrates the power of TypeId: we can safely dispatch
  // to different validators based on opaque type identity
  val validatorRegistry: Map[TypeId.Erased, Validator] = Map(
    TypeId.of[UserId].erased       -> userIdValidator,
    TypeId.of[Email].erased        -> emailValidator,
    TypeId.of[SessionToken].erased -> sessionTokenValidator
  )

  println("Built validator registry keyed by opaque type")
  println("Validators can enforce different validation rules per type\n")

  // Demonstrate validation dispatch
  def validateString(value: String, typeId: TypeId[_]): Boolean =
    validatorRegistry
      .get(typeId.erased)
      .map(_.validate(value))
      .getOrElse {
        println(s"No validator found for type: ${typeId.fullName}")
        false
      }

  def getValidationError(typeId: TypeId[_]): String =
    validatorRegistry
      .get(typeId.erased)
      .map(_.errorMessage)
      .getOrElse("Unknown validator")

  println("--- Validation Examples ---\n")

  val testUserId = "12345"
  val testEmail  = "user@example.com"
  val testToken  = "a" * 32

  println(s"Validating UserId: '$testUserId'")
  if (validateString(testUserId, TypeId.of[UserId])) {
    println("✓ Valid UserId\n")
  } else {
    println(s"✗ Invalid: ${getValidationError(TypeId.of[UserId])}\n")
  }

  println(s"Validating Email: '$testEmail'")
  if (validateString(testEmail, TypeId.of[Email])) {
    println("✓ Valid Email\n")
  } else {
    println(s"✗ Invalid: ${getValidationError(TypeId.of[Email])}\n")
  }

  println(s"Validating SessionToken: '$testToken'")
  if (validateString(testToken, TypeId.of[SessionToken])) {
    println("✓ Valid SessionToken\n")
  } else {
    println(s"✗ Invalid: ${getValidationError(TypeId.of[SessionToken])}\n")
  }

  println("--- What Pure Scala Cannot Do ---\n")

  println("With pure Scala reflection:")
  println("- classOf[UserId] == classOf[String]  (erased at runtime)")
  println("- classOf[Email] == classOf[String]   (erased at runtime)")
  println("- You cannot distinguish opaque types from their base type\n")

  println("With TypeId:")
  println("- TypeId.of[UserId] != TypeId.of[String]  (preserved)")
  println("- TypeId.of[Email] != TypeId.of[String]   (preserved)")
  println("- You can build type-safe validators and registries\n")

  println("═══════════════════════════════════════════════════════════════")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/typeid/OpaqueTypesExample.scala))

```bash
sbt "schema-examples/runMain typeid.OpaqueTypesExample"
```
