# Smithy

> `zio-blocks-smithy` is a **Smithy IDL parser and AST library** providing a complete representation of Smithy 2.0 API models. It enables parsing Smithy IDL text into rich data structures, querying shape definitions, and pretty-printing models back to valid IDL syntax—all without external dependencies.

## Installation

Add the library to your build configuration:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-smithy" % "0.0.56"
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
      │       ├─ EnumShape(members: List[EnumMember])
      │       ├─ ResourceShape(identifiers, create, read, update, delete, list, ...)
      │       ├─ StringShape, BooleanShape, IntegerShape, and 10 more simple shapes
      │       └─ ... (20 subtypes in total — see the Shape Catalog below)
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

## Shape Catalog

`Shape` is a sealed trait with 20 subtypes, covering the shape categories the Smithy specification defines. Every shape carries a `Shape#name` and a list of applied `Shape#traits`; the families differ in what else they hold:

```scala
sealed trait Shape {
  def name: String
  def traits: List[TraitApplication]
}
```

| Family    | Subtypes | What they add                            |
| --------- | -------: | ---------------------------------------- |
| Simple    |       13 | Nothing — name and traits only            |
| Enum      |        2 | A member list of allowed values           |
| Aggregate |        4 | Member definitions naming target shapes   |
| Service   |        3 | `ShapeId` references to other shapes      |

A parsed shape is wrapped in a `ShapeDefinition`, which pairs the name with the shape. `Shape` also carries its own `Shape#name`, so the two agree and either can be read:

```scala
final case class ShapeDefinition(name: String, shape: Shape)
```

### Simple Shapes

Thirteen shapes carry no structure beyond their name and traits. They differ only in which IDL keyword produces them and what the target protocol is expected to do with them:

| Type              | IDL keyword  | Represents                        |
| ----------------- | ------------ | --------------------------------- |
| `BlobShape`       | `blob`       | Arbitrary binary data              |
| `BooleanShape`    | `boolean`    | True/false values                  |
| `StringShape`     | `string`     | UTF-8 text                         |
| `ByteShape`       | `byte`       | 8-bit signed integer               |
| `ShortShape`      | `short`      | 16-bit signed integer              |
| `IntegerShape`    | `integer`    | 32-bit signed integer              |
| `LongShape`       | `long`       | 64-bit signed integer              |
| `FloatShape`      | `float`      | Single-precision IEEE 754          |
| `DoubleShape`     | `double`     | Double-precision IEEE 754          |
| `BigIntegerShape` | `bigInteger` | Arbitrarily large signed integer   |
| `BigDecimalShape` | `bigDecimal` | Arbitrary-precision decimal        |
| `TimestampShape`  | `timestamp`  | A point in time                    |
| `DocumentShape`   | `document`   | Protocol-agnostic open content     |

Because they share one shape, parsing them produces values that differ only in their type:

```scala
import zio.blocks.smithy._

val simpleModel = SmithyModel.parse(
  """$version: "2"
    |namespace com.example
    |blob Payload
    |timestamp CreatedAt
    |document Metadata
    |""".stripMargin
).toOption.get
```

Each definition names the shape and holds the corresponding subtype:

```scala
simpleModel.shapes
// res1: List[ShapeDefinition] = List(
//   ShapeDefinition(
//     name = "Payload",
//     shape = BlobShape(name = "Payload", traits = List())
//   ),
//   ShapeDefinition(
//     name = "CreatedAt",
//     shape = TimestampShape(name = "CreatedAt", traits = List())
//   ),
//   ShapeDefinition(
//     name = "Metadata",
//     shape = DocumentShape(name = "Metadata", traits = List())
//   )
// )
```

`DocumentShape` is the one to reach for when a field's contents are not known at model time — it is the Smithy equivalent of an open JSON value, and no member list constrains it.

### Enum Shapes

`EnumShape` and `IntEnumShape` each hold a fixed set of permitted values. They differ in the value type and in whether the value is optional:

```scala
final case class EnumShape(
  name: String,
  traits: List[TraitApplication] = Nil,
  members: List[EnumMember] = Nil
) extends Shape

final case class IntEnumShape(
  name: String,
  traits: List[TraitApplication] = Nil,
  members: List[IntEnumMember] = Nil
) extends Shape
```

