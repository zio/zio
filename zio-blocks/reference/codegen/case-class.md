# CaseClass

> `CaseClass` represents an immutable case class in the IR. Use it when generating Scala code from data models, APIs, or structured data—it's the most frequently chosen type definition.

`CaseClass` represents an immutable case class in the IR. Use it when generating Scala code from data models, APIs, or structured data—it's the most frequently chosen type definition.

## Use Cases

- Modeling product types (records) from OpenAPI schemas, JSON Schema, or data formats
- Defining data transfer objects (DTOs) with named fields
- Creating immutable value types with automatic `equals`, `hashCode`, `copy`

## Construction

Build a case class with a name and field list:

```scala
import zio.blocks.codegen.ir._

val person = CaseClass(
  name = "Person",
  fields = List(
    Field("id", TypeRef.Long),
    Field("name", TypeRef.String),
    Field("email", TypeRef.String)
  )
)
```

With optional type parameters:

```scala
import zio.blocks.codegen.ir._

val box = CaseClass(
  name = "Box",
  fields = List(Field("value", TypeRef("T"))),
  typeParams = List(TypeParam("T"))
)
```

With derives clauses:

```scala
import zio.blocks.codegen.ir._

val user = CaseClass(
  name = "User",
  fields = List(
    Field("id", TypeRef.Long),
    Field("name", TypeRef.String)
  ),
  derives = List("Show", "Eq", "Codec")
)
```

## Key Operations

All `CaseClass` instances support these core operations:

### Accessing Components

Extract parts of a case class:

```scala
import zio.blocks.codegen.ir._

val person = CaseClass(
  name = "Person",
  fields = List(
    Field("id", TypeRef.Long),
    Field("name", TypeRef.String),
    Field("email", TypeRef.String)
  )
)
```

Access each component by reading their properties:

```scala
import zio.blocks.codegen.ir._

val person = CaseClass(
  name = "Person",
  fields = List(
    Field("id", TypeRef.Long),
    Field("name", TypeRef.String),
    Field("email", TypeRef.String)
  )
)
```

Each component can be accessed and displayed:

```scala
import zio.blocks.codegen.ir._

person.name            // "Person"
// res4: String = "Person"
person.fields          // List[Field]
// res5: List[Field] = List(
//   Field(
//     name = "id",
//     typeRef = TypeRef(name = "Long", typeArgs = List()),
//     defaultValue = None,
//     annotations = List(),
//     doc = None
//   ),
//   Field(
//     name = "name",
//     typeRef = TypeRef(name = "String", typeArgs = List()),
//     defaultValue = None,
//     annotations = List(),
//     doc = None
//   ),
//   Field(
//     name = "email",
//     typeRef = TypeRef(name = "String", typeArgs = List()),
//     defaultValue = None,
//     annotations = List(),
//     doc = None
//   )
// )
person.typeParams      // List[TypeParam] (empty if not generic)
// res6: List[TypeParam] = List()
person.derives         // List[String]
// res7: List[String] = List()
person.annotations     // List[Annotation]
// res8: List[Annotation] = List()
person.isValueClass    // Boolean
// res9: Boolean = false
```

### Building with Copy

Use the copy method to modify a case class:

```scala
import zio.blocks.codegen.ir._

val person = CaseClass(
  name = "Person",
  fields = List(
    Field("id", TypeRef.Long),
    Field("name", TypeRef.String),
    Field("email", TypeRef.String)
  )
)
```

Create a new version with additional fields:

```scala
import zio.blocks.codegen.ir._

val extended = person.copy(
  fields = person.fields :+ Field("phone", TypeRef.String),
  derives = List("Show")
)
// extended: CaseClass = CaseClass(
//   name = "Person",
//   fields = List(
//     Field(
//       name = "id",
//       typeRef = TypeRef(name = "Long", typeArgs = List()),
//       defaultValue = None,
//       annotations = List(),
//       doc = None
//     ),
//     Field(
//       name = "name",
//       typeRef = TypeRef(name = "String", typeArgs = List()),
//       defaultValue = None,
//       annotations = List(),
//       doc = None
//     ),
//     Field(
//       name = "email",
//       typeRef = TypeRef(name = "String", typeArgs = List()),
//       defaultValue = None,
//       annotations = List(),
//       doc = None
//     ),
//     Field(
//       name = "phone",
//       typeRef = TypeRef(name = "String", typeArgs = List()),
//       defaultValue = None,
//       annotations = List(),
//       doc = None
//     )
//   ),
//   typeParams = List(),
//   extendsTypes = List(),
//   derives = List("Show"),
//   annotations = List(),
//   companion = None,
//   doc = None,
//   isValueClass = false
// )
```

