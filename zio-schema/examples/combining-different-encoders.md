# Combining Different Encoders

> Let's take a look at a round-trip converting an object to JSON and back, then converting it to a protobuf and back. This is a simple example, but it shows how to combine different encoders to achieve a round-trip.

Let's take a look at a round-trip converting an object to JSON and back, then converting it to a protobuf and back. This is a simple example, but it shows how to combine different encoders to achieve a round-trip.

```scala
object CombiningExample extends zio.App {

  override def run(args: List[String]): UIO[ExitCode] = for {
    _ <- ZIO.unit
    _ <- ZIO.debug("combining roundtrip")
    person = Person("Michelle", 32)

    personToJson = JsonCodec.encoder[Person](schemaPerson)
    jsonToPerson = JsonCodec.decoder[Person](schemaPerson)

    personToProto = ProtobufCodec.encoder[Person](schemaPerson)
    protoToPerson = ProtobufCodec.decoder[Person](schemaPerson)

    newPerson <- ZStream(person)
      .tap(v => ZIO.debug("input object is: " + v))
      .transduce(personToJson)
      .transduce(jsonToPerson)
      .tap(v => ZIO.debug("object after json roundtrip: " + v))
      .transduce(personToProto)
      .transduce(protoToPerson)
      .tap(v => ZIO.debug("person after protobuf roundtrip: " + v))
      .runHead
      .some
      .catchAll(error => ZIO.debug(error))
    _ <- ZIO.debug("is old person the new person? " + (person == newPerson).toString)
    _ <- ZIO.debug("old person: " + person)
    _ <- ZIO.debug("new person: " + newPerson)
  } yield ExitCode.success
}
```
