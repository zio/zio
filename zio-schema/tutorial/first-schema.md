# Your First Schema

> Learn to derive a schema, encode it as JSON, validate values, and compare two values with ZIO Schema.

Welcome to ZIO Schema! This tutorial is for Scala developers who have never used ZIO Schema before. You need only basic Scala knowledge, such as case classes and companion objects.

## Learning Objectives

By the end of this tutorial, you will understand:

- What a `Schema` is and how `DeriveSchema` creates one from a case class
- How a schema drives encoding to and decoding from JSON
- How validation rules attached to a schema reject bad values
- How `Schema#diff` and `Schema#patch` describe and apply changes between two values

We will learn these concepts through five sections:

1. Describing a Model with a Schema
2. Encoding and Decoding JSON
3. Checking Values with Validation
4. Comparing Values with Diff and Patch
5. Putting the Pieces Together

We recommend reading from top to bottom, because each section builds on the previous one. This is the first of three tutorials: [Evolve a Data Model Safely](evolve-a-data-model.md) and [Working with Dynamic Data](dynamic-data.md) continue from where this one ends.

## Setting Up the Project

The examples need the core module and the JSON codec. Add both to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-schema"      % "1.9.2",
  "dev.zio" %% "zio-schema-json" % "1.9.2"
)
```

The JSON module already brings in `zio-schema-derivation`, which provides `DeriveSchema`. On Scala 2, derivation also needs `scala-reflect` at compile time, as the [Installation](../index.md#installation) section shows.

## 1. Describing a Model with a Schema

A `Schema[A]` is a value that describes the structure of the type `A`: its name, its fields, and the types of those fields. Other ZIO Schema features, such as codecs, validation, and diffing, read this description instead of working with the type directly. In this section, we describe a `Person` case class:

```scala
import zio.Chunk
import zio.schema._
import zio.schema.annotation.validate
import zio.schema.codec.JsonCodec
import zio.schema.validation.Validation

final case class Person(
    @validate(Validation.minLength(1)) name: String,
    @validate(Validation.between(0, 120)) age: Int
)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}
```

The code above does the following:

- `final case class Person(...)` declares the model with two fields, `name` and `age`.
- `@validate(...)` attaches a validation rule to a field. We use it in section 3.
- `DeriveSchema.gen[Person]` builds the `Schema[Person]` by inspecting the case class at compile time.
- The `implicit val schema` in the companion object makes the schema available wherever a `Schema[Person]` is required.

## 2. Encoding and Decoding JSON

Once we have a schema, a codec turns values into bytes and back. The `JsonCodec` module from `zio-schema-json` creates a codec from any schema. We derive the codec once and reuse it:

```scala
val codec = JsonCodec.schemaBasedBinaryCodec[Person]
```

Next, we encode a `Person` into JSON bytes:

```scala
val john  = Person("John", 42)
val bytes = codec.encode(john)
```

`BinaryCodec#encode` returns a `Chunk[Byte]`, which is ZIO's immutable byte sequence. To read the JSON, we turn the bytes into text and print it:

```scala
println(new String(bytes.toArray, "UTF-8"))
// {"name":"John","age":42}
```

To go back, we decode the same bytes:

```scala
codec.decode(bytes)
// res1: Either[zio.schema.codec.DecodeError, Person] = Right(
//   value = Person(name = "John", age = 42)
// )
```

The result is `Right(Person("John", 42))`, so the round trip returns the original value. Decoding can also fail, for example when the input is not valid JSON for this type. In that case `BinaryCodec#decode` returns a `Left` containing a `DecodeError` instead of throwing an exception.

## 3. Checking Values with Validation

The `@validate` annotations from section 1 become part of the schema. The `Schema#validate` method checks a value against those rules and returns the list of rules that failed. A valid value returns an empty result:

```scala
Person.schema.validate(john).isEmpty
// res2: Boolean = true
```

An invalid value returns one entry for each check that fails. The following person has an empty name and an age above the allowed range:

```scala
val invalid = Person("", 130)
// invalid: Person = Person(name = "", age = 130)

Person.schema.validate(invalid).size
// res3: Int = 3
```

The count of 3 is the number of failed checks across both fields: one for the name, and two for the age, because `Validation.between` is built from a "less than" check and an "equal to" check that both fail for 130. Validation does not change the value. It only reports problems, so the calling code decides what to do with them.

## 4. Comparing Values with Diff and Patch

Sometimes we need to know how two values differ, for example to send only the changes over a network. `Schema#diff` compares two values of the same type and returns a `Patch[A]`. `Schema#patch` applies that `Patch` to an old value to produce the new one.

We first make a copy of `john` with a new age, then compute the difference:

```scala
val olderJohn = john.copy(age = 43)

val patch = Person.schema.diff(john, olderJohn)
```

The `Patch` value is a detailed structure, so we do not print it. Instead, we apply it to the old value and compare the result:

```scala
Person.schema.patch(john, patch) == Right(olderJohn)
// res4: Boolean = true
```

The result is `true`, so the patch turns `john` into `olderJohn`. The patch for a record holds one entry per field: a change for `age`, and an "identical" marker for `name`, which did not change.

## 5. Putting the Pieces Together

This small program combines the steps. It validates a person before encoding it:

```scala
def sendPerson(updated: Person): Either[String, Chunk[Byte]] = {
  val problems = Person.schema.validate(updated)

  if (problems.nonEmpty) Left(s"invalid person: ${problems.size} problem(s)")
  else {
    Right(codec.encode(updated))
  }
}
```

The `sendPerson` function checks the value first and returns a `Left` when a rule fails. Otherwise it encodes the whole value with the `codec` from section 2. JSON decoding does not check the rules, so a receiver that wants the same guarantee calls `Schema#validate` again after decoding.

The valid person is encoded, and the invalid one is rejected:

```scala
sendPerson(olderJohn).isRight
// res5: Boolean = true
sendPerson(invalid).isLeft
// res6: Boolean = true
```

## What You've Learned

In this tutorial, you learned:

- What a `Schema[A]` describes and how `DeriveSchema.gen` builds it from a case class
- How `JsonCodec.schemaBasedBinaryCodec` encodes values to JSON bytes and decodes them back
- How `@validate` rules become part of a schema and how `Schema#validate` reports failures
- How `Schema#diff` and `Schema#patch` describe and apply changes

You now have a foundation for the rest of the library. The next step is to change a stored model over time without breaking the JSON that older versions of it wrote.

## Where to Go Next

- **Continue the tutorial series:** [Evolve a Data Model Safely](evolve-a-data-model.md) changes a stored case class step by step and shows which changes old JSON survives.
- **Explore the reference pages:** [Basic Building Blocks](../basic-building-blocks.md) describes the kinds of schemas, and [Standard Type Reference](../standard-type-reference.md) lists the built-in primitive types.
- **Learn how to write schemas by hand:** [Manual Schema Construction](../manual-schema-construction.md) shows how to build a schema without deriving it.
- **Choose a codec:** [Codecs](../derivations/codecs/index.md) compares JSON, Protobuf, Avro, and the other formats.
- **Work with changing models:** [Schema Migration](../operations/schema-migration.md) shows how to move data between two versions of a model.
