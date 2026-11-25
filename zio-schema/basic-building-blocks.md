# Basic Building Blocks

> Every Schema subtype: primitives, records, fields, enums, cases, collections, optionals, tuples, Either, Fallback, Transform, Lazy, Dynamic and Fail.

A ZIO Schema is built up from a few kinds of schemas: primitives, records, enumerations and collections. Most other schemas, such as `Optional` and `Tuple2`, combine these. `Schema[A]` is a sealed trait, so every schema is one of a **closed set of schema data types**, each describing one shape of data. Codecs, differs and other generic code take a `Schema[A]` apart by matching on these subtypes. This page describes every subtype with its fields, constructors and an example.

You rarely build these values by hand: `DeriveSchema.gen`, the implicit instances in the `Schema` companion, and methods such as `Schema#transform` build them for you. This page matters when you write a schema by hand, write a codec or another generic function over schemas, or need the exact shape of a schema.

The type parameter `A` is the type of value the schema describes. `Schema` is **invariant in `A`** by necessity: a schema lets us derive operations that produce an `A` and operations that consume an `A`, which limits the transformation and composition operators it can offer. Every schema data type extends `Schema`:

```scala
sealed trait Schema[A] {
  def annotations: Chunk[Any]
  def annotate(annotation: Any): Schema[A]
  def defaultValue: scala.util.Either[String, A]
  def zip[B](that: Schema[B]): Schema[(A, B)]
  def transform[B](f: A => B, g: B => A)(implicit loc: SourceLocation): Schema[B]
  // ... the shared methods are listed on the Schema Methods page
}
```

The subtypes form this hierarchy. `Field` and `Case` are not schemas; they are the parts that records and enums are made of:

```
Schema[A]
├── Primitive[A]
├── Record[R]
│   ├── GenericRecord                          R = ListMap[String, _]
│   └── CaseClass0[Z] ... CaseClass22[A1, ..., A22, Z]
├── Enum[Z]
│   ├── Enum1[A, Z] ... Enum22[A1, ..., A22, Z]
│   └── EnumN[Z, C <: CaseSet.Aux[Z]]
├── Collection[Col, Elem]
│   ├── Sequence[Col, Elem, I]
│   ├── NonEmptySequence[Col, Elm, I]
│   ├── Map[K, V]
│   ├── NonEmptyMap[K, V]
│   └── Set[A]
├── Optional[A]
├── Tuple2[A, B]
├── Either[A, B]
├── Fallback[A, B]
├── Transform[A, B, I]
├── Lazy[A]
├── Dynamic
└── Fail[A]

Field[R, A]   one field of a Record
Case[R, A]    one case of an Enum
```

All of them are members of the `Schema` companion object, so we write them as `Schema.Primitive`, `Schema.Record` and so on. The methods that every schema shares, such as `Schema#toDynamic` or `Schema#validate`, are covered in [Schema Methods](reference/schema-methods.md).

## Usage

A generic function over schemas matches on the subtypes. This one describes the shape of any schema, and we run it on a few schemas built by derivation and by the implicit instances:

```scala
import zio.schema._

def describe(schema: Schema[_]): String =
  schema match {
    case Schema.Primitive(standardType, _) => s"primitive ${standardType.tag}"
    case record: Schema.Record[_]          => s"record(${record.fields.map(_.name).mkString(", ")})"
    case enumeration: Schema.Enum[_]       => s"enum(${enumeration.cases.map(_.id).mkString(", ")})"
    case sequence: Schema.Sequence[_, _, _] => s"sequence of ${describe(sequence.elementSchema)}"
    case Schema.Optional(inner, _)         => s"optional ${describe(inner)}"
    case transform: Schema.Transform[_, _, _] => s"transform of ${describe(transform.schema)}"
    case lazySchema: Schema.Lazy[_]        => describe(lazySchema.schema)
    case other                             => other.getClass.getSimpleName
  }

final case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}

describe(Schema[Int])
// res1: String = "primitive int"
describe(Person.schema)
// res2: String = "record(name, age)"
describe(Schema[List[Option[String]]])
// res3: String = "sequence of optional primitive string"
describe(Schema[BigDecimal])
// res4: String = "transform of primitive bigDecimal"
describe(Schema[Either[String, Int]])
// res5: String = "Either"
```

## Primitive

`Schema.Primitive` describes a value of one of the built-in standard types, such as `Int`, `String` or `java.time.Instant`: an element that we cannot define further through other schemas. If we picture a data structure as a tree, primitives are its leaves. The `StandardType` sets the type and its default value. The [Standard Type Reference](standard-type-reference.md) lists all of them:

```scala
final case class Primitive[A](standardType: StandardType[A], annotations: Chunk[Any] = Chunk.empty)
    extends Schema[A]
```

The `Schema` companion has an implicit method that builds a `Primitive` for any type with an implicit `StandardType`:

```scala
object Schema {
  implicit def primitive[A](implicit standardType: StandardType[A]): Schema[A]
}
```

So `Schema.primitive[A]` and `Schema[A]` for a standard type both return a `Primitive`:

```scala
import zio.schema._

val intSchema1: Schema[Int] = Schema[Int]
val intSchema2: Schema[Int] = Schema.primitive[Int]
```

We can also build one directly and give it a different default:

```scala
val port: Schema[Int] = Schema.Primitive(StandardType.IntType)
// port: Schema[Int] = Primitive(
//   standardType = int,
//   annotations = IndexedSeq()
// )
port.defaultValue
// res7: Either[String, Int] = Right(value = 0)
port.default(8080).defaultValue
// res8: Either[String, Int] = Right(value = 8080)
```

`Schema#default` stores the value as a `@fieldDefaultValue` annotation, and only `Primitive` reads that annotation back, in `Primitive#defaultValue`. On a record schema, the annotation has no effect:

```scala
final case class Point(x: Int, y: Int)

object Point {
  implicit val schema: Schema[Point] = DeriveSchema.gen[Point]
}

Point.schema.default(Point(1, 2)).defaultValue
// res9: Either[String, Point] = Right(value = Point(x = 0, y = 0))
```

:::caution[`Schema#default` Works Only on Primitive Schemas]
`Schema#default(v)` only takes effect on primitive schemas. On records, enums, collections and transformed schemas such as `Schema[BigDecimal]`, `Schema#defaultValue` ignores it. To change the default of a record field, use a Scala default parameter or the `@fieldDefaultValue` annotation on the field; see [Schema Annotations](schema-annotations.md).
:::

## Records

Our data types are usually composed of other types: a user, for example, has a name, an age, an address and a list of friends. Functional programming names such a type a **product type**, and ZIO Schema calls it a record: a fixed list of named fields. Case classes become `CaseClass0` to `CaseClass22`, and records whose fields are only known at runtime use `GenericRecord`.

### The Record Trait

`Schema.Record[R]` is the common parent of every record schema. It lists the fields, and it converts between a value and its field values:

```scala
sealed trait Record[R] extends Schema[R] {
  val fields: Chunk[Field[R, _]]
  lazy val nonTransientFields: Chunk[Field[R, _]]
  val rejectExtraFields: Boolean

  def id: TypeId
  def construct(fieldValues: Chunk[Any])(implicit unsafe: Unsafe): scala.util.Either[String, R]
  def deconstruct(value: R)(implicit unsafe: Unsafe): Chunk[Option[Any]]
  def defaultValue: scala.util.Either[String, R]
}
```

`Record#construct` builds a value from field values in field order, and `Record#deconstruct` returns the field values of a value. Both take an implicit `zio.Unsafe`, which marks them as unsafe operations: `Record#construct` casts each field value to its field type. `Record#nonTransientFields` skips fields annotated with `@transientField`, and `Record#rejectExtraFields` is true when the record carries the `@rejectExtraFields` annotation. `Record#defaultValue` builds a value from the default of every field. We look inside a derived schema:

```scala
import zio.{Chunk, Unsafe}
import zio.schema._

final case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}

val personRecord: Option[Schema.Record[Person]] =
  Person.schema match {
    case record: Schema.Record[Person @unchecked] => Some(record)
    case _                                        => None
  }
```

With the record in hand, we read its fields and convert values in both directions:

```scala
personRecord.map(_.id.name)
// res11: Option[String] = Some(value = "Person")
personRecord.map(_.fields.map(_.name))
// res12: Option[Chunk[(Schema.Field[Person, _])#Field]] = Some(
//   value = IndexedSeq("name", "age")
// )
personRecord.map(record => Unsafe.unsafe { implicit unsafe => record.deconstruct(Person("Ann", 30)) })
// res13: Option[Chunk[Option[Any]]] = Some(
//   value = IndexedSeq(Some(value = "Ann"), Some(value = 30))
// )
personRecord.map(record => Unsafe.unsafe { implicit unsafe => record.construct(Chunk("Bob", 25)) })
// res14: Option[Either[String, Person]] = Some(
//   value = Right(value = Person(name = "Bob", age = 25))
// )
personRecord.map(_.defaultValue)
// res15: Option[Either[String, Person]] = Some(
//   value = Right(value = Person(name = "", age = 0))
// )
```

### Fields

`Schema.Field[R, A]` describes one field of type `A` in a record of type `R`. It carries the field's name, schema, annotations and validation rule, plus a getter and a setter:

```scala
sealed trait Field[R, A] {
  type Field <: Singleton with String

  def name: Field
  def schema: Schema[A]
  def annotations: Chunk[Any]
  def validation: Validation[A]
  def get: R => A
  def set: (R, A) => R

  lazy val defaultValue: Option[A]
  val optional: Boolean
  val transient: Boolean
  val fieldName: String
  val aliases: Set[String]
  val nameAndAliases: Set[String]
}

object Field {
  type WithFieldName[R, F <: Singleton with String, A] = Field[R, A] { type Field = F }

  def apply[R, A](
    name0: String,
    schema0: Schema[A],
    annotations0: Chunk[Any] = Chunk.empty,
    validation0: Validation[A] = Validation.succeed[A],
    get0: R => A,
    set0: (R, A) => R
  ): Field[R, A]

  def unapply[R, A](field: Field[R, A]): Some[(String, Schema[A], Chunk[Any], Validation[A], R => A, (R, A) => R)]
}
```

The type member `Field` is the singleton type of the name; derivation uses it, through `Field.WithFieldName`, to give optics a typed field name. The derived members read the annotations:

- `Field#defaultValue` is the value of a `@fieldDefaultValue` annotation, or else the default of the field's schema.
- `Field#optional` is true when the field has an `@optionalField` or a `@fieldDefaultValue` annotation.
- `Field#transient` is true when the field has a `@transientField` annotation.
- `Field#fieldName` is the name from a `@fieldName` annotation, or else `Field#name`.
- `Field#aliases` collects the names of `@fieldNameAliases` annotations, and `Field#nameAndAliases` adds `Field#fieldName` to them.

`Field#validation` holds the rule that `Schema.validate` runs for the field; see [Validation](operations/validating-types.md). We build a field by hand and query it:

```scala
import zio.schema.annotation.{fieldDefaultValue, fieldNameAliases}
import zio.schema.validation.Validation

val ageField: Schema.Field[Person, Int] =
  Schema.Field[Person, Int](
    name0 = "age",
    schema0 = Schema[Int],
    annotations0 = Chunk(fieldDefaultValue(18), fieldNameAliases("years")),
    validation0 = Validation.greaterThan(0),
    get0 = _.age,
    set0 = (person, age) => person.copy(age = age)
  )
```

