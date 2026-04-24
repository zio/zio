# XML

> `Xml` is a **sealed trait representing XML nodes**. It provides a type-safe, immutable representation of all valid XML document structures including elements, text nodes, CDATA sections, comments, and processing instructions.

`Xml` is a **sealed trait representing XML nodes**. It provides a type-safe, immutable representation of all valid XML document structures including elements, text nodes, CDATA sections, comments, and processing instructions.

```scala
sealed trait Xml {
  def xmlType: XmlType
  def is(xmlType: XmlType): Boolean
  def as(xmlType: XmlType): Option[xmlType.Type]
  def unwrap(xmlType: XmlType): Option[xmlType.Unwrap]
  def print: String
  def printPretty: String
  def select: XmlSelection
}
```

`Xml` supports:
- Type-safe representation of all XML node types
- Fluent navigation and querying with XmlSelection
- Schema-derived automatic codec generation
- Zero external dependencies
- Full cross-platform support (JVM and Scala.js)

## Overview

Zero-dependency XML codec for ZIO Blocks Schema with cross-platform support.

The schema-xml module provides automatic XML codec derivation for any type with a `Schema`. It includes a complete XML AST, fluent navigation API, and support for XML-specific features like attributes and namespaces.

Key features:

- **Zero Dependencies**: No external XML libraries required
- **Cross-Platform**: Full support for JVM and Scala.js
- **Schema-Based**: Automatic codec derivation from Schema definitions
- **XML AST**: Complete representation of XML documents
- **Fluent API**: Navigation and transformation with XmlSelection
- **Attributes**: First-class support for XML attributes via annotations
- **Namespaces**: XML namespace support with prefix handling

## Installation

To use the schema-xml module, add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-xml" % "0.0.14"
```

## Basic Usage

Start by deriving an XML codec from your Schema definition:

### Deriving Codecs

To create an XML codec, use `Schema[A].derive(XmlFormat)`:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Derive XML codec using the unified format API
val codec = Schema[Person].derive(XmlFormat)
```

### Encoding to XML

Encode your values to XML using the codec:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Schema[Person].derive(XmlFormat)
val person = Person("Alice", 30)
```

Encode to XML bytes:

```scala
val bytes: Array[Byte] = codec.encode(person)
// bytes: Array[Byte] = Array(
//   60,
//   80,
//   101,
//   114,
//   115,
//   111,
//   110,
//   62,
//   60,
//   110,
//   97,
//   109,
//   101,
//   62,
//   65,
//   108,
//   105,
//   99,
//   101,
//   60,
//   47,
//   110,
//   97,
//   109,
//   101,
//   62,
//   60,
//   97,
//   103,
//   101,
//   62,
//   51,
//   48,
//   60,
//   47,
//   97,
//   103,
//   101,
//   62,
//   60,
//   47,
//   80,
//   101,
//   114,
//   115,
//   111,
//   110,
//   62
// )
```

Encode to XML string:

```scala
val xmlString: String = codec.encodeToString(person)
// xmlString: String = "<Person><name>Alice</name><age>30</age></Person>"
```

Encode to pretty-printed XML:

```scala
val prettyXml = codec.encodeToString(person, WriterConfig.pretty)
// prettyXml: String = """<Person>
//   <name>Alice</name>
//   <age>30</age>
// </Person>"""
```

### Decoding from XML

Decode XML strings or bytes back to your typed values:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Schema[Person].derive(XmlFormat)

// Decode from XML string
val xml = "<Person><name>Alice</name><age>30</age></Person>"
```

Decode the XML string and see the result:

```scala
val result: Either[SchemaError, Person] = codec.decode(xml)
// result: Either[SchemaError, Person] = Right(
//   Person(name = "Alice", age = 30)
// )
```

You can also decode from bytes:

```scala
val bytes = xml.getBytes("UTF-8")
```

```scala
val fromBytes: Either[SchemaError, Person] = codec.decode(bytes)
// fromBytes: Either[SchemaError, Person] = Right(
//   Person(name = "Alice", age = 30)
// )
```

## XML AST

The `Xml` ADT represents all valid XML node types:

```
Xml
 ├── Xml.Element          (element with name, attributes, children)
 ├── Xml.Text             (character data)
 ├── Xml.CData            (unparsed character data)
 ├── Xml.Comment          (XML comment)
 └── Xml.ProcessingInstruction (processing instruction)
```

### Creating XML Nodes

Construct XML nodes directly using the case class constructors:

```scala

// Create elements
val simple = Xml.Element("person")
val withChildren = Xml.Element("person", Xml.Text("Alice"))

// Create with XmlName (for namespaces)
val namespaced = Xml.Element(
  XmlName("person", "http://example.com"),
  Chunk.empty,
  Chunk.empty
)

// Create text nodes
val text = Xml.Text("Hello, World!")
val cdata = Xml.CData("<script>...</script>")

// Create comments and processing instructions
val comment = Xml.Comment("This is a comment")
val pi = Xml.ProcessingInstruction("xml-stylesheet", "href=\"style.css\"")
```

### XmlName

`XmlName` represents an element or attribute name with optional namespace. Create instances with different namespace configurations:

```scala

// Local name only
val simple = XmlName("person")
simple.localName     // "person"
simple.namespace     // ""
simple.prefix        // ""

// With namespace URI
val ns = XmlName("person", "http://example.com/ns")

// With prefix (for prefixed elements like atom:feed)
val prefixed = XmlName("feed", Some("atom"), None)
prefixed.localName              // "feed"
prefixed.prefix.contains("atom") // true
```

## XmlBuilder

Construct XML documents programmatically with a fluent API:

```scala

// Build an element with attributes and children
val doc = XmlBuilder.element("person")
  .attr("id", "123")
  .attr("status", "active")
  .child(XmlBuilder.element("name").text("Alice").build)
  .child(XmlBuilder.element("age").text("30").build)
  .build

// Result:
// <person id="123" status="active">
//   <name>Alice</name>
//   <age>30</age>
// </person>

// Create other node types
val textNode = XmlBuilder.text("content")
val cdataNode = XmlBuilder.cdata("<![CDATA[raw content]]>")
val commentNode = XmlBuilder.comment("comment text")
```

## Configuration

The schema-xml module provides configuration options for both parsing and writing:

### WriterConfig

Use `WriterConfig` to control XML output formatting:

```scala

// Compact output (default)
val compact = WriterConfig.default
// <Person><name>Alice</name></Person>

// Pretty-printed with 2-space indentation
val pretty = WriterConfig.pretty
// <Person>
//   <name>Alice</name>
// </Person>

// With XML declaration
val withDecl = WriterConfig.withDeclaration
// <?xml version="1.0" encoding="UTF-8"?>
// <Person><name>Alice</name></Person>

// Custom configuration
val custom = WriterConfig(
  indentStep = 4,
  includeDeclaration = true,
  encoding = "UTF-8"
)
```

| Option | Default | Description |
|--------|---------|-------------|
| `indentStep` | `0` | Spaces per indentation level (0 = compact) |
| `includeDeclaration` | `false` | Include XML declaration |
| `encoding` | `"UTF-8"` | Character encoding in declaration |

### ReaderConfig

Controls XML parsing behavior and security limits:

```scala

// Default configuration
val default = ReaderConfig.default

// Custom limits
val custom = ReaderConfig(
  maxDepth = 100,
  maxAttributes = 50,
  maxTextLength = 1000000,
  preserveWhitespace = true
)
```

| Option | Default | Description |
|--------|---------|-------------|
| `maxDepth` | `1000` | Maximum element nesting depth |
| `maxAttributes` | `1000` | Maximum attributes per element |
| `maxTextLength` | `10000000` | Maximum text content length |
| `preserveWhitespace` | `false` | Preserve whitespace in text nodes |

## Attributes

Encode case class fields as XML attributes using the `@xmlAttribute` annotation:

```scala

case class Person(
  @xmlAttribute() id: String,
  @xmlAttribute("status") active: String,
  name: String,
  age: Int
)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Schema[Person].derive(XmlFormat)
val person = Person("123", "active", "Alice", 30)
```

Encode the person with attributes:

```scala
val xml = codec.encodeToString(person)
// xml: String = "<Person><id>123</id><active>active</active><name>Alice</name><age>30</age></Person>"
```

The `@xmlAttribute` annotation accepts an optional custom name:

