# EmitterConfig

> `EmitterConfig` controls how `ScalaEmitter` formats Scala code. Customize indentation, import sorting, trailing commas, and target Scala version (2 vs 3) by creating a configured instance.

## Use Cases

- Configuring code style (indentation, commas) to match your project conventions
- Targeting Scala 3 features (enums, derives) or Scala 2 fallback syntax
- Controlling import statement ordering
- Emitting code for different codebases with different style preferences

## Configuration Fields

| Field            | Type    | Default | Description                             |
|------------------|---------|---------|-----------------------------------------|
| `indentWidth`    | Int     | 2       | Spaces per indentation level            |
| `sortImports`    | Boolean | true    | Sort import statements alphabetically   |
| `trailingCommas` | Boolean | true    | Add trailing commas in collections      |
| `scala3Syntax`   | Boolean | true    | Target Scala 3 syntax features          |

## Construction

Create a default configuration:

```scala
import zio.blocks.codegen.emit._

val default = EmitterConfig()
// indentWidth=2, sortImports=true, trailingCommas=true, scala3Syntax=true
```

With custom settings:

```scala
import zio.blocks.codegen.emit._

val custom = EmitterConfig(
  indentWidth = 4,
  sortImports = true,
  trailingCommas = false,
  scala3Syntax = false
)
```

## Key Operations

All `EmitterConfig` instances provide these operations:

### Accessing Configuration

Read configuration values:

```scala
import zio.blocks.codegen.emit._

val config = EmitterConfig(indentWidth = 4)

config.indentWidth      // 4
config.sortImports      // true (default)
config.trailingCommas   // true (default)
config.scala3Syntax     // true (default)
```

### Building with Copy

Modify a configuration:

```scala
import zio.blocks.codegen.emit._

val base = EmitterConfig()

val scala2Config = base.copy(
  scala3Syntax = false
)

val wideIndentConfig = base.copy(
  indentWidth = 4
)
```

## Examples

Here are practical examples showing different configuration scenarios:

### Example 1: Standard Configuration

The default configuration for most projects:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(
    CaseClass("User", List(
      Field("id", TypeRef.Long),
      Field("name", TypeRef.String)
    ))
  )
)

val config = EmitterConfig()
```

Emits:

```scala
import zio.blocks.codegen.emit._
ScalaEmitter.emit(file, config)
// res5: String = """package com.example
// 
// case class User(
//   id: Long,
//   name: String,
// )
// """
```

### Example 2: Wide Indentation (4 spaces)

For projects preferring 4-space indentation:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  types = List(
    SealedTrait(
      "Result",
      cases = List(
        SealedTraitCase.CaseClassCase(
          CaseClass("Success", List(
            Field("value", TypeRef.String)
          ))
        ),
        SealedTraitCase.CaseObjectCase("Failure")
      )
    )
  )
)

val config = EmitterConfig(indentWidth = 4)
```

Emits with 4-space indentation:

```scala
import zio.blocks.codegen.emit._
ScalaEmitter.emit(file, config)
// res7: String = """package com.example
// 
// sealed trait Result
// 
// object Result {
//     case class Success(
//         value: String,
//     ) extends Result
//     case object Failure extends Result
// }
// """
```

### Example 3: Scala 2 Compatibility

Target Scala 2 syntax for older codebases:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.legacy"),
  imports = List(
    Import.WildcardImport("scala.collection")
  ),
  types = List(
    Enum(
      name = "Color",
      cases = List(
        EnumCase.SimpleCase("Red"),
        EnumCase.SimpleCase("Blue")
      )
    )
  )
)

val scala2Config = EmitterConfig(
  scala3Syntax = false
)
```

Emits sealed trait syntax (Scala 2 compatible):

```scala
import zio.blocks.codegen.emit._
ScalaEmitter.emit(file, scala2Config)
// res9: String = """package com.legacy
// 
// import scala.collection._
// 
// sealed trait Color
// 
// object Color {
//   case object Red extends Color
//   case object Blue extends Color
// }
// """
```

### Example 4: No Trailing Commas

For projects with strict no-trailing-commas rules:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.strict"),
  types = List(
    CaseClass(
      "Options",
      List(
        Field("a", TypeRef.String),
        Field("b", TypeRef.Int),
        Field("c", TypeRef.Boolean)
      )
    )
  )
)

val config = EmitterConfig(
  trailingCommas = false
)
```

Emits without trailing comma on last field:

```scala
import zio.blocks.codegen.emit._
ScalaEmitter.emit(file, config)
// res11: String = """package com.strict
// 
// case class Options(
//   a: String,
//   b: Int,
//   c: Boolean
// )
// """
```

### Example 5: Unsorted Imports

For projects that manage imports manually:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.example"),
  imports = List(
    Import.WildcardImport("zio"),
    Import.SingleImport("scala.collection", "Seq"),
    Import.WildcardImport("scala")
  ),
  types = List(
    CaseClass("Data", List(
      Field("items", TypeRef("Seq", List(TypeRef.String)))
    ))
  )
)

val config = EmitterConfig(
  sortImports = false  // Keep imports in declaration order
)
```

Preserves import order:

```scala
import zio.blocks.codegen.emit._
ScalaEmitter.emit(file, config)
// res13: String = """package com.example
// 
// import zio.*
// import scala.collection.Seq
// import scala.*
// 
// case class Data(
//   items: Seq[String],
// )
// """
```

### Example 6: Custom Combination

Combining multiple preferences:

```scala
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

val file = ScalaFile(
  packageDecl = PackageDecl("com.myproject"),
  imports = List(
    Import.WildcardImport("zio")
  ),
  types = List(
    SealedTrait(
      "Response",
      cases = List(
        SealedTraitCase.CaseClassCase(
          CaseClass("Ok", List(
            Field("data", TypeRef.String),
            Field("status", TypeRef.Int)
          ))
        ),
        SealedTraitCase.CaseObjectCase("Error")
      )
    )
  )
)

val config = EmitterConfig(
  indentWidth = 4,
  sortImports = true,
  trailingCommas = false,
  scala3Syntax = false
)
```

Emits:

```scala
import zio.blocks.codegen.emit._
ScalaEmitter.emit(file, config)
// res15: String = """package com.myproject
// 
// import zio._
// 
// sealed trait Response
// 
// object Response {
//     case class Ok(
//         data: String,
//         status: Int
//     ) extends Response
//     case object Error extends Response
// }
// """
```

## Cross-Scala Behavior

The `scala3Syntax` field affects code generation:

### Scala 3 Features

When targeting Scala 3, the emitter uses:

- **Enums**: `enum Color { case Red; case Blue }`
- **Derives (case classes only)**: `derives Show, Eq` (sealed traits and enums do not emit derives)
- **Wildcard imports**: `import scala.collection.*`
- **Rename imports**: `import foo.{bar as baz}`
- **Opaque types**: `opaque type UserId = Long`

### Scala 2 Fallback

When targeting Scala 2, the emitter uses:

- **Sealed traits** instead of enums: `sealed trait Color` + `case object Red`
- **Implicit/given** syntax converts to Scala 2 compatible form
- **Wildcard imports**: `import scala.collection._`
- **Rename imports**: `import foo.{bar => baz}`
- **Type aliases** instead of opaque types: `type UserId = Long`
