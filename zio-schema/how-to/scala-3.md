# How to Use ZIO Schema with Scala 3

> Derive schemas with derives Schema, and handle Scala 3 enums, union types, opaque types, Scaladoc, and custom derivers.

In this guide, we model a small domain in idiomatic Scala 3 and give every type a schema: case classes with `derives Schema`, an `enum` for a closed set of states, a union type for a field that holds one of several types, and an opaque type for an identifier. We then turn Scaladoc comments into schema descriptions and derive a type class of our own with a `derives` clause. Each section adds one Scala 3 feature, so you can jump to the one you need.

## The Problem

Most ZIO Schema examples use the Scala 2 idiom: an `implicit val schema = DeriveSchema.gen[A]` in a hand-written companion object for every type. That still compiles on Scala 3, but a Scala 3 code base usually looks different. Its types say `derives` instead of declaring companions, closed sets of values are `enum`s rather than sealed traits, identifiers are opaque types, and some fields are union types such as `Int | String`. Each of these behaves differently under derivation: some derive with no extra code, some need a hand-written schema, and one Scala 3 feature, Scaladoc, silently changes what the derived schema contains. This guide shows what to write for each.

## Prerequisites

Schema derivation lives in the `zio-schema-derivation` module, and the JSON module, used here to show encoded output, already depends on it:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-schema"      % "1.9.2",
  "dev.zio" %% "zio-schema-json" % "1.9.2"
)
```

Scala 3 needs no `scala-reflect` dependency, because derivation uses inline methods and quoted macros instead of Scala 2 macros. Every example imports `zio.schema._`, which brings `Schema`, `DeriveSchema`, and the `Schema.derived` extension method into scope. The documentation build compiles its snippets with Scala 2.13, so the Scala 3 snippets on this page are plain code blocks.

:::note
The Scala 3 artifacts of ZIO Schema are compiled with Scala 3.9, so your project needs Scala 3.9 or later. An older Scala 3 compiler cannot read them and reports errors such as `Not found: type Schema`.
:::

## Derive a Schema with `derives Schema`

The shortest way to give a case class a schema is a `derives Schema` clause. The compiler calls `Schema.derived[Person]` and stores the result as a `given` in the companion object of `Person`, so every `Schema[Person]` lookup finds it without an import:

```scala
import zio.schema._
import zio.schema.codec.JsonCodec

final case class Person(name: String, age: Int) derives Schema

val json = JsonCodec.jsonCodec(Schema[Person]).encodeJson(Person("Ada", 36), None)
```

`Schema.derived[A]` is an extension method on the `Schema` companion object that calls `DeriveSchema.gen[A]`. It is also the method to call when you write the `given` yourself, for example for a type from another library whose definition you cannot change:

```scala
import zio.schema._

// Defined in a library, so it cannot have a derives clause
final case class Money(amount: BigDecimal, currency: String)

object schemas {
  given Schema[Money] = Schema.derived[Money]
}

import schemas.given
```

`derives Schema`, `Schema.derived`, and `DeriveSchema.gen` all produce the same schema. Pick `derives Schema` for types you own and `Schema.derived` for the rest.

If the same sources also compile on Scala 2, keep the Scala 2 form instead, because Scala 2 has neither `derives` nor `given`. An implicit value in the companion object compiles on both versions:

```scala
import zio.schema._

final case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}
```

## Model Closed Sets of Values with Enums

A Scala 3 `enum` derives like a sealed trait: each case becomes a case of the schema. When no case has fields and the enum has no parameters, derivation also adds the `simpleEnum` annotation, so the JSON codec writes each case as a plain string, such as `"Active"`. Annotations on the cases work as on sealed-trait cases; here `@caseName` changes the name that codecs read and write:

```scala
import zio.schema._
import zio.schema.annotation.caseName

enum Status derives Schema {
  case Active
  case Suspended
  case Closed
}

enum Priority derives Schema {
  @caseName("low") case Low
  @caseName("high") case High
}
```

An enum whose cases carry data derives one record per case, and it gets no `simpleEnum` annotation. Neither does an enum with constructor parameters, such as `enum Colour(val rgb: Int)`, even when its cases have no fields of their own:

```scala
import zio.schema._

enum Shape derives Schema {
  case Circle(radius: Double)
  case Rectangle(width: Double, height: Double)
}
```

## Accept One of Several Types with a Union Type

A union type such as `Int | String` derives as an enum with one case per member. The cases are named `"1"`, `"2"`, and so on, in the order the members are written, the `TypeId` of the schema is named `|`, and the schema carries the `noDiscriminator` annotation. A union can be derived on its own or appear as the type of a field:

```scala
import zio.schema._

val keySchema: Schema[Int | String] = DeriveSchema.gen

