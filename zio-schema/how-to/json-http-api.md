# How to Build a JSON HTTP API with ZIO HTTP

> Derive schemas for request and response models and use them in ZIO HTTP endpoints that validate input and report errors.

In this guide, we build a small user API with [ZIO HTTP](https://ziohttp.com) whose request and response bodies are JSON. ZIO Schema describes every model once; ZIO HTTP's `Endpoint` API reads those schemas to decode requests, encode responses, check validation rules, and report bad input. By the end, you have a server with two endpoints, `POST /users` and `GET /users/{id}`, that rejects invalid bodies with a structured error.

We work in this order:

- Define the request, response, and error models with their schemas and rules.
- Check the JSON wire format and the validation rules without starting a server.
- Describe the endpoints and implement them.
- Shape the error response that ZIO HTTP sends for a body it cannot accept.
- Put everything together in one runnable server.

The ZIO HTTP code in this guide targets **ZIO HTTP 3.11.6**. The documentation build does not compile ZIO HTTP, so the blocks that use it are shown without compile checks; the blocks that only use ZIO Schema are compiled.

## The Problem

An HTTP API reads bytes from a request, turns them into a value, checks the value, and writes another value back as bytes. Written by hand, each endpoint repeats that work for its own types. The JSON format, the field names, and the validation messages are spread over the handlers, and a field added to a model has to be added to every place that reads or writes it.

For example, a hand-written version of the create-user endpoint needs its own encoder and its own checks:

```scala
final case class CreateUser(name: String, email: String, age: Int)

def toJson(user: CreateUser): String =
  s"""{"name":"${user.name}","email":"${user.email}","age":${user.age}}"""

def check(user: CreateUser): List[String] =
  List(
    if (user.name.isEmpty) Some("name must not be empty") else None,
    if (!user.email.contains("@")) Some("email is invalid") else None,
    if (user.age < 18) Some("age must be at least 18") else None
  ).flatten
```

The encoder does not escape quotes in the name, the decoder is still missing, and nothing ties `check` to the endpoint, so a handler can forget to call it. In this guide, the schema of each model drives all of it: ZIO HTTP derives the JSON codecs from the schema and runs the schema's validation rules on every request body.

## Prerequisites

ZIO HTTP provides the server and the `Endpoint` API. ZIO Schema provides the schemas; its JSON module also brings in `zio-schema-derivation`, which provides `DeriveSchema.gen`:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-http"        % "3.11.6",
  "dev.zio" %% "zio-schema"      % "1.9.2",
  "dev.zio" %% "zio-schema-json" % "1.9.2"
)
```

ZIO HTTP depends on ZIO Schema itself, so sbt keeps the newer of the two ZIO Schema versions. On Scala 2, schema derivation also needs `scala-reflect` at compile time, as the [Installation](../index.md#installation) section shows. We assume you know how to derive a schema and how ZIO effects compose.

## Define the Models

Each body type is a case class with a derived schema in its companion object. ZIO HTTP finds the schema there, so no import or extra wiring is needed. The request model, `CreateUser`, carries `@validate` rules on its fields; the response model, `User`, and the error model, `UserNotFound`, are plain:

```scala
import zio.Chunk
import zio.schema._
import zio.schema.annotation.validate
import zio.schema.codec.JsonCodec
import zio.schema.validation.{Validation, ValidationError}

final case class CreateUser(
  @validate(Validation.minLength(1) && Validation.maxLength(50)) name: String,
  @validate(Validation.email) email: String,
  @validate(Validation.greaterThan(17)) age: Int
)

object CreateUser {
  implicit val schema: Schema[CreateUser] = DeriveSchema.gen[CreateUser]
}

final case class User(id: Int, name: String, email: String, age: Int)

object User {
  implicit val schema: Schema[User] = DeriveSchema.gen[User]
}

final case class UserNotFound(id: Int, message: String)

