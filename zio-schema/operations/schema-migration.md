# Schema Migration

> With ZIO Schema, we can automatically migrate data from one version of a schema to another. As software evolves, we often need to add, change or remove old fields. ZIO Schema provides two methods called `migrate` and `coerce` which help migrate the old schema to the new one:

## Automatic Migration

With ZIO Schema, we can automatically migrate data from one version of a schema to another. As software evolves, we often need to add, change or remove old fields. ZIO Schema provides two methods called `migrate` and `coerce` which help migrate the old schema to the new one:

```scala
sealed trait Schema[A] {
  def migrate[B](newSchema: Schema[B]): Either[String, A => scala.util.Either[String, B]]

  def coerce[B](newSchema: Schema[B]): Either[String, Schema[B]]
}
```

The `migrate` method takes a new schema and returns a function that can migrate values of the old schema to values of the new schema as a `Right` value of `Either`. If the schemas have unambiguous transformations or are incompatible, the method returns a `Left` value containing an error message.

## Manual Migration

By having `DynamicValue` which its type information embedded in the data itself, we can perform migrations of the data easily by applying a sequence of migration steps to the data.

```scala
trait DynamicValue {
  def transform(transforms: Chunk[Migration]): Either[String, DynamicValue]
}
```

The `Migration` is a sealed trait with several subtypes:

```scala
sealed trait Migration
object Migration {
  final case class AddNode(override val path: NodePath, node: MetaSchema) extends Migration

  final case class DeleteNode(override val path: NodePath) extends Migration

  final case class AddCase(override val path: NodePath, node: MetaSchema) extends Migration

  // ...
}
```

Using the `Migration` ADT we can describe the migration steps and then we can apply them to the `DynamicValue`. Let's try a simple example:

```scala

case class Person1(name: String, age: Int)

object Person1 {
  implicit val schema: Schema[Person1] = DeriveSchema.gen
}

case class Person2(name: String)

object Person2 {
  implicit val schema: Schema[Person2] = DeriveSchema.gen
}

val person1: Person1 = Person1("John Doe", 42)

val migrations: Chunk[Migration] = Chunk(DeleteNode(NodePath.root / "age"))

val person2 = DeriveSchema
  .gen[Person1]
  .toDynamic(person1)
  .transform(migrations)
  .flatMap(_.toTypedValue[Person2])
  
assert(person2 == Right(Person2("John Doe")))
```

## Deriving Migrations

ZIO Schema provides a way to derive migrations automatically using the `Migration.derive` operation:

```scala
object Migration {
  def derive(from: MetaSchema, to: MetaSchema): Either[String, Chunk[Migration]]
}
```

It takes two `MetaSchema` values, the old and the new schema, and returns a `Chunk[Migration]` that describes the migrations steps. Let's try a simple example:

```scala

case class Person1(name: String, age: Int, language: String, height: Int)

object Person1 {
  implicit val schema: Schema[Person1] = DeriveSchema.gen
}

case class Person2(
    name: String,
    role: String,
    language: Set[String],
    height: Double
)

object Person2 {
  implicit val schema: Schema[Person2] = DeriveSchema.gen
}

val migrations = Migration.derive(
  MetaSchema.fromSchema(Person1.schema),
  MetaSchema.fromSchema(Person2.schema)
)

println(migrations)
```

The output of the above code is:

```scala
Right(
  Chunk(IncrementDimensions(Chunk(language,item),1),
  ChangeType(Chunk(height),double),
  AddNode(Chunk(role),string),
  DeleteNode(Chunk(age)))
)
```

This output describes a series of migration steps that should be applied to the old schema to be transformed into the new schema.
