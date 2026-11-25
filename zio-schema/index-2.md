# ZIO Schema Operations

> Once we define our schemas, we can use them to perform a variety of operations. Every operation is a method on `Schema[A]`, so it works the same way for any type that has a schema. The pages in this section cover the most common operations:

- [Getting The Default Value](the-default-value.md) describes `Schema#defaultValue` and `Schema#default`.
- [Transforming Schemas](transforming-schemas.md) describes `Schema#transform` and `Schema#transformOrFail`.
- [Validation](validating-types.md) describes `Schema#validate` and the `Validation` rules.
- [Diffing and Patching](diffing-and-patching.md) describes `Schema#diff` and `Schema#patch`.
- [Dynamic Data Representation](dynamic-data-representation.md) describes `Schema#toDynamic` and `Schema#fromDynamic`.
- [Schema Migration](schema-migration.md) describes `Schema#migrate` and `Schema#coerce`.
- [Serialization of the Schema Itself](serialization-of-the-schema-itself.md) describes `Schema#ast` and `Schema#serializable`.

The [Schema Methods](../reference/schema-methods.md) reference page shows each method with a short example.

## Method Overview

The following simplified listing shows the signatures of the operations on `Schema[A]`, grouped by purpose:

```scala
sealed trait Schema[A] {
  type Accessors[Lens[_, _, _], Prism[_, _, _], Traversal[_, _]]

  // Building new schemas
  def ? : Schema[Option[A]]
  def optional: Schema[Option[A]]
  def <*>[B](that: Schema[B]): Schema[(A, B)]
  def zip[B](that: Schema[B]): Schema[(A, B)]
  def <+>[B](that: Schema[B]): Schema[scala.util.Either[A, B]]
  def orElseEither[B](that: Schema[B]): Schema[scala.util.Either[A, B]]
  def repeated: Schema[Chunk[A]]
  def transform[B](f: A => B, g: B => A)(implicit loc: SourceLocation): Schema[B]
  def transformOrFail[B](
    f: A => scala.util.Either[String, B],
    g: B => scala.util.Either[String, A]
  )(implicit loc: SourceLocation): Schema[B]

  // Defaults and annotations
  def defaultValue: scala.util.Either[String, A]
  def default(value: A): Schema[A]
  def annotations: Chunk[Any]
  def annotate(annotation: Any): Schema[A]

  // Validation
  def validate(value: A)(implicit schema: Schema[A]): Chunk[ValidationError]
  def validation(validation: Validation[A]): Schema[A]

  // Dynamic values, diffing, and migration
  def toDynamic(value: A): DynamicValue
  def fromDynamic(value: DynamicValue): scala.util.Either[String, A]
  def diff(thisValue: A, thatValue: A): Patch[A]
  def patch(oldValue: A, diff: Patch[A]): scala.util.Either[String, A]
  def migrate[B](newSchema: Schema[B]): Either[String, A => scala.util.Either[String, B]]
  def coerce[B](newSchema: Schema[B]): Either[String, Schema[B]]

  // Structure
  def ast: MetaSchema
  def serializable: Schema[Schema[A]]
  def ordering: Ordering[A]
  def makeAccessors(b: AccessorBuilder): Accessors[b.Lens, b.Prism, b.Traversal]
}
```
