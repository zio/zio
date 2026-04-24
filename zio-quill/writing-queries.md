# Writing Queries

> The QDSL allows the user to write plain Scala code, leveraging Scala's syntax and type system. Quotations are created using the `quote` method and can contain any excerpt of code that uses supported operations. To create quotations, first create a context instance. Please see the [context](contexts.md) section for more details on the different context available.

## Introduction

The QDSL allows the user to write plain Scala code, leveraging Scala's syntax and type system. Quotations are created using the `quote` method and can contain any excerpt of code that uses supported operations. To create quotations, first create a context instance. Please see the [context](contexts.md) section for more details on the different context available.

For this documentation, a special type of context that acts as a [mirror](contexts.md#mirror-context) is used:

```scala

val ctx = new SqlMirrorContext(MirrorSqlDialect, Literal)
```

The context instance provides all the types, methods, and encoders/decoders needed for quotations:

```scala

```

A quotation can be a simple value:

```scala
val pi = quote(3.14159)
```

And be used within another quotation:

```scala
final case class Circle(radius: Float)

val areas = quote {
  query[Circle].map(c => pi * c.radius * c.radius)
}
```

Quotations can also contain high-order functions and inline values:

```scala
val area = quote {
  (c: Circle) => {
    val r2 = c.radius * c.radius
    pi * r2
  }
}
```

```scala
val areas = quote {
  query[Circle].map(c => area(c))
}
```

Quill's normalization engine applies reduction steps before translating the quotation to the target language. The correspondent normalized quotation for both versions of the `areas` query is:

```scala
val areas = quote {
  query[Circle].map(c => 3.14159 * c.radius * c.radius)
}
```

Scala doesn't have support for high-order functions with type parameters. It's possible to use a method type parameter for this purpose:

```scala
def existsAny[T] = quote {
  (xs: Query[T]) => (p: T => Boolean) =>
    	xs.filter(p(_)).nonEmpty
}

val q = quote {
  query[Circle].filter { c1 =>
    existsAny(query[Circle])(c2 => c2.radius > c1.radius)
  }
}
```

You can also use implicit classes to extend things in quotations.

```scala
implicit final class Ext(private val q: Query[Person]) extends AnyVal {
  def olderThan(age: Int) = quote {
    query[Person].filter(p => p.age > lift(age))
  }
}

run(query[Person].olderThan(44))
```
(see [implicit-extensions](extending-quill.md#implicit-extensions) for additional information.)

## Compile-time quotations

Quotations are both compile-time and runtime values. Quill uses a type refinement to store the quotation's AST as an annotation available at compile-time and the `q.ast` method exposes the AST as runtime value.

It is important to avoid giving explicit types to quotations when possible. For instance, this quotation can't be read at compile-time as the type refinement is lost:

```scala
// Avoid type widening (Quoted[Query[Circle]]), or else the quotation will be dynamic.
val q: Quoted[Query[Circle]] = quote {
  query[Circle].filter(c => c.radius > 10)
}

ctx.run(q) // Dynamic query
```

Quill falls back to runtime normalization and query generation if the quotation's AST can't be read at compile-time. Please refer to [dynamic queries](#dynamic-queries) for more information.

#### Inline queries

Quoting is implicit when writing a query in a `run` statement.

```scala
ctx.run(query[Circle].map(_.radius))
// SELECT r.radius FROM Circle r
```

## Bindings

Quotations are designed to be self-contained, without references to runtime values outside their scope. There are two mechanisms to explicitly bind runtime values to a quotation execution.

### Lifted values

A runtime value can be lifted to a quotation through the method `lift`:

```scala
def biggerThan(i: Float) = quote {
  query[Circle].filter(r => r.radius > lift(i))
}
ctx.run(biggerThan(10)) // SELECT r.radius FROM Circle r WHERE r.radius > ?
```

Note that literal-constants do not need to be lifted, they can be used in queries directly. Literal constants are supported starting Scala 2.12.
```scala
final val minAge = 21  // This is the same as: final val minAge: 21 = 21
ctx.run(query[Person].filter(p => p.age > minAge)) // SELECT p.name, p.age FROM Person p WHERE p.name > 21
```

### Lifted queries

A `Iterable` instance can be lifted as a `Query`. There are two main usages for lifted queries:

#### contains

```scala
def find(radiusList: List[Float]) = quote {
  query[Circle].filter(r => liftQuery(radiusList).contains(r.radius))
}
ctx.run(find(List(1.1F, 1.2F)))
// SELECT r.radius FROM Circle r WHERE r.radius IN (?)
```

#### batch action
```scala
def insertValues(circles: List[Circle]) = quote {
  liftQuery(circles).foreach(c => query[Circle].insertValue(c))
}
ctx.run(insertValues(List(Circle(1.1F), Circle(1.2F))))
// INSERT INTO Circle (radius) VALUES (?)
```

## Schema

The database schema is represented by case classes. By default, quill uses the class and field names as the database identifiers:

```scala
final case class Circle(radius: Float)

val q = quote {
  query[Circle].filter(c => c.radius > 1)
}

ctx.run(q) // SELECT c.radius FROM Circle c WHERE c.radius > 1
```

### Schema customization

Alternatively, the identifiers can be customized:

```scala
val circles = quote {
  querySchema[Circle]("circle_table", _.radius -> "radius_column")
}

val q = quote {
  circles.filter(c => c.radius > 1)
}

ctx.run(q)
// SELECT c.radius_column FROM circle_table c WHERE c.radius_column > 1
```

If multiple tables require custom identifiers, it is good practice to define a `schema` object with all table queries to be reused across multiple queries:

```scala
final case class Circle(radius: Int)
final case class Rectangle(length: Int, width: Int)
object schema {
  val circles = quote {
    querySchema[Circle](
        "circle_table",
        _.radius -> "radius_column")
  }
  val rectangles = quote {
    querySchema[Rectangle](
        "rectangle_table",
        _.length -> "length_column",
        _.width -> "width_column")
  }
}
```

### Database-generated values

#### returningGenerated

Database generated values can be returned from an insert query by using `.returningGenerated`. These properties
will also be excluded from the insertion since they are database generated.

```scala
final case class Product(id: Int, description: String, sku: Long)

val q = quote {
  query[Product].insertValue(lift(Product(0, "My Product", 1011L))).returningGenerated(_.id)
}

val returnedIds = ctx.run(q) //: List[Int]
// INSERT INTO Product (description,sku) VALUES (?, ?) -- NOTE that 'id' is not being inserted.
```

Multiple properties can be returned in a Tuple or Case Class and all of them will be excluded from insertion.

> NOTE: Using multiple properties is currently supported by Postgres, Oracle and SQL Server

```scala
// Assuming sku is generated by the database.
val q = quote {
  query[Product].insertValue(lift(Product(0, "My Product", 1011L))).returningGenerated(r => (id, sku))
}

val returnedIds = ctx.run(q) //: List[(Int, Long)]
// INSERT INTO Product (description) VALUES (?) RETURNING id, sku -- NOTE that 'id' and 'sku' are not being inserted.
```

#### returning

In UPDATE and DELETE queries we frequently want to return the records that were modified/deleted.
The `returning` method is used for that.

> Note that most of these operations are only supported in Postgres and SQL Server

For example when we want to return information from records that are being updated:

```scala
val desc = "Update Product"
val sku = 2002L
val q = quote {
  query[Product].filter(p => p.id == 42).update(_.description -> lift(desc), _.sku -> lift(sku)).returning(r => (r.id, r.description))
}
val updated = ctx.run(q) //: (Int, String)
// Postgres
// UPDATE Product AS p SET description = ?, sku = ? WHERE p.id = 42 RETURNING p.id, p.description
// SQL Server
// UPDATE Product SET description = ?, sku = ? OUTPUT id, description WHERE id = 42
```
> When multiple records are updated using `update.returning` a warning will be issued and only the first result will be returned.
> Use [`returningMany`](writing-queries.md#returningmany) to return all the updated records in this case.

You can do the same thing with `updateValue`.

```scala
// (use an UpdateMeta to exclude generated id columns)
implicit val productUpdateMeta = updateMeta[Product](_.id)
val q = quote {
  query[Product].filter(p => p.id == 42).updateValue(lift(Product(42, "Updated Product", 2022L))).returning(r => (r.id, r.description))
}
val updated = ctx.run(q) //: (Int, String)
// Postgres
// UPDATE Product AS p SET description = ?, sku = ? WHERE p.id = 42 RETURNING p.id, p.description
// SQL Server
// UPDATE Product SET description = ?, sku = ? OUTPUT INSERTED.id, INSERTED.description WHERE id = 42
```

You can also return information that is being deleted in a DELETE query. Or even the entire deleted record!

```scala
val q = quote {
  query[Product].filter(p => p.id == 42).delete.returning(r => r)
}

val deleted = ctx.run(q) //: Product
// Postgres
// DELETE FROM Product AS p WHERE p.id = 42 RETURNING p.id, p.description, p.sku 
// SQL Server
// DELETE FROM Product OUTPUT DELETED.id, DELETED.description, DELETED.sku WHERE id = 42
```
> When multiple records are deleted using `delete.returning` a warning will be issued and only the first result will be returned.
> Use [`returningMany`](writing-queries.md#returningmany) to return all the deleted records in this case.

#### returningMany

Similar to insert/update.returning, the returningMany function can be used to return all the values that were
updated/deleted from a query. Not just one.

Return *all* the records that were updated.

```scala
val desc = "Update Product"
val sku = 2002L
val q = quote {
  query[Product].filter(p => p.id == 42).update(_.description -> lift(desc), _.sku -> lift(sku)).returning(r => (r.id, r.description))
}
val updated = ctx.run(q) //: List[(Int, String)]
// Postgres
// UPDATE Product AS p SET description = ?, sku = ? WHERE p.id = 42 RETURNING p.id, p.description
// SQL Server
// UPDATE Product SET description = ?, sku = ? OUTPUT id, description WHERE id = 42
```

Return *all* the records that were deleted.

```scala
val q = quote {
  query[Product].filter(p => p.id == 42).delete.returning(r => r)
}

val deleted = ctx.run(q) //: List[Product]
// Postgres
// DELETE FROM Product AS p WHERE p.id = 42 RETURNING p.id, p.description, p.sku 
// SQL Server
// DELETE FROM Product OUTPUT DELETED.id, DELETED.description, DELETED.sku WHERE id = 42
```

#### Postgres Customized returning

Returning values returned can be further customized in some databases.

In Postgres, the `returning` and `returningGenerated` methods also support arithmetic operations, SQL UDFs and
even entire queries for INSERT, UPDATE, and DELETE actions. These are inserted directly into the SQL `RETURNING` clause.

For example, assuming this basic query:
```scala
val q = quote {
  query[Product].filter(p => p.id == 42).update(_.description -> "My Product", _.sku -> 1011L)
}
```

Add 100 to the value of `id`:
```scala
ctx.run(q.returning(r => r.id + 100)) //: List[Int]
// UPDATE Product AS p SET description = 'My Product', sku = 1011L WHERE p.id = 42 RETURNING p.id + 100
```

Pass the value of `id` into a UDF:
```scala
val udf = quote { (i: Long) => sql"myUdf($i)".as[Int] }
ctx.run(q.returning(r => udf(r.id))) //: List[Int]
// UPDATE Product AS p SET description = 'My Product', sku = 1011L WHERE p.id = 42 RETURNING myUdf(p.id)
```

Use the return value of `sku` to issue a query:
```scala
final case class Supplier(id: Int, clientSku: Long)
ctx.run {
  q.returning(r => query[Supplier].filter(s => s.sku == r.sku).map(_.id).max)
} //: List[Option[Long]]
// UPDATE Product AS p SET description = 'My Product', sku = 1011L WHERE p.id = 42 RETURNING (SELECT MAX(s.id) FROM Supplier s WHERE s.sku = clientSku)
```

As is typically the case with Quill, you can use all of these features together.
```scala
ctx.run {
  q.returning(r =>
    (r.id + 100, udf(r.id), query[Supplier].filter(s => s.sku == r.sku).map(_.id).max)
  )
} // List[(Int, Int, Option[Long])]
// UPDATE Product AS p SET description = 'My Product', sku = 1011L WHERE p.id = 42
// RETURNING id + 100, myUdf(id), (SELECT MAX(s.id) FROM Supplier s WHERE s.sku = sku)
```

> NOTE: Queries used inside of return clauses can only return a single row per insert.
Otherwise, Postgres will throw:
`ERROR: more than one row returned by a subquery used as an expression`. This is why is it strongly
recommended that you use aggregators such as `max` or `min`inside of quill returning-clause queries.
In the case that this is impossible (e.g. when using Postgres booleans), you can use the `.value` method:
`q.returning(r => query[Supplier].filter(s => s.sku == r.sku).map(_.id).value)`.

#### insert.returning

In certain situations we also may want to return information from inserted records.

```scala
val q = quote {
  query[Product].insertValue(lift(Product(0, "My Product", 1011L))).returning(r => (r.id, r.description))
}

val returnedIds = ctx.run(q) //: List[(Int, String)]
// INSERT INTO Product (id, description, sku) VALUES (?, ?, ?) RETURNING id, description
```

Wait a second! Why did we just insert `id` into the database? That is because `returning` does not exclude values
from the insertion! We can fix this situation by manually specifying the columns to insert:

```scala
val q = quote {
  query[Product].insert(_.description -> "My Product", _.sku -> 1011L))).returning(r => (r.id, r.description))
}

val returnedIds = ctx.run(q) //: List[(Int, String)]
// INSERT INTO Product (description, sku) VALUES (?, ?) RETURNING id, description
```

We can also fix this situation by using an insert-meta.

```scala
implicit val productInsertMeta = insertMeta[Product](_.id)
val q = quote {
  query[Product].insertValue(lift(Product(0L, "My Product", 1011L))).returning(r => (r.id, r.description))
}

val returnedIds = ctx.run(q) //: List[(Int, String)]
// INSERT INTO Product (description, sku) VALUES (?, ?) RETURNING id, description
```

### Embedded case classes

Quill supports nested embedded case classes.
> In previous iterations of Quill would need to extend the `Embedded` trait but this is no longer necessary.

```scala
final case class Contact(phone: String, address: String) /* The embedded class */
final case class Person(id: Int, name: String, contact: Contact)

ctx.run(query[Person])
// SELECT x.id, x.name, x.phone, x.address FROM Person x
```

Note that default naming behavior uses the name of the nested case class properties. It's possible to override this default behavior using a custom `schema`:

```scala
final case class Contact(phone: String, address: String) extends Embedded
final case class Person(id: Int, name: String, homeContact: Contact, workContact: Option[Contact])

val q = quote {
  querySchema[Person](
    "Person",
    _.homeContact.phone          -> "homePhone",
    _.homeContact.address        -> "homeAddress",
    _.workContact.map(_.phone)   -> "workPhone",
    _.workContact.map(_.address) -> "workAddress"
  )
}

ctx.run(q)
// SELECT x.id, x.name, x.homePhone, x.homeAddress, x.workPhone, x.workAddress FROM Person x
```

## Queries

The overall abstraction of quill queries uses database tables as if they were in-memory collections. Scala for-comprehensions provide syntactic sugar to deal with these kinds of monadic operations:

```scala
final case class Person(id: Int, name: String, age: Int)
final case class Contact(personId: Int, phone: String)

val q = quote {
  for {
    p <- query[Person] if(p.id == 999)
    c <- query[Contact] if(c.personId == p.id)
  } yield {
    (p.name, c.phone)
  }
}

ctx.run(q)
// SELECT p.name, c.phone FROM Person p, Contact c WHERE (p.id = 999) AND (c.personId = p.id)
```

Quill normalizes the quotation and translates the monadic joins to applicative joins, generating a database-friendly query that avoids nested queries.

Any of the following features can be used together with the others and/or within a for-comprehension:

### filter
```scala
val q = quote {
  query[Person].filter(p => p.age > 18)
}

ctx.run(q)
// SELECT p.id, p.name, p.age FROM Person p WHERE p.age > 18
```

### map
```scala
val q = quote {
  query[Person].map(p => p.name)
}

ctx.run(q)
// SELECT p.name FROM Person p
```

### flatMap
```scala
val q = quote {
  query[Person].filter(p => p.age > 18).flatMap(p => query[Contact].filter(c => c.personId == p.id))
}

ctx.run(q)
// SELECT c.personId, c.phone FROM Person p, Contact c WHERE (p.age > 18) AND (c.personId = p.id)
```

### sortBy
```scala
val q1 = quote {
  query[Person].sortBy(p => p.age)
}

ctx.run(q1)
// SELECT p.id, p.name, p.age FROM Person p ORDER BY p.age ASC NULLS FIRST

val q2 = quote {
  query[Person].sortBy(p => p.age)(Ord.descNullsLast)
}

ctx.run(q2)
// SELECT p.id, p.name, p.age FROM Person p ORDER BY p.age DESC NULLS LAST

val q3 = quote {
  query[Person].sortBy(p => (p.name, p.age))(Ord(Ord.asc, Ord.desc))
}

ctx.run(q3)
// SELECT p.id, p.name, p.age FROM Person p ORDER BY p.name ASC, p.age DESC
```

### aggregation

You can use aggregators inside of map-clauses. Multiple aggregators can be used as needed.
Available aggregators are `max`, `min`, `count`, `avg` and `sum`.
```scala
val q = quote {
  query[Person].map(p => (min(p.age), max(p.age)))
}
// SELECT MIN(p.age), MAX(p.age) FROM Person p
```

### groupByMap

The `groupByMap` method is the preferred way to do grouping in Quill. It provides a simple aggregation-syntax similar to SQL.
Available aggregators are `max`, `min`, `count`, `avg` and `sum`.

```scala
val q = quote {
  query[Person].groupByMap(p => p.name)(p => (p.name, max(p.age)))
}
ctx.run(q)
// SELECT p.name, MAX(p.age) FROM Person p GROUP BY p.name
```

You can use as many aggregators as needed and group by multiple fields (using a Tuple).

```scala
val q = quote {
  query[Person].groupByMap(p => (p.name, p.otherField))(p => (p.name, p.otherField, max(p.age)))
}
ctx.run(q)
// SELECT p.name, p.otherField, MAX(p.age) FROM Person p GROUP BY p.name, p.otherField
```

Writing a custom aggregator using `infix` with the `groupByMax` syntax is also very simple.
For example, in Postgres the `STRING_AGG` function is used to concatenate all the encountered strings.
```scala
val stringAgg = quote {
  (str: String, separator: String) => sql"STRING_AGG($str, $separator)".pure.as[String]
}
val q = quote {
  query[Person].groupByMap(p => p.age)(p => (p.age, stringAgg(p.name, ";")))
}
run(q)
// SELECT p.age, STRING_AGG(p.name, ';') FROM Person p GROUP BY p.age
```

You can also map to a case class instead of a tuple. This will give you a `Query[YourCaseClass]` that you can further compose.
```scala
final case class NameAge(name: String, age: Int)
// Will return Query[NameAge]
val q = quote {
  query[Person].groupByMap(p => p.name)(p => NameAge(p.name, max(p.age)))
}
ctx.run(q)
// SELECT p.name, MAX(p.age) FROM Person p GROUP BY p.name
```

> Note that it is a requirement in SQL for every column in the selection (without an aggregator) to be in the `GROUP BY` clause.
> If it is not, an exception will be thrown by the database. Quill does not (yet!) protect the user in this situation.
> ```scala
> run( query[Person].groupByMap(p => p.name)(p => (p.name, p.otherField, max(p.age))) )
> // > SELECT p.name, p.otherField, MAX(p.age) FROM Person p GROUP BY p.name
> // ERROR: column "person.otherField" must appear in the GROUP BY clause or be used in an aggregate function
> ```

### groupBy

Quill also provides a way to do `groupBy/map` in a more scala-idiomatic way. In this case (below), the `groupBy`
produces a `Query[(Int,Query[Person])]` where the inner Query can be mapped to an expression with an aggregator
(as would be the Scala `List[Person]` in `Map[Int,List[Person]]` resulting from a `(people:List[Person]).groupBy(_.name)`.

```scala
val q = quote {
  query[Person].groupBy(p => p.age).map {
    case (age, people) =>
      (age, people.size)
  }
}

ctx.run(q)
// SELECT p.age, COUNT(*) FROM Person p GROUP BY p.age
```

### drop/take

```scala
val q = quote {
  query[Person].drop(2).take(1)
}

ctx.run(q)
// SELECT x.id, x.name, x.age FROM Person x LIMIT 1 OFFSET 2
```

### concatMap (i.e. `UNNEST`)
```scala
// similar to `flatMap` but for transformations that return a traversable instead of `Query`

val q = quote {
  query[Person].concatMap(p => p.name.split(" "))
}

ctx.run(q)
// SELECT UNNEST(SPLIT(p.name, " ")) FROM Person p
```

### union
```scala
val q = quote {
  query[Person].filter(p => p.age > 18).union(query[Person].filter(p => p.age > 60))
}

ctx.run(q)
// SELECT x.id, x.name, x.age FROM (SELECT id, name, age FROM Person p WHERE p.age > 18
// UNION SELECT id, name, age FROM Person p1 WHERE p1.age > 60) x
```

### unionAll/++
```scala
val q = quote {
  query[Person].filter(p => p.age > 18).unionAll(query[Person].filter(p => p.age > 60))
}

ctx.run(q)
// SELECT x.id, x.name, x.age FROM (SELECT id, name, age FROM Person p WHERE p.age > 18
// UNION ALL SELECT id, name, age FROM Person p1 WHERE p1.age > 60) x

val q2 = quote {
  query[Person].filter(p => p.age > 18) ++ query[Person].filter(p => p.age > 60)
}

ctx.run(q2)
// SELECT x.id, x.name, x.age FROM (SELECT id, name, age FROM Person p WHERE p.age > 18
// UNION ALL SELECT id, name, age FROM Person p1 WHERE p1.age > 60) x
```

### aggregation
```scala
val r = quote {
  query[Person].map(p => p.age)
}

ctx.run(r.min) // SELECT MIN(p.age) FROM Person p
ctx.run(r.max) // SELECT MAX(p.age) FROM Person p
ctx.run(r.avg) // SELECT AVG(p.age) FROM Person p
ctx.run(r.sum) // SELECT SUM(p.age) FROM Person p
ctx.run(r.size) // SELECT COUNT(p.age) FROM Person p
```

### isEmpty/nonEmpty
```scala
val q = quote {
  query[Person].filter{ p1 =>
    query[Person].filter(p2 => p2.id != p1.id && p2.age == p1.age).isEmpty
  }
}

ctx.run(q)
// SELECT p1.id, p1.name, p1.age FROM Person p1 WHERE
// NOT EXISTS (SELECT * FROM Person p2 WHERE (p2.id <> p1.id) AND (p2.age = p1.age))

val q2 = quote {
  query[Person].filter{ p1 =>
    query[Person].filter(p2 => p2.id != p1.id && p2.age == p1.age).nonEmpty
  }
}

ctx.run(q2)
// SELECT p1.id, p1.name, p1.age FROM Person p1 WHERE
// EXISTS (SELECT * FROM Person p2 WHERE (p2.id <> p1.id) AND (p2.age = p1.age))
```

### contains
```scala
val q = quote {
  query[Person].filter(p => liftQuery(Set(1, 2)).contains(p.id))
}

ctx.run(q)
// SELECT p.id, p.name, p.age FROM Person p WHERE p.id IN (?, ?)

val q1 = quote { (ids: Query[Int]) =>
  query[Person].filter(p => ids.contains(p.id))
}

ctx.run(q1(liftQuery(List(1, 2))))
// SELECT p.id, p.name, p.age FROM Person p WHERE p.id IN (?, ?)

val peopleWithContacts = quote {
  query[Person].filter(p => query[Contact].filter(c => c.personId == p.id).nonEmpty)
}
val q2 = quote {
  query[Person].filter(p => peopleWithContacts.contains(p.id))
}

ctx.run(q2)
// SELECT p.id, p.name, p.age FROM Person p WHERE p.id IN (SELECT p1.* FROM Person p1 WHERE EXISTS (SELECT c.* FROM Contact c WHERE c.personId = p1.id))
```

### distinct
```scala
val q = quote {
  query[Person].map(p => p.age).distinct
}

ctx.run(q)
// SELECT DISTINCT p.age FROM Person p
```

### distinct on

> Note that `DISTINCT ON` is currently only supported in Postgres and H2.

```scala
val q = quote {
  query[Person].distinctOn(p => p.name)
}

ctx.run(q)
// SELECT DISTINCT ON (p.name) p.name, p.age FROM Person
```

Typically, `DISTINCT ON` is used with `SORT BY`.
```scala
val q = quote {
  query[Person].distinctOn(p => p.name).sortBy(p => p.age)
}

ctx.run(q)
// SELECT DISTINCT ON (p.name) p.name, p.age FROM Person ORDER BY p.age ASC NULLS FIRST
```
You can also use multiple fields in the `DISTINCT ON` criteria:
```scala
// final case class Person(firstName: String, lastName: String, age: Int)
val q = quote {
  query[Person].distinctOn(p => (p.firstName, p.lastName))
}

ctx.run(q)
// SELECT DISTINCT ON (p.firstName, p.lastName) p.firstName, p.lastName, p.age FROM Person p
```

### nested
```scala
val q = quote {
  query[Person].filter(p => p.name == "John").nested.map(p => p.age)
}

ctx.run(q)
// SELECT p.age FROM (SELECT p.age FROM Person p WHERE p.name = 'John') p
```

### joins
Joins are arguably the largest source of complexity in most SQL queries.
Quill offers a few different syntaxes so you can choose the right one for your use-case!

```scala
final case class A(id: Int)
final case class B(fk: Int)

// Applicative Joins:
quote {
  query[A].join(query[B]).on(_.id == _.fk)
}

// Implicit Joins:
quote {
  for {
    a <- query[A]
    b <- query[B] if (a.id == b.fk)
  } yield (a, b)
}

// Flat Joins:
quote {
  for {
    a <- query[A]
    b <- query[B].join(_.fk == a.id)
  } yield (a, b)
}
```

Let's see them one by one assuming the following schema:
```scala
final case class Person(id: Int, name: String)
final case class Address(street: String, zip: Int, fk: Int)
```
(Note: If your use case involves lots and lots of joins, both inner and outer. Skip right to the flat-joins section!)

#### applicative joins

Applicative joins are useful for joining two tables together,
they are straightforward to understand, and typically look good on one line.
Quill supports inner, left-outer, right-outer, and full-outer (i.e. cross) applicative joins.

```scala
// Inner Join
val q = quote {
  query[Person].join(query[Address]).on(_.id == _.fk)
}

ctx.run(q) //: List[(Person, Address)]
// SELECT x1.id, x1.name, x2.street, x2.zip, x2.fk
// FROM Person x1 INNER JOIN Address x2 ON x1.id = x2.fk

// Left (Outer) Join
val q = quote {
  query[Person].leftJoin(query[Address]).on((p, a) => p.id == a.fk)
}

ctx.run(q) //: List[(Person, Option[Address])]
// Note that when you use named-variables in your comprehension, Quill does its best to honor them in the query.
// SELECT p.id, p.name, a.street, a.zip, a.fk
// FROM Person p LEFT JOIN Address a ON p.id = a.fk

// Right (Outer) Join
val q = quote {
  query[Person].rightJoin(query[Address]).on((p, a) => p.id == a.fk)
}

ctx.run(q) //: List[(Option[Person], Address)]
// SELECT p.id, p.name, a.street, a.zip, a.fk
// FROM Person p RIGHT JOIN Address a ON p.id = a.fk

// Full (Outer) Join
val q = quote {
  query[Person].fullJoin(query[Address]).on((p, a) => p.id == a.fk)
}

ctx.run(q) //: List[(Option[Person], Option[Address])]
// SELECT p.id, p.name, a.street, a.zip, a.fk
// FROM Person p FULL JOIN Address a ON p.id = a.fk
```

What about joining more than two tables with the applicative syntax?
Here's how to do that:
```scala
final case class Company(zip: Int)

// All is well for two tables but for three or more, the nesting mess begins:
val q = quote {
  query[Person]
    .join(query[Address]).on({case (p, a) => p.id == a.fk}) // Let's use `case` here to stay consistent
    .join(query[Company]).on({case ((p, a), c) => a.zip == c.zip})
}

ctx.run(q) //: List[((Person, Address), Company)]
// (Unfortunately when you use `case` statements, Quill can't help you with the variables names either!)
// SELECT x01.id, x01.name, x11.street, x11.zip, x11.fk, x12.name, x12.zip
// FROM Person x01 INNER JOIN Address x11 ON x01.id = x11.fk INNER JOIN Company x12 ON x11.zip = x12.zip
```
No worries though, implicit joins and flat joins have your other use-cases covered!

#### implicit joins

Quill's implicit joins use a monadic syntax making them pleasant to use for joining many tables together.
They look a lot like Scala collections when used in for-comprehensions
making them familiar to a typical Scala developer.
What's the catch? They can only do inner-joins.

```scala
val q = quote {
  for {
    p <- query[Person]
    a <- query[Address] if (p.id == a.fk)
  } yield (p, a)
}

run(q) //: List[(Person, Address)]
// SELECT p.id, p.name, a.street, a.zip, a.fk
// FROM Person p, Address a WHERE p.id = a.fk
```

Now, this is great because you can keep adding more and more joins
without having to do any pesky nesting.
```scala
val q = quote {
  for {
    p <- query[Person]
    a <- query[Address] if (p.id == a.fk)
    c <- query[Company] if (c.zip == a.zip)
  } yield (p, a, c)
}

run(q) //: List[(Person, Address, Company)]
// SELECT p.id, p.name, a.street, a.zip, a.fk, c.name, c.zip
// FROM Person p, Address a, Company c WHERE p.id = a.fk AND c.zip = a.zip
```
Well that looks nice but wait! What If I need to inner, **and** outer join lots of tables nicely?
No worries, flat-joins are here to help!

### flat joins

Flat Joins give you the best of both worlds! In the monadic syntax, you can use both inner joins,
and left-outer joins together without any of that pesky nesting.

```scala
// Inner Join
val q = quote {
  for {
    p <- query[Person]
    a <- query[Address].join(a => a.fk == p.id)
  } yield (p,a)
}

ctx.run(q) //: List[(Person, Address)]
// SELECT p.id, p.name, a.street, a.zip, a.fk
// FROM Person p INNER JOIN Address a ON a.fk = p.id

// Left (Outer) Join
val q = quote {
  for {
    p <- query[Person]
    a <- query[Address].leftJoin(a => a.fk == p.id)
  } yield (p,a)
}

ctx.run(q) //: List[(Person, Option[Address])]
// SELECT p.id, p.name, a.street, a.zip, a.fk
// FROM Person p LEFT JOIN Address a ON a.fk = p.id
```

Now you can keep adding both right and left joins without nesting!
```scala
val q = quote {
  for {
    p <- query[Person]
    a <- query[Address].join(a => a.fk == p.id)
    c <- query[Company].leftJoin(c => c.zip == a.zip)
  } yield (p,a,c)
}

ctx.run(q) //: List[(Person, Address, Option[Company])]
// SELECT p.id, p.name, a.street, a.zip, a.fk, c.name, c.zip
// FROM Person p
// INNER JOIN Address a ON a.fk = p.id
// LEFT JOIN Company c ON c.zip = a.zip
```

Can't figure out what kind of join you want to use? Who says you have to choose?

With Quill the following multi-join queries are equivalent, use them according to preference:

```scala

final case class Employer(id: Int, personId: Int, name: String)

val qFlat = quote {
  for{
    (p,e) <- query[Person].join(query[Employer]).on(_.id == _.personId)
       c  <- query[Contact].leftJoin(_.personId == p.id)
  } yield(p, e, c)
}

val qNested = quote {
  for{
    ((p,e),c) <-
      query[Person].join(query[Employer]).on(_.id == _.personId)
      .leftJoin(query[Contact]).on(
        _._1.id == _.personId
      )
  } yield(p, e, c)
}

ctx.run(qFlat)
ctx.run(qNested)
// SELECT p.id, p.name, p.age, e.id, e.personId, e.name, c.id, c.phone
// FROM Person p INNER JOIN Employer e ON p.id = e.personId LEFT JOIN Contact c ON c.personId = p.id
```

Note that in some cases implicit and flat joins cannot be used together, for example, the following
query will fail.
```scala
val q = quote {
  for {
    p <- query[Person]
    p1 <- query[Person] if (p1.name == p.name)
    c <- query[Contact].leftJoin(_.personId == p.id)
  } yield (p, c)
}

// ctx.run(q)
// java.lang.IllegalArgumentException: requirement failed: Found an `ON` table reference of a table that is
// not available: Set(p). The `ON` condition can only use tables defined through explicit joins.
```
This happens because an explicit join typically cannot be done after an implicit join in the same query.

A good guideline is in any query or subquery, choose one of the following:
* Use flat-joins + applicative joins or
* Use implicit joins

Also, note that not all Option operations are available on outer-joined tables (i.e. tables wrapped in an `Option` object),
only a specific subset. This is mostly due to the inherent limitations of SQL itself. For more information, see the
'Optional Tables' section.

### Optionals / Nullable Fields

> Note that the behavior of Optionals has recently changed to include stricter null-checks. See the [orNull / getOrNull](#ornull--getornull) section for more details.

Option objects are used to encode nullable fields.
Say you have the following schema:
```sql
CREATE TABLE Person(
  id INT NOT NULL PRIMARY KEY,
  name VARCHAR(255) -- This is nullable!
);
CREATE TABLE Address(
  fk INT, -- This is nullable!
  street VARCHAR(255) NOT NULL,
  zip INT NOT NULL,
  CONSTRAINT a_to_p FOREIGN KEY (fk) REFERENCES Person(id)
);
CREATE TABLE Company(
  name VARCHAR(255) NOT NULL,
  zip INT NOT NULL
)
```
This would encode to the following:
```scala
final case class Person(id:Int, name:Option[String])
final case class Address(fk:Option[Int], street:String, zip:Int)
final case class Company(name:String, zip:Int)
```

Some important notes regarding Optionals and nullable fields.

> In many cases, Quill tries to rely on the null-fallthrough behavior that is ANSI standard:
>  * `null == null := false`
>  * `null == [true | false] := false`
>
> This allows the generated SQL for most optional operations to be simple. For example, the expression
> `Option[String].map(v => v + "foo")` can be expressed as the SQL `v || 'foo'` as opposed to
> `CASE IF (v is not null) v || 'foo' ELSE null END` so long as the concatenation operator `||`
> "falls-through" and returns `null` when the input is null. This is not true of all databases (e.g. [Oracle](https://community.oracle.com/ideas/19866)),
> forcing Quill to return the longer expression with explicit null-checking. Also, if there are conditionals inside
> of an Option operation (e.g. `o.map(v => if (v == "x") "y" else "z")`) this creates SQL with case statements,
> which will never fall-through when the input value is null. This forces Quill to explicitly null-check such statements in every
> SQL dialect.

Let's go through the typical operations of optionals.

#### isDefined / isEmpty

The `isDefined` method is generally a good way to null-check a nullable field:
```scala
val q = quote {
  query[Address].filter(a => a.fk.isDefined)
}
ctx.run(q)
// SELECT a.fk, a.street, a.zip FROM Address a WHERE a.fk IS NOT NULL
```
The `isEmpty` method works the same way:
```scala
val q = quote {
  query[Address].filter(a => a.fk.isEmpty)
}
ctx.run(q)
// SELECT a.fk, a.street, a.zip FROM Address a WHERE a.fk IS NULL
```

#### exists

This method is typically used for inspecting nullable fields inside of boolean conditions, most notably joining!
```scala
val q = quote {
  query[Person].join(query[Address]).on((p, a)=> a.fk.exists(_ == p.id))
}
ctx.run(q)
// SELECT p.id, p.name, a.fk, a.street, a.zip FROM Person p INNER JOIN Address a ON a.fk = p.id
```

Note that in the example above, the `exists` method does not cause the generated
SQL to do an explicit null-check in order to express the `False` case. This is because Quill relies on the
typical database behavior of immediately falsifying a statement that has `null` on one side of the equation.

#### forall

Use this method in boolean conditions that should succeed in the null case.
```scala
val q = quote {
  query[Person].join(query[Address]).on((p, a) => a.fk.forall(_ == p.id))
}
ctx.run(q)
// SELECT p.id, p.name, a.fk, a.street, a.zip FROM Person p INNER JOIN Address a ON a.fk IS NULL OR a.fk = p.id
```
Typically this is useful when doing negative conditions, e.g. when a field is **not** some specified value (e.g. `"Joe"`).
Being `null` in this case is typically a matching result.
```scala
val q = quote {
  query[Person].filter(p => p.name.forall(_ != "Joe"))
}

ctx.run(q)
// SELECT p.id, p.name FROM Person p WHERE p.name IS NULL OR p.name <> 'Joe'
```

#### filterIfDefined
Use this to filter by a optional field that you want to ignore when None.
This is useful when you want to filter by a map-key that may or may not exist.

```scala
val fieldFilters: Map[String, String] = Map("name" -> "Joe", "age" -> "123")
val q = quote {
  query[Person].filter(p => lift(fieldFilters.get("name)).filterIfDefined(_ == p.name))
}
 
ctx.run(q)
// SELECT p.id, p.name, p.title FROM Person p WHERE p.title IS NULL OR p.title = 'The Honorable'
```

It also works for regular fields.
```scala
// final case class Person(name: String, age: Int, title: Option[String])
val q = quote {
  query[Person].filter(p => p.title.filterIfDefined(_ == "The Honorable"))
}
 
ctx.run(q)
// SELECT p.id, p.name, p.title FROM Person p WHERE p.title IS NULL OR p.title = 'The Honorable'
```

#### map
As in regular Scala code, performing any operation on an optional value typically requires using the `map` function.
```scala
val q = quote {
 for {
    p <- query[Person]
  } yield (p.id, p.name.map("Dear " + _))
}

ctx.run(q)
// SELECT p.id, 'Dear ' || p.name FROM Person p
// * In Dialects where `||` does not fall-through for nulls (e.g. Oracle):
// * SELECT p.id, CASE WHEN p.name IS NOT NULL THEN 'Dear ' || p.name ELSE null END FROM Person p
```

Additionally, this method is useful when you want to get a non-optional field out of an outer-joined table
(i.e. a table wrapped in an `Option` object).

```scala
val q = quote {
  query[Company].leftJoin(query[Address])
    .on((c, a) => c.zip == a.zip)
    .map {case(c,a) =>                          // Row type is (Company, Option[Address])
      (c.name, a.map(_.street), a.map(_.zip))   // Use `Option.map` to get `street` and `zip` fields
    }
}

run(q)
// SELECT c.name, a.street, a.zip FROM Company c LEFT JOIN Address a ON c.zip = a.zip
```

For more details about this operation (and some caveats), see the 'Optional Tables' section.

#### flatMap and flatten

Use these when the `Option.map` functionality is not sufficient. This typically happens when you need to manipulate
multiple nullable fields in a way which would otherwise result in `Option[Option[T]]`.
```scala
val q = quote {
  for {
    a <- query[Person]
    b <- query[Person] if (a.id > b.id)
  } yield (
    // If this was `a.name.map`, resulting record type would be Option[Option[String]]
    a.name.flatMap(an =>
      b.name.map(bn =>
        an+" comes after "+bn)))
}

ctx.run(q) //: List[Option[String]]
// SELECT (a.name || ' comes after ') || b.name FROM Person a, Person b WHERE a.id > b.id
// * In Dialects where `||` does not fall-through for nulls (e.g. Oracle):
// * SELECT CASE WHEN a.name IS NOT NULL AND b.name IS NOT NULL THEN (a.name || ' comes after ') || b.name ELSE null END FROM Person a, Person b WHERE a.id > b.id

// Alternatively, you can use `flatten`
val q = quote {
  for {
    a <- query[Person]
    b <- query[Person] if (a.id > b.id)
  } yield (
    a.name.map(an =>
      b.name.map(bn =>
        an + " comes after " + bn)).flatten)
}

ctx.run(q) //: List[Option[String]]
// SELECT (a.name || ' comes after ') || b.name FROM Person a, Person b WHERE a.id > b.id
```
This is also very useful when selecting from outer-joined tables i.e. where the entire table
is inside of an `Option` object. Note how below we get the `fk` field from `Option[Address]`.

```scala
val q = quote {
  query[Person].leftJoin(query[Address])
    .on((p, a) => a.fk.exists(_ == p.id))
    .map {case (p /*Person*/, a /*Option[Address]*/) => (p.name, a.flatMap(_.fk))}
}

ctx.run(q) //: List[(Option[String], Option[Int])]
// SELECT p.name, a.fk FROM Person p LEFT JOIN Address a ON a.fk = p.id
```

#### orNull / getOrNull

The `orNull` method can be used to convert an Option-enclosed row back into a regular row.
Since `Option[T].orNull` does not work for primitive types (e.g. `Int`, `Double`, etc...),
you can use the `getOrNull` method inside of quoted blocks to do the same thing.

> Note that since the presence of null columns can cause queries to break in some data sources (e.g. Spark), so use this operation very carefully.

```scala
val q = quote {
  query[Person].join(query[Address])
    .on((p, a) => a.fk.exists(_ == p.id))
    .filter {case (p /*Person*/, a /*Option[Address]*/) =>
      a.fk.getOrNull != 123 } // Exclude a particular value from the query.
                              // Since we already did an inner-join on this value, we know it is not null.
}

ctx.run(q) //: List[(Address, Person)]
// SELECT p.id, p.name, a.fk, a.street, a.zip FROM Person p INNER JOIN Address a ON a.fk IS NOT NULL AND a.fk = p.id WHERE a.fk <> 123
```

In certain situations, you may wish to pretend that a nullable-field is not actually nullable and perform regular operations
(e.g. arithmetic, concatenation, etc...) on the field. You can use a combination of `Option.apply` and `orNull` (or `getOrNull` where needed)
in order to do this.

```scala
val q = quote {
  query[Person].map(p => Option(p.name.orNull + " suffix"))
}

ctx.run(q)
// SELECT p.name || ' suffix' FROM Person p
// i.e. same as the previous behavior
```

In all other situations, since Quill strictly checks nullable values, and `case.. if` conditionals will work correctly in all Optional constructs.
However, since they may introduce behavior changes in your codebase, the following warning has been introduced:

> Conditionals inside of Option.[map | flatMap | exists | forall] will create a `CASE` statement in order to properly null-check the sub-query (...)

```
val q = quote {
  query[Person].map(p => p.name.map(n => if (n == "Joe") "foo" else "bar").getOrElse("baz"))
}
// Information:(16, 15) Conditionals inside of Option.map will create a `CASE` statement in order to properly null-check the sub-query: `p.name.map((n) => if(n == "Joe") "foo" else "bar")`.
// Expressions like Option(if (v == "foo") else "bar").getOrElse("baz") will now work correctly, but expressions that relied on the broken behavior (where "bar" would be returned instead) need to be modified  (see the "orNull / getOrNull" section of the documentation of more detail).

ctx.run(a)
// Used to be this:
// SELECT CASE WHEN CASE WHEN p.name = 'Joe' THEN 'foo' ELSE 'bar' END IS NOT NULL THEN CASE WHEN p.name = 'Joe' THEN 'foo' ELSE 'bar' END ELSE 'baz' END FROM Person p
// Now is this:
// SELECT CASE WHEN p.name IS NOT NULL AND CASE WHEN p.name = 'Joe' THEN 'foo' ELSE 'bar' END IS NOT NULL THEN CASE WHEN p.name = 'Joe' THEN 'foo' ELSE 'bar' END ELSE 'baz' END FROM Person p
```

### equals

The `==`, `!=`, and `.equals` methods can be used to compare regular types as well Option types in a scala-idiomatic way.
That is to say, either `T == T` or `Option[T] == Option[T]` is supported and the following "truth-table" is observed:

Left         | Right        | Equality   | Result
-------------|--------------|------------|----------
`a`          | `b`          | `==`       | `a == b`
`Some[T](a)` | `Some[T](b)` | `==`       | `a == b`
`Some[T](a)` | `None`       | `==`       | `false`
`None      ` | `Some[T](b)` | `==`       | `false`
`None      ` | `None`       | `==`       | `true`
`Some[T]   ` | `Some[R]   ` | `==`       | Exception thrown.
`a`          | `b`          | `!=`       | `a != b`
`Some[T](a)` | `Some[T](b)` | `!=`       | `a != b`
`Some[T](a)` | `None`       | `!=`       | `true`
`None      ` | `Some[T](b)` | `!=`       | `true`
`Some[T]   ` | `Some[R]   ` | `!=`       | Exception thrown.
`None      ` | `None`       | `!=`       | `false`

```scala
final case class Node(id:Int, status:Option[String], otherStatus:Option[String])

val q = quote { query[Node].filter(n => n.id == 123) }
ctx.run(q)
// SELECT n.id, n.status, n.otherStatus FROM Node n WHERE p.id = 123

val q = quote { query[Node].filter(r => r.status == r.otherStatus) }
ctx.run(q)
// SELECT r.id, r.status, r.otherStatus FROM Node r WHERE r.status IS NULL AND r.otherStatus IS NULL OR r.status = r.otherStatus

val q = quote { query[Node].filter(n => n.status == Option("RUNNING")) }
ctx.run(q)
// SELECT n.id, n.status, n.otherStatus FROM node n WHERE n.status IS NOT NULL AND n.status = 'RUNNING'

val q = quote { query[Node].filter(n => n.status != Option("RUNNING")) }
ctx.run(q)
// SELECT n.id, n.status, n.otherStatus FROM node n WHERE n.status IS NULL OR n.status <> 'RUNNING'
```

If you would like to use an equality operator that follows that ansi-idiomatic approach, failing
the comparison if either side is null as well as the principle that `null = null := false`, you can import `===` (and `=!=`)
from `Context.extras`. These operators work across `T` and `Option[T]` allowing comparisons like `T === Option[T]`,
`Option[T] == T` etc... to be made. You can use also `===`
directly in Scala code and it will have the same behavior, returning `false` when other the left-hand
or right-hand side is `None`. This is particularity useful in paradigms like Spark where
you will typically transition inside and outside of Quill code.

> When using `a === b` or `a =!= b` sometimes you will see the extra `a IS NOT NULL AND b IS NOT NULL` comparisons
> and sometimes you will not. This depends on `equalityBehavior` in `SqlIdiom` which determines whether the given SQL
> dialect already does ansi-idiomatic comparison to `a`, and `b` when an `=` operator is used,
> this allows us to omit the extra `a IS NOT NULL AND b IS NOT NULL`.

```scala

// === works the same way inside of a quotation
val q = run( query[Node].filter(n => n.status === "RUNNING") )
// SELECT n.id, n.status FROM node n WHERE n.status IS NOT NULL AND n.status = 'RUNNING'

// as well as outside
(nodes:List[Node]).filter(n => n.status === "RUNNING")
```

#### Optional Tables

As we have seen in the examples above, only the `map` and `flatMap` methods are available on outer-joined tables
(i.e. tables wrapped in an `Option` object).

Since you cannot use `Option[Table].isDefined`, if you want to null-check a whole table
(e.g. if a left-join was not matched), you have to `map` to a specific field on which you can do the null-check.

```scala
val q = quote {
  query[Company].leftJoin(query[Address])
    .on((c, a) => c.zip == a.zip)         // Row type is (Company, Option[Address])
    .filter({case(c,a) => a.isDefined})   // You cannot null-check a whole table!
}
```

Instead, map the row-variable to a specific field and then check that field.
```scala
val q = quote {
  query[Company].leftJoin(query[Address])
    .on((c, a) => c.zip == a.zip)                     // Row type is (Company, Option[Address])
    .filter({case(c,a) => a.map(_.street).isDefined}) // Null-check a non-nullable field instead
}
ctx.run(q)
// SELECT c.name, c.zip, a.fk, a.street, a.zip
// FROM Company c
// LEFT JOIN Address a ON c.zip = a.zip
// WHERE a.street IS NOT NULL
```

Finally, it is worth noting that a whole table can be wrapped into an `Option` object. This is particularly
useful when doing a union on table-sets that are both right-joined and left-joined together.
```scala
val aCompanies = quote {
  for {
    c <- query[Company] if (c.name like "A%")
    a <- query[Address].join(_.zip == c.zip)
  } yield (c, Option(a))  // change (Company, Address) to (Company, Option[Address])
}
val bCompanies = quote {
  for {
    c <- query[Company] if (c.name like "A%")
    a <- query[Address].leftJoin(_.zip == c.zip)
  } yield (c, a) // (Company, Option[Address])
}
val union = quote {
  aCompanies union bCompanies
}
ctx.run(union)
// SELECT x.name, x.zip, x.fk, x.street, x.zip FROM (
// (SELECT c.name name, c.zip zip, x1.zip zip, x1.fk fk, x1.street street
// FROM Company c INNER JOIN Address x1 ON x1.zip = c.zip WHERE c.name like 'A%')
// UNION
// (SELECT c1.name name, c1.zip zip, x2.zip zip, x2.fk fk, x2.street street
// FROM Company c1 LEFT JOIN Address x2 ON x2.zip = c1.zip WHERE c1.name like 'A%')
// ) x
```

### Ad-Hoc Case Classes

Case Classes can also be used inside quotations as output values:

```scala
final case class Person(id: Int, name: String, age: Int)
final case class Contact(personId: Int, phone: String)
final case class ReachablePerson(name:String, phone: String)

val q = quote {
  for {
    p <- query[Person] if(p.id == 999)
    c <- query[Contact] if(c.personId == p.id)
  } yield {
    ReachablePerson(p.name, c.phone)
  }
}

ctx.run(q)
// SELECT p.name, c.phone FROM Person p, Contact c WHERE (p.id = 999) AND (c.personId = p.id)
```

As well as in general:

```scala
final case class IdFilter(id:Int)

val q = quote {
  val idFilter = new IdFilter(999)
  for {
    p <- query[Person] if(p.id == idFilter.id)
    c <- query[Contact] if(c.personId == p.id)
  } yield {
    ReachablePerson(p.name, c.phone)
  }
}

ctx.run(q)
// SELECT p.name, c.phone FROM Person p, Contact c WHERE (p.id = 999) AND (c.personId = p.id)
```
***Note*** however that this functionality has the following restrictions:
1. The Ad-Hoc Case Class can only have one constructor with one set of parameters.
2. The Ad-Hoc Case Class must be constructed inside the quotation using one of the following methods:
    1. Using the `new` keyword: `new Person("Joe", "Bloggs")`
    2. Using a companion object's apply method:  `Person("Joe", "Bloggs")`
    3. Using a companion object's apply method explicitly: `Person.apply("Joe", "Bloggs")`
4. Any custom logic in a constructor/apply-method of an Ad-Hoc case class will not be invoked when it is 'constructed' inside a quotation. To construct an Ad-Hoc case class with custom logic inside a quotation, you can use a quoted method.

## Query probing

Query probing validates queries against the database at compile time, failing the compilation if it is not valid. The query validation does not alter the database state.

This feature is disabled by default. To enable it, mix the `QueryProbing` trait to the database configuration:

```
object myContext extends YourContextType with QueryProbing
```

The context must be created in a separate compilation unit in order to be loaded at compile time. Please use [this guide](https://www.scala-sbt.org/0.13/docs/Macro-Projects.html) that explains how to create a separate compilation unit for macros, that also serves to the purpose of defining a query-probing-capable context. `context` could be used instead of `macros` as the name of the separate compilation unit.

The configurations correspondent to the config key must be available at compile time. You can achieve it by adding this line to your project settings:

```
unmanagedClasspath in Compile += baseDirectory.value / "src" / "main" / "resources"
```

If your project doesn't have a standard layout, e.g. a play project, you should configure the path to point to the folder that contains your config file.

## Actions

Database actions are defined using quotations as well. These actions don't have a collection-like API but rather a custom DSL to express inserts, deletes, and updates.

### insertValue / insert

```scala
val a = quote(query[Contact].insertValue(lift(Contact(999, "+1510488988"))))

ctx.run(a) // = 1 if the row was inserted 0 otherwise
// INSERT INTO Contact (personId,phone) VALUES (?, ?)
```

#### It is also possible to insert specific columns (via insert):

```scala
val a = quote {
  query[Contact].insert(_.personId -> lift(999), _.phone -> lift("+1510488988"))
}

ctx.run(a)
// INSERT INTO Contact (personId,phone) VALUES (?, ?)
```

### batch insert
```scala
val a = quote {
  liftQuery(List(Person(0, "John", 31),Person(2, "name2", 32))).foreach(e => query[Person].insertValue(e))
}

ctx.run(a) //: List[Long] size = 2. Contains 1 @ positions, where row was inserted E.g List(1,1)
// INSERT INTO Person (id,name,age) VALUES (?, ?, ?)
```
> In addition to regular JDBC batching, Quill can optimize batch queries by using multiple VALUES-clauses e.g:
> ```scala
> ctx.run(a, 2)
> // INSERT INTO Person (id,name,age) VALUES (?, ?, ?), (?, ?, ?) // Note, the extract (?, ?, ?) will not be visible in the compiler output. 
> ```
> In situations with high network latency this can improve performance by 20-40x! See the [Batch Optimization](#batch-optimization) below for more info.

Just as in regular queries use the extended insert/update syntaxes to achieve finer-grained control of the data being created/modified modified.
For example, if the ID is a generated value you can skip ID insertion like this:
(This can also be accomplished with an insert-meta).
```scala
// final case class Person(id: Int, name: String, age: Int)
val a = quote {
  liftQuery(List(Person(0, "John", 31),Person(0, "name2", 32))).foreach(e => query[Person].insert(_.name -> p.name, _.age -> p.age))
}

ctx.run(a)
// INSERT INTO Person (name,age) VALUES (?, ?)
```

Batch queries can also have a returning/returningGenerated clause:
```scala
// final case class Person(id: Int, name: String, age: Int)
val a = quote {
  liftQuery(List(Person(0, "John", 31),Person(0, "name2", 32))).foreach(e => query[Person].insert(_.name -> p.name, _.age -> p.age)).returning(_.id)
}

ctx.run(a)
// INSERT INTO Person (name,age) VALUES (?, ?) RETURNING id
```

Note that the `liftQuery[Something]` and the query[Something]` values do not necessarily need to be the same object-type.
(In fact the liftQuery value can even be a constant!)
For example:
```scala
// final case class Person(name: String, age: Int)
// final case class Vip(first: String, last: String, age: Int)
// val vips: List[Vip] = ...
val q = quote {
  liftQuery(vips).foreach(v => query[Person].insertValue(Person(v.first + v.last, v.age)))
}

ctx.run(q)
// INSERT INTO Person (name,age) VALUES ((? || ?), ?)
```

Note that UPDATE queries can also be done in batches (as well as DELETE queries).
```scala
val q = quote {
  liftQuery(vips).foreach(v => query[Person].filter(p => p.age > 22).updateValue(Person(v.first + v.last, v.age)))
}

ctx.run(q)
// UPDATE Person SET name = (? || ?), age = ? WHERE age > 22
```

### updateValue / update
```scala
val a = quote {
  query[Person].filter(_.id == 999).updateValue(lift(Person(999, "John", 22)))
}

ctx.run(a) // = Long number of rows updated
// UPDATE Person SET id = ?, name = ?, age = ? WHERE id = 999
```

#### Using specific columns (via update):

```scala
val a = quote {
  query[Person].filter(p => p.id == lift(999)).update(_.age -> lift(18))
}

ctx.run(a)
// UPDATE Person SET age = ? WHERE id = ?
```

#### Using columns as part of the update:

```scala
val a = quote {
  query[Person].filter(p => p.id == lift(999)).update(p => p.age -> (p.age + 1))
}

ctx.run(a)
// UPDATE Person SET age = (age + 1) WHERE id = ?
```

### batch update

```scala
val a = quote {
  liftQuery(List(Person(1, "name", 31),Person(2, "name2", 32))).foreach { person =>
     query[Person].filter(_.id == person.id).update(_.name -> person.name, _.age -> person.age)
  }
}

ctx.run(a) // : List[Long] size = 2. Contains 1 @ positions, where row was inserted E.g List(1,0)
// UPDATE Person SET name = ?, age = ? WHERE id = ?
```

### delete
```scala
val a = quote {
  query[Person].filter(p => p.name == "").delete
}

ctx.run(a) // = Long the number of rows deleted
// DELETE FROM Person WHERE name = ''
```

### insert or update (upsert, conflict)

Upsert is supported by Postgres, SQLite, MySQL and H2 `onConflictIgnore` only (since v1.4.200 in PostgreSQL compatibility mode)

#### Postgres and SQLite

##### Ignore conflict
```scala
val a = quote {
  query[Product].insert(_.id -> 1, _.sku -> 10).onConflictIgnore
}

// INSERT INTO Product AS t (id,sku) VALUES (1, 10) ON CONFLICT DO NOTHING
```

Ignore conflict by explicitly setting conflict target
```scala
val a = quote {
  query[Product].insert(_.id -> 1, _.sku -> 10).onConflictIgnore(_.id)
}

// INSERT INTO Product AS t (id,sku) VALUES (1, 10) ON CONFLICT (id) DO NOTHING
```

Multiple properties can be used as well.
```scala
val a = quote {
  query[Product].insert(_.id -> 1, _.sku -> 10).onConflictIgnore(_.id, _.description)
}

// INSERT INTO Product (id,sku) VALUES (1, 10) ON CONFLICT (id,description) DO NOTHING
```

##### Update on Conflict

Resolve conflict by updating existing row if needed. In `onConflictUpdate(target)((t, e) => assignment)`: `target` refers to
conflict target, `t` - to existing row and `e` - to excluded, e.g. row proposed for insert.
```scala
val a = quote {
  query[Product]
    .insert(_.id -> 1, _.sku -> 10)
    .onConflictUpdate(_.id)((t, e) => t.sku -> (t.sku + e.sku))
}

// INSERT INTO Product AS t (id,sku) VALUES (1, 10) ON CONFLICT (id) DO UPDATE SET sku = (t.sku + EXCLUDED.sku)
```
Multiple properties can be used with `onConflictUpdate` as well.
```scala
val a = quote {
  query[Product]
    .insert(_.id -> 1, _.sku -> 10)
    .onConflictUpdate(_.id, _.description)((t, e) => t.sku -> (t.sku + e.sku))
}

INSERT INTO Product AS t (id,sku) VALUES (1, 10) ON CONFLICT (id,description) DO UPDATE SET sku = (t.sku + EXCLUDED.sku)
```

#### MySQL

Ignore any conflict, e.g. `insert ignore`
```scala
val a = quote {
  query[Product].insert(_.id -> 1, _.sku -> 10).onConflictIgnore
}

// INSERT IGNORE INTO Product (id,sku) VALUES (1, 10)
```

Ignore duplicate key conflict by explicitly setting it
```scala
val a = quote {
  query[Product].insert(_.id -> 1, _.sku -> 10).onConflictIgnore(_.id)
}

// INSERT INTO Product (id,sku) VALUES (1, 10) ON DUPLICATE KEY UPDATE id=id
```

Resolve duplicate key by updating existing row if needed. In `onConflictUpdate((t, e) => assignment)`: `t` refers to
existing row and `e` - to values, e.g. values proposed for insert.
```scala
val a = quote {
  query[Product]
    .insert(_.id -> 1, _.sku -> 10)
    .onConflictUpdate((t, e) => t.sku -> (t.sku + e.sku))
}

// INSERT INTO Product (id,sku) VALUES (1, 10) ON DUPLICATE KEY UPDATE sku = (sku + VALUES(sku))
```

## Batch Optimization

When doing batch INSERT queries (as well as UPDATE, and DELETE), Quill mostly delegates the functionality to standard JDBC batching.
This functionality works roughly in the following way.
```scala
val ps: PreparedStatement = connection.prepareStatement("INSERT ... VALUES ...")
// 1. Iterate over the rows
for (row <- rowsToInsert) {
  // 2. For each row, add the columns to the prepared statement
  for ((column, columnIndex) <- row)
    row.setColumn(column, columnIndex)
  // 3. Add the row to the list of things being added in the batch
  ps.addBatch()
}
// 4. Write everything in the batch to the Database
ps.executeBatch()
```
Reasonably speaking, we would expect each call in Stage #3 to locally stage the value of the row and then submit
all of the rows to the database in Stage #4 but that basically every database that is not what happens. In Stage #3,
a network call is actually made to the Database to remotely stage the row. Practically this means that the performance of
addBatch/executeBatch degrades per-row, per-millisecond-network-latency. Even at 50 milliseconds of network latency
the impact of this is highly significant:

|Network Latency | Rows Inserted | Total Time
|-------|-----------|---------|
| 0ms   | 10k rows  | 0.486   |
| 50ms  | 10k rows  | 3.226   |
| 100ms | 10k rows  | 5.266   |
| 0ms   | 100k rows | 1.416   |
| 50ms  | 100k rows | 23.248  |
| 100ms | 100k rows | 43.077  |
| 0ms   | 1m rows   | 13.616  |
| 50ms  | 1m rows   | 234.452 |
| 100ms | 1m rows   | 406.101 |

In order to alleviate this problem Quill can take advantage of the ability of most database dialects to use multiple
VALUES-clauses to batch-insert rows. Conceptually, this works in the following way:
```scala
final case class Person(name: String, age: Int)
val people = List(Person("Joe", 22), Person("Jack", 33), Person("Jill", 44))
val q = quote { liftQuery(people).foreach(p => query[Person].insertValue(p)) }
run(q, 2) // i.e. insert rows from the `people` list in batches of 2
//
// Query1) INSERT INTO Person (name, age) VALUES ([Joe] , [22]), ([Jack], [33])
//         INSERT INTO Person (name, age) VALUES (  ?   ,  ?  ), (   ?  ,  ?  ) <- actual query
// Query2) INSERT INTO Person (name, age) VALUES ([Jill], [44])
//         INSERT INTO Person (name, age) VALUES (  ?   ,  ?  )                 <- actual query
```
> Note that only `INSERT INTO Person (name, age) VALUES (?, ?)` will appear in the compiler-output for this query!

Using a batch-count of about 1000-5000 rows (i.e. `run(q, 1000)`) can significantly improve query performance:

| Network Latency | Rows Inserted | Total Time |
|-----------------|---------------|------------|
| 0ms             | 10k rows      | 3.772      |
| 50ms            | 10k rows      | 3.899      |
| 100ms           | 10k rows      | 4.63       |
| 0ms             | 100k rows     | 2.902      |
| 50ms            | 100k rows     | 3.225      |
| 100ms           | 100k rows     | 3.554      |
| 0ms             | 1m rows       | 9.923      |
| 50ms            | 1m rows       | 10.035     |
| 100ms           | 1m rows       | 10.328     |

One thing to take note of is that each one of the `?` placeholders above is a prepared-statement variable. This means
that in batch-sizes of 1000, there will be 1000 `?` variables in each query. In many databases this has a strict limit.
For example, in Postgres this is restricted to 32767. This means that when using batches of 1000 rows, each row can have
up to 32 columns or the following error will occur:
```
IOException: Tried to send an out-of-range integer as a 2-byte value
```
In other database e.g. SQL Server, unfortunately this limit is much smaller. For example in SQL Server it is just 2100 variables
or the following error will occur.
```
The server supports a maximum of 2100 parameters. Reduce the number of parameters and resend the request
```
This means that in SQL Server, for a batch-size of 100, you can only insert into a table of up to 21 columns.

In the future, we hope to alleviate this issue by directly substituting variables into `?` variables before the query is executed
however such functionality could potentially come at the risk of SQL-injection vulnerabilities.

## Printing Queries

The `translate` method is used to convert a Quill query into a string which can then be printed.

```scala
val str = ctx.translate(query[Person])
println(str)
// SELECT x.id, x.name, x.age FROM Person x
```

Insert queries can also be printed:

```scala
val str = ctx.translate(query[Person].insertValue(lift(Person(0, "Joe", 45))))
println(str)
// INSERT INTO Person (id,name,age) VALUES (0, 'Joe', 45)
```

As well as batch insertions:

```scala
val q = quote {
  liftQuery(List(Person(0, "Joe",44), Person(1, "Jack",45)))
    .foreach(e => query[Person].insertValue(e))
}
val strs: List[String] = ctx.translate(q)
strs.map(println)
// INSERT INTO Person (id, name,age) VALUES (0, 'Joe', 44)
// INSERT INTO Person (id, name,age) VALUES (1, 'Jack', 45)
```

The `translate` method is available in every Quill context as well as the Cassandra and OrientDB contexts,
the latter two, however, do not support Insert and Batch Insert query printing.

## IO Monad

Quill provides an IO monad that allows the user to express multiple computations and execute them separately. This mechanism is also known as a free monad, which provides a way of expressing computations as referentially-transparent values and isolates the unsafe IO operations into a single operation. For instance:

```scala
// this code using Future

final case class Person(id: Int, name: String, age: Int)

val p = Person(0, "John", 22)
ctx.run(query[Person].insertValue(lift(p))).flatMap { _ =>
  ctx.run(query[Person])
}

// isn't referentially transparent because if you refactor the second database
// interaction into a value, the result will be different:

val allPeople = ctx.run(query[Person])
ctx.run(query[Person].insertValue(lift(p))).flatMap { _ =>
  allPeople
}

// this happens because `ctx.run` executes the side-effect (database IO) immediately
```

```scala
// The IO monad doesn't perform IO immediately, so both computations:

val p = Person(0, "John", 22)

val a =
  ctx.runIO(query[Person].insertValue(lift(p))).flatMap { _ =>
    ctx.runIO(query[Person])
  }

val allPeople = ctx.runIO(query[Person])

val b =
  ctx.runIO(query[Person].insertValue(lift(p))).flatMap { _ =>
    allPeople
  }

// produce the same result when executed

performIO(a) == performIO(b)
```

The IO monad has an interface similar to `Future`; please refer to [the class](https://github.com/getquill/quill/blob/master/quill-core/src/main/scala/io/getquill/monad/IOMonad.scala#L39) for more information regarding the available operations.

The return type of `performIO` varies according to the context. For instance, async contexts return `Future`s while JDBC returns values synchronously.

***NOTE***: Avoid using the variable name `io` since it conflicts with Quill's package `io.getquill`, otherwise you will get the following error.
```
recursive value io needs type
```

### IO Monad and transactions

`IO` also provides the `transactional` method that delimits a transaction:

```scala
val a =
  ctx.runIO(query[Person].insertValue(lift(p))).flatMap { _ =>
    ctx.runIO(query[Person])
  }

performIO(a.transactional) // note: transactional can be used outside of `performIO`
```

### Getting a ResultSet

Quill JDBC Contexts allow you to use `prepare` in order to get a low-level `ResultSet` that is useful
for interacting with legacy APIs. This function  returns a `f: (Connection) => (PreparedStatement)`
closure as opposed to a `PreparedStatement` in order to guarantee that JDBC Exceptions are not
thrown until you can wrap them into the appropriate Exception-handling mechanism (e.g.
`try`/`catch`, `Try` etc...).

```scala
val q = quote {
  query[Product].filter(_.id == 1)
}
val preparer: (Connection) => (PreparedStatement)  = ctx.prepare(q)
// SELECT x1.id, x1.description, x1.sku FROM Product x1 WHERE x1.id = 1

// Use ugly stateful code, bracketed effects, or try-with-resources here:
var preparedStatement: PreparedStatement = _
var resultSet: ResultSet = _

try {
  preparedStatement = preparer(myCustomDataSource.getConnection)
  resultSet = preparedStatement.executeQuery()
} catch {
  case e: Exception =>
    // Close the preparedStatement and catch possible exceptions
    // Close the resultSet and catch possible exceptions
}
```

The `prepare` function can also be used with `insertValue`, and `updateValue` actions.

```scala
val q = quote {
  query[Product].insertValue(lift(Product(1, "Desc", 123))
}
val preparer: (Connection) => (PreparedStatement)  = ctx.prepare(q)
// INSERT INTO Product (id,description,sku) VALUES (?, ?, ?)
```

As well as with batch queries.
> Make sure to first quote your batch query and then pass the result into the `prepare` function
(as is done in the example below) or the Scala compiler may not type the output correctly
[#1518](https://github.com/getquill/quill/issues/1518).

```scala
val q = quote {
  liftQuery(products).foreach(e => query[Product].insertValue(e))
}
val preparers: Connection => List[PreparedStatement] = ctx.prepare(q)
val preparedStatement: List[PreparedStatement] = preparers(jdbcConf.dataSource.getConnection)
```

### Effect tracking

The IO monad tracks the effects that a computation performs in its second type parameter:

```scala
val a: IO[ctx.RunQueryResult[Person], Effect.Write with Effect.Read] =
  ctx.runIO(query[Person].insertValue(lift(p))).flatMap { _ =>
    ctx.runIO(query[Person])
  }
```

This mechanism is useful to limit the kind of operations that can be performed. See this [blog post](https://danielwestheide.com/blog/2015/06/28/put-your-writes-where-your-master-is-compile-time-restriction-of-slick-effect-types.html) as an example.

## Implicit query

Quill provides implicit conversions from case class companion objects to `query[T]` through an additional trait:

```scala
val ctx = new SqlMirrorContext(MirrorSqlDialect, Literal) with ImplicitQuery

val q = quote {
  for {
    p <- Person if(p.id == 999)
    c <- Contact if(c.personId == p.id)
  } yield {
    (p.name, c.phone)
  }
}

ctx.run(q)
// SELECT p.name, c.phone FROM Person p, Contact c WHERE (p.id = 999) AND (c.personId = p.id)
```

Note the usage of `Person` and `Contact` instead of `query[Person]` and `query[Contact]`.

## SQL-specific operations

Some operations are SQL-specific and not provided with the generic quotation mechanism. The SQL contexts provide implicit classes for this kind of operation:

```scala
val ctx = new SqlMirrorContext(MirrorSqlDialect, Literal)

```

### like

```scala
val q = quote {
  query[Person].filter(p => p.name like "%John%")
}
ctx.run(q)
// SELECT p.id, p.name, p.age FROM Person p WHERE p.name like '%John%'
```

### forUpdate

```scala
val q = quote {
  query[Person].filter(p => p.name == "Mary").forUpdate()
}
ctx.run(q)
// SELECT p.id, p.name, p.age FROM Person p WHERE p.name = 'Mary' FOR UPDATE
```

## SQL-specific encoding

### Arrays

Quill provides SQL Arrays support. In Scala we represent them as any collection that implements `Seq`:
```scala

final case class Book(id: Int, notes: List[String], pages: Vector[Int], history: Seq[Date])

ctx.run(query[Book])
// SELECT x.id, x.notes, x.pages, x.history FROM Book x
```
Note that not all drivers/databases provides such feature hence only `PostgresJdbcContext` support SQL Arrays.

## Cassandra-specific encoding

```scala
val ctx = new CassandraMirrorContext(Literal)

```

### Collections

The Cassandra context provides List, Set, and Map encoding:

```scala

final case class Book(id: Int, notes: Set[String], pages: List[Int], history: Map[Int, Boolean])

ctx.run(query[Book])
// SELECT id, notes, pages, history FROM Book
```

### User-Defined Types

The cassandra context provides encoding of UDT (user-defined types).
```scala

final case class Name(firstName: String, lastName: String) extends Udt
```

To encode the UDT and bind it into the query (insert/update queries), the context needs to retrieve UDT metadata from
the cluster object. By default, the context looks for UDT metadata within the currently logged keyspace, but it's also possible to specify a
concrete keyspace with `udtMeta`:

```scala
implicit val nameMeta = udtMeta[Name]("keyspace2.my_name")
```
When a keyspace is not set in `udtMeta` then the currently logged one is used.

Since it's possible to create a context without
specifying a keyspace, (e.g. the keyspace parameter is null and the session is not bound to any keyspace), the UDT metadata will be
resolved throughout the entire cluster.

It is also possible to rename UDT columns with `udtMeta`:

```scala
implicit val nameMeta = udtMeta[Name]("name", _.firstName -> "first", _.lastName -> "last")
```

## Cassandra-specific operations

The cassandra context also provides a few additional operations:

### allowFiltering
```scala
val q = quote {
  query[Person].filter(p => p.age > 10).allowFiltering
}
ctx.run(q)
// SELECT id, name, age FROM Person WHERE age > 10 ALLOW FILTERING
```

### ifNotExists
```scala
val q = quote {
  query[Person].insert(_.age -> 10, _.name -> "John").ifNotExists
}
ctx.run(q)
// INSERT INTO Person (age,name) VALUES (10, 'John') IF NOT EXISTS
```

### ifExists
```scala
val q = quote {
  query[Person].filter(p => p.name == "John").delete.ifExists
}
ctx.run(q)
// DELETE FROM Person WHERE name = 'John' IF EXISTS
```

### usingTimestamp
```scala
val q1 = quote {
  query[Person].insert(_.age -> 10, _.name -> "John").usingTimestamp(99)
}
ctx.run(q1)
// INSERT INTO Person (age,name) VALUES (10, 'John') USING TIMESTAMP 99

val q2 = quote {
  query[Person].usingTimestamp(99).update(_.age -> 10)
}
ctx.run(q2)
// UPDATE Person USING TIMESTAMP 99 SET age = 10
```

### usingTtl
```scala
val q1 = quote {
  query[Person].insert(_.age -> 10, _.name -> "John").usingTtl(11)
}
ctx.run(q1)
// INSERT INTO Person (age,name) VALUES (10, 'John') USING TTL 11

val q2 = quote {
  query[Person].usingTtl(11).update(_.age -> 10)
}
ctx.run(q2)
// UPDATE Person USING TTL 11 SET age = 10

val q3 = quote {
  query[Person].usingTtl(11).filter(_.name == "John").delete
}
ctx.run(q3)
// DELETE FROM Person USING TTL 11 WHERE name = 'John'
```

### using
```scala
val q1 = quote {
  query[Person].insert(_.age -> 10, _.name -> "John").using(ts = 99, ttl = 11)
}
ctx.run(q1)
// INSERT INTO Person (age,name) VALUES (10, 'John') USING TIMESTAMP 99 AND TTL 11

val q2 = quote {
  query[Person].using(ts = 99, ttl = 11).update(_.age -> 10)
}
ctx.run(q2)
// UPDATE Person USING TIMESTAMP 99 AND TTL 11 SET age = 10

val q3 = quote {
  query[Person].using(ts = 99, ttl = 11).filter(_.name == "John").delete
}
ctx.run(q3)
// DELETE FROM Person USING TIMESTAMP 99 AND TTL 11 WHERE name = 'John'
```

### ifCond
```scala
val q1 = quote {
  query[Person].update(_.age -> 10).ifCond(_.name == "John")
}
ctx.run(q1)
// UPDATE Person SET age = 10 IF name = 'John'

val q2 = quote {
  query[Person].filter(_.name == "John").delete.ifCond(_.age == 10)
}
ctx.run(q2)
// DELETE FROM Person WHERE name = 'John' IF age = 10
```

### delete column
```scala
val q = quote {
  query[Person].map(p => p.age).delete
}
ctx.run(q)
// DELETE p.age FROM Person
```

### list.contains / set.contains
requires `allowFiltering`
```scala
val q = quote {
  query[Book].filter(p => p.pages.contains(25)).allowFiltering
}
ctx.run(q)
// SELECT id, notes, pages, history FROM Book WHERE pages CONTAINS 25 ALLOW FILTERING
```

### map.contains
requires `allowFiltering`
```scala
val q = quote {
  query[Book].filter(p => p.history.contains(12)).allowFiltering
}
ctx.run(q)
// SELECT id, notes, pages, history FROM book WHERE history CONTAINS 12 ALLOW FILTERING
```

### map.containsValue
requires `allowFiltering`
```scala
val q = quote {
  query[Book].filter(p => p.history.containsValue(true)).allowFiltering
}
ctx.run(q)
// SELECT id, notes, pages, history FROM book WHERE history CONTAINS true ALLOW FILTERING
```

## Dynamic queries

Quill's default operation mode is compile-time, but there are queries that have their structure defined only at runtime. Quill automatically falls back to runtime normalization and query generation if the query's structure is not static. Example:

```scala
val ctx = new SqlMirrorContext(MirrorSqlDialect, Literal)

sealed trait QueryType
case object Minor extends QueryType
case object Senior extends QueryType

def people(t: QueryType): Quoted[Query[Person]] =
  t match {
    case Minor => quote {
      query[Person].filter(p => p.age < 18)
    }
    case Senior => quote {
      query[Person].filter(p => p.age > 65)
    }
  }

ctx.run(people(Minor))
// SELECT p.id, p.name, p.age FROM Person p WHERE p.age < 18

ctx.run(people(Senior))
// SELECT p.id, p.name, p.age FROM Person p WHERE p.age > 65
```

### Dynamic query API

Additionally, Quill provides a separate query API to facilitate the creation of dynamic queries. This API allows users to easily manipulate quoted values instead of working only with quoted transformations.

**Important**: A few of the dynamic query methods accept runtime string values. It's important to keep in mind that these methods could be a vector for SQL injection.

Let's use the `filter` transformation as an example. In the regular API, this method has no implementation since it's an abstract member of a trait:

```
def filter(f: T => Boolean): EntityQuery[T]
```

In the dynamic API, `filter` is has a different signature and a body that is executed at runtime:

```
def filter(f: Quoted[T] => Quoted[Boolean]): DynamicQuery[T] =
  transform(f, Filter)
```

It takes a `Quoted[T]` as input and produces a `Quoted[Boolean]`. The user is free to use regular scala code within the transformation:

```scala
def people(onlyMinors: Boolean) =
  dynamicQuery[Person].filter(p => if(onlyMinors) quote(p.age < 18) else quote(true))
```

In order to create a dynamic query, use one of the following methods:

```scala
dynamicQuery[Person]
dynamicQuerySchema[Person]("people", alias(_.name, "pname"))
```

It's also possible to transform a `Quoted` into a dynamic query:

```scala
val q = quote {
  query[Person]
}
q.dynamic.filter(p => quote(p.name == "John"))
```

The dynamic query API is very similar to the regular API but has a few differences:

**Queries**
```scala
// schema queries use `alias` instead of tuples
dynamicQuerySchema[Person]("people", alias(_.name, "pname"))

// this allows users to use a dynamic list of aliases
val aliases = List(alias[Person](_.name, "pname"), alias[Person](_.age, "page"))
dynamicQuerySchema[Person]("people", aliases:_*)

// a few methods have an overload with the `Opt` suffix,
// which apply the transformation only if the option is defined:

def people(minAge: Option[Int]) =
  dynamicQuery[Person].filterOpt(minAge)((person, minAge) => quote(person.age >= minAge))

def people(maxRecords: Option[Int]) =
  dynamicQuery[Person].takeOpt(maxRecords)

def people(dropFirst: Option[Int]) =
  dynamicQuery[Person].dropOpt(dropFirst)

// method with `If` suffix, for better chaining
def people(userIds: Seq[Int]) =
  dynamicQuery[Person].filterIf(userIds.nonEmpty)(person => quote(liftQuery(userIds).contains(person.id)))
```

**Actions**
```scala
// actions use `set`
dynamicQuery[Person].filter(_.id == 1).update(set(_.name, quote("John")))

// or `setValue` if the value is not quoted
dynamicQuery[Person].insert(setValue(_.name, "John"))

// or `setOpt` that will be applied only the option is defined
dynamicQuery[Person].insert(setOpt(_.name, Some("John")))

// it's also possible to use a runtime string value as the column name
dynamicQuery[Person].filter(_.id == 1).update(set("name", quote("John")))

// to insert or update a case class instance, use `insertValue`/`updateValue`
val p = Person(0, "John", 21)
dynamicQuery[Person].insertValue(p)
dynamicQuery[Person].filter(_.id == 1).updateValue(p)
```

### Dynamic query normalization cache

Quill is super fast for static queries (almost zero runtime overhead compared to directly sql executing).

But there is significant impact for dynamic queries.

Normalization caching was introduced to improve the situation, which will speedup dynamic queries significantly. It is enabled by default.

To disable dynamic normalization caching, pass following property to sbt during compile time

```
sbt -Dquill.query.cacheDynamic=false
```
