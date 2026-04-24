# ZIO Schema Operations

> Once we have defined our schemas, we can use them to perform a variety of operations. In this section, we will explore some of the most common operations that we can perform on schemas.

Once we have defined our schemas, we can use them to perform a variety of operations. In this section, we will explore some of the most common operations that we can perform on schemas.

Before diving into the details, let's see a quick overview of the operations that we can perform on schemas:

```scala
sealed trait Schema[A] {
  self =>

  type Accessors[Lens[_, _, _], Prism[_, _, _], Traversal[_, _]]

  def ? : Schema[Option[A]]

  def <*>[B](that: Schema[B]): Schema[(A, B)]

  def <+>[B](that: Schema[B]): Schema[scala.util.Either[A, B]]

  def defaultValue: scala.util.Either[String, A]

  def annotations: Chunk[Any]

  def ast: MetaSchema 

  def annotate(annotation: Any): Schema[A]

  def coerce[B](newSchema: Schema[B]): Either[String, Schema[B]]

  def diff(thisValue: A, thatValue: A): Patch[A]

  def patch(oldValue: A, diff: Patch[A]): scala.util.Either[String, A]

  def fromDynamic(value: DynamicValue): scala.util.Either[String, A] 

  def makeAccessors(b: AccessorBuilder): Accessors[b.Lens, b.Prism, b.Traversal]

  def migrate[B](newSchema: Schema[B]): Either[String, A => scala.util.Either[String, B]]

  def optional: Schema[Option[A]]

  def ordering: Ordering[A]

  def orElseEither[B](that: Schema[B]): Schema[scala.util.Either[A, B]]

  def repeated: Schema[Chunk[A]]

  def serializable: Schema[Schema[A]]

  def toDynamic(value: A): DynamicValue

  def transform[B](f: A => B, g: B => A): Schema[B]
  
  def transformOrFail[B](f: A => scala.util.Either[String, B], g: B => scala.util.Either[String, A]): Schema[B]

  def validate(value: A)(implicit schema: Schema[A]): Chunk[ValidationError]

  def zip[B](that: Schema[B]): Schema[(A, B)]
}
```
