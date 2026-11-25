# Schema Methods

> Reference for the methods of Schema[A]: combinators, defaults, validation, conversion, and diff and patch.

Every `Schema[A]` provides the methods below. They are grouped by purpose. Each example uses the same `Person` model:

```scala
import zio.schema._

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}

val john = Person("John", 42)
```

## Building Schemas

Combinators build a new schema from existing ones. `Schema#optional`, written `?`, accepts a missing value. `Schema#zip`, written `<*>`, pairs two schemas into a tuple schema. `Schema#orElseEither`, written `<+>`, accepts a value of either schema, as an `Either`. `Schema#repeated` returns a schema for a `Chunk` of values. Each combinator returns a new schema for the combined type:

```scala
val maybeAge: Schema[Option[Int]] = Schema[Int].?

val pair: Schema[(String, Int)] = Schema[String] <*> Schema[Int]

val either: Schema[Either[String, Int]] = Schema[String] <+> Schema[Int]

val names: Schema[zio.Chunk[String]] = Schema[String].repeated
```

## Defaults and Annotations

`Schema#defaultValue` returns the default value of the schema. `Schema#default` returns a copy of a primitive schema with a different default; other kinds of schemas, such as records and `Transform` schemas, ignore the value it sets. `Schema#annotate` attaches an annotation of any type, and `Schema#annotations` lists the annotations of the schema. Schema annotations, such as `fieldName`, are covered in the [Schema Annotations](../schema-annotations.md) page. A primitive default set with `Schema#default` shows up in `Schema#defaultValue`, and the built-in `Schema[Int]` carries no annotations:

```scala
Schema[Int].default(7).defaultValue
// res0: Either[String, Int] = Right(value = 7)

Schema[Int].annotations.isEmpty
// res1: Boolean = true
```

For the default of each kind of schema, and for how to set a default on a case class field, see [Getting The Default Value](../operations/the-default-value.md).

## Validation

`Schema#validate` checks a value against the rules that its fields carry and returns the failed checks as a `Chunk`. An empty chunk means that the value passes. It checks the value with the implicit `Schema[A]` in scope, which is usually the schema you call it on. The [Validation](../operations/validating-types.md) page explains how rules are attached and checked, and the [Validation reference](../operations/validating-types.md) lists the rules and their combinators. The `Person` model has no rules, so `john` passes:

```scala
Person.schema.validate(john).isEmpty
// res2: Boolean = true
```

:::caution
`Schema#validation` stores a `Validation` rule as an annotation on the schema, but `Schema#validate` never reads that annotation, so the rule is not checked. Attach rules to case class fields with the `@validate` annotation instead, or call `Validation#validate` on the value directly.
:::

## Transformations

`Schema#transform` maps a schema of `A` to a schema of `B`, given a function in each direction. `Schema#transformOrFail` does the same, but each function can fail with a message. These methods are covered in [Transforming Schemas](../operations/transforming-schemas.md). The following schema views a `Person` as its name and fills in an age of `0` on the way back:

```scala
val nameOnly: Schema[String] = Person.schema.transform(_.name, name => Person(name, 0))
```

The default of the new schema is the default `Person` passed through the forward function:

```scala
nameOnly.defaultValue
// res3: Either[String, String] = Right(value = "")
```

## Conversion Through DynamicValue

`Schema#toDynamic` turns a value into a `DynamicValue`, and `Schema#fromDynamic` turns it back. `DynamicValue` is covered in [Dynamic Data Representation](../operations/dynamic-data-representation.md) and in the [DynamicValue reference](../operations/dynamic-data-representation.md). A round trip through `DynamicValue` gives back the original value:

```scala
val dynamic = Person.schema.toDynamic(john)
// dynamic: DynamicValue = Record(
//   id = Nominal(
//     packageName = IndexedSeq("repl"),
//     objectNames = IndexedSeq("MdocSession", "MdocApp"),
//     typeName = "Person"
//   ),
//   values = ListMap(
//     "name" -> Primitive(value = "John", standardType = string),
//     "age" -> Primitive(value = 42, standardType = int)
//   )
// )

Person.schema.fromDynamic(dynamic) == Right(john)
// res4: Boolean = true
```

## Migration

`Schema#migrate` returns a function that converts values of this schema to values of another schema, when the two schemas have a clear mapping. `Schema#coerce` returns a schema for the new type when migrations exist in both directions, so values can convert back and forth. Both methods succeed for some changes, such as an added field, whose conversion function then fails on every value. See [Schema Migration](../operations/schema-migration.md) for the changes that work and the full process.

## Diff and Patch

`Schema#diff` compares two values and returns a `Patch`. `Schema#patch` applies a patch to an old value. See [Diffing and Patching](../operations/diffing-and-patching.md) for details, and [Patch and Differ](patch-and-differ.md) for the `Patch` cases. Applying the diff of two values to the first value gives the second:

```scala
val older = john.copy(age = 43)
// older: Person = Person(name = "John", age = 43)

Person.schema.patch(john, Person.schema.diff(john, older)) == Right(older)
// res5: Boolean = true
```

## Ordering and Structure

`Schema#ordering` returns a Scala `Ordering` for the type, which is useful for sorting. Records compare field by field in declaration order, enumerations compare by the order of their cases, and sequences compare element by element. Each comparison converts both values to `DynamicValue` first. `Schema#ast` returns the [`MetaSchema`](../operations/serialization-of-the-schema-itself.md) tree that describes the structure of the schema. Sorting two people orders them by name, the first field:

```scala
List(john, Person("Ann", 30)).sorted(Person.schema.ordering).map(_.name)
// res6: List[String] = List("Ann", "John")
```

:::caution
`Set`, `Map`, `NonEmptyChunk`, `NonEmptySet`, `NonEmptyMap`, and `Fallback` values have no ordering rule, so `Schema#ordering` treats any two of them as equal. For example, `Schema[Set[Int]].ordering.compare(Set(1), Set(2))` returns `0`, and a record that differs only in such a field compares as equal. Sort such values with your own `Ordering`.
:::

`Schema#serializable` is meant to return a schema that describes the schema itself, but it does not work in the current version. To send a schema over the wire, encode `Schema#ast` with `MetaSchema.schema`, as shown in [Serialization of the Schema Itself](../operations/serialization-of-the-schema-itself.md).
