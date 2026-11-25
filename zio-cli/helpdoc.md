# Help Documentation

> `HelpDoc` is the text that a ZIO CLI application prints for `--help` and for errors. It lives in `zio.cli` and is a tree of headers, paragraphs, description lists and enumerations, joined into sequences with `HelpDoc#+`. The words inside each element are stored as `HelpDoc.Span` values, which also record how a piece of text is styled: plain, code, error, weak, strong or a link.

You attach a `HelpDoc` to a [`Command`](commands.md) with `Command#withHelp`, and to a [`CliApp`](cli-app.md) as its footer. [`Options`](options.md) and [`Args`](args.md) take a plain `String` with `Options#??` and `Args#??`, which is added as a paragraph. Parse errors also carry a `HelpDoc`, as described in [Validation and Errors](validation-and-errors.md).

## Creating Help Documents

The constructors on the `HelpDoc` companion object build the elements of a document. Every constructor that takes a `String` also has an overload that takes a `Span`, so a header or paragraph can contain styled text.

### Headers and Paragraphs

`HelpDoc.h1`, `HelpDoc.h2` and `HelpDoc.h3` create headers of level 1, 2 and 3. `HelpDoc.p` creates a paragraph, and `HelpDoc.empty` is a document with no content:

```scala
object HelpDoc {
  val empty: HelpDoc
  def h1(t: String): HelpDoc
  def h1(span: Span): HelpDoc
  def h2(t: String): HelpDoc
  def h2(span: Span): HelpDoc
  def h3(t: String): HelpDoc
  def h3(span: Span): HelpDoc
  def p(t: String): HelpDoc
  def p(span: Span): HelpDoc
}
```

We build a header and a paragraph from strings, and a second paragraph from spans:

```scala
import zio.cli._
import zio.cli.HelpDoc.Span

val title: HelpDoc   = HelpDoc.h1("Usage")
val intro: HelpDoc   = HelpDoc.p("Copies files from one place to another.")
val styled: HelpDoc  = HelpDoc.p(Span.text("Run ") + Span.code("copy --help") + Span.text(" for details."))
val nothing: HelpDoc = HelpDoc.empty
```

### Description Lists

`HelpDoc.descriptionList` takes any number of `(Span, HelpDoc)` pairs. Each pair is a term and its description, the same layout the help output uses for options and arguments:

```scala
object HelpDoc {
  def descriptionList(definitions: (Span, HelpDoc)*): HelpDoc
}
```

We describe two flags:

```scala
val flags: HelpDoc = HelpDoc.descriptionList(
  Span.text("--verbose") -> HelpDoc.p("Prints more."),
  Span.text("--quiet")   -> HelpDoc.p("Prints less.")
)
```

### Enumerations

`HelpDoc.enumeration` takes any number of documents and renders them as a bulleted list. An enumeration passed inside another enumeration is flattened, so its items become items of the outer list:

```scala
object HelpDoc {
  def enumeration(elements: HelpDoc*): HelpDoc
}
```

We list two steps:

```scala
val steps: HelpDoc = HelpDoc.enumeration(HelpDoc.p("first item"), HelpDoc.p("second item"))
```

### Blocks

`HelpDoc.blocks` joins documents one after another, the same as chaining them with `HelpDoc#+`. One overload takes at least one document, the other takes a collection and returns `HelpDoc.empty` when the collection is empty:

```scala
object HelpDoc {
  def blocks(helpDoc: HelpDoc, helpDocs0: HelpDoc*): HelpDoc
  def blocks(bs: Iterable[HelpDoc]): HelpDoc
}
```

We stack the header, the paragraph and the flags into one document:

```scala
val page: HelpDoc = HelpDoc.blocks(title, intro, HelpDoc.h2("Options"), flags)
```

## Spans

A `Span` holds a piece of inline text together with its style. Help output and errors use the style to colour the text in a terminal or to pick a tag in HTML.

### Creating Spans

The constructors on `HelpDoc.Span` cover every style:

```scala
object Span {
  def text(t: String): Span
  def code(t: String): Span
  def error(t: String): Span
  def error(span: Span): Span
  def weak(t: String): Span
  def weak(span: Span): Span
  def strong(t: String): Span
  def strong(span: Span): Span
  def uri(uri: java.net.URI): Span
  def space: Span
  def empty: Span
  def spans(span: Span, spans0: Span*): Span
  def spans(spans: Iterable[Span]): Span
}
```

