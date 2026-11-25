# How to Derive Debug with Magnolia

> Derive Debug instances for case classes automatically with the zio-prelude-magnolia module, on Scala 2 and Scala 3.

In this guide, we derive `Debug` instances for a small drawing model instead of writing them by hand. By the end, you will have instances for case classes with nested case classes, collections and optional fields, rendered in the readable and the paste-able forms.

We build the instances for the leaf types first, then for the types that contain them, and finish with the limits of derivation.

:::note
The code blocks of this guide are plain `scala` blocks, not compiled in the documentation build. The `zio-prelude-magnolia` module is not on the classpath of the documentation project, and the module's macros are checked by its own tests. The output shown was produced by running the same code against the published module.
:::

## The Problem

A `Debug` instance for a case class lists every field with its name and its own representation. For one class this is short, and for a model with dozens of classes it is boilerplate that must be updated whenever a field changes:

```scala
import zio.prelude._
import zio.prelude.Debug.Repr

final case class Point(x: Int, y: Int)

object Point {
  implicit val debug: Debug[Point] =
    Debug.make { point =>
      Repr.Constructor(
        List("example"),
        "Point",
        "x" -> point.x.debug,
        "y" -> point.y.debug
      )
    }
}
```

When we add a field to `Point` and forget to add it to the instance, the output silently omits it. The compiler cannot notice, because the instance is built from a function that reads fields by hand.

In this guide, we derive the instance from the shape of the class, so it always matches the fields.

## Prerequisites

We add the module to `build.sbt`. It provides the derivation for Scala 2 and Scala 3:

```scala
libraryDependencies += "dev.zio" %% "zio-prelude-magnolia" % "1.0.0-RC49"
```

We assume that you know what a `Debug` instance is. See [How to Define Equal, Ord, Hash and Debug Instances](./define-equal-ord-hash-debug-instances.md) for the hand-written version and for the renderers.

## Deriving an Instance for a Case Class

On Scala 2, the module provides `DeriveDebug.derived`, a macro that builds the instance, and we assign it to an implicit value in the companion object. On Scala 3, the module adds a `derived` extension to `Debug`, which makes the `derives Debug` clause work, once we import the package of the module:

  

**Scala 2**

```scala
import zio.prelude._
import zio.prelude.magnolia.DeriveDebug

final case class Point(x: Int, y: Int)

object Point {
  implicit val debug: Debug[Point] = DeriveDebug.derived[Point]
}
```

  
  

**Scala 3**

```scala
import zio.prelude.*
import zio.prelude.magnolia.*

case class Point(x: Int, y: Int) derives Debug
```

  

Without the import of `zio.prelude.magnolia`, the Scala 3 compiler reports that `derived` is not a member of `Debug`. With the instance in place, `render` prints the class name and every field with its name:

```scala
Point(1, 2).render
```

The output is:

```text
Point(x = 1, y = 2)
```

## Deriving Instances for Nested Types

A derived instance uses the instances of the field types, so every case class that appears in a field needs its own instance. Types from the standard library, such as `String`, `List`, `Option`, `Map` and tuples, already have one. We add a `Drawing` that holds a name, a `Point`, a list of points and an optional tag:

  

**Scala 2**

```scala
final case class Drawing(name: String, origin: Point, corners: List[Point], tag: Option[String])

object Drawing {
  implicit val debug: Debug[Drawing] = DeriveDebug.derived[Drawing]
}
```

  
  

**Scala 3**

```scala
case class Drawing(name: String, origin: Point, corners: List[Point], tag: Option[String]) derives Debug
```

  

The instance of `Drawing` finds the instance of `Point` and the instances of `List` and `Option` by itself, and rendering shows the whole structure:

```scala
Drawing("sketch", Point(0, 0), List(Point(1, 0), Point(1, 1)), Some("draft")).render
```

The output is:

```text
Drawing(name = "sketch", origin = Point(x = 0, y = 0), corners = List(Point(x = 1, y = 0), Point(x = 1, y = 1)), tag = Some("draft"))
```

## Choosing a Renderer

The derived instance produces a structured `Debug.Repr`, and a renderer turns it into text. `render` uses the `Simple` renderer, which is shown above. The `Scala` renderer drops the field names and the quotes, and the `Full` renderer prints the fully qualified names of the types:

```scala
Drawing("sketch", Point(0, 0), List(Point(1, 0)), Some("draft")).debug.render(Debug.Renderer.Scala)
```

The output is:

```text
Drawing(sketch,Point(0,0),List(Point(1,0)),Some(draft))
```

The `d` string interpolator renders each interpolated value with its own instance, so derived instances work inside log messages too, for example `d"drawing: $drawing"`.

## Knowing the Limits

Derivation covers case classes, case objects and value classes. A value class renders as a constructor call with its single value, and a case object renders as its name.

:::warning
Do not derive a `Debug` for a sealed trait while that instance is visible to the search for the instances of its cases. `Debug` is contravariant, so an instance of `Debug[Shape]` also satisfies the search for `Debug[Circle]`, and the derived instance calls itself until the stack overflows. We saw this in a test with an implicit value in the companion object of the sealed trait on Scala 2, and with a `derives Debug` clause on Scala 3. For a sealed trait, write the instance by hand with a pattern match, as shown in [How to Define Equal, Ord, Hash and Debug Instances](./define-equal-ord-hash-debug-instances.md), and derive only the case classes.
:::

## Going Further

- Read the [Debug](../functional-abstractions/concrete-types/debug.md) reference page for the structure of `Debug.Repr`.
- Test the derived output in a unit test by comparing `render` with the expected text, as the tests of the module do.
