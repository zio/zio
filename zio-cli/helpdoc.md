# Help Documentation

> `HelpDoc` is a description of the documentation of a CLI App. They can be added to any `Command`, `Options` or `Args`.
`HelpDoc` is composed of a list of `HelpDoc` items that can be headers, paragraphs, description lists, sequences and enumerations.

`HelpDoc` is a description of the documentation of a CLI App. They can be added to any `Command`, `Options` or `Args`.
`HelpDoc` is composed of a list of `HelpDoc` items that can be headers, paragraphs, description lists, sequences and enumerations.

## Building blocks
The most basic forms of `HelpDoc` are headers and paragraphs. Method `HelpDoc.p` can create paragraphs from text, while methods `HelpDoc.h1`, `HelpDoc.h2` and `HelpDoc.h3` create headers of different levels.

```scala

val t = "text"

val element: HelpDoc  = HelpDoc.empty             // HelpDoc without content
val header1: HelpDoc  = HelpDoc.h1("Level 1")     // Header of level 1
val header2: HelpDoc  = HelpDoc.h2("Level 2")     // Header of level 2
val header3: HelpDoc  = HelpDoc.h3(t: String)     // Header of level 3
val p: HelpDoc        = HelpDoc.p(t: String)      // Paragraph
```

It is possible to construct a more complex `HelpDoc` by combining them. We can use enumerations, description lists and blocks. In each of the following methods, it is possible to use as many `HelpDoc` arguments as desired.
- Enumeration
```scala
// Enumeration of HelpDocs
HelpDoc.enumeration(element: HelpDoc): HelpDoc
HelpDoc.enumeration(header1 + p, header2 + p)

```
The output of `HelpDoc.enumeration(header1 + p, header2 + p)` is:
```
- 
LEVEL 1

  text
  -
LEVEL 2

  text
```

- Description List
```scala
  
// Creates a list with Span as header
val definition1 = (Span.text("Span1"), HelpDoc.p("Description 1"))
val definition2 = (Span.text("Span2"), HelpDoc.p("Description 12"))
HelpDoc.descriptionList(definition1: (Span, HelpDoc)): HelpDoc
HelpDoc.descriptionList(definition1: (Span, HelpDoc), definition2: (Span, HelpDoc))

```
The output of `HelpDoc.descriptionList(definition1: (Span, HelpDoc), definition2: (Span, HelpDoc))` is:
```
Span1
  Description 1

Span2
  Description 2
```

- Blocks
```scala
// Stacks HelpDocs
HelpDoc.blocks(element: HelpDoc): HelpDoc
HelpDoc.blocks(header1 + p, header2 + p)

```
The output of `HelpDoc.blocks(header1 + p, header2 + p)` is:
```
LEVEL 1

  text

LEVEL 2

  text
```

### Span
The data of a HelpDoc is not stored as text, rather as `Span` type, that also contains information about the type of information. It is possible to use methods `h1`, `h2`, `h3` and `p` with `Span` instead of `String`. You can create `Span` instances using the following methods:
```scala

val span = Span.code(t: String)
Span.empty
Span.error(span: Span)
Span.error(t: String)
Span.space
Span.spans(span: Span)                // You can add more than one span
Span.spans(span, span)
Span.spans(List(span))
Span.strong(span: Span)
Span.strong(t: String)
Span.text(t: String)
Span.uri(java.net.URI.create("https://zio.dev/"))
Span.weak(span: Span)
Span.weak(t: String)
```
You can also obtain its text value using `span.text` or concatenate `Span` using `span1 + span2`.

## Transformation methods
A `HelpDoc` can be converted into plaintext and HTML:
```scala
trait HelpDoc {
  def toHTML: String
  def toPlaintext(columnWidth: Int = 100, color: Boolean = true): String
}
```

## Combination methods
The following methods allow to combine `HelpDoc`:
```scala
trait HelpDoc {
  def +(that: HelpDoc): HelpDoc     // Concatenate HelpDocs in successive levels
  def |(that: HelpDoc): HelpDoc
}
```
- Method `+`

It concatenates `HelpDoc`:
```scala

HelpDoc.h1("Header 1") + HelpDoc.p("paragraph content")
```
It shows:
```
Header 1

  paragraph content
```

- Method `|`

It shows the second `HelpDoc` only if the first one is empty. It could be used to show a backup `HelpDoc`.

## Examples
The more common use case is through the operators `??` and `withHelp`.
- `??` can be applied to `Options` and `Args`. It adds a string to the current description.
```scala

trait Options[A] {
  def ??(that: String): Options[A] // or Args[A]
}

val optionsWithHelp = Options.text("sample") ?? "description of options"
```

- `withHelp` is applied to `Command`. It overwrites the current help of the command, so use it cautiously! On the other hand, you need to use it to add your desired `HelpDoc`
```scala

trait Options[A] {
  def withHelp(that: String): Command[A] // that is converted into a paragraph
  def withHelp(that: HelpDoc): Command[A]
}

val optionsWithHelp = Options.text("sample") ?? "description of options"
val commandWithHelp = Command("command", optionsWithHelp).withHelp("description of command")
```
When `withHelp` is used with a command that has parent and children subcommands, it is applied only to the parent command.

If a more complex use is desired, it is possible to combine headers and paragraphs using the methods before.
```scala
// Create a HelpDoc with a header depending on an integer
val paragraph = HelpDoc.p("paragraph")
def header(n: Int) = HelpDoc.h1("Header: " + n)
def completeDoc(n: Int) = header(n) + paragraph

// Create an enumeration of HelpDocs
val complexDoc = HelpDoc.enumeration(completeDoc(7), completeDoc(-1), completeDoc(3))
```
