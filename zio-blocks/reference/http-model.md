# HTTP Model

> `zio-http-model` is a **pure, zero-dependency HTTP data model** for building HTTP clients and servers. It provides immutable types representing requests, responses, headers, URLs, paths, query parameters, and all HTTP primitives.

`zio-http-model` is a **pure, zero-dependency HTTP data model** for building HTTP clients and servers. It provides immutable types representing requests, responses, headers, URLs, paths, query parameters, and all HTTP primitives.

The module is designed as a pure data layer:

- **Zero effects**: no streaming, no I/O, no mutable state (except monotonic lazy-parse caches in `Headers`)
- **Zero ZIO dependency**: uses `zio.blocks.chunk.Chunk`, not `zio.Chunk`
- **Single encoding contract**: `Path` and `QueryParams` store values decoded internally, encode only on output
- **Cross-platform**: JVM and Scala.js support
- **Cross-version**: Scala 2.13 and 3.x support

```scala
package zio.http

// Core request/response types
final case class Request(method: Method, url: URL, headers: Headers, body: Body, version: Version)
final case class Response(status: Status, headers: Headers, body: Body, version: Version)

// URL structure
final case class URL(scheme: Option[Scheme], host: Option[String], port: Option[Int],
                     path: Path, queryParams: QueryParams, fragment: Option[String])

final case class Path(segments: Chunk[String], hasLeadingSlash: Boolean, trailingSlash: Boolean)
final class QueryParams private[http] (...)

// HTTP primitives
sealed abstract class Method(val name: String, val ordinal: Int)
opaque type Status = Int  // Scala 3
sealed abstract class Version(val major: Int, val minor: Int)
sealed trait Scheme

// Headers and body
final class Headers private[http] (...)
sealed trait Header
final class Body private (val data: Chunk[Byte], val contentType: ContentType)

// Supporting types
final case class ContentType(mediaType: MediaType, boundary: Option[Boundary], charset: Option[Charset])
final case class ResponseCookie(...), RequestCookie(name: String, value: String)
final case class Form(entries: Chunk[(String, String)])
```

## Motivation

HTTP libraries often couple protocol concerns with effects and streaming, making it difficult to:

- Share data structures across client and server implementations
- Serialize requests/responses for caching or testing
- Work with HTTP primitives without committing to a specific effect system

`zio-http-model` solves this by providing pure data types that:

- Represent all HTTP concepts as immutable values
- Encode only on output (decode on input, store decoded)
- Parse incrementally with lazy caching (`Headers` parses typed headers on first access and caches the result)
- Work with any effect system or none at all

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-http-model" % "<version>"
```

For cross-platform projects (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-http-model" % "<version>"
```

Supported Scala versions: 2.13.x and 3.x

## Quick Start

Creating a request with query parameters:

```scala

val url = URL.parse("https://api.example.com/users?active=true").toOption.get
val request = Request.get(url)

val withHeader = request.addHeader("authorization", "Bearer token123")
```

Creating a JSON response:

```scala

val jsonBody = Body.fromString("""{"message":"ok"}""", Charset.UTF8)
val response = Response(
  status = Status.Ok,
  body = jsonBody,
  headers = Headers("content-type" -> "application/json")
)
```

## Method

`Method` represents standard HTTP methods as case objects:

```scala
sealed abstract class Method(val name: String, val ordinal: Int)
```

### Predefined Methods

```scala

val get     = Method.GET
val post    = Method.POST
val put     = Method.PUT
val delete  = Method.DELETE
val patch   = Method.PATCH
val head    = Method.HEAD
val options = Method.OPTIONS
val trace   = Method.TRACE
val connect = Method.CONNECT
```

### Parsing

```scala

Method.fromString("GET")    // Some(Method.GET)
Method.fromString("POST")   // Some(Method.POST)
Method.fromString("CUSTOM") // None (unknown method)
```

### Rendering

```scala

Method.render(Method.GET)  // "GET"
Method.GET.name            // "GET"
Method.GET.toString        // "GET"
```

## Status

`Status` is an opaque type alias for `Int` in Scala 3 (AnyVal wrapper in Scala 2.13), providing zero-allocation status codes with predefined constants.

```scala
opaque type Status = Int  // Scala 3
```

### Predefined Status Codes

Status codes are organized by category:

```scala

// 1xx Informational
Status.Continue           // 100
Status.SwitchingProtocols // 101

// 2xx Success
Status.Ok                 // 200
Status.Created            // 201
Status.NoContent          // 204

// 3xx Redirection
Status.MovedPermanently   // 301
Status.Found              // 302
Status.SeeOther           // 303
Status.NotModified        // 304

// 4xx Client Errors
Status.BadRequest         // 400
Status.Unauthorized       // 401
Status.Forbidden          // 403
Status.NotFound           // 404

// 5xx Server Errors
Status.InternalServerError // 500
Status.BadGateway          // 502
Status.ServiceUnavailable  // 503
```

### Creating Status Codes

```scala

val custom = Status(418)          // I'm a teapot
val ok     = Status.fromInt(200)  // Status.Ok
```

### Status Code Operations

```scala

val status = Status.Ok

status.code              // 200
status.text              // "OK"
status.isSuccess         // true (2xx)
status.isInformational   // false (1xx)
status.isRedirection     // false (3xx)
status.isClientError     // false (4xx)
status.isServerError     // false (5xx)
status.isError           // false (4xx or 5xx)
```

## Version

`Version` represents HTTP protocol versions:

```scala
sealed abstract class Version(val major: Int, val minor: Int)
```

### Predefined Versions

```scala

val v10 = Version.`HTTP/1.0`
val v11 = Version.`HTTP/1.1`
val v20 = Version.`HTTP/2.0`
val v30 = Version.`HTTP/3.0`
```

### Parsing and Rendering

```scala

Version.fromString("HTTP/1.1") // Some(Version.`HTTP/1.1`)
Version.fromString("HTTP/2")   // Some(Version.`HTTP/2.0`)
Version.fromString("HTTP/3")   // Some(Version.`HTTP/3.0`)

Version.render(Version.`HTTP/1.1`) // "HTTP/1.1"
Version.`HTTP/2.0`.text            // "HTTP/2.0"
```

## Scheme

`Scheme` represents URL schemes with support for HTTP, HTTPS, WebSocket (WS, WSS), and custom schemes:

```scala
sealed trait Scheme {
  def text: String
  def defaultPort: Option[Int]
  def isSecure: Boolean
  def isWebSocket: Boolean
}
```

### Predefined Schemes

```scala

val http  = Scheme.HTTP   // http://, port 80
val https = Scheme.HTTPS  // https://, port 443
val ws    = Scheme.WS     // ws://, port 80
val wss   = Scheme.WSS    // wss://, port 443
```

### Scheme Properties

```scala

Scheme.HTTPS.isSecure      // true
Scheme.WS.isWebSocket      // true
Scheme.HTTP.defaultPort    // Some(80)
```

### Custom Schemes

```scala

val custom = Scheme.Custom("git+ssh")
custom.text         // "git+ssh"
custom.defaultPort  // None
```

### Parsing

```scala

Scheme.fromString("https")    // Scheme.HTTPS
Scheme.fromString("wss")      // Scheme.WSS
Scheme.fromString("custom")   // Scheme.Custom("custom")
```

## Charset

`Charset` represents character encodings with JVM-only conversion to `java.nio.charset.Charset`:

```scala
sealed abstract class Charset(val name: String)
```

### Predefined Charsets

```scala

val utf8      = Charset.UTF8       // "UTF-8"
val ascii     = Charset.ASCII      // "US-ASCII"
val iso88591  = Charset.ISO_8859_1 // "ISO-8859-1"
val utf16     = Charset.UTF16      // "UTF-16"
val utf16be   = Charset.UTF16BE    // "UTF-16BE"
val utf16le   = Charset.UTF16LE    // "UTF-16LE"
```

### Parsing

```scala

Charset.fromString("UTF-8")      // Some(Charset.UTF8)
Charset.fromString("utf8")       // Some(Charset.UTF8) (case-insensitive)
Charset.fromString("ISO-8859-1") // Some(Charset.ISO_8859_1)
Charset.fromString("LATIN1")     // Some(Charset.ISO_8859_1) (alias)
```

## Boundary

`Boundary` represents multipart form-data boundaries:

```scala

val boundary = Boundary("----WebKitFormBoundary7MA4YWxkTrZu0gW")
boundary.value    // "----WebKitFormBoundary7MA4YWxkTrZu0gW"
boundary.toString // "----WebKitFormBoundary7MA4YWxkTrZu0gW"
```

### Generating Boundaries

```scala

val generated = Boundary.generate  // random 24-character alphanumeric string
```

## PercentEncoder

`PercentEncoder` provides RFC 3986 percent-encoding for URL components. Each URL component has different encoding rules:

