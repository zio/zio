# TypeRef

> `TypeRef` represents a reference to a Scala type in the IR. It captures both simple types (like `String`, `Int`) and generic types (like `List[String]`, `Map[String, Int]`).

`TypeRef` represents a reference to a Scala type in the IR. It captures both simple types (like `String`, `Int`) and generic types (like `List[String]`, `Map[String, Int]`).

## Use Cases

- Specifying the types of fields in case classes
- Defining return types of methods
- Representing type arguments in generic types
- Building complex nested type expressions

## Construction

Create a simple type reference:

```scala
import zio.blocks.codegen.ir._

val stringType = TypeRef("String")
val intType = TypeRef("Int")
val booleanType = TypeRef("Boolean")
```

With type arguments for generics:

```scala
import zio.blocks.codegen.ir._

val listOfString = TypeRef("List", List(TypeRef("String")))
val mapStringInt = TypeRef("Map", List(TypeRef("String"), TypeRef("Int")))
val optionalLong = TypeRef("Option", List(TypeRef("Long")))
```

Using factory methods from the companion object:

```scala
import zio.blocks.codegen.ir._

val string = TypeRef.String
val int = TypeRef.Int
val optional = TypeRef.optional(TypeRef.String)
val list = TypeRef.list(TypeRef.Int)
val map = TypeRef.map(TypeRef.String, TypeRef.Int)
```

## Key Operations

All core operations are shown below:

### Accessing Components

Extract parts of a type reference:

```scala
import zio.blocks.codegen.ir._

val optional = TypeRef("Option", List(TypeRef.String))

optional.name      // "Option"
optional.typeArgs  // List[TypeRef] = List(TypeRef("String"))
```

### Building Nested Types

Compose type references:

```scala
import zio.blocks.codegen.ir._
val nestedList = TypeRef("List", List(
  TypeRef("Option", List(TypeRef("String")))
))
// Represents: List[Option[String]]
```

## Primitive Types

`TypeRef` provides factory methods for built-in types:

```scala
import zio.blocks.codegen.ir._

TypeRef.Unit        // Unit
TypeRef.Boolean     // Boolean
TypeRef.Byte        // Byte
TypeRef.Short       // Short
TypeRef.Int         // Int
TypeRef.Long        // Long
TypeRef.Float       // Float
TypeRef.Double      // Double
TypeRef.String      // String
TypeRef.BigInt      // BigInt
TypeRef.BigDecimal  // BigDecimal
TypeRef.Nothing     // Nothing
TypeRef.Any         // Any
```

## Generic Type Factories

Convenience methods simplify common generic patterns:

```scala
import zio.blocks.codegen.ir._

TypeRef.optional(TypeRef.String)  // Option[String]
TypeRef.list(TypeRef.Int)         // List[Int]
TypeRef.set(TypeRef.String)       // Set[String]
TypeRef.map(TypeRef.String, TypeRef.Int)  // Map[String, Int]
TypeRef.tuple(TypeRef.String, TypeRef.Int)  // Tuple2[String, Int]
```

## Examples

Practical examples demonstrate common usage:

### Example 1: Field Types

Use `TypeRef` to define field types in a case class:

```scala
import zio.blocks.codegen.ir._

val user = CaseClass(
  name = "User",
  fields = List(
    Field("id", TypeRef.Long),
    Field("name", TypeRef.String),
    Field("email", TypeRef.optional(TypeRef.String)),
    Field("tags", TypeRef.list(TypeRef.String))
  )
)
```

### Example 2: Generic Type Parameters

Use `TypeRef` with type variable names:

```scala
import zio.blocks.codegen.ir._

val container = CaseClass(
  name = "Container",
  fields = List(
    Field("value", TypeRef("T"))
  ),
  typeParams = List(TypeParam("T"))
)
```

Represents: `Container[T]` with a field `value: T`

### Example 3: Complex Nested Generics

Build deeply nested type expressions:

```scala
import zio.blocks.codegen.ir._

val complexType = TypeRef("Map", List(
  TypeRef.String,
  TypeRef("List", List(
    TypeRef("Option", List(TypeRef.Int))
  ))
))
// Represents: Map[String, List[Option[Int]]]

val field = Field("data", complexType)
```

### Example 4: Qualified Type Names

Use fully qualified names for non-standard types:

```scala
import zio.blocks.codegen.ir._

val bigDecimal = TypeRef("java.math.BigDecimal")
val jsonObject = TypeRef("com.example.json.JsonObject")

val field1 = Field("price", bigDecimal)
val field2 = Field("metadata", jsonObject)
```

### Example 5: Union and Intersection Types (Scala 3)

Represent Scala 3 union and intersection types:

```scala
import zio.blocks.codegen.ir._

// Represents: String | Int
val unionType = TypeRef("|", List(TypeRef.String, TypeRef.Int))

// Represents: Serializable & Comparable
val intersectionType = TypeRef("&", List(
  TypeRef("Serializable"),
  TypeRef("Comparable")
))
```
