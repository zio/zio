# Optics Derivation

> Optics are a way of accessing and manipulating data in a functional way. They can be used to get, set, and update values in data structures, as well as to traverse and explore data.

Optics are a way of accessing and manipulating data in a functional way. They can be used to get, set, and update values in data structures, as well as to traverse and explore data.

## Manual Derivation of Optics

Before we dive into auto-derivation of optics and how we can derive optics from a ZIO Schema, let's take a look at the pure optics and how we can create them manually using [ZIO Optics](https://zio.dev/zio-optics) library.

First, we should add `zio-optics` to our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-optics" % "<version>"
```

Now let's define a simple data type called `User` and create two optics for its `name` and `age` fields:

```scala

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

Now we can use these optics to get, set, and update values in the `Person` data structure:

```scala

object Main extends ZIOAppDefault {
  def run =
    for {
      _ <- ZIO.debug("Pure Optics")
      user = User("John", 34)

      updatedUser1 <- ZIO.fromEither(nameLens.setOptic("Jane")(user))
      _ <- ZIO.debug(s"Name of user updated: $updatedUser1")

      updatedUser2 <- ZIO.fromEither(ageLens.setOptic(32)(user))
      _ <- ZIO.debug(s"Age of user updated: $updatedUser2")

      updatedUser3 <- ZIO.fromEither(
        ageAndNameLens.set(("Jane", 32))(User("John", 34))
      )
      _ <- ZIO.debug(s"Name and age of the user updated: $updatedUser3")
    } yield ()
}
```

## Automatic Derivation of Optics

ZIO Schema has a module called `zio-schema-optics` which provides functionalities to derive various optics from a ZIO Schema.

By having a `Schema[A]`, we can derive optics automatically from a schema. This means that we don't have to write the optics manually, but instead, we can use the `Schema#makeAccessors` method which will derive the optics for us:

```scala
trait Schema[A] {
  def makeAccessors(b: AccessorBuilder): Accessors[b.Lens, b.Prism, b.Traversal]
}
```

It takes an `AccessorBuilder` which is an interface of the creation of optics:

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

Let's take a look at how we can derive optics using ZIO Schema Optics.

### Installation

To be able to derive optics from a ZIO Schema, we need to add the following line to our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-optics" % 1.7.5
```

This package contains a `ZioOpticsBuilder` which is an implementation of the `AccessorBuilder` interface based on ZIO Optics library.

Now we are ready to try any of the following examples:

### Examples

#### Lens

Now we can derive the schema for our `User` data type in its companion object, and then derive optics using `Schema#makeAccessors` method:

```scala

case class User(name: String, age: Int)

object User {
  implicit val schema: CaseClass2[String, Int, User] =
    DeriveSchema.gen[User].asInstanceOf[CaseClass2[String, Int, User]]

  val (nameLens, ageLens) = schema.makeAccessors(ZioOpticsBuilder)
}
```

Based on the type of the schema, the `makeAccessors` method will derive the proper optics for us.

Now we can use these optics to update values in the `User` data structure:

```scala
object MainApp extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.debug("Auto-derivation of Optics")
    user = User("John", 42)

    updatedUser1 = User.nameLens.set("Jane")(user)
    _ <- ZIO.debug(s"Name of user updated: $updatedUser1")

    updatedUser2 = User.ageLens.set(32)(user)
    _ <- ZIO.debug(s"Age of user updated: $updatedUser2")

    nameAndAgeLens = User.nameLens.zip(User.ageLens)
    updatedUser3   = nameAndAgeLens.set(("Jane", 32))(user)
    _ <- ZIO.debug(s"Name and age of the user updated: $updatedUser3")
  } yield ()
}
```

Output:

```scala
Auto-derivation of Lens Optics:
Name of user updated: Right(User(Jane,42))
Age of user updated: Right(User(John,32))
Name and age of the user updated: Right(User(Jane,32))
```

#### Prism

```scala

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
    DeriveSchema.gen[Shape].asInstanceOf[Enum2[Circle, Rectangle, Shape]]

  val (circlePrism, rectanglePrism) =
    schema.makeAccessors(ZioOpticsBuilder)
}

object MainApp extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.debug("Auto-derivation of Prism Optics")
    shape = Circle(1.2)
    _ <- ZIO.debug(s"Original shape: $shape")
    updatedShape <- ZIO.fromEither(
      Shape.rectanglePrism.setOptic(Rectangle(2.0, 3.0))(shape)
    )
    _ <- ZIO.debug(s"Updated shape: $updatedShape")
  } yield ()

}
```

Output:

```scala
Auto-derivation of Prism Optics:
Original shape: Circle(1.2)
Updated shape: Rectangle(2.0,3.0)
```

#### Traversal

```scala

object IntList {
  implicit val listschema: Schema.Sequence[List[Int], Int, String] =
    Sequence[List[Int], Int, String](
      elementSchema = Schema[Int],
      fromChunk = _.toList,
      toChunk = i => Chunk.fromIterable(i),
      annotations = Chunk.empty,
      identity = "List"
    )

  val traversal: ZTraversal[List[Int], List[Int], Int, Int] =
    listschema.makeAccessors(ZioOpticsBuilder)
}

object MainApp extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.debug("Auto-derivation of Traversal Optic:")
    list = List(1, 2, 3, 4, 5)
    _           <- ZIO.debug(s"Original list: $list")
    updatedList <- ZIO.fromEither(IntList.traversal.set(Chunk(1, 5, 7))(list))
    _           <- ZIO.debug(s"Updated list: $updatedList")
  } yield ()
}
```

Output:

```scala
Auto-derivation of Traversal Optic:
Original list: List(1, 2, 3, 4, 5)
Updated list: List(1, 5, 7, 4, 5)
```
