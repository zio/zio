# Optics Derivation

> Optics get, set, and update parts of a data structure, and they compose: a lens for a field of a case class and a lens for a field of a nested case class combine into one lens for the inner field. ZIO Schema derives [ZIO Optics](https://zio.dev/zio-optics) lenses, prisms, and traversals from a schema, so we do not have to write them by hand. This page explains how the derivation works; [How to Update Nested Data with Derived Optics](../how-to/update-nested-data-with-optics.md) shows how to put the derived optics to work on nested data.

## Manual Derivation of Optics

Before we derive optics from a schema, let's look at how we create them manually with the ZIO Optics library.

The `zio-schema-optics` module, described in the [Installation](#installation) section below, depends on ZIO Optics, so adding it also brings in `zio-optics`. To use ZIO Optics on its own, follow the installation steps in the [ZIO Optics](https://zio.dev/zio-optics) documentation.

Let's define a simple data type called `User` and create two optics for its `name` and `age` fields:

```scala
import zio.optics._

case class User(name: String, age: Int)

val nameLens = Lens[User, String](
  user => Right(user.name),
  name => user => Right(user.copy(name = name))
)

val ageLens = Lens[User, Int](
  user => Right(user.age),
  age => user => Right(user.copy(age = age))
)

val ageAndNameLens = nameLens.zip(ageLens)
```

Each optic returns an `Either`, because getting or setting a part can fail. Setting the name, the age, and both at once gives back updated copies of the user:

```scala
val user = User("John", 34)
// user: User = User(name = "John", age = 34)

nameLens.setOptic("Jane")(user)
// res0: package.OpticResult[(Nothing, User), User] = Right(
//   value = User(name = "Jane", age = 34)
// )

ageLens.setOptic(32)(user)
// res1: package.OpticResult[(Nothing, User), User] = Right(
//   value = User(name = "John", age = 32)
// )

ageAndNameLens.set(("Jane", 32))(user)
// res2: package.OpticResult[Nothing, User] = Right(
//   value = User(name = "Jane", age = 32)
// )
```

## Automatic Derivation of Optics

With a `Schema[A]`, we do not have to write these optics ourselves. The `Schema#makeAccessors` method derives them from the schema:

```scala
trait Schema[A] {
  def makeAccessors(b: AccessorBuilder): Accessors[b.Lens, b.Prism, b.Traversal]
}
```

It takes an `AccessorBuilder`, an interface for creating optics:

```scala
trait AccessorBuilder {
  type Lens[F, S, A]
  type Prism[F, S, A]
  type Traversal[S, A]

  def makeLens[F, S, A](
    product: Schema.Record[S],
    term: Schema.Field[S, A]
  ): Lens[F, S, A]

  def makePrism[F, S, A](
    sum: Schema.Enum[S],
    term: Schema.Case[S, A]
  ): Prism[F, S, A]

  def makeTraversal[S, A](
    collection: Schema.Collection[S, A],
    element: Schema[A]
  ): Traversal[S, A]
}
```

It has three methods for creating three types of optics:

- **Lens** is an optic used to get and update values in a product type.
- **Prism** is an optic used to get and update values in a sum type.
- **Traversal** is an optic used to get and update values in a collection type.

The type of the `Accessors` value depends on the schema: a record schema returns one lens per field, an enum schema returns one prism per case (as a tuple when there are several), and a collection schema returns a single traversal. To get these precise types, the schema value must have its precise type, such as `Schema.CaseClass2[String, Int, User]`, not just `Schema[User]`.

### Installation

To derive optics from a ZIO Schema, we add the following line to our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-optics" % "1.9.2"
```

This module contains `ZioOpticsBuilder`, an implementation of the `AccessorBuilder` interface based on the ZIO Optics library. Its lenses convert the whole record to and from [`DynamicValue`](../operations/dynamic-data-representation.md) on each get and set, so a derived lens is slower than a hand-written one.

### Examples

The examples below derive each kind of optic in turn.

#### Lens

We derive the schema for our `User` data type in its companion object, and then derive its lenses with `Schema#makeAccessors`:

```scala
import zio.schema.DeriveSchema
import zio.schema.Schema.CaseClass2
import zio.schema.optics.ZioOpticsBuilder

case class User(name: String, age: Int)

object User {
  implicit val schema: CaseClass2[String, Int, User] =
    DeriveSchema.gen[User]

  val (nameLens, ageLens) = schema.makeAccessors(ZioOpticsBuilder)
}
```

`DeriveSchema.gen` returns the precise schema type, here `CaseClass2[String, Int, User]`, so we can declare that type without a cast. `Schema#makeAccessors` then returns one lens per field, in declaration order.

The derived lenses update a user just like the hand-written ones, and they compose with `Optic#zip` in the same way:

```scala
val user = User("John", 42)
// user: User = User(name = "John", age = 42)

User.nameLens.set("Jane")(user)
// res4: zio.optics.package.OpticResult[zio.optics.package.OpticFailure, User] = Right(
//   value = User(name = "Jane", age = 42)
// )

User.ageLens.set(32)(user)
// res5: zio.optics.package.OpticResult[zio.optics.package.OpticFailure, User] = Right(
//   value = User(name = "John", age = 32)
// )

User.nameLens.zip(User.ageLens).set(("Jane", 32))(user)
// res6: zio.optics.package.OpticResult[zio.optics.package.OpticFailure, User] = Right(
//   value = User(name = "Jane", age = 32)
// )
```

#### Prism

For a sealed trait, `Schema#makeAccessors` derives one prism per case, in declaration order. We define the cases first and derive the schema after them, in the companion object:

```scala
import zio.schema.Schema.Enum2

sealed trait Shape {
  def area: Double
}

case class Circle(radius: Double) extends Shape {
  val area: Double = Math.PI * radius * radius
}

case class Rectangle(width: Double, height: Double) extends Shape {
  val area: Double = width * height
}

object Shape {
  implicit val schema: Enum2[Circle, Rectangle, Shape] =
    DeriveSchema.gen[Shape]

  val (circlePrism, rectanglePrism) =
    schema.makeAccessors(ZioOpticsBuilder)
}
```

Setting a value through the prism for `Rectangle` replaces the whole shape with the given rectangle, whatever case the shape had before:

```scala
val shape: Shape = Circle(1.2)
// shape: Shape = Circle(radius = 1.2)

Shape.rectanglePrism.setOptic(Rectangle(2.0, 3.0))(shape)
// res7: zio.optics.package.OpticResult[(Nothing, Shape), Shape] = Right(
//   value = Rectangle(width = 2.0, height = 3.0)
// )
```

#### Traversal

For a collection schema, `Schema#makeAccessors` derives a traversal over its elements. `Schema.list` declares its result as `Schema[List[A]]`, which hides the precise type, so we build the `Schema.Sequence` for `List[Int]` ourselves:

```scala
import zio.Chunk
import zio.optics.ZTraversal
import zio.schema.Schema

object IntList {
  implicit val schema: Schema.Sequence[List[Int], Int, String] =
    Schema.Sequence[List[Int], Int, String](
      elementSchema = Schema[Int],
      fromChunk = _.toList,
      toChunk = i => Chunk.fromIterable(i),
      annotations = Chunk.empty,
      identity = "List"
    )

  val traversal: ZTraversal[List[Int], List[Int], Int, Int] =
    schema.makeAccessors(ZioOpticsBuilder)
}
```

Setting a chunk through the traversal replaces the elements from the start of the list, one for each element of the chunk, and keeps the rest:

```scala
IntList.traversal.set(Chunk(1, 5, 7))(List(1, 2, 3, 4, 5))
// res8: zio.optics.package.OpticResult[zio.optics.package.OpticFailure, List[Int]] = Right(
//   value = List(1, 5, 7, 4, 5)
// )
```