`EnumMember` carries an `Option[String]`, because the IDL allows a bare member name; `IntEnumMember` carries a required `Int`, because an integer enum has no name to fall back on:

```scala
final case class EnumMember(name: String, value: Option[String] = None, traits: List[TraitApplication] = Nil)
final case class IntEnumMember(name: String, value: Int, traits: List[TraitApplication] = Nil)
```

A string enum with explicit values fills in each `value`:

```scala
val colorModel = SmithyModel.parse(
  """$version: "2"
    |namespace com.example
    |enum Color {
    |    RED = "red"
    |    GREEN = "green"
    |}
    |""".stripMargin
).toOption.get
```

Each member pairs the declared name with the string it maps to:

```scala
colorModel.shapes
// res2: List[ShapeDefinition] = List(
//   ShapeDefinition(
//     name = "Color",
//     shape = EnumShape(
//       name = "Color",
//       traits = List(),
//       members = List(
//         EnumMember(name = "RED", value = Some("red"), traits = List()),
//         EnumMember(name = "GREEN", value = Some("green"), traits = List())
//       )
//     )
//   )
// )
```

Omitting the values leaves `value` absent rather than duplicating the name, so a consumer wanting the effective wire value reads `EnumMember#value` and falls back to `EnumMember#name`:

```scala
val suitModel = SmithyModel.parse(
  """$version: "2"
    |namespace com.example
    |enum Suit {
    |    CLUB
    |    HEART
    |}
    |""".stripMargin
).toOption.get
```

The distinction survives parsing, which is what lets a round trip reproduce the original document:

```scala
suitModel.shapes
// res3: List[ShapeDefinition] = List(
//   ShapeDefinition(
//     name = "Suit",
//     shape = EnumShape(
//       name = "Suit",
//       traits = List(),
//       members = List(
//         EnumMember(name = "CLUB", value = None, traits = List()),
//         EnumMember(name = "HEART", value = None, traits = List())
//       )
//     )
//   )
// )
```

An integer enum requires a value for every member, so `IntEnumMember` holds an `Int` rather than an option:

```scala
val cardModel = SmithyModel.parse(
  """$version: "2"
    |namespace com.example
    |intEnum FaceCard {
    |    JACK = 11
    |    QUEEN = 12
    |}
    |""".stripMargin
).toOption.get
```

Reading the members gives the integers directly:

```scala
cardModel.shapes
// res4: List[ShapeDefinition] = List(
//   ShapeDefinition(
//     name = "FaceCard",
//     shape = IntEnumShape(
//       name = "FaceCard",
//       traits = List(),
//       members = List(
//         IntEnumMember(name = "JACK", value = 11, traits = List()),
//         IntEnumMember(name = "QUEEN", value = 12, traits = List())
//       )
//     )
//   )
// )
```

### Aggregate Shapes

Four shapes compose other shapes, and all of them do it through `MemberDefinition` — a name, the `ShapeId` of the target, and any traits on the member itself:

| Type             | IDL keyword | Members                                              |
| ---------------- | ----------- | ---------------------------------------------------- |
| `ListShape`      | `list`      | `member: MemberDefinition`                            |
| `MapShape`       | `map`       | `key` and `value`, both `MemberDefinition`             |
| `StructureShape` | `structure` | `members: List[MemberDefinition]`                     |
| `UnionShape`     | `union`     | `members: List[MemberDefinition]`, one set at a time   |

A structure's members name their targets by `ShapeId`, not by nested shape, so the model stays flat and a member's target is resolved by lookup:

```scala
val userModel = SmithyModel.parse(
  """$version: "2"
    |namespace com.example
    |structure User {
    |    @required
    |    id: String
    |    tags: TagList
    |}
    |list TagList {
    |    member: String
    |}
    |""".stripMargin
).toOption.get
```

Both shapes appear at the top level, and the `tags` member of `User` points at `TagList` by reference:

```scala
userModel.shapes
// res5: List[ShapeDefinition] = List(
//   ShapeDefinition(
//     name = "User",
//     shape = StructureShape(
//       name = "User",
//       traits = List(),
//       members = List(
//         MemberDefinition(
//           name = "id",
//           target = ShapeId(namespace = "", name = "String"),
//           traits = List(
//             TraitApplication(
//               id = ShapeId(namespace = "smithy.api", name = "required"),
//               value = None
//             )
//           )
//         ),
//         MemberDefinition(
//           name = "tags",
//           target = ShapeId(namespace = "", name = "TagList"),
//           traits = List()
//         )
//       )
//     )
//   ),
//   ShapeDefinition(
//     name = "TagList",
//     shape = ListShape(
//       name = "TagList",
//       traits = List(),
//       member = MemberDefinition(
//         name = "member",
//         target = ShapeId(namespace = "", name = "String"),
//         traits = List()
//       )
//     )
//   )
// )
```