- `@xmlAttribute()` - Uses the field name as the attribute name
- `@xmlAttribute("customName")` - Uses the provided name as the attribute name

## Namespaces

Support for XML namespaces with the `@xmlNamespace` annotation:

```scala

@xmlNamespace(uri = "http://www.w3.org/2005/Atom", prefix = "atom")
case class Feed(
  title: String,
  updated: String
)

object Feed {
  implicit val schema: Schema[Feed] = Schema.derived
}

val codec = Schema[Feed].derive(XmlFormat)
val feed = Feed("My Blog", "2024-01-01T00:00:00Z")
```

Encode the feed with a namespace prefix:

```scala
val xml = codec.encodeToString(feed)
// xml: String = "<Feed><title>My Blog</title><updated>2024-01-01T00:00:00Z</updated></Feed>"
```

Without a prefix (default namespace):

```scala
@xmlNamespace(uri = "http://www.w3.org/2005/Atom")
case class Feed(title: String)

object Feed {
  implicit val schema: Schema[Feed] = Schema.derived
}

val codec = Schema[Feed].derive(XmlFormat)
val feed = Feed("My Blog")
```

Encode the feed with default namespace:

```scala
val xml = codec.encodeToString(feed)
// xml: String = "<Feed><title>My Blog</title></Feed>"
```

## XmlSelection

`XmlSelection` provides a fluent API for navigating and querying XML structures:

### Navigation

Navigate to child elements, filter by type, and extract content:

```scala

val xml = XmlReader.read("""
<library>
  <books>
    <book id="1">
      <title>Functional Programming</title>
      <author>Alice</author>
    </book>
    <book id="2">
      <title>Advanced Scala</title>
      <author>Bob</author>
    </book>
  </books>
</library>
""")

// Navigate to child elements
val books = xml.select.get("library").get("books")

// Navigate by index
val firstBook = books.get("book")(0)
```

Extract text content from the first book:

```scala
val title: Either[SchemaError, String] = firstBook.get("title").text
// title: Either[SchemaError, String] = Left(
//   SchemaError(
//     List(
//       Message(
//         source = DynamicOptic(IndexedSeq()),
//         details = "Expected single value but got 0"
//       )
//     )
//   )
// )
```

You can also navigate descendants recursively:

```scala
val allTitles = xml.select.descendant("title")
// allTitles: XmlSelection = XmlSelection(
//   Right(
//     IndexedSeq(
//       Element(
//         name = XmlName(localName = "title", prefix = None, namespace = None),
//         attributes = IndexedSeq(),
//         children = IndexedSeq(Text("Functional Programming"))
//       ),
//       Element(
//         name = XmlName(localName = "title", prefix = None, namespace = None),
//         attributes = IndexedSeq(),
//         children = IndexedSeq(Text("Advanced Scala"))
//       )
//     )
//   )
// )
```

### Filtering

Filter selections by node type or custom predicates:

```scala

val selection: XmlSelection = ???

// Filter by type
val elements = selection.elements
val texts = selection.texts
val comments = selection.comments

// Custom filtering
val filtered = selection.filter(xml => xml.is(XmlType.Element))
```

### Terminal Operations

Execute a selection to extract values or convert to other formats:

```scala

val selection: XmlSelection = ???

// Get single value (fails if not exactly one)
val one: Either[SchemaError, Xml] = selection.one

// Get any value (first of many)
val any: Either[SchemaError, Xml] = selection.any

// Get all values as a single XML element
val all: Either[SchemaError, Xml] = selection.all

// Convert to chunk
val chunk = selection.toChunk

// Extract text content
val text: Either[SchemaError, String] = selection.text
val allText: String = selection.textContent
```

### Combinators

Combine and transform selections using monadic operations:

```scala

val selection1: XmlSelection = ???
val selection2: XmlSelection = ???

// Map over selections
val mapped = selection1.map(xml => xml)

// FlatMap for chaining
val nested = selection1.flatMap(xml => XmlSelection.succeed(xml))

// Combine selections
val combined = selection1 ++ selection2

// Alternative on failure
val withFallback = selection1.orElse(selection2)
```

## XmlPatch

`XmlPatch` provides composable XML modification operations:

### Creating Patches

