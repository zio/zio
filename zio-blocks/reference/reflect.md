# Reflect

> The `Reflect` data type is the foundational data structure underlying ZIO Blocks. While `Schema[A]` is the user-facing API that wraps a `Reflect`, the `Reflect` type itself contains the actual reflective metadata that describes the structure of Scala data types at runtime:

The `Reflect` data type is the foundational data structure underlying ZIO Blocks. While `Schema[A]` is the user-facing API that wraps a `Reflect`, the `Reflect` type itself contains the actual reflective metadata that describes the structure of Scala data types at runtime:

```scala
// Simplified definition of Schema and Reflect
final case class Schema[A](reflect: Reflect.Bound[A])

sealed trait Reflect[F[_, _], A]
object Reflect {
  case class Record   [F[_, _], A](???)             extends Reflect[F, A]
  case class Variant  [F[_, _], A](???)             extends Reflect[F, A]
  case class Sequence [F[_, _], A, C[_]](???)       extends Reflect[F, C[A]]
  case class Map      [F[_, _], K, V, M[_, _]](???) extends Reflect[F, M[K, V]]
  case class Dynamic  [F[_, _]](???)                extends Reflect[F, DynamicValue]
  case class Primitive[F[_, _], A](???)             extends Reflect[F, A]
  case class Wrapper  [F[_, _], A, B](???)          extends Reflect[F, A]
  case class Deferred [F[_, _], A](???)             extends Reflect[F, A]
}
```

The `Reflect` type has two type parameters:

1. **`F[_, _]`** The binding type constructor. When `F = Binding`, the Reflect is "bound" and contains runtime functions for construction/deconstruction. When `F = NoBinding`, the Reflect is "unbound" and contains only pure structural data. 
2. **`A`** The Scala type that this Reflect describes.                                                                         

## Bound and Unbound Reflects

Each of the eight case class nodes of `Reflect` corresponds to a different category of Scala types (records, variants, sequences, maps, primitives, ... types). They may contain a field of type `F[BindingType.X, A]` that holds the runtime binding information for that type. For example the `Record` variant contains a `F[BindingType.Record, A]` instance that holds the `Constructor[A]` and `Deconstructor[A]` functions:

```scala
case class Record[F[_, _], A](
  fields: IndexedSeq[Term[F, A, ?]],
  typeName: TypeName[A],
  recordBinding: F[BindingType.Record, A],  // Binding info
  doc: Doc = Doc.Empty,
  modifiers: Seq[Modifier.Reflect] = Nil
) extends Reflect[F, A]
```

We have discussed the binding system in detail on [the Binding System page](./binding.md).

This dual-nature design enables schema serialization:

- **Bound Reflect (`Reflect.Bound[A]`)**: Contains runtime bindings (constructors, deconstructors, functions) that allow actual construction and deconstruction of values. This is what `Schema` wraps.
- **Unbound Reflect**: Contains only pure data with no functions or closures. Can be serialized and deserialized identically, enabling transmission of schemas across the wire.

```scala
type Reflect.Bound[A]   = Reflect[Binding, A]
type Reflect.Unbound[A] = Reflect[NoBinding, A]

// Schemas are always bound
final case class Schema[A](reflect: Reflect.Bound[A])
```

## Reflect Nodes

`Reflect` is a sealed trait with eight nodes, each representing a different category of Scala types.

### 1. Record

`Reflect.Record` represents case classes and other product types:

```scala
case class Record[F[_, _], A](
  fields: IndexedSeq[Term[F, A, ?]],
  typeName: TypeName[A],
  recordBinding: F[BindingType.Record, A],
  doc: Doc = Doc.Empty,
  modifiers: Seq[Modifier.Record] = Nil
) extends Reflect[F, A]
```

**Key Components:**

- **fields**: An indexed sequence of `Term` objects, each describing a field with its name, type, and nested `Reflect`.
- **typeName**: The fully qualified type name including namespace.
- **recordBinding**: Contains the `Constructor[A]` and `Deconstructor[A]` for building and tearing apart values.
- **doc**: Optional documentation.
- **modifiers**: Metadata modifiers for customization.

The following example shows a `Person` case class represented as a `Reflect.Record`:

```scala

case class Person(
  name: String,
  email: String,
  age: Int,
  height: Double,
  weight: Double
)

object Person {
  implicit val schema: Schema[Person] =
    Schema {
      Reflect.Record[Binding, Person](
        fields = Vector(
          Term("name", Schema.string.reflect),
          Term("email", Schema.string.reflect),
          Term("age", Schema.int.reflect),
          Term("height", Schema.double.reflect),
          Term("weight", Schema.double.reflect)
        ),
        typeId = TypeId.of[Person],
        recordBinding = Binding.Record[Person](
          constructor = new Constructor[Person] {
            override def usedRegisters: RegisterOffset =
              RegisterOffset(objects = 2, ints = 1, doubles = 2)
            override def construct(in: Registers, offset: RegisterOffset): Person =
              Person(
                in.getObject(offset).asInstanceOf[String],
                in.getObject(offset + RegisterOffset(objects = 1)).asInstanceOf[String],
                in.getInt(offset + RegisterOffset.Zero),
                in.getDouble(offset + RegisterOffset(ints = 1)),
                in.getDouble(offset + RegisterOffset(ints = 1, doubles = 1))
              )
          },
          deconstructor = new Deconstructor[Person] {
            override def usedRegisters: RegisterOffset =
              RegisterOffset(objects = 2, ints = 1, doubles = 2)
            override def deconstruct(out: Registers, offset: RegisterOffset, in: Person): Unit = {
              out.setObject(offset, in.name)
              out.setObject(offset + RegisterOffset(objects = 1), in.email)
              out.setInt(offset, in.age)
              out.setDouble(offset + RegisterOffset(ints = 1), in.height)
              out.setDouble(offset + RegisterOffset(ints = 1, doubles = 1), in.weight)
            }
          }
        )
      )
    }
}
```

### 2. Variant

`Reflect.Variant` represents sealed traits, enums, and other sum types—data structures that can be one of several cases:

```scala
case class Variant[F[_, _], A](
  cases: IndexedSeq[Term[F, A, ?]],
  typeName: TypeName[A],
  variantBinding: F[BindingType.Variant, A],
  doc: Doc = Doc.Empty,
  modifiers: Seq[Modifier.Variant] = Nil
) extends Reflect[F, A]
```

**Key Components:**

- **cases**: An indexed sequence of `Term` objects, each describing one of the possible cases with its name, type, and nested `Reflect`.
- **typeName**: The fully qualified type name including namespace.
- **variantBinding**: Contains the binding information for the variant, such as a discriminator and matcher functions.

The following example shows a `Shape` sealed trait represented as a `Reflect.Variant`. We assume the schema for the `Circle` and `Rectangle` case classes are defined elsewhere:

```scala
sealed trait Shape extends Product with Serializable

object Shape {
   case class Circle(radius: Double) extends Shape
   object Circle {
      implicit val schema: Schema[Circle] = ???
   }
  
   case class Rectangle(width: Double, height: Double) extends Shape
   object Rectangle {
      implicit val schema: Schema[Rectangle] = ???
   }
  
  implicit val schema: Schema[Shape] =
    Schema[Shape] {
      Reflect.Variant[Binding, Shape](
        cases = IndexedSeq(
          Term(
            name = "Circle",
            value = Circle.schema.reflect
          ),
          Term(
            name = "Rectangle",
            value = Rectangle.schema.reflect
          )
        ),
        typeName = TypeName(namespace = Namespace(Seq.empty), name = "Shape"),
        variantBinding = Binding.Variant[Shape](
          discriminator = new Discriminator[Shape] {
            override def discriminate(a: Shape): Int = a match {
              case Circle(_)       => 0
              case Rectangle(_, _) => 1
            }
          },
          matchers = Matchers(
            new Matcher[Circle] {
              override def downcastOrNull(any: Any): Circle =
                any match {
                  case c: Circle => c
                  case _         => null.asInstanceOf[Circle]
                }
            },
            new Matcher[Rectangle] {
              override def downcastOrNull(any: Any): Rectangle =
                any match {
                  case r: Rectangle => r
                  case _            => null.asInstanceOf[Rectangle]
                }
            }
          )
        ),
        doc = Doc("A geometric shape"),
        modifiers = Seq(Modifier.config("protobuf.field-id", "1"))
      )
    }
}
```

### 3. Sequence

`Reflect.Sequence` represents sequential collections like `List`, `Vector`, `Array`, `Set`, and other `Iterable` types:

```scala
case class Sequence[F[_, _], A, C[_]](
  element: Reflect[F, A],
  typeName: TypeName[C[A]],
  seqBinding: F[BindingType.Seq[C], C[A]],
  doc: Doc = Doc.Empty,
  modifiers: scala.Seq[Modifier.Reflect] = Nil
) extends Reflect[F, C[A]]
```

