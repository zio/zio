# Use zio-bson with the MongoDB Driver

> The MongoDB Java driver, which the Scala driver also uses, converts Scala and Java objects to and from BSON through a `CodecRegistry`. A registry is a list of `CodecProvider`s. Each provider says whether it can convert a given class. You want the driver to use your zio-bson codecs for your own types, so that `insertOne` and `find` work on case classes without any extra mapping.

## Problem

## Create a CodecProvider

`zioBsonCodecProvider` returns a `CodecProvider` for one type. It uses the codec in scope for that type, which is usually the one from `DeriveBsonCodec.derive`:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
import zio.bson.magnolia._

final case class Order(orderId: String, totalCents: Long)

object Order {
  implicit val codec: BsonCodec[Order] = DeriveBsonCodec.derive
}

val orderProvider = zioBsonCodecProvider[Order]
```

The provider is a plain value, so it can be listed with other providers when you build a registry.

## Build a Registry

A registry combines providers. The `bson` library's `CodecRegistries` builds one from a list, and `Bson.DEFAULT_CODEC_REGISTRY` already knows the standard types, such as `String` and `BsonDocument`. Put the zio-bson provider first, so it is checked before the defaults:

```scala
import org.bson.codecs.configuration.CodecRegistries
import org.bson.conversions.Bson

val registry = CodecRegistries.fromRegistries(
  CodecRegistries.fromProviders(orderProvider),
  Bson.DEFAULT_CODEC_REGISTRY
)
```

The MongoDB driver takes this kind of registry in its client settings. With the synchronous driver, the setup looks like this:

```scala
val settings = MongoClientSettings.builder()
  .applyConnectionString(new ConnectionString("mongodb://localhost:27017"))
  .codecRegistry(registry)
  .build()

val client     = MongoClients.create(settings)
val collection = client.getDatabase("shop").getCollection("orders", classOf[Order])

collection.insertOne(Order("A-1", 1250L))
```

The driver calls the codec for `Order`, which is the zio-bson codec. That snippet needs a running MongoDB server and the driver library, which this documentation build does not include, so it is shown as plain text. The rest of this guide runs without a server, using the same registry the driver uses.

## Encode and Decode Through the Registry

To check the registry, look up the codec for `Order` and write a value with it. The `BsonDocumentWriter` writes into an in-memory `BsonDocument`, which is the same kind of object the driver sends to the server:

```scala
import org.bson.{BsonDocument, BsonDocumentWriter}
import org.bson.codecs.EncoderContext

val orderCodec = registry.get(classOf[Order])
```

```scala
val stored = new BsonDocument()
// stored: BsonDocument = {"orderId": "A-1", "totalCents": 1250}
orderCodec.encode(new BsonDocumentWriter(stored), Order("A-1", 1250L), EncoderContext.builder().build())
println(stored)
// {"orderId": "A-1", "totalCents": 1250}
```

The reverse direction reads the document back into an `Order`:

```scala
import org.bson.BsonDocumentReader
import org.bson.codecs.DecoderContext

val loaded = orderCodec.decode(new BsonDocumentReader(stored), DecoderContext.builder().build())
// loaded: Order = Order(orderId = "A-1", totalCents = 1250L)
println(loaded)
// Order(A-1,1250)
```

## Check the Type You Asked For

The provider matches the class it was built for, and classes that extend it. Any other class returns `null` from the registry, so the driver falls back to its own codecs for those types. This is why the zio-bson provider goes first in the list. A type that has no provider and no default codec fails at lookup, so give every type you store a provider.

## Related Pages

- [BsonCodec](./bson-codec.md) describes the codec that the provider wraps.
- [BsonCodecConfiguration](./bson-codec-configuration.md) controls how the derived codec writes fields.
- [Tutorial: Store and Load a Case Class with MongoDB](./tutorial-store-and-load.md) puts these steps in order.