The getter, the setter and the derived members behave as follows:

```scala
ageField.get(Person("Ann", 30))
// res16: Int = 30
ageField.set(Person("Ann", 30), 31)
// res17: Person = Person(name = "Ann", age = 31)
ageField.defaultValue
// res18: Option[Int] = Some(value = 18)
ageField.optional
// res19: Boolean = true
ageField.nameAndAliases
// res20: Set[String] = Set("years", "age")
ageField.validation.validate(-1).isLeft
// res21: Boolean = true
```

### Case Class Schemas

`CaseClass0` to `CaseClass22` describe case classes with zero to 22 fields. They are sealed traits whose companion objects have `CaseClassN.apply` and `CaseClassN.unapply`. `CaseClass0` describes case objects and parameterless case classes, `CaseClass1` takes `field0` and `defaultConstruct0`, and `CaseClass2` to `CaseClass22` take one `field0N` parameter per field and a `construct0` function:

```scala
sealed trait CaseClass0[Z] extends Record[Z] {
  def defaultConstruct: () => Z
}

object CaseClass0 {
  def apply[Z](id0: TypeId, defaultConstruct0: () => Z, annotations0: Chunk[Any] = Chunk.empty): CaseClass0[Z]
}

sealed trait CaseClass1[A, Z] extends Record[Z] {
  def field: Field.WithFieldName[Z, Field1, A]
  def defaultConstruct: A => Z
}

object CaseClass1 {
  def apply[A, Z](
    id0: TypeId,
    field0: Field[Z, A],
    defaultConstruct0: A => Z,
    annotations0: Chunk[Any] = Chunk.empty
  ): CaseClass1[A, Z]
}

sealed trait CaseClass2[A1, A2, Z] extends Record[Z] {
  def field1: Field.WithFieldName[Z, Field1, A1]
  def field2: Field.WithFieldName[Z, Field2, A2]
  def construct: (A1, A2) => Z
}

object CaseClass2 {
  def apply[A1, A2, Z](
    id0: TypeId,
    field01: Field[Z, A1],
    field02: Field[Z, A2],
    construct0: (A1, A2) => Z,
    annotations0: Chunk[Any] = Chunk.empty
  ): CaseClass2[A1, A2, Z]
}

// CaseClass3 ... CaseClass22 follow the CaseClass2 pattern, with field1 ... fieldN
```

The companions of `CaseClass1` to `CaseClass22` also have a `WithFields` type alias that fixes the singleton field-name types, which derivation uses for optics. A case object becomes a `CaseClass0` whose constructor returns the object:

```scala
case object Ping

val pingSchema: Schema[Ping.type] = Schema.CaseClass0(TypeId.parse("Ping"), () => Ping)
// pingSchema: Schema[Ping.type] = CaseClass0(Nominal(Chunk(),Chunk(),Ping), )
pingSchema.defaultValue
// res22: Either[String, Ping.type] = Right(value = Ping)
```

[Manual Schema Construction](manual-schema-construction.md) builds `CaseClass2` and `CaseClass3` schemas step by step. On both Scala 2 and Scala 3, `DeriveSchema.gen` describes a case class with more than 22 fields as a `GenericRecord` inside a `Transform`.

### Generic Records

`Schema.GenericRecord` describes a record as a `ListMap` from field name to value. It suits records whose fields are only known at runtime, such as rows read from a configuration file:

```scala
sealed case class GenericRecord(
  id: TypeId,
  fieldSet: FieldSet,
  override val annotations: Chunk[Any] = Chunk.empty
) extends Record[ListMap[String, _]]

object Schema {
  def record(id: TypeId, fields: Field[ListMap[String, _], _]*): Schema[ListMap[String, _]]
  def record(id: TypeId, fieldSet: FieldSet): Schema[ListMap[String, _]]
}
```

`Schema.record` builds a `GenericRecord`. A `FieldSet` is the list of its fields; `FieldSet.apply(fields*)` builds one, and so does the operator form `field1 :*: field2 :*: FieldSet.Empty`. The getter and setter of each field read and update the `ListMap`:

```scala
import scala.collection.immutable.ListMap
import zio.schema._

val nameField = Schema.Field[ListMap[String, _], String](
  name0 = "name",
  schema0 = Schema[String],
  get0 = _("name").asInstanceOf[String],
  set0 = (record, value) => record.updated[Any]("name", value)
)

val ageField = Schema.Field[ListMap[String, _], Int](
  name0 = "age",
  schema0 = Schema[Int],
  get0 = _("age").asInstanceOf[Int],
  set0 = (record, value) => record.updated[Any]("age", value)
)

val personSchema: Schema[ListMap[String, _]] = Schema.record(TypeId.parse("Person"), nameField, ageField)

val ann = ListMap("name" -> "Ann", "age" -> 30)
```

The generic record has a default value and converts to and from `DynamicValue` like any other record:

```scala
personSchema.defaultValue
// res24: Either[String, ListMap[String, _]] = Right(
//   value = ListMap("name" -> "", "age" -> 0)
// )
personSchema.fromDynamic(personSchema.toDynamic(ann))
// res25: Either[String, ListMap[String, _]] = Right(
//   value = ListMap("name" -> "Ann", "age" -> 30)
// )
```

## Enumerations

Other times, a type is one of several alternatives: a payment method, for example, is either a credit card or a wire transfer. Functional programming names such a type a **sum type**; Scala 2 writes it as a sealed trait, and Scala 3 also offers an `enum`. ZIO Schema calls it an enumeration: a value is exactly one of several cases. Sealed traits become `Enum1` to `Enum22`, and larger sums use `EnumN`.

### The Enum Trait

