# HTTP Model

> `zio-http-model` is a **pure, zero-dependency HTTP data model** for building HTTP clients and servers. It provides immutable types representing all HTTP concepts: requests, responses, headers, URLs, paths, query parameters, methods, status codes, versions, cookies, and forms.

Core types: `Request`, `Response`, `URL`, `Headers`, `Body`, `Method`, `Status`, `Version`, `Scheme`, `Path`, `QueryParams`, `ContentType`, `RequestCookie`, `ResponseCookie`, `Form`.

Here is a high-level overview of the core types and their relationships:

```scala
// Core request/response types
final case class Request(method: Method, url: URL, headers: Headers, body: Body, version: Version)
final case class Response(status: Status, headers: Headers, body: Body, version: Version)

// URL structure
final case class URL(scheme: Option[Scheme], host: Option[String], port: Option[Int],
                     path: Path, queryParams: QueryParams, fragment: Option[String])
final case class Path(segments: Chunk[String], hasLeadingSlash: Boolean, trailingSlash: Boolean)

// HTTP primitives
sealed abstract class Method(val name: String, val ordinal: Int)
opaque type Status = Int
sealed abstract class Version(val major: Int, val minor: Int)
sealed trait Scheme

// Headers and body
final class Headers(...)
sealed trait Header
final class Body(val stream: Stream[Nothing, Byte], val contentType: ContentType)
```

## Motivation

Building modern distributed systems requires HTTP clients and servers, but most HTTP libraries bake effects (I/O, streaming, async) directly into their data types. This creates coupling problems when sharing types across layers or testing without pulling in entire runtimes.

### The Problem: Protocol, Effects, and Coupling

Imagine building a distributed system where you need an HTTP client to call external APIs and an HTTP server to handle incoming requests. Your first instinct is to reach for a popular HTTP library. But here's the trouble: most HTTP libraries bake "effects" (I/O operations, streaming, async) directly into their data structures.

This creates a coupling problem:

**Scenario 1: Sharing Types Across Layers**
You want your client request logic (building a request to send) to use the same types as your server request handling (receiving and parsing a request). But your HTTP library makes this difficult — the `Request` type is tied to async effects, file streams, or a specific Scala version's IO model. Sharing becomes messy.

**Scenario 2: Testing Without Effects**
You're writing unit tests for your request-building logic. You want to serialize a request to JSON for snapshots, or cache requests for debugging. But your `Request` type requires pulling in async runtimes, streaming libraries, or other baggage you don't need in tests. A simple unit test becomes a production-grade effect setup.

**Scenario 3: Lock-In**
You've built your entire API client around ZIO's HTTP library, but your team decides to use Akka for one microservice. Now your request/response types aren't portable — they're coupled to ZIO. Refactoring is painful.

### The Solution: Pure HTTP Data

`zio-http-model` separates **protocol concerns** (representing HTTP messages) from **effect concerns** (actually sending/receiving them). It provides:

- **Pure immutable data types** — `Request`, `Response`, `URL`, `Headers`, and chunk-backed `Body` values are just data. Stream-backed bodies are still effect-free, but collecting them may consume the underlying stream.

- **No dependency on an HTTP runtime** — Not coupled to ZIO HTTP, Akka, or a server/client implementation. The module depends on ZIO Blocks primitives such as Chunk, MediaType, and Stream.

- **Incremental, lazy parsing** — Headers are parsed on first access and cached as an optimization. You pay only for what you use.

- **Efficient, composable** — Headers and QueryParams use parallel array-backed collections for fast lookups. Types compose naturally without forcing a single architectural path.

This separation is powerful: you can build, manipulate, serialize, and test HTTP messages using pure data, then hand them off to any HTTP client/server library (ZIO, Akka, Play, etc.) for the actual I/O work. Your domain logic stays portable and testable.

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-http-model" % "0.0.51"
```

For cross-platform projects (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-http-model" % "0.0.51"
```

Supported Scala versions: Scala 2.13 and Scala 3.x. The artifacts are cross-platform for JVM and Scala.js.

## How They Work Together

The HTTP model consists of **request/response messages** at the center, composed of smaller types that handle specific concerns:

```
Request ────────────────────────────────────────────────────
  ├─ method: Method (HTTP verb: GET, POST, PUT, DELETE, etc.)
  ├─ url: URL (target endpoint)
  │   ├─ scheme: Option[Scheme] (HTTP, HTTPS, WS, WSS, Custom)
  │   ├─ host: Option[String] (domain name)
  │   ├─ port: Option[Int] (port number)
  │   ├─ path: Path (URL path segments)
  │   ├─ queryParams: QueryParams (query string parameters)
  │   └─ fragment: Option[String] (anchor)
  ├─ headers: Headers (HTTP headers, lazily parsed for typed access)
  │   └─ Header (individual headers: Authorization, Content-Type, etc.)
  ├─ body: Body (message content)
  │   └─ contentType: ContentType (media type, charset, boundary)
  └─ version: Version (HTTP/1.0, HTTP/1.1, HTTP/2.0, HTTP/3.0)

Response ────────────────────────────────────────────────────
  ├─ status: Status (HTTP status code: 200, 404, 500, etc.)
  ├─ headers: Headers (same as Request)
  ├─ body: Body (response content)
  └─ version: Version (HTTP protocol version)
```

