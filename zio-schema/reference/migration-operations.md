# Migration Operations

> Reference for the Migration cases that change a DynamicValue, with an example of each operation that applies to values.

A `Migration` is one step that changes a `DynamicValue` so that it matches a new schema. Several steps can be combined in one `Chunk`. The [Schema Migration](../operations/schema-migration.md) page shows how to run them and how `Migration.derive` produces them. This page lists each operation and what it does to a value. The examples apply steps to the same `Person1` value through a small `applied` helper:

```scala
import zio.Chunk
import zio.schema._
import zio.schema.meta.{Migration, MetaSchema, NodePath}

case class Person1(name: String, age: Int)

object Person1 {
  implicit val schema: Schema[Person1] = DeriveSchema.gen[Person1]
}

val person1: DynamicValue = Person1.schema.toDynamic(Person1("John Doe", 42))

def applied(migrations: Migration*): Either[String, DynamicValue] =
  person1.transform(Chunk.fromIterable(migrations))
```

## Operations on Fields

`DeleteNode` removes the node at a path. Here, the `age` field disappears from the value:

```scala
applied(Migration.DeleteNode(NodePath.root / "age"))
// res0: Either[String, DynamicValue] = Right(
//   value = Record(
//     id = Nominal(
//       packageName = IndexedSeq("repl"),
//       objectNames = IndexedSeq("MdocSession", "MdocApp"),
//       typeName = "Person1"
//     ),
//     values = ListMap(
//       "name" -> Primitive(value = "John Doe", standardType = string)
//     )
//   )
// )
```

`AddNode` describes a new node with the shape given by a `MetaSchema`. In the current version, applying `AddNode` always fails, because the step has no value to put in the new node. The following step tries to add a `nickname` field, and its result shows that error:

```scala
applied(Migration.AddNode(NodePath.root / "nickname", MetaSchema.fromSchema(Schema[String])))
// res1: Either[String, DynamicValue] = Left(
//   value = "Cannot add node at path nickname: No default value is available"
// )
```

`ChangeType` describes a change of the standard type of the primitive at a path. In the current version, no type conversions are implemented, so applying `ChangeType` always fails. The following step tries to change `age` from an integer to a double, and the library reports that no conversion is available:

```scala
applied(Migration.ChangeType(NodePath.root / "age", StandardType.DoubleType))
// res2: Either[String, DynamicValue] = Left(
//   value = "Cannot change type of node at path age: No type conversion is available"
// )
```

`Optional` wraps the value at a path in an option, which turns a required field into an optional one. The following step makes `age` optional, and the result has the field inside an option:

```scala
applied(Migration.Optional(NodePath.root / "age"))
// res3: Either[String, DynamicValue] = Right(
//   value = Record(
//     id = Nominal(
//       packageName = IndexedSeq("repl"),
//       objectNames = IndexedSeq("MdocSession", "MdocApp"),
//       typeName = "Person1"
//     ),
//     values = ListMap(
//       "name" -> Primitive(value = "John Doe", standardType = string),
//       "age" -> SomeValue(value = Primitive(value = 42, standardType = int))
//     )
//   )
// )
```

`Require` is the reverse. It makes an optional node required, and it fails on a node that is not optional. The following step applies it to `age`, which is not optional, so the step fails:

```scala
applied(Migration.Require(NodePath.root / "age"))
// res4: Either[String, DynamicValue] = Left(
//   value = "Failed to require leaf at path age: Expected optional value at lead"
// )
```

## Operations on Values

`UpdateFail` replaces the failure message of a node that is a failure. It fails on any other node. The following step applies it to `age`, which is an integer, so the step fails:

```scala
applied(Migration.UpdateFail(NodePath.root / "age", "age can no longer be converted"))
// res5: Either[String, DynamicValue] = Left(
//   value = "Failed to update fail message at age. Unexpected type"
// )
```

`AddCase` records that an enum has a new case. Applying it leaves the value unchanged, since an existing value never uses the new case.

`IncrementDimensions` and `DecrementDimensions` add or remove a level of sequence nesting at a path. They are produced when a field changes from a single value to a collection, or back.

`Recursive` applies a migration to a recursive type, where a node refers to a part of the value that repeats. The path given to `Recursive` is relative to the repeated part, so one migration covers every level.

`Relabel` renames a node. It takes a `LabelTransformation`, and this version of the library provides no public cases for that type, so applications should not construct `Relabel` directly.

## Running Several Steps

`DynamicValue#transform` takes a `Chunk[Migration]` and applies the steps to a value. It stops at the first step that fails and returns its error. [Manual Migration](../operations/schema-migration.md#manual-migration) shows a complete example that runs a step and reads the result back as a typed value.

:::caution
`DynamicValue#transform` applies the steps from the last one in the chunk to the first one. The order matters when two steps touch the same node. For example, to make `age` optional and then required again, `Optional` must come last in the chunk, because it runs first:

```scala
applied(Migration.Require(NodePath.root / "age"), Migration.Optional(NodePath.root / "age"))
// res6: Either[String, DynamicValue] = Right(
//   value = Record(
//     id = Nominal(
//       packageName = IndexedSeq("repl"),
//       objectNames = IndexedSeq("MdocSession", "MdocApp"),
//       typeName = "Person1"
//     ),
//     values = ListMap(
//       "name" -> Primitive(value = "John Doe", standardType = string),
//       "age" -> Primitive(value = 42, standardType = int)
//     )
//   )
// )
```

In the opposite order, `Require` runs first on a value that is not optional, and the whole transform fails.
:::