```scala

val segment = PercentEncoder.encode("hello world", ComponentType.PathSegment)
// "hello%20world"

val queryKey = PercentEncoder.encode("filter[name]", ComponentType.QueryKey)
// "filter%5Bname%5D"

val decoded = PercentEncoder.decode("hello%20world")
// "hello world"
```

### Component Types

The encoder recognizes these component types:

- `PathSegment`: path segments between `/`
- `QueryKey`: query parameter names
- `QueryValue`: query parameter values
- `Fragment`: fragment identifiers after `#`
- `UserInfo`: userinfo in authority

Each type has specific rules for which characters must be percent-encoded.

## ContentType

`ContentType` combines a media type with optional charset and boundary parameters:

```scala

val json = ContentType(MediaTypes.application.`json`)

val htmlUtf8 = ContentType(
  mediaType = MediaTypes.text.`html`,
  charset = Some(Charset.UTF8)
)

val multipart = ContentType(
  mediaType = MediaTypes.multipart.`form-data`,
  boundary = Some(Boundary("----boundary"))
)
```

### Parsing

```scala

ContentType.parse("application/json")
// Right(ContentType(MediaType("application", "json")))

ContentType.parse("text/html; charset=utf-8")
// Right(ContentType(..., charset = Some(Charset.UTF8)))

ContentType.parse("multipart/form-data; boundary=abc123")
// Right(ContentType(..., boundary = Some(Boundary("abc123"))))

ContentType.parse("")
// Left("Invalid content type: cannot be empty")
```

### Rendering

```scala

val ct = ContentType(
  MediaTypes.text.`plain`,
  charset = Some(Charset.UTF8)
)

ct.render  // "text/plain; charset=UTF-8"
```

### Predefined Content Types

```scala

val json   = ContentType.`application/json`
val plain  = ContentType.`text/plain`
val html   = ContentType.`text/html`
val binary = ContentType.`application/octet-stream`
```

## Path

`Path` represents URL paths with decoded segments stored internally:

```scala
final case class Path(
  segments: Chunk[String],
  hasLeadingSlash: Boolean,
  trailingSlash: Boolean
)
```

Paths use a **single encoding contract**: decode on input, store decoded, encode on output.

### Creating Paths

```scala

val empty = Path.empty                    // ""
val root  = Path.root                     // "/"
val users = Path("/users")                // segments: ["users"], leading slash: true
val api   = Path("api/v1/users/")         // segments: ["api", "v1", "users"], trailing slash: true
```

### Parsing Encoded Paths

`Path.fromEncoded` decodes percent-encoded segments:

```scala

val path = Path.fromEncoded("/hello%20world/foo%2Fbar")
// Path(Chunk("hello world", "foo/bar"), hasLeadingSlash = true, trailingSlash = false)

path.segments(0)  // "hello world" (decoded)
path.segments(1)  // "foo/bar" (decoded)
```

### Building Paths

```scala

val base = Path("/api")
val extended = base / "users" / "123"  // "/api/users/123"

val combined = Path("/api") ++ Path("v1/users")  // "/api/v1/users"
```

### Encoding Paths

```scala

val path = Path(Chunk("hello world", "foo/bar"), hasLeadingSlash = true, trailingSlash = false)

path.encode  // "/hello%20world/foo%2Fbar" (percent-encoded)
path.render  // "/hello world/foo/bar" (decoded for display)
```

### Path Properties

```scala

val path = Path("/api/v1/users/")

path.isEmpty           // false
path.nonEmpty          // true
path.length            // 3 (number of segments)
path.hasLeadingSlash   // true
path.trailingSlash     // true
```

### Path Navigation

```scala

val path = Path("/api/v1/users")

// Slash manipulation
path.addLeadingSlash     // same (already has one)
path.dropLeadingSlash    // "api/v1/users" (relative)
path.addTrailingSlash    // "/api/v1/users/"
path.dropTrailingSlash   // same (already no trailing slash)

// Inspecting
path.isRoot              // false (root is "/" with no segments)
Path.root.isRoot         // true

// Prefix checking
path.startsWith(Path("api/v1"))  // true

// Slicing
path.drop(1)       // "/v1/users" (drop first segment)
path.take(2)       // "/api/v1" (take first 2 segments)
path.dropRight(1)  // "/api/v1" (drop last segment)
path.initial       // "/api/v1" (all but last)
path.last          // Some("users")
path.reverse       // "/users/v1/api"
```

## QueryParams

`QueryParams` stores query parameters with multiple values per key:

```scala
final class QueryParams private[http] (
  private val keys: Array[String],
  private val vals: Array[Chunk[String]],
  val size: Int
)
```

