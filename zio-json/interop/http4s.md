# HTTP4s Interop

> Use ZIO JSON codecs as http4s 0.23 entity encoders and decoders: jsonOf, jsonEncoderOf, and automatic instances through ZIOEntityCodec.

The `zio-json-interop-http4s` module turns ZIO JSON codecs into [http4s](https://http4s.org/v0.23) entity codecs. An http4s `EntityDecoder[F, A]` reads a request or response body into an `A`, and an `EntityEncoder[F, A]` writes an `A` as a body. With this module, any type that has a `JsonDecoder` or `JsonEncoder` can be read from or written to an HTTP body as JSON.

The module targets http4s 0.23 and cats-effect 3 (the build uses http4s 0.23.38 and cats-effect 3.7.0). It is published for the JVM only, for Scala 2.12, 2.13 and 3.

## Installation

Add the module to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-json-interop-http4s" % "1.1.3"
```

The module depends on `http4s-dsl`, `cats-effect` and `zio`, so those come in transitively. There is no Scala.js or Scala Native artifact, so always use `%%`.

## What the Module Provides

The module has two styles. You either create the instances you need with `jsonOf` and `jsonEncoderOf`, or you import implicit instances that cover every type with a ZIO JSON codec:

| Member                              | Result                         | Requires                          | Purpose                                                             |
| ----------------------------------- | ------------------------------ | --------------------------------- | ------------------------------------------------------------------- |
| `jsonOf[F, A]`                      | `EntityDecoder[F, A]`          | `Concurrent[F]`, `JsonDecoder[A]` | Reads the body as UTF-8 text and decodes it with the `JsonDecoder`. |
| `jsonEncoderOf[F, A]`               | `EntityEncoder[F, A]`          | `JsonEncoder[A]`                  | Writes `A` with `toJson` and sets `Content-Type: application/json`. |
| `ZIOEntityDecoder.zioEntityDecoder` | implicit `EntityDecoder[F, A]` | `Concurrent[F]`, `JsonDecoder[A]` | `jsonOf[F, A]` for every `A` with a `JsonDecoder`.                  |
| `ZIOEntityEncoder.zioEntityEncoder` | implicit `EntityEncoder[F, A]` | `JsonEncoder[A]`                  | `jsonEncoderOf[F, A]` for every `A` with a `JsonEncoder`.           |
| `ZIOEntityCodec`                    | object                         |                                   | Both implicits above in one import.                                 |

`jsonOf` and `jsonEncoderOf` live in the `ZIOJsonInstances` trait. The package object `zio.json.interop.http4s` extends that trait, which is why `import zio.json.interop.http4s._` makes them available.

## Explicit Instances

The explicit style gives you full control over which types get JSON entity codecs. We define the payload types with ZIO JSON codecs, then declare one `EntityDecoder` for the request type and one `EntityEncoder` for the response type:

```scala
import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import zio.json._
import zio.json.interop.http4s._

final case class Greeting(name: String)

object Greeting {
  implicit val codec: JsonCodec[Greeting] = DeriveJsonCodec.gen[Greeting]

  implicit val entityDecoder: EntityDecoder[IO, Greeting] = jsonOf[IO, Greeting]
}

final case class Reply(message: String)

object Reply {
  implicit val codec: JsonCodec[Reply] = DeriveJsonCodec.gen[Reply]

  implicit val entityEncoder: EntityEncoder[IO, Reply] = jsonEncoderOf[IO, Reply]
}
```

The instances sit in the companion objects, so http4s finds them wherever `Greeting` and `Reply` are used. A route can now decode the body with `Request#as` and pass a `Reply` straight to `Ok`:

```scala
val routes: HttpRoutes[IO] = HttpRoutes.of[IO] { case req @ POST -> Root / "greet" =>
  for {
    greeting <- req.as[Greeting]
    response <- Ok(Reply(s"Hello, ${greeting.name}!"))
  } yield response
}
```

## Running the Routes in Memory

http4s routes are plain functions from a request to an effect, so we can call them without starting a server. This helper builds a `POST` request with a JSON body:

```scala
import cats.effect.unsafe.implicits.global

def jsonRequest(body: String): Request[IO] =
  Request[IO](Method.POST, uri"/greet")
    .withEntity(body)
    .withContentType(`Content-Type`(MediaType.application.json))
```

Sending a valid body runs the route and gives back the response:

```scala
val response = routes.orNotFound.run(jsonRequest("""{"name":"Ada"}""")).unsafeRunSync()
```

The response has the status `200 OK`, the JSON content type and a JSON body:

```scala
println(response.status)
// 200 OK
println(response.headers.get[`Content-Type`])
// Some(Content-Type(MediaType(application/json),None))
println(response.bodyText.compile.string.unsafeRunSync())
// {"message":"Hello, Ada!"}
```

## Decoding Errors

`jsonOf` turns every decoding problem into an http4s `MalformedMessageBodyFailure`. An empty body fails with the message `Invalid JSON: empty body`, and a ZIO JSON decoding error is passed on as the failure's message. `Request#attemptAs` shows the failure as a value:

```scala
jsonRequest("").attemptAs[Greeting].value.unsafeRunSync()
// res3: Either[DecodeFailure, Greeting] = Left(
//   value = MalformedMessageBodyFailure(
//     details = "Invalid JSON: empty body",
//     cause = None
//   )
// )

jsonRequest("""{"nom":"Ada"}""").attemptAs[Greeting].value.unsafeRunSync()
// res4: Either[DecodeFailure, Greeting] = Left(
//   value = MalformedMessageBodyFailure(details = ".name(missing)", cause = None)
// )

jsonRequest("""{"name":"Ada"""").attemptAs[Greeting].value.unsafeRunSync()
// res5: Either[DecodeFailure, Greeting] = Left(
//   value = MalformedMessageBodyFailure(
//     details = "Unexpected end of input",
//     cause = None
//   )
// )
```

Inside a route, `Request#as` raises the same failure in `IO`. `MalformedMessageBodyFailure` is an http4s `MessageFailure`, and the http4s server backends turn an unhandled `MessageFailure` into an error response. For this failure the response is `400 Bad Request`:

```scala
jsonRequest("""{"nom":"Ada"}""")
  .attemptAs[Greeting]
  .value
  .unsafeRunSync()
  .left
  .map(_.toHttpResponse[IO](HttpVersion.`HTTP/1.1`).status)
// res6: Either[Status, Greeting] = Left(value = Status(code = 400))
```

The decoder declares `application/json` as its media type. `Request#as` and `Request#attemptAs` decode non-strictly, so they also read a body sent with another `Content-Type`, such as `text/plain`. A strict decode, `jsonOf[IO, A].decode(request, strict = true)`, rejects such a request with a `MediaTypeMismatch` failure.

## Automatic Instances

When most of your payload types have ZIO JSON codecs, declaring an entity codec for each one gets repetitive. Importing `ZIOEntityCodec._` provides an `EntityEncoder` for every type with a `JsonEncoder` and an `EntityDecoder` for every type with a `JsonDecoder`. Use `ZIOEntityEncoder._` or `ZIOEntityDecoder._` to import only one direction.

The payload types now need only their ZIO JSON codecs:

```scala
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._
import zio.json._
import zio.json.interop.http4s.ZIOEntityCodec._

final case class Order(item: String, quantity: Int)

object Order {
  implicit val codec: JsonCodec[Order] = DeriveJsonCodec.gen[Order]
}

final case class Receipt(item: String, quantity: Int, total: BigDecimal)

object Receipt {
  implicit val codec: JsonCodec[Receipt] = DeriveJsonCodec.gen[Receipt]
}

val orderRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] { case req @ POST -> Root / "orders" =>
  req.as[Order].flatMap { order =>
    Ok(Receipt(order.item, order.quantity, BigDecimal("2.50") * order.quantity))
  }
}
```

The imported encoder also works for building requests, so `Request#withEntity` accepts an `Order` and writes it as JSON:

```scala
val orderRequest = Request[IO](Method.POST, uri"/orders").withEntity(Order("tea", 4))
```

The request carries the JSON content type, and the route decodes the `Order` and encodes the `Receipt` with the imported instances:

```scala
println(orderRequest.headers.get[`Content-Type`])
// Some(Content-Type(MediaType(application/json),None))
println(orderRoutes.orNotFound.run(orderRequest).unsafeRunSync().bodyText.compile.string.unsafeRunSync())
// {"item":"tea","quantity":4,"total":10.00}
```

:::warning
The imported instances apply to every type with a ZIO JSON codec, including `String`, numbers and collections. They take precedence over http4s's built-in instances, so with `ZIOEntityCodec._` in scope, `Ok("hello")` sends the JSON string `"hello"` (with the quotes) as `application/json`, not the plain text `hello`. The same applies to `Request#withEntity` with a `String` argument: the text is encoded as a JSON string instead of being sent as is. Keep the import in the files that serve JSON, or use the explicit style when a route also handles plain text.
:::

## Example Project

The repository contains an older example project in `examples/interop-http4s`. It is written for http4s 0.21 and cats-effect 2 and does not build against this module; the code on this page is the up-to-date reference.
