# SealedTrait

> `SealedTrait` represents a sealed trait in the IR—a sum type (algebraic data type) that enumerates all possible cases. It's essential for modeling discriminated unions and exhaustive pattern matching.

## Use Cases

- Modeling sum types (ADTs) from API responses or domain models
- Creating hierarchies of case classes and case objects
- Defining error types with multiple failure cases
- Implementing discriminated unions with exhaustive matching

## Construction

Build a sealed trait with cases:

```scala
import zio.blocks.codegen.ir._

val httpStatus = SealedTrait(
  name = "HttpStatus",
  cases = List(
    SealedTraitCase.CaseObjectCase("Ok"),
    SealedTraitCase.CaseObjectCase("NotFound"),
    SealedTraitCase.CaseObjectCase("ServerError")
  )
)
```

With case classes as cases:

```scala
import zio.blocks.codegen.ir._

val result = SealedTrait(
  name = "Result",
  cases = List(
    SealedTraitCase.CaseClassCase(
      CaseClass("Success", List(Field("value", TypeRef.String)))
    ),
    SealedTraitCase.CaseClassCase(
      CaseClass("Failure", List(Field("error", TypeRef.String)))
    )
  )
)
```

With type parameters:

```scala
import zio.blocks.codegen.ir._

val option = SealedTrait(
  name = "Option",
  typeParams = List(TypeParam("A")),
  cases = List(
    SealedTraitCase.CaseObjectCase("None"),
    SealedTraitCase.CaseClassCase(
      CaseClass("Some", List(Field("value", TypeRef("A"))), typeParams = List(TypeParam("A")))
    )
  )
)
```

## Key Operations

All core operations are shown below:

### Accessing Components

Extract parts of a sealed trait:

```scala
import zio.blocks.codegen.emit._
result.name          // "Result"
// res2: String = "Result"
result.cases         // List[SealedTraitCase]
// res3: List[SealedTraitCase] = List(
//   CaseClassCase(
//     CaseClass(
//       name = "Success",
//       fields = List(
//         Field(
//           name = "value",
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
//   ),
//   CaseClassCase(
//     CaseClass(
//       name = "Failure",
//       fields = List(
//         Field(
//           name = "error",
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
result.typeParams    // List[TypeParam] (empty if not generic)
// res4: List[TypeParam] = List()
result.derives       // List[String]
// res5: List[String] = List()
result.annotations   // List[Annotation]
// res6: List[Annotation] = List()
```

### Working with Cases

Each case is either a `CaseObjectCase` or `CaseClassCase`:

```scala
import zio.blocks.codegen.ir._

val caseObj = SealedTraitCase.CaseObjectCase("Unknown")

val caseClass = SealedTraitCase.CaseClassCase(
  CaseClass("Error", List(Field("msg", TypeRef.String)))
)
```

### Building with Copy

Modify a sealed trait:

```scala
import zio.blocks.codegen.emit._
val updated = result.copy(
  cases = result.cases :+ SealedTraitCase.CaseObjectCase("Pending")
)
// updated: SealedTrait = SealedTrait(
//   name = "Result",
//   typeParams = List(),
//   extendsTypes = List(),
//   cases = List(
//     CaseClassCase(
//       CaseClass(
//         name = "Success",
//         fields = List(
//           Field(
//             name = "value",
//             typeRef = TypeRef(name = "String", typeArgs = List()),
//             defaultValue = None,
//             annotations = List(),
//             doc = None
//           )
//         ),
//         typeParams = List(),
//         extendsTypes = List(),
//         derives = List(),
//         annotations = List(),
//         companion = None,
//         doc = None,
//         isValueClass = false
//       )
//     ),
//     CaseClassCase(
//       CaseClass(
//         name = "Failure",
//         fields = List(
//           Field(
//             name = "error",
//             typeRef = TypeRef(name = "String", typeArgs = List()),
//             defaultValue = None,
//             annotations = List(),
//             doc = None
//           )
//         ),
//         typeParams = List(),
//         extendsTypes = List(),
//         derives = List(),
//         annotations = List(),
//         companion = None,
//         doc = None,
//         isValueClass = false
//       )
//     ),
//     CaseObjectCase("Pending")
//   ),
// ...
```

