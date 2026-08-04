# Smithy

> `zio-blocks-smithy` is a **Smithy IDL parser and AST library** providing a complete representation of Smithy 2.0 API models. It enables parsing Smithy IDL text into rich data structures, querying shape definitions, and pretty-printing models back to valid IDL syntax—all without external dependencies.

`zio-blocks-smithy` is a **Smithy IDL parser and AST library** providing a complete representation of Smithy 2.0 API models. It enables parsing Smithy IDL text into rich data structures, querying shape definitions, and pretty-printing models back to valid IDL syntax—all without external dependencies.

## Installation

Add the library to your build configuration:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-smithy" % "0.0.51"
```

Supported Scala versions: 2.13.x and 3.x

## Quick Start

Parse Smithy IDL text into a model, query shapes, and serialize back:

```scala
import zio.blocks.smithy._

val smithyText = """$version: "2"
namespace com.example.api

structure User {
  @required
  id: String
  name: String
}

operation GetUser {
  input: GetUserInput
  output: User
}

structure GetUserInput {
  @required
  id: String
}
"""

// Parse IDL text into a model
val result = SmithyModel.parse(smithyText)

// Access shapes and data
result match {
  case Right(model) =>
    model.findShape("User").foreach { userDef =>
      println(s"Found shape: ${userDef.name}")
    }
  case Left(error) =>
    println(s"Parse error: ${error.message}")
}

// Serialize back to IDL
result.foreach { model =>
  val idlText = model.prettyPrint
  println(idlText)
}
```

## Core Types

The library provides core types that work together to parse, query, and serialize Smithy models. The main types work together in a parsing → querying → serialization pipeline:

```
Smithy IDL Text
      ↓
SmithyModel.parse (public API)
      ↓
SmithyModel (contains shapes, metadata, traits)
      ├─ shapes: List[ShapeDefinition]
      │   └─ shape: Shape (sealed trait — central type)
      │       ├─ StructureShape(members: List[MemberDefinition])
      │       ├─ ListShape(member: MemberDefinition)
      │       ├─ MapShape(key: MemberDefinition, value: MemberDefinition)
      │       ├─ ServiceShape(operations, resources, errors)
      │       ├─ OperationShape(input, output, errors)
      │       ├─ UnionShape(members: List[MemberDefinition])
      │       ├─ StringShape, BooleanShape, IntegerShape, etc.
      │       └─ ... (and other shape subtypes)
      ├─ MemberDefinition(name: String, target: ShapeId, traits: List[TraitApplication])
      ├─ TraitApplication(id: ShapeId, value: Option[NodeValue])
      ├─ ShapeId (namespace + name identifier)
      └─ NodeValue (metadata values: String, Number, Boolean, Array, Object, Null)
            ↓
SmithyModel.prettyPrint (public API)
      ↓
Smithy IDL Text
```

The root container for a Smithy model. Contains version, namespace, shapes, metadata, and trait applications. The case class and companion object expose the following API:

```scala
case class SmithyModel(
  version: String,           // Smithy version (e.g., "2")
  namespace: String,
  useStatements: List[ShapeId],
  metadata: Map[String, NodeValue],
  shapes: List[ShapeDefinition],
  applyStatements: List[ApplyStatement] = Nil
) {
  def findShape(name: String): Option[ShapeDefinition]
  def allShapeIds: List[ShapeId]
  def prettyPrint: String
  def prettyPrint(indent: Int): String
}

object SmithyModel {
  def parse(input: String): Either[SmithyError, SmithyModel]
}
```

## Parsing

Parse Smithy IDL text into structured models using `SmithyModel.parse`, handle errors, and validate round-trips.

### Basic Parsing

Parse Smithy IDL text and handle the result:

```scala
import zio.blocks.smithy._

val smithyText = """$version: "2"
namespace com.example

string Name
"""

SmithyModel.parse(smithyText) match {
  case Right(model) =>
    println(s"Parsed ${model.shapes.length} shapes")
  case Left(error) =>
    println(s"Error: ${error.message}")
}
```

### Handling Parse Errors

Access error details including line and column information when parsing fails. `SmithyError` provides detailed context to help locate and fix issues in your Smithy definitions:

```scala
import zio.blocks.smithy._

