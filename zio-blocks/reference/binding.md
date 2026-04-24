# The Binding Data Type

> `Binding` is a sealed trait in ZIO Blocks that provides the operational machinery for constructing and deconstructing values of schema-described types. While `Reflect` describes the **structure** of data types, `Binding` provides the **behavior** needed to work with those types at runtime.

`Binding` is a sealed trait in ZIO Blocks that provides the operational machinery for constructing and deconstructing values of schema-described types. While `Reflect` describes the **structure** of data types, `Binding` provides the **behavior** needed to work with those types at runtime.

In other words, a binding is used to attach non-serializable Scala functions, such as **constructors**, **deconstructors**, and **matchers**, to a reflection type.

The combination of `Reflect` and `Binding` forms the foundation of `Schema`: a bound reflect (one where `F[_, _] = Binding`) that can both describe and manipulate data.

```scala
sealed trait Binding[T <: BindingType, A]
```

The `Binding` trait is parameterized by:

- **`T <: BindingType`**: A phantom type that identifies the kind of binding (Record, Variant, Seq, Map, etc.)
- **`A`**: The Scala type that this binding operates on

## The Role of Binding in the Schema Architecture

ZIO Blocks separates operational behavior from structural metadata using two key abstractions:

1. **Reflect** (`Reflect[F, A]`): A generic data structure describing the shape of type `A`. It's parameterized by `F[_, _]`, which can be plugged with different "binding strategies".
2. **Binding**: The concrete binding strategy that embeds construction/deconstruction capabilities within a `Reflect` structure. The binding is the operational behavior attached to the reflect.

When `F[_, _] = Binding`, you get a **bound reflect** that can actually construct and deconstruct values. When `F[_, _] = NoBinding`, you get an **unbound reflect** that contains only structural metadata and is fully **serializable**.

```scala
// Type aliases for clarity
type Reflect.Bound[A] = Reflect[Binding, A]     // Can construct/deconstruct A
type Reflect.Unbound[A] = Reflect[NoBinding, A] // Pure metadata, serializable
```

A `Schema[A]` is simply a wrapper around `Reflect.Bound[A]`:

```scala
final class Schema[A](val reflect: Reflect.Bound[A])
```

## Binding Variants

The `Binding` sealed trait has several case class variants, each corresponding to a different kind of `Reflect` node:

### 1. `Binding.Record`

Provides construction and deconstruction capabilities for product types—case classes, tuples, and any type composed of multiple named or positional fields.

```scala
final case class Record[A](
  constructor: Constructor[A],
  deconstructor: Deconstructor[A],
  defaultValue: Option[() => A] = None,
  examples: collection.immutable.Seq[A] = Nil
) extends Binding[BindingType.Record, A]
```

**Components:**

- `constructor`: Builds an `A` from primitive components stored in `Registers`
- `deconstructor`: Breaks down an `A` into primitive components in `Registers`
- `defaultValue`: An optional thunk `() => A` that produces a default instance. This is useful for formats like Protocol Buffers where missing fields assume default values.
- `examples`: Sample values for documentation or testing, or OpenAPI schema generation.

Here is an example of a `Binding.Record` for a simple `Person` case class:

```scala

case class Person(name: String, age: Int)

val initialRegisters = RegisterOffset(objects = 1, ints = 1)

val personRecord: Binding.Record[Person] =
  Binding.Record[Person](
    constructor = new Constructor[Person] {
      def usedRegisters = initialRegisters

      def construct(in: Registers, offset: RegisterOffset): Person =
        Person(
          in.getObject(offset).asInstanceOf[String],
          in.getInt(offset + RegisterOffset(objects = 1))
        )
    },
    deconstructor = new Deconstructor[Person] {

      def usedRegisters = initialRegisters

      def deconstruct(out: Registers, offset: RegisterOffset, p: Person): Unit = {
        out.setObject(offset, p.name)
        out.setInt(offset + RegisterOffset(objects = 1), p.age)
      }
    }
  )
```

