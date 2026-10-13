# Header

> `Header` is the typed model of a single HTTP header field. Each of the 75 built-in headers is a case class or sealed ADT paired with a companion that knows the header's wire name and how to parse and render it. `Header.Codec[A]` is the type class carrying that knowledge, and `Headers` uses it to decode raw strings on demand and cache the result. The lead-in to every typed read:

```scala
trait Header {
  def headerName: String
  def renderedValue: String
}

object Header {
  trait Codec[A] {
    def name: String
    def parse(value: String): Either[String, A]
    def render(value: A): String
  }

  trait Typed[H <: Header] extends Codec[H]

  case class Custom(headerName: String, rawValue: String) extends Header
}
```

## Motivation

An HTTP message is a bag of strings, and treating it that way pushes the same three mistakes into every handler. `headers.rawGet("content-length").map(_.toInt)` throws on a malformed value. `headers.rawGet("Content-Length")` works only because someone remembered the casing rules. `cache-control: max-age=600, no-store` has to be split, trimmed, and matched by hand at each call site.

A typed header replaces all three with one lookup. `Header.ContentLength` knows its wire name is `content-length`, that the value is a `Long`, and that a non-numeric value is a parse failure rather than an exception. Reading it is `headers.get(Header.ContentLength)`, and the result is an `Option[ContentLength]` with a `length: Long` inside.

Parsing stays lazy because most handlers read two or three headers out of fifteen. `Headers` keeps the raw strings and only parses an entry when a typed read asks for it, caching the parsed value per entry so a header read twice is parsed once.

## Quick Showcase

Setting a typed header renders it; reading it back parses it:

```scala
import zio.http.{Header, Headers}

val headers = Headers.empty
  .add(Header.ContentLength(1024))
  .add(Header.CacheControl.MaxAge(600))
  .add("x-request-id", "abc-123")
```

Rendering happened at `Headers#add`, so the collection holds wire-format strings:

```scala
headers.toList
// res0: List[Tuple2[String, String]] = List(
//   ("content-length", "1024"),
//   ("cache-control", "max-age=600"),
//   ("x-request-id", "abc-123")
// )
```

Typed reads give back the domain value, and untyped reads give back the string:

```scala
headers.get(Header.ContentLength)
// res1: Option[ContentLength] = Some(ContentLength(1024L))
headers.get(Header.CacheControl)
// res2: Option[CacheControl] = Some(MaxAge(600L))
headers.rawGet("x-request-id")
// res3: Option[String] = Some("abc-123")
```

Lookups are case-insensitive in both directions, because names are lowercased on the way in:

```scala
headers.rawGet("X-Request-ID")
// res4: Option[String] = Some("abc-123")
headers.has("Content-Length")
// res5: Boolean = true
```

## The Codec Type Class

`Header.Codec[A]` is what every typed read and write goes through. It pairs a wire name with a parse and a render:

```scala
trait Codec[A] {
  def name: String
  def parse(value: String): Either[String, A]
  def render(value: A): String
}
```

`Header.Typed[H <: Header]` narrows that to codecs whose domain value is itself a `Header`, which is what all 75 built-ins are. Each header's companion object *is* its codec — `Header.ContentLength` refers to the companion when used as a codec and to the case class when used as a type:

```scala
import zio.http.Header
```

The codec's name is the canonical lowercase wire name, and parsing reports a message rather than throwing:

```scala
Header.ContentLength.name
// res7: String = "content-length"
Header.ContentLength.parse("1024")
// res8: Either[String, ContentLength] = Right(ContentLength(1024L))
Header.ContentLength.parse("not-a-number")
// res9: Either[String, ContentLength] = Left(
//   "Invalid content-length: not-a-number"
// )
```

Rendering is the inverse, and every built-in round-trips:

```scala
Header.ContentLength.render(Header.ContentLength(1024))
// res10: String = "1024"
```

Because `Header` itself carries `headerName` and `renderedValue`, an instance knows how to write itself without the codec being named again:

```scala
Header.CacheControl.MaxAge(600).headerName
// res11: String = "cache-control"
Header.CacheControl.MaxAge(600).renderedValue
// res12: String = "max-age=600"
```

## Reading Headers

Six methods read from a `Headers` collection: three typed, three raw. Which you want depends on whether you need the domain value or the exact bytes.

### `Headers#get` — first parseable match

`Headers#get` scans for the first entry whose name matches the codec and returns the parsed value:

```scala
final class Headers {
  def get[A](headerCodec: Header.Codec[A]): Option[A]
}
```

A present, well-formed header parses; an absent one is `None`:

```scala
import zio.http.{Header, Headers}

val headers = Headers("content-length" -> "2048", "accept" -> "application/json")
```

The typed result carries the domain value, not the string:

```scala
headers.get(Header.ContentLength)
// res14: Option[ContentLength] = Some(ContentLength(2048L))
headers.get(Header.ETag)
// res15: Option[ETag] = None
```

:::warning[Unparseable headers are skipped, not reported]
`Headers#get` treats a parse failure exactly like a name mismatch: it discards the error and keeps scanning. A malformed header therefore reads as `None` — or as the *next* entry with the same name that does parse. There is no method that surfaces the parse error from a collection read.
:::

That behaviour is worth seeing, because it is the one place a typed read can mislead you:

```scala
import zio.http.{Header, Headers}

val malformed = Headers("content-length" -> "huge")
val shadowing = Headers("content-length" -> "huge", "content-length" -> "512")
```

A single bad value is indistinguishable from an absent header, and a bad value followed by a good one silently yields the good one:

```scala
malformed.get(Header.ContentLength)
// res17: Option[ContentLength] = None
shadowing.get(Header.ContentLength)
// res18: Option[ContentLength] = Some(ContentLength(512L))
```

To tell "missing" from "malformed", read the raw value and parse it explicitly:

```scala
malformed.rawGet("content-length").map(Header.ContentLength.parse)
// res19: Option[Either[String, ContentLength]] = Some(
//   Left("Invalid content-length: huge")
// )
```

### `Headers#getAll` — every parseable match

`Headers#getAll` returns all matching entries in header order, skipping the ones that fail to parse:

```scala
final class Headers {
  def getAll[A](headerCodec: Header.Codec[A]): Chunk[A]
}
```

Multi-value headers are the normal case for `accept-encoding`, `via`, and `set-cookie`:

```scala
import zio.http.{Header, Headers}

val multi = Headers(
  "content-length" -> "100",
  "content-length" -> "oops",
  "content-length" -> "300"
)
```

Two entries parse and the middle one is dropped, so the typed result is shorter than the raw one:

```scala
multi.getAll(Header.ContentLength)
// res21: Chunk[ContentLength] = IndexedSeq(
//   ContentLength(100L),
//   ContentLength(300L)
// )
multi.rawGetAll("content-length").length
// res22: Int = 3
```

How many entries survive depends on how strict the individual codec is, and they vary. `Header.ContentLength` rejects anything non-numeric, while some codecs accept almost any input:

```scala
Header.AcceptEncoding.parse("gzip")
// res23: Either[String, AcceptEncoding] = Right(GZip(None))
Header.AcceptEncoding.parse("!!!")
// res24: Either[String, AcceptEncoding] = Right(GZip(None))
```