Like `Path`, query parameters store decoded values internally and encode only on output.

### Creating QueryParams

```scala

val empty = QueryParams.empty

val params = QueryParams(
  "name" -> "Alice",
  "age" -> "30",
  "active" -> "true"
)
```

### Parsing Encoded Query Strings

```scala

val params = QueryParams.fromEncoded("name=Alice%20Smith&age=30&active=true")

params.getFirst("name")    // Some("Alice Smith") (decoded)
params.getFirst("age")     // Some("30")
params.getFirst("active")  // Some("true")
```

### Accessing Values

```scala

val params = QueryParams(
  "color" -> "red",
  "color" -> "blue",
  "size" -> "large"
)

params.get("color")       // Some(Chunk("red", "blue"))
params.getFirst("color")  // Some("red")
params.getFirst("size")   // Some("large")
params.getFirst("other")  // None
params.has("color")       // true
```

### Modifying QueryParams

```scala

val params = QueryParams("a" -> "1", "b" -> "2")

val added = params.add("c", "3")         // adds "c=3"
val set   = params.set("a", "100")       // replaces all "a" values
val removed = params.remove("b")         // removes all "b" entries
```

### Encoding

```scala

val params = QueryParams(
  "name" -> "Alice Smith",
  "filter[status]" -> "active"
)

params.encode  // "name=Alice%20Smith&filter%5Bstatus%5D=active"
```

### Converting to List

```scala

val params = QueryParams("a" -> "1", "a" -> "2", "b" -> "3")
params.toList  // List(("a", "1"), ("a", "2"), ("b", "3"))
```

## URL

`URL` combines scheme, host, port, path, query parameters, and fragment:

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

```scala

val absolute = URL.parse("https://api.example.com:8080/users?active=true#results")
// Right(URL(
//   scheme = Some(Scheme.HTTPS),
//   host = Some("api.example.com"),
//   port = Some(8080),
//   path = Path("/users"),
//   queryParams = QueryParams("active" -> "true"),
//   fragment = Some("results")
// ))

val relative = URL.parse("/api/users?page=2")
// Right(URL(
//   scheme = None,
//   host = None,
//   port = None,
//   path = Path("/api/users"),
//   queryParams = QueryParams("page" -> "2"),
//   fragment = None
// ))
```

The parser handles:

- IPv6 hosts in brackets: `http://[::1]:8080/`
- Userinfo: `https://user:pass@example.com/` (userinfo is skipped)
- Relative URLs: `/path?query`
- Fragment identifiers: `#section`

### Building URLs

```scala

val base = URL.root  // http://localhost/

val extended = base / "api" / "users"  // adds path segments

val withQuery = extended ?? ("active", "true") ?? ("page", "1")
// http://localhost/api/users?active=true&page=1
```

### URL from Path

```scala

val path = Path("/api/users")
val url = URL.fromPath(path)  // relative URL with just path
```

### Encoding URLs

```scala

val url = URL.parse("https://example.com/hello world?name=Alice Smith").toOption.get

url.encode  // "https://example.com/hello%20world?name=Alice%20Smith"
url.toString  // same as encode
```

### URL Properties

```scala

val absolute = URL.parse("https://example.com/").toOption.get
val relative = URL.parse("/api/users").toOption.get

absolute.isAbsolute  // true (has scheme)
relative.isRelative  // true (no scheme)
```

### URL Transformation

```scala

val url = URL.parse("https://api.example.com:8080/users?page=1").toOption.get

// Setting components
url.host("other.com")           // changes host
url.port(9090)                  // changes port
url.scheme(Scheme.HTTP)          // changes scheme
url.path(Path("/v2/users"))      // replaces path
url.fragment("top")              // sets fragment

// Adding paths
url.addPath("123")             // appends segment: /users/123
url.addPath(Path("v2/items"))  // appends multi-segment path

// Query manipulation
url.addQueryParams(QueryParams("sort" -> "name"))
url.updateQueryParams(_.remove("page"))

// Slash manipulation (delegates to path)
url.addLeadingSlash
url.dropTrailingSlash

// Derived properties
url.hostPort   // Some("api.example.com:8080")
url.relative   // strips scheme/host/port, keeps path and query
```

## Header

`Header` is a trait representing typed HTTP headers:

```scala
trait Header {
  def headerName: String
  def renderedValue: String
}
```

Each header type has a companion object implementing `Header.Typed[H]` for parsing and rendering.

### Predefined Header Types

