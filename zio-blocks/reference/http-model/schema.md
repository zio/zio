# Schema-Based Typed Access

> `zio-http-model-schema` adds **type-safe, validated extraction** of query parameters and headers to the core HTTP model. It provides extension methods on `QueryParams`, `Headers`, `Request`, and `Response` that automatically decode string values to typed objects using schema-based decoding with comprehensive error reporting.

Core features are built on **extension methods** — `QueryParamsSchemaOps`, `HeadersSchemaOps`, `RequestSchemaOps`, `ResponseSchemaOps` — which add typed, schema-based extraction to query parameters and headers. Complemented by error types `QueryParamError` and `HeaderError`, the module provides automatic decoding for 11 primitive types and extensible support for custom types via `Schema[T]`.

## Motivation

Building HTTP handlers often requires extracting and validating query parameters or headers — "get the `userId` query parameter as a `UUID`." Without schema-based extraction, this becomes tedious and error-prone:

```scala
import zio.http.QueryParams

// Manual extraction (error-prone, repetitive)
val params = QueryParams("userId" -> "550e8400-e29b-41d4-a716-446655440000")
val userIdStr = params.getFirst("userId")
val userId = userIdStr match {
  case None => Left("Missing userId")
  case Some(s) =>
    try Right(java.util.UUID.fromString(s))
    catch { case e: IllegalArgumentException => Left(s"Invalid UUID format: ${e.getMessage}") }
}
```

Every parameter requires 8+ lines of boilerplate with manual exception handling, error message creation, and type-specific parsing. UUID parsing alone involves `IllegalArgumentException` handling; multiply this across dozens of handlers extracting `UUID`, `Int`, `Boolean` parameters, and you have duplicated extraction logic everywhere — inconsistent error messages, risk of forgotten error handling, and no compile-time guarantees on correctness.

The solution is to use schema-based extraction for clean, declarative code:

```scala
import zio.http.QueryParams
import zio.http.schema._

val params = QueryParams("userId" -> "550e8400-e29b-41d4-a716-446655440000")
val userId = params.query[java.util.UUID]("userId")  // 1 line, automatic UUID parsing + errors
```

`zio-http-model-schema` separates **extraction logic from business logic**. The module achieves this through:

- **Automatic decoding** — Pass a `Schema[T]`, get `Either[Error, T]` back. Works for 11 primitive types out of the box.
- **Explicit error handling** — `Either` forces error handling. `QueryParamError` and `HeaderError` distinguish "missing" from "malformed" cases.
- **Composable** — Works directly on `QueryParams`, `Headers`, `Request`, `Response` with zero configuration.
- **Zero-dependency** — Pure extraction layer; doesn't pull in ZIO, async runtimes, or HTTP client libraries.

This keeps HTTP request handling clean, testable, and portable across different effect systems.

## Installation

Add the following to your `build.sbt`:

```
libraryDependencies += "dev.zio" %% "zio-http-model-schema" % "0.0.51"
```

For cross-platform projects (Scala.js):

```
libraryDependencies += "dev.zio" %%% "zio-http-model-schema" % "0.0.51"
```

Supported Scala versions: Scala 3.x only. Requires `zio-http-model` and `zio-blocks-schema` as dependencies.

## How They Work Together

To understand how the module works, we add a **schema-based extraction layer** on top of core HTTP model types:

```
HTTP Model Types (from zio-http-model)
    ├─ QueryParams: raw string key-value pairs
    ├─ Headers: raw string header name-value pairs
    ├─ Request: contains queryParams and headers
    └─ Response: contains headers

Schema-Based Extraction (this module)
    ├─ QueryParamsSchemaOps.query[T](key) ─────┐
    ├─ HeadersSchemaOps.header[T](name) ───────┼──> StringDecoder.decode(raw, Schema[T])
    ├─ RequestSchemaOps.query[T](key) ─────────┤   ├─> Right(typedValue)
    ├─ RequestSchemaOps.header[T](name) ───────┤   └─> Left(error)
    └─ ResponseSchemaOps.header[T](name) ──────┘

Typical Workflow:
1. Parse URL or receive Request (core HTTP model)
2. Extract queryParams or headers (access raw strings)
3. Use schema methods to decode to typed values (this module)
4. Handle Either[Error, T] in business logic
```

## Quick Showcase

