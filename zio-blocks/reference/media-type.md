# MediaType

> `MediaType` is a **type-safe representation of IANA media types** (also known as MIME types). It captures structured metadata about content types including compressibility, binary/text classification, and associated file extensions.

`MediaType` is a **type-safe representation of IANA media types** (also known as MIME types). It captures structured metadata about content types including compressibility, binary/text classification, and associated file extensions. 

ZIO Blocks MediaType is designed to be a comprehensive and efficient implementation for handling media types in Scala applications, especially those involving HTTP content negotiation, file handling, and data serialization.

`MediaType`:

- is a zero-dependency data type in the `zio-blocks-mediatype` module
- ships with 2,600+ predefined IANA media types auto-generated from the [mime-db](https://github.com/jshttp/mime-db) database
- provides a `mediaType"..."` string interpolator with compile-time validation
- supports wildcard and case-insensitive matching
- is cross-platform (JVM and Scala.js) and cross-version (Scala 2.13 and 3.x)

```scala
final case class MediaType(
  mainType: String,
  subType: String,
  compressible: Boolean = false,
  binary: Boolean = false,
  fileExtensions: List[String] = Nil,
  extensions: Map[String, String] = Map.empty,
  parameters: Map[String, String] = Map.empty
)
```

## Motivation

Media types are fundamental to content negotiation in HTTP, file type detection, and serialization format selection. Working with raw strings like `"application/json"` is error-prone — typos go undetected, metadata (is it compressible? binary? what file extensions?) must be tracked separately, and matching logic must account for wildcards and case insensitivity.

`MediaType` solves these problems by providing:

- **Structured representation** — main type, subtype, and parameters as distinct fields
- **Rich metadata** — compressibility, binary/text classification, and file extensions baked in
- **Compile-time safety** — the `mediaType"..."` interpolator catches malformed types at compile time
- **Correct matching** — wildcard and case-insensitive matching with parameter awareness

```text
                    MediaType
           ┌──────────┼──────────┐
        mainType    subType   parameters
           │          │          │
     "application"  "json"   Map("charset" -> "utf-8")
           │
     ┌─────┴──────────────────────────────────┐
     │  compressible = true                   │
     │  binary       = false                  │
     │  fileExtensions = List("json", "map")  │
     └────────────────────────────────────────┘
```

A quick example:

```scala

// Compile-time validated media type
val json = mediaType"application/json"

// Look up by file extension
val detected = MediaType.forFileExtension("png")
// Some(MediaType("image", "png", binary = true, ...))

// Wildcard matching
val textAny = mediaType"text/*"
val html    = mediaType"text/html"
textAny.matches(html) // true
```

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-mediatype" % "0.0.33"
```

For cross-platform projects (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-mediatype" % "0.0.33"
```

Supported Scala versions: 2.13.x and 3.x.

## Creating Instances

### Direct Construction

We can create a `MediaType` by specifying the main type and subtype directly. All other fields have sensible defaults:

```scala

// Minimal — just mainType and subType
val plain = MediaType("text", "plain")
// compressible = false, binary = false, fileExtensions = Nil, ...

// With all fields
val json = MediaType(
  mainType       = "application",
  subType        = "json",
  compressible   = true,
  binary         = false,
  fileExtensions = List("json", "map"),
  extensions     = Map("source" -> "iana"),
  parameters     = Map("charset" -> "utf-8")
)
```

### Parsing from a String

`MediaType.parse` parses a standard media type string in the format `mainType/subType[; key=value]*`:

```scala

// Simple type
val json: Either[String, MediaType] = MediaType.parse("application/json")
// Right(MediaType("application", "json", compressible = true, ...))

// With parameters
val html = MediaType.parse("text/html; charset=utf-8")
// Right(MediaType("text", "html", ..., parameters = Map("charset" -> "utf-8")))

// Predefined instances are reused — parse returns the same object
val parsed = MediaType.parse("application/json").toOption.get
parsed eq MediaTypes.application.`json` // true (reference equality)

// Invalid input returns Left with an error message
MediaType.parse("")               // Left("Invalid media type: cannot be empty")
MediaType.parse("applicationjson") // Left("Invalid media type: must contain '/' separator")
MediaType.parse("/json")          // Left("Invalid media type: main type cannot be empty")
MediaType.parse("application/")   // Left("Invalid media type: subtype cannot be empty")
```

### Unsafe Parsing

When we are certain the input is valid, `unsafeFromString` returns a `MediaType` directly or throws an `IllegalArgumentException`:

```scala

val json = MediaType.unsafeFromString("application/json")
// MediaType("application", "json", compressible = true, ...)

// Throws IllegalArgumentException for invalid input:
// MediaType.unsafeFromString("invalid")
```

### String Interpolator

The `mediaType"..."` interpolator validates the media type at compile time. Invalid types produce compile errors, not runtime failures:

```scala

val json     = mediaType"application/json"
val htmlUtf8 = mediaType"text/html; charset=utf-8"
val wildcard = mediaType"*/*"
val vendor   = mediaType"text/vnd.api+json"
```

The interpolator reuses predefined instances when available (reference equality with `MediaTypes` constants). It does not support variable interpolation — only literal strings are accepted.

:::note
The `mediaType` interpolator is available after importing `zio.blocks.mediatype._`. Invalid inputs produce clear compile-time errors:

```text
mediaType""                → "Invalid media type: cannot be empty"
mediaType"applicationjson" → "Invalid media type: must contain '/' separator"
mediaType"/json"           → "Invalid media type: main type cannot be empty"
mediaType"application/"    → "Invalid media type: subtype cannot be empty"
```
:::

### File Extension Lookup

`MediaType.forFileExtension` finds a `MediaType` by its associated file extension. The lookup is case-insensitive and strips a leading `.` if present:

```scala

MediaType.forFileExtension("json")  // Some(MediaType("application", "json", ...))
MediaType.forFileExtension(".html") // Some(MediaType("text", "html", ...))
MediaType.forFileExtension("PNG")   // Some(MediaType("image", "png", ...))
MediaType.forFileExtension("jpg")   // Some(MediaType("image", "jpeg", ...))
MediaType.forFileExtension("pdf")   // Some(MediaType("application", "pdf", ...))

MediaType.forFileExtension("xyz123") // None (unknown extension)
MediaType.forFileExtension("")       // None
MediaType.forFileExtension(".")      // None
```

## Predefined Media Types

The `MediaTypes` object contains 2,600+ predefined media type constants auto-generated from the [jshttp/mime-db](https://github.com/jshttp/mime-db) database. Types are organized by main type category:

| Category     | Object                    | Examples                             |
|--------------|---------------------------|--------------------------------------|
| Application  | `MediaTypes.application`  | `json`, `pdf`, `xml`, `octet-stream` |
| Audio        | `MediaTypes.audio`        | `mpeg`, `ogg`, `wav`                 |
| Chemical     | `MediaTypes.chemical`     | `x-cif`, `x-pdb`                     |
| Font         | `MediaTypes.font`         | `woff`, `woff2`, `otf`               |
| Image        | `MediaTypes.image`        | `png`, `jpeg`, `gif`, `svg+xml`      |
| Message      | `MediaTypes.message`      | `rfc822`, `partial`                  |
| Model        | `MediaTypes.model`        | `gltf+json`, `stl`                   |
| Multipart    | `MediaTypes.multipart`    | `form-data`, `mixed`                 |
| Text         | `MediaTypes.text`         | `html`, `css`, `plain`, `csv`        |
| Video        | `MediaTypes.video`        | `mp4`, `webm`, `ogg`                 |
| X-Conference | `MediaTypes.x_conference` | `x-cooltalk`                         |
| X-Shader     | `MediaTypes.x_shader`     | `x-vertex`, `x-fragment`             |
| Wildcard     | `MediaTypes.any`          | `*/*`                                |

### Accessing Predefined Types

Since many subtype names contain special characters (hyphens, dots, plus signs), predefined constants use backtick identifiers:

```scala

// Common application types
val json      = MediaTypes.application.`json`
val pdf       = MediaTypes.application.`pdf`
val xmlType   = MediaTypes.application.`xml`

// Common text types
val html      = MediaTypes.text.`html`
val css       = MediaTypes.text.`css`
val plain     = MediaTypes.text.`plain`

// Common image types
val png       = MediaTypes.image.`png`
val jpeg      = MediaTypes.image.`jpeg`

// Wildcard (matches any type)
val any       = MediaTypes.any
```

### Listing All Types

Each category object has an `all` field returning a `List[MediaType]` of all types in that category. The top-level `allMediaTypes` aggregates every category:

```scala

// All types in a category
val appTypes: List[zio.blocks.mediatype.MediaType] = MediaTypes.application.all

// All 2,600+ predefined types
val everything: List[zio.blocks.mediatype.MediaType] = MediaTypes.allMediaTypes
```

### Predefined Metadata

Each predefined instance comes with rich metadata from the IANA registry:

```scala

val json = MediaTypes.application.`json`
json.compressible   // true
json.binary         // false
json.fileExtensions // List("json", "map")

val jpeg = MediaTypes.image.`jpeg`
jpeg.compressible   // false
jpeg.binary         // true
jpeg.fileExtensions // List("jpg", "jpeg", "jpe")

val html = MediaTypes.text.`html`
html.compressible   // true
html.binary         // false
html.fileExtensions // List("html", "htm", "shtml")
```

## Core Operations

### `fullType`

Returns the complete media type string by combining `mainType` and `subType` with a `/` separator:

```scala

val mt = MediaType("application", "json")
mt.fullType // "application/json"

MediaType("*", "*").fullType  // "*/*"
MediaType("text", "*").fullType // "text/*"
```

### `matches`

Compares two `MediaType` values with support for wildcards, case insensitivity, and parameter matching:

```scala
final case class MediaType(...) {
  def matches(other: MediaType, ignoreParameters: Boolean = false): Boolean
}
```

The matching rules are:

1. **Wildcard matching** — `"*"` in either `mainType` or `subType` matches any value
2. **Case-insensitive** — `"APPLICATION/JSON"` matches `"application/json"`
3. **Parameter subset** — when `ignoreParameters = false` (the default), all parameters in `this` must exist in `other` with matching values (case-insensitive). Extra parameters in `other` are allowed.

```scala

val json    = mediaType"application/json"
val textAll = mediaType"text/*"
val html    = mediaType"text/html"
val any     = mediaType"*/*"

// Exact match
json.matches(json) // true

// Wildcard matching
any.matches(json)      // true — */* matches anything
textAll.matches(html)  // true — text/* matches text/html
textAll.matches(json)  // false — text/* does not match application/json

// Case-insensitive
MediaType("APPLICATION", "JSON").matches(json) // true

// Parameter matching
val htmlUtf8  = MediaType("text", "html", parameters = Map("charset" -> "utf-8"))
val htmlLatin = MediaType("text", "html", parameters = Map("charset" -> "iso-8859-1"))
val htmlFull  = MediaType("text", "html", parameters = Map("charset" -> "utf-8", "boundary" -> "xxx"))

htmlUtf8.matches(htmlLatin)                          // false — charset mismatch
htmlUtf8.matches(htmlLatin, ignoreParameters = true) // true  — parameters ignored
htmlUtf8.matches(htmlFull)                           // true  — subset match (charset matches)
```

### `forFileExtension`

Looks up a `MediaType` by file extension. The lookup is case-insensitive and strips a leading `.` if present:

```scala
object MediaType {
  def forFileExtension(ext: String): Option[MediaType]
}
```

```scala

MediaType.forFileExtension("json")  // Some(MediaType("application", "json", ...))
MediaType.forFileExtension(".html") // Some(MediaType("text", "html", ...))
MediaType.forFileExtension("PNG")   // Some(MediaType("image", "png", ...))
MediaType.forFileExtension("")      // None
MediaType.forFileExtension("xyz")   // None
```

:::tip
`forFileExtension` gives priority to `text/*` types when an extension maps to multiple types. For example, `"js"` maps to `text/javascript` rather than `application/javascript`.
:::

### `parse`

Parses a media type string into a `MediaType`, returning `Left` with an error message for invalid input:

```scala
object MediaType {
  def parse(s: String): Either[String, MediaType]
}
```

When the parsed type matches a predefined instance, that instance is returned (preserving reference equality and all metadata). Parameters from the input string are merged into the result:

```scala

// Returns predefined instance with full metadata
val json = MediaType.parse("application/json")
// Right(MediaType("application", "json", compressible=true, binary=false, ...))

// Parameters are parsed and attached
val result = MediaType.parse("multipart/form-data; boundary=abc; charset=utf-8")
// Right(MediaType(..., parameters = Map("boundary" -> "abc", "charset" -> "utf-8")))

// Unknown types get a fresh instance
val custom = MediaType.parse("custom/x-my-format")
// Right(MediaType("custom", "x-my-format"))

// Malformed parameters (no "=") are silently ignored
val partial = MediaType.parse("text/html; charset=utf-8; malformed")
// Right(MediaType(..., parameters = Map("charset" -> "utf-8")))
```

### `unsafeFromString`

Like `parse`, but throws `IllegalArgumentException` instead of returning `Left`:

```scala
object MediaType {
  def unsafeFromString(s: String): MediaType
}
```

For valid input, it returns the corresponding `MediaType` (reusing predefined instances when possible). For invalid input, it throws an exception with a descriptive message:

```scala

val json = MediaType.unsafeFromString("application/json")

// Throws IllegalArgumentException:
// MediaType.unsafeFromString("not-a-media-type")
```

## Advanced Usage

### Content Negotiation

We can use `matches` with wildcard types to implement HTTP-style content negotiation:

```scala

def negotiate(
  accept: List[MediaType],
  available: List[MediaType]
): Option[MediaType] =
  available.find(avail => accept.exists(_.matches(avail, ignoreParameters = true)))

val accept    = List(mediaType"text/*", mediaType"application/json")
val available = List(mediaType"application/xml", mediaType"application/json", mediaType"text/html")

negotiate(accept, available)
// Some(MediaType("application", "json", ...))
```

### File Type Detection

Combine `forFileExtension` with file path processing to detect content types:

```scala

def detectContentType(filename: String): Option[MediaType] = {
  val ext = filename.lastIndexOf('.') match {
    case -1 => ""
    case i  => filename.substring(i + 1)
  }
  MediaType.forFileExtension(ext)
}

detectContentType("report.pdf")   // Some(MediaType("application", "pdf", ...))
detectContentType("photo.jpg")    // Some(MediaType("image", "jpeg", ...))
detectContentType("data.json")    // Some(MediaType("application", "json", ...))
detectContentType("Makefile")     // None
```