These types don't exist in isolation — they work together as you build requests and handle responses. The hierarchy above shows the structure, but understanding the actual workflow reveals why each piece is important. Here is the typical flow when using the HTTP model in a client or server:

1. **Build URL** — Parse or construct a URL with path segments and query parameters
2. **Create Request** — Combine method, URL, headers, and body into a Request
3. **Send** — Transmit the request (handled by HTTP client/server library, not http-model)
4. **Receive Response** — Parse incoming Response with status, headers, and body
5. **Access Data** — Extract typed headers, cookies, content type from Headers
6. **Parse Content** — Body contains raw bytes; higher-level libraries deserialize based on content type

## Common Patterns

### Building Requests with Headers and Query Parameters

Create a request with headers and query parameters:

```scala
import zio.http._

val url = URL.parse("https://api.example.com/users?role=admin").toOption.get
val req = Request
  .get(url)
  .addHeader("authorization", "Bearer token123")
  .addHeader("content-type", "application/json")
```

### Extracting and Manipulating Headers

Access headers by name or retrieve all headers:

```scala
import zio.http._

// Accessing headers in a request
val url = URL.parse("https://example.com").toOption.get
val req = Request.get(url).addHeader("content-type", "application/json")

val allHeaders = req.headers.toList                // All headers as List[(String, String)]
val headerCount = allHeaders.length                // Count headers
val contentType = req.headers.rawGet("content-type") // Get raw string header value (case-insensitive lookup)
```

Custom typed headers do not need their own `Header` subtype. Define a `Header.Codec[A]` and reuse it with `Headers`, `Request`, and `Response`:

```scala
import zio.http._

object TraceIdHeader extends Header.Codec[String] {
  def name: String = "x-trace-id"
  def parse(value: String): Either[String, String] =
    if (value.startsWith("trace-")) Right(value) else Left("trace id must start with trace-")
  def render(value: String): String = value
}

val request = Request
  .get(URL.parse("https://example.com").toOption.get)
  .addHeader("x-trace-id", "trace-123")

val traceId = request.header(TraceIdHeader)
```

### Creating Responses with Status and Content

Build a JSON response:

```scala
import zio.http._

val body = Body.fromString("""{"message":"ok"}""", Charset.UTF8)
val response = Response(
  status = Status.Ok,
  body = body,
  headers = Headers("content-type" -> "application/json"),
  version = Version.`HTTP/1.1`
)
```

### URL Manipulation

Parse URLs and extract components:

```scala
import zio.http._

val url = URL.parse("https://example.com/api/v1/users?page=1&limit=10").toOption.get

url.scheme                           // Some(Scheme.HTTPS)
url.host                             // Some("example.com")
url.port                             // None (defaults to 443 for HTTPS)
url.path.segments                    // Chunk("api", "v1", "users")
url.queryParams.getFirst("page")     // Some("1") (first value for key)
url.??("filter", "active")           // Add parameter using ?? operator
```

### Form Data Submission

Submit a form with key-value pairs:

```scala
import zio.http._

val url = URL.parse("https://example.com/login").toOption.get
val form = Form("username" -> "alice", "password" -> "secret123", "remember" -> "true")
val body = Body.fromString(form.encode, Charset.UTF8)
val req = Request.post(url, body)
  .addHeader("content-type", "application/x-www-form-urlencoded")
```

### Cookies in Requests and Responses

Send cookies to server and receive cookies from server:

```scala
import zio.http._

val url = URL.parse("https://example.com").toOption.get

// Request: send cookies to server
val req = Request.get(url)
  .addHeader("cookie", "sessionId=abc123; userId=456")

// Response: server sets cookies for client to store
val response = Response.ok
  .addHeader("set-cookie", "sessionId=abc123; Max-Age=3600; Secure; HttpOnly")
```

---

## Method

`Method` represents the HTTP verb (request method) as sealed case objects:

```scala
sealed abstract class Method(val name: String, val ordinal: Int)
```

### Predefined Methods

The nine standard HTTP methods are predefined as case objects:

```scala
import zio.http.Method

Method.GET      // Retrieve a resource
Method.POST     // Create a new resource
Method.PUT      // Replace a resource entirely
Method.DELETE   // Remove a resource
Method.PATCH    // Partially update a resource
Method.HEAD     // Retrieve headers only (no body)
Method.OPTIONS  // Describe communication options
Method.TRACE    // Echo the request back (for debugging)
Method.CONNECT  // Establish a tunnel (for proxies)
```

### Parsing Methods

Parse HTTP method strings to `Method` objects:

```scala
import zio.http.Method

Method.fromString("GET")    // Some(Method.GET)
Method.fromString("POST")   // Some(Method.POST)
Method.fromString("CUSTOM") // None (HTTP allows custom methods, but not predefined)
```

### Method Properties

Access properties of a `Method` object:

```scala
import zio.http.Method

val m = Method.GET
m.name       // "GET"
m.ordinal    // ordinal position for sorting
m.toString   // "GET"
```

---

## Status

`Status` is an opaque type alias for `Int`, providing zero-allocation HTTP status codes with predefined constants for all standard codes (1xx–5xx).

```scala
opaque type Status = Int  // Scala 3
```

### Predefined Status Codes

