# Integration with ZIO Streams

> In addition to the regular `encode` and `decode` functions, each codec also has a streaming version of these functions called `streamEncoder` and `streamDecoder`. By invoking these methods on codecs, we can obtain a `ZPipeline` where the encoder and decoder are integrated into the `ZPipeline` stream transformer.

In addition to the regular `encode` and `decode` functions, each codec also has a streaming version of these functions called `streamEncoder` and `streamDecoder`. By invoking these methods on codecs, we can obtain a `ZPipeline` where the encoder and decoder are integrated into the `ZPipeline` stream transformer.

We can use the `ZPipline` to transform (encode/decode) a stream of values of type `A` into a stream of values of type `B`. 

For example, assume we have a stream of `Person` values, and we want to encode them into a stream of bytes and then convert back to `Person` values. We can do this as follows:

```scala

object Main extends ZIOAppDefault {
  case class Person(name: String, age: Int)

  object Person {
    implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
  }

  def run: ZIO[Any, Exception, Unit] =
    ZStream
      .fromIterable(Seq(Person("John", 42)))
      .debug("the input object is")
      .via(JsonCodec.schemaBasedBinaryCodec[Person].streamEncoder)
      .via(ZPipeline.utfDecode)
      .debug("json string of person")
      .via(ZPipeline.utf8Encode)
      .via(JsonCodec.schemaBasedBinaryCodec[Person].streamDecoder)
      .debug("person after roundtrip")
      .runDrain
}
```