Setting up and extracting query parameters with type safety:

```scala
import zio.http.{Request, URL}
import zio.http.schema._

val url = URL.parse("/api/users?page=2&limit=50&sort=name").toOption.get
// url: URL = URL(
//   scheme = None,
//   host = None,
//   port = None,
//   path = Path(
//     segments = IndexedSeq("api", "users"),
//     hasLeadingSlash = true,
//     trailingSlash = false
//   ),
//   queryParams = QueryParams((page,2), (limit,50), (sort,name)),
//   fragment = None
// )
val request = Request.get(url)
// request: Request = Request(
//   method = GET,
//   url = URL(
//     scheme = None,
//     host = None,
//     port = None,
//     path = Path(
//       segments = IndexedSeq("api", "users"),
//       hasLeadingSlash = true,
//       trailingSlash = false
//     ),
//     queryParams = QueryParams((page,2), (limit,50), (sort,name)),
//     fragment = None
//   ),
//   headers = Headers(),
//   body = Body(length=0, contentType=ContentType(MediaType(application,octet-stream,true,true,List(bin, dms, lrf, mar, so, dist, distz, pkg, bpk, dump, elc, deploy, exe, dll, deb, dmg, iso, img, msi, msp, msm, buffer),Map(),Map()),None,None)),
//   version = HTTP/1.1
// )

// Extract query parameters
val pageResult = request.query[Int]("page")
// pageResult: Either[QueryParamError, Int] = Right(2)
val limitResult = request.query[Int]("limit")
// limitResult: Either[QueryParamError, Int] = Right(50)
val sortResult = request.query[String]("sort")
// sortResult: Either[QueryParamError, String] = Right("name")

// Results are properly typed and decoded
(pageResult, limitResult, sortResult)
// res4: Tuple3[Either[QueryParamError, Int], Either[QueryParamError, Int], Either[QueryParamError, String]] = (
//   Right(2),
//   Right(50),
//   Right("name")
// )

// Handle errors with pattern matching
pageResult match {
  case Right(page) => s"Page: $page"
  case Left(QueryParamError.Missing(key)) => s"Missing $key"
  case Left(QueryParamError.Malformed(key, value, cause)) => s"Bad $key: $cause"
}
// res5: String = "Page: 2"
```

## Extension Classes

### QueryParamsSchemaOps

Extension methods for `QueryParams` to extract and decode query parameters with type safety.

#### `QueryParams#query[T]`

Extract a single query parameter value and decode it to type `T`.

**Signature:** `query[T](key: String): Either[QueryParamError, T]`

Returns `Right(value)` if parameter exists and decoding succeeds. Returns `Left(QueryParamError.Missing(key))` if parameter is missing. Returns `Left(QueryParamError.Malformed(...))` if parameter exists but decoding fails.

When a query parameter is required, use `query[T]` and handle the error:

```scala
import zio.http.{URL}
import zio.http.schema._

val url = URL.parse("/search?q=zio").toOption.get
// url: URL = URL(
//   scheme = None,
//   host = None,
//   port = None,
//   path = Path(
//     segments = IndexedSeq("search"),
//     hasLeadingSlash = true,
//     trailingSlash = false
//   ),
//   queryParams = QueryParams((q,zio)),
//   fragment = None
// )
val params = url.queryParams
// params: QueryParams = QueryParams((q,zio))

params.query[String]("q") match {
  case Right(q) => s"Search for: $q"
  case Left(error) => s"Error: ${error.message}"
}
// res7: String = "Search for: zio"
```

#### `QueryParams#queryAll[T]`

Extract all values for a query parameter key and decode them to type `T`.

**Signature:** `queryAll[T](key: String): Either[QueryParamError, Chunk[T]]`

Returns `Right(chunk)` with all decoded values if parameter exists and all values decode successfully. Returns `Left(QueryParamError.Missing(key))` if no values exist for the key. Returns `Left(QueryParamError.Malformed(...))` if any value fails to decode.

**Pattern: Extract Multiple Values for Same Parameter**

When a query parameter appears multiple times (e.g., `?tag=scala&tag=fp`), use `queryAll[T]`:

```scala
import zio.http.{URL}
import zio.http.schema._

val url = URL.parse("/search?tag=scala&tag=functional&tag=zio").toOption.get
// url: URL = URL(
//   scheme = None,
//   host = None,
//   port = None,
//   path = Path(
//     segments = IndexedSeq("search"),
//     hasLeadingSlash = true,
//     trailingSlash = false
//   ),
//   queryParams = QueryParams((tag,scala), (tag,functional), (tag,zio)),
//   fragment = None
// )
val params = url.queryParams
// params: QueryParams = QueryParams((tag,scala), (tag,functional), (tag,zio))
```

Extract all values for a multi-valued parameter:

```scala
params.queryAll[String]("tag") match {
  case Right(tags) => s"Tags: ${tags.toList}"
  case Left(error) => s"Error: ${error.message}"
}
// res9: String = "Tags: List(scala, functional, zio)"
```

**Short-circuit behavior:** Decoding stops at the first malformed value; only the first error is reported.

#### `QueryParams#queryOrElse[T]`

Extract a query parameter with a default fallback.

**Signature:** `queryOrElse[T](key: String, default: => T): T`

Returns the decoded value if parameter exists and decodes successfully. Returns `default` if parameter is missing or decoding fails (errors are silently ignored).

**Pattern: Extract with Default Fallback**

When a query parameter is optional with a sensible default, use `queryOrElse`:

```scala
import zio.http.{URL}
import zio.http.schema._

val url = URL.parse("/api/items?page=2").toOption.get
// url: URL = URL(
//   scheme = None,
//   host = None,
//   port = None,
//   path = Path(
//     segments = IndexedSeq("api", "items"),
//     hasLeadingSlash = true,
//     trailingSlash = false
//   ),
//   queryParams = QueryParams((page,2)),
//   fragment = None
// )
val params = url.queryParams
// params: QueryParams = QueryParams((page,2))
```

Extract with fallback defaults:

```scala
val page = params.queryOrElse[Int]("page", 1)
// page: Int = 2
val limit = params.queryOrElse[Int]("limit", 20)
// limit: Int = 20
(page, limit)
// res11: Tuple2[Int, Int] = (2, 20)
```

### HeadersSchemaOps

Extension methods for `Headers` to extract and decode header values with type safety. Uses `rawGet`/`rawGetAll` internally for raw string header access. API identical to `QueryParamsSchemaOps`.

#### `Headers#header[T]`

Extract a single header value and decode it to type `T`.

**Signature:** `header[T](name: String): Either[HeaderError, T]`

Header name matching is **case-insensitive** (HTTP spec). Returns `Right(value)` on success, `Left(HeaderError.Missing(name))` if header not found, or `Left(HeaderError.Malformed(...))` if decoding fails.

Here's how to use `header[T]`:

```scala
import zio.http.Headers
import zio.http.schema._

val headers = Headers("x-user-id" -> "42", "x-api-version" -> "2")
// headers: Headers = Headers(x-user-id: 42, x-api-version: 2)
```

Calling `header[T]` returns an `Either` with the decoded value or an error:

```scala
headers.header[Int]("x-user-id")
// res13: Either[HeaderError, Int] = Right(42)
```

Header names are **case-insensitive**:

```scala
headers.header[Int]("X-User-ID")
// res14: Either[HeaderError, Int] = Right(42)
```

Missing headers produce a `Missing` error:

```scala
headers.header[Int]("x-missing")
// res15: Either[HeaderError, Int] = Left(Missing("x-missing"))
```

You can also decode with a custom `Header.Codec[A]` when the header type belongs to your domain instead of the core HTTP model:

```scala
import zio.http.{Header, Headers}
import zio.http.schema._

object TraceIdHeader extends Header.Codec[String] {
  def name: String = "x-trace-id"
  def parse(value: String): Either[String, String] =
    if (value.startsWith("trace-")) Right(value) else Left("trace id must start with trace-")
  def render(value: String): String = value
}

val customHeaders = Headers("x-trace-id" -> "trace-123")
// customHeaders: Headers = Headers(x-trace-id: trace-123)
customHeaders.header(TraceIdHeader)
// res16: Either[HeaderError, String] = Right("trace-123")
```

#### `Headers#headerAll[T]`

Extract all values for a header name and decode them to type `T`.

**Signature:** `headerAll[T](name: String): Either[HeaderError, Chunk[T]]`