**Key components**:
- **element**: The `Reflect` describing the element type.
- **seqBinding**: Contains the corresponding sequence binding

### 4. Map

`Reflect.Map` represents key-value collections like `Map` etc:

```scala
case class Map[F[_, _], K, V, M[_, _]](
  key: Reflect[F, K],
  value: Reflect[F, V],
  typeName: TypeName[M[K, V]],
  mapBinding: F[BindingType.Map[M], M[K, V]],
  doc: Doc = Doc.Empty,
  modifiers: Seq[Modifier.Reflect] = Nil
) extends Reflect[F, M[K, V]]
```

**Key Components:**

- **key**: The `Reflect` describing the key type.
- **value**: The `Reflect` describing the value type.
- **mapBinding**: Contains the corresponding map binding.

### 5. Dynamic

`Reflect.Dynamic` represents values whose types are not known at compile time. This is essential for handling JSON payloads, schemaless data, or any scenario where the structure is determined at runtime.

```scala
case class Dynamic[F[_, _]](
  dynamicBinding: F[BindingType.Dynamic, DynamicValue],
  doc: Doc = Doc.Empty,
  modifiers: Seq[Modifier.Dynamic] = Nil
) extends Reflect[F, DynamicValue]
```

### 6. Primitive

`Reflect.Primitive` represents primitive and scalar types. This includes numeric types (`Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigInt`, `BigDecimal`), text types (`String`, `Char`), and `Boolean`. It also covers the full range of `java.time` temporal types: instants and dates (`Instant`, `LocalDate`, `LocalDateTime`, `LocalTime`, `OffsetDateTime`, `OffsetTime`, `ZonedDateTime`), durations and periods (`Duration`, `Period`), calendar components (`Year`, `YearMonth`, `Month`, `MonthDay`, `DayOfWeek`), and time zones (`ZoneId`, `ZoneOffset`). Additionally, it supports `Currency`, `UUID`, and `Unit`, and can be extended to support custom primitive types as needed.

```scala
case class Primitive[F[_, _], A](
  primitiveType: PrimitiveType[A],
  typeName: TypeName[A],
  primitiveBinding: F[BindingType.Primitive, A],
  doc: Doc = Doc.Empty,
  modifiers: Seq[Modifier.Reflect] = Nil
) extends Reflect[F, A]
```

You can access all built-in primitive types inside companion object of `Reflect` data type, e.g. `Reflect.int`, `Reflect.string`, `Reflect.instant`, etc.

### 7. Wrapper

Modern Scala development often involves creating domain-specific types that add semantic meaning or validation to primitive types:

```scala
// Opaque type in Scala 3
opaque type Age = Int

// Newtype pattern
case class Email(value: String)

// ZIO Prelude newtypes
object UserId extends Newtype[String]
type UserId = UserId.Type
```

Each of these patterns shares a common characteristic: they wrap an underlying type (`Int`, `String`) to create a new type with distinct semantics.

`Reflect.Wrapper` is a specialized node type that models the relationship between a wrapper type and its underlying representation. It provides a unified abstraction for opaque types, newtypes, wrapper classes, and similar patterns where one type wraps another with optional validation logic:

```
case class Wrapper[F[_, _], A, B](
  wrapped: Reflect[F, B],
  typeId: TypeId[A],
  wrapperBinding: F[BindingType.Wrapper[A, B], A],
  doc: Doc = Doc.Empty,
  modifiers: Seq[Modifier.Reflect] = Nil
) extends Reflect[F, A]
```

The `Wrapper` has three type parameters:

- **`F[_, _]`**: The binding type constructor (typically `Binding` for bound schemas)
- **`A`**: The wrapper type (e.g., `Age`, `Email`)
- **`B`**: The underlying/wrapped type (e.g., `Int`, `String`)

So we can say a wrapper of type `Wrapper[F[_, _], A, B]` wraps a type `B` (described by `wrapped: Reflect[F, B]`) to create a new type `A`. 

Assume we have a positive integer type `PosInt` that wraps an `Int` but enforces a validation rule that the value must be non-negative. We can define its schema using `Reflect.Wrapper` as follows:

```scala

case class PosInt private (value: Int) extends AnyVal

object PosInt {
  def apply(value: Int): Either[String, PosInt] =
    if (value >= 0) Right(new PosInt(value))
    else Left("Expected positive value")

  implicit val schema: Schema[PosInt] = Schema(
    Reflect.Wrapper(
      wrapped = Schema[Int].reflect,
      typeId = TypeId.of[PosInt],
      wrapperBinding = Binding.Wrapper[PosInt, Int](
        wrap = v => PosInt(v), 
        unwrap = _.value
      )
    )
  )
}
```