Create patches for add, remove, replace, and attribute operations:

```scala

val path = p".library.books.book"

// Add content
val addPatch = XmlPatch.add(
  path,
  XmlBuilder.element("book").attr("id", "3").build,
  XmlPatch.Position.AppendChild
)

// Remove element
val removePatch = XmlPatch.remove(path)

// Replace element
val replacePatch = XmlPatch.replace(
  path,
  XmlBuilder.element("book").attr("id", "999").build
)

// Set attribute
val attrPatch = XmlPatch.setAttribute(path, "featured", "true")

// Remove attribute
val removeAttrPatch = XmlPatch.removeAttribute(path, "id")
```

### Position Options

Position options control where new content is inserted relative to the target:

```scala

Position.Before         // Insert before the target element
Position.After          // Insert after the target element
Position.PrependChild   // Insert as first child of target
Position.AppendChild    // Insert as last child of target
```

### Applying Patches

Apply a patch to an XML document to produce a modified result:

```scala

val xml: Xml = ???
val patch = XmlPatch.setAttribute(p".person", "active", "true")

// Apply the patch
val result: Either[SchemaError, Xml] = patch(xml)
```

### Composing Patches

Combine multiple patches to apply transformations in sequence:

```scala

val patch1 = XmlPatch.setAttribute(p".person", "id", "123")
val patch2 = XmlPatch.add(
  p".person",
  XmlBuilder.element("email").text("alice@example.com").build,
  XmlPatch.Position.AppendChild
)

// Compose patches - applies patch1, then patch2
val combined = patch1 ++ patch2
```

## Extension Syntax

When a `Schema` is in scope, use convenient extension methods:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val person = Person("Alice", 30)

// Encode to XML AST
val xml: Xml = person.toXml

// Encode to XML string
val xmlString: String = person.toXmlString
// <Person><name>Alice</name><age>30</age></Person>

// Encode to bytes
val bytes: Array[Byte] = person.toXmlBytes

// Decode from XML string
val parsed: Either[SchemaError, Person] = 
  "<Person><name>Bob</name><age>25</age></Person>".fromXml[Person]

// Decode from bytes
val fromBytes: Either[SchemaError, Person] = bytes.fromXml[Person]
```

## Printing XML

Format XML documents using compact or pretty-printed output. You can convert XML to string with different formatting options:

```scala

val xml = Xml.Element("person", Xml.Element("name", Xml.Text("Alice")))
```

Compact output:

```scala
val compact: String = xml.print
// compact: String = "<person><name>Alice</name></person>"
```

Pretty-printed output:

```scala
val pretty: String = xml.printPretty
// pretty: String = """<person>
//   <name>Alice</name>
// </person>"""
```

Custom configuration:

```scala
val custom: String = xml.print(WriterConfig(indentStep = 4))
```

## Type Testing and Access

Test and extract values from XML nodes using type guards and unwrapping:

```scala

val xml: Xml = Xml.Element("person")

// Type testing
xml.is(XmlType.Element)  // true
xml.is(XmlType.Text)     // false

// Type narrowing (returns Option)
val elem: Option[Xml.Element] = xml.as(XmlType.Element)

// Value extraction (returns Option)
val (name, attrs, children) = xml.unwrap(XmlType.Element).get
```

## Supported Types

All standard ZIO Blocks Schema types are supported:

**Numeric Types**:
- `Boolean`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `Char`
- `BigInt`, `BigDecimal`

**Text Types**:
- `String`

**Special Types**:
- `Unit`, `UUID`, `Currency`

**Java Time Types**:
- `Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`
- `OffsetTime`, `OffsetDateTime`, `ZonedDateTime`
- `Duration`, `Period`
- `Year`, `YearMonth`, `MonthDay`
- `DayOfWeek`, `Month`
- `ZoneId`, `ZoneOffset`

**Composite Types**:
- Records (case classes)
- Variants (sealed traits)
- Sequences (`List`, `Vector`, `Set`, etc.)
- Maps (`Map[K, V]`)
- Options (`Option[A]`)
- Wrappers (newtypes)

## XmlCodec

`XmlCodec[A]` is the low-level codec interface that bridges Schema definitions with XML serialization. While usually derived automatically, you can work with it directly:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Get the underlying binary codec
val codec: XmlCodec[Person] = Schema[Person].derive(XmlCodecDeriver)

// Encode to Xml directly
val person = Person("Alice", 30)
val xml: Xml = codec.encodeValue(person)

// Decode from Xml directly
val decoded: Person = codec.decodeValue(xml)
```