HTTP allows multiple headers with the same name; this method collects and decodes all of them. Returns `Right(chunk)` with all decoded values, `Left(HeaderError.Missing(name))` if no headers exist for the name, or `Left(HeaderError.Malformed(...))` if any value fails to decode.

Here's how to extract multiple headers:

```scala
import zio.http.Headers
import zio.http.schema._

val headers = Headers("x-tag" -> "scala", "x-tag" -> "functional", "x-tag" -> "zio")
// headers: Headers = Headers(x-tag: scala, x-tag: functional, x-tag: zio)
```

Extract all values for a header:

```scala
headers.headerAll[String]("x-tag")
// res18: Either[HeaderError, Chunk[String]] = Right(
//   IndexedSeq("scala", "functional", "zio")
// )
```

Missing headers return a `Missing` error:

```scala
headers.headerAll[String]("x-missing")
// res19: Either[HeaderError, Chunk[String]] = Left(Missing("x-missing"))
```

The codec-based overload works for repeated headers as well and reports the first malformed value as `HeaderError.Malformed`:

```scala
import zio.http.{Header, Headers}
import zio.http.schema._

object TraceIdHeader extends Header.Codec[String] {
  def name: String = "x-trace-id"
  def parse(value: String): Either[String, String] =
    if (value.startsWith("trace-")) Right(value) else Left("trace id must start with trace-")
  def render(value: String): String = value
}

val repeatedHeaders = Headers("x-trace-id" -> "trace-1", "x-trace-id" -> "trace-2")
// repeatedHeaders: Headers = Headers(x-trace-id: trace-1, x-trace-id: trace-2)
repeatedHeaders.headerAll(TraceIdHeader)
// res20: Either[HeaderError, Chunk[String]] = Right(
//   IndexedSeq("trace-1", "trace-2")
// )
```

#### `Headers#headerOrElse[T]`

Extract a header with a default fallback (errors are silently ignored).

**Signature:** `headerOrElse[T](name: String, default: => T): T`

Use `headerOrElse[T]` when a header is optional with a sensible default:

```scala
import zio.http.Headers
import zio.http.schema._

val headers = Headers("x-count" -> "5")
// headers: Headers = Headers(x-count: 5)
```

When the header exists, it's decoded and returned:

```scala
headers.headerOrElse[Int]("x-count", 0)
// res22: Int = 5
```

When missing, the default is used:

```scala
headers.headerOrElse[Int]("x-missing", 0)
// res23: Int = 0
```

### RequestSchemaOps

Extension methods for `Request` to extract query parameters and headers using the same schema-based API.

Exposes all methods from `QueryParamsSchemaOps` and `HeadersSchemaOps` directly on `Request` — they work identically but operate on the request object.

Query parameters and headers are extracted identically; just use `header[T]` or `headerAll[T]`:

```scala
import zio.http.{Request, URL}
import zio.http.schema._

val request = Request.get(URL.parse("/").toOption.get)
  .addHeader("x-user-id", "42")
  .addHeader("x-api-version", "2")
// request: Request = Request(
//   method = GET,
//   url = URL(
//     scheme = None,
//     host = None,
//     port = None,
//     path = Path(
//       segments = IndexedSeq(),
//       hasLeadingSlash = true,
//       trailingSlash = false
//     ),
//     queryParams = QueryParams(),
//     fragment = None
//   ),
//   headers = Headers(x-user-id: 42, x-api-version: 2),
//   body = Body(length=0, contentType=ContentType(MediaType(application,octet-stream,true,true,List(bin, dms, lrf, mar, so, dist, distz, pkg, bpk, dump, elc, deploy, exe, dll, deb, dmg, iso, img, msi, msp, msm, buffer),Map(),Map()),None,None)),
//   version = HTTP/1.1
// )
```

Extract headers from the request using the headers API:

```scala
val userId = request.headers.header[Int]("x-user-id")
// userId: Either[HeaderError, Int] = Right(42)
val apiVersion = request.headers.headerOrElse[Int]("x-api-version", 1)
// apiVersion: Int = 2
(userId, apiVersion)
// res25: Tuple2[Either[HeaderError, Int], Int] = (Right(42), 2)
```

### ResponseSchemaOps

Extension methods for `Response` to extract headers using the schema-based API.