To create schemas for wrapper types, use `transform`:

```scala

case class PosInt private (value: Int) extends AnyVal

object PosInt {
  def unsafeApply(value: Int): PosInt =
    if (value >= 0) new PosInt(value)
    else throw SchemaError.validationFailed("Expected positive value")

  implicit val schema: Schema[PosInt] = 
    Schema[Int].transform(PosInt.unsafeApply, _.value)
}
```

### 8. Deferred

`Reflect.Deferred` introduces laziness to handle recursive and mutually recursive data types. Without deferred evaluation, recursive types would cause infinite loops:

```scala
case class Deferred[F[_, _], A](
  deferred: () => Reflect[F, A]
) extends Reflect[F, A]
```

For example, if we have a recursive data type like below:

```scala
case class Tree(value: Int, children: List[Tree])
```

We can define its schema using `Reflect.Deferred` as follows:

```scala

// Recursive data type
case class Tree(value: Int, children: List[Tree])

object Tree {
  implicit val schema: Schema[Tree] = {
    lazy val treeReflect: Reflect.Bound[Tree] = Reflect.Record[Binding, Tree](
      fields = Vector(
        Schema[Int].reflect.asTerm("value"),
        Reflect.Deferred(() => Schema.list(new Schema(treeReflect)).reflect).asTerm("children")
      ),
      typeId = TypeId.of[Tree],
      recordBinding = Binding.Record(
        constructor = new Constructor[Tree] {
          def usedRegisters: RegisterOffset = RegisterOffset(ints = 1, objects = 1)

          def construct(in: Registers, offset: RegisterOffset): Tree =
            Tree(
              in.getInt(offset),
              in.getObject(offset).asInstanceOf[List[Tree]]
            )
        },
        deconstructor = new Deconstructor[Tree] {
          def usedRegisters: RegisterOffset = RegisterOffset(ints = 1, objects = 1)

          def deconstruct(out: Registers, offset: RegisterOffset, in: Tree): Unit = {
            out.setInt(offset, in.value)
            out.setObject(offset, in.children)
          }
        }
      )
    )

    new Schema(treeReflect)
  }
}
```

## Debug-Friendly toString

`Reflect` types have a custom `toString` that produces a human-readable SDL (Schema Definition Language) format. This makes debugging schemas much easier compared to the default case class output.

```scala

case class Person(name: String, age: Int, address: Address)
case class Address(street: String, city: String)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

println(Schema[Person].reflect)
// Output:
// record Person {
//   name: String
//   age: Int
//   address: record Address {
//     street: String
//     city: String
//   }
// }
```

**Format by Reflect type:**

| Type | Format |
|------|--------|
| `Primitive` | Type name (e.g., `String`, `Int`, `java.time.Instant`) |
| `Record` | `record Name { fields... }` |
| `Variant` | `variant Name { \| Case1 \| Case2... }` |
| `Sequence` | `sequence List[Element]` or multiline for complex elements |
| `Map` | `map Map[Key, Value]` or multiline for complex types |
| `Wrapper` | `wrapper Name(underlying)` |
| `Deferred` | `deferred => TypeName` (breaks recursive cycles) |
| `Dynamic` | `DynamicValue` |

**Variant example:**

```scala
sealed trait PaymentMethod
case object Cash extends PaymentMethod
case class CreditCard(number: String, cvv: String) extends PaymentMethod

// toString output:
// variant PaymentMethod {
//   | Cash
//   | CreditCard(
//       number: String,
//       cvv: String
//     )
// }
```

**Recursive type example:**

```scala
case class Tree(value: Int, children: List[Tree])

// toString output:
// record Tree {
//   value: Int
//   children: sequence List[
//     deferred => Tree
//   ]
// }
```

## Auto-Derivation

While you can manually construct `Reflect` instances as shown in the examples above, ZIO Blocks provides powerful auto-derivation capabilities that can automatically generate `Schema` instances (and thus `Reflect` instances) for most Scala types using macros and implicit resolution.

The auto-derivation mechanism inspects the structure of your data types at compile time and generates the appropriate `Reflect` representation, including nested types, collections, options, and more.

To leverage auto-derivation, simply define an implicit `Schema` for your type using `Schema.derived`:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}
```

The above will automatically generate a `Reflect.Record` for the `Person` case class, including fields for `name` and `age`, along with the necessary bindings for construction and deconstruction. The same applies to more complex types, including variants, collections, and recursive structures.