val invalidSmithy = """$version: "2"
namespace com.example

structure User {
  invalid syntax
}
"""

SmithyModel.parse(invalidSmithy) match {
  case Right(_) =>
    println("Unexpected success")
  case Left(error) =>
    println(s"Error at line ${error.line}, column ${error.column}: ${error.message}")
}
```

### Round-Trip Validation

Verify a model parses correctly by round-tripping (parse → serialize → parse again):

```scala
import zio.blocks.smithy._

val original = """$version: "2"
namespace com.example

string MyString
"""

val parsed = SmithyModel.parse(original)
val reprinted = parsed.map(_.prettyPrint)
val reparsed = reprinted.flatMap(SmithyModel.parse)

println(reparsed.isRight)  // true if round-trip succeeds
```

## Querying & Traversing Shapes

Once you have a parsed model, query shapes by name, pattern match on shape types, and traverse their members.

### Finding Shapes

Locate shapes by name or retrieve all shape identifiers:

```scala
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace example

structure User {
  id: String
  name: String
}
""").toOption.get

// Find by name
model.findShape("User").foreach { shapeDef =>
  println(s"Found: ${shapeDef.name}")
}

// Get all shape IDs
val allIds = model.allShapeIds
println(s"Total shapes: ${allIds.length}")
```

### Pattern Matching on Shapes

Determine shape type and access type-specific properties:

```scala
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace example

structure User { id: String }
list UserIds { member: String }
""").toOption.get

model.findShape("User").foreach { shapeDef =>
  shapeDef.shape match {
    case struct: StructureShape =>
      println(s"Structure with ${struct.members.length} members")
    case list: ListShape =>
      println(s"List of ${list.member.target}")
    case _ =>
      println("Other shape type")
  }
}
```

### Traversing Members

Iterate over structure/union members and inspect their traits:

```scala
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace example

structure User {
  @required
  id: String
  name: String
}
""").toOption.get

model.findShape("User").foreach { shapeDef =>
  shapeDef.shape match {
    case struct: StructureShape =>
      struct.members.foreach { member =>
        val required = member.traits.exists(_.id.name == "required")
        println(s"${member.name}: ${member.target} (required: $required)")
      }
    case _ => ()
  }
}
```

## Building Models Programmatically

Construct Smithy models in code by creating shapes, adding traits, and assembling them into a complete model.

### Creating Shapes

Programmatically construct shapes and assemble them into a complete model:

```scala
import zio.blocks.smithy._

val userStructure = StructureShape(
  "User",
  traits = Nil,
  members = List(
    MemberDefinition(
      "id",
      ShapeId("smithy.api", "String"),
      traits = List(TraitApplication.required)
    ),
    MemberDefinition(
      "name",
      ShapeId("smithy.api", "String"),
      traits = Nil
    )
  )
)

val model = SmithyModel(
  version = "2",
  namespace = "com.example",
  useStatements = Nil,
  metadata = Map.empty,
  shapes = List(ShapeDefinition("User", userStructure))
)
```

### Adding Traits

Attach metadata traits to shapes during construction. `TraitApplication` provides companion object helper methods like `required`, `documentation`, and others for common traits:

```scala
import zio.blocks.smithy._

val serviceShape = ServiceShape(
  "UserService",
  traits = List(
    TraitApplication.documentation("User management API")
  ),
  version = Some("1.0"),
  operations = List(
    ShapeId("com.example", "GetUser"),
    ShapeId("com.example", "CreateUser")
  ),
  resources = Nil,
  errors = Nil
)
```

## Serializing Models

Convert models back to valid Smithy IDL text using `prettyPrint`, with options for custom formatting.

### Basic Serialization

Convert a model to valid Smithy IDL text:

```scala
import zio.blocks.smithy._

val model = SmithyModel(
  version = "2",
  namespace = "com.example",
  useStatements = Nil,
  metadata = Map.empty,
  shapes = List(
    ShapeDefinition("Name", StringShape("Name"))
  )
)

val idlText = model.prettyPrint
println(idlText)
```

### Custom Indentation

Control indentation width when serializing models:

```scala
import zio.blocks.smithy._

val model = SmithyModel(
  version = "2",
  namespace = "com.example",
  useStatements = Nil,
  metadata = Map.empty,
  shapes = List(
    ShapeDefinition("Data", StructureShape(
      "Data",
      traits = Nil,
      members = List(
        MemberDefinition("field1", ShapeId("smithy.api", "String")),
        MemberDefinition("field2", ShapeId("smithy.api", "String"))
      )
    ))
  )
)

val compact = model.prettyPrint(indent = 2)
val verbose = model.prettyPrint(indent = 8)
```

## Common Use-Cases

See how to apply Smithy parsing and querying to real-world workflows: code generation, validation, and model transformation.

### Use-Case 1: Code Generation

Load a Smithy model and generate code for each operation:

```scala
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace api

service MyService {
  operations: [GetUser, CreateUser]
}

@http(method: "GET", uri: "/users/{id}")
operation GetUser {
  input: GetUserInput
  output: User
}

@http(method: "POST", uri: "/users")
operation CreateUser {
  input: CreateUserInput
  output: User
}

structure User { id: String, name: String }
structure GetUserInput { @required id: String }
structure CreateUserInput { @required name: String }
""").toOption.get