final case class Lookup(key: Int | String) derives Schema
```

To encode a value, the schema picks the first member whose runtime type test (`isInstanceOf`) succeeds. Choose members that such a test can tell apart: `List[Int] | List[String]` cannot work, because both members erase to `List` and every list matches the first case.

## Give an Opaque Type a Schema

Derivation cannot see through an opaque type, so `DeriveSchema.gen` fails on one with the compile error "Deriving schema for ... is not supported". Define the schema yourself in the opaque type's companion object. Inside the scope that defines the opaque type, the type equals its underlying type, so the schema of the underlying type can be used as it is. Derivation of an enclosing case class then finds that `given`:

```scala
import zio.schema._

object ids {
  opaque type UserId = String

  object UserId {
    def apply(value: String): UserId = value

    given Schema[UserId] = Schema[String]
  }
}

import ids.UserId

final case class Account(id: UserId, owner: String) derives Schema
```

An opaque type often exists to guarantee an invariant, such as a well-formed email address. In that case, build the schema with `Schema#transformOrFail`, so decoding rejects a value that the smart constructor rejects:

```scala
import zio.schema._

object emails {
  opaque type Email = String

  object Email {
    def make(value: String): Either[String, Email] =
      if (value.contains("@")) Right(value) else Left(s"invalid email: $value")

    extension (email: Email) def value: String = email

    given Schema[Email] = Schema[String].transformOrFail(make, email => Right(email.value))
  }
}
```

## Turn Scaladoc into Descriptions

On Scala 3, derivation copies the Scaladoc comment of a type into a `description` annotation of its schema. This applies to case classes, objects, and enums. Comments on fields are not read. Comments on enum cases are not kept per case: every case without fields receives the comments of all the enum's cases, and a case with fields receives none. Give a case whose description matters an explicit `@description` annotation. The annotation holds the comment exactly as written, including the `/**` and `*/` delimiters:

```scala
import zio.schema._
import zio.schema.annotation.description

/** A customer who placed at least one order. */
final case class Customer(name: String) derives Schema

val doc: Option[String] =
  Schema[Customer].annotations.collectFirst { case d: description => d.text }
```

Here `doc` is `Some("/** A customer who placed at least one order. */")`. Strip the delimiters before showing the text to people, for example with `doc.map(_.stripPrefix("/**").stripSuffix("*/").trim)`.

An explicit `@description` annotation comes before the one taken from Scaladoc, so code that reads the first description sees yours. Scala 2 derivation never reads Scaladoc. In a project that builds for both versions, schemas therefore differ between the two builds; use explicit `@description` annotations when the description must be the same on both.

## Derive Your Own Type Class with `derives`

A `Deriver` builds instances of your own type class from a schema; [Write a Custom Deriver](custom-deriver.md) shows how to write one. The examples below use the `LeafCount` type class and the `leafCounter` deriver from that guide.

On Scala 3, every deriver has a `Deriver#derive` method, the same as `Derive.derive[LeafCount, A](leafCounter)`. It needs a `Schema[A]` in scope:

```scala
import zio.schema._

final case class Person(name: String, age: Int) derives Schema

object Person {
  given LeafCount[Person] = leafCounter.derive[Person]
}
```

To let a type write `derives LeafCount`, give the type class companion an inline `LeafCount.derived` method that takes the schema as a `using` parameter and calls the deriver. The `derives` clause must list `Schema` too, so that a schema is in scope when `LeafCount.derived` runs:

```scala
import zio.schema._

trait LeafCount[A] {
  def count: Int
}

object LeafCount {
  inline def derived[A](using schema: Schema[A]): LeafCount[A] = leafCounter.derive[A]
}

final case class Team(members: List[Person], lead: Option[Person]) derives Schema, LeafCount
```

The method must be `inline`, because the derivation macro has to see the concrete type, `Team`, at the place where the instance is created.

An opaque type whose underlying type is a primitive, such as `UserId`, reaches the deriver through `Deriver#derivePrimitiveAlias`. Its default implementation returns the summoned instance and throws at runtime when there is none, so a deriver used with opaque types should override it. The method receives the `StandardType` of the underlying type, and the macro needs a `ClassTag` for the opaque type:

```scala
import scala.reflect.ClassTag
import zio.schema._

val leafCounter: Deriver[LeafCount] = new Deriver[LeafCount] {
  // derivePrimitive, deriveRecord, and the other methods as in Write a Custom Deriver

  override def derivePrimitiveAlias[A: ClassTag, U](
    st: StandardType[U],
    summoned: => Option[LeafCount[A]]
  ): LeafCount[A] =
    summoned.getOrElse(known(1))
}
```

The opaque type still needs its own `Schema` in scope, as shown in [Give an Opaque Type a Schema](#give-an-opaque-type-a-schema).

## Going Further

[Schema Derivation on Scala 2 and Scala 3](../derivations/derivation-scala-versions.md) compares the two derivation mechanisms side by side, and [Automatic Schema Derivation](../automatic-schema-derivation.md) lists the shapes that derivation supports on both versions. One more difference matters for validation: on Scala 3, `@validate` annotations are kept on case classes with more than 22 fields, while Scala 2 drops them; [How to Validate Untrusted Input](validate-input.md) covers validation in general. For the annotations used above, such as `@caseName` and `@description`, see [Schema Annotations](../schema-annotations.md).
