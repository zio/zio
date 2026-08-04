# ScalaFile

> `ScalaFile` is the root IR node representing a complete Scala source file. It holds everything needed to emit a compilable file: the package declaration, imports, and type definitions.

`ScalaFile` is the root IR node representing a complete Scala source file. It holds everything needed to emit a compilable file: the package declaration, imports, and type definitions.

## Use Cases

- As the entry point to `ScalaEmitter.emit(file, config)` to generate source code
- As a container when building complex file structures with multiple types
- As the canonical representation of "what I want to emit as Scala"

## Construction

Build a `ScalaFile` with a package declaration, optional imports, and optional types:

```scala
import zio.blocks.codegen.ir._

val simpleFile = ScalaFile(
  packageDecl = PackageDecl("com.example")
)
```

With imports and types:

```scala
import zio.blocks.codegen.ir._

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  imports = List(
    Import.WildcardImport("zio")
  ),
  types = List(
    CaseClass(
      name = "User",
      fields = List(Field("id", TypeRef.Long))
    )
  )
)
```

## Key Operations

All core operations are shown below:

### Accessing Components

Extract the parts of a `ScalaFile`:

```scala
import zio.blocks.codegen.emit._
// Read-only access to all components
file.packageDecl    // PackageDecl
// res1: PackageDecl = PackageDecl("com.example")
file.imports        // List[Import]
// res2: List[Import] = List(WildcardImport("zio"))
file.types          // List[TypeDefinition]
// res3: List[TypeDefinition] = List(
//   CaseClass(
//     name = "User",
//     fields = List(
//       Field(
//         name = "id",
//         typeRef = TypeRef(name = "Long", typeArgs = List()),
//         defaultValue = None,
//         annotations = List(),
//         doc = None
//       )
//     ),
//     typeParams = List(),
//     extendsTypes = List(),
//     derives = List(),
//     annotations = List(),
//     companion = None,
//     doc = None,
//     isValueClass = false
//   )
// )
```

### Building with Copy

Modify a file by copying with new values:

```scala
import zio.blocks.codegen.emit._
val updatedFile = file.copy(
  types = file.types :+ CaseClass(
    name = "Product",
    fields = List(Field("name", TypeRef.String))
  )
)
// updatedFile: ScalaFile = ScalaFile(
//   packageDecl = PackageDecl("com.example"),
//   imports = List(WildcardImport("zio")),
//   types = List(
//     CaseClass(
//       name = "User",
//       fields = List(
//         Field(
//           name = "id",
//           typeRef = TypeRef(name = "Long", typeArgs = List()),
//           defaultValue = None,
//           annotations = List(),
//           doc = None
//         )
//       ),
//       typeParams = List(),
//       extendsTypes = List(),
//       derives = List(),
//       annotations = List(),
//       companion = None,
//       doc = None,
//       isValueClass = false
//     ),
//     CaseClass(
//       name = "Product",
//       fields = List(
//         Field(
//           name = "name",
//           typeRef = TypeRef(name = "String", typeArgs = List()),
//           defaultValue = None,
//           annotations = List(),
//           doc = None
//         )
//       ),
//       typeParams = List(),
//       extendsTypes = List(),
//       derives = List(),
//       annotations = List(),
//       companion = None,
//       doc = None,
//       isValueClass = false
//     )
//   )
// )
```

### Emitting to Source

Generate Scala source from the file:

```scala
import zio.blocks.codegen.emit._

val config = EmitterConfig()
val sourceCode = ScalaEmitter.emit(file, config)
// sourceCode is a String ready to write to a .scala file
```

## Examples

Practical examples demonstrate common usage. Each builds a `ScalaFile`, calls `ScalaEmitter.emit()`, and displays the resulting output:

### Example 1: Minimal File

A file with just a package and one case class (all examples use `zio.blocks.codegen.ir._` and `zio.blocks.codegen.emit._` imports):

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val minimal = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(
    CaseClass(
      name = "Empty",
      fields = Nil
    )
  )
)
```

Emits:

```scala
import zio.blocks.codegen.emit._
ScalaEmitter.emit(minimal, EmitterConfig())
// res6: String = """package com.example
// 
// case class Empty()
// """
```

### Example 2: File with Multiple Types

A file with a sealed trait and case classes:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val multiType = ScalaFile(
  packageDecl = PackageDecl("com.payment"),
  imports = List(Import.WildcardImport("zio")),
  types = List(
    SealedTrait(
      name = "PaymentMethod",
      cases = List(
        SealedTraitCase.CaseClassCase(
          CaseClass(
            "Card",
            List(Field("cardNumber", TypeRef.String))
          )
        ),
        SealedTraitCase.CaseObjectCase("Cash")
      )
    )
  )
)
```

Emits:

```scala
import zio.blocks.codegen.emit._
ScalaEmitter.emit(multiType, EmitterConfig())
// res8: String = """package com.payment
// 
// import zio.*
// 
// sealed trait PaymentMethod
// 
// object PaymentMethod {
//   case class Card(
//     cardNumber: String,
//   ) extends PaymentMethod
//   case object Cash extends PaymentMethod
// }
// """
```

### Example 3: File with Imports and Type Parameters

A file demonstrating generic types and selective imports:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val generic = ScalaFile(
  packageDecl = PackageDecl("com.containers"),
  imports = List(
    Import.SingleImport("scala.collection", "Seq"),
    Import.WildcardImport("zio")
  ),
  types = List(
    CaseClass(
      name = "Wrapper",
      fields = List(
        Field("items", TypeRef("Seq", List(TypeRef("T"))))
      ),
      typeParams = List(TypeParam("T"))
    )
  )
)
```

Emits:

```scala
import zio.blocks.codegen.emit._
ScalaEmitter.emit(generic, EmitterConfig())
// res10: String = """package com.containers
// 
// import scala.collection.Seq
// import zio.*
// 
// case class Wrapper[T](
//   items: Seq[T],
// )
// """
```