`Schema.Enum[Z]` is the common parent of every enumeration schema. It lists the cases and finds the case of a value:

```scala
sealed trait Enum[Z] extends Schema[Z] {
  def id: TypeId
  def cases: Chunk[Case[Z, _]]
  def caseOf(id: String): Option[Case[Z, _]]
  def caseOf(z: Z): Option[Case[Z, _]]

  val discriminatorName: Option[String]
  val noDiscriminator: Boolean
  lazy val nonTransientCases: Chunk[Case[Z, _]]
}
```

`Enum#caseOf` finds a case either by its name (the name after any `@caseName` annotation) or by testing a value. `Enum#discriminatorName` and `Enum#noDiscriminator` read the `@discriminatorName` and `@noDiscriminator` annotations, and `Enum#nonTransientCases` skips cases annotated with `@transientCase`. We look inside a derived sealed trait:

```scala
import zio.schema._

sealed trait Shape

object Shape {
  final case class Circle(radius: Double) extends Shape
  final case class Square(side: Double)   extends Shape

  implicit val circleSchema: Schema[Circle] = DeriveSchema.gen[Circle]
  implicit val squareSchema: Schema[Square] = DeriveSchema.gen[Square]
  implicit val schema: Schema[Shape]        = DeriveSchema.gen[Shape]
}

val shapeEnum: Option[Schema.Enum[Shape]] =
  Shape.schema match {
    case enumeration: Schema.Enum[Shape @unchecked] => Some(enumeration)
    case _                                          => None
  }

val circle: Shape = Shape.Circle(1.0)
```

The enum lists its cases and finds the case that matches a value:

```scala
shapeEnum.map(_.cases.map(_.id))
// res27: Option[zio.Chunk[String]] = Some(
//   value = IndexedSeq("Circle", "Square")
// )
shapeEnum.flatMap(_.caseOf(circle)).map(c => (c.id, c.deconstruct(circle)))
// res28: Option[(String, Any)] = Some(
//   value = ("Circle", Circle(radius = 1.0))
// )
shapeEnum.flatMap(_.caseOf("Square")).map(_.isCase(circle))
// res29: Option[Boolean] = Some(value = false)
```

### Cases

`Schema.Case[R, A]` describes one case of type `A` in a sum type `R`. It converts between the sum type and the case type:

```scala
sealed case class Case[R, A](
  id: String,
  schema: Schema[A],
  private val unsafeDeconstruct: R => A,
  construct: A => R,
  isCase: R => Boolean,
  annotations: Chunk[Any] = Chunk.empty
) {
  val caseName: String
  val caseNameAliases: Seq[String]
  val transient: Boolean

  def deconstruct(r: R): A
  def deconstructOption(r: R): Option[A]
}
```

`Case#isCase` tests whether a value belongs to the case. `Case#deconstruct` casts a value to the case type without checking, so call it only when `Case#isCase` is true; `Case#deconstructOption` checks first and returns `None` for other cases. `Case#construct` widens a case value back to the sum type. `Case#caseName` is the name from a `@caseName` annotation, or else `id`; `Case#caseNameAliases` collects `@caseNameAliases` annotations, and `Case#transient` is true for a `@transientCase` annotation.

### Enum1 to Enum22 and EnumN

`Enum1` to `Enum22` hold their cases in the fields `case1` to `caseN`. `EnumN` holds any number of cases in a `CaseSet`, and `Schema.enumeration` builds one:

```scala
sealed case class Enum1[A, Z](id: TypeId, case1: Case[Z, A], annotations: Chunk[Any] = Chunk.empty) extends Enum[Z]

sealed case class Enum2[A1, A2, Z](
  id: TypeId,
  case1: Case[Z, A1],
  case2: Case[Z, A2],
  annotations: Chunk[Any] = Chunk.empty
) extends Enum[Z]

// Enum3 ... Enum22 follow the same pattern

sealed case class EnumN[Z, C <: CaseSet.Aux[Z]](id: TypeId, caseSet: C, annotations: Chunk[Any] = Chunk.empty)
    extends Enum[Z]

object Schema {
  def enumeration[A, C <: CaseSet.Aux[A]](id: TypeId, caseSet: C, annotations: Chunk[Any] = Chunk.empty): Schema[A]
}
```

`CaseSet.caseOf` builds a one-case `CaseSet` from an implicit schema for the case and three functions: deconstruct, construct and the case test. `CaseSet#++` appends case sets, and `case1 :+: case2 :+: CaseSet.Empty[Z]()` builds one from `Case` values. We rebuild the `Shape` schema as an `EnumN`:

```scala
import zio.schema.CaseSet.caseOf

val manualShape: Schema[Shape] =
  Schema.enumeration[Shape, CaseSet.Aux[Shape]](
    TypeId.parse("Shape"),
    caseOf[Shape.Circle, Shape]("Circle")(_.asInstanceOf[Shape.Circle])(c => c)(_.isInstanceOf[Shape.Circle]) ++
      caseOf[Shape.Square, Shape]("Square")(_.asInstanceOf[Shape.Square])(s => s)(_.isInstanceOf[Shape.Square])
  )
```

The hand-written enum defaults to its first case and round-trips through `DynamicValue`:

```scala
manualShape.defaultValue
// res30: Either[String, Shape] = Right(value = Circle(radius = 0.0))
manualShape.fromDynamic(manualShape.toDynamic(circle))
// res31: Either[String, Shape] = Right(value = Circle(radius = 1.0))
```

## Collections

Collection schemas describe a container of elements. All of them extend `Schema.Collection`, which converts the container to and from a `Chunk` of elements:

```scala
sealed trait Collection[Col, Elem] extends Schema[Col] {
  def fromChunk: Chunk[Elem] => Col
  def toChunk: Col => Chunk[Elem]
  def empty: Col
}
```