```scala

val contentType   = headers.ContentType
val accept        = headers.Accept
val authorization = headers.Authorization
val host          = headers.Host
val userAgent     = headers.UserAgent
val cacheControl  = headers.CacheControl
val contentLength = headers.ContentLength
val location      = headers.Location
val setCookie     = headers.SetCookieHeader
val cookie        = headers.CookieHeader
```

### Creating Typed Headers

```scala

val ct = headers.ContentType(
  ContentType(MediaTypes.application.`json`, charset = Some(Charset.UTF8))
)

val auth = headers.Authorization.Bearer("token123")

val host = headers.Host("api.example.com", Some(8080))
```

### Parsing Headers

```scala

headers.ContentType.parse("application/json; charset=utf-8")
// Right(headers.ContentType(...))

headers.Host.parse("example.com:443")
// Right(headers.Host("example.com", Some(443)))

headers.ContentLength.parse("1024")
// Right(headers.ContentLength(1024))

headers.ContentLength.parse("-1")
// Left("Invalid content-length: -1")
```

### Custom Headers

```scala

val custom = Header.Custom("x-request-id", "abc-123")
custom.headerName     // "x-request-id"
custom.renderedValue  // "abc-123"
```

## Headers

`Headers` is a flat array-based collection with lazy monotonic parsing:

```scala
final class Headers private[http] (
  private val names: Array[String],
  private val rawValues: Array[String],
  private val parsed: Array[AnyRef],  // null -> unparsed, value -> cached
  val size: Int
)
```

When you call `get[H]`, the header is parsed once and cached. Subsequent calls return the cached result.

### Creating Headers

```scala

val empty = Headers.empty

val headers = Headers(
  "content-type" -> "application/json",
  "authorization" -> "Bearer token",
  "x-request-id" -> "abc-123"
)
```

### Getting Typed Headers

```scala

val headers = Headers(
  "content-type" -> "application/json",
  "content-length" -> "1024"
)

val ct = headers.get(h.ContentType)
// Some(h.ContentType(...)) (parsed and cached)

val cl = headers.get(h.ContentLength)
// Some(h.ContentLength(1024)) (parsed and cached)

val auth = headers.get(h.Authorization)
// None (not present)
```

### Getting Raw Values

```scala

val headers = Headers("x-custom" -> "value")

headers.rawGet("x-custom")  // Some("value")
headers.rawGet("missing")   // None
```

### Getting All Headers of a Type

Some headers can appear multiple times (like `Set-Cookie`):

```scala

val headers = Headers(
  "set-cookie" -> "session=abc",
  "set-cookie" -> "preference=dark"
)

val cookies = headers.getAll(h.SetCookieHeader)
// Chunk(h.SetCookieHeader(...), h.SetCookieHeader(...))
```

### Modifying Headers

```scala

val headers = Headers("a" -> "1", "b" -> "2")

val added = headers.add("c", "3")        // adds "c: 3"
val set   = headers.set("a", "100")      // replaces all "a" values
val removed = headers.remove("b")        // removes all "b" entries
val has = headers.has("a")               // true
```

### Converting to List

```scala

val headers = Headers("a" -> "1", "b" -> "2")
headers.toList  // List(("a", "1"), ("b", "2"))
```

### Combining Headers

```scala

val auth = Headers("authorization" -> "Bearer token")
val cors = Headers("access-control-allow-origin" -> "*")

val combined = auth ++ cors  // Headers(authorization: ..., access-control-allow-origin: ...)

combined.contains("authorization")  // true (alias for `has`)
combined.toChunk                    // Chunk(("authorization", ...), ("access-control-allow-origin", ...))
```

## Body

`Body` wraps a materialized `Chunk[Byte]` with a content type:

```scala
final class Body private (
  val data: Chunk[Byte],
  val contentType: ContentType
)
```

### Creating Bodies

`Body.empty` provides an empty body with default `application/octet-stream` content type:

```scala

val empty = Body.empty
// Body(data = Chunk.empty, contentType = application/octet-stream)
```

`Body.fromString` creates a body with `text/plain` content type:

```scala

val fromString = Body.fromString("Hello, World!", Charset.UTF8)
// Content-Type: text/plain; charset=UTF-8
```

`Body.fromArray` creates a body with default `application/octet-stream` content type:

```scala

val fromBytes = Body.fromArray(Array[Byte](1, 2, 3))
// Content-Type: application/octet-stream
```

`Body.fromChunk` creates a body from a `Chunk[Byte]` with optional content type:

```scala

val chunk = Chunk[Byte](1, 2, 3, 4, 5)
val body = Body.fromChunk(chunk)
// Content-Type: application/octet-stream (default)

val jsonBody = Body.fromChunk(chunk, ContentType(MediaTypes.application.`json`))
// Content-Type: application/json
```

