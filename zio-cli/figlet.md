# Figlet banners

> A `CliApp` prints its name as a banner of ASCII art at the top of the help output and of wizard mode. The art comes from a FIGlet font. FIGlet is a text format for fonts made of ASCII characters: a font file, called an FLF file after its file extension, holds one drawing for each character.

`FigFont` in the `zio.cli.figlet` package is a parsed font. ZIO CLI includes one font, `FigFont.Default`, so a banner works without any setup. On the JVM you can also load another font from a file, a classpath resource, a URL or any `scala.io.Source`.

## Setting the Banner Font

`CliApp.make` has a `figFont` parameter, which defaults to `FigFont.Default`. We pass the font explicitly:

```scala
import zio._
import zio.cli._
import zio.cli.figlet.FigFont

val greetCommand: Command[String] = Command("hello", Options.text("name"))

val greetApp = CliApp.make(
  name = "hello",
  version = "1.0.0",
  summary = HelpDoc.Span.text("Greets someone."),
  command = greetCommand,
  figFont = FigFont.Default
)(name => Console.printLine(s"Hello, $name!"))
```

`FigFont.Default` is the "Slant" font. Its source text is part of the library and is parsed the first time `FigFont.Default` is used. [CliApp](cli-app.md) describes the other parameters of `CliApp.make`.

## Rendering Text

`FigFont#render` draws a string and returns the lines joined with `\n`. `FigFont#renderLines` returns the same lines as a `Chunk[String]`:

```scala
final case class FigFont(header: FigHeader, rightToLeft: Boolean, layout: Layouts, chars: Map[Char, FigChar]) {
  def render(text: String): String
  def renderLines(text: String): Chunk[String]
}
```

We draw the banner that the help output of `greetApp` shows:

```scala
println(FigFont.Default.render("hello"))
//     __         ____    
//    / /_  ___  / / /___ 
//   / __ \/ _ \/ / / __ \
//  / / / /  __/ / / /_/ /
// /_/ /_/\___/_/_/\____/ 
//
```

A `\n` in the text starts a new row of characters below the first one. A character that the font does not define is drawn with the font's glyph for character code 0, or left out when the font has none.

## Loading a Font

`FigFont.fromLines` parses a font from the lines of an FLF file. It is available on every platform and returns an error message when the lines are not a valid font. The message starts with the line number where parsing failed, followed by the column when the error is inside a line:

```scala
object FigFont {
  def fromLines(lines: Iterable[String]): Either[String, FigFont]
}
```

The loaders below read the lines from a source and then call `FigFont.fromLines`. They exist only on the JVM. Each one returns a `ZIO` that reads nothing until it runs, and fails with an `Either`: `Left` holds the `IOException` when the source could not be read, and `Right` holds the parse message when the text is not a valid font:

```scala
object FigFont {
  def fromFile(name: String): ZIO[Any, Either[IOException, String], FigFont]
  def fromResource(name: String, loader: ClassLoader): ZIO[Any, Either[IOException, String], FigFont]
  def fromURL(url: String): ZIO[Any, Either[IOException, String], FigFont]
  def fromSource[R, A, E >: IOException](source: => ZIO[R, E, Source]): ZIO[R, Either[E, String], FigFont]
}
```

| Loader                 | Reads                                    | Encoding                        |
| ---------------------- | ---------------------------------------- | ------------------------------- |
| `FigFont.fromFile`     | A file path                              | ISO-8859-1                      |
| `FigFont.fromResource` | A classpath resource of the class loader | The platform's default encoding |
| `FigFont.fromURL`      | A URL, over the network                  | ISO-8859-1                      |
| `FigFont.fromSource`   | A `Source` produced by a `ZIO`           | The encoding of the `Source`    |

We load a font from a file and from a classpath resource:

```scala
import java.io.IOException

val fontFromFile: ZIO[Any, Either[IOException, String], FigFont] =
  FigFont.fromFile("fonts/small.flf")

val fontFromResource: ZIO[Any, Either[IOException, String], FigFont] =
  FigFont.fromResource("fonts/small.flf", getClass.getClassLoader)
```

:::caution
When `FigFont.fromResource` cannot find the resource, the effect dies with a `NullPointerException` instead of failing with `Left`. Check that the resource exists, for example with `ClassLoader#getResource`, before loading it.
:::

`FigFont.fromURL` downloads the font when the effect runs:

```scala
val fontFromUrl: ZIO[Any, Either[IOException, String], FigFont] =
  FigFont.fromURL("https://example.com/fonts/small.flf")
```

`FigFont.fromSource` takes a `ZIO` that opens a `scala.io.Source`, reads its lines and closes it. The other loaders are built on it. We load a font whose text is already in memory, such as text read from a database:

```scala
import scala.io.Source

def fontFromText(text: String): ZIO[Any, Either[IOException, String], FigFont] =
  FigFont.fromSource(ZIO.attemptBlockingIO(Source.fromString(text)))
```

Because a loader returns a `ZIO`, the application is built after the font is loaded. `ZIO#orElseSucceed` falls back to the default font when loading fails:

```scala
def helloApp(font: FigFont) =
  CliApp.make(
    name = "hello",
    version = "1.0.0",
    summary = HelpDoc.Span.text("Greets someone."),
    command = greetCommand,
    figFont = font
  )(name => Console.printLine(s"Hello, $name!"))

val runHello: ZIO[Any, Any, Any] =
  FigFont.fromFile("fonts/small.flf").orElseSucceed(FigFont.Default).flatMap(font => helloApp(font).run(List("--help")))
```

## Font Model

The fields of `FigFont` and the types they use are public, so a program can inspect a parsed font. You rarely need them: they matter only if you write your own renderer.

| Type        | Holds                                                                                           |
| ----------- | ----------------------------------------------------------------------------------------------- |
| `FigHeader` | The values of the font's header line, such as the hard-blank character and the character height |
| `Layouts`   | The horizontal and vertical `Layout` of the font                                                |
| `Layout`    | `Layout.FullWidth`, `Layout.Fitting` or `Layout.Smushing` with its `SmushingRule`               |
| `FigChar`   | The drawing of one character, as `FigCharLine` rows with a width and height                     |

## Platform Support

The parser and the renderer work on the JVM, Scala.js and Scala Native. Only the loaders that read from a source are JVM-only:

| Method                 | JVM | Scala.js | Scala Native |
| ---------------------- | --- | -------- | ------------ |
| `FigFont.Default`      | Yes | Yes      | Yes          |
| `FigFont#render`       | Yes | Yes      | Yes          |
| `FigFont.fromLines`    | Yes | Yes      | Yes          |
| `FigFont.fromFile`     | Yes | No       | No           |
| `FigFont.fromResource` | Yes | No       | No           |
| `FigFont.fromURL`      | Yes | No       | No           |
| `FigFont.fromSource`   | Yes | No       | No           |

On Scala.js and Scala Native, a call to a JVM-only loader does not compile. To use another font there, read the file with the platform's own API and pass its lines to `FigFont.fromLines`.

The objects `FigFontParser` and `FigFontRenderer` are private to the library, and the `Parser` and `ParseResult` classes in the same package are its parser combinators. They are not meant for application code.