**XmlCodec supports all Schema types:**
- Primitives (Int, String, Boolean, etc.)
- Java time types (Instant, LocalDate, Duration, etc.)
- Records (case classes with field-level configuration)
- Variants (sealed traits with discriminators)
- Collections (List, Vector, Map, etc.)
- Optional fields (Option[A])
- Custom wrappers and dynamic values

## Error Handling

All decoding operations return `Either[SchemaError, A]` or `Either[SchemaError, A]`. The `SchemaError` type provides detailed error information:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Schema[Person].derive(XmlFormat)

// Decoding invalid XML
val invalid = "<Person><name>Alice</name></Person>"  // missing age
val result = codec.decode(invalid)

result match {
  case Right(person) => println(s"Decoded: $person")
  case Left(error) =>
    println(s"Error: ${error.getMessage}")
    // Error information includes parse location and context
}
```

`SchemaError` provides detailed error information for debugging:

```scala

val error = SchemaError("Parse failed")

// Error message
val message: String = error.getMessage

// Get error message for inspection
val errorMsg: String = error.getMessage
```

Error handling best practices:

```scala

case class Config(database: String, port: Int)
object Config {
  implicit val schema: Schema[Config] = Schema.derived
}

def loadConfig(xml: String): Either[String, Config] = {
  val codec = Schema[Config].derive(XmlFormat)
  codec.decode(xml).left.map { error =>
    s"Configuration error: ${error.getMessage}\nCheck XML format and required fields."
  }
}
```

## Cross-Platform Support

The XML module works across all platforms:

- **JVM** - Full functionality
- **Scala.js** - Browser and Node.js

All features including parsing, writing, navigation, and patching work identically on both platforms.

## Examples

These examples demonstrate common use cases and patterns with the XML module:

### Complete Example with Attributes and Namespaces

Define a schema with attributes and namespaces, then encode and decode:

```scala

@xmlNamespace(uri = "http://www.w3.org/2005/Atom", prefix = "atom")
case class Entry(
  @xmlAttribute() id: String,
  title: String,
  updated: String,
  author: Author
)

case class Author(name: String, email: String)

object Entry {
  implicit val authorSchema: Schema[Author] = Schema.derived
  implicit val schema: Schema[Entry] = Schema.derived
}

val codec = Schema[Entry].derive(XmlFormat)

val entry = Entry(
  id = "entry-1",
  title = "First Post",
  updated = "2024-01-01T00:00:00Z",
  author = Author("Alice", "alice@example.com")
)
```

Encode the entry with pretty printing:

```scala
val xmlStr = codec.encodeToString(entry, WriterConfig.pretty)
// xmlStr: String = """<Entry>
//   <id>entry-1</id>
//   <title>First Post</title>
//   <updated>2024-01-01T00:00:00Z</updated>
//   <author>
//     <name>Alice</name>
//     <email>alice@example.com</email>
//   </author>
// </Entry>"""
```

Decode the XML back to a typed value:

```scala
val decoded = codec.decode(xmlStr)
// decoded: Either[SchemaError, Entry] = Right(
//   Entry(
//     id = "entry-1",
//     title = "First Post",
//     updated = "2024-01-01T00:00:00Z",
//     author = Author(name = "Alice", email = "alice@example.com")
//   )
// )
```

### Navigation and Transformation

Find elements, extract data, and apply patches to modify XML:

```scala

val xmlString = """
<library>
  <books>
    <book id="1">
      <title>Functional Programming</title>
      <price>49.99</price>
    </book>
    <book id="2">
      <title>Advanced Scala</title>
      <price>59.99</price>
    </book>
  </books>
</library>
"""

val xml = XmlReader.read(xmlString)

// Find all books
val books = xml.select.get("library").get("books").get("book")

// Extract all titles
val titles = books.get("title").toChunk.map { titleElem =>
  titleElem.asInstanceOf[Xml.Element].children.head match {
    case Xml.Text(text) => text
    case _ => ""
  }
}
// Chunk("Functional Programming", "Advanced Scala")

