# Basic Building Blocks

> To get started, first we need to understand that a ZIO Schema is basically built-up from these three
sealed traits: `Record[R]`, `Enum[A]` and `Sequence[Col, Elem]`, along with the case class `Primitive[A]`. Every other type is just a specialisation of one of these (or not relevant to get you started).

To get started, first we need to understand that a ZIO Schema is basically built-up from these three
sealed traits: `Record[R]`, `Enum[A]` and `Sequence[Col, Elem]`, along with the case class `Primitive[A]`. Every other type is just a specialisation of one of these (or not relevant to get you started).

The core data type of ZIO Schema is a `Schema[A]` which is **invariant in `A`** by necessity, because a Schema allows us to derive operations that produce an `A` but also operations that consume an `A` and that imposes limitations on the types of **transformation operators** and **composition operators** that we can provide based on a `Schema`.

It looks kind of like this (simplified):

```scala
sealed trait Schema[A] { self =>
  def zip[B](that: Schema[B]): Schema[(A, B)]

  def transform[B](f: A => B, g: B => A): Schema[B]
}
```

## Primitives

To describe scalar data type `A`, we use the `Primitive[A]` data type which basically is a wrapper around `StandardType`:

```scala
case class Primitive[A](standardType: StandardType[A]) extends Schema[A]
```

Primitive values are represented using the `Primitive[A]` type class and represent the elements that we cannot further define through other means. If we visualize our data structure as a tree, primitives are the leaves.

For a list of all standard types (and therefore primitive types) with built-in support, please see the [standard type reference](standard-type-reference.md)

Inside `Schema`'s companion object, we have an implicit conversion from `StandardType[A]` to `Schema[A]`:

```scala
object Schema {
   implicit def primitive[A](implicit standardType: StandardType[A]): Schema[A] = ???
}
```

So we can easily create a `Schema` for a primitive type `A` either by calling `Schema.primitive[A]` or by calling `Schema.apply[A]`:

```scala
val intSchema1: Schema[Int] = Schema[Int]
val intSchema2: Schema[Int] = Schema.primitive[Int] 
```

## Fail

To represents the absence of schema information for the given `A` type, we can use `Schema.fail` constructor, which creates the following schema:

```scala
object Schema {
  case class Fail[A](
    message: String,
    annotations: Chunk[Any] = Chunk.empty
  ) extends Schema[A]
}
```

## Collections

### Sequence

Often we have a type that is a collection of elements. For example, we might have a `List[User]`. This is called a `Sequence` and is represented using the `Sequence[Col, Elem, I]` type class:

```scala
object Schema {
  sealed trait Collection[Col, Elem] extends Schema[Col]
  
  final case class Sequence[Col, Elem, I](
      elementSchema: Schema[Elem],
      fromChunk: Chunk[Elem] => Col,
      toChunk: Col => Chunk[Elem],
      override val annotations: Chunk[Any] = Chunk.empty,
      identity: I
    ) extends Collection[Col, Elem]
}
```

The `Sequence` can be anything that can be isomorphic to a list. 

Here is an example schema for list of `Person`s:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}

val personListSchema: Schema[List[Person]] =
  Sequence[List[Person], Person, String](
    elementSchema = Schema[Person],
    fromChunk = _.toList,
    toChunk = i => Chunk.fromIterable(i),
    annotations = Chunk.empty,
    identity = "List"
  )
```

ZIO Schema has `Schema.list[A]`, `Schema.chunk[A]` and `Schema.vector[A]` constructors that create `Schema[List[A]]`, `Schema[Chunk[A]]` and `Schema[Vector[A]]` for us:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
  
  implicit val listSchema:   Schema[List[Person]]   = Schema.list[Person]
  implicit val chunkSchema:  Schema[Chunk[Person]]  = Schema.chunk[Person]
  implicit val vectorSchema: Schema[Vector[Person]] = Schema.vector[Person]
}
```

### Map

Likewise, we can have a type that is a map of keys to values. ZIO Schema represents this using the following type class:

```scala
object Schema {
  sealed trait Collection[Col, Elem] extends Schema[Col]

  case class Map[K, V](
    keySchema: Schema[K],
    valueSchema: Schema[V],
    override val annotations: Chunk[Any] = Chunk.empty
  ) extends Collection[scala.collection.immutable.Map[K, V], (K, V)]
}
```