### Reading Bodies

`Body` provides direct access to data and content type:

```scala

val body = Body.fromString("Hello!", Charset.UTF8)

body.length           // 6
body.isEmpty          // false
body.nonEmpty         // true
body.asString()       // "Hello!" (UTF-8 default)
body.asString(Charset.ASCII)  // "Hello!" (explicit charset)
body.data             // Chunk[Byte](72, 101, 108, 108, 111, 33)
body.contentType      // ContentType(text/plain; charset=UTF-8)
```

## Cookie

Cookies are split into `RequestCookie` and `ResponseCookie` with different structures:

```scala
final case class RequestCookie(name: String, value: String)

final case class ResponseCookie(
  name: String,
  value: String,
  domain: Option[String],
  path: Option[Path],
  maxAge: Option[Long],
  isSecure: Boolean,
  isHttpOnly: Boolean,
  sameSite: Option[SameSite]
)
```

### SameSite

```scala

val strict = SameSite.Strict
val lax    = SameSite.Lax
val none   = SameSite.None_  // underscore avoids conflict with scala.None
```

### Parsing Request Cookies

```scala

val cookies = Cookie.parseRequest("session=abc123; preference=dark")
// Chunk(RequestCookie("session", "abc123"), RequestCookie("preference", "dark"))
```

### Parsing Response Cookies

```scala

val cookie = Cookie.parseResponse("session=abc; Domain=example.com; Path=/; Secure; HttpOnly; SameSite=Strict")
// Right(ResponseCookie(
//   name = "session",
//   value = "abc",
//   domain = Some("example.com"),
//   path = Some(Path("/")),
//   isSecure = true,
//   isHttpOnly = true,
//   sameSite = Some(SameSite.Strict)
// ))
```

### Rendering Cookies

```scala

val requestCookies = Chunk(
  RequestCookie("session", "abc"),
  RequestCookie("theme", "dark")
)
Cookie.renderRequest(requestCookies)
// "session=abc; theme=dark"

val responseCookie = ResponseCookie(
  name = "session",
  value = "xyz",
  domain = Some("example.com"),
  path = Some(Path("/")),
  maxAge = Some(3600),
  isSecure = true,
  isHttpOnly = true,
  sameSite = Some(SameSite.Strict)
)
Cookie.renderResponse(responseCookie)
// "session=xyz; Domain=example.com; Path=/; Max-Age=3600; Secure; HttpOnly; SameSite=Strict"
```

## Form

`Form` represents URL-encoded form data:

```scala
final case class Form(entries: Chunk[(String, String)])
```

### Creating Forms

```scala

val empty = Form.empty

val form = Form(
  "username" -> "alice",
  "password" -> "secret",
  "remember" -> "true"
)
```

### Accessing Form Data

```scala

val form = Form(
  "tag" -> "scala",
  "tag" -> "functional",
  "page" -> "1"
)

form.get("tag")      // Some("scala") (first value)
form.getAll("tag")   // Chunk("scala", "functional")
form.get("page")     // Some("1")
form.get("missing")  // None
```

### Modifying Forms

```scala

val form = Form("a" -> "1")
val added = form.add("b", "2")  // Form(("a", "1"), ("b", "2"))
```

### Encoding and Parsing

```scala

val form = Form(
  "name" -> "Alice Smith",
  "email" -> "alice@example.com"
)

val encoded = form.encode
// "name=Alice%20Smith&email=alice%40example.com"

val parsed = Form.fromString(encoded)
// Form with decoded entries
```

## FormField

`FormField` is a sealed trait for multipart form fields, supporting simple key-value pairs, text parts with optional metadata, and binary parts:

```scala

// Simple key-value field
val simple = FormField.Simple("username", "alice")

// Text field with optional content type and filename
val text = FormField.Text(
  name = "bio",
  value = "Hello world",
  contentType = Some(ContentType(MediaTypes.text.`plain`)),
  filename = None
)

// Binary field with content type and optional filename
val binary = FormField.Binary(
  name = "avatar",
  data = Chunk.fromArray(Array[Byte](1, 2, 3)),
  contentType = ContentType(MediaTypes.image.`png`),
  filename = Some("avatar.png")
)

// All variants share a common `name` accessor
simple.name  // "username"
text.name    // "bio"
binary.name  // "avatar"
```

## Request

`Request` combines all HTTP request components:

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

