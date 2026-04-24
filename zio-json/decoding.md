# Decoding

> Say we want to be able to read some JSON like

## Automatic Derivation

Say we want to be able to read some JSON like

```json
{ "curvature": 0.5 }
```

into a Scala `case class`

```scala
case class Banana(curvature: Double)
```

To do this, we create an *instance* of the `JsonDecoder` typeclass for `Banana` using the `zio-json` code generator. It is best practice to put it on the companion of `Banana`, like so

```scala

object Banana {
  implicit val decoder: JsonDecoder[Banana] =
    DeriveJsonDecoder.gen[Banana]
}
```

Now we can parse JSON into our object

```scala
"""{ "curvature": 0.5 }""".fromJson[Banana]
// res0: Either[String, Banana] = Right(value = Banana(curvature = 0.5))
```

### Automatic Derivation and case class default field values

If a case class field is defined with a default value and the field is not present or `null`, the default value will be used (or evaluated when it is a method).

Say we have a Scala `case class`

```scala
case class Entity(id: Long, description: String = "", related: Seq[Entity] = Seq())

implicit val decoder: JsonDecoder[Entity] =
  DeriveJsonDecoder.gen[Entity]
// decoder: JsonDecoder[Entity] = zio.json.DeriveJsonDecoder$$anon$1@744ae108
```

```scala
"""{ "id": 42, "related": null }""".fromJson[Entity]
// res1: Either[String, Entity] = Right(
//   value = Entity(id = 42L, description = "", related = List())
// )
```

_Note: If you’re using Scala 3 and your case class is defining default parameters, `-Yretain-trees` needs to be added to `scalacOptions`._

## ADTs

Say we extend our data model to include more data types

```scala
sealed trait Fruit

case class Banana(curvature: Double) extends Fruit
case class Apple (poison: Boolean)   extends Fruit
```

we can generate the decoder for the entire `sealed` family:

```scala

object Fruit {
  implicit val decoder: JsonDecoder[Fruit] =
    DeriveJsonDecoder.gen[Fruit]
}
```

```scala
"""{ "Banana":{ "curvature":0.5 }}""".fromJson[Fruit]
// res3: Either[String, Fruit] = Right(value = Banana(curvature = 0.5))
```

```scala
"""{ "Apple": { "poison": false }}""".fromJson[Fruit]
// res4: Either[String, Fruit] = Right(value = Apple(poison = false))
```

### String-based union types (Enum)
The codecs support string-based union types (enums) out of the box. This is useful when the overhead of a Enum is not desired.

```scala
val appleOrBanana: "Apple" | "Banana" = "Apple"
```
Decoding succeeds because 'Apple' is a valid value
```scala
appleOrBanana.toJson
"Apple".fromJson["Apple" | "Banana"]
```
Decoding fail because 'Pear' is not a valid value
```scala
"Peer".fromJson["Apple" | "Banana"]
```

Almost all of the standard library data types are supported as fields on the case class, and it is easy to add support if one is missing.

### Sealed families and enums for Scala 3
Sealed families where all members are only objects, or a Scala 3 enum with all cases parameterless are interpreted as enumerations and will encode 1:1 with their value-names.
```scala
enum Foo derives JsonDecoder:
  case Bar
  case Baz
  case Qux
```
or
```scala
sealed trait Foo derives JsonDecoder
object Foo:
  case object Bar extends Foo
  case object Baz extends Foo
  case object Qux extends Foo
```

## Manual instances

Sometimes it is easier to reuse an existing `JsonDecoder` rather than generate a new one. This can be accomplished using convenience methods on the `JsonDecoder` typeclass to *derive* new decoders

```scala
trait JsonDecoder[A] {
  def map[B](f: A => B): JsonDecoder[B]
  def mapOrFail[B](f: A => Either[String, B]): JsonDecoder[B]
  ...
}
```

### `.map`

We can `.map` from another `JsonDecoder` in cases where the conversion will always succeed. This is very useful if we have a `case class` that simply wraps another thing and shares the same expected JSON.

For example, say we want to model the count of fruit with a `case class` to provide us with additional type safety in our business logic (this pattern is known as a *newtype*).

```scala
case class FruitCount(value: Int)
```

but this would cause us to expect JSON of the form

```json
{"value": 1}
```

wheres we really expect the raw number. We can derive a decoder from `JsonDecoder[Int]` and `.map` the result into a `FruitCount`

```scala
object FruitCount {
  implicit val decoder: JsonDecoder[FruitCount] =
    JsonDecoder[Int].map(FruitCount(_))
}
```

