# Extending Quill

> Infix is a very flexible mechanism to use non-supported features without having to use plain queries in the target language. It allows the insertion of arbitrary strings within quotations.

## Infix

Infix is a very flexible mechanism to use non-supported features without having to use plain queries in the target language. It allows the insertion of arbitrary strings within quotations.

For instance, quill doesn't support the `FOR UPDATE` SQL feature. It can still be used through infix and implicit classes:

```scala
implicit final class ForUpdate[T](private val q: Query[T]) extends AnyVal {
  def forUpdate = quote(sql"$q FOR UPDATE".as[Query[T]])
}

val a = quote {
  query[Person].filter(p => p.age < 18).forUpdate
}

ctx.run(a)
// SELECT p.name, p.age FROM person p WHERE p.age < 18 FOR UPDATE
```

The `forUpdate` quotation can be reused for multiple queries.

Queries that contain `infix` will generally not be flattened since it is not assumed that the contents
of the infix are a pure function.
> Since SQL is typically less performant when there are many nested queries,
be careful with the use of `infix` in queries that have multiple `map`+`filter` clauses.

```scala
final case class Data(id: Int)
final case class DataAndRandom(id: Int, value: Int)

// This should be alright:
val q = quote {
  query[Data].map(e => DataAndRandom(e.id, sql"RAND()".as[Int])).filter(r => r.value <= 10)
}
run(q)
// SELECT e.id, e.value FROM (SELECT RAND() AS value, e.id AS id FROM Data e) AS e WHERE e.value <= 10

// This might not be:
val q = quote {
  query[Data]
    .map(e => DataAndRandom(e.id, sql"SOME_UDF(${e.id})".as[Int]))
    .filter(r => r.value <= 10)
    .map(e => DataAndRandom(e.id, sql"SOME_OTHER_UDF(${e.value})".as[Int]))
    .filter(r => r.value <= 100)
}
// Produces too many layers of nesting!
run(q)
// SELECT e.id, e.value FROM (
//   SELECT SOME_OTHER_UDF(e.value) AS value, e.id AS id FROM (
//     SELECT SOME_UDF(e.id) AS value, e.id AS id FROM Data e
//   ) AS e WHERE e.value <= 10
// ) AS e WHERE e.value <= 100
```

If you are sure that the content of your infix is a pure function, you can use the `pure` method
in order to indicate to Quill that the infix clause can be copied in the query. This gives Quill much
more leeway to flatten your query, possibly improving performance.

```scala
val q = quote {
  query[Data]
    .map(e => DataAndRandom(e.id, sql"SOME_UDF(${e.id})".pure.as[Int]))
    .filter(r => r.value <= 10)
    .map(e => DataAndRandom(e.id, sql"SOME_OTHER_UDF(${e.value})".pure.as[Int]))
    .filter(r => r.value <= 100)
}
// Copying SOME_UDF and SOME_OTHER_UDF allows the query to be completely flattened.
run(q)
// SELECT e.id, SOME_OTHER_UDF(SOME_UDF(e.id)) FROM Data e
// WHERE SOME_UDF(e.id) <= 10 AND SOME_OTHER_UDF(SOME_UDF(e.id)) <= 100
```

### Infixes With Conditions

#### Summary
Use `sql"...".asCondition` to express an infix that represents a conditional expression.

#### Explanation

When synthesizing queries for databases which do not have proper boolean-type support (e.g. SQL Server,
Oracle etc...) boolean infix clauses inside projections must become values.
Typically this requires a `CASE WHERE ... END`.

Take the following example:
```scala
final case class Node(name: String, isUp: Boolean, uptime:Long)
final case class Status(name: String, allowed: Boolean)
val allowedStatus:Boolean = getState

quote {
  query[Node].map(n => Status(n.name, n.isUp == lift(allowedStatus)))
}
run(q)
// This is invalid in most databases:
//   SELECT n.name, n.isUp = ?, uptime FROM Node n
// It will be converted to this:
//   SELECT n.name, CASE WHEN (n.isUp = ?) THEN 1 ELSE 0, uptime FROM Node n
```
However, in certain cases, infix clauses that express conditionals should actually represent
boolean expressions for example:
```scala
final case class Node(name: String, isUp: Boolean)
val maxUptime:Boolean = getState

quote {
  query[Node].filter(n => sql"${n.uptime} > ${lift(maxUptime)}".as[Boolean])
}
run(q)
// Should be this:
//  SELECT n.name, n.isUp, n.uptime WHERE n.uptime > ?
// However since sql"...".as[Boolean] is treated as a Boolean Value (as opposed to an expression) it will be converted to this:
//  SELECT n.name, n.isUp, n.uptime WHERE 1 == n.uptime > ?
```

In order to avoid this problem, use sql"...".asCondition so that Quill understands that the boolean is an expression:
```scala
quote {
  query[Node].filter(n => sql"${n.uptime} > ${lift(maxUptime)}".asCondition)
}
run(q) // SELECT n.name, n.isUp, n.uptime WHERE n.uptime > ?
```