:::warning[`AcceptEncoding` falls back to `GZip` on unknown values]
`Header.AcceptEncoding` matches a fixed set of encoding names and returns `GZip` for anything it does not recognize, rather than reporting a parse failure. A request with `accept-encoding: bogus` — or a typo like `identityy` — reads as if the client asked for gzip. `Header.AcceptEncoding.parse` rejects only values with no non-empty comma-separated part, so almost any string succeeds. A server choosing a response encoding from this header should read the raw value instead of trusting the parsed variant. Tracked as [zio/zio-blocks#1618](https://github.com/zio/zio-blocks/issues/1618).
:::

### `Headers#getLast` — last parseable match

`Headers#getLast` is `Headers#getAll` keeping only the final element, which is what you want when a later header is meant to override an earlier one:

```scala
final class Headers {
  def getLast[H <: Header](headerType: Header.Typed[H]): Option[H]
}
```

Note the tighter bound: `Headers#getLast` takes a `Header.Typed[H]`, so it works with the built-ins but not with a bare `Header.Codec[A]` over a non-`Header` type:

```scala
import zio.http.{Header, Headers}

val overridden = Headers("content-length" -> "100", "content-length" -> "200")
```

First and last differ, and each method says which it takes:

```scala
overridden.get(Header.ContentLength)
// res26: Option[ContentLength] = Some(ContentLength(100L))
overridden.getLast(Header.ContentLength)
// res27: Option[ContentLength] = Some(ContentLength(200L))
```

### Raw Access

Three methods bypass parsing entirely and hand back the stored string. Use them for headers with no typed model, for pass-through proxying, and for telling a malformed value from an absent one:

| Method                    | Returns             | Picks                        |
| ------------------------- | ------------------- | ---------------------------- |
| `Headers#rawGet`          | `Option[String]`    | First entry with that name    |
| `Headers#rawGetLast`      | `Option[String]`    | Last entry with that name     |
| `Headers#rawGetAll`       | `Chunk[String]`     | Every entry, in header order  |

All three validate the name before scanning and are case-insensitive:

```scala
import zio.http.Headers

val headers = Headers("x-trace" -> "a", "x-trace" -> "b")
```

Raw reads never fail on content, only on a structurally invalid name:

```scala
headers.rawGet("X-Trace")
// res29: Option[String] = Some("a")
headers.rawGetLast("x-trace")
// res30: Option[String] = Some("b")
headers.rawGetAll("x-trace")
// res31: Chunk[String] = IndexedSeq("a", "b")
```

`Headers#has` and its alias `Headers#contains` test for presence without reading a value:

```scala
headers.has("x-trace")
// res32: Boolean = true
headers.contains("x-missing")
// res33: Boolean = false
```

## The Parse Cache

`Headers` stores three parallel arrays: lowercased names, raw values, and a lazily-filled slot for the parsed value of each entry. A typed read fills that slot; a second read of the same header reuses it.

The cache is keyed by *codec identity*, compared by reference. Two codecs sharing a wire name therefore never read each other's cached values, which is what keeps a custom `Header.Codec[MyType]` named `content-length` from colliding with `Header.ContentLength`.

Three consequences are worth knowing:

- **The cache is dropped by `Headers#add`.** Appending builds fresh arrays and does not carry parsed values across, so a read-then-add-then-read sequence parses twice. Read after you finish assembling, not between additions.
- **Duplicate parses are possible under concurrency.** Filling a slot is not synchronized, so two threads reading the same header on the same instance may both parse it. Both produce equal values, so the race is benign — but it is a race, and a codec with side effects would run twice.
- **Failures are not cached.** An entry that fails to parse leaves its slot empty, so every read retries the failing parse.

## Writing Headers

Writes come in typed and raw forms, and the distinction that matters is append versus replace.

### Appending and Replacing

`Headers#add` appends, keeping any existing header of the same name. `Headers#set` replaces every entry with that name:

```scala
final class Headers {
  def add(header: Header): Headers
  def add(name: String, value: String): Headers
  def set(header: Header): Headers
  def set(name: String, value: String): Headers
  def remove(name: String): Headers
}
```

Starting from a collection that already has a value, the two diverge:

```scala
import zio.http.{Header, Headers}

val base = Headers.empty.add(Header.ContentLength(100))
```

`Headers#add` produces two entries, while `Headers#set` produces one:

```scala
base.add(Header.ContentLength(200)).toList
// res35: List[Tuple2[String, String]] = List(
//   ("content-length", "100"),
//   ("content-length", "200")
// )
base.set(Header.ContentLength(200)).toList
// res36: List[Tuple2[String, String]] = List(("content-length", "200"))
```

`Headers#remove` drops every entry with the given name, and `Headers#++` concatenates without deduplicating — the result can hold duplicates from both sides:

```scala
base.remove("content-length").toList
// res37: List[Tuple2[String, String]] = List()
(base ++ Headers("content-length" -> "300")).toList
// res38: List[Tuple2[String, String]] = List(
//   ("content-length", "100"),
//   ("content-length", "300")
// )
```

Every method returns a new `Headers`; the class is immutable and the arrays are never mutated in place.

### `Headers.apply` and `Headers.empty`

The companion builds a collection from name-value pairs, and `Headers.empty` is the identity for `Headers#++`:

```scala
object Headers {
  def apply(pairs: (String, String)*): Headers
  val empty: Headers
}
```

Pairs are validated and lowercased as they are added:

```scala
import zio.http.Headers
```

Both entry points produce the same shape, so a builder chain can start from either:

```scala
Headers("Content-Type" -> "application/json").toList
// res40: List[Tuple2[String, String]] = List(
//   ("content-type", "application/json")
// )
Headers.empty.size
// res41: Int = 0
```

### `HeadersBuilder` — batch construction

Building with `Headers#add` allocates fresh arrays per call, which is wasteful when adding many headers at once. `HeadersBuilder` accumulates into a growable buffer and copies once:

```scala
final class HeadersBuilder {
  def add(name: String, value: String): Unit
  def reset(): Unit
  def build(): Headers
}

object HeadersBuilder {
  def make(initialCapacity: Int = 8): HeadersBuilder
}
```

The builder is mutable and single-threaded by design, and `HeadersBuilder#build` snapshots it:

```scala
import zio.http.{Header, HeadersBuilder}

val builder = HeadersBuilder.make(4)
builder.add("content-type", "application/json")
builder.add(Header.ContentLength(64).headerName, Header.ContentLength(64).renderedValue)
```

The built collection is an ordinary immutable `Headers`:

```scala
builder.build().toList
// res45: List[Tuple2[String, String]] = List(
//   ("content-type", "application/json"),
//   ("content-length", "64")
// )
```

`HeadersBuilder#reset` clears the buffer for reuse without reallocating, which is why the capacity argument exists. The requested capacity is raised to a minimum of four, and the buffer doubles when it fills.

## Validation and Injection Safety

Header names and values are validated on every write, and the value rule exists for a security reason rather than a formatting one.

A name must be a non-empty HTTP token: ASCII letters, digits, or one of `!#$%&'*+-.^_`|~`. A value may contain anything **except** carriage return or line feed. Rejecting CR and LF is what prevents response splitting and header injection — a value carrying `\r\n` would otherwise terminate the header and let an attacker append headers or a body of their own choosing.

Two public methods expose the checks as values, for validating input before you try to use it:

```scala
object Headers {
  def validateName(name: String): Either[String, Unit]
  def validateValue(value: String): Either[String, Unit]
}
```

Both report a message rather than throwing:

```scala
import zio.http.Headers
```

A structurally invalid name and a CR/LF-bearing value are each rejected with a reason:

```scala
Headers.validateName("x-trace")
// res47: Either[String, Unit] = Right(())
Headers.validateName("x trace")
// res48: Either[String, Unit] = Left("Invalid header name: x trace")
Headers.validateValue("ok")
// res49: Either[String, Unit] = Right(())
Headers.validateValue("evil\r\nSet-Cookie: admin=true")
// res50: Either[String, Unit] = Left("Header value cannot contain CR or LF")
```

:::danger[The mutating methods throw]
`Headers#add`, `Headers#set`, `Headers#remove`, `Headers#has`, the `rawGet*` family, and `HeadersBuilder#add` all enforce the same invariants by throwing `IllegalArgumentException`. When a name or value comes from outside your program — a proxied request, a user-supplied field — check it with `Headers.validateName` or `Headers.validateValue` first, or the throw becomes your error handling.
:::

Note that the raw *read* methods validate their name argument too, so `headers.rawGet(userSuppliedName)` can throw even though it only reads.

## Equality and Rendering

`Headers#equals` compares `Headers#toList`, so equality is order-sensitive and name-case-insensitive — names were lowercased on the way in, but two collections holding the same headers in a different order are not equal. `Headers#hashCode` is derived from the same list, so it agrees.

`Headers#toString` renders every name and raw value:

```scala
import zio.http.Headers

val withAuth = Headers("authorization" -> "Bearer sk-live-1234567890")
```

The value appears in full, which matters wherever a `Headers` reaches a log:

```scala
withAuth.toString
// res52: String = "Headers(authorization: Bearer sk-live-1234567890)"
```

:::warning[`toString` prints credentials]
There is no redaction. `authorization`, `cookie`, and `set-cookie` values are rendered verbatim, and anything that logs a `Request` or `Response` logs its headers. Strip or replace sensitive entries with `Headers#remove` or `Headers#set` before logging.
:::

`Headers#toList` and `Headers#toChunk` expose the same pairs for iteration, differing only in collection type.

## The Header Catalog

All 75 built-in headers, grouped the way the module's own test suites group them. Every entry's companion object is its `Header.Typed` codec, and the wire name column is exactly what `Codec#name` returns.

### Authentication

| Type                               | Wire name              | Shape                                          |
| ---------------------------------- | ---------------------- | ---------------------------------------------- |
| `Header.Authorization`             | `authorization`        | ADT: `Basic`, `Bearer`, `Digest`, `Unparsed`    |
| `Header.ProxyAuthorization`        | `proxy-authorization`  | ADT: `Basic`, `Bearer`, `Digest`, `Unparsed`    |
| `Header.WWWAuthenticate`           | `www-authenticate`     | `scheme: String`, `params: Map[String, String]` |
| `Header.ProxyAuthenticate`         | `proxy-authenticate`   | `scheme: String`, `params: Map[String, String]` |

`Unparsed` is the escape hatch: a scheme the ADT does not model is preserved rather than rejected, so an unknown authentication scheme survives a parse-render round trip.

### Content

| Type                               | Wire name                   | Shape                                                      |
| ---------------------------------- | --------------------------- | ---------------------------------------------------------- |
| `Header.ContentType`               | `content-type`              | `value: ContentType`                                        |
| `Header.ContentLength`             | `content-length`            | `length: Long`                                              |
| `Header.ContentEncoding`           | `content-encoding`          | ADT: `GZip`, `Deflate`, `Br`, `Compress`, `Identity`, `Multiple` |
| `Header.ContentDisposition`        | `content-disposition`       | ADT: `Attachment`, `Inline`, `FormData`                       |
| `Header.ContentLanguage`           | `content-language`          | `language: String`                                           |
| `Header.ContentLocation`           | `content-location`          | `location: String`                                           |
| `Header.ContentRange`              | `content-range`             | `unit: String`, `range: Option[(Long, Long)]`, `size: Option[Long]` |
| `Header.ContentSecurityPolicy`     | `content-security-policy`   | `directives: String`                                         |
| `Header.ContentTransferEncoding`   | `content-transfer-encoding` | ADT: `SevenBit`, `EightBit`, `Binary`, `QuotedPrintable`, `Base64` |
| `Header.ContentMd5`                | `content-md5`               | `value: String`                                              |
| `Header.ContentBase`               | `content-base`              | `uri: String`                                                |

`Multiple` on `ContentEncoding` carries a `Chunk` of the others, which is how a comma-separated list becomes one value rather than several entries.

### Caching

| Type                        | Wire name             | Shape                                    |
| --------------------------- | --------------------- | ---------------------------------------- |
| `Header.CacheControl`       | `cache-control`       | ADT, 17 variants — see below              |
| `Header.ETag`               | `etag`                | `tag: String`, `weak: Boolean`            |
| `Header.IfMatch`            | `if-match`            | ADT: `Any`, `ETags`                       |
| `Header.IfNoneMatch`        | `if-none-match`       | ADT: `Any`, `ETags`                       |
| `Header.IfModifiedSince`    | `if-modified-since`   | `date: String`                            |
| `Header.IfUnmodifiedSince`  | `if-unmodified-since` | `date: String`                            |
| `Header.IfRange`            | `if-range`            | `value: String`                           |
| `Header.Expires`            | `expires`             | `date: String`                            |
| `Header.Age`                | `age`                 | `seconds: Long`                           |
| `Header.LastModified`       | `last-modified`       | `date: String`                            |
| `Header.Pragma`             | `pragma`              | `directives: String`                      |
| `Header.Vary`               | `vary`                | ADT: `Any`, `Headers`                     |

`CacheControl` is the largest ADT in the module. Ten variants are flag directives with no argument — `NoCache`, `NoStore`, `NoTransform`, `Public`, `Private`, `MustRevalidate`, `ProxyRevalidate`, `Immutable`, `OnlyIfCached`, `MustUnderstand` — and six take a duration: `MaxAge`, `SMaxAge`, `MinFresh`, `StaleWhileRevalidate`, and `StaleIfError` each hold a `Long`, while `MaxStale` holds an `Option[Long]` because `max-stale` is valid with or without a value. `Multiple` wraps a `Chunk[CacheControl]` for comma-separated directive lists.

Parsing splits on `=` to decide between the two families, so an unknown directive name and a non-numeric duration produce different messages:

```scala
import zio.http.Header
```

Both failures name what went wrong, which is what makes them useful in a 400 response:

```scala
Header.CacheControl.parse("max-age=600")
// res54: Either[String, CacheControl] = Right(MaxAge(600L))
Header.CacheControl.parse("max-stale")
// res55: Either[String, CacheControl] = Right(MaxStale(None))
Header.CacheControl.parse("nonsense")
// res56: Either[String, CacheControl] = Left(
//   "Unknown cache-control directive: nonsense"
// )
Header.CacheControl.parse("max-age=soon")
// res57: Either[String, CacheControl] = Left(
//   "Invalid number in cache-control directive: soon"
// )
```

### Content Negotiation

| Type                      | Wire name          | Shape                                                            |
| ------------------------- | ------------------ | ---------------------------------------------------------------- |
| `Header.Accept`           | `accept`           | `mediaRanges: Chunk[Accept.MediaRange]`                           |
| `Header.AcceptEncoding`   | `accept-encoding`  | ADT: `GZip`, `Deflate`, `Br`, `Compress`, `Identity`, `Any`, `Multiple` |
| `Header.AcceptLanguage`   | `accept-language`  | `languages: Chunk[AcceptLanguage.LanguageRange]`                  |
| `Header.AcceptRanges`     | `accept-ranges`    | ADT: `Bytes`, `None_`                                             |
| `Header.AcceptPatch`      | `accept-patch`     | `mediaTypes: Chunk[MediaType]`                                    |

`Accept.MediaRange` and `AcceptLanguage.LanguageRange` are the per-entry types that carry a quality weight, which is what makes these headers a `Chunk` rather than a single value. `None_` on `AcceptRanges` is spelled with a trailing underscore because `None` is taken.

### CORS

| Type                                     | Wire name                          | Shape                             |
| ---------------------------------------- | ---------------------------------- | --------------------------------- |
| `Header.AccessControlAllowOrigin`        | `access-control-allow-origin`       | ADT: `All`, `Specific`             |
| `Header.AccessControlAllowMethods`       | `access-control-allow-methods`      | `methods: Chunk[Method]`           |
| `Header.AccessControlAllowHeaders`       | `access-control-allow-headers`      | `headers: Chunk[String]`           |
| `Header.AccessControlAllowCredentials`   | `access-control-allow-credentials`  | `allow: Boolean`                   |
| `Header.AccessControlExposeHeaders`      | `access-control-expose-headers`     | `headers: Chunk[String]`           |
| `Header.AccessControlMaxAge`             | `access-control-max-age`            | `seconds: Long`                    |
| `Header.AccessControlRequestHeaders`     | `access-control-request-headers`    | `headers: Chunk[String]`           |
| `Header.AccessControlRequestMethod`      | `access-control-request-method`     | `method: Method`                   |
| `Header.Origin`                          | `origin`                           | ADT: `Null_`, `Value`              |

`AccessControlAllowMethods` and `AccessControlRequestMethod` hold `Method` values rather than strings, so an unrecognized verb fails at parse time instead of reaching your CORS logic.

### Routing and Identity

| Type                    | Wire name       | Shape                                |
| ----------------------- | --------------- | ------------------------------------ |
| `Header.Host`           | `host`          | `host: String`, `port: Option[Int]`   |
| `Header.Location`       | `location`      | `uri: String`                         |
| `Header.Referer`        | `referer`       | `uri: String`                         |
| `Header.Via`            | `via`           | `entries: Chunk[String]`              |
| `Header.Forwarded`      | `forwarded`     | `params: String`                      |
| `Header.MaxForwards`    | `max-forwards`  | `count: Int`                          |
| `Header.From`           | `from`          | `email: String`                       |
| `Header.UserAgent`      | `user-agent`    | `product: String`                     |
| `Header.Server`         | `server`        | `product: String`                     |
| `Header.Date`           | `date`          | `value: String`                       |
| `Header.Link`           | `link`          | `value: String`                       |
| `Header.RetryAfter`     | `retry-after`   | `value: String`                       |
| `Header.Allow`          | `allow`         | `methods: Chunk[Method]`              |
| `Header.Expect`         | `expect`        | `value: String`                       |
| `Header.Range`          | `range`         | `unit: String`, `ranges: String`      |

`Host` splits the port out as an `Option[Int]`, so `example.com` and `example.com:8080` both parse and render back to their original form.

### Cookies

| Type                       | Wire name    | Shape           |
| -------------------------- | ------------ | --------------- |
| `Header.CookieHeader`      | `cookie`     | `value: String`  |
| `Header.SetCookieHeader`   | `set-cookie` | `value: String`  |

Both model the header as an unparsed string; structured cookie handling lives in the separate `Cookie` type documented in [the HTTP model](./model.md). Two aliases exist on the companion for readability at call sites — `Header.Cookie` is `Header.CookieHeader` and `Header.SetCookie` is `Header.SetCookieHeader`, both typed as the codec rather than the case class.

### Connection Management

| Type                       | Wire name            | Shape                                                                  |
| -------------------------- | -------------------- | ---------------------------------------------------------------------- |
| `Header.Connection`        | `connection`         | ADT: `Close`, `KeepAlive`, `Other`                                       |
| `Header.Upgrade`           | `upgrade`            | `protocol: String`                                                      |
| `Header.Te`                | `te`                 | `value: String`                                                         |
| `Header.Trailer`           | `trailer`            | `value: String`                                                         |
| `Header.TransferEncoding`  | `transfer-encoding`  | ADT: `Chunked`, `Compress`, `Deflate`, `GZip`, `Identity`, `Multiple`    |

### Security

| Type                               | Wire name                    | Shape                                              |
| ---------------------------------- | ---------------------------- | -------------------------------------------------- |
| `Header.XFrameOptions`             | `x-frame-options`            | ADT: `Deny`, `SameOrigin`                           |
| `Header.XRequestedWith`            | `x-requested-with`           | `value: String`                                     |
| `Header.DNT`                       | `dnt`                        | ADT: `TrackingAllowed`, `TrackingNotAllowed`, `Unset` |
| `Header.UpgradeInsecureRequests`   | `upgrade-insecure-requests`  | `upgrade: Boolean`                                  |
| `Header.ClearSiteData`             | `clear-site-data`            | `directives: Chunk[String]`                         |

### WebSocket

| Type                              | Wire name                  | Shape                       |
| --------------------------------- | -------------------------- | --------------------------- |
| `Header.SecWebSocketAccept`       | `sec-websocket-accept`      | `value: String`              |
| `Header.SecWebSocketExtensions`   | `sec-websocket-extensions`  | `value: String`              |
| `Header.SecWebSocketKey`          | `sec-websocket-key`         | `value: String`              |
| `Header.SecWebSocketLocation`     | `sec-websocket-location`    | `value: String`              |
| `Header.SecWebSocketOrigin`       | `sec-websocket-origin`      | `value: String`              |
| `Header.SecWebSocketProtocol`     | `sec-websocket-protocol`    | `protocols: Chunk[String]`   |
| `Header.SecWebSocketVersion`      | `sec-websocket-version`     | `version: String`            |

## Headers Without a Typed Model

Two mechanisms cover headers the catalog does not include, and the choice between them is whether you want a value or a type.

### `Header.Custom` — a name and a string

`Header.Custom` is a `Header` whose name and value you supply directly. Use it to write an arbitrary header through the same API as a built-in:

```scala
import zio.http.{Header, Headers}

val custom = Header.Custom("x-tenant-id", "acme-42")
```

It renders exactly what it holds, with no parsing on either side:

```scala
Headers.empty.add(custom).toList
// res59: List[Tuple2[String, String]] = List(("x-tenant-id", "acme-42"))
```

`Header.Custom` has no codec, so it cannot be passed to `Headers#get`. Reading it back means `Headers#rawGet`.

### A Custom `Header.Codec`

Implementing `Header.Codec[A]` gives a header both a domain type and typed reads. Nothing requires `A` to extend `Header` unless you also want `Headers#getLast`:

```scala
import zio.http.{Header, Headers}

final case class TenantId(value: String)

val tenantIdCodec: Header.Codec[TenantId] = new Header.Codec[TenantId] {
  def name: String = "x-tenant-id"

  def parse(value: String): Either[String, TenantId] =
    if (value.isEmpty) Left("x-tenant-id must not be empty")
    else Right(TenantId(value))

  def render(value: TenantId): String = value.value
}
```

The custom codec now works with the typed read path, cache included:

```scala
val headers = Headers("x-tenant-id" -> "acme-42")
// headers: Headers = Headers(x-tenant-id: acme-42)
headers.get(tenantIdCodec)
// res61: Option[TenantId] = Some(TenantId("acme-42"))
```

A rejected value is skipped like any other parse failure, so the collection read is `None` rather than the error message:

```scala
Headers("x-tenant-id" -> "").get(tenantIdCodec)
// res62: Option[TenantId] = None
tenantIdCodec.parse("")
// res63: Either[String, TenantId] = Left("x-tenant-id must not be empty")
```

Return a message describing what was expected. Parse errors reach users through whatever 400 response your handler builds, and the codec's message is the only description available.

:::tip[Hold the codec as a `val`]
The parse cache compares codec identity by reference. A codec constructed inline on every request is a different instance each time, so its cached values are never reused. Define it once as a `val` or an `object`.
:::

## Integration Points

`Header` and `Headers` are used by `Request` and `Response`, which each hold a `Headers` alongside their status or method, URL, and body — see [the HTTP model](./model.md). `Header.ContentType` wraps the `ContentType` type that `Body` also carries, and the media-type values inside it come from `zio-blocks-mediatype`.

Several headers hold values from elsewhere in the module rather than strings: `Allow`, `AccessControlAllowMethods`, and `AccessControlRequestMethod` hold `Method`, and `Chunk` is the sequence type throughout.

For deriving header codecs from a `Schema[A]` instead of writing them by hand, see [HTTP model schema](./schema.md), which builds `HeaderCodec` and `QueryCodec` instances from schemas. `ServerSentEvent` uses `Headers` indirectly through `Response`, and is documented in [Server-Sent Events](./server-sent-event.md).