```scala

val url = URL.parse("https://api.example.com/users").toOption.get
val getRequest = Request.get(url)

val jsonBody = Body.fromString("""{"name":"Alice"}""", Charset.UTF8)
val postRequest = Request.post(url, jsonBody)
```

### Request Factory Methods

```scala

val url = URL.parse("https://api.example.com/resource/1").toOption.get
val body = Body.fromString("""{"name":"updated"}""")

Request.get(url)              // GET with empty body
Request.post(url, body)       // POST with body
Request.put(url, body)        // PUT with body
Request.patch(url, body)      // PATCH with body
Request.delete(url)           // DELETE with empty body
Request.head(url)             // HEAD with empty body
Request.options(url)          // OPTIONS with empty body
```

### Modifying Requests

All modification methods return a new `Request`—the original is unchanged:

```scala

val request = Request.get(URL.parse("https://api.example.com/users").toOption.get)

// Adding/modifying headers
request.addHeader("Accept", "application/json")
request.addHeaders(Headers("X-A" -> "1", "X-B" -> "2"))
request.setHeader("Accept", "text/html")       // replaces existing
request.removeHeader("Accept")

// Replacing components
request.body(Body.fromString("data"))
request.url(URL.parse("/other").toOption.get)
request.method(Method.POST)
request.version(Version.`HTTP/2.0`)
// Functional updates
request.updateHeaders(_.add("X-Custom", "value"))
request.updateUrl(_ / "123")   // appends path segment
```

Full control:

```scala

val url = URL.parse("https://api.example.com/users").toOption.get
val body = Body.fromString("""{"name":"Alice"}""", Charset.UTF8)

val request = Request(
  method = Method.POST,
  url = url,
  headers = Headers(
    "content-type" -> "application/json",
    "authorization" -> "Bearer token123"
  ),
  body = body,
  version = Version.`HTTP/1.1`
)
```

### Accessing Request Data

```scala

val request = Request.get(URL.parse("/api/users?page=1").toOption.get)

request.path         // Path("/api/users")
request.queryParams  // QueryParams("page" -> "1")
request.contentType  // Option[ContentType]
request.header(h.Authorization)  // Option[h.Authorization]
```

## Response

`Response` represents HTTP responses:

```scala
final case class Response(
  status: Status,
  headers: Headers,
  body: Body,
  version: Version
)
```

### Creating Responses

```scala

val ok = Response.ok  // 200 OK, empty body

val notFound = Response.notFound  // 404 Not Found, empty body
```

### Response Factory Methods

```scala

// Status-only responses
Response.ok                    // 200
Response.notFound              // 404
Response.badRequest            // 400
Response.unauthorized          // 401
Response.forbidden             // 403
Response.internalServerError   // 500
Response.serviceUnavailable    // 503

// Responses with bodies
Response.text("Hello, World!")    // 200 with text/plain body
Response.json("""{'ok':true}""")  // 200 with application/json in headers and body
// Redirects
Response.redirect("/new-location")                     // 307 Temporary Redirect
Response.redirect("/new-location", isPermanent = true) // 308 Permanent Redirect
Response.seeOther("/other")                            // 303 See Other
```

Note that `Response.json` creates bodies with `application/json` content-type on the `Body` itself, not just in the headers.

### Modifying Responses

```scala

val response = Response.ok

// Adding/modifying headers
response.addHeader("X-Custom", "value")
response.setHeader("X-Custom", "new-value")
response.removeHeader("X-Custom")

// Replacing components
response.body(Body.fromString("data"))
response.status(Status.Created)
response.version(Version.`HTTP/2.0`)
// Functional update
response.updateHeaders(_.add("X-Request-Id", "abc"))

// Add a Set-Cookie header
response.addCookie(ResponseCookie("session", "abc123"))
```

Full control:

```scala

val jsonBody = Body.fromString("""{"message":"created"}""", Charset.UTF8)

val response = Response(
  status = Status.Created,
  headers = Headers(
    "content-type" -> "application/json",
    "location" -> "/users/123"
  ),
  body = jsonBody,
  version = Version.`HTTP/1.1`
)
```

### Accessing Response Data

```scala

val response = Response.ok

response.status.code                      // 200
response.status.isSuccess                 // true
response.contentType                      // Option[ContentType]
response.header(h.ContentType)       // Option[h.ContentType]
response.header(h.Location)          // Option[h.Location]
```

## Advanced Usage

### Building a Complete HTTP Exchange

```scala

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

```scala

val url = URL.parse("https://api.example.com").toOption.get

val extended = (url / "v1" / "users" / "123") ?? ("include", "profile") ?? ("include", "posts")