object UserNotFound {
  implicit val schema: Schema[UserNotFound] = DeriveSchema.gen[UserNotFound]
}
```

The rules use ready-made validators: `Validation.email` checks the address format, and `Validation.greaterThan(17)` requires an adult. [How to Validate Untrusted Input](validate-input.md) covers the rules in more depth.

## Check the Wire Format Without a Server

ZIO HTTP encodes and decodes JSON bodies with the schema-based codec of `zio-schema-json`, so we can see what clients receive before writing any HTTP code. ZIO HTTP configures that codec with its own `CodecConfig`, whose settings for `null` values, empty collections, and field-name formats are described in the [Endpoint reference](https://ziohttp.com/reference/endpoint). Our models have no optional fields or collections, and the default field-name format keeps names as written. A `User` response body looks like this:

```scala
println(JsonCodec.jsonCodec(User.schema).encodeJson(User(1, "Ada", "ada@example.com", 36), None))
// {"id":1,"name":"Ada","email":"ada@example.com","age":36}
```

For a request body, ZIO HTTP decodes the bytes and then calls `Schema.validate` on the result, failing the request when either step fails. The following helper performs the same two steps, which lets a unit test check a model's rules without starting a server:

```scala
def decodeBody[A](body: String)(implicit schema: Schema[A]): Either[String, A] =
  JsonCodec.jsonDecoder(schema).decodeJson(body) match {
    case Left(error) => Left(s"malformed body: $error")
    case Right(value) =>
      val errors = Schema.validate(value)
      if (errors.isEmpty) Right(value)
      else Left(s"failed validation: ${errors.map(_.message).mkString("; ")}")
  }
```

We try a valid body, a body that lacks the email address, and a body whose values break the rules:

```scala
decodeBody[CreateUser]("""{"name":"Ada","email":"ada@example.com","age":36}""")
// res2: Either[String, CreateUser] = Right(
//   value = CreateUser(name = "Ada", email = "ada@example.com", age = 36)
// )

decodeBody[CreateUser]("""{"name":"Ada","age":36}""")
// res3: Either[String, CreateUser] = Left(
//   value = "malformed body: .email(missing)"
// )

decodeBody[CreateUser]("""{"name":"","email":"ada@example.com","age":12}""")
// res4: Either[String, CreateUser] = Left(
//   value = "failed validation: Expected the length of  to be at least 1 characters but was 0 characters.; 12 should be greater than 17"
// )
```

The third body is well-formed JSON, so the decoder alone accepts it. Only the validation step rejects it.

## Describe the Endpoints

An `Endpoint` describes a route, its inputs, and its outputs as data. `Endpoint#in` declares the request body type, `Endpoint#out` the success body type, and `Endpoint#outError` an error body type with its status code. Each of them needs a codec for the type, and ZIO HTTP builds one from the implicit `Schema`. The following definitions describe our two endpoints:

```scala
import zio._
import zio.http._
import zio.http.codec._
import zio.http.endpoint._

val createUser =
  Endpoint(RoutePattern.POST / "users")
    .in[CreateUser]
    .out[User](Status.Created)

val getUser =
  Endpoint(RoutePattern.GET / "users" / PathCodec.int("id"))
    .out[User]
    .outError[UserNotFound](Status.NotFound)
```

`createUser` reads a `CreateUser` from the request body and answers `201 Created` with a `User`. `getUser` reads the `id` path segment as an `Int` and answers either `200 OK` with a `User` or `404 Not Found` with a `UserNotFound`.

## Implement the Endpoints

`Endpoint#implement` turns an endpoint into a `Route` by supplying a function from the endpoint's input to a ZIO effect that produces its output or fails with its error type. The handlers only see values that have already been decoded and validated. We keep the users in a `Ref`:

```scala
def routes(users: Ref[Map[Int, User]]) =
  Routes(
    createUser.implement { request =>
      users.modify { current =>
        val user = User(current.size + 1, request.name, request.email, request.age)
        (user, current + (user.id -> user))
      }
    },
    getUser.implement { id =>
      users.get.flatMap { current =>
        ZIO.fromOption(current.get(id)).orElseFail(UserNotFound(id, s"No user with id $id"))
      }
    }
  )
```

The handler of `createUser` cannot fail, so its error type is `ZNothing`. The handler of `getUser` fails with `UserNotFound`, which `Endpoint#outError` encodes as a JSON body with status 404.

## Return Decode and Validation Errors

When a request body does not decode, or decodes into a value that breaks a `@validate` rule, ZIO HTTP does not call the handler. It answers `400 Bad Request` with a body that has two fields, `name` and `message`, as JSON or HTML depending on the request's `Accept` header. A body that fails validation is reported as a `MalformedBody` error whose message includes the message of each `ValidationError`.