and now the `JsonDecoder` for `FruitCount` just expects a raw `Int`.

```scala
"""3""".fromJson[FruitCount]
// res5: Either[String, FruitCount] = Right(value = FruitCount(value = 3))
```

Another use case is if we want to encode a `case class` as an array of values, rather than an object with named fields. Such an encoding is very efficient because the messages are smaller and require less processing, but are very strict schemas that cannot be upgraded.

```scala:mdoc:reset

case class Things(s: String, i: Int, b: Boolean)

object Things {
  implicit val decoder: JsonDecoder[Things] =
    JsonDecoder[(String, Int, Boolean)].map { case (p1, p2, p3) => Things(p1, p2, p3) }
}

"""[ "hello", 1, true ]""".fromJson[Things]
```

### `.mapOrFail`

We can use `.mapOrFail` to take the result of another `JsonDecoder` and try to convert it into our custom data type, failing with a message if there is an error.

Say we are using the [`refined`](https://github.com/fthomas/refined) library to ensure that a `Person` data type only holds a non-empty string in its `name` field

```scala

case class Person(name: String Refined NonEmpty)
```

we will get a compile time error because there is no `JsonDecoder[String Refined NonEmpty]`.

```scala
object Person {
  implicit val decoder: JsonDecoder[Person] = DeriveJsonDecoder.gen
}
// error: magnolia: could not find JsonDecoder.Typeclass for type eu.timepit.refined.api.Refined[String,eu.timepit.refined.collection.NonEmpty]
//     in parameter 'name' of product type MdocApp0.this.Person
// 
//   implicit val decoder: JsonDecoder[Person] = DeriveJsonDecoder.gen
//                                               ^^^^^^^^^^^^^^^^^^^^^
```

However, we can derive one by requesting the `JsonDecoder[String]` and calling `.mapOrFail`, supplying the constructor for our special `String Refined NonEmpty` type

```scala

implicit val decodeName: JsonDecoder[String Refined NonEmpty] =
  JsonDecoder[String].mapOrFail(refined.refineV[NonEmpty](_))
// decodeName: JsonDecoder[Refined[String, NonEmpty]] = zio.json.JsonDecoder$$anon$4@44a2a185
```

Now the code compiles.

# Parsing custom JSON

In this section we show several approaches for decoding JSON that looks like:

```json
{
  "01. symbol": "IBM",
  "02. open": "182.4300",
  "03. high": "182.8000"
}
```

Which we want to decode into the following case class:

```scala
final case class Quote(
  symbol: String,
  open: String,
  high: String
)
```

All approaches have the same result:

```scala
"""{"01. symbol":"IBM","02. open": "182.4300","03. high": "182.8000"}""".fromJson[Quote]
// >> Right(Quote(IBM,182.4300,182.8000))
// error: could not find implicit value for parameter decoder: zio.json.JsonDecoder[MdocApp0.this.Quote]
// """{"01. symbol":"IBM","02. open": "182.4300","03. high": "182.8000"}""".fromJson[Quote]
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
```

## Approach 1: use annotation hints

In this approach we enrich the case class with annotations to tell the derived decoder which field names to use.
Obviously, this approach only works if we can/want to change the case class.

```scala

final case class Quote(
  @jsonField("01. symbol") symbol: String,
  @jsonField("02. open") open: String,
  @jsonField("03. high") high: String
)

object Quote {
  implicit val decoder: JsonDecoder[Quote] = DeriveJsonDecoder.gen[Quote]
}
```

## Approach 2: use an intermediate case class

Instead of hints, we can also put the actual field names in an intermediate case class. In our example the field names
are not valid scala identifiers. We fix this by putting the names in backticks:

```scala

final case class Quote(symbol: String, open: String, high: String)

object Quote {
  private final case class JsonQuote(
    `01. symbol`: String,
    `02. open`: String,
    `03. high`: String
  )

  implicit val decoder: JsonDecoder[Quote] =
    DeriveJsonDecoder
      .gen[JsonQuote]
      .map { case JsonQuote(s, o, h) => Quote(s, o, h) }
}
```

## Approach 3: decode to JSON

In this approach we first decode to the generic `Json` data structure. This approach is very flexible because it can
extract data from any valid JSON.

Note that this implementation is a bit sloppy. It uses `toString` on a JSON node. The node is not necessarily a
String, it can be of any JSON type! So this might happily process JSON that doesn't match your expectations.

```scala

final case class Quote(symbol: String, open: String, high: String)

object Quote {
  implicit val decoder: JsonDecoder[Quote] = JsonDecoder[Json]
    .mapOrFail {
      case Json.Obj(fields) =>
        def findField(name: String): Either[String, String] =
          fields
            .find(_._1 == name)
            .map(_._2.toString())  // ⚠️ .toString on any JSON type
            .toRight(left = s"Field '$name' is missing")
  
        for {
          symbol <- findField("01. symbol")
          open <- findField("02. open")
          high <- findField("03. high")
        } yield Quote(symbol, open, high)
      case _ =>
        Left("Not a JSON record")
    }
}
```

## Approach 4: decode to JSON, use cursors

Here we also first decode to `Json`, but now we use cursors to find the data we need. Here we do check that the fields
are actually strings.

```scala

final case class Quote(symbol: String, open: String, high: String)

object Quote {
  private val symbolC = JsonCursor.field("01. symbol") >>> JsonCursor.isString
  private val openC = JsonCursor.field("02. open") >>> JsonCursor.isString
  private val highC = JsonCursor.field("03. high") >>> JsonCursor.isString

  implicit val decoder: JsonDecoder[Quote] = JsonDecoder[Json]
    .mapOrFail { c =>
      for {
        symbol <- c.get(symbolC)
        open <- c.get(openC)
        high <- c.get(highC)
      } yield Quote(symbol.value, open.value, high.value)
  }
}
```

# More custom decoder examples

Let's consider an `Animal` case class with a `categories` field that should be a list of strings. However, some
producers accidentally represent the categories as a comma-separated string instead of a proper list. We want to parse
both cases.

Here's a custom decode for our Animal case class:

```scala

case class Animal(name: String, categories: List[String])

object Animal {
  private val nameC = JsonCursor.field("name") >>> JsonCursor.isString
  private val categoryArrayC = JsonCursor.field("categories") >>> JsonCursor.isArray
  private val categoryStringC = JsonCursor.field("categories") >>> JsonCursor.isString

  implicit val decoder: JsonDecoder[Animal] = JsonDecoder[Json]
    .mapOrFail { c =>
      for {
        name <- c.get(nameC).map(_.value)
        categories <- arrayCategory(c).map(_.toList)
          .orElse(c.get(categoryStringC).map(_.value.split(',').map(_.trim).toList))
      } yield Animal(name, categories)
    }

  private def arrayCategory(c: Json): Either[String, Chunk[String]] =
    c.get(categoryArrayC)
      .flatMap { arr =>
        // Get the string elements, and sequence the obtained eithers to a single either
        sequence(arr.elements.map(_.get(JsonCursor.isString).map(_.value)))
      }

  private def sequence[A, B](chunk: Chunk[Either[A, B]]): Either[A, Chunk[B]] =
    chunk.partition(_.isLeft) match {
      case (Nil, rights) => Right(rights.collect { case Right(r) => r })
      case (lefts, _) => Left(lefts.collect { case Left(l) => l }.head)
    }
}
```

And now, the Json decoder for Animal can handle both formats:
```scala
"""{"name": "Dog", "categories": "Warm-blooded, Mammal"}""".fromJson[Animal]
// res13: Either[String, Animal] = Right(
//   value = Animal(name = "Dog", categories = List("Warm-blooded", "Mammal"))
// )
// >> Right(Animal(Dog,List(Warm-blooded, Mammal)))
"""{"name": "Snake", "categories": [ "Cold-blooded", "Reptile"]}""".fromJson[Animal]
// res14: Either[String, Animal] = Right(
//   value = Animal(name = "Snake", categories = List("Cold-blooded", "Reptile"))
// )
// >>  Right(Animal(Snake,List(Cold-blooded, Reptile)))
```

# JSON AST and Cursors

In most cases it is not necessary to work with the JSON AST directly,
instead it is more convenient to decode directly to domain objects.
However, sometimes it is handy to work with a lower level representation of JSON.
This may for example be the case when you need to work with deeply nested JSON structures 
that would result in deeply nested case classes,
or when you expect a lot of variation in the JSON structure, which would result in nasty decoders.

## JSON AST

To get the AST representation of a JSON string, use the `fromJson[Json]` method.

```scala

val jsonString: String            = """{"name": "John Doe"}"""
// jsonString: String = "{\"name\": \"John Doe\"}"
val jsonAst: Either[String, Json] = jsonString.fromJson[Json]
// jsonAst: Either[String, Json] = Right(
//   value = Obj(fields = IndexedSeq(("name", Str(value = "John Doe"))))
// )
```

The `Json` type is a recursive data structure that can be navigated in a fairly straightforward way.

```scala

val jsonString: String = """{"name": "John Doe"}"""
// jsonString: String = "{\"name\": \"John Doe\"}"
val jsonAst: Json      = jsonString.fromJson[Json].toOption.get
// jsonAst: Json = Obj(fields = IndexedSeq(("name", Str(value = "John Doe"))))
jsonAst match {
  case Obj(fields: Chunk[(String, Json)]) => ()
  case Arr(elements: Chunk[Json])         => ()
  case Bool(value: Boolean)               => ()
  case Str(value: String)                 => ()
  case Num(value: java.math.BigDecimal)   => ()
  case Json.Null                          => ()
}
```

To get the `name` field, you could do the following:

```scala

val json: Option[Json] = """{"name": "John Doe"}""".fromJson[Json].toOption
// json: Option[Json] = Some(
//   value = Obj(fields = IndexedSeq(("name", Str(value = "John Doe"))))
// )
val name: Option[String] = json.flatMap { json =>
  json match {
    case Json.Obj(fields) => fields.collectFirst { case ("name", Json.Str(name)) => name }
    case _                => None
  }
}
// name: Option[String] = Some(value = "John Doe")
```

## Cursors

In practice, it is normally more convenient to use cursors to navigate the JSON AST.

```scala

val json: Either[String, Json]    = """{"name": "John Doe"}""".fromJson[Json]
// json: Either[String, Json] = Right(
//   value = Obj(fields = IndexedSeq(("name", Str(value = "John Doe"))))
// )
val cursor: JsonCursor[Json, Str] = JsonCursor.field("name").isString
// cursor: JsonCursor[Json, Str] = FilterType(
//   parent = DownField(
//     parent = FilterType(parent = Identity, jsonType = Obj),
//     name = "name"
//   ),
//   jsonType = Str
// )
val name: Either[String, String]  = json.flatMap(_.get(cursor).map(_.value))
// name: Either[String, String] = Right(value = "John Doe")
```

Cursors can be composed to navigate more complex JSON structures.

```scala

val json1: Either[String, Json] = """{"posts": [{"id": 0, "title": "foo"}]}""".fromJson[Json]
// json1: Either[String, Json] = Right(
//   value = Obj(
//     fields = IndexedSeq(
//       (
//         "posts",
//         Arr(
//           elements = IndexedSeq(
//             Obj(
//               fields = IndexedSeq(
//                 ("id", Num(value = 0)),
//                 ("title", Str(value = "foo"))
//               )
//             )
//           )
//         )
//       )
//     )
//   )
// )
val json2: Either[String, Json] = """{"userPosts": [{"id": 1, "title": "bar"}]}""".fromJson[Json]
// json2: Either[String, Json] = Right(
//   value = Obj(
//     fields = IndexedSeq(
//       (
//         "userPosts",
//         Arr(
//           elements = IndexedSeq(
//             Obj(
//               fields = IndexedSeq(
//                 ("id", Num(value = 1)),
//                 ("title", Str(value = "bar"))
//               )
//             )
//           )
//         )
//       )
//     )
//   )
// )

val commonCursor = 
  JsonCursor.isArray >>> 
    JsonCursor.element(0) >>> 
    JsonCursor.isObject >>> 
    JsonCursor.field("title") >>> 
    JsonCursor.isString
// commonCursor: JsonCursor[Json, Str] = FilterType(
//   parent = DownField(
//     parent = FilterType(
//       parent = FilterType(
//         parent = DownElement(
//           parent = FilterType(
//             parent = FilterType(parent = Identity, jsonType = Arr),
//             jsonType = Arr
//           ),
//           index = 0
//         ),
//         jsonType = Obj
//       ),
//       jsonType = Obj
//     ),
//     name = "title"
//   ),
//   jsonType = Str
// )

val cursor1 = JsonCursor.field("posts")
// cursor1: JsonCursor[Json.Obj, Json] = DownField(
//   parent = FilterType(parent = Identity, jsonType = Obj),
//   name = "posts"
// )
val cursor2 = JsonCursor.field("userPosts")
// cursor2: JsonCursor[Json.Obj, Json] = DownField(
//   parent = FilterType(parent = Identity, jsonType = Obj),
//   name = "userPosts"
// )

def getTitle(json: Either[String, Json]) =
  for {
    ast   <- json
    posts <- ast.get(cursor1).orElse(ast.get(cursor2))
    title <- posts.get(commonCursor).map(_.value)
  } yield title

val title1 = getTitle(json1)
// title1: Either[String, String] = Right(value = "foo")
val title2 = getTitle(json2)
// title2: Either[String, String] = Right(value = "bar")
```
