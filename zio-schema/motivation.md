# The Motivation Behind ZIO Schema

> ZIO Schema is a library used in many ZIO projects such as _ZIO Flow_, _ZIO Redis_, _ZIO SQL_ and _ZIO DynamoDB_. It is all about reification of our types. Reification means turning something abstract, such as a side effect, a field access, or the structure of a type, into something "real": a value.

## Reification: Functional Effects

In functional effects, we reify by turning side-effects into values. For example, we might have a simple statement like this:

```scala
println("Hello")
println("World")
```

In ZIO, we reify these statements into values:

```scala
import zio._

val effect1 = ZIO.attempt(println("Hello"))
val effect2 = ZIO.attempt(println("World"))
```

Because the effects are values, we can combine them with operators, for example to run both in parallel and retry them on failure:

```scala
import zio._

val both = (ZIO.attempt(println("Hello")) zipPar ZIO.attempt(println("World"))).retryN(100)
```

## Reification: Optics

In Scala, we have product types like this case class of a `Person`:

```scala
final case class Person(name: String, age: Int)
```

This case class has two fields: `name` of type `String` and `age` of type `Int`.

The Scala language provides special support to access the fields inside case classes using the dot syntax:

```scala
val person = Person("Michelle", 32)
val name = person.name
val age  = person.age
```

However, field access is a special language feature. It is not a value, unlike the reified effects in the previous section (`println(...)` vs. `ZIO.attempt(println(...))`).

Because field access is not a value, we cannot pass it around or build operators on it. For example, we cannot combine two field accesses into one that reaches a field inside a nested structure.

The solution to this kind of problem is called an "optic". Optics provide a way to access the fields of a case class and nested structures. There are three main types of optics:

- `Lens`: A lens focuses on one field of a product type, such as a case class.
- `Prism`: A prism focuses on one case of a sum type, such as one subtype of a sealed trait.
- `Traversal`: A traversal focuses on all the elements of a collection.

Optics take something that is not a first-class **concept**, drilling down into a field of a case class or into a nested structure, and turn it into a first-class **value**.

Once we have a value, we can compose optics to solve hard problems in functional programming, such as updating a field deep inside nested case classes without a chain of `copy` calls, or updating every element of a collection inside a nested structure.

For more information on optics, refer to the [ZIO Optics](https://zio.dev/zio-optics/) documentation. ZIO Schema can derive these optics from a schema, as [Optics Derivation](derivations/optics-derivation.md) shows.

## Reification: Schema

The previous sections reify side effects into values (ZIO), and reify accessing and modifying fields inside case classes or arbitrary structures into values as well (optics).

**ZIO Schema** is now about how to **describe entire data structures using values**.

The built-in way to describe data structures in Scala is case classes and classes.

For example, assume we have the `Person` data type, like this:

```scala
final case class Person(name: String, age: Int)
```

It has the following information:

- Name of the structure: `Person`
- Fields: `name` and `age`
- Type of the fields: `String` and `Int`
- Type of the structure: `Person`

ZIO Schema reifies the structure of data types by turning this information into values. It does so not only for case classes, but also for other types such as collections, tuples, and enumerations. [Automatic Schema Derivation](automatic-schema-derivation.md) shows how to get such a value for your own types.