// Generate code stubs for each operation by pattern matching:

model.shapes.foreach { shapeDef =>
  shapeDef.shape match {
    case op: OperationShape =>
      println(s"// Generate operation: ${op.name}")
      op.input.foreach(in => println(s"//   input: ${in.name}"))
      op.output.foreach(out => println(s"//   output: ${out.name}"))
    case _ => ()
  }
}
```

### Use-Case 2: Validation & Analysis

Find deprecated shapes and analyze trait coverage:

```scala
import zio.blocks.smithy._

val model = SmithyModel.parse("""$version: "2"
namespace example

@deprecated
structure LegacyUser { id: String }

structure ModernUser {
  @required
  id: String
  email: String
}
""").toOption.get

// Find all deprecated shapes:

val deprecated = model.shapes.filter { shapeDef =>
  shapeDef.shape.traits.exists(_.id.name == "deprecated")
}

println(s"Deprecated shapes: ${deprecated.map(_.name)}")
```

### Use-Case 3: Model Transformation

Parse, modify, and re-serialize a model with updated metadata:

```scala
import zio.blocks.smithy._

val original = """$version: "2"
namespace com.example

string UserId
"""

val modified = SmithyModel.parse(original).map { model =>
  // Add metadata to the model:

  val newMetadata = model.metadata + ("version" -> NodeValue.String("1.0"))
  model.copy(metadata = newMetadata)
}

modified.foreach { model =>
  println(model.prettyPrint)
}
```

## Running the Examples

All code from this guide is available as runnable examples in the `smithy-examples` module. Examples demonstrate different aspects of the Smithy library.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Step 1: Basic Parsing and Querying

Parse Smithy IDL text, find shapes by name, and access their structure and metadata:

```bash
sbt "smithy-examples/runMain smithyexample.BasicParsingAndQuerying"
```

### Step 2: Building Models Programmatically

Construct Smithy models in code by creating shapes, adding traits, and assembling them into a complete model:

```bash
sbt "smithy-examples/runMain smithyexample.BuildingModelsAndTraits"
```

### Step 3: Validation and Analysis

Analyze Smithy models for completeness, find deprecated shapes, check for documentation, and validate API contracts:

```bash
sbt "smithy-examples/runMain smithyexample.ValidationAndAnalysis"
```

### Step 4: Complete Example — Book Store API

A comprehensive end-to-end workflow showing a complete book store API model with parsing, entity analysis, error handling, code generation, and statistics:

```bash
sbt "smithy-examples/runMain smithyexample.BookStoreAPI"
```

**3. Or compile all examples at once:**

```bash
sbt "smithy-examples/compile"
```
