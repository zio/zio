# Docs Reference

> Complete API reference for the zio-blocks-docs module - a zero-dependency GitHub Flavored Markdown library.

# Docs Module Reference

Complete API reference for the zio-blocks-docs module - a zero-dependency GitHub Flavored Markdown library.

## Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-docs" % "0.0.33"
```

## Core Types

### Doc

The top-level document container. A `Doc` wraps a `Chunk[Block]` representing the document's block-level elements, plus optional metadata.

```scala
final case class Doc(blocks: Chunk[Block], metadata: Map[String, String] = Map.empty)
```

**Key methods:**
- `++`: Concatenate two documents (merges blocks and metadata, right wins on conflicts)
- `normalize`: Merge adjacent Text nodes and remove empty blocks
- `toHtml`: Render to full HTML5 document (with DOCTYPE, html, head, body tags)
- `toHtmlFragment`: Render to HTML content only (no html/head/body wrapper)
- `toTerminal`: Render with ANSI escape codes for terminal display
- `toString`: Render back to GFM Markdown

**Equality:** Two documents are equal if their normalized forms are equal.

**Example:**
```scala

val doc = Parser.parse("# Hello World").toOption.get
val markdown = doc.toString           // "# Hello World\n"
val html = doc.toHtml                 // Full HTML5 document
val fragment = doc.toHtmlFragment     // Just the content
val terminal = doc.toTerminal         // ANSI colored output
```

### Block

Block-level elements that make up a document:

| Variant | Description |
|---------|-------------|
| `Paragraph(content: Chunk[Inline])` | A paragraph of inline content |
| `Heading(level: HeadingLevel, content: Chunk[Inline])` | ATX heading (H1-H6) |
| `CodeBlock(info: Option[String], code: String)` | Fenced code block with optional language |
| `ThematicBreak` | Horizontal rule (`---`, `***`, `___`) |
| `BlockQuote(content: Chunk[Block])` | Quoted block content |
| `BulletList(items: Chunk[ListItem], tight: Boolean)` | Unordered list |
| `OrderedList(start: Int, items: Chunk[ListItem], tight: Boolean)` | Ordered list with start number |
| `ListItem(content: Chunk[Block], checked: Option[Boolean])` | List item, optionally a task item |
| `HtmlBlock(content: String)` | Raw HTML block |
| `Table(header: TableRow, alignments: Chunk[Alignment], rows: Chunk[TableRow])` | GFM table |

**Note on Lists:** The `tight` parameter indicates whether the list should be rendered without blank lines between items (tight) or with blank lines (loose).

### Inline

Inline elements within blocks:

| Variant | Description |
|---------|-------------|
| `Text(value: String)` | Plain text |
| `Code(value: String)` | Inline code (backticks) |
| `Emphasis(content: Chunk[Inline])` | Italic text (`*text*` or `_text_`) |
| `Strong(content: Chunk[Inline])` | Bold text (`**text**` or `__text__`) |
| `Strikethrough(content: Chunk[Inline])` | Strikethrough (`~~text~~`) |
| `Link(text: Chunk[Inline], url: String, title: Option[String])` | Hyperlink |
| `Image(alt: String, url: String, title: Option[String])` | Image |
| `HtmlInline(content: String)` | Raw inline HTML |
| `SoftBreak` | Soft line break (rendered as space in HTML) |
| `HardBreak` | Hard line break (two spaces or backslash before newline) |
| `Autolink(url: String, isEmail: Boolean)` | Auto-detected URL or email |

**Note:** Both top-level case classes and `Inline.X` nested variants exist for compatibility. They are treated identically.

### HeadingLevel

Heading levels H1 through H6:

```scala
sealed abstract class HeadingLevel(val value: Int)
object HeadingLevel {
  case object H1 extends HeadingLevel(1)
  case object H2 extends HeadingLevel(2)
  case object H3 extends HeadingLevel(3)
  case object H4 extends HeadingLevel(4)
  case object H5 extends HeadingLevel(5)
  case object H6 extends HeadingLevel(6)
  
