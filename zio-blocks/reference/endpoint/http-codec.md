# HttpCodec

> `HttpCodec[K, A]` is a composable, typed descriptor for HTTP request and response parts. The phantom type parameter `K` (either `CodecKind.Request` or `CodecKind.Response`) tracks which direction the codec belongs to, so the compiler prevents mixing request-side codecs (query, request header, request body) with response-side codecs (status, response header, response body). The trait signature is:

`HttpCodec[K, A]` is a composable, typed descriptor for HTTP request and response parts. The phantom type parameter `K` (either `CodecKind.Request` or `CodecKind.Response`) tracks which direction the codec belongs to, so the compiler prevents mixing request-side codecs (query, request header, request body) with response-side codecs (status, response header, response body). The trait signature is:

```scala
sealed trait HttpCodec[+K <: CodecKind, A]
```

## Motivation

HTTP surfaces have two directions — request and response — and each direction has several distinct parts. Without static direction tracking, it is easy to accidentally pass a response status codec where a request header codec is expected, or combine a query parameter with a response body.

`HttpCodec` makes that class of mistake a compile error. The phantom type `K` carries direction at the type level, so `HttpCodec[CodecKind.Request, A]` and `HttpCodec[CodecKind.Response, A]` are incompatible types. All combinators (`++`, `|`) preserve this constraint: combining two request codecs yields a request codec, and combining request with response is a type error.

## `CodecKind`

`CodecKind` is a phantom type hierarchy with two sealed subtypes:

```scala
sealed trait CodecKind

object CodecKind {
  sealed trait Request  extends CodecKind  // query, request header, request body
  sealed trait Response extends CodecKind  // status, response header, response body
}
```

These are never instantiated — they exist only to parameterize `HttpCodec[K, A]` at the type level.

## Structure

`HttpCodec` is an ADT with seven node types:

| Node          | Kind      | Carries                                              |
| ------------- | --------- | ---------------------------------------------------- |
| `Empty`       | both      | No data — neutral element for `++`                  |
| `Combine`     | both      | Two codecs composed sequentially with `++`           |
| `Fallback`    | both      | Two codecs composed as alternatives with `&#124;`    |
| `Query`       | `Request` | Named query parameter with `Schema[A]`               |
| `Header`      | both      | Named HTTP header with `Schema[A]` (request or response) |
| `Body`        | both      | Request or response body with `Schema[A]`            |
| `StatusCodec` | `Response`| HTTP status code                                     |

## Construction

Smart constructors on the `HttpCodec` companion build each atom type. Choose the constructor that matches the HTTP part you are describing.

### Query parameters

To describe a named query parameter, use `HttpCodec.query`:

```scala
import zio.blocks.endpoint._
import zio.blocks.schema.Schema

val limitCodec: HttpCodec.Query[Int] = HttpCodec.query("limit", Schema.int)
```

Optional fields on `Query` include `default`, `doc`, `examples`, and `deprecated`. To create a query codec with a default value:

```scala
import zio.blocks.endpoint._
import zio.blocks.schema.Schema

val pageCodec = HttpCodec.query("page", Schema.int, default = Some(1))
```

### Request headers

To describe a request header by name and schema, use `HttpCodec.requestHeader`:

```scala
import zio.blocks.endpoint._
import zio.blocks.schema.Schema

val traceHeader: HttpCodec.Header[CodecKind.Request, String] =
  HttpCodec.requestHeader("X-Trace-Id", Schema.string)
```

To use a zio-http typed header instance (which provides its own name and parse/render logic), pass the typed header directly:

```scala
import zio.blocks.endpoint._
import zio.http.Header

val authHeader: HttpCodec.Header[CodecKind.Request, Header.Authorization] =
  HttpCodec.requestHeader(Header.Authorization)
```

### Response headers

To describe a response header, use `HttpCodec.responseHeader`:

```scala
import zio.blocks.endpoint._
import zio.blocks.schema.Schema

val totalCount: HttpCodec.Header[CodecKind.Response, Int] =
  HttpCodec.responseHeader("X-Total-Count", Schema.int)
```

### Request body

To describe a request body, use `HttpCodec.requestBody`:

```scala
import zio.blocks.endpoint._
import zio.blocks.schema.Schema

val body: HttpCodec[CodecKind.Request, String] =
  HttpCodec.requestBody(Schema.string)
```

To restrict the accepted content types, pass a `Chunk[MediaType]`:

```scala
import zio.blocks.chunk.Chunk
import zio.blocks.endpoint._
import zio.blocks.mediatype.MediaTypes
import zio.blocks.schema.Schema

val jsonBody: HttpCodec[CodecKind.Request, String] =
  HttpCodec.requestBody(Schema.string, mediaTypes = Chunk.single(MediaTypes.application.`json`))
```

### Response body

To describe a response body, use `HttpCodec.responseBody`:

```scala
import zio.blocks.endpoint._
import zio.blocks.schema.Schema

val body: HttpCodec[CodecKind.Response, String] =
  HttpCodec.responseBody(Schema.string)
```

### Status codes

To describe a required HTTP status code, use `HttpCodec.status`:

```scala
import zio.blocks.endpoint._
import zio.http.Status

val created: HttpCodec[CodecKind.Response, Unit] = HttpCodec.status(Status.Created)
```

Predefined status constants are available directly on `HttpCodec`:

```scala
import zio.blocks.endpoint._

val ok           = HttpCodec.Ok
val created      = HttpCodec.Created
val notFound     = HttpCodec.NotFound
val badRequest   = HttpCodec.BadRequest
val unauthorized = HttpCodec.Unauthorized
```

For any other status code, use `HttpCodec.CustomStatus(code)`.

## Composition

Two operators combine `HttpCodec` values: `++` sequences parts within the same direction, while `|` creates alternatives for content negotiation or multi-status responses.

### Sequential composition with `++`

`++` combines two codecs of the same direction into a single codec whose type is the product of both. The result type is automatically flattened, eliminating `Unit` components and nested tuples:

```scala
import zio.blocks.endpoint._
import zio.blocks.schema.Schema

val queryAndHeader: HttpCodec[CodecKind.Request, (String, Int)] =
  HttpCodec.query("name", Schema.string) ++ HttpCodec.query("age", Schema.int)
```

The compiler rejects mixing directions — combining a request codec with a response codec is a type error:

```scala
import zio.blocks.endpoint._
import zio.blocks.schema.Schema
import zio.http.Status

// This would be a compile error:
// HttpCodec.query("name", Schema.string) ++ HttpCodec.status(Status.Ok)
```

### Alternative composition with `|`

`|` combines two codecs as alternatives. The result type is automatically computed as a nested `Either`:

```scala
import zio.blocks.endpoint._
import zio.blocks.schema.Schema
import zio.http.Status

val okOrCreated =
  (HttpCodec.responseBody(Schema.string) ++ HttpCodec.Ok) |
  (HttpCodec.responseBody(Schema.int) ++ HttpCodec.Created)
```

## Authentication Codecs

Pre-built request codecs for common authorization header schemes are available on `HttpCodec`:

```scala
import zio.blocks.endpoint._
import zio.http.Header

val basic: HttpCodec[CodecKind.Request, Header.Authorization.Basic]   = HttpCodec.basicAuth
val bearer: HttpCodec[CodecKind.Request, Header.Authorization.Bearer] = HttpCodec.bearerAuth
val digest: HttpCodec[CodecKind.Request, Header.Authorization.Digest] = HttpCodec.digestAuth
val proxy: HttpCodec[CodecKind.Request, Header.ProxyAuthorization]    = HttpCodec.proxyAuthorization
```

These codecs use `Schema.transform` internally to parse the raw `Authorization` header string into the typed zio-http auth model, surfacing a `SchemaError` if the scheme does not match.

## Metadata Fields

Every atom node (`Query`, `Header`, `Body`, `StatusCodec`) carries optional metadata that documentation renderers and OpenAPI generators consume:

| Field        | Type              | Purpose                                        |
| ------------ | ----------------- | ---------------------------------------------- |
| `doc`        | `Doc`             | Free-text description for OpenAPI output       |
| `examples`   | `Chunk[(String, A)]` | Example values for the OpenAPI spec         |
| `deprecated` | `Option[Doc]`     | Marks the field as deprecated with a message   |
| `default`    | `Option[A]`       | Default value (query and header only)          |

To create a query codec with documentation and an example:

```scala
import zio.blocks.chunk.Chunk
import zio.blocks.docs.Doc
import zio.blocks.endpoint._
import zio.blocks.schema.Schema

val limitCodec = HttpCodec.query(
  name     = "limit",
  schema   = Schema.int,
  default  = Some(20),
  doc      = Doc.empty,
  examples = Chunk("default" -> 20, "max" -> 100)
)
```
