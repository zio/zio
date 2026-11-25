# Scala 2 and Scala 3 Differences

> Why ZIO JSON behaves differently on Scala 2 and Scala 3, every difference in one table, and a checklist for migrating.

ZIO JSON has the same API on Scala 2 and Scala 3, and most code compiles on both without changes. The JSON it produces is not always the same, though. This page explains where the differences come from, lists each of them with the output of the same code on Scala 2.13.18 and Scala 3.9.0, and ends with a checklist for moving a project from Scala 2 to Scala 3.

## Why the Two Versions Differ

Codecs for your case classes and sealed traits are written at compile time by macros (code that runs inside the compiler and generates code). Scala 2 and Scala 3 have different macro systems, so ZIO JSON has two separate implementations of derivation:

- On Scala 2, `DeriveJsonEncoder.gen` and `DeriveJsonDecoder.gen` are built on [Magnolia](https://github.com/softwaremill/magnolia), a library that describes the shape of a type to the code that builds the instance. `DeriveJsonCodec.gen` is a small macro that calls the other two.
- On Scala 3, derivation uses native Scala 3 macros together with the `Mirror` that the compiler provides for case classes, sealed traits and enums. Magnolia is not used.

Both implementations read the same annotations and the same `JsonCodecConfiguration` options. Where they disagree, it is because each one decides a few details on its own: what counts as an enumeration value, what to do when a field type has no instance, or what an empty case class looks like inside another one. The defaults of `JsonCodecConfiguration` also live in two separate source files, one per version, and one default differs.

The rest of the library, the parser, the built-in instances for standard types and the `Json` tree, is shared code. Apart from a few instances for types that only exist on one version, it behaves the same on both. [How ZIO JSON works](how-it-works.md) describes derivation in general.

## The Differences at a Glance

The Scala 2 column is for Scala 2.13 (most rows also apply to 2.12):

| Behaviour                                                                | Scala 2                                                   | Scala 3                                                                                            |
|--------------------------------------------------------------------------|-----------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| Field type without a codec                                               | Compile error                                             | Derived with the outer configuration                                                               |
| Default of `enumValuesAsStrings`                                         | `false`: case objects are written as `{"Red":{}}`         | `true`: case objects are written as `"Red"`                                                        |
| Case class with no fields in a family of case objects                    | Counts as an enum value, written as `"Stop"`              | Does not count, the whole family is written as objects                                             |
| Case object with a hand-written codec, family as strings                 | String form is turned off, the hand-written codec is used | String form is used; the encoder ignores the hand-written codec, and the decoder passes `{}` to it |
| Empty case class field with `ExplicitEmptyCollections(encoding = false)` | Field is left out of the JSON                             | Field is written as `{}`                                                                           |
| Recursive type with a plain `implicit val` or `implicit def`             | Fails at runtime, needs `lazy val`                        | Works, with `derives` or a plain `given`                                                           |
| Generic sealed trait with a case class extending `T[Nothing]`            | Works                                                     | Compiler crash, give the case class the type parameter                                             |
| Compile error for a missing instance                                     | `magnolia: could not find ... Typeclass[...]`             | `Cannot find or derive JsonEncoder for ...`                                                        |
| Sum type read from a `Json.Obj` with several keys                        | `expected single field object`                            | `expected object with a single field`                                                              |
| How a configuration is passed                                            | `implicit val`                                            | `given`, also picked up by `derives`                                                               |
| `derives JsonCodec`, Scala 3 `enum`, opaque types                        | Not available                                             | Available                                                                                          |
| String-literal union types, `IArray`, `T <: String` map keys             | Not available                                             | Built-in instances                                                                                 |
| `@jsonDerive` from `zio-json-macros`                                     | Available                                                 | Not published                                                                                      |

These behaviours are the same on both versions: the subtypes of a sealed trait are derived together with the trait, a nested sealed trait is flattened to its leaf cases, default values of fields are used when a field is missing (Scala 3 does not need the `-Yretain-trees` option for this), and every alias in `@jsonAliases` is accepted.

The sections below show each difference with code. Scala 2 code is compiled and run when this page is built. Scala 3 code is shown from example files, followed by the output of running them on Scala 3.9.0.

## Field Types Without a Codec

A derived codec needs an instance for the type of every field. On Scala 2, Magnolia looks the instance up in implicit scope and stops with a compile error when there is none. Here `Address` has no codec:

```scala
import zio.json._

final case class Address(street: String)
final case class Person(name: String, address: Address)

object Person {
  implicit val codec: JsonCodec[Person] = DeriveJsonCodec.gen[Person]
}
// error: magnolia: could not find zio.json.DeriveJsonEncoder.Typeclass[repl.MdocSession.MdocApp0.Address]
//     in parameter 'address' of product type repl.MdocSession.MdocApp0.Person
// 
//   implicit val codec: JsonCodec[Person] = DeriveJsonCodec.gen[Person]
//                                                              ^
```

On Scala 3, derivation looks for an instance first, and when it finds none, it derives one for the field type on the spot, with the configuration in scope at the outer type:

```scala
final case class Address(street: String)
final case class Person(name: String, address: Address) derives JsonCodec
```

Running it on Scala 3 prints:

```text
nested: {"name":"Ada","address":{"street":"Main St"}}
```

Scala 3 accepts every such codec that Scala 2 accepts, but not the other way round. When the nested type must look the same everywhere, or is also encoded on its own, give it its own codec on both versions. [JsonCodecConfiguration](../reference/json-codec-configuration.md#passing-a-custom-configuration) shows how a nested type picks up the outer configuration.

## Case Objects as Strings

The `enumValuesAsStrings` option decides whether a family of case objects is written as plain strings. Its default is `false` on Scala 2 and `true` on Scala 3, so the same family is written in two ways. On Scala 2:

```scala
import zio.json._
import zio.json.ast.Json

sealed trait Color
case object Red   extends Color
case object Green extends Color

object Color {
  implicit val codec: JsonCodec[Color] = DeriveJsonCodec.gen[Color]
}
```

On Scala 2, a case object is written as an object with one key:

```scala
println((Red: Color).toJson)
// {"Red":{}}
```

The same family on Scala 3:

```scala
sealed trait Color derives JsonCodec
case object Red   extends Color
case object Green extends Color
```

Scala 3 writes the plain string:

```text
case objects: "Red"
```

This is the difference most likely to break a running system after a migration: a Scala 3 service sends `"Red"` to a Scala 2 client that expects `{"Red":{}}`, and each side rejects the other's JSON. Set the option explicitly in a shared configuration to get the same JSON on both versions. [Sealed traits](../how-to/sealed-traits.md#5-write-case-objects-as-strings) covers the string form in full.

## What Counts as an Enumeration Value

Even with `enumValuesAsStrings = true` on both sides, the two versions disagree on which families qualify. Scala 2 accepts a case class with an empty parameter list as an enumeration value, like a case object. Scala 3 accepts only case objects and parameterless `enum` cases. A family with `Stop()` is written as strings on Scala 2:

```scala
object strings {
  implicit val config: JsonCodecConfiguration = JsonCodecConfiguration(enumValuesAsStrings = true)

  sealed trait Signal
  case object Go          extends Signal
  final case class Stop() extends Signal

  object Signal {
    implicit val codec: JsonCodec[Signal] = DeriveJsonCodec.gen[Signal]
  }
}
```

Both cases become strings on Scala 2:

```scala
println((strings.Go: strings.Signal).toJson)
// "Go"
println((strings.Stop(): strings.Signal).toJson)
// "Stop"
```

On Scala 3, the empty case class makes the whole family use objects, the case object included:

```scala
given JsonCodecConfiguration = JsonCodecConfiguration(enumValuesAsStrings = true)

sealed trait Signal derives JsonCodec
case object Go          extends Signal
final case class Stop() extends Signal
```

Running it on Scala 3 prints:

```text
Go: {"Go":{}}
Stop(): {"Stop":{}}
```

The two versions also disagree when one case object has a codec of its own. Scala 2 then turns the string form off for the whole family and wraps the hand-written JSON in the usual object. Scala 3 keeps the string form: the encoder ignores the hand-written codec, and the decoder passes `{}` to it, so a hand-written decoder that expects anything else fails. On Scala 2:

```scala
object handwritten {
  implicit val config: JsonCodecConfiguration = JsonCodecConfiguration(enumValuesAsStrings = true)

  sealed trait Light
  case object Amber extends Light
  case object Blue  extends Light

  implicit val amberCodec: JsonCodec[Amber.type] = JsonCodec.string.transform(_ => Amber, _ => "AMBER")

  object Light {
    implicit val codec: JsonCodec[Light] = DeriveJsonCodec.gen[Light]
  }
}
```

The hand-written JSON of `Amber` appears inside the wrapper, and `Blue` is an object too:

```scala
println((handwritten.Amber: handwritten.Light).toJson)
// {"Amber":"AMBER"}
println((handwritten.Blue: handwritten.Light).toJson)
// {"Blue":{}}
```

The same code on Scala 3:

```scala
given JsonCodecConfiguration = JsonCodecConfiguration(enumValuesAsStrings = true)

sealed trait Light
case object Amber extends Light
case object Blue  extends Light

given JsonCodec[Amber.type] = JsonCodec.string.transform(_ => Amber, _ => "AMBER")

object Light {
  given JsonCodec[Light] = DeriveJsonCodec.gen
}
```

Running it on Scala 3 prints the encoded cases, then the result of decoding `"Amber"`. The decoder passes `{}` to the string codec of `Amber`, which rejects it:

```text
Amber: "Amber"
Blue: "Blue"
Amber decoded: Left((expected string))
```

To get the same JSON on both versions, use only case objects in a family that should be written as strings, and change a string with `@jsonHint` rather than with a hand-written codec.

## Empty Case Classes as Fields

With `ExplicitEmptyCollections(encoding = false)`, the encoder leaves out fields whose value is empty, such as an empty `List`. On Scala 2, the encoder of a case class with no fields also reports its value as empty, so a field of that type disappears too. With the default `decoding = true`, the decoder then rejects the JSON that the encoder wrote:

```scala
object emptyfield {
  implicit val config: JsonCodecConfiguration =
    JsonCodecConfiguration(explicitEmptyCollections = ExplicitEmptyCollections(encoding = false))

  final case class Marker()

  object Marker {
    implicit val codec: JsonCodec[Marker] = DeriveJsonCodec.gen[Marker]
  }

  final case class Holder(name: String, marker: Marker)

  object Holder {
    implicit val codec: JsonCodec[Holder] = DeriveJsonCodec.gen[Holder]
  }
}
```

The `marker` field is missing from the JSON, so decoding it fails:

```scala
println(emptyfield.Holder("a", emptyfield.Marker()).toJson)
// {"name":"a"}
emptyfield.Holder("a", emptyfield.Marker()).toJson.fromJson[emptyfield.Holder]
// res12: Either[String, emptyfield.Holder] = Left(value = ".marker(missing)")
```

On Scala 3 the field is written as `{}`, and the round trip works:

```scala
given JsonCodecConfiguration =
  JsonCodecConfiguration(explicitEmptyCollections = ExplicitEmptyCollections(encoding = false))

final case class Marker() derives JsonCodec
final case class Holder(name: String, marker: Marker) derives JsonCodec
```

Running it on Scala 3 prints:

```text
empty field: {"name":"a","marker":{}}
empty field round trip: Right(Holder(a,Marker()))
```

[JsonCodecConfiguration](../reference/json-codec-configuration.md#explicitemptycollections) describes the option.

## Recursive Types

A recursive type, such as a node whose children are nodes, refers to its own codec while that codec is being built. On Scala 2 the derived instance reads the instance of each field when it is created. With `implicit val codec: JsonCodec[Node] = DeriveJsonCodec.gen[Node]`, that instance is still `null` at that moment, and the first use fails with a `NullPointerException` inside an `ExceptionInInitializerError`. Declare the codec as an `implicit lazy val`:

```scala
final case class Node(name: String, children: List[Node])

object Node {
  implicit lazy val codec: JsonCodec[Node] = DeriveJsonCodec.gen[Node]
}
```

Encoding a tree with one child:

```scala
println(Node("root", List(Node("leaf", Nil))).toJson)
// {"name":"root","children":[{"name":"leaf","children":[]}]}
```

A generic recursive type with an `implicit def` fails with a `StackOverflowError` instead, and needs a local `implicit lazy val`, as shown in [Derive codecs for generic types](../how-to/generic-types.md#4-derive-a-recursive-generic-type).

The Scala 3 derivation reads field instances lazily, so `derives` works for recursive types without any extra code:

```scala
final case class Node(name: String, children: List[Node]) derives JsonCodec
```

Running it on Scala 3 prints:

```text
recursive: {"name":"root","children":[{"name":"leaf","children":[]}]}
```

A `lazy val` is also correct on Scala 3, so keep it in code that cross-builds.

## Generic Sealed Traits

A generic sealed trait such as `sealed trait Result[+A]` often has cases that do not use the type parameter, for example `final case class Failed(reason: String) extends Result[Nothing]`. Scala 2 derives such a family. On Scala 3.9.0 the same family makes the compiler crash with `java.lang.AssertionError: assertion failed: TypeRef(..., class Failed)`, because the derivation applies the type arguments of `Result` to `Failed`, which has none. Case objects are not affected. The workaround is to give the case class the type parameter: `final case class Failed[A](reason: String) extends Result[A]`. [Derive codecs for generic types](../how-to/generic-types.md#3-derive-a-generic-sealed-trait) shows both versions.

## Error Messages

When derivation cannot find or build an instance, the compile errors differ. Scala 2 reports the Magnolia type class, the field and the outer type, as in the first example on this page. Outside mdoc, with the types in an object `Err`, the message reads:

```text
magnolia: could not find zio.json.DeriveJsonEncoder.Typeclass[Err.Address]
    in parameter 'address' of product type Err.Person
```

Scala 3 reports only the field type, followed by an inline stack trace that points at the `derives` clause or the `DeriveJsonCodec.gen` call. For a case class with a `java.io.File` field:

```text
Cannot find or derive JsonDecoder for java.io.File
```

Runtime messages are almost all shared, because the decoders for standard types are shared code. One message from derived sum types differs: reading a sealed trait from a `Json.Obj` that has more than one key. On Scala 2:

```scala
sealed trait Fruit
final case class Banana(curvature: Double) extends Fruit
final case class Apple(poison: Boolean)    extends Fruit

object Fruit {
  implicit val codec: JsonCodec[Fruit] = DeriveJsonCodec.gen[Fruit]
}
```

Decoding an object that has both keys fails on Scala 2 with:

```scala
Json.Obj("Banana" -> Json.Obj(), "Apple" -> Json.Obj()).as[Fruit]
// res16: Either[String, Fruit] = Left(
//   value = "(expected single field object)"
// )
```

On Scala 3 the same call returns `Left((expected object with a single field))`. Tests that compare error strings must allow for both. [Errors](../reference/errors.md) lists the messages.

## Features of Only One Version

Some features exist on one version only, because the language feature they build on exists on one version only.

Scala 3 adds:

- The `derives JsonCodec`, `derives JsonEncoder` and `derives JsonDecoder` clauses, and the `given` form of the configuration that they pick up. See [Derive codecs on Scala 3](../how-to/scala-3-derivation.md).
- Derivation for Scala 3 `enum` types. A parameterless enum is written as strings, an enum with parameters as objects.
- An encoder and a decoder for unions of string literals, such as `"EUR" | "USD"`, which also work as `Map` keys. See [JsonEncoder](../reference/json-encoder.md#scala-2-vs-scala-3).
- Key decoding for any subtype of `String` that has a `JsonDecoder`, such as an opaque type declared `<: String`. See [Use your own type as a Map key](../how-to/custom-map-keys.md#scala-3-string-subtypes-need-no-key-decoder).
- An instance for `IArray`. Both Scala 2.13 and Scala 3 have one for `immutable.ArraySeq`; Scala 2.12 has neither.

Opaque types are Scala 3 only, but ZIO JSON has no automatic support for them. Each one needs a hand-written codec, usually `JsonCodec.string.transform(...)` on the underlying type.

Scala 2 has `@jsonDerive` from the `zio-json-macros` module, which generates the companion codec for you. Scala 3 has no macro annotations, so the module is not published for Scala 3. A `derives` clause replaces it. See [@jsonDerive Macro Annotation](../reference/zio-json-macros.md#scala-3-alternative).

## Migrating From Scala 2 to Scala 3

Most of a migration is mechanical: the code compiles, and derived codecs keep working. The risk is JSON that changes without a compile error. Work through these steps:

1. Write [golden tests](../how-to/golden-testing.md) for every type that crosses a process boundary (HTTP payloads, messages, stored documents) while the project is still on Scala 2. They record the current JSON, so any change on Scala 3 fails a test instead of reaching production.
2. Decide on `enumValuesAsStrings`. To keep the Scala 2 JSON, put `JsonCodecConfiguration(enumValuesAsStrings = false)` in scope for every derivation. To switch to strings, change both sides of every integration at the same time.
3. Search for case classes with an empty parameter list, `final case class Stop()`, in sealed families written as strings. Replace them with case objects, or accept that Scala 3 writes the family as objects.
4. Search for hand-written codecs of case objects that belong to a derived family. When the family is written as strings, the Scala 3 encoder ignores them and the decoder passes `{}` to them. Use `@jsonHint` to change the string instead.
5. If you use `ExplicitEmptyCollections(encoding = false)`, check fields whose type is a case class with no fields: Scala 3 starts writing them as `{}`.
6. Replace `@jsonDerive` with a `derives` clause, or with an explicit companion codec if the code must still compile on Scala 2.
7. Move each `implicit val` configuration next to the types it should affect. A `given` at the top level of a file applies to every `derives` clause in the whole package.
8. For generic sealed traits, give every case class the type parameter of the trait, so that the Scala 3 compiler does not crash.
9. Update tests that compare error strings, and drop `-Yretain-trees` from `scalacOptions` if it was only there for default values.

Scala 3 derives the instances of nested field types for you, so you can drop codecs that existed only to satisfy Scala 2. Keep a codec when the nested type is also encoded on its own, or when it must use a different configuration than the outer type.

## See Also

- [Derive codecs on Scala 3](../how-to/scala-3-derivation.md) shows `derives`, enums, union types and opaque types step by step.
- [Derive codecs for generic types](../how-to/generic-types.md) covers type parameters and recursive types on both versions.
- [JsonCodecConfiguration](../reference/json-codec-configuration.md#scala-2-and-scala-3-differences) has a short version of the configuration-related differences.
- [Golden testing](../how-to/golden-testing.md) explains how to pin the JSON before you migrate.
