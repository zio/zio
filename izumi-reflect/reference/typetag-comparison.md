# Comparison with scala-reflect TypeTag

> What izumi-reflect covers compared with scala-reflect TypeTag, where the answers differ, and how both perform.

`izumi-reflect` is a replacement for `TypeTag` from `scala-reflect`, which does not exist in Scala 3. It covers the part of `TypeTag` that most programs use, identifying and comparing types at runtime, and leaves out the rest. This page shows what carries over, what does not, and where the two give different answers, so you can decide whether `izumi-reflect` fits your use case.

## What izumi-reflect Covers

`izumi-reflect` models types, not values or code. The table compares the features of the two libraries:

| Feature                                          | `scala-reflect` `TypeTag`        | `izumi-reflect` `Tag`                             |
| ------------------------------------------------ | -------------------------------- | ------------------------------------------------- |
| Scala versions                                   | 2.12, 2.13                       | 2.12, 2.13, 3                                     |
| Platforms                                        | JVM                              | JVM, Scala.js, Scala Native, GraalVM Native Image |
| Type equality                                    | `=:=`                            | `=:=` and `==`                                    |
| Subtype checks                                   | `<:<`                            | `<:<`, with a simplified model                    |
| Usable as `Map` keys                             | Not reliably (`==` is not `=:=`) | Yes                                               |
| Tags for unapplied type constructors (`F[_]`)    | No                               | Yes, `TagK` and friends                           |
| Applying type constructors at runtime            | Through the reflection API       | `LightTypeTag#combine`                            |
| Type arguments of an applied type                | `Type#typeArgs`                  | `LightTypeTag#typeArgs`                           |
| Thread safety                                    | Known problems                   | Immutable values, thread-safe                     |
| Fields, methods and constructors                 | Yes                              | No                                                |
| Annotations                                      | Yes                              | No                                                |
| Creating instances, calling methods              | Through mirrors                  | No; only `AnyTag#closestClass`                    |
| Code trees and compiler internals                | Yes                              | No                                                |
| Serialization                                    | No stable format                 | Java serialization and `LightTypeTag#serialize`   |

In short, use `izumi-reflect` when you need to know which type you have and how it relates to other types. If you need to list the fields of a case class or call methods reflectively, use a library that preserves them, or derive the information at compile time, for example with a type class.

## Where the Answers Differ

`izumi-reflect` keeps a lightweight model of the Scala type system, not the full one the compiler uses. For the types most programs work with (classes, traits, type aliases, type constructors, intersections, unions, structural refinements and path-dependent types) the results usually agree with the compiler. A few rules are simplified on purpose. For example, `Null` counts as a subtype of every type, like `Nothing`, and every type counts as a subtype of `AnyRef`, including value types such as `Int`:

```scala
import izumi.reflect.Tag

Tag[Null] <:< Tag[Int]
// res0: Boolean = true
Tag[Int] <:< Tag[AnyRef]
// res1: Boolean = true
```

The Scala compiler rejects both relations. Other simplifications cover F-bounded types, existential types, path-dependent types that refer to different values of the same type, `this.type`, and lower bounds of higher-kinded type members. The [Limitations](../index.md#limitations) section of the introduction lists them all, and [Scala 2 vs Scala 3](scala-2-vs-scala-3.md#known-version-specific-gaps) lists the gaps that apply to only one version.

## Performance

A `TypeTag` is created at compile time but needs the runtime reflection universe of `scala-reflect` to do anything with it. A `Tag` is created at compile time as a compact serialized string and parsed on first use. Equality checks of tags created at compile time usually compare those strings, and subtype checks use the inheritance data stored in the tag, without touching `scala-reflect` or Java reflection.

## Moving from TypeTag

To replace `TypeTag` with `Tag` in existing code, see [Migrate from scala-reflect TypeTag](../guides/migrate-from-typetag.md).