It stores the key and value schemas. Like `Sequence`, instead of using `Map` directly, we can use the `Schema.map[K, V]` constructor:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
  
  implicit val mapSchema: Schema[scala.collection.immutable.Map[String, Person]] = 
    Schema.map[String, Person]
}
```

### Set

The `Set` type class is similar to `Sequence` and `Map`. It is used to represent a schema for a set of elements:

```scala
object Schema {
  sealed trait Collection[Col, Elem] extends Schema[Col]

  case class Set[A](
    elementSchema: Schema[A],
    override val annotations: Chunk[Any] = Chunk.empty
  ) extends Collection[scala.collection.immutable.Set[A], A]
}
```

To create a `Schema` for a `Set[A]`, we can use the above type class directly or use the `Schema.set[A]` constructor:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
  
  implicit val setSchema: Schema[scala.collection.immutable.Set[Person]] = 
    Schema.set[Person]
}
```

## Records

Our data structures usually are composed of a lot of types. For example, we might have a `User` type that has a `name` field, an `age` field, an `address` field, and a `friends` field.

```scala
case class User(name: String, age: Int, address: Address, friends: List[User])
```

This is called a **product type** in functional programming. The equivalent of a product type in ZIO Schema is called a record.

In ZIO Schema such a record would be represented using the `Record[R]` typeclass:

```scala
object Schema {
  sealed trait Field[R, A] {
    type Field <: Singleton with String
    def name: Field
    def schema: Schema[A]
  }

  sealed trait Record[R] extends Schema[R] {
    def id: TypeId
    def fields: Chunk[Field[_]]
    def construct(fieldValues: Chunk[Any]): Either[String, R]
  }
}
```

ZIO Schema has specialized record types for case classes, called `CaseClass1[A, Z]`, `CaseClass2[A1, A2, Z]`, ..., `CaseClass22`. Here is the definition of `apply` method of `CaseClass1` and `CaseClass2`:

```scala
sealed trait CaseClass1[A, Z] extends Record[Z]

object CaseClass1 {
  def apply[A, Z](
    id0: TypeId,
    field0: Field[Z, A],
    defaultConstruct0: A => Z,
    annotations0: Chunk[Any] = Chunk.empty
  ): CaseClass1[A, Z] = ???
}

object CaseClass2 {
  def apply[A1, A2, Z](
    id0: TypeId,
    field01: Field[Z, A1],
    field02: Field[Z, A2],
    construct0: (A1, A2) => Z,
    annotations0: Chunk[Any] = Chunk.empty
  ): CaseClass2[A1, A2, Z] = ???
}
```

As we can see, they take a `TypeId`, a number of fields of type `Field`, and a construct function. The `TypeId` is used to uniquely identify the type. The `Field` is used to store the name of the field and the schema of the field. The `construct` is used to construct the type from the field values.

Here is an example of defining schema for `Person` data type:

```scala

final case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] =
    Schema.CaseClass2[String, Int, Person](
      id0 = TypeId.fromTypeName("Person"),
      field01 = 
        Schema.Field(
          name0 = "name",
          schema0 = Schema[String],
          get0 = _.name,
          set0 = (p, x) => p.copy(name = x)
        ),
      field02 = 
        Schema.Field(
          name0 = "age",
          schema0 = Schema[Int],
          get0 = _.age,
          set0 = (person, age) => person.copy(age = age)
        ),
      construct0 = (name, age) => Person(name, age),
    )
}
```

There is also the `GenericRecord` which is used to either ad-hoc records or records that have more than 22 fields:

```scala
object Schema {
  sealed case class GenericRecord(
    id: TypeId,
    fieldSet: FieldSet,
    override val annotations: Chunk[Any] = Chunk.empty
  ) extends Record[ListMap[String, _]]
}
```

## Enumerations

Other times, you might have a type that represents a list of different types. For example, we might have a type, like this:

```scala
sealed trait PaymentMethod 

object PaymentMethod {
  final case class CreditCard(number: String, expirationMonth: Int, expirationYear: Int) extends PaymentMethod
  final case class WireTransfer(accountNumber: String, bankCode: String) extends PaymentMethod
}
```