| Constructor   | Result                                                  |
| ------------- | ------------------------------------------------------- |
| `Span.text`   | Plain text.                                             |
| `Span.code`   | A command or code fragment.                             |
| `Span.error`  | Text marked as an error, from a string or another span. |
| `Span.weak`   | De-emphasised text, from a string or another span.      |
| `Span.strong` | Emphasised text, from a string or another span.         |
| `Span.uri`    | A link.                                                 |
| `Span.space`  | A single space, the same as `Span.text(" ")`.           |
| `Span.empty`  | An empty text span, the same as `Span.text("")`.        |
| `Span.spans`  | The given spans joined in order.                        |

We combine several styles in one span:

```scala
val hint: Span = Span.spans(
  Span.error("Unknown flag."),
  Span.space,
  Span.text("See "),
  Span.uri(java.net.URI.create("https://zio.dev/zio-cli/"))
)
```

### Span Operations

A `Span` has three methods that read it and one that extends it:

```scala
sealed trait Span {
  def +(that: Span): Span
  def text: String
  def size: Int
  def isEmpty: Boolean
}
```

`Span#+` appends another span. `Span#text` returns the characters without any style, `Span#size` returns their number, and `Span#isEmpty` is `true` when the size is zero:

```scala
hint.text
// res1: String = "Unknown flag. See https://zio.dev/zio-cli/"
hint.size
// res2: Int = 42
Span.empty.isEmpty
// res3: Boolean = true
```

The cases of the sealed trait are `Span.Text`, `Span.Code`, `Span.Error`, `Span.Weak`, `Span.Strong`, `Span.URI` and `Span.Sequence`. They are public, so you can pattern match on a span, for example to write your own renderer.

## Combining Help Documents

Two operators join documents. `HelpDoc#+` places one document after another, and `HelpDoc#|` picks a fallback:

```scala
sealed trait HelpDoc {
  def +(that: HelpDoc): HelpDoc
  def |(that: HelpDoc): HelpDoc
}
```

`HelpDoc#+` returns a sequence of both documents. If either side is empty, it returns the other side unchanged, so adding `HelpDoc.empty` does nothing. `HelpDoc#|` returns the left document, or the right one when the left document is empty:

```scala
(HelpDoc.empty + intro) == intro
// res4: Boolean = true
(HelpDoc.empty | intro) == intro
// res5: Boolean = true
(title | intro) == title
// res6: Boolean = true
```

"Empty" follows `HelpDoc#isEmpty`, described in the next section. A paragraph or header built from an empty string is not empty, so `HelpDoc.p("") | intro` keeps the empty paragraph.

## Inspecting and Transforming

`HelpDoc` has methods that ask what kind of document it is, read its text and change description lists:

```scala
sealed trait HelpDoc {
  def isEmpty: Boolean
  def isHeader: Boolean
  def isParagraph: Boolean
  def isDescriptionList: Boolean
  def isEnumeration: Boolean
  def isSequence: Boolean
  def getSpan: Span
  def mapDescriptionList(f: (Span, HelpDoc) => (Span, HelpDoc)): HelpDoc
}
```

- `HelpDoc#isEmpty` is `true` for `HelpDoc.empty`, for a sequence or enumeration whose parts are all empty, and for a description list whose descriptions are all empty. A description list like that is dropped by `HelpDoc#+`, even when it has terms.
- `HelpDoc#isHeader`, `HelpDoc#isParagraph`, `HelpDoc#isDescriptionList` and `HelpDoc#isEnumeration` are `true` when the document is that element, or is a sequence that starts with it.
- `HelpDoc#isSequence` is `true` only for a sequence built with `HelpDoc#+`.
- `HelpDoc#getSpan` returns the span of a header or paragraph, and `Span.empty` for anything else.
- `HelpDoc#mapDescriptionList` changes every entry of a description list and leaves any other document unchanged.

We check the page built earlier and add a note to every flag:

```scala
page.isHeader
// res7: Boolean = true
page.isSequence
// res8: Boolean = true
title.getSpan.text
// res9: String = "Usage"

val annotated = flags.mapDescriptionList { case (term, description) =>
  term -> (description + HelpDoc.p("Can be given once."))
}
// annotated: HelpDoc = DescriptionList(
//   definitions = List(
//     (
//       Text(value = "--verbose"),
//       Sequence(
//         left = Paragraph(value = Text(value = "Prints more.")),
//         right = Paragraph(value = Text(value = "Can be given once."))
//       )
//     ),
//     (
//       Text(value = "--quiet"),
//       Sequence(
//         left = Paragraph(value = Text(value = "Prints less.")),
//         right = Paragraph(value = Text(value = "Can be given once."))
//       )
//     )
//   )
// )
println(annotated.toPlaintext(color = false))
// --verbose
//   Prints more.
// 
//   Can be given once.
// 
// --quiet
//   Prints less.
// 
//   Can be given once.
// 
//
```

The cases of the sealed trait are `HelpDoc.Empty`, `HelpDoc.Header(value, level)`, `HelpDoc.Paragraph(value)`, `HelpDoc.DescriptionList(definitions)`, `HelpDoc.Enumeration(elements)` and `HelpDoc.Sequence(left, right)`. They are public, so you can pattern match on a document. `HelpDoc.Enumeration#flatten` lifts the items of nested enumerations into the outer one, which `HelpDoc.enumeration` already does for you.

## Rendering

A `HelpDoc` renders to terminal text with `HelpDoc#toPlaintext` and to an HTML page with `HelpDoc#toHTML`:

```scala
sealed trait HelpDoc {
  def toPlaintext(columnWidth: Int = 100, color: Boolean = true): String
  def toHTML: String
}
```

### Plain Text

`HelpDoc#toPlaintext` writes headers in upper case on their own line and indents the content under them by two spaces. All three header levels look the same in plain text. Words wrap at `columnWidth` characters. With `color = true`, the default, the text contains ANSI escape codes for the styles and ends with a reset code. With `color = false` there are no escape codes. The help screen of a `CliApp` uses a column width of 300, and parse errors use 80.

We render the page built earlier without colour:

```scala
println(page.toPlaintext(color = false))
// 
// USAGE
// 
//   Copies files from one place to another.
// 
// OPTIONS
// 
//   --verbose
//     Prints more.
// 
//   --quiet
//     Prints less.
// 
//
```

An enumeration puts a dash before each item:

```scala
println(steps.toPlaintext(color = false))
// - first item
// - second item
//
```

A narrow `columnWidth` wraps a long paragraph at the last space that fits:

```scala
println(HelpDoc.p("This paragraph is long enough to wrap at a narrow column width.").toPlaintext(columnWidth = 30, color = false))
// This paragraph is long enough
// to wrap at a narrow column
// width.
//
```

With `color = true`, each style maps to a terminal attribute:

| Element or span       | Terminal style       |
| --------------------- | -------------------- |
| Header                | Bold, upper case     |
| Description-list term | Bold                 |
| `Span.strong`         | Bold                 |
| `Span.weak`           | Bold                 |
| `Span.error`          | Red                  |
| `Span.uri`            | Underlined           |
| `Span.code`           | No style             |
| `Span.text`           | Style of the context |

### HTML

`HelpDoc#toHTML` returns a complete HTML page, with a `<style>` element in the head and the document in the body. Headers become `<h1>` to `<h3>`, paragraphs `<p>`, enumerations `<ul>` with `<li>` items, and the parts of a sequence are separated by `<br/>`. Inside text, `Span.code` becomes `<pre><code>`, `Span.strong` becomes `<b>`, `Span.error` becomes `<span class='error'>`, `Span.uri` becomes an `<a>` link and `Span.weak` has no tag. The characters `<` and `>` in text are escaped.

We print the body of the page built earlier:

```scala
val html = page.toHTML
// html: String = """<html><head><style>
//         h1 {
//             color: rgb(36, 41, 46);
//             font-weight: 600;
//             line-height: 1.25;
//             margin-bottom: 16px;
//             font-family: -apple-system, BlinkMacSystemFont, Segoe UI, Helvetica, Arial, sans-serif, Apple Color Emoji, Segoe UI Emoji, Segoe UI Symbol;
//         }
// 
//         h2 {
//             font-family: -apple-system, BlinkMacSystemFont, Segoe UI, Helvetica, Arial, sans-serif, Apple Color Emoji, Segoe UI Emoji, Segoe UI Symbol;
//             font-size: 24px;
//             letter-spacing: 0px;
//             word-spacing: 2px;
//             color: rgb(36, 41, 46);
//             font-weight: 600;
//         }
// 
//         h3 {
//             font-family: -apple-system, BlinkMacSystemFont, Segoe UI, Helvetica, Arial, sans-serif, Apple Color Emoji, Segoe UI Emoji, Segoe UI Symbol;
//             font-size: 21px;
//             letter-spacing: 0px;
//             word-spacing: 2px;
//             color: rgb(36, 41, 46);
//             font-weight: 700;
//         }
// 
//         p {
//             font-family: -apple-system, BlinkMacSystemFont, Segoe UI, Helvetica, Arial, sans-serif, Apple Color Emoji, Segoe UI Emoji, Segoe UI Symbol;
//             color: #24292e;
//         }
// 
//         .error {
//             font-family: -apple-system, BlinkMacSystemFont, Segoe UI, Helvetica, Arial, sans-serif, Apple Color Emoji, Segoe UI Emoji, Segoe UI Symbol;
//             color: #24292e;
//         }
// 
//         a {
//             border: 0;
//             color: rgb(189, 39, 26);
//             text-decoration: none;
//             font-family: -apple-system, BlinkMacSystemFont, Segoe UI, Helvetica, Arial, sans-serif, Apple Color Emoji, Segoe UI Emoji, Segoe UI Symbol;
//             font-size: inherit;
// ...
println(html.substring(html.indexOf("<body>")))
// <body><h1>Usage</h1><br/><p>Copies files from one place to another.</p><br/><h2>Options</h2><br/>--quiet<p>Prints less.</p>--verbose<p>Prints more.</p></body></html>
```

:::caution
`HelpDoc#toHTML` writes the entries of a description list in reverse order, with no list markup around them: in the output above, `--quiet` comes before `--verbose`. `HelpDoc#toPlaintext` keeps the original order.
:::

## Attaching Help to Commands and Parameters

Help text reaches the help screen through the command, its options and arguments, and the application.

### Options and Args

`Options#??` and `Args#??` add a sentence to the description of an option or argument. Each call adds one paragraph after the existing description. On options or arguments combined with `++`, the text is added to every option or argument in the combination, so call `??` on each one before combining them:

```scala
import zio.cli._

val verbose: Options[Boolean] = Options.boolean("verbose") ?? "Prints more."
val source: Args[String]      = Args.text("source") ?? "The file to copy."
```

### Commands

`Command#withHelp` replaces the help of a command. The `String` overload wraps the text in a paragraph, and the `HelpDoc` overload takes any document:

```scala
sealed trait Command[+A] {
  def withHelp(help: String): Command[A]
  def withHelp(help: HelpDoc): Command[A]
}
```

On a command with subcommands, the help goes to the parent command only. On two commands joined with `Command#|`, the help replaces the help of both alternatives. We give the copy command a paragraph and an enumeration:

```scala
val copy: Command[(Boolean, String)] =
  Command("copy", verbose, source).withHelp(
    HelpDoc.p("Copies a file.") + HelpDoc.enumeration(HelpDoc.p("Keeps timestamps."), HelpDoc.p("Never overwrites."))
  )
```

### Applications

A `CliApp` takes a `HelpDoc.Span` summary, shown on the first line of the help screen, and a `HelpDoc` footer, shown after the options and arguments. Both are parameters of `CliApp.make`, and `CliApp#summary` and `CliApp#footer` append to them on an existing application. We add a footer to an application built from the copy command:

```scala
val copyApp = CliApp.make(
  name = "copy",
  version = "1.0.0",
  summary = HelpDoc.Span.text("Copies files"),
  command = copy,
  footer = HelpDoc.p("Report problems at https://github.com/zio/zio-cli/issues.")
) { case (verbose, source) => zio.Console.printLine(s"Copying $source") }
```

[CliApp](cli-app.md) lists the other parameters of `CliApp.make`, and [Built-in Commands](built-in-commands.md) shows the help screen that `--help` prints.