### 2. `Binding.Variant`

The `Binding.Variant` data type provides discrimination capabilities and matchers for sum types—sealed traits, Scala 3 enums, and any type that represents one of several possible alternatives:

```scala
final case class Variant[A](
  discriminator: Discriminator[A],
  matchers: Matchers[A],
  defaultValue: Option[() => A] = None,
  examples: collection.immutable.Seq[A] = Nil
) extends Binding[BindingType.Variant, A]
```

**Discriminator** and **Matchers** are complementary components in ZIO Blocks that work together to handle sum types (ADTs/sealed traits/enums):

- `Discriminator`: Given a value of a sum type, determine which case it belongs to by returning a numerical index.
- `Matchers`: Given a value and a case index, safely downcast the value to the specific case type, or return null if it doesn't match.

```scala

sealed trait Shape extends Product with Serializable
case class Circle(radius: Double) extends Shape
case class Rectangle(width: Double, height: Double) extends Shape

val shapeBinding = Binding.Variant[Shape](
  discriminator = new Discriminator[Shape] {
    override def discriminate(a: Shape): Int = a match {
      case Circle(_) => 0
      case Rectangle(_, _) => 1
    }
  },
  matchers = Matchers(
    new Matcher[Circle] {
      override def downcastOrNull(any: Any): Circle =
        any match {
          case c: Circle => c
          case _ => null.asInstanceOf[Circle]
        }
    },
    new Matcher[Rectangle] {
      override def downcastOrNull(any: Any): Rectangle =
        any match {
          case r: Rectangle => r
          case _ => null.asInstanceOf[Rectangle]
        }
    }
  )
)
```

Please note that the `Variant` is only responsible for discrimination and downcasting. The actual construction and deconstruction of each case is handled by the individual case's `Binding.Record` (or other appropriate binding).

### 3. Binding.Seq

The `Binding.Seq` data type provides efficient construction and deconstruction capabilities for sequence/collection types—`List`, `Vector`, `Array`, `Set`, `Chunk`, and any ordered collection:

```scala
final case class Seq[C[_], A](
  constructor: SeqConstructor[C],
  deconstructor: SeqDeconstructor[C],
  defaultValue: Option[() => C[A]] = None,
  examples: collection.immutable.Seq[C[A]] = Nil
) extends Binding[BindingType.Seq[C], C[A]]
```

The type parameter is `C[_]` (the collection type constructor) rather than `C[A]` (a specific element type). This allows a single `Binding.Seq[List]` to work for `List[Int]`, `List[String]`, `List[Person]`, etc. Inside the companion object of `Binding.Seq`, there are implementations for standard Scala collections like `List`, `Vector`, `Seq`, `IndexedSeq`, `Set`, and `ArraySeq`.

Sequences are ubiquitous in data modeling, and their efficient handling is critical for performance. The `Seq` binding abstracts over the specific collection type using the **builder pattern**, enabling both stacked and heap-allocated construction strategies.

- `seqConstructor` is a sequence constructor which provides how to build the sequence of type `C[_]`. It provides collection-specific builder operations. Different collections have different constructor strategies (e.g., `Array` needs size upfront and is stack-allocated, while `List` can be built incrementally on the heap).
- `seqDeconstructor` is a sequence deconstructor that provides iteration and size information for the collection type `C[_]`, which enables us to tear down the collection into its elements efficiently. The size hints enable codecs to pre-allocate output buffers and builders.

Let's take a deep dive into these two main components.

#### SeqConstructor

The `SeqConstructor` has two main variants:

1. **Heap-allocated Constructors** which are for collections like `List`, `Vector`, `Set`, etc. These collections typically use mutable builders under the hood to efficiently accumulate elements before producing an immutable collection. The `Boxed` abstract class is a convenient base class for heap-allocated constructors that uses a single builder type for all primitive types. This makes it easy to implement sequence constructors for collections of heap-allocated objects:

```scala
abstract class Boxed[C[_]] extends SeqConstructor[C] {
  override type BooleanBuilder = ObjectBuilder[Boolean]
  override type ByteBuilder = ObjectBuilder[Byte]
  // ... all primitive builders are aliases to ObjectBuilder

  def addBoolean(builder: BooleanBuilder, a: Boolean): Unit = addObject(builder, a)
  // primitives get boxed when passed to addObject

  // ... similarly for other primitive adders
}
```

To implement a boxed sequence, we have to define just four things: the builder type, how to create a new builder, how to add elements to it, and how to produce the final collection.

ZIO Blocks provides `SeqConstructor.Boxed` implementations for standard Scala collections like `List`, `Vector`, `Set`, `IndexedSeq` and `Seq`. Here is an example of a `SeqConstructor.Boxed` for `List`:

```scala
val listConstructor: SeqConstructor[List] = new Boxed[List] {
  type ObjectBuilder[A] = scala.collection.mutable.ListBuffer[A]

  def newObjectBuilder[A](sizeHint: Int): ObjectBuilder[A] = new ListBuffer[A]

  def addObject[A](builder: ObjectBuilder[A], a: A): Unit = builder.addOne(a)

  def resultObject[A](builder: ObjectBuilder[A]): List[A] = builder.toList
}
```

2. **Stack-allocated Constructors** which are for collections like `Array`, `ArraySeq`, and `IArray`.

All the implementations are found in the companion object of the `SeqConstructor` trait.

#### SeqDeconstructor

The `SeqDeconstructor` has a simple interface with two methods:

```scala
trait SeqDeconstructor[C[_]] {
  def deconstruct[A](c: C[A]): Iterator[A]

  def size[A](c: C[A]): Int
}
```

The `deconstruct` method provides an iterator over the elements of the collection, while the `size` method returns the number of elements.

Inside the companion object of the `SeqDeconstructor` there are implementations for standard Scala collections like `List`, `Vector`, `Seq`, and `IndexedSeq`.

For efficiency, there are specialized implementations for array-based sequences like `Array`, `ArraySeq`, and `IArray` which provide direct access to elements, besides the iterator-based approach. As a result, they are much more performant than the generic iterator-based deconstruction. When an `Optic` needs to access a specific element by index, it can use these specialized methods to avoid the overhead of iterator traversal.

This is done through the `SpecializedIndexed` trait:

```scala
sealed trait SpecializedIndexed[C[_]] extends SeqDeconstructor[C] {
  def elementType[A](c: C[A]): RegisterType[A]
  def objectAt[A](c: C[A], index: Int): A
  def booleanAt(c: C[Boolean], index: Int): Boolean
  def byteAt(c: C[Byte], index: Int): Byte
  def shortAt(c: C[Short], index: Int): Short
  def intAt(c: C[Int], index: Int): Int
  def longAt(c: C[Long], index: Int): Long
  def floatAt(c: C[Float], index: Int): Float
  def doubleAt(c: C[Double], index: Int): Double
  def charAt(c: C[Char], index: Int): Char
}
```

You can find implementations of `SpecializedIndexed` for `Array`, `ArraySeq`, and `IArray` in the companion object of `SeqDeconstructor`.

### 4. Binding.Map

To describe the construction and deconstruction of key-value collections, use `Binding.Map`:

```scala
final case class Map[M[_, _], K, V](
  constructor: MapConstructor[M],
  deconstructor: MapDeconstructor[M],
  defaultValue: Option[() => M[K, V]] = None,
  examples: collection.immutable.Seq[M[K, V]] = Nil
) extends Binding[BindingType.Map[M], M[K, V]]
```

It has two main components for building maps and tearing down maps to their key-value pairs:

- `mapConstructor`: Builds maps from key-value pairs using builders
- `mapDeconstructor`: Provides key-value iteration for maps

Their interfaces are similar to `SeqConstructor` and `SeqDeconstructor`, but specialized for key-value pairs.

ZIO Blocks has an implementation of `Binding.Map` for standard Scala `Map`s in the companion object of `Binding.Map` called `Binding.Map.map`.