For a map, the element type is the `(K, V)` pair. `Collection#empty` throws `IllegalArgumentException` on the non-empty collections, and so does `Collection#fromChunk` when given an empty chunk.

### Sequence

`Schema.Sequence` describes an ordered collection, such as `List`, `Vector` or `Chunk`. The `identity` value names the collection type; the built-in instances use a string such as `"List"`:

```scala
final case class Sequence[Col, Elem, I](
  elementSchema: Schema[Elem],
  fromChunk: Chunk[Elem] => Col,
  toChunk: Col => Chunk[Elem],
  override val annotations: Chunk[Any] = Chunk.empty,
  identity: I
) extends Collection[Col, Elem]
```

`Schema.list`, `Schema.vector` and `Schema.chunk` return a `Sequence`, and its default value is the empty collection. They are implicit, so `Schema[List[A]]` finds them for any `A` that has a schema:

```scala
import scala.collection.immutable.Queue
import zio.Chunk
import zio.schema._

val listSchema: Schema[List[Int]]     = Schema.list[Int]
val vectorSchema: Schema[Vector[Int]] = Schema.vector[Int]
val chunkSchema: Schema[Chunk[Int]]   = Schema.chunk[Int]
```

For a collection without a built-in schema, we supply the two conversions:

```scala
val queueSchema: Schema[Queue[Int]] =
  Schema.Sequence[Queue[Int], Int, String](
    elementSchema = Schema[Int],
    fromChunk = chunk => Queue(chunk: _*),
    toChunk = queue => Chunk.fromIterable(queue),
    identity = "Queue"
  )
```

The new schema converts a queue to `DynamicValue` and back:

```scala
queueSchema.defaultValue
// res33: Either[String, Queue[Int]] = Right(value = Queue())
queueSchema.fromDynamic(queueSchema.toDynamic(Queue(1, 2, 3)))
// res34: Either[String, Queue[Int]] = Right(value = Queue(1, 2, 3))
```

### NonEmptySequence

`Schema.NonEmptySequence` describes an ordered collection that has at least one element, such as `zio.NonEmptyChunk` or `zio.prelude.NonEmptySet`. Its `NonEmptySequence#fromChunkOption` function returns `None` for an empty chunk:

```scala
final case class NonEmptySequence[Col, Elm, I](
  elementSchema: Schema[Elm],
  fromChunkOption: Chunk[Elm] => Option[Col],
  override val toChunk: Col => Chunk[Elm],
  override val annotations: Chunk[Any] = Chunk.empty,
  identity: I
) extends Collection[Col, Elm]
```

`Schema.nonEmptyChunk` and `Schema.nonEmptySet` return a `NonEmptySequence`. A non-empty collection has no default value:

```scala
import zio.NonEmptyChunk
import zio.schema._

val nonEmptyInts: Schema[NonEmptyChunk[Int]] = Schema[NonEmptyChunk[Int]]
```

Its `Schema#defaultValue` is a `Left`, and converting a value through `DynamicValue` does not give the value back:

```scala
nonEmptyInts.defaultValue
// res36: Either[String, NonEmptyChunk[Int]] = Left(
//   value = "NonEmptyChunk cannot be empty"
// )
nonEmptyInts.fromDynamic(nonEmptyInts.toDynamic(NonEmptyChunk(1, 2))).isLeft
// res37: Boolean = true
```

:::caution[No `DynamicValue` Round Trip for Non-Empty Collections and `Fallback`]
`fromDynamic(toDynamic(x))` fails for `NonEmptySequence`, `NonEmptyMap` and `Fallback` schemas: `Schema#fromDynamic` returns a `Left` with a cast error. Operations built on this round trip, such as `Schema#migrate` and `Schema#coerce`, fail for records that contain these types. See [DynamicValue](operations/dynamic-data-representation.md).
:::

### Map

`Schema.Map` describes an immutable `Map`. `Schema.map` returns one, and its default value is the empty map:

```scala
final case class Map[K, V](
  keySchema: Schema[K],
  valueSchema: Schema[V],
  override val annotations: Chunk[Any] = Chunk.empty
) extends Collection[scala.collection.immutable.Map[K, V], (K, V)]
```

As a `Collection`, a map schema converts a map to a chunk of pairs:

```scala
val scores = Schema.Map(Schema[String], Schema[Int])
// scores: Schema.Map[String, Int] = Map(
//   keySchema = Primitive(standardType = string, annotations = IndexedSeq()),
//   valueSchema = Primitive(standardType = int, annotations = IndexedSeq()),
//   annotations = IndexedSeq()
// )
scores.toChunk(Map("ann" -> 3, "bob" -> 5))
// res38: zio.Chunk[(String, Int)] = IndexedSeq(("ann", 3), ("bob", 5))
scores.defaultValue
// res39: Either[String, Map[String, Int]] = Right(value = Map())
```

### NonEmptyMap

`Schema.NonEmptyMap` describes a `zio.prelude.NonEmptyMap`. `Schema.nonEmptyMap` returns one. Besides the `Collection` members, it has `NonEmptyMap#fromChunkOption` and `NonEmptyMap#fromMap`; `NonEmptyMap#fromMap` throws `IllegalArgumentException` for an empty map:

```scala
final case class NonEmptyMap[K, V](
  keySchema: Schema[K],
  valueSchema: Schema[V],
  override val annotations: Chunk[Any] = Chunk.empty
) extends Collection[prelude.NonEmptyMap[K, V], (K, V)] {
  def fromChunkOption(chunk: Chunk[(K, V)]): Option[prelude.NonEmptyMap[K, V]]
  def fromMap(map: scala.collection.immutable.Map[K, V]): prelude.NonEmptyMap[K, V]
}
```

A non-empty map has no default value, and an empty chunk gives no map:

