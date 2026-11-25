# Tutorial: Store and Load a Case Class with MongoDB

> In this tutorial, we will define a case class, derive a BSON codec for it, and see the exact document that MongoDB would store. Then we will load that document back into a Scala value. At the end, we will connect the codec to the MongoDB driver.

## What You Will Build

By the end of this tutorial, you will know how to derive a codec for your own types, how the fields of a case class become BSON keys, and how to check that a stored document reads back correctly. You do not need a MongoDB server to follow the steps. The driver step is shown as text, because it needs a running server.

## Step 1: Add the Dependencies

Add the two zio-bson modules to your `build.sbt` file. `zio-bson` holds the encoders, decoders and codecs. `zio-bson-magnolia` derives codecs for case classes automatically:

```scala
libraryDependencies += "dev.zio" %% "zio-bson" % "1.0.12"
libraryDependencies += "dev.zio" %% "zio-bson-magnolia" % "1.0.12"
```

The MongoDB driver is a separate library. You add it when you connect to a server, in Step 6.

## Step 2: Define a Case Class and Its Codec

We will store books. Each book has a title, a page count, and an optional subtitle. We need imports for the zio-bson types, the BSON document builders, and the derivation module:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
import zio.bson.magnolia._

final case class Book(title: String, pages: Int, subtitle: Option[String])
```

To derive the codec, we put a `DeriveBsonCodec.derive` call in the companion object of `Book`. The compiler finds this codec wherever a `BsonEncoder[Book]` or `BsonDecoder[Book]` is needed:

```scala
object Book {
  implicit val codec: BsonCodec[Book] = DeriveBsonCodec.derive
}
```

`DeriveBsonCodec.derive` reads the fields of `Book` and builds the encoder and decoder from them. We did not write any mapping code, and we will see the result in the next step.

## Step 3: Encode a Book to a BSON Document

When the driver stores a value, it first converts the value to BSON. We can do the same conversion ourselves with `toBsonValue`. We will create a book without a subtitle:

```scala
val dune = Book("Dune", 412, None)
// dune: Book = Book(title = "Dune", pages = 412, subtitle = None)

println(dune.toBsonValue)
// {"title": "Dune", "pages": 412}
```

The output has two keys, `title` and `pages`. The `subtitle` field is missing, because it is `None` and the default configuration leaves out empty optional fields. The key names match the field names of `Book`.

Now we create a book with a subtitle, and see that the key appears:

```scala
val hobbit = Book("The Hobbit", 310, Some("There and Back Again"))
// hobbit: Book = Book(
//   title = "The Hobbit",
//   pages = 310,
//   subtitle = Some(value = "There and Back Again")
// )

println(hobbit.toBsonValue)
// {"title": "The Hobbit", "pages": 310, "subtitle": "There and Back Again"}
```

## Step 4: Load a Document Back into a Book

Loading is the reverse of storing. The `as` method reads a `BsonValue` with the decoder for `Book`, and returns either the value or an error message:

```scala
println(hobbit.toBsonValue.as[Book])
// Right(Book(The Hobbit,310,Some(There and Back Again)))
```

The result is `Right` with a `Book` that equals the original. Now we read a document that is missing the `pages` field, which is required:

```scala
println(doc("title" -> str("Emma")).as[Book])
// Left(.pages: missing)
```

The error names the missing field with a leading dot, `.pages`, so we can see what to fix in the stored document.

## Step 5: Check the Round Trip

A good habit is to check that a value survives a round trip: encode it, decode it, and compare the result with the original. We will check both books from Step 3:

```scala
val roundTrip = List(dune, hobbit).map(b => b.toBsonValue.as[Book])
// roundTrip: List[Either[String, Book]] = List(
//   Right(value = Book(title = "Dune", pages = 412, subtitle = None)),
//   Right(
//     value = Book(
//       title = "The Hobbit",
//       pages = 310,
//       subtitle = Some(value = "There and Back Again")
//     )
//   )
// )

println(roundTrip)
// List(Right(Book(Dune,412,None)), Right(Book(The Hobbit,310,Some(There and Back Again))))
println(roundTrip == List(Right(dune), Right(hobbit)))
// true
```

The second line prints `true`, so both books came back unchanged.

## Step 6: Connect the Codec to MongoDB

The MongoDB driver stores values through a `CodecRegistry`. A registry holds a `CodecProvider` for each type. The provider returned by `zioBsonCodecProvider` wraps the `Book` codec:

```scala
import org.bson.codecs.configuration.CodecRegistries
import org.bson.conversions.Bson

val registry = CodecRegistries.fromRegistries(
  CodecRegistries.fromProviders(zioBsonCodecProvider[Book]),
  Bson.DEFAULT_CODEC_REGISTRY
)
```

With the registry in the driver's client settings, `insertOne` and `find` work on `Book` values. The driver writes the same document that we printed in Step 3. The driver code needs a running MongoDB server and the synchronous driver library, so it is shown as text:

```scala
val settings = MongoClientSettings.builder()
  .applyConnectionString(new ConnectionString("mongodb://localhost:27017"))
  .codecRegistry(registry)
  .build()

val collection = MongoClients.create(settings)
  .getDatabase("library")
  .getCollection("books", classOf[Book])

collection.insertOne(dune)
println(collection.find().first())
```

The [MongoDB driver how-to](./how-to-use-with-mongodb-driver.md) explains the registry in more detail and shows the same encode and decode steps that run without a server.

## Putting It Together

The complete example below holds the model, the codec, and the checks from Steps 2 to 5 in one place:

```scala
final case class Shelf(name: String, books: List[Book])

object Shelf {
  implicit val codec: BsonCodec[Shelf] = DeriveBsonCodec.derive
}

val shelf = Shelf("Science Fiction", List(dune, hobbit))
// shelf: Shelf = Shelf(
//   name = "Science Fiction",
//   books = List(
//     Book(title = "Dune", pages = 412, subtitle = None),
//     Book(
//       title = "The Hobbit",
//       pages = 310,
//       subtitle = Some(value = "There and Back Again")
//     )
//   )
// )

val stored = shelf.toBsonValue
// stored: org.bson.BsonValue = {"name": "Science Fiction", "books": [{"title": "Dune", "pages": 412}, {"title": "The Hobbit", "pages": 310, "subtitle": "There and Back Again"}]}
println(stored)
// {"name": "Science Fiction", "books": [{"title": "Dune", "pages": 412}, {"title": "The Hobbit", "pages": 310, "subtitle": "There and Back Again"}]}
println(stored.as[Shelf] == Right(shelf))
// true
```

We derived a codec for `Shelf`, which holds a list of `Book` values. The codec for `Book` is used for each element, so no extra code is needed.

## Next Steps

- [Customize Field Names and Discriminators](./how-to-customize-field-names-and-discriminators.md) changes the keys in the stored document.
- [Write a Codec by Hand](./how-to-write-a-codec-by-hand.md) covers types that derivation cannot handle.
- [BsonCodecConfiguration](./bson-codec-configuration.md) lists every setting that controls derivation.
