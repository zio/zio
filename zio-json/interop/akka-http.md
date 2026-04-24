# Akka Http Interop

> An interop module is provided for [Akka Http 10.2.4](https://doc.akka.io/docs/akka-http/10.2.4/index.html)

An interop module is provided for [Akka Http 10.2.4](https://doc.akka.io/docs/akka-http/10.2.4/index.html)

## Installation

```scala
libraryDependencies ++= Seq(
  "de.heikoseeberger" %% "akka-http-zio-json" % "1.36.0",
)
```

## Usage

```scala

final case class Greeting(greeting: String)

object Greeting {

  implicit val decoder: JsonDecoder[Greeting] =
    DeriveJsonDecoder.gen[Greeting]

  implicit val encoder: JsonEncoder[Greeting] =
    DeriveJsonEncoder.gen[Greeting]

  //    automatically derived from the zio-json codecs

  //    implicit val akkaMarshaller: ToEntityMarshaller[Greeting] =
  //      implicitly[ToEntityMarshaller[Greeting]]
  //
  //    implicit val akkaUnmarshaller: FromEntityUnmarshaller[Greeting] =
  //      implicitly[FromEntityUnmarshaller[Greeting]]

  implicit val system = ActorSystem()

  // under the hood, Akka Http uses marshallers and unmarshallers for Json http entities

  def marshallGreeting(greeting: Greeting): Task[HttpEntity] =
    ZIO.fromFuture(implicit ec => Marshal(greeting).to[HttpEntity])

  def unmarshallGreeting(entity: HttpEntity): Task[Greeting] =
    ZIO.fromFuture(implicit ec => Unmarshal(entity).to[Greeting])
}
```

## Example project

A fully working example [can be found here](https://github.com/hseeberger/akka-http-json/blob/master/akka-http-zio-json/src/test/scala/de/heikoseeberger/akkahttpziojson/ExampleApp.scala).