  def fromInt(n: Int): Option[HeadingLevel]
  def unsafeFromInt(n: Int): HeadingLevel  // Throws on invalid input
}
```

**Example:**
```scala
HeadingLevel.fromInt(2)        // Some(H2)
HeadingLevel.fromInt(7)        // None
HeadingLevel.unsafeFromInt(3)  // H3
HeadingLevel.H1.value          // 1
```

### Alignment

Table column alignment:

```scala
sealed trait Alignment
object Alignment {
  case object None extends Alignment    // Default alignment (---)
  case object Left extends Alignment    // Left aligned (:---)
  case object Center extends Alignment  // Center aligned (:---:)
  case object Right extends Alignment   // Right aligned (---:)
}
```

### TableRow

A row in a table:

```scala
final case class TableRow(cells: Chunk[Chunk[Inline]])
```

Each cell contains a chunk of inline elements, allowing rich formatting within table cells.

## Parsing

### Parser.parse

Parse a Markdown string into a `Doc`:

```scala
object Parser {
  def parse(input: String): Either[ParseError, Doc]
}
```

**Example:**
```scala

val result = Parser.parse("# Hello\n\nThis is **bold**.")
// Right(Doc(Chunk(
//   Heading(H1, Chunk(Text("Hello"))),
//   Paragraph(Chunk(Text("This is "), Strong(Chunk(Text("bold"))), Text(".")))
// )))
```

### Supported Features

The parser supports all GitHub Flavored Markdown features:

- **ATX headings** (# to ######)
- **Fenced code blocks** (``` or ~~~)
- **Thematic breaks** (---, ***, ___)
- **Block quotes** (> prefix)
- **Bullet and ordered lists**
- **Task lists** (- [ ] and - [x])
- **Tables with alignment**
- **Inline formatting** (emphasis, strong, strikethrough, code)
- **Links and images**
- **Autolinks** (`<url>` or plain URLs)
- **HTML blocks and inline HTML**

### Not Supported