:::note[`member` has no default, `traits` does]
`ListShape` and `MapShape` declare their `Shape#traits` parameter with a default *before* the parameters that have none, so positional construction does not work — `ListShape("TagList", member = m)` compiles while `ListShape("TagList", m)` does not. `StructureShape` and `UnionShape` default their member lists, so both forms work there.
:::

### Service Shapes

`ServiceShape`, `OperationShape`, and `ResourceShape` describe an API rather than a value, and all of their cross-references are `ShapeId`s:

```scala
final case class ServiceShape(
  name: String,
  traits: List[TraitApplication] = Nil,
  version: Option[String] = None,
  operations: List[ShapeId] = Nil,
  resources: List[ShapeId] = Nil,
  errors: List[ShapeId] = Nil
) extends Shape

final case class OperationShape(
  name: String,
  traits: List[TraitApplication] = Nil,
  input: Option[ShapeId] = None,
  output: Option[ShapeId] = None,
  errors: List[ShapeId] = Nil
) extends Shape
```

`ResourceShape` is the largest shape in the module, because a Smithy resource binds identifiers, five named lifecycle operations, and three further reference lists:

| Field                  | Type                   | Meaning                                           |
| ---------------------- | ---------------------- | ------------------------------------------------- |
| `identifiers`          | `Map[String, ShapeId]`  | Identifier names mapped to their target shapes     |
| `create`               | `Option[ShapeId]`       | Create lifecycle operation                         |
| `read`                 | `Option[ShapeId]`       | Read lifecycle operation                           |
| `update`               | `Option[ShapeId]`       | Update lifecycle operation                         |
| `delete`               | `Option[ShapeId]`       | Delete lifecycle operation                         |
| `list`                 | `Option[ShapeId]`       | List lifecycle operation                           |
| `operations`           | `List[ShapeId]`         | Instance operations that are not lifecycle ones     |
| `collectionOperations` | `List[ShapeId]`         | Operations on the collection rather than one item   |
| `resources`            | `List[ShapeId]`         | Child resources                                    |

The five lifecycle fields are separate rather than a map, which is what makes "does this resource support deletion?" a field access instead of a lookup:

```scala
val resourceModel = SmithyModel.parse(
  """$version: "2"
    |namespace com.example
    |resource FooResource {
    |    identifiers: {id: FooId}
    |    read: GetFoo
    |    list: ListFoos
    |}
    |""".stripMargin
).toOption.get
```

Unset lifecycle operations stay `None`, so an absent `create` is distinguishable from one bound to an operation:

```scala
resourceModel.shapes
// res6: List[ShapeDefinition] = List(
//   ShapeDefinition(
//     name = "FooResource",
//     shape = ResourceShape(
//       name = "FooResource",
//       traits = List(),
//       identifiers = Map("id" -> ShapeId(namespace = "", name = "FooId")),
//       create = None,
//       read = Some(ShapeId(namespace = "", name = "GetFoo")),
//       update = None,
//       delete = None,
//       list = Some(ShapeId(namespace = "", name = "ListFoos")),
//       operations = List(),
//       collectionOperations = List(),
//       resources = List()
//     )
//   )
// )
```

### Shape References

Every cross-shape reference is a `ShapeRef`, a sealed trait with exactly two cases:

```scala
sealed trait ShapeRef

final case class ShapeId(namespace: String, name: String) extends ShapeRef

object ShapeId {
  final case class Member(shape: ShapeId, memberName: String) extends ShapeRef
  def parse(s: String): Either[String, ShapeRef]
}
```

`ShapeRef` exists to give `ShapeId` and `ShapeId.Member` a common supertype without a Scala 3 union type, so the same signatures compile on 2.13.

Rendering follows the IDL: a shape is `namespace#name`, and a member appends `$` and the member name:

```scala
ShapeId("com.example", "User").toString
// res7: String = "com.example#User"
ShapeId.Member(ShapeId("com.example", "User"), "id").toString
// res8: String = "com.example#User$id"
```