## Examples

Practical examples demonstrate common usage:

### Example 1: Simple Enum-Like Sealed Trait

A sealed trait with only case objects:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val color = SealedTrait(
  name = "Color",
  cases = List(
    SealedTraitCase.CaseObjectCase("Red"),
    SealedTraitCase.CaseObjectCase("Green"),
    SealedTraitCase.CaseObjectCase("Blue")
  )
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.graphics"),
  types = List(color)
)
```

Emits:

```scala
import zio.blocks.codegen.emit._
ScalaEmitter.emit(file, EmitterConfig())
// res9: String = """package com.graphics
// 
// sealed trait Color
// 
// object Color {
//   case object Red extends Color
//   case object Green extends Color
//   case object Blue extends Color
// }
// """
```

### Example 2: Mixed Case Objects and Case Classes

A sealed trait with both simple and complex cases:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val payment = SealedTrait(
  name = "Payment",
  cases = List(
    SealedTraitCase.CaseClassCase(
      CaseClass("CreditCard", List(
        Field("number", TypeRef.String),
        Field("expiry", TypeRef.String)
      ))
    ),
    SealedTraitCase.CaseClassCase(
      CaseClass("BankTransfer", List(
        Field("accountNumber", TypeRef.String)
      ))
    ),
    SealedTraitCase.CaseObjectCase("Cash"),
    SealedTraitCase.CaseObjectCase("Check")
  )
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(payment)
)
```

Emits:

```scala
import zio.blocks.codegen.emit._
ScalaEmitter.emit(file, EmitterConfig())
// res11: String = """package com.example
// 
// sealed trait Payment
// 
// object Payment {
//   case class CreditCard(
//     number: String,
//     expiry: String,
//   ) extends Payment
//   case class BankTransfer(
//     accountNumber: String,
//   ) extends Payment
//   case object Cash extends Payment
//   case object Check extends Payment
// }
// """
```

### Example 3: Generic Sealed Trait

A polymorphic sealed trait with type parameters:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val either = SealedTrait(
  name = "Either",
  typeParams = List(TypeParam("L"), TypeParam("R")),
  cases = List(
    SealedTraitCase.CaseClassCase(
      CaseClass("Left", List(Field("value", TypeRef("L"))), typeParams = List(TypeParam("L")))
    ),
    SealedTraitCase.CaseClassCase(
      CaseClass("Right", List(Field("value", TypeRef("R"))), typeParams = List(TypeParam("R")))
    )
  )
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(either)
)
```

Emits:

```scala
import zio.blocks.codegen.emit._
ScalaEmitter.emit(file, EmitterConfig())
// res13: String = """package com.example
// 
// sealed trait Either[L, R]
// 
// object Either {
//   case class Left[L](
//     value: L,
//   ) extends Either[L, R]
//   case class Right[R](
//     value: R,
//   ) extends Either[L, R]
// }
// """
```

### Example 4: Error ADT

A sealed trait for error handling:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val appError = SealedTrait(
  name = "AppError",
  cases = List(
    SealedTraitCase.CaseClassCase(
      CaseClass("ValidationError", List(
        Field("field", TypeRef.String),
        Field("reason", TypeRef.String)
      ))
    ),
    SealedTraitCase.CaseClassCase(
      CaseClass("NotFound", List(
        Field("id", TypeRef.Long)
      ))
    ),
    SealedTraitCase.CaseObjectCase("Unauthorized"),
    SealedTraitCase.CaseObjectCase("InternalServerError")
  )
)

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(appError)
)
```

Emits:

```scala
import zio.blocks.codegen.emit._
ScalaEmitter.emit(file, EmitterConfig())
// res15: String = """package com.example
// 
// sealed trait AppError
// 
// object AppError {
//   case class ValidationError(
//     field: String,
//     reason: String,
//   ) extends AppError
//   case class NotFound(
//     id: Long,
//   ) extends AppError
//   case object Unauthorized extends AppError
//   case object InternalServerError extends AppError
// }
// """
```