To send a request with `curl`, set the `Content-Type` header, so that ZIO HTTP picks its JSON codec for the body:

```bash
curl -i -X POST http://localhost:8080/users \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{"name":"","email":"ada@example.com","age":12}'
```

The default body suits debugging, but clients usually want a fixed format. We describe that format with a case class, `ApiProblem`, and a function that turns a chunk of `ValidationError`s into one:

```scala
final case class ApiProblem(error: String, details: List[String])

object ApiProblem {
  implicit val schema: Schema[ApiProblem] = DeriveSchema.gen[ApiProblem]

  def fromValidationErrors(errors: Chunk[ValidationError]): ApiProblem =
    ApiProblem(
      "validation_failed",
      errors.map {
        // the default RegexMatch message prints the whole regular expression
        case ValidationError.RegexMatch(value, _) => s"'$value' has an invalid format"
        case other                                => other.message
      }.toList
    )
}
```

For the third body of the previous section, the client would receive this JSON:

```scala
val problem = ApiProblem.fromValidationErrors(Schema.validate(CreateUser("", "ada@example.com", 12)))
// problem: ApiProblem = ApiProblem(
//   error = "validation_failed",
//   details = List(
//     "Expected the length of  to be at least 1 characters but was 0 characters.",
//     "12 should be greater than 17"
//   )
// )

println(JsonCodec.jsonCodec(ApiProblem.schema).encodeJson(problem, None))
// {"error":"validation_failed","details":["Expected the length of  to be at least 1 characters but was 0 characters.","12 should be greater than 17"]}
```

ZIO HTTP reports every decoding problem as an `HttpCodecError`. `Endpoint#outCodecError` adds a codec for those errors that ZIO HTTP prefers over its default one. The schema below converts an `HttpCodecError` to an `ApiProblem` with `Schema#transformOrFail`. A body that fails validation arrives as `HttpCodecError.MalformedBody` whose cause is an `HttpCodecError.InvalidEntity`, which holds the `ValidationError`s; other inputs, such as query parameters, report `InvalidEntity` directly:

```scala
import zio.http._
import zio.http.codec._
import zio.schema._
import zio.schema.codec.JsonCodec

val problemSchema: Schema[HttpCodecError] =
  Schema[ApiProblem].transformOrFail[HttpCodecError](
    problem => Right(HttpCodecError.CustomError(problem.error, problem.details.mkString("\n"))),
    {
      case HttpCodecError.MalformedBody(_, Some(HttpCodecError.InvalidEntity(_, errors))) =>
        Right(ApiProblem.fromValidationErrors(errors))
      case HttpCodecError.InvalidEntity(_, errors) =>
        Right(ApiProblem.fromValidationErrors(errors))
      case other =>
        Right(ApiProblem(other.productPrefix, List(other.getMessage())))
    }
  )

val problemContentCodec: HttpContentCodec[HttpCodecError] =
  HttpContentCodec.from(
    MediaType.application.json -> BinaryCodecWithSchema(
      JsonCodec.schemaBasedBinaryCodec(problemSchema),
      problemSchema
    )
  )

val problemCodec: HttpCodec[HttpCodecType.ResponseType, HttpCodecError] =
  ContentCodec.content(problemContentCodec) ++ StatusCodec.BadRequest
```

The second function of `Schema#transformOrFail` runs when the server writes an error; the first one runs only when a ZIO HTTP client reads it back. Attaching the codec to an endpoint makes it the format for every decoding error of that endpoint:

```scala
val createUserChecked = createUser.outCodecError(problemCodec)
```

To hide the details of decoding errors from clients instead, call `Endpoint#emptyErrorResponse`, which answers `400 Bad Request` with an empty body.

