# Schema Derivation on Scala 2 and Scala 3

> Explains how DeriveSchema differs between Scala 2 macros and Scala 3 inline methods, how to call it on each version, and which Scala 3 types it supports.

`DeriveSchema.gen` builds a `Schema` at compile time. The Scala version decides how that call is written, because the two compilers offer different ways to define a derivation, and Scala 3 supports a few kinds of types that Scala 2 does not have. This page explains the differences so you can use the right form on each version. [Automatic Schema Derivation](../automatic-schema-derivation.md) lists the shapes both versions support.

## Scala 2: An Implicit Macro

On Scala 2, `DeriveSchema.gen` is an implicit whitebox macro. A whitebox macro can see the concrete types it is given and can return a tree that the compiler then type-checks.

The recommended form calls the macro once, in the companion object, and stores the result in an implicit value:

```scala
import zio.schema._

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}
```

Placing the `implicit val` in the companion object means the schema is found without an import wherever `Person` is used.

Although `DeriveSchema.gen` is declared `implicit`, the compiler does not find it on its own. `DeriveSchema` is a plain object, outside the implicit scope of `Schema[T]`, so `DeriveSchema.gen` takes part in implicit search only where it is imported. With `import zio.schema.DeriveSchema._`, the compiler derives a `Schema[T]` on the spot wherever one is needed:

```scala
import zio.schema._
import zio.schema.DeriveSchema._

case class Point(x: Int, y: Int)

val pointSchema: Schema[Point] = implicitly[Schema[Point]]
```

The import derives a new schema at every place that needs one, which costs compile time and creates many copies of the same schema. Imported implicits are also searched before companion objects, so the import can bypass a hand-written schema in a companion object. Prefer the companion-object form for types you own.

## Scala 3: An Inline Method

On Scala 3, `DeriveSchema.gen` is a `transparent inline` method that expands during compilation. It is not implicit, so you call it explicitly and bind the result to an implicit value (or a `given`) yourself, as the previous section does on Scala 2.

Scala 3 offers two shorter spellings. `Schema.derived[A]` is an extension method on the `Schema` companion object that calls `DeriveSchema.gen[A]`, and a `derives Schema` clause on the type calls `Schema.derived` for you and puts the result in the companion object:

```scala
import zio.schema._

final case class Person(name: String, age: Int) derives Schema

final case class Point(x: Int, y: Int)

object Point {
  given Schema[Point] = Schema.derived[Point]
}
```

All three spellings produce the same schema. Use whichever your team prefers.

The documentation build compiles its snippets with Scala 2.13, so the Scala 3 snippets on this page are not compiled. The Scala 3 artifacts of ZIO Schema are compiled with Scala 3.9, so a Scala 3 project needs Scala 3.9 or later.

## Scala 3 Enums, Union Types, and Opaque Types

Scala 3 adds kinds of types that Scala 2 does not have, and derivation treats each of them differently:

- An `enum` derives like a sealed trait: each case becomes a case of the schema. When no case has fields and the enum itself has no parameters, derivation also adds the `simpleEnum` annotation, so the JSON codec encodes each case as a plain string.
- A union type such as `Int | String` derives as an enum with one case per member. The cases are named `"1"`, `"2"`, and so on, in the order the members are written, and the schema carries the `noDiscriminator` annotation.
- An opaque type is not derived: `DeriveSchema.gen` fails with the compile error "Deriving schema for ... is not supported". Write its schema by hand in the opaque type's companion object, and derivation of an enclosing case class picks it up.

Scala 3 derivation also turns the Scaladoc comment of a type into a `description` annotation. The annotation holds the raw comment, including the `/**` and `*/` delimiters. Comments on enum cases are not kept per case: every case without fields receives the comments of all the enum's cases, and a case with fields receives none.

[How to Use ZIO Schema with Scala 3](../how-to/scala-3.md) shows each of these with examples, including a schema for an opaque type that checks its invariant.

## Choosing a Form

The table summarizes the differences:

| Aspect                                               | Scala 2                                                                        | Scala 3                                                                            |
| ---------------------------------------------------- | ------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------- |
| Mechanism                                            | Whitebox macro                                                                 | Transparent inline method                                                          |
| `DeriveSchema.gen`                                   | Implicit, but used by implicit search only where it is imported                | Not implicit                                                                       |
| Extra entry points                                   | None                                                                           | `Schema.derived[A]` and `derives Schema`                                           |
| Typical use                                          | `implicit val schema: Schema[T] = DeriveSchema.gen[T]` in the companion object | `derives Schema`, or `given Schema[T] = Schema.derived[T]` in the companion object |
| Enums, union types                                   | Not available in the language                                                  | Supported                                                                          |
| Opaque types                                         | Not available in the language                                                  | Not derived; write the schema by hand                                              |
| Scaladoc as `description`                            | No                                                                             | Yes                                                                                |
| `@validate` on case classes with more than 22 fields | Dropped (see the caution below)                                                | Kept                                                                               |

Both forms need the `zio-schema-derivation` module on the classpath.

:::caution
On Scala 2, `DeriveSchema.gen` drops the `@validate` annotations of a case class with more than 22 fields, so `Schema.validate` reports no errors for those fields. On Scala 2, move the validated fields into a nested case class with 22 fields or fewer; `Schema.validate` checks the rules of nested case classes too.
:::

For a walkthrough of deriving schemas in a project, see [Automatic Schema Derivation](../automatic-schema-derivation.md).