Status codes organized by category:

```scala
import zio.http.Status

// 1xx Informational
Status.Continue           // 100
Status.SwitchingProtocols // 101

// 2xx Success
Status.Ok                 // 200
Status.Created            // 201
Status.Accepted           // 202
Status.NoContent          // 204

// 3xx Redirection
Status.MovedPermanently   // 301
Status.Found              // 302 (temporary redirect)
Status.SeeOther           // 303
Status.NotModified        // 304 (response not modified since condition)
Status.TemporaryRedirect  // 307
Status.PermanentRedirect  // 308

// 4xx Client Errors
Status.BadRequest         // 400 (malformed request)
Status.Unauthorized       // 401 (authentication required)
Status.Forbidden          // 403 (authenticated but not authorized)
Status.NotFound           // 404 (resource not found)
Status.MethodNotAllowed   // 405
Status.Conflict           // 409
Status.Gone               // 410 (resource permanently removed)
Status.UnprocessableEntity // 422
Status.TooManyRequests    // 429 (rate limited)

// 5xx Server Errors
Status.InternalServerError // 500 (generic server error)
Status.BadGateway          // 502
Status.ServiceUnavailable  // 503 (server temporarily unavailable)
Status.GatewayTimeout      // 504
```

### Creating Custom Status Codes

Create arbitrary status codes:

```scala
import zio.http.Status

val custom = Status(418)          // I'm a teapot (Easter egg)
val ok     = Status.fromInt(200)  // Parse from Int, returns Status
```

### Status Code Operations

Query status code properties:

```scala
import zio.http.Status

val status = Status.Ok

status.code              // 200 (the underlying Int)
status.text              // "OK" (human-readable)
status.isSuccess         // true (2xx status codes)
status.isInformational   // false (1xx codes)
status.isRedirection     // false (3xx codes)
status.isClientError     // false (4xx codes)
status.isServerError     // false (5xx codes)
status.isError           // false (true for 4xx or 5xx)
```

---

## Version

`Version` represents HTTP protocol versions:

```scala
sealed abstract class Version(val major: Int, val minor: Int)
```

### Predefined Versions

The four standard HTTP versions are predefined:

```scala
import zio.http.Version

Version.`HTTP/1.0`  // HTTP/1.0 (1996)
Version.`HTTP/1.1`  // HTTP/1.1 (1997, persistent connections)
Version.`HTTP/2.0`  // HTTP/2.0 (2015, multiplexing)
Version.`HTTP/3.0`  // HTTP/3.0 (2022, QUIC-based)
```

### Parsing and Rendering

Parse version strings and render to strings:

```scala
import zio.http.Version

Version.fromString("HTTP/1.1") // Some(Version.`HTTP/1.1`)
Version.fromString("HTTP/2")   // Some(Version.`HTTP/2.0`) (suffix optional)
Version.fromString("HTTP/3")   // Some(Version.`HTTP/3.0`)

Version.render(Version.`HTTP/1.1`) // "HTTP/1.1"
Version.`HTTP/2.0`.text            // "HTTP/2.0"
```

### Version Properties

Access version components:

```scala
import zio.http.Version

val v = Version.`HTTP/2.0`
v.major  // 2 (major version number)
v.minor  // 0 (minor version number)
```

---

## Scheme

`Scheme` represents URL schemes with support for HTTP, HTTPS, WebSocket protocols, and custom schemes:

```scala
sealed trait Scheme {
  def text: String
  def defaultPort: Option[Int]
  def isSecure: Boolean
  def isWebSocket: Boolean
}
```

### Predefined Schemes

Standard schemes for HTTP, HTTPS, and WebSocket:

```scala
import zio.http.Scheme

Scheme.HTTP   // http://, default port 80 (unencrypted)
Scheme.HTTPS  // https://, default port 443 (encrypted)
Scheme.WS     // ws://, default port 80 (WebSocket, unencrypted)
Scheme.WSS    // wss://, default port 443 (WebSocket, encrypted)
```

### Scheme Properties

```scala
import zio.http.Scheme

Scheme.HTTPS.isSecure      // true
Scheme.HTTP.isSecure       // false
Scheme.WS.isWebSocket      // true
Scheme.HTTP.isWebSocket    // false

Scheme.HTTP.defaultPort    // Some(80)
Scheme.HTTPS.defaultPort   // Some(443)
Scheme.WS.defaultPort      // Some(80)
Scheme.WSS.defaultPort     // Some(443)

Scheme.HTTPS.text          // "https"
```

### Custom Schemes

Create custom schemes (e.g., FTP):

```scala
import zio.http.Scheme

val ftp = Scheme.Custom("ftp")
val text = ftp.text              // "ftp"
val isSecure = ftp.isSecure      // false (custom schemes assumed insecure)
```

---

## URL

`URL` represents a complete Uniform Resource Locator with scheme, host, port, path, query parameters, and fragment:

```scala
final case class URL(
  scheme: Option[Scheme],
  host: Option[String],
  port: Option[Int],
  path: Path,
  queryParams: QueryParams,
  fragment: Option[String]
)
```

### Parsing URLs

Parse URL strings into `URL` objects:

```scala
import zio.http.URL

URL.parse("https://example.com/api/users?id=123&role=admin")
// Returns: Either[ParseError, URL]

URL.parse("https://api.example.com:8080/v1/users?page=1#results")
// Parses: scheme, host, port (explicit), path, query params, fragment
```

### Constructing URLs

Build URLs by parsing strings:

```scala
import zio.http._

// Simplest approach: parse a URL string
val url1 = URL.parse("https://api.example.com/users/123?filter=active&sort=name").toOption.get

// Modify an existing URL by adding query parameters
val url2 = url1.??("page", "1")
```

### URL Operations

Access URL components and modify them:

```scala
import zio.http.URL

val url = URL.parse("https://example.com:8080/api/v1/users?page=1#section").toOption.get

val scheme = url.scheme                        // Some(Scheme.HTTPS)
val host = url.host                            // Some("example.com")
val port = url.port                            // Some(8080)
val pathSegments = url.path.segments           // Chunk("api", "v1", "users")
val pageParam = url.queryParams.getFirst("page") // Some("1") (first value for key)
val fragment = url.fragment                    // Some("section")

val urlWithSort = url.??("sort", "name")      // Add query parameter using ?? operator
val urlWithNewFragment = url.copy(fragment = Some("top")) // Set fragment
```

---

## Path

`Path` represents the URL path (e.g., `/api/v1/users`) stored as segments for efficient manipulation:

```scala
final case class Path(
  segments: Chunk[String],
  hasLeadingSlash: Boolean,
  trailingSlash: Boolean
)
```

### Creating Paths

Create paths from segments or parse from strings:

```scala
import zio.http.Path

val path1 = Path.apply("/api/v1/users")    // Parse from decoded path string
val path2 = Path.fromEncoded("%2Fapi%2Fv1%2Fusers") // Parse from percent-encoded string
val root = Path.root                       // Empty path "/"
```

### Path Operations

Access path segments and build modified paths:

```scala
import zio.http.Path

val path = Path.apply("/api/v1/users")

val segments = path.segments              // Chunk("api", "v1", "users")
val hasLeadingSlash = path.hasLeadingSlash // true
val hasTrailingSlash = path.trailingSlash  // false
val encoded = path.encode                 // "/api/v1/users" (encoded string)
```

---

## QueryParams

`QueryParams` is an immutable, case-insensitive multi-map for URL query parameters, optimized for performance:

```scala
final class QueryParams private[http] (...)
```

### Creating QueryParams

Create query parameters from key-value pairs:

```scala
import zio.http.QueryParams

val params1 = QueryParams("page" -> "1", "limit" -> "10")
val params2 = QueryParams.empty
val params3 = QueryParams.fromEncoded("page=1&limit=10") // Parse from encoded query string
```

### QueryParams Operations

Access and modify query parameters:

```scala
import zio.http.QueryParams

val params = QueryParams("page" -> "1", "role" -> "admin", "role" -> "user")

val withSort = params.add("sort", "name")   // Add parameter
val withoutPage = params.remove("page")     // Remove all values for key
val encoded = params.encode                 // "page=1&role=admin&role=user"
```

### Multi-Value Parameters

`QueryParams` supports multiple values for the same key:

```scala
import zio.http.QueryParams

val params = QueryParams("id" -> "1", "id" -> "2", "id" -> "3")
val firstId = params.getFirst("id")   // Some("1") (first value)
val allIds = params.get("id")         // Some(Chunk("1", "2", "3"))
```

---

## Headers

`Headers` is an immutable, case-insensitive collection of HTTP headers with lazy parsing for typed header access:

```scala
final class Headers private[http] (...)
```

### Creating Headers

Create header collections:

```scala
import zio.http.Headers

val headers1 = Headers("content-type" -> "application/json", "authorization" -> "Bearer token123")
val headers2 = Headers.empty
```

### Headers Operations

Access and modify headers:

```scala
import zio.http.Headers

val headers = Headers("content-type" -> "application/json", "cache-control" -> "no-cache")

// Headers can be queried and modified using methods
val withAuth = headers.add("authorization", "Bearer token") // Add header
val asList = headers.toList                                 // All headers as List
```

---

## Body

`Body` wraps a `Stream[Nothing, Byte]` with a content type:

```scala
final class Body private (
  val stream: Stream[Nothing, Byte],
  val contentType: ContentType
)
```

### Creating Bodies

`Body.empty` provides an empty body with default `application/octet-stream` content type:

```scala
import zio.http.Body

val empty = Body.empty
// Body(stream = Stream.fromChunk(Chunk.empty[Byte]), contentType = application/octet-stream)
```

`Body.fromString` creates a body with `text/plain` content type:

```scala
import zio.http.{Body, Charset}

val fromString = Body.fromString("Hello, World!", Charset.UTF8)
// Content-Type: text/plain; charset=UTF-8
```

`Body.fromArray` creates a body with default `application/octet-stream` content type without copying the supplied array. Later mutations to the array are visible through the body:

```scala
import zio.http.Body

val fromBytes = Body.fromArray(Array[Byte](1, 2, 3))
// Content-Type: application/octet-stream
```

`Body.fromChunk` creates a body from a `Chunk[Byte]` with optional content type:

```scala
import zio.http.{Body, ContentType}
import zio.blocks.chunk.Chunk
import zio.blocks.mediatype.MediaTypes

val chunk = Chunk[Byte](1, 2, 3, 4, 5)
val body = Body.fromChunk(chunk)
// Content-Type: application/octet-stream (default)

val jsonBody = Body.fromChunk(chunk, ContentType(MediaTypes.application.`json`))
// Content-Type: application/json
```

`Body.fromStream` creates a body from a `Stream[Nothing, Byte]` with optional content type:

```scala
import zio.http.{Body, ContentType}
import zio.blocks.chunk.Chunk
import zio.blocks.streams.Stream
import zio.blocks.mediatype.MediaTypes

val stream = Stream.fromChunk(Chunk[Byte](1, 2, 3))
val body = Body.fromStream(stream, ContentType(MediaTypes.application.`octet-stream`))
// Content-Type: application/octet-stream
```

### Reading Bodies

`Body` provides access to the stream, content type, and convenience methods:

```scala
import zio.http.{Body, Charset}

val body = Body.fromString("Hello!", Charset.UTF8)

body.length           // Some(6)
body.isEmpty          // false
body.nonEmpty         // true
body.asString()       // "Hello!" (UTF-8 default)
body.asString(Charset.ASCII)  // "Hello!" (explicit charset)
body.toChunk          // Chunk[Byte](72, 101, 108, 108, 111, 33)
body.toStream         // Stream[Nothing, Byte]
body.toArray          // Array[Byte](72, 101, 108, 108, 111, 33)
body.contentType      // ContentType(text/plain; charset=UTF-8)
```

---

## ContentType

`ContentType` represents the MIME type, character encoding, and multipart boundary:

```scala
final case class ContentType(
  mediaType: MediaType,
  boundary: Option[Boundary] = None,
  charset: Option[Charset] = None
)
```

### Setting Content Types

Content types are specified as header values in requests and responses:

```scala
import zio.http._

// Common content type headers
val jsonRequest = Request.post(
  URL.parse("https://example.com/api").toOption.get,
  Body.fromString("""{"key":"value"}""", Charset.UTF8)
).addHeader("content-type", "application/json")

val htmlResponse = Response.ok
  .addHeader("content-type", "text/html; charset=utf-8")

val xmlRequest = Request.post(
  URL.parse("https://example.com/xml").toOption.get,
  Body.fromString("<root/>", Charset.UTF8)
).addHeader("content-type", "application/xml")

val formRequest = Request.post(
  URL.parse("https://example.com/form").toOption.get,
  Body.fromString("username=alice&password=secret", Charset.UTF8)
).addHeader("content-type", "application/x-www-form-urlencoded")

val imageResponse = Response.ok
  .addHeader("content-type", "image/png")
```

---

## Charset

`Charset` represents character encoding for text content:

```scala
sealed abstract class Charset(val name: String)
```

### Predefined Charsets

Standard character encodings:

```scala
import zio.http.Charset

Charset.UTF8      // UTF-8 (Unicode, variable-length encoding)
Charset.ASCII     // ASCII (7-bit encoding)
Charset.ISO_8859_1 // ISO-8859-1 (Latin-1)
Charset.UTF16     // UTF-16 (with BOM detection)
Charset.UTF16BE   // UTF-16 Big-Endian
Charset.UTF16LE   // UTF-16 Little-Endian
```

### Charset Properties

```scala
import zio.http.Charset

Charset.UTF8.name   // "UTF-8"
Charset.ISO_8859_1.name // "ISO-8859-1"
```

---

## RequestCookie

`RequestCookie` represents a simple cookie sent from client to server in the Cookie header:

```scala
final case class RequestCookie(name: String, value: String)
```

### Creating Request Cookies

```scala
import zio.http.RequestCookie

val cookie = RequestCookie("sessionId", "abc123xyz")
val name = cookie.name       // "sessionId"
val value = cookie.value     // "abc123xyz"
```

### Sending Cookies

Include cookies in requests:

```scala
import zio.http._

val url = URL.parse("https://example.com").toOption.get
val req = Request.get(url)
  .addHeader("cookie", "sessionId=abc123; userId=456")

// Or build from RequestCookie objects
val cookies = zio.Chunk(
  RequestCookie("sessionId", "abc123"),
  RequestCookie("userId", "456")
)
// Render as Cookie header value (see Header operations)
```

---

## ResponseCookie

`ResponseCookie` represents a cookie set by server for client storage, with full RFC 6265 attributes:

```scala
final case class ResponseCookie(
  name: String,
  value: String,
  expires: Option[String] = None,
  domain: Option[String] = None,
  path: Option[Path] = None,
  maxAge: Option[Long] = None,
  isSecure: Boolean = false,
  isHttpOnly: Boolean = false,
  sameSite: Option[SameSite] = None,
  isPartitioned: Boolean = false,
  priority: Option[CookiePriority] = None
)
```

### Creating Response Cookies

Create cookies with full control over attributes:

```scala
import zio.http._

val sessionCookie = ResponseCookie(
  name = "sessionId",
  value = "abc123xyz",
  path = Some(Path.apply("/")),  // Apply to root path
  maxAge = Some(3600),           // 1 hour (in seconds)
  isSecure = true,               // HTTPS only (prevents transmission over HTTP)
  isHttpOnly = true,             // No JavaScript access (prevents XSS theft)
  sameSite = Some(SameSite.Strict), // Prevent CSRF attacks
  priority = Some(CookiePriority.High)
)
```