`ShapeId.parse` reads either form back, choosing the case by whether a `$` is present:

```scala
ShapeId.parse("com.example#User")
// res9: Either[String, ShapeRef] = Right(
//   ShapeId(namespace = "com.example", name = "User")
// )
ShapeId.parse("com.example#User$id")
// res10: Either[String, ShapeRef] = Right(
//   Member(
//     shape = ShapeId(namespace = "com.example", name = "User"),
//     memberName = "id"
//   )
// )
```

Malformed input is reported rather than thrown, and the message names the rule that failed:

```scala
ShapeId.parse("User")
// res11: Either[String, ShapeRef] = Left(
//   "ShapeId must contain '#' separator, got: User"
// )
ShapeId.parse("com.example#User$id$extra")
// res12: Either[String, ShapeRef] = Left(
//   "Member reference must have exactly one separator, got: com.example#User$id$extra"
// )
```

#### Resolving a Reference

A reference names a shape; it does not contain one. Resolving means looking the target up in the model, which `SmithyModel#findShape` does by **name** rather than by `ShapeId`:

```scala
final case class SmithyModel(...) {
  def findShape(name: String): Option[ShapeDefinition]
  def allShapeIds: List[ShapeId]
}
```

Following a structure member to its target definition is therefore a lookup on the name inside the `ShapeId`:

```scala
val tagsTarget = userModel.shapes
  .collectFirst { case ShapeDefinition("User", s: StructureShape) => s }
  .flatMap(_.members.find(_.name == "tags"))
  .map(_.target)
```

The member's target resolves to the `list` shape declared alongside it:

```scala
tagsTarget
// res13: Option[ShapeId] = Some(ShapeId(namespace = "", name = "TagList"))
tagsTarget.flatMap(id => userModel.findShape(id.name))
// res14: Option[ShapeDefinition] = Some(
//   ShapeDefinition(
//     name = "TagList",
//     shape = ListShape(
//       name = "TagList",
//       traits = List(),
//       member = MemberDefinition(
//         name = "member",
//         target = ShapeId(namespace = "", name = "String"),
//         traits = List()
//       )
//     )
//   )
// )
```

Looking up by name rather than by `ShapeId` is not a shortcut — it matches what the parser produces. An IDL target written without a namespace prefix becomes a `ShapeId` whose namespace is the **empty string**, not the model's namespace and not `smithy.api`:

```scala
userModel.shapes.collect { case ShapeDefinition(_, s: ListShape) => s.member.target }
// res15: List[ShapeId] = List(ShapeId(namespace = "", name = "String"))
resourceModel.shapes.collect { case ShapeDefinition(_, s: ResourceShape) => s.identifiers }
// res16: List[Map[String, ShapeId]] = List(
//   Map("id" -> ShapeId(namespace = "", name = "FooId"))
// )
```

Trait identifiers are the exception: the parser resolves those against the prelude, so `@required` arrives fully qualified:

```scala
userModel.shapes
  .collect { case ShapeDefinition("User", s: StructureShape) => s }
  .flatMap(_.members.flatMap(_.traits.map(_.id)))
// res17: List[ShapeId] = List(
//   ShapeId(namespace = "smithy.api", name = "required")
// )
```

Two consequences follow. A reference to a prelude shape such as `String` has no definition in the parsed model, so resolving it yields nothing — a consumer generating code has to recognize prelude names itself:

```scala
userModel.findShape("String")
// res18: Option[ShapeDefinition] = None
```

And a `ShapeId` taken from a parsed model does not necessarily round-trip through `ShapeId.parse`, because the renderer emits the empty namespace as a bare `#` that the parser then rejects:

```scala
ShapeId("", "String").toString
// res19: String = "#String"
ShapeId.parse(ShapeId("", "String").toString)
// res20: Either[String, ShapeRef] = Left("ShapeId namespace cannot be empty")
```

:::warning[Namespaces on references are not populated]
Because unprefixed targets carry an empty namespace, comparing `ShapeId#namespace` on a parsed reference tells you nothing unless the IDL spelled the namespace out. `SmithyModel#findShape` matching on name alone is consistent with that, but it also means a model that uses a `use` statement to import `other.ns#User` while declaring its own `User` cannot distinguish the two by reference alone.
:::

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