Exposes all header methods from `HeadersSchemaOps` directly on `Response` — they work identically but operate on the response object. Note: `Response` does not have `query*` methods (responses don't have query parameters).

`Response` provides the same header extraction methods:

```scala
import zio.http.Response
import zio.http.schema._

val response = Response.ok
  .addHeader("x-request-id", "req-12345")
  .addHeader("x-ratelimit-remaining", "99")
// response: Response = Response(
//   status = 200,
//   headers = Headers(x-request-id: req-12345, x-ratelimit-remaining: 99),
//   body = Body(length=0, contentType=ContentType(MediaType(application,octet-stream,true,true,List(bin, dms, lrf, mar, so, dist, distz, pkg, bpk, dump, elc, deploy, exe, dll, deb, dmg, iso, img, msi, msp, msm, buffer),Map(),Map()),None,None)),
//   version = HTTP/1.1
// )
```

Extract a header from the response using the headers API:

```scala
response.headers.header[String]("x-request-id")
// res27: Either[HeaderError, String] = Right("req-12345")
```

Use a default if the header is missing:

```scala
response.headers.headerOrElse[Int]("x-ratelimit-remaining", 100)
// res28: Int = 99
```

## Composing Multiple Extractions

Extract multiple parameters or headers in a single operation using `Either`'s monadic operations:

```scala
import zio.http.{Request, URL}
import zio.http.schema._

val request = Request.get(URL.parse("/api/posts?userId=5&page=2").toOption.get)
// request: Request = Request(
//   method = GET,
//   url = URL(
//     scheme = None,
//     host = None,
//     port = None,
//     path = Path(
//       segments = IndexedSeq("api", "posts"),
//       hasLeadingSlash = true,
//       trailingSlash = false
//     ),
//     queryParams = QueryParams((userId,5), (page,2)),
//     fragment = None
//   ),
//   headers = Headers(),
//   body = Body(length=0, contentType=ContentType(MediaType(application,octet-stream,true,true,List(bin, dms, lrf, mar, so, dist, distz, pkg, bpk, dump, elc, deploy, exe, dll, deb, dmg, iso, img, msi, msp, msm, buffer),Map(),Map()),None,None)),
//   version = HTTP/1.1
// )
```

Combine multiple extractions with a for-comprehension:

```scala
val result = for {
  userId <- request.query[Int]("userId")
  page <- request.query[Int]("page")
} yield (userId, page)
// result: Either[QueryParamError, Tuple2[Int, Int]] = Right((5, 2))
```

Handle the combined result:

```scala
result match {
  case Right((userId, page)) => s"User $userId, page $page"
  case Left(error) => s"Extraction failed: ${error.message}"
}
// res30: String = "User 5, page 2"
```

The for-comprehension short-circuits on the first error, so only the first error is reported if any extraction fails. This pattern is useful when you need multiple parameters to be present and valid before proceeding with business logic.

## Error Handling

The module provides two error types for explicit error handling: `QueryParamError` and `HeaderError`.

### QueryParamError

Error type for query parameter extraction failures:

```scala
sealed trait QueryParamError extends Product with Serializable {
  def message: String
}

object QueryParamError {
  final case class Missing(key: String) extends QueryParamError {
    def message: String = s"Missing query parameter: $key"
  }
  final case class Malformed(key: String, value: String, cause: String) extends QueryParamError {
    def message: String = s"Malformed query parameter '$key' value '$value': $cause"
  }
}
```

**Variants:**

- **`Missing(key)`** — Query parameter with name `key` is not present in the parameters
  - Example: `QueryParamError.Missing("page")` when accessing a non-existent parameter
  - Message: `"Missing query parameter: page"`

- **`Malformed(key, value, cause)`** — Query parameter with name `key` is present but decoding the `value` to the requested type fails
  - Example: `QueryParamError.Malformed("age", "abc", "Cannot parse 'abc' as Int")` when `age=abc` but `Int` was requested
  - Message: `"Malformed query parameter 'age' value 'abc': Cannot parse 'abc' as Int"`

**Accessing error messages:**

All `QueryParamError` subtypes have a `message` property for user-friendly error reporting:

```scala
import zio.http.schema._

val error: QueryParamError = QueryParamError.Malformed("page", "invalid", "Cannot parse 'invalid' as Int")
// error: QueryParamError = Malformed(
//   key = "page",
//   value = "invalid",
//   cause = "Cannot parse 'invalid' as Int"
// )
```

The message provides detailed error information:

```scala
error.message
// res33: String = "Malformed query parameter 'page' value 'invalid': Cannot parse 'invalid' as Int"
```

### HeaderError

Error type for header extraction failures. Structurally identical to `QueryParamError`, with `name` replacing `key` and header-specific message prefixes:

```scala
sealed trait HeaderError extends Product with Serializable {
  def message: String
}

object HeaderError {
  final case class Missing(name: String) extends HeaderError {
    def message: String = s"Missing header: $name"
  }
  final case class Malformed(name: String, value: String, cause: String) extends HeaderError {
    def message: String = s"Malformed header '$name' value '$value': $cause"
  }
}
```

**Handling patterns:**

Pattern-match on error type to distinguish "missing" from "malformed":

```scala
import zio.http.{Request, URL}
import zio.http.schema._

val request = Request.get(URL.parse("/").toOption.get)
  .addHeader("x-token", "invalid-token")
// request: Request = Request(
//   method = GET,
//   url = URL(
//     scheme = None,
//     host = None,
//     port = None,
//     path = Path(
//       segments = IndexedSeq(),
//       hasLeadingSlash = true,
//       trailingSlash = false
//     ),
//     queryParams = QueryParams(),
//     fragment = None
//   ),
//   headers = Headers(x-token: invalid-token),
//   body = Body(length=0, contentType=ContentType(MediaType(application,octet-stream,true,true,List(bin, dms, lrf, mar, so, dist, distz, pkg, bpk, dump, elc, deploy, exe, dll, deb, dmg, iso, img, msi, msp, msm, buffer),Map(),Map()),None,None)),
//   version = HTTP/1.1
// )
```

When you extract a header with the wrong type, you get a `Malformed` error:

```scala
request.headers.header[Int]("x-token") match {
  case Right(token) => s"Token: $token"
  case Left(HeaderError.Missing(name)) => s"Missing required header: $name"
  case Left(HeaderError.Malformed(name, value, cause)) => s"Bad header: $cause"
}
// res35: String = "Bad header: Cannot parse 'invalid-token' as Int"
```

## Supported Types

The module supports decoding to any type with a `Schema[T]` instance. Built-in support includes:

### Primitives

- **`String`** — No decoding, raw string value
- **`Int`** — Parsed via `String#toInt`, error on invalid format
- **`Long`** — Parsed via `String#toLong`, error on invalid format
- **`Boolean`** — Parsed via `String#toBoolean` (case-insensitive; accepts "true"/"True"/"TRUE" → true and "false"/"False"/"FALSE" → false; any other value produces a Malformed error)
- **`Double`** — Parsed via `String#toDouble`, error on invalid format
- **`Float`** — Parsed via `String#toFloat`, error on invalid format
- **`Short`** — Parsed via `String#toShort`, error on invalid format
- **`Byte`** — Parsed via `String#toByte`, error on invalid format
- **`Char`** — Parses single character; returns a `Left` with error message `"Expected single character but got 'value'"` if string length ≠ 1 (differs from standard error pattern)

### Big Numbers

- **`BigInt`** — Parsed via `scala.BigInt(string)`, error on invalid format
- **`BigDecimal`** — Parsed via `scala.BigDecimal(string)`, error on invalid format

### UUID

- **`java.util.UUID`** — Parsed via `java.util.UUID.fromString(string)`, error on invalid format (must be standard UUID format: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`)

### Error Messages

Most decoding errors follow the pattern: `"Cannot parse 'value' as TypeName"`. Example error messages:

Here are common error message formats:

```
Cannot parse 'abc' as Int
Cannot parse 'notaboolean' as Boolean
Cannot parse 'not-a-uuid' as UUID
Cannot parse '12.34.56' as BigDecimal
```

**Exception:** `Char` parsing uses a different error message format:

```
Expected single character but got 'multichar'
Expected single character but got ''
```

### Custom Types

To support custom types, provide a `Schema[T]` instance. The module automatically uses the schema's primitive type information via `StringDecoder`. For case classes or other compound types, manually create a `Schema[T]` using the schema module's derivation tools or manual construction.

## See Also

- [Combinators](../combinators.md) — Systematically compose and canonicalize Either types for uniform error handling across multiple query parameter or header extractions. The `Eithers` combinator canonicalizes nested Either types, making error accumulation consistent.