:::caution[Limits of Schema Validation Apply]
ZIO HTTP runs `Schema.validate` on the body, so the same limits apply as anywhere else: a rule added with `Schema#validation` is not checked, and on Scala 2 the `@validate` annotations of a case class with more than 22 fields are dropped. [Rules That Are Silently Skipped](validate-input.md#rules-that-are-silently-skipped) explains each case and its workaround.
:::

## Putting It Together

The complete server combines the models, the endpoints with the custom error format, and the routes. Run it and send requests with `curl` as shown above:

```scala
import zio._
import zio.http._
import zio.http.codec._
import zio.http.endpoint._
import zio.schema._
import zio.schema.annotation.validate
import zio.schema.codec.JsonCodec
import zio.schema.validation.{Validation, ValidationError}

final case class CreateUser(
  @validate(Validation.minLength(1) && Validation.maxLength(50)) name: String,
  @validate(Validation.email) email: String,
  @validate(Validation.greaterThan(17)) age: Int
)

object CreateUser {
  implicit val schema: Schema[CreateUser] = DeriveSchema.gen[CreateUser]
}

final case class User(id: Int, name: String, email: String, age: Int)

object User {
  implicit val schema: Schema[User] = DeriveSchema.gen[User]
}

final case class UserNotFound(id: Int, message: String)

object UserNotFound {
  implicit val schema: Schema[UserNotFound] = DeriveSchema.gen[UserNotFound]
}

final case class ApiProblem(error: String, details: List[String])

object ApiProblem {
  implicit val schema: Schema[ApiProblem] = DeriveSchema.gen[ApiProblem]

  def fromValidationErrors(errors: Chunk[ValidationError]): ApiProblem =
    ApiProblem(
      "validation_failed",
      errors.map {
        // the default RegexMatch message prints the whole regular expression
        case ValidationError.RegexMatch(value, _) => s"'$value' has an invalid format"
        case other                                => other.message
      }.toList
    )
}

object UserApi extends ZIOAppDefault {

  val problemSchema: Schema[HttpCodecError] =
    Schema[ApiProblem].transformOrFail[HttpCodecError](
      problem => Right(HttpCodecError.CustomError(problem.error, problem.details.mkString("\n"))),
      {
        case HttpCodecError.MalformedBody(_, Some(HttpCodecError.InvalidEntity(_, errors))) =>
          Right(ApiProblem.fromValidationErrors(errors))
        case HttpCodecError.InvalidEntity(_, errors) =>
          Right(ApiProblem.fromValidationErrors(errors))
        case other =>
          Right(ApiProblem(other.productPrefix, List(other.getMessage())))
      }
    )

  val problemContentCodec: HttpContentCodec[HttpCodecError] =
    HttpContentCodec.from(
      MediaType.application.json -> BinaryCodecWithSchema(
        JsonCodec.schemaBasedBinaryCodec(problemSchema),
        problemSchema
      )
    )

  val problemCodec: HttpCodec[HttpCodecType.ResponseType, HttpCodecError] =
    ContentCodec.content(problemContentCodec) ++ StatusCodec.BadRequest

  val createUser =
    Endpoint(RoutePattern.POST / "users")
      .in[CreateUser]
      .out[User](Status.Created)
      .outCodecError(problemCodec)

  val getUser =
    Endpoint(RoutePattern.GET / "users" / PathCodec.int("id"))
      .out[User]
      .outError[UserNotFound](Status.NotFound)
      .outCodecError(problemCodec)

  def routes(users: Ref[Map[Int, User]]) =
    Routes(
      createUser.implement { request =>
        users.modify { current =>
          val user = User(current.size + 1, request.name, request.email, request.age)
          (user, current + (user.id -> user))
        }
      },
      getUser.implement { id =>
        users.get.flatMap { current =>
          ZIO.fromOption(current.get(id)).orElseFail(UserNotFound(id, s"No user with id $id"))
        }
      }
    )

  override def run =
    Ref.make(Map.empty[Int, User]).flatMap { users =>
      Server.serve(routes(users)).provide(Server.default)
    }
}
```

`Server.default` listens on port 8080.

## Going Further

The ZIO HTTP [Endpoint reference](https://ziohttp.com/reference/endpoint) covers query parameters, headers, multiple error types, authentication, and OpenAPI generation, which also reads your schemas. On the ZIO Schema side, [How to Validate Untrusted Input](validate-input.md) goes deeper into rules and error reporting, [JSON Codecs](../derivations/codecs/json.md) describes `JsonCodec.Configuration`, and [Schema Annotations](../schema-annotations.md) shows how `@fieldName` and similar annotations change the JSON that clients see.