- **YAML frontmatter** (causes parse error)
- **Setext headings** (use ATX style with #)
- **Indented code blocks** (use fenced code blocks)
- **Link reference definitions**

### ParseError

Parsing error with location information:

```scala
final case class ParseError(
  message: String,
  line: Int,        // 1-based line number
  column: Int,      // 1-based column number
  input: String     // The line that caused the error
)
```

**Example:**
```scala
Parser.parse("---\ntitle: Test\n---") match {
  case Left(err) => 
    println(s"Error at line ${err.line}: ${err.message}")
    // "Error at line 1: Frontmatter is not supported"
  case Right(doc) => // Process doc
}
```

## Rendering

### Markdown Rendering

Render a `Doc` back to GFM Markdown:

```scala
object Renderer {
  def render(doc: Doc): String
  def renderBlock(block: Block): String
  def renderInlines(inlines: Chunk[Inline]): String
  def renderInline(inline: Inline): String
}
```

**Example:**
```scala
val doc = Parser.parse("# Title\n\nParagraph.").toOption.get
val markdown = Renderer.render(doc)
// "# Title\n\nParagraph.\n\n"
```

The rendered output is GFM-compliant and can be re-parsed to produce an equivalent AST.

### HTML Rendering

Render to HTML5-compliant HTML:

```scala
object HtmlRenderer {
  def render(doc: Doc): String         // Full HTML5 document
  def renderFragment(doc: Doc): String // Content only, no wrapper
  def renderBlock(block: Block): String
  def renderInlines(inlines: Chunk[Inline]): String
  def renderInline(inline: Inline): String
  def escape(s: String): String        // HTML entity escaping
}
```

**Example:**
```scala
val doc = Parser.parse("# Hello\n\n**Bold**").toOption.get

// Full document with <!DOCTYPE html>, <html>, <head>, <body>
val fullHtml = HtmlRenderer.render(doc)

// Just the content: HelloBold
val fragment = HtmlRenderer.renderFragment(doc)
```

**HTML Features:**
- Code blocks with language classes (`language-scala`, etc.)
- Tables with proper alignment styles
- Task list items with disabled checkboxes
- Proper HTML entity escaping for safety

### Terminal Rendering

Render with ANSI escape codes for colorful terminal display:

```scala
object TerminalRenderer {
  def render(doc: Doc): String
  def renderBlock(block: Block): String
  def renderInlines(inlines: Chunk[Inline]): String
  def renderInline(inline: Inline): String
}
```

**Example:**
```scala
val doc = Parser.parse("# Hello\n\nThis is **bold** and *italic*.").toOption.get
val terminal = TerminalRenderer.render(doc)
println(terminal)  // Displays with colors and formatting
```

**ANSI Styling:**
- **Headings:** Bold + colored (H1=red, H2=yellow, H3=green, H4=cyan, H5=blue, H6=magenta)
- **Code blocks:** Gray background
- **Inline code:** Gray background
- **Emphasis:** Italic
- **Strong:** Bold
- **Strikethrough:** Strike-through style
- **Links:** Blue + underlined
- **Block quotes:** Prefixed with │

## String Interpolator

### The md"..." Interpolator

Build documents with compile-time validated Markdown syntax:

```scala

val name = "World"
val greeting = md"# Hello $name"
// Doc(Chunk(Heading(H1, Chunk(Text("Hello World")))))

val items = List("one", "two", "three")
val list = md"""
# My List

${items.map(i => s"- $i").mkString("\n")}
"""
```

The interpolator:
- **Validates syntax at compile time** - invalid markdown causes compilation error
- **Requires ToMarkdown instances** for interpolated values
- **Supports multi-line markdown** with triple quotes

**Example with validation:**
```scala
// This won't compile - invalid heading level
val bad = md"####### Too many hashes"
// Error: Invalid markdown: Invalid heading level: 7 (max is 6)
```

### ToMarkdown Typeclass

Make custom types interpolatable:

```scala
trait ToMarkdown[-A] {
  def toMarkdown(a: A): Inline
}
```

**Built-in instances:**
- `String`, `Int`, `Long`, `Double`, `Boolean` → `Text`
- `Inline` → identity
- `Block` → rendered to markdown then wrapped as `Text`
- `List[A]`, `Vector[A]`, `Seq[A]`, `Chunk[A]` → comma-separated (where `A: ToMarkdown`)

**Custom instance example:**
```scala
case class User(name: String, email: String)

implicit val userToMarkdown: ToMarkdown[User] = user =>
  Text(s"${user.name} <${user.email}>")

val user = User("Alice", "alice@example.com")
val doc = md"Contact: $user"
// Doc(Chunk(Paragraph(Chunk(Text("Contact: Alice <alice@example.com>")))))
```

**Advanced example - custom formatting:**
```scala
case class CodeSnippet(lang: String, code: String)

implicit val codeSnippetToMarkdown: ToMarkdown[CodeSnippet] = snippet =>
  Text(s"```${snippet.lang}\n${snippet.code}\n```")

val snippet = CodeSnippet("scala", "val x = 42")
val doc = md"Here's an example:\n\n$snippet"
```

## Working with the AST

### Building Documents Programmatically

```scala

val doc = Doc(Chunk(
  Heading(HeadingLevel.H1, Chunk(Text("Title"))),
  Paragraph(Chunk(
    Text("This is "),
    Strong(Chunk(Text("important"))),
    Text(".")
  )),
  CodeBlock(Some("scala"), "val x = 42"),
  BulletList(Chunk(
    ListItem(Chunk(Paragraph(Chunk(Text("Item 1")))), None),
    ListItem(Chunk(Paragraph(Chunk(Text("Done")))), Some(true)),
    ListItem(Chunk(Paragraph(Chunk(Text("Todo")))), Some(false))
  ), tight = true)
))
```

### Concatenation

Combine documents with `++`:

```scala
val header = md"# Document Title"
val body = md"Some content here."
val footer = md"---\n*Footer*"

val full = header ++ body ++ footer
```

**Metadata merging:**
```scala
val doc1 = Doc(Chunk(Paragraph(Chunk(Text("A")))), Map("author" -> "Alice"))
val doc2 = Doc(Chunk(Paragraph(Chunk(Text("B")))), Map("version" -> "1.0"))
val combined = doc1 ++ doc2
// combined.metadata == Map("author" -> "Alice", "version" -> "1.0")
```

### Normalization

`normalize` cleans up the AST:
- Merges adjacent `Text` nodes
- Removes empty paragraphs and other empty blocks
- Recursively normalizes nested structures (lists, block quotes, tables)

```scala
val messy = Doc(Chunk(
  Paragraph(Chunk(
    Text("Hello "),
    Text("World")  // Adjacent Text nodes
  )),
  Paragraph(Chunk.empty)  // Empty paragraph
))

val clean = messy.normalize
// Doc(Chunk(Paragraph(Chunk(Text("Hello World")))))
```

**When to normalize:**
- Before comparing documents for equality (equality uses normalized form)
- After programmatic AST construction with potential duplicates
- When cleaning up parsed or generated content

**Note:** `Doc.equals` automatically normalizes both sides, so explicit normalization isn't needed for equality checks.

## Advanced Usage

### Custom Renderers

You can traverse the AST to create custom renderers:

```scala
def customRender(doc: Doc): String = {
  doc.blocks.map {
    case Heading(level, content) => 
      s"${"=" * level.value} ${renderInlines(content)}\n"
    case Paragraph(content) => 
      renderInlines(content) + "\n\n"
    case _ => 
      Renderer.renderBlock(_)
  }.mkString
}
```

### Extracting Information

Pattern match on the AST to extract structured data:

```scala
def extractHeadings(doc: Doc): List[(Int, String)] = {
  doc.blocks.collect {
    case Heading(level, content) =>
      (level.value, Renderer.renderInlines(content))
  }.toList
}

def extractLinks(doc: Doc): List[String] = {
  def findLinksInInlines(inlines: Chunk[Inline]): List[String] = {
    inlines.toList.flatMap {
      case Link(_, url, _) => List(url)
      case Strong(content) => findLinksInInlines(content)
      case Emphasis(content) => findLinksInInlines(content)
      case _ => Nil
    }
  }
  
  doc.blocks.flatMap {
    case Paragraph(content) => findLinksInInlines(content)
    case Heading(_, content) => findLinksInInlines(content)
    case _ => Nil
  }.toList
}
```

### Transforming Documents

Apply transformations to the AST:

```scala
def uppercaseHeadings(doc: Doc): Doc = {
  val transformedBlocks = doc.blocks.map {
    case Heading(level, content) =>
      val upperContent = content.map {
        case Text(value) => Text(value.toUpperCase)
        case other => other
      }
      Heading(level, upperContent)
    case other => other
  }
  Doc(transformedBlocks, doc.metadata)
}
```

## Best Practices

### Parsing
- Always handle `Either[ParseError, Doc]` - don't assume parsing succeeds
- For user input, display parse errors with line/column information
- Use the interpolator for static markdown (compile-time validation)

### Building
- Prefer the `md"..."` interpolator for compile-time safety
- Use programmatic construction for dynamic content
- Call `normalize` after complex programmatic construction

### Rendering
- Use `toHtmlFragment` when embedding in existing HTML pages
- Use `render` (full HTML) for standalone documents
- Use `toTerminal` for CLI tools and REPLs
- Use `toString` when you need markdown output

### Performance
- Parse once, render multiple times if possible
- Normalization is not free - don't call it unnecessarily
- The AST is immutable - transformations create new instances