### Adding a Companion Object

Case classes can have companion objects with static members:

```scala
import zio.blocks.codegen.ir._

val withCompanion = CaseClass(
  name = "Config",
  fields = List(Field("timeout", TypeRef.Long)),
  companion = Some(
    CompanionObject(
      members = List(
        ObjectMember.ValMember("Default", TypeRef("Config"), "Config(5000L)")
      )
    )
  )
)
```

## Examples

Here are concrete examples demonstrating typical `CaseClass` usage patterns:

### Example 1: Simple Case Class

A basic case class with primitive fields:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val order = CaseClass(
  name = "Order",
  fields = List(
    Field("id", TypeRef.Long),
    Field("total", TypeRef("BigDecimal")),
    Field("status", TypeRef.String)
  )
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.shop"),
  types = List(order)
)
```

Emits:

```scala
import zio.blocks.codegen.emit._

ScalaEmitter.emit(file, EmitterConfig())
// res13: String = """package com.shop
// 
// case class Order(
//   id: Long,
//   total: BigDecimal,
//   status: String,
// )
// """
```

### Example 2: Nested Types

A case class with optional and collection fields:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val user = CaseClass(
  name = "User",
  fields = List(
    Field("id", TypeRef.Long),
    Field("name", TypeRef.String),
    Field("email", TypeRef("Option", List(TypeRef.String))),
    Field("tags", TypeRef("List", List(TypeRef.String)))
  )
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(user)
)
```

Emits:

```scala
import zio.blocks.codegen.emit._

ScalaEmitter.emit(file, EmitterConfig())
// res15: String = """package com.example
// 
// case class User(
//   id: Long,
//   name: String,
//   email: Option[String],
//   tags: List[String],
// )
// """
```

### Example 3: Generic Case Class

A polymorphic case class with type parameters:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val page = CaseClass(
  name = "Page",
  fields = List(
    Field("items", TypeRef("List", List(TypeRef("T")))),
    Field("total", TypeRef.Long),
    Field("pageSize", TypeRef.Int)
  ),
  typeParams = List(TypeParam("T"))
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(page)
)
```

Emits:

```scala
import zio.blocks.codegen.emit._

ScalaEmitter.emit(file, EmitterConfig())
// res17: String = """package com.example
// 
// case class Page[T](
//   items: List[T],
//   total: Long,
//   pageSize: Int,
// )
// """
```

### Example 4: Case Class with Derives

Automatic typeclass derivation:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val product = CaseClass(
  name = "Product",
  fields = List(
    Field("id", TypeRef.String),
    Field("name", TypeRef.String),
    Field("price", TypeRef("java.math.BigDecimal"))
  )
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(product)
)
```

Emits:

```scala
import zio.blocks.codegen.emit._

ScalaEmitter.emit(file, EmitterConfig())
// res19: String = """package com.example
// 
// case class Product(
//   id: String,
//   name: String,
//   price: java.math.BigDecimal,
// )
// """
```

### Example 5: Case Class with Companion

A case class with factory methods in the companion:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val config = CaseClass(
  name = "AppConfig",
  fields = List(
    Field("host", TypeRef.String),
    Field("port", TypeRef.Int)
  ),
  companion = Some(
    CompanionObject(
      members = List(
        ObjectMember.ValMember(
          "Default",
          TypeRef("AppConfig"),
          "AppConfig(\"localhost\", 8080)"
        )
      )
    )
  )
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(config)
)
```

Emits:

```scala
import zio.blocks.codegen.emit._

ScalaEmitter.emit(file, EmitterConfig())
// res21: String = """package com.example
// 
// case class AppConfig(
//   host: String,
//   port: Int,
// )
// 
// object AppConfig {
//   val Default: AppConfig = AppConfig("localhost", 8080)
// }
// """
```