// Apply a patch to add a new book
val patch = XmlPatch.add(
  p".library.books",
  XmlBuilder.element("book")
    .attr("id", "3")
    .child(XmlBuilder.element("title").text("ZIO Essentials").build)
    .child(XmlBuilder.element("price").text("39.99").build)
    .build,
  XmlPatch.Position.AppendChild
)

val updated = patch(xml)
```

## Real-World Examples

Learn by examining practical examples of XML codecs in action:

### RSS Feed Parsing

Parse RSS feeds by defining a schema and decoding XML:

```scala

@xmlNamespace(uri = "http://www.rss.org/", prefix = "rss")
case class Item(
  @xmlAttribute() guid: String,
  title: String,
  link: String,
  pubDate: String,
  description: String
)

@xmlNamespace(uri = "http://www.rss.org/", prefix = "rss")
case class Channel(
  title: String,
  link: String,
  description: String,
  items: List[Item]
)

object Channel {
  implicit val itemSchema: Schema[Item] = Schema.derived
  implicit val schema: Schema[Channel] = Schema.derived
}

// Parse RSS feed from string
val feedXml = """<rss:Channel xmlns:rss="http://www.rss.org/">
  <title>Tech Blog</title>
  <link>https://example.com</link>
  <description>Latest tech articles</description>
  <items>
    <Item guid="1">
      <title>Functional Programming in Scala</title>
      <link>https://example.com/fp</link>
      <pubDate>2024-01-01</pubDate>
      <description>Deep dive into FP concepts</description>
    </Item>
  </items>
</rss:Channel>"""

val codec = Schema[Channel].derive(XmlFormat)
val result: Either[SchemaError, Channel] = codec.decode(feedXml)
```

### Atom Feed with Advanced Features

Work with Atom feeds using attributes, multiple entries, and custom configurations:

```scala

@xmlNamespace(uri = "http://www.w3.org/2005/Atom", prefix = "atom")
case class Entry(
  @xmlAttribute() id: String,
  title: String,
  author: String,
  updated: String,
  @xmlAttribute("href") link: String
)

@xmlNamespace(uri = "http://www.w3.org/2005/Atom", prefix = "atom")
case class Feed(
  @xmlAttribute() id: String,
  title: String,
  updated: String,
  entries: List[Entry]
)

object Feed {
  implicit val entrySchema: Schema[Entry] = Schema.derived
  implicit val schema: Schema[Feed] = Schema.derived
}

// Encode feed to XML with custom formatting
val feed = Feed(
  id = "urn:uuid:60a76c80-d399-11d9-b91C-0003939e0af6",
  title = "Example Feed",
  updated = "2024-01-01T18:30:02Z",
  entries = List(
    Entry(
      id = "urn:uuid:1225c695-cfb8-4ebb-aaaa-80da344efa6a",
      title = "Atom-Powered Robots Run Amok",
      author = "John Doe",
      updated = "2024-01-01T18:30:02Z",
      link = "http://example.org/2024/01/entry"
    )
  )
)

val codec = Schema[Feed].derive(XmlFormat)
val xmlOutput = codec.encodeToString(feed, WriterConfig.pretty)
```

### Sitemap XML

Generate sitemap XML with URLs and optional metadata fields:

```scala

case class Url(
  loc: String,
  lastmod: Option[String],
  changefreq: Option[String],
  priority: Option[Double]
)

case class Urlset(
  urls: List[Url]
)

object Urlset {
  implicit val urlSchema: Schema[Url] = Schema.derived
  implicit val schema: Schema[Urlset] = Schema.derived
}

// Build and encode sitemap
val sitemap = Urlset(List(
  Url("https://example.com", Some("2024-01-01"), Some("monthly"), Some(1.0)),
  Url("https://example.com/about", Some("2024-01-01"), Some("monthly"), Some(0.8)),
  Url("https://example.com/contact", None, Some("monthly"), Some(0.5))
))

val codec = Schema[Urlset].derive(XmlFormat)
val sitemapXml = codec.encodeToString(sitemap, WriterConfig(
  indentStep = 2,
  includeDeclaration = true
))
```
