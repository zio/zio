# ZIO SBT Source

> ZIO SBT Source is a Scala 2.13 and Scala 3 cross-compiled library, not an sbt plugin, that provides utilities for self-documenting example code. It contains the `ExprEval` macro, which captures the source text of expressions at compile time and prints it alongside the evaluated result at runtime, `SourceFile` and `SourceReader`, which read source files, and `EmbedSourceModifier`, an mdoc modifier that embeds a source file into a documentation page.

## Installation

Add the following line to your `libraryDependencies` in the `build.sbt` of the docs project:

```scala
libraryDependencies += "dev.zio" %% "zio-sbt-source" % "0.8.12"
```

## ExprEval

`ExprEval.show` prints each expression followed by its value:

```scala
import zio.sbt.ExprEval.show

show(
  42 + 8,
  "Hello, " + "World!"
)
```

It prints:

```
42 + 8
// 50
"Hello, " + "World!"
// Hello, World!
```

### Comment Labels

Consecutive `//` comment lines immediately above the `show` call are printed as a label above the output:

```scala
// Calculate the answer to life, the universe, and everything
show(6 * 7)
```

It prints:

```
// Calculate the answer to life, the universe, and everything
6 * 7
// 42
```

### Block Form

`show` also accepts a block, with one expression per line:

```scala
show {
  1 + 2
  3 * 4
  "hello"
}
```

### Implementation

The Scala 3 version is an `inline def` that uses `scala.quoted` to capture the source. The Scala 2.13 version is a whitebox macro that achieves the same. At runtime, `SourceReader` reads the comment lines above the call from the source file, and returns nothing when the file does not exist.

## SourceFile

`SourceFile` reads a source file and prints it as a fenced code block:

| Method                                                   | Description                                                                                                                                  |
| -------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `SourceFile.readSource(path)`                            | Returns the file content. When `path` does not exist relative to the working directory, it is tried again relative to the parent directory   |
| `SourceFile.fileExtension(path)`                         | Returns the extension of the file name, used as the language of the fence                                                                    |
| `SourceFile.printSource(path, comment = true, showLineNumbers = false)` | Prints a code fence whose language is the file extension. With `comment` the fence has `title="<path>"`, and with `showLineNumbers` it has the `showLineNumbers` flag |

`SourceFile.printSource` prints a source file from an mdoc block:

```scala
import zio.sbt.SourceFile

SourceFile.printSource("path/to/Example.scala")
```

## EmbedSourceModifier

`EmbedSourceModifier` extends mdoc with the `embed` modifier. A code fence whose info string is `mdoc:embed:<path>` is replaced by the file at `<path>`, rendered with `SourceFile.printSource`. Leave the body of the fence empty:

```
\`\`\`scala mdoc:embed:path/to/Example.scala
\`\`\`
```

An empty path, or a file that cannot be read, is reported as an mdoc error.

### Line Numbers

The `:showLineNumbers` flag, or its kebab-case alias `:show-line-numbers`, enables line numbers in [Docusaurus](https://docusaurus.io) code blocks:

```
\`\`\`scala mdoc:embed:path/to/Example.scala:showLineNumbers
\`\`\`
```

Both forms emit `showLineNumbers` in the code fence header.
