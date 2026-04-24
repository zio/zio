# AttributeValue

> The sealed trait `AttributeValue` has a one to one correspondence with the concept of an [attribute value in the AWS DDB API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_AttributeValue.html). It has implementations for all the types that are supported by DynamoDB

The sealed trait `AttributeValue` has a one to one correspondence with the concept of an [attribute value in the AWS DDB API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_AttributeValue.html). It has implementations for all the types that are supported by DynamoDB

Internally there are the type classes `ToAttributeValue` and `FromAttribute` to convert between Scala types and `AttributeValue` and vice versa
whenever these conversions are required. Some example constructors are show below:

```scala
val s = AttributeValue.String("hello")
val bool = AttributeValue.Boolean(true)
```

However, even when working with the Low Level API you would not use these constructors directly, instead you would use the `AttrMap` class (or more likely one of it's type aliases) which is a convenience container for working with `AttributeValue` instances (see next section).

# AttrMap
An `AttrMap` is a convenience container for working an [DDB Item](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/WorkingWithItems.html) and _cuts down on boilerplate code when working with the Low Level API_. Conceptually it is a map of field name to AttributeValue `Map[String, AttributeValue]`.
However rather than requiring you to create an `AttributeValue` instance manually for each field, you can work with literal Scala types and type classes will handle the conversion to `AttributeValue` for you. There are also the the `PrimaryKey` and `Item` type aliases for `AttrMap` for readability as these structures follow the same pattern.

Some examples are shown below:

```scala
val attrMap = AttrMap("id" -> "1", "age" -> 30) 
val item = Item("id" -> "1", "age" -> 30) // uses Item type alias
val pk = PrimaryKey("id" -> "1", "count" -> 30) // uses PrimaryKey type alias 
// AttrMaps can also be nested 
val item = Item("id" -> "1", "age" -> 30, "address" -> Item("city" -> "London", "postcode" -> "SW1A 1AA")) 
```

The example below demonstrate the reduction in boilerplate when compared to working with `AttributeValue` directly:

```scala
val attrMap1 = AttrMap("id" -> "1", "age" -> 30) 
val attrMap2 = Map("id" -> AttributeValue.String("1"), "age" -> AttributeValue.Number(30))
```

There are also some convenience methods on the `AttrMap` class for accessing fields:

```scala

  // Field accessors

  def get[A](field: String)(implicit ev: FromAttributeValue[A]): Either[ItemError, A] = ???

  def getOptional[A](field: String)(implicit ev: FromAttributeValue[A]): Either[Nothing, Option[A]] = ???

  def getItem[A](field: String)(f: AttrMap => Either[ItemError, A]): Either[ItemError, A] = ???

  // methods for accessing fields that are Item's themselves

  def getOptionalItem[A](
    field: String
  )(f: AttrMap => Either[ItemError, A]): Either[ItemError, Option[A]] = ???

  def getIterableItem[A](
    field: String
  )(f: AttrMap => Either[ItemError, A]): Either[ItemError, Iterable[A]] = ???

  def getOptionalIterableItem[A](
    field: String
  )(f: AttrMap => Either[ItemError, A]): Either[ItemError, Option[Iterable[A]]] = ???
```

An example of using AttrMap access methods is shown below:

```scala
val attrMap                                = AttrMap("f1" -> AttrMap("f2" -> "a", "f3" -> "b"))
val either: Either[ItemError, Option[Foo]] = for {
  maybeFoo <- attrMap.getOptionalItem("f1") { m =>
           for {
             s <- m.get[String]("f2")
             o <- m.getOptional[String]("f3")
           } yield Foo(s, o)
         }
  } yield maybeFoo
```