In functional programming, this kind of type is called a **sum type**:
- In Scala 2, this is called a **sealed trait**.
- In Scala3, this is called an **enum**.

In ZIO Schema we call these types `enumeration` types, and they are represented using the `Enum[A]` type class.

```scala
object Schema {
  sealed trait Enum[Z] extends Schema[Z]
}
```

It has specialized types `Enum1[A, Z]`, `Enum2[A1, A2, Z]`, ..., `Enum22[A1, A2, ..., A22, Z]` for enumerations with 1, 2, ..., 22 cases. Here is the definition of `Enum1` and `Enum2`:

```scala 
  sealed case class Enum1[A, Z](
    id: TypeId,
    case1: Case[Z, A],
    annotations: Chunk[Any] = Chunk.empty
  ) extends Enum[Z]

  sealed case class Enum2[A1, A2, Z](
    id: TypeId,
    case1: Case[Z, A1],
    case2: Case[Z, A2],
    annotations: Chunk[Any] = Chunk.empty
  ) extends Enum[Z]

  // Enum3, Enum4, ..., Enum22
}
```

If the enumeration has more than 22 cases, we can use the `EnumN` type class:

```scala
object Schema {
  sealed case class EnumN[Z, C <: CaseSet.Aux[Z]](
    id: TypeId,
    caseSet: C,
    annotations: Chunk[Any] = Chunk.empty
  ) extends Enum[Z]
}
```

It has a simple constructor called `Schema.enumeration`:

```scala
object Schema {
  def enumeration[A, C <: CaseSet.Aux[A]](id: TypeId, caseSet: C): Schema[A] = ???
}
```

## Optionals

To create a `Schema` for optional values, we can use the `Optional` type class:

```scala
object Schema {
  case class Optional[A](
    schema: Schema[A],
    annotations: Chunk[Any] = Chunk.empty
  ) extends Schema[Option[A]]
}
```

Using the `Schema.option[A]` constructor, makes it easier to do so:

```scala
val option: Schema[Option[Person]] = Schema.option[Person]
```

## Either

Here is the same but for `Either`:

```scala
object Schema {
  case class Either[A, B](
    left: Schema[A],
    right: Schema[B],
    annotations: Chunk[Any] = Chunk.empty
  ) extends Schema[scala.util.Either[A, B]]
}
```

We can use `Schema.either[A, B]` to create a `Schema` for `scala.util.Either[A, B]`:

```scala
val eitherPersonSchema: Schema[scala.util.Either[String, Person]] = 
  Schema.either[String, Person]
```

## Tuple

Each schema has a `Schema#zip` operator that allows us to combine two schemas and create a schema for a tuple of the two types:

```scala
object Schema {
  def zip[B](that: Schema[B]): Schema[(A, B)] = 
    Schema.Tuple2(self, that)
}
```

It is implemented using the `Schema.Tuple2` type class:

```scala
object Schema {
  final case class Tuple2[A, B](
    left: Schema[A],
    right: Schema[B], 
    annotations: Chunk[Any] = Chunk.em
    pty
  ) extends Schema[(A, B)] 
}
```

ZIO Schema also provides implicit conversions for tuples of arity 2, 3, ..., 22:

```scala
object Schema {
  implicit def tuple2[A, B](implicit c1: Schema[A], c2: Schema[B]): Schema[(A, B)] =
    c1.zip(c2)

  implicit def tuple3[A, B, C](implicit c1: Schema[A], c2: Schema[B], c3: Schema[C]): Schema[(A, B, C)] =
    c1.zip(c2).zip(c3).transform({ case ((a, b), c) => (a, b, c) }, { case (a, b, c) => ((a, b), c) })

  // tuple3, tuple4, ..., tuple22
}
```

So we can easily create a `Schema` for a tuple of n elements, just by calling `Schema[(A1, A2, ..., An)]`:

```scala

val tuple2: Schema[(String, Int)]          = Schema[(String, Int)]
val tuple3: Schema[(String, Int, Boolean)] = Schema[(String, Int, Boolean)]
// ...
```