extended.encode
// "https://api.example.com/v1/users/123?include=profile&include=posts"
```

### Typed Header Access

```scala

val headers = Headers(
  "content-type" -> "application/json; charset=utf-8",
  "content-length" -> "1024",
  "authorization" -> "Bearer token"
)

// Type-safe header access with parsing
val ct = headers.get(h.ContentType)
ct.map(_.value.charset)  // Some(Some(Charset.UTF8))

val cl = headers.get(h.ContentLength)
cl.map(_.length)  // Some(1024)

// Raw access
headers.rawGet("authorization")  // Some("Bearer token")
```

### Cookie Management

```scala

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

```scala

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

`Headers` stores raw string values and parses typed headers on first access. Parsed results are cached in a parallel `Array[AnyRef]` for O(1) subsequent lookups. This design:

- Avoids parsing headers that are never accessed
- Avoids re-parsing the same header multiple times
- Supports unknown/custom headers without parsing failures

### No Streaming

Bodies are fully materialized `Chunk[Byte]`. Streaming is left to higher-level HTTP libraries that compose with this data model. This keeps the model simple and effect-free.

### Zero ZIO Dependency

The module uses `zio.blocks.chunk.Chunk` instead of `zio.Chunk`, making it usable in any Scala project without ZIO.

## Schema-Based Typed Access (zio-http-model-schema)

The `zio-http-model-schema` module provides schema-based extraction of query parameters and headers with automatic decoding and validation.

### Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-http-model-schema" % "<version>"
```

For cross-platform projects (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-http-model-schema" % "<version>"
```

### Imports

Import the schema module to enable extension methods:

```scala

```

### Query Parameter Extraction

`QueryParams` gains schema-based extraction methods via implicit conversions:

```scala

val url = URL.parse("/api/users?page=2&tag=scala&tag=fp").toOption.get
val params = url.queryParams

// Extract single value with automatic decoding
params.query[Int]("page")
// Right(2)

// Extract all values for a key
params.queryAll[String]("tag")
// Right(Chunk("scala", "fp"))

// Extract with default fallback
params.queryOrElse[Int]("limit", 10)
// 10 - uses default since "limit" not present
```

### Header Extraction

`Headers` gains schema-based extraction methods:

```scala

val request = Request.get(URL.parse("/").toOption.get)
  .addHeader("x-page", "5")
  .addHeader("x-tag", "scala")
  .addHeader("x-tag", "functional")

val headers = request.headers

// Extract single header value
headers.header[Int]("x-page")
// Right(5)

// Extract all header values
headers.headerAll[String]("x-tag")
// Right(Chunk("scala", "functional"))

// Extract with default fallback
headers.headerOrElse[Int]("x-limit", 100)
// 100
```

### Request and Response Extensions

`Request` and `Response` gain schema-based extraction methods that delegate to their query parameters and headers:

```scala

// Request query parameter extraction
val request = Request.get(URL.parse("/search?q=zio&limit=20").toOption.get)

request.query[String]("q")
// Right("zio")

request.query[Int]("limit")
// Right(20)

// Response header extraction via schema
val response = Response.ok.addHeader("x-correlation-id", "abc-123")

val responseOps = new ResponseSchemaOps(response)
responseOps.header[String]("x-correlation-id")
```

### Error Handling

Schema-based extraction returns `Either[QueryParamError, A]` or `Either[HeaderError, A]` for explicit error handling:

```scala

val params = QueryParams("name" -> "Alice", "age" -> "invalid")

params.query[String]("name") match {
  case Right(name) => println(s"Name: $name")
  case Left(QueryParamError.Missing(key)) => println(s"Missing key: $key")
  case Left(QueryParamError.Malformed(key, value, cause)) =>
    println(s"Failed to parse $key=$value: $cause")
}

params.query[Int]("age") match {
  case Right(age) => println(s"Age: $age")
  case Left(QueryParamError.Missing(key)) => println(s"Missing key: $key")
  case Left(QueryParamError.Malformed(key, value, cause)) =>
    println(s"Failed to parse $key=$value: $cause")
}
```

### Supported Types

The schema module provides built-in `Schema` instances for common types. Any type with a `Schema[T]` can be extracted:

- **Primitives**: `String`, `Int`, `Long`, `Boolean`, `Double`, `Float`, `Short`, `Byte`, `Char`
- **Big Numbers**: `BigInt`, `BigDecimal`
- **UUID**: `java.util.UUID`

For custom types, define a `Schema[T]` instance using schema derivation or manual construction.
