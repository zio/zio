# Magnolia Derivation

> Derive Debug instances for case classes and sealed traits automatically with the zio-prelude-magnolia module.

The Magnolia module derives `Debug` instances automatically, so we do not have to write one by hand for every case class. `Debug` renders a value as a structured, human-readable representation, and for a plain case class that representation follows the shape of the class: its name, its field names and its field values. The module builds the derivation on the Magnolia library, which inspects case classes and sealed traits at compile time.

The module has a single entry point, `DeriveDebug`, with a `derived` method that produces a `Debug[T]`:

```scala
object DeriveDebug {
  def derived[T]: Debug[T]
}
```

## Installation

We add the module next to the core library. It is available for the JVM, Scala.js and Scala Native, and for Scala 2.12, 2.13 and 3. On Scala 2 it uses Magnolia 1 and a macro, and on Scala 3 it uses Magnolia 1.3 with the `derives` clause:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-prelude"          % "1.0.0-RC49",
  "dev.zio" %% "zio-prelude-magnolia" % "1.0.0-RC49"
)
```

## Deriving an Instance

To derive a `Debug` instance, we call `DeriveDebug.derived` and store the result in an implicit value, conventionally in the companion object of the type. A derived instance works for case classes, including nested ones, and for fields whose own types have `Debug` instances, such as `List`, `Option` and the primitive types:

  

**Scala 2**

```scala
import zio.prelude._
import zio.prelude.magnolia._

case class Inner(x: Int)
object Inner {
  implicit val debug: Debug[Inner] = DeriveDebug.derived[Inner]
}

case class Outer(name: String, inner: Inner, maybe: Option[Inner])
object Outer {
  implicit val debug: Debug[Outer] = DeriveDebug.derived[Outer]
}
```

  
  

**Scala 3**

```scala
import zio.prelude.*
import zio.prelude.magnolia.*

case class Inner(x: Int) derives Debug

case class Outer(name: String, inner: Inner, maybe: Option[Inner]) derives Debug
```

  

On Scala 3 the module also adds an extension method `Debug.derived`, which is what the `derives Debug` clause calls. Once the instance exists, the `render` syntax from `zio.prelude._` turns a value into a string:

```scala
Outer("a", Inner(1), Some(Inner(2))).render
// Outer(name = "a", inner = Inner(x = 1), maybe = Some(Inner(x = 2)))
```

The derivation covers three kinds of types. A case class renders as its name with `name = value` pairs, a value class (`extends AnyVal`) renders as its name with the wrapped value, and a case object renders as its name. A sealed trait renders as whichever subtype the value is.

## Sealed Traits

A sealed trait or enum is derived by delegating to the instance of each subtype, so every subtype needs a `Debug` instance of its own. `Debug` is contravariant, which means an instance for the parent type is also a valid candidate for each subtype. In our tests with the 1.0.0-RC48 release, `DeriveDebug.derived` on a sealed trait whose instance was implicit failed with a `StackOverflowError` when rendering, which suggests that the derivation found the parent's own instance for a subtype and called itself.

The pattern that worked on Scala 2.13 and Scala 3 is to give each subtype its own instance and to keep the parent's instance out of implicit scope, for example as a plain `val`:

  

**Scala 2**

```scala
sealed trait Shape
case class Circle(r: Double) extends Shape
object Circle {
  implicit val debug: Debug[Circle] = DeriveDebug.derived[Circle]
}
case class Square(side: Double) extends Shape
object Square {
  implicit val debug: Debug[Square] = DeriveDebug.derived[Square]
}

object Shape {
  val shapeDebug: Debug[Shape] = DeriveDebug.derived[Shape]
}
```

  
  

**Scala 3**

```scala
sealed trait Shape
case class Circle(r: Double) extends Shape derives Debug
case class Square(side: Double) extends Shape derives Debug

object Shape {
  val shapeDebug: Debug[Shape] = DeriveDebug.derived[Shape]
}
```

  

We then render through the explicit instance:

```scala
Shape.shapeDebug.render(Circle(1.0))
// Circle(r = 1.0)
```

## Choosing a Renderer

`DeriveDebug` produces the same structured `Debug.Repr` as every other `Debug` instance, so the renderers described on the [`Debug`](../functional-abstractions/concrete-types/debug.md) page apply. The `render` syntax uses the simple renderer. To include the package of each type in the output, we render the `Repr` with `Debug.Renderer.Full`:

```scala
Outer("a", Inner(1), None).debug.render(Debug.Renderer.Full)
```

## Related Pages

The derived instance is an ordinary [`Debug`](../functional-abstractions/concrete-types/debug.md), so everything described there, including the renderers and the `d` string interpolator, applies to it. For types whose representation should differ from their structure, we implement `Debug` by hand instead of deriving it.