```scala
val nonEmptyScores = Schema.NonEmptyMap(Schema[String], Schema[Int])
// nonEmptyScores: Schema.NonEmptyMap[String, Int] = NonEmptyMap(
//   keySchema = Primitive(standardType = string, annotations = IndexedSeq()),
//   valueSchema = Primitive(standardType = int, annotations = IndexedSeq()),
//   annotations = IndexedSeq()
// )
nonEmptyScores.defaultValue
// res40: Either[String, zio.prelude.NonEmptyMap[String, Int]] = Left(
//   value = "NonEmptyMap has no default value"
// )
nonEmptyScores.fromChunkOption(zio.Chunk.empty)
// res41: Option[zio.prelude.NonEmptyMap[String, Int]] = None
nonEmptyScores.fromChunkOption(zio.Chunk("ann" -> 3))
// res42: Option[zio.prelude.NonEmptyMap[String, Int]] = Some(
//   value = NonEmptyMap(ann -> 3)
// )
```

### Set

`Schema.Set` describes an immutable `Set`. `Schema.set` returns one, and its default value is the empty set:

```scala
final case class Set[A](elementSchema: Schema[A], override val annotations: Chunk[Any] = Chunk.empty)
    extends Collection[scala.collection.immutable.Set[A], A]
```

The set schema keeps the element schema and converts sets to chunks:

```scala
val idSet = Schema.Set(Schema[Int])
// idSet: Schema.Set[Int] = Set(
//   elementSchema = Primitive(standardType = int, annotations = IndexedSeq()),
//   annotations = IndexedSeq()
// )
idSet.toChunk(Set(1, 2))
// res43: zio.Chunk[Int] = IndexedSeq(1, 2)
idSet.defaultValue
// res44: Either[String, Set[Int]] = Right(value = Set())
```

## Optionals, Tuples, and Alternatives

These schemas combine one or two other schemas: an optional value, a pair, or one of two alternatives.

### Optional

`Schema.Optional` describes `Option[A]`. `Schema.option`, `Schema#optional` and `Schema#?` return one. Its default value is `None`, and `Optional#toEnum` views it as an enum with the cases `Some` and `None`:

```scala
final case class Optional[A](schema: Schema[A], annotations: Chunk[Any] = Chunk.empty) extends Schema[Option[A]] {
  lazy val toEnum: Enum2[Some[A], None.type, Option[A]]
}
```

The optional schema wraps the schema of its content:

```scala
import zio.schema._

val maybeAge = Schema.Optional(Schema[Int])
// maybeAge: Schema.Optional[Int] = Optional(
//   schema = Primitive(standardType = int, annotations = IndexedSeq()),
//   annotations = IndexedSeq()
// )
maybeAge.defaultValue
// res46: Either[String, Option[Int]] = Right(value = None)
maybeAge.toEnum.cases.map(_.id)
// res47: zio.Chunk[String] = IndexedSeq("Some", "None")
```

### Tuple2

`Schema.Tuple2` describes a pair. `Schema.tuple2`, `Schema#zip` and `Schema#<*>` return one; the implicit instances for larger tuples nest `Tuple2` schemas inside a `Transform`. `Tuple2#toRecord` views the pair as a record with the fields `_1` and `_2`:

```scala
final case class Tuple2[A, B](left: Schema[A], right: Schema[B], annotations: Chunk[Any] = Chunk.empty)
    extends Schema[(A, B)] {
  val toRecord: CaseClass2[A, B, (A, B)]
  def extract1(value: (A, B)): A
  def extract2(value: (A, B)): B
}
```

The default value of a pair combines the defaults of both sides:

```scala
val namedAge = Schema.Tuple2(Schema[String], Schema[Int])
// namedAge: Schema.Tuple2[String, Int] = Tuple2(
//   left = Primitive(standardType = string, annotations = IndexedSeq()),
//   right = Primitive(standardType = int, annotations = IndexedSeq()),
//   annotations = IndexedSeq()
// )
namedAge.defaultValue
// res48: Either[String, (String, Int)] = Right(value = ("", 0))
namedAge.toRecord.fields.map(_.name)
// res49: zio.Chunk[(Schema.Field[(String, Int), _])#Field] = IndexedSeq(
//   "_1",
//   "_2"
// )
```

The implicit instances `Schema.tuple2` to `Schema.tuple22` build on `Schema#zip`: `Schema.tuple2` is `c1.zip(c2)`, and `Schema.tuple3` zips three schemas and flattens the nested pair with `Schema#transform`:

```scala
object Schema {
  implicit def tuple3[A, B, C](implicit c1: Schema[A], c2: Schema[B], c3: Schema[C]): Schema[(A, B, C)] =
    c1.zip(c2).zip(c3).transform({ case ((a, b), c) => (a, b, c) }, { case (a, b, c) => ((a, b), c) })

  // tuple4 ... tuple22
}
```

So `Schema[(A1, ..., An)]` summons a schema for any tuple whose elements have schemas:

```scala
Schema[(String, Int, Boolean)].defaultValue
// res50: Either[String, (String, Int, Boolean)] = Right(
//   value = ("", 0, false)
// )
```

### Either

`Schema.Either` describes `scala.util.Either[A, B]`. `Schema.either`, `Schema#orElseEither` and `Schema#<+>` return one. `Either#toEnum` views it as an enum with the cases `Right` and `Left`:

```scala
final case class Either[A, B](left: Schema[A], right: Schema[B], annotations: Chunk[Any] = Chunk.empty)
    extends Schema[scala.util.Either[A, B]] {
  val leftSchema: Schema[Left[A, Nothing]]
  val rightSchema: Schema[Right[Nothing, B]]
  val toEnum: Enum2[Right[Nothing, B], Left[A, Nothing], scala.util.Either[A, B]]
}
```