### 5. Binding.Primitive

The `Binding.Primitive` provides metadata for primitive/scalar types—`Int`, `Long`, `Double`, `String`, `Boolean`, `Byte`, `Short`, `Char`, `Float`, `BigInt`, `BigDecimal`, `UUID`, and temporal types.

```scala
final case class Primitive[A](
  defaultValue: Option[() => A] = None,
  examples: collection.immutable.Seq[A] = Nil
) extends Binding[BindingType.Primitive, A]
```

Primitives require minimal binding logic since they are atomic values that don't need construction from parts. You can access all built-in primitive bindings in the companion object of `Binding.Primitive`, such as `Primitive.int`, `Primitive.string`, etc.

### 6. Binding.Wrapper

The `Binding.Wrapper` provides wrap/unwrap capabilities for newtype patterns—opaque types, value classes, validated wrappers, and single-field case class wrappers.

`Newtypes` are extremely common in well-designed codebases. Instead of passing raw String values for emails, you define `Email` as a distinct type. Instead of `Int` for user IDs, you define `UserId`. This provides type safety without runtime overhead.

The Wrapper binding handles the bidirectional transformation between the wrapper type and its underlying representation, with optional validation:

```scala
final case class Wrapper[A, B](
  wrap: B => Either[SchemaError, A],
  unwrap: A => Either[SchemaError, B]
) extends Binding[BindingType.Wrapper[A, B], A]
```

**Components:**

- `wrap`: Converts from the underlying type `B` to the wrapper type `A`
- `unwrap`: Extracts the underlying `B` from an `A`

Here is an example of a `Binding.Wrapper` for an `Email` newtype:

```scala

case class Email(value: String)

object Email {
  new Binding.Wrapper[Email, String](
    wrap = value => new Email(value),
    unwrap = email => email.value
  )
}
```

### 7. Binding.Dynamic

To bind untyped dynamic values, use `Binding.Dynamic`. It provides a binding for dynamically typed values whose structure is not known at compile time:

```scala
final case class Dynamic(
  defaultValue: Option[() => DynamicValue] = None,
  examples: collection.immutable.Seq[DynamicValue] = Nil
) extends Binding[BindingType.Dynamic, DynamicValue]
```

Used when the schema of the data is not known at compile time, such as JSON payloads with arbitrary structure.

## Binding and Schema Serialization

When `F[_, _] = NoBinding` in `Reflect[F[_, _], A]` the `Reflect` structure contains only pure data (no functions) and becomes fully serializable. This enables:

1. **Schema serialization**: Convert schemas to JSON Schema or other formats, making them portable
2. **Schema rebinding**: Deserialize a schema and rebind it using a `TypeRegistry`, so it becomes type-safe and operational again

We will cover schema serialization and rebinding in more detail in the `Reflect` data type documentation page. For the full API of the binding lookup mechanism used during rebinding, see [BindingResolver](./binding-resolver.md).

## Summary

The `Binding` data type is the operational heart of ZIO Blocks' schema system:

| Reflect Node        | Binding Variant     | Construction and Deconstruction of |
|---------------------|---------------------|------------------------------------|
| `Reflect.Record`    | `Binding.Record`    | Product Types                      |
| `Reflect.Variant`   | `Binding.Variant`   | Sum Types                          |
| `Reflect.Sequence`  | `Binding.Seq`       | Sequence Collection Types          |
| `Reflect.Map`       | `Binding.Map`       | Key-value Collection Types         |
| `Reflect.Primitive` | `Binding.Primitive` | Primitive Types                    |
| `Reflect.Wrapper`   | `Binding.Wrapper`   | Wrap/unwrap newtypes               |
| `Reflect.Dynamic`   | `Binding.Dynamic`   | Untyped Dynamic Values             |

A key innovation is the clean separation between structure (`Reflect`) and behavior (`Binding`), enabling us to achieve serializable schemas with unbound reflects, while the `Reflect` remains pluggable with the `Binding` for runtime operations.