### Setting Cookies in Responses

Include cookies in responses by adding the Set-Cookie header:

```scala
import zio.http._

val response = Response(Status.Ok)
  .addHeader("set-cookie", "sessionId=abc123; Max-Age=3600; Secure; HttpOnly; SameSite=Strict")
```

### SameSite Attribute

Control cross-site request behavior:

```scala
import zio.http.SameSite

val strict = SameSite.Strict  // Only same-site requests (default, safest)
val lax = SameSite.Lax        // Top-level navigations allowed (links, forms)
val none = SameSite.None_     // Cross-site allowed; render requires Secure flag
```

---

## Form

`Form` represents `application/x-www-form-urlencoded` form data as key-value pairs. Spaces are encoded as `+`, and `+` decodes back to a space.

```scala
final case class Form(entries: Chunk[(String, String)])
```

### Creating Forms

Create forms with key-value pairs:

```scala
import zio.http.Form

val form = Form(
  "username" -> "alice",
  "email" -> "alice@example.com",
  "subscribe" -> "true"
)
```

### Submitting Forms

Send forms as request body:

```scala
import zio.http._

val form = Form("username" -> "alice", "password" -> "secret")
val body = Body.fromString(form.encode, Charset.UTF8)

val url = URL.parse("https://example.com/login").toOption.get
val request = Request.post(url, body)
  .addHeader("content-type", "application/x-www-form-urlencoded")
```

### Form Operations

Access and encode form data:

```scala
import zio.http.Form

val form = Form("key1" -> "value1", "key2" -> "value2")

val entries = form.entries      // Chunk[(String, String)] of all entries
val encoded = form.encode       // "key1=value1&key2=value2" (form-url-encoded)
```

---

## FormField

`FormField` represents individual fields in multipart form data (used for file uploads):

```scala
sealed trait FormField

case class FormField.Simple(name: String, value: String)
case class FormField.Text(name: String, filename: Option[String], value: String, contentType: Option[ContentType])
case class FormField.Binary(name: String, filename: Option[String], data: Chunk[Byte], contentType: Option[ContentType])
```

### Multipart Form Fields

Create fields for multipart form submissions:

```scala
import zio.http._
import zio.blocks.chunk.Chunk

val textField = FormField.Simple("username", "alice")

val imageBytes = Chunk.fromArray("...image data...".getBytes)
val fileField = FormField.Binary(
  name = "avatar",
  filename = Some("profile.png"),
  data = imageBytes,
  contentType = zio.http.ContentType.parse("image/png").toOption.get
)
```

---

## Request

`Request` is the core type representing an HTTP request message with method, URL, headers, body, and version:

```scala
final case class Request(
  method: Method,
  url: URL,
  headers: Headers,
  body: Body,
  version: Version
)
```

### Creating Requests

Create requests using convenience methods:

```scala
import zio.http._

val url = URL.parse("https://api.example.com/users").toOption.get
val body = Body.fromString("""{"name":"Alice"}""", Charset.UTF8)

// Using convenience methods (recommended)
val getReq = Request.get(url)
val postReq = Request.post(url, body)
val putReq = Request.put(url, body)
val deleteReq = Request.delete(url)
val patchReq = Request.patch(url, body)
val headReq = Request.head(url)
val optionsReq = Request.options(url)
```

### Request Operations

Access and modify request components:

```scala
import zio.http._

val url = URL.parse("https://api.example.com/users").toOption.get
val req = Request.get(url)

val method = req.method              // Method.GET
val reqUrl = req.url                 // URL
val headers = req.headers            // Headers collection
val body = req.body                  // Body
val version = req.version            // Version

val withAuth = req.addHeader("authorization", "Bearer token")  // Add header
val transformed = req.updateHeaders(_.add("x-custom", "value")) // Transform headers
```

---

## Response

`Response` is the core type representing an HTTP response message with status, headers, body, and version:

```scala
final case class Response(
  status: Status,
  headers: Headers,
  body: Body,
  version: Version
)
```

### Creating Responses

Create responses with status, headers, and body:

```scala
import zio.http._

Response(
  status = Status.Ok,
  headers = Headers("content-type" -> "application/json"),
  body = Body.fromString("""{"message":"ok"}""", Charset.UTF8),
  version = Version.`HTTP/1.1`
)
```

### Response Convenience Constructors

Quick constructors for common responses:

```scala
import zio.http._

Response.ok                                              // 200 OK response value
Response.ok.addHeader("content-type", "application/json") // With headers
Response(Status.NotFound)                              // 404 Not Found
Response(Status.InternalServerError)                   // 500 Internal Server Error
```

### Response Operations

Access and modify response components:

```scala
import zio.http._

val body = Body.fromString("""{"data":"example"}""", Charset.UTF8)
val resp = Response.ok.addHeader("content-type", "application/json")

resp.status                     // Status.Ok
resp.headers                    // Headers
resp.body                       // Body
resp.version                    // Version

val modified = resp.copy(status = Status.Created) // Change status code
modified.addHeader("cache-control", "no-cache")  // Add header
```

---

## Integration Points

The HTTP model types integrate with each other in a natural composition hierarchy:

- **Request & Response** are the top-level message types, containing all other types
- **URL** decomposes into Scheme, Host, Port, Path, QueryParams, and Fragment
- **Headers** is a collection of individual Header objects (generic strings and typed headers)
- **Body** carries ContentType metadata describing its content
- **Method & Status** are enumerations representing HTTP verbs and response codes
- **Version** describes the HTTP protocol version being used
- **Cookies** (Request & Response variants) appear as header values in Cookie and Set-Cookie headers
- **Form** is serialized as a Body with `application/x-www-form-urlencoded` content type

## Advanced Usage

### Building a Complete HTTP Exchange

Here's how to build a full request and response for a real scenario:

```scala
import zio.http._

// Build request
val url = URL.parse("https://api.example.com/users").toOption.get
val requestBody = Body.fromString("""{"name":"Alice","age":30}""", Charset.UTF8)

val request = Request(
  method = Method.POST,
  url = url,
  headers = Headers(
    "content-type" -> "application/json",
    "authorization" -> "Bearer abc123",
    "user-agent" -> "MyClient/1.0"
  ),
  body = requestBody,
  version = Version.`HTTP/1.1`
)

// Build response
val responseBody = Body.fromString("""{"id":123,"name":"Alice","age":30}""", Charset.UTF8)

val response = Response(
  status = Status.Created,
  headers = Headers(
    "content-type" -> "application/json",
    "location" -> "/users/123"
  ),
  body = responseBody,
  version = Version.`HTTP/1.1`
)
```

### URL Building with Fluent API

Compose complex URLs using operators:

```scala
import zio.http._

val url = URL.parse("https://api.example.com").toOption.get

val extended = (url / "v1" / "users" / "123") ?? ("include", "profile") ?? ("include", "posts")

extended.encode
// "https://api.example.com/v1/users/123?include=profile&include=posts"
```

### Cookie Management

Parse and render cookies in requests and responses:

```scala
import zio.http._

// Parse cookies from request header
val cookieHeader = "session=abc; theme=dark"
val requestCookies = Cookie.parseRequest(cookieHeader)

// Create response with Set-Cookie headers
val sessionCookie = ResponseCookie(
  name = "session",
  value = "xyz123",
  path = Some(Path("/")),
  maxAge = Some(3600),
  isSecure = true,
  isHttpOnly = true,
  sameSite = Some(SameSite.Strict)
)

val response = Response(
  status = Status.Ok,
  headers = Headers(
    "set-cookie" -> Cookie.renderResponse(sessionCookie)
  ),
  body = Body.empty
)
```

### Form Submission

Build and submit HTML forms:

```scala
import zio.http._

val form = Form(
  "username" -> "alice",
  "password" -> "secret",
  "remember" -> "true"
)

val formBody = Body.fromString(form.encode, Charset.UTF8)

val request = Request(
  method = Method.POST,
  url = URL.parse("/login").toOption.get,
  headers = Headers(
    "content-type" -> "application/x-www-form-urlencoded"
  ),
  body = formBody,
  version = Version.`HTTP/1.1`
)
```

## Design Principles

### Single Encoding Contract

`Path` and `QueryParams` store decoded values internally. Encoding happens only at output boundaries:

- `Path.fromEncoded(s)` decodes, stores decoded segments
- `Path.encode` encodes segments for transmission
- `QueryParams.fromEncoded(s)` decodes, stores decoded key-value pairs
- `QueryParams.encode` encodes for transmission

This eliminates double-encoding bugs and clarifies responsibilities.

### Lazy Header Parsing

Imagine you're building a high-traffic microservice that receives thousands of HTTP requests per second. Each request arrives with 20–30 headers: `content-type`, `authorization`, `cache-control`, `etag`, `x-request-id`, `x-trace-id`, custom headers your team added, and more.

Here's the problem: Your handler only *actually needs* three of those headers:

```scala
def handleRequest(request: Request): Response = {
  val authToken = request.headers.get("authorization")
  val contentType = request.headers.get("content-type")
  val requestId = request.headers.get("x-request-id")
  
  // The other 17+ headers? Never touched.
  processRequest(authToken, contentType, requestId)
}
```

If the `Headers` class eagerly parsed all 20+ headers the moment the request arrived, you'd be wasting CPU time parsing headers you don't care about. With 1,000 requests/second, that's parsing 20,000 unnecessary headers per second.

**http-model's solution: Parse headers only when you ask for them.**

Internally, `Headers` stores header names and values as raw strings. When you call `headers.get("authorization")` for the *first time*, it parses that specific header value into a typed structure (extracting charset, splitting directives, validating format, etc.), then *caches* the result. The second time you ask for the same header, it returns the cached parsed value instantly — no re-parsing.

```scala
val headers = Headers(
  "content-type" -> "application/json; charset=utf-8",
  "authorization" -> "Bearer abc123xyz",
  "cache-control" -> "no-cache, max-age=3600",
  // ... 17 more headers
)

// First access: parses "content-type" string
val ct1 = headers.get("content-type")
// Internally parsed and cached as: ContentType(mediaType=ApplicationJson, charset=UTF8)

// Second access: returns cached parsed result (no parsing!)
val ct2 = headers.get("content-type")  // Instant — already cached

// Other headers never accessed? Never parsed. ✓
```

This design shines in three ways:

**Performance**: Only pay the cost for headers you actually use. With 20 headers and using 3, that's an ~85% reduction in parsing work. At scale (thousands of requests/second), this savings compounds dramatically.

**Robustness**: Custom or unknown headers don't cause parsing failures. If a header can't be parsed, it stays as a raw string, and your code can still access it as a plain value without crashing the handler.

**Simplicity**: Your code is clean — you just ask for headers by name, and http-model handles parsing transparently. No manual string manipulation or error handling on your end.

### No Streaming

Let's say you're downloading a 500MB video file over HTTP. Should your `Body` object represent that as:

**Option A: A single `Chunk[Byte]` with all 500MB in memory?**

```scala
val body = Body(data = Chunk[Byte](/* 500MB of bytes */))
// Everything loaded into RAM at once
```

**Option B: A Stream that yields bytes incrementally as they arrive?**

```scala
val body = Body(data = Stream[Byte]) // Yields chunks as they download
// Only a small buffer in RAM; the rest comes from the network
```

Most HTTP libraries choose Option B for large files — streaming makes sense when you want to process data *as it arrives* without loading everything into memory first.

**http-model chooses an effect-free stream-backed body.** Here's why.

#### The Streaming Trade-off

Streaming sounds great on paper — save memory, start processing immediately — but it brings complexity:

**Streaming requires effects:**

```scala
// With streaming, reading a body becomes an effect:
val body: Body = request.body
val bytes: IO[Chunk[Byte]] = body.stream.runCollect()
// Reading the body is now an IO operation, not a pure value!
```

This couples `Body` to a specific effect system (ZIO, Cats Effect, Scala Futures, etc.). Different effect systems have different streaming abstractions, and your `Body` type would need to know about all of them — or you'd lock users into one.

**Streaming requires error handling:**
```scala
// With streaming, errors can happen mid-stream:
body.stream.fold(
  error => handleNetworkFailure(error),      // Network cut out!
  chunk => processChunk(chunk),
  () => done()
)
// You must handle errors at every chunk boundary
```

**Streaming complicates testing:**

```scala
// Testing code that consumes streams is verbose:
val testStream = Stream(
  Chunk(1, 2, 3),
  Chunk(4, 5, 6),
  Chunk(7, 8, 9)
).flatMap(_.stream)
// vs. just: Chunk(1, 2, 3, 4, 5, 6, 7, 8, 9)
```

#### http-model's Choice: Stream-Backed Bodies

http-model wraps a `Stream[Nothing, Byte]` — a synchronous, pull-based stream with no effect system:

```scala
final class Body private (
  val stream: Stream[Nothing, Byte],
  val contentType: ContentType
)
```

Create bodies from chunks, arrays, strings, or streams:

```scala
val body = Body.fromChunk(
  Chunk.fromArray(myBytes),
  ContentType.`application/json`
)

// Accessing the data:
val bytes: Chunk[Byte] = body.toChunk       // Materializes the stream (O(1) for chunk-backed bodies)
val len: Option[Long]  = body.length        // Known length without materializing, if available
val raw: Stream[Nothing, Byte] = body.toStream  // Access the underlying stream directly
```

For chunk-backed bodies, `toChunk` is O(1) and `length` returns `Some(n)`. For stream-backed bodies, `toChunk` runs the stream to collect all bytes and `length` returns `None`.

```
Your Application Code
        ↓
     (uses)
        ↓
┌─────────────────────┐
│   HTTP Client       │ (ZIO HTTP, Akka HTTP, etc.)
│   (does streaming)  │
└─────────────────────┘
        ↓
    (wraps/unwraps)
        ↓
┌─────────────────────┐
│  http-model Body    │ (stream-backed, synchronous, no effects)
│  (pull-based I/O)   │
└─────────────────────┘
```

Body is synchronous and effect-free — it uses ZIO Blocks' pull-based `Stream`, not an effectful stream type. When the body wraps a known `Chunk`, access is pure and immediate. When it wraps an opaque stream, `toChunk` pulls all bytes on demand.

## Running the Examples

All code from this guide is available as runnable examples in the `http-model-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Basic HTTP Request/Response

Demonstrates creating HTTP requests and responses with URLs, methods, headers, and bodies. Shows how `Request`, `Response`, `Method`, `URL`, `Headers`, and `Body` types work together.

```bash
sbt "http-model-examples/runMain httpmodel.BasicHttpRequest"
```

### Headers and Query Parameters

Shows how to work with headers and query parameters in URLs and requests. Demonstrates how `Headers`, `QueryParams`, and `URL` types compose for extracting and manipulating HTTP metadata.

```bash
sbt "http-model-examples/runMain httpmodel.HeadersAndQueryParams"
```

### Form Submission and Cookies

Demonstrates handling form data and cookies using `Request`, `Response`, `Form`, and cookie types. Shows realistic form submission scenarios with proper content-type headers and cookie management.

```bash
sbt "http-model-examples/runMain httpmodel.FormAndCookies"
```

### Complete HTTP Exchange

Shows a realistic HTTP exchange scenario: creating a request with multiple headers and query parameters, sending it, and receiving a response with status codes and headers. Demonstrates all core types working together in a practical scenario.

```bash
sbt "http-model-examples/runMain httpmodel.CompleteHttpExchange"
```