The default value is `Left` with the default of the left schema, or `Right` when the left schema has no default:

```scala
val idOrName = Schema.Either(Schema[Int], Schema[String])
// idOrName: Schema.Either[Int, String] = Either(
//   left = Primitive(standardType = int, annotations = IndexedSeq()),
//   right = Primitive(standardType = string, annotations = IndexedSeq()),
//   annotations = IndexedSeq()
// )
idOrName.defaultValue
// res51: Either[String, Either[Int, String]] = Right(value = Left(value = 0))
idOrName.toEnum.cases.map(_.id)
// res52: zio.Chunk[String] = IndexedSeq("Right", "Left")
```

### Fallback

`zio.schema.Fallback[A, B]` is an `Either` that can also hold both values. `Fallback.Left` and `Fallback.Right` hold one value, and `Fallback.Both` holds both, with the left value as the preferred one:

```scala
sealed trait Fallback[+A, +B] {
  def toEither: Either[A, B]
  def simplify: Fallback[A, B]
  def fold[C](fa: A => C, fb: B => C): C
  def map[C](f: A => C): Fallback[C, B]
  def mapLeft[C](f: A => C): Fallback[C, B]
  def mapRight[C](f: B => C): Fallback[A, C]
  def swap: Fallback[B, A]
}

object Fallback {
  final case class Left[+A, +B](left: A)           extends Fallback[A, B]
  final case class Right[+A, +B](right: B)         extends Fallback[A, B]
  final case class Both[+A, +B](left: A, right: B) extends Fallback[A, B]

  def fromEither[A, B](either: Either[A, B]): Fallback[A, B]
}
```

`Fallback#toEither` and `Fallback#fold` use the left value of a `Both`, `Fallback#simplify` drops its right value, and `Fallback#map` is `Fallback#mapLeft`. The operations work as follows on a `Both`:

```scala
val both: Fallback[Int, String] = Fallback.Both(1, "one")
// both: Fallback[Int, String] = Both(left = 1, right = "one")
both.toEither
// res53: Either[Int, String] = Left(value = 1)
both.simplify
// res54: Fallback[Int, String] = Left(left = 1)
both.swap
// res55: Fallback[String, Int] = Both(left = "one", right = 1)
both.fold(i => s"number $i", s => s"text $s")
// res56: String = "number 1"
both.mapRight(_.length)
// res57: Fallback[Int, Int] = Both(left = 1, right = 3)
Fallback.fromEither(Right("x"))
// res58: Fallback[Nothing, String] = Right(right = "x")
```

`Schema.Fallback` describes a `Fallback` value, and `Schema.fallback` returns one. The JSON codec, for example, encodes `Both` as a two-element array. The `fullDecode` flag controls decoding of such a `Both`: when it is `true`, the decoder returns `Fallback.Both`; when it is `false` (the default), it returns `Fallback.Left` when the left value decodes, and `Fallback.Right` otherwise. `Fallback#toEither` on the schema returns the matching `Schema.Either`:

```scala
final case class Fallback[A, B](
  left: Schema[A],
  right: Schema[B],
  fullDecode: Boolean = false,
  annotations: Chunk[Any] = Chunk.empty
) extends Schema[zio.schema.Fallback[A, B]] {
  val toEither: Either[A, B]
}
```

The default value is `Both` when both sides have a default:

```scala
Schema.Fallback(Schema[Int], Schema[String]).defaultValue
// res59: Either[String, Fallback[Int, String]] = Right(
//   value = Both(left = 0, right = "")
// )
Schema.Fallback(Schema[Int], Schema[String], fullDecode = true).fullDecode
// res60: Boolean = true
```

On Scala 3, `zio.schema` adds the extensions `Schema#orElse`, which builds a `Schema.Fallback` and merges it into a schema of the common supertype, and `merge` on `Schema.Fallback` and on `Fallback` values.

## Transform

`Schema.Transform` describes a type `B` through a schema for another type `A`, with a function in each direction that can fail. `Schema#transform` and `Schema#transformOrFail` return one; see [Transforming Schemas](operations/transforming-schemas.md):

```scala
final case class Transform[A, B, I](
  schema: Schema[A],
  f: A => scala.util.Either[String, B],
  g: B => scala.util.Either[String, A],
  annotations: Chunk[Any],
  identity: I
) extends Schema[B]
```

`Schema#transform` and `Schema#transformOrFail` set `identity` to the source location of the call. `Schema.strictEquality` compares this identity, so two transforms written at different places are not strictly equal. The default value of a `Transform` is the default of `Transform#schema` mapped through `Transform#f`, so a failing `Transform#f` gives no default. Many built-in schemas are transforms, including `Schema[BigDecimal]`, `Schema[java.net.URI]` and the larger tuples. `Schema.singleton(instance)` builds a transform from `Schema[Unit]` that always returns `instance`. We wrap a string in a type that rejects values without `@`:

```scala
import zio.schema._

final case class Email(value: String)

val emailSchema: Schema[Email] =
  Schema[String].transformOrFail(
    (s: String) => if (s.contains("@")) Right(Email(s)) else Left(s"not an email: '$s'"),
    (email: Email) => Right(email.value)
  )
```

The empty default string fails `f`, while a valid string converts:

```scala
emailSchema.defaultValue
// res62: Either[String, Email] = Left(value = "not an email: ''")
emailSchema.fromDynamic(Schema[String].toDynamic("jane@example.com"))
// res63: Either[String, Email] = Right(
//   value = Email(value = "jane@example.com")
// )
```

## Lazy

`Schema.Lazy` delays building a schema until its first access. A recursive type needs it, because its schema refers to itself. `Schema.defer` builds a `Lazy`, and `Schema.force` removes any `Lazy` layers from the outside of a schema:

```scala
final case class Lazy[A](private val schema0: () => Schema[A]) extends Schema[A] {
  lazy val schema: Schema[A]
}

object Schema {
  def defer[A](schema: => Schema[A]): Schema[A]
  def force[A](schema: Schema[A]): Schema[A]
}
```

`DeriveSchema.gen` handles recursive types on its own; [Recursive Schemas](how-to/recursive-schemas.md) covers both approaches. When we write the schema of a recursive type by hand, we declare it as a `lazy val` and wrap the self-reference in `Schema.defer`:

```scala
import zio.schema._

final case class Category(name: String, subcategories: List[Category])

object Category {
  implicit lazy val schema: Schema[Category] =
    Schema.CaseClass2[String, List[Category], Category](
      id0 = TypeId.parse("Category"),
      field01 = Schema.Field[Category, String](
        name0 = "name",
        schema0 = Schema[String],
        get0 = _.name,
        set0 = (category, name) => category.copy(name = name)
      ),
      field02 = Schema.Field[Category, List[Category]](
        name0 = "subcategories",
        schema0 = Schema.defer(Schema.list(schema)),
        get0 = _.subcategories,
        set0 = (category, subcategories) => category.copy(subcategories = subcategories)
      ),
      construct0 = (name, subcategories) => Category(name, subcategories)
    )
}

val books = Category("books", List(Category("fiction", Nil), Category("poetry", Nil)))
```

The recursive schema converts a nested value and gives it back unchanged:

```scala
Category.schema.fromDynamic(Category.schema.toDynamic(books)) == Right(books)
// res65: Boolean = true
Schema.force(Schema.defer(Schema[Int]))
// res66: Schema[Int] = Primitive(
//   standardType = int,
//   annotations = IndexedSeq()
// )
```

`Lazy#annotations` and `Lazy#annotate` work on the inner schema, which they build on every call.

## Dynamic

`Schema.Dynamic` describes a `DynamicValue`, a value whose structure is only known at runtime. `Schema[DynamicValue]` is a `Dynamic`, and its default value is `DynamicValue.NoneValue`. `Schema#fromDynamic` with it returns the `DynamicValue` unchanged; see [DynamicValue](operations/dynamic-data-representation.md):

```scala
final case class Dynamic(override val annotations: Chunk[Any] = Chunk.empty) extends Schema[DynamicValue] {
  def id: TypeId
}
```

The dynamic schema accepts any `DynamicValue`:

```scala
import zio.schema._

val anything = Schema[DynamicValue]
// anything: Schema[DynamicValue] = Dynamic(annotations = IndexedSeq())
anything.defaultValue
// res68: Either[String, DynamicValue] = Right(value = NoneValue)
anything.fromDynamic(DynamicValue.Primitive(42, StandardType.IntType))
// res69: Either[String, DynamicValue] = Right(
//   value = Primitive(value = 42, standardType = int)
// )
```

## Fail

`Schema.Fail` stands for a type that has no schema. `Schema.fail` returns one. Its default value is a `Left` with the message, and `Schema.validate` adds no checks for it:

```scala
final case class Fail[A](message: String, annotations: Chunk[Any] = Chunk.empty) extends Schema[A]
```

A failing schema reports its message as the default:

```scala
Schema.fail[java.io.File]("files are not serializable").defaultValue
// res70: Either[String, java.io.File] = Left(
//   value = "files are not serializable"
// )
```

## Companion Constructors

The `Schema` companion object has constructors and implicit instances that return these data types. The following table lists which data type each one builds:

| Constructor                                                             | Data type                        |
| ----------------------------------------------------------------------- | -------------------------------- |
| `Schema.apply[A]` (summons the implicit instance)                       | any                              |
| `Schema.primitive`                                                      | `Primitive`                      |
| `Schema.record`                                                         | `GenericRecord`                  |
| `Schema.enumeration`                                                    | `EnumN`                          |
| `Schema.list`, `Schema.vector`, `Schema.chunk`                          | `Sequence`                       |
| `Schema.nonEmptyChunk`, `Schema.nonEmptySet`                            | `NonEmptySequence`               |
| `Schema.map`                                                            | `Map`                            |
| `Schema.nonEmptyMap`                                                    | `NonEmptyMap`                    |
| `Schema.set`                                                            | `Set`                            |
| `Schema.option`                                                         | `Optional`                       |
| `Schema.tuple2`                                                         | `Tuple2`                         |
| `Schema.tuple3` to `Schema.tuple22`                                     | `Transform` over nested `Tuple2` |
| `Schema.either`                                                         | `Either`                         |
| `Schema.fallback`                                                       | `Fallback`                       |
| `Schema.defer`                                                          | `Lazy`                           |
| `Schema.dynamicValue`                                                   | `Dynamic`                        |
| `Schema.fail`                                                           | `Fail`                           |
| `Schema.singleton`, `Schema.first`, `Schema.second`                     | `Transform`                      |
| `Schema.bigDecimal`, `Schema.bigInt`, `Schema.uri`, `Schema.chronoUnit` | `Transform` over a primitive     |

`DeriveSchema.gen` builds `CaseClass0` to `CaseClass22` for case classes, a `Transform` over a `GenericRecord` for case classes with more than 22 fields, and `Enum1` to `Enum22` or `EnumN` for sealed traits.

## Integration

Schema data types are the input of every generic operation in ZIO Schema. [Schema Methods](reference/schema-methods.md) covers the methods shared by all of them. [Validation](operations/validating-types.md) covers the rules stored in `Schema.Field#validation`, and [Schema Annotations](schema-annotations.md) covers the annotations that the `Field`, `Case`, `Record` and `Enum` members read. [DynamicValue](operations/dynamic-data-representation.md) is the runtime value that `Schema#toDynamic` produces from any of these schemas.