### Dynamic infix

Infix supports runtime string values through the `#$` prefix. Example:

```scala
def test(functionName: String) =
  ctx.run(query[Person].map(p => sql"#$functionName(${p.name})".as[Int]))
```

### Implicit Extensions

You can use implicit extensions in quill in several ways.
> NOTE. In ProtoQuill extensions must be written using the Scala 3 `extension` syntax and implicit class extensions are not supported. Please see [Extensions in ProtoQuill/Scala3](#extensions-in-protoquillscala3) below for more info.

##### Standard quoted extension:
```scala
implicit final class Ext(private val q: Query[Person]) extends AnyVal {
  def olderThan(age: Int) = quote {
    query[Person].filter(p => p.age > lift(age))
  }
}
run(query[Person].olderThan(44))
// SELECT p.name, p.age FROM Person p WHERE p.age > ?
```

##### Higher-order quoted extension:
```scala
implicit final class Ext(private val q: Query[Person]) extends AnyVal {
  def olderThan = quote {
    (age: Int) =>
      query[Person].filter(p => p.age > lift(age))
  }
}
run(query[Person].olderThan(44))
// SELECT p.name, p.age FROM Person p WHERE p.age > 44

run(query[Person].olderThan(lift(44)))
// SELECT p.name, p.age FROM Person p WHERE p.age > ?
```
The advantage of this approach is that you can choose to either lift or use a constant.

##### Scalar quoted extension:

Just as `Query` can be extended, scalar values can be similarly extended.
```scala
implicit final class Ext(private val i: Int) extends AnyVal {
  def between = quote {
    (a: Int, b:Int) =>
      i > a && i < b
  }
}
run(query[Person].filter(p => p.age.between(33, 44)))
// SELECT p.name, p.age FROM Person p WHERE p.age > 33 AND p.age < 44
```

##### Extensions in ProtoQuill/Scala3:
In ProtoQuill, the implicit class pattern for extensions is not supported. Please switch to using Scala 3 extension methods combined with inline definitions to achieve the same functionality.

```scala 3
extension (q: Query[Person]) {
  inline def olderThan(inline age: Int) = quote {
    query[Person].filter(p => p.age > lift(age))
  }
}
run(query[Person].olderThan(44))
// SELECT p.name, p.age FROM Person p WHERE p.age > ?
```

### Raw SQL queries

You can also use infix to port raw SQL queries to Quill and map it to regular Scala tuples.

```scala
val rawQuery = quote {
  (id: Int) => sql"""SELECT id, name FROM my_entity WHERE id = $id""".as[Query[(Int, String)]]
}
ctx.run(rawQuery(1))
//SELECT x._1, x._2 FROM (SELECT id AS "_1", name AS "_2" FROM my_entity WHERE id = 1) x
```

Note that in this case the result query is nested.
It's required since Quill is not aware of a query tree and cannot safely unnest it.
This is different from the example above because infix starts with the query `sql"$q...` where its tree is already compiled

### Database functions

A custom database function can also be used through infix:

```scala
val myFunction = quote {
  (i: Int) => sql"MY_FUNCTION($i)".as[Int]
}

val q = quote {
  query[Person].map(p => myFunction(p.age))
}

ctx.run(q)
// SELECT MY_FUNCTION(p.age) FROM Person p
```

### Comparison operators

The easiest way to use comparison operators with dates is to import them from the `extras` module.
```scala
final case class Person(name: String, bornOn: java.util.Date)

val ctx = new SqlMirrorContext(PostgresDialect, Literal)

run(query[Person].filter(p => p.bornOn > lift(myDate)))
```
> Note that in ProtoQuill you should import `io.getquill.extras._` since they are now global.

#### Using Ordered

You can also define an implicit-class that converts your Date/Numeric type to a `Ordered[YourType]`
which will also give it `>`, `<` etc... comparison operators.

```scala
implicit final class LocalDateTimeOps(val value: MyCustomNumber) extends Ordered[MyCustomNumber] {
  def compare(that: MyCustomNumber): Int = value.compareTo(that)
}
```
Note that Quill will not actually use this `compare` method, that is strictly for your own
data needs. You can technically define it as `def compare(that: MyCustomNumber): Int = ???`
because Quill will never actually invoke this function. It uses the `LocalDateTimeOps`
merely as a sort of marker to know that the `>`, `<` operators etc... can be transpiled to SQL.

> Note that although in ProtoQuill implicit-class based approaches are generally not supported,
> this particular pattern will work as well.

#### Using Implicit Classes

Finally, can implement comparison operators (or any other kinds of operators) by defining
implicit conversion and using infix.

```scala
implicit final class DateQuotes(private val left: MyCustomDate) extends AnyVal {
  def >(right: MyCustomDate) = quote(sql"$left > $right".as[Boolean])
  def <(right: MyCustomDate) = quote(sql"$left < $right".as[Boolean])
}
```

### batch with infix

```scala
implicit final class OnDuplicateKeyIgnore[T](private val q: Insert[T]) extends AnyVal {
  def ignoreDuplicate = quote(sql"$q ON DUPLICATE KEY UPDATE id=id".as[Insert[T]])
}

ctx.run(
  liftQuery(List(
    Person(1, "Test1", 30),
    Person(2, "Test2", 31)
  )).foreach(row => query[Person].insertValue(row).ignoreDuplicate)
)
```

## Custom encoding

Quill uses `Encoder`s to encode query inputs and `Decoder`s to read values returned by queries. The library provides a few built-in encodings and two mechanisms to define custom encodings: mapped encoding and raw encoding.

### Custom Encoders

If the correspondent database type is already supported, use `encoder.contramap` and `decoder.map` to define encoders/decoders for
the custom type. In this example, `String` is already supported by Quill and the `UUID` encoding from/to `String` is defined through a custom encoding:
```scala

implicit val encodeUUID: Encoder[UUID] = 
  implicitly[Encoder[String]].contramap((id: UUID) => id.toString)
implicit val decodeUUID: Decoder[UUID] =
  implicitly[Decoder[String]].map((idStr: String) => UUID.fromString(idStr))
```

You can also MappedEncoding to define instances that convert to/from the target type.

```scala

// import io.getquill.MappedEncoding - (or import MappedEncoding directly)

implicit val encodeUUID = MappedEncoding[UUID, String](_.toString)
implicit val decodeUUID = MappedEncoding[String, UUID](UUID.fromString(_))
```
Note that can it be also used to provide mapping for element types of collection (SQL Arrays or Cassandra Collections).

### Raw Encoding

If the database type is not supported by Quill, it is possible to provide "raw" encoders and decoders:

```scala
trait UUIDEncodingExample {
  val jdbcContext: PostgresJdbcContext[Literal] // your context should go here

  implicit val uuidDecoder: Decoder[UUID] =
    decoder((index, row) =>
      UUID.fromString(row.getObject(index).toString)) // database-specific implementation

  implicit val uuidEncoder: Encoder[UUID] =
    encoder(java.sql.Types.OTHER, (index, value, row) =>
        row.setObject(index, value, java.sql.Types.OTHER)) // database-specific implementation

  // Only for postgres
  implicit def arrayUUIDEncoder[Col <: Seq[UUID]]: Encoder[Col] = arrayRawEncoder[UUID, Col]("uuid")
  implicit def arrayUUIDDecoder[Col <: Seq[UUID]](implicit bf: CBF[UUID, Col]): Decoder[Col] =
    arrayRawDecoder[UUID, Col]
}
```

## `AnyVal`

Quill automatically encodes `AnyVal`s (value classes):

```scala
final case class UserId(value: Int) extends AnyVal
final case class User(id: UserId, name: String)

val q = quote {
  for {
    u <- query[User] if u.id == lift(UserId(1))
  } yield u
}

ctx.run(q)
// SELECT u.id, u.name FROM User u WHERE (u.id = 1)
```

## Table/Column Customizations

The meta DSL allows the user to customize how Quill handles column/table naming and behavior.

### Changing Table and Column Names

You can change how Quill queries handle table and column names for a record case class.

```scala
def example = {
  implicit val personSchemaMeta = schemaMeta[Person]("people", _.id -> "person_id")

  ctx.run(query[Person])
  // SELECT x.person_id, x.name, x.age FROM people x
}
```

By default, quill expands `query[Person]` to `querySchema[Person]("Person")`. It's possible to customize this behavior using an implicit instance of `SchemaMeta`:

### Excluding Columns from Insert

You can exclude columns (e.g. Auto-Generated ones) from insertion in `q.insertValue(...)` by using an `InsertMeta`.

```scala
implicit val personInsertMeta = insertMeta[Person](_.id)

ctx.run(query[Person].insertValue(lift(Person(-1, "John", 22))))
// INSERT INTO Person (name,age) VALUES (?, ?)
```

Note that the parameter of `insertMeta` is called `exclude`, but it isn't possible to use named parameters for macro invocations.

### Excluding Columns from Update

You can exclude columns (e.g. Auto-Generated ones) from updates in `q.insertValue(...)` by using an `UpdateMeta`.

```scala
implicit val personUpdateMeta = updateMeta[Person](_.id)

ctx.run(query[Person].filter(_.id == 1).updateValue(lift(Person(1, "John", 22))))
// UPDATE Person SET name = ?, age = ? WHERE id = 1
```

Note that the parameter of `updateMeta` is called `exclude`, but it isn't possible to use named parameters for macro invocations.

## Mapped Records

The QueryMeta customizes the expansion of query types and extraction of the final value. For instance, it's possible to use this feature to normalize values before reading them from the database:

```scala
implicit val personQueryMeta =
  queryMeta(
    (q: Query[Person]) =>
      q.map(p => (p.id, sql"CONVERT(${p.name} USING utf8)".as[String], p.age))
  ) {
    case (id, name, age) =>
      Person(id, name, age)
  }
```

The query meta definition is open and allows the user to even join values from other tables before reading the final value. This kind of usage is not encouraged.
