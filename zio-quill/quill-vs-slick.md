# Quill vs. Slick

> This document compares Quill to the [Typesafe Slick](https://scala-slick.org) library. This is an incomplete comparison, additions and corrections are welcome.

This document compares Quill to the [Typesafe Slick](https://scala-slick.org) library. This is an incomplete comparison, additions and corrections are welcome.

## Abstraction level ##

Quill and Slick have similar abstraction levels. They represent database rows as flat immutable structures (case classes without nested data) and provide a type-safe composable query DSL.

Slick's documentation refers to this abstraction level as a [new paradigm called functional-relational mapping (FRM)](https://github.com/slick/slick/blob/3b3bd36c93c6d9c63b0471ff4d8409f913954b2b/slick/src/sphinx/introduction.rst#functional-relational-mapping). In fact, the approach is not new and was introduced in the late '90s by ["Kleisli􏰂, a Functional Query System"](https://www.comp.nus.edu.sg/~wongls/psZ/wls-jfp98-3.ps). It was also used by the [Links programming language](https://web.archive.org/web/20120127183323/https://groups.inf.ed.ac.uk/links/papers/links-fmco06.pdf), and later on was popularized by [Microsoft LINQ](https://msdn.microsoft.com/en-us/library/bb425822.aspx) in a less functional manner.

Quill is referred as a Language Integrated Query library to match the available publications on the subject. The paper ["Language-integrated query using comprehension syntax: state of the art, open problems, and work in progress"](http://research.microsoft.com/en-us/events/dcp2014/cheney.pdf) has an overview with some of the available implementations of language integrated queries.

## QDSL versus EDSL ##

Quill's DSL is a macro-based quotation mechanism, allowing usage of Scala types and operators directly. Please refer to the paper ["Everything old is new again: Quoted Domain Specific Languages"](https://homepages.inf.ed.ac.uk/wadler/papers/qdsl/qdsl.pdf) for more details. On the other hand, Slick provides a DSL that requires lifting of types and operations to the DSL counterparts at runtime. Example:

**quill**
```scala
case class Person(name: String, age: Int)

val range = quote {
  (a: Int, b: Int) =>
    for {
      u <- query[Person] if (a <= u.age && u.age < b)
    } yield {
      u
    }
}
val ageFromName = quote {
  (s: String) =>
    for {
      u <- query[Person] if (s == u.name)
    } yield {
      u.age
    }
}
val q = quote {
  (s: String, t: String) =>
    for {
      a <- ageFromName(s)
      b <- ageFromName(t)
      r <- range(a, b)
    } yield {
      r
    }
}
```

**slick**
```scala
case class PersonRow(name: String, age: Int)

class Person(_tableTag: Tag) extends Table[PersonRow](_tableTag, "Person") {
  def * = (name, age) <> (PersonRow.tupled, PersonRow.unapply)
  def ? = (Rep.Some(name), Rep.Some(age)).shaped.<>({r=>import r._; _1.map(_=> PersonRow.tupled((_1.get, _2.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

  val name: Rep[String] = column[String]("name", O.Length(255,varying=true))
  val age: Rep[Int] = column[Int]("age")
}

lazy val Person = new TableQuery(tag => new Person(tag))

val range =
  (a: Rep[Int], b: Rep[Int]) =>
    for {
      u <- Person if (a <= u.age && u.age < b)
    } yield {
      u
    }
val ageFromName =
  (s: Rep[String]) =>
    for {
      u <- Person if (s === u.name)
    } yield {
      u.age
    }
val q = 
  (s: String, t: String) =>
    for {
      a <- ageFromName(s)
      b <- ageFromName(t)
      r <- range(a, b)
    } yield {
      r
    }
```

Slick requires explicit type definition to map the database model to lifted values, which can be automatically generated and maintained by the [`slick-codegen`](https://scala-slick.org/doc/3.1.0/code-generation.html) tool. The query definition also requires special equality operators and usage of `Rep` for composable queries.

## Compile-time versus Runtime ##

Quill's quoted DSL opens a new path to query generation. For the quotations that are known statically, the query normalization and translation to SQL happen at compile-time. The user receives feedback during compilation, knows the SQL string that will be used and if it will succeed when executed against the database.

The feedback cycle using Slick is typically longer. Some factors like normalization bugs and unsupported operations can make the query fail, but only at runtime it is possible to know whether they will affect the query or not.

## Non-blocking IO ##

Slick provides an asynchronous wrapper on top of jdbc's blocking interface, making it harder to scale applications using it. On the other hand, quill provides fully asynchronous non-blocking database access through quill-zio.

## Extensibility ##

It is common to have to write plain SQL statements when a feature is not supported by Slick. Quill's [`infix` mechanism](https://github.com/getquill/quill#infix) solves this problem by allowing the user to insert arbitrary SQL within quotations at any position.

## Normalization ##

Quill's normalization engine is based on the rules introduced by the paper ["A practical theory of language-integrated query"](https://www.infoq.com/presentations/theory-language-integrated-query). They ensure that, given some fulfilled requirements, the normalization will always succeed. Quill verifies these requirements at compile-time.

Unfortunately, the paper doesn't cover all SQL features supported by Quill. Some additional transformations were added to the normalization engine for this reason.

Slick's normalization is based on an multi-phase compilation engine. The code complexity is very high, probably due to the lack of principled normalization rules.

The last stable version (3.1) features a major rewrite of the query compiler. Before it, even simple compositions used to produce highly nested queries with bad performance characteristics when executed against MySQL.

The reader is invited to compare the libraries' normalization code:

https://github.com/getquill/quill/tree/master/quill-core/src/main/scala/io/getquill/norm
https://github.com/slick/slick/tree/master/slick/src/main/scala/slick/compiler
