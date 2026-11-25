# Example: a GraphQL Parser

> The `zio-parser-caliban` module is a real-world parser built with ZIO Parser. It parses GraphQL documents, the query language used by the [Caliban](https://github.com/ghostdogpr/caliban) library. Unlike the JSON example, it is a production grammar with many rules, and it shows how a large grammar is organized into small named parts.

This page shows how to call the parser and what it accepts. The full source is in [`zio-parser-caliban/src/main/scala/zio/parser/caliban/CalibanParser.scala`](https://github.com/zio/zio-parser/blob/master/zio-parser-caliban/src/main/scala/zio/parser/caliban/CalibanParser.scala).

## Using the Module

The module lives in this repository, next to the core library. It is not published as a separate artifact, so use it from a checkout of the repository or copy its source into your project. The examples on this page run against the same source during the documentation build.

```scala
import zio.parser.caliban.CalibanParser
```

## Parsing a Query

`CalibanParser.document` is a `Syntax` for a whole GraphQL document. Calling `parseString` on it gives an `Either`, just like any other syntax in this library. We parse a small query with a field and a nested selection:

```scala
CalibanParser.document.parseString("{ hero { name } }").map(_.definitions.length)
// res0: Either[StringParserError[String], Int] = Right(1)
```

The result has one definition. A document with a syntax error returns a `Left` with the same `StringParserError` type you saw in the [error handling](errors.md) page:

```scala
CalibanParser.document.parseString("{ hero { name } ").left.map(_.pretty).merge
// res1: String | ParsedDocument = "All branches failed: All branches failed: All branches failed: All branches failed: All branches failed: All branches failed: Failure at position 0: Not 'query' and Failure at position 0: Not 'mutation' and Failure at position 0: Not 'subscription' and Unexpected end of input and Failure at position 0: Not 'fragment' and All branches failed: All branches failed: All branches failed: All branches failed: All branches failed: All branches failed: All branches failed: Failure at position 0: Not 'type' and Failure at position 0: Not 'interface' and Failure at position 0: Not 'input' and Failure at position 0: Not 'enum' and Failure at position 0: Not 'union' and Failure at position 0: Not 'scalar' and Failure at position 0: Not 'schema' and Failure at position 0: Not 'directive @' and Failure at position 0: Not 'extend '"
```

## Checking a Query

When you only need to know whether a query is valid, `CalibanParser.check` returns `None` for a valid query, and `Some` with the error text for an invalid one:

```scala
CalibanParser.check("{ hero { name } }")
// res2: Option[String] = None
```

:::note
`CalibanParser.parseQuery` wraps the same parser in a `ZIO`, and turns parse failures into a `ParsingError` that Caliban can report. It also catches unexpected exceptions. Use it inside a `ZIO` program.
:::

## What the Grammar Accepts

GraphQL allows commas as optional separators, and `#` comments anywhere between tokens. The module handles both by defining a shared whitespace syntax that skips spaces, line breaks, commas, the byte order mark, and comments. This is the same technique described in the [whitespace and comments](how-to-whitespace-and-comments.md) guide.

The grammar is split into many named `lazy val`s, one per GraphQL construct, such as `typeDefinition`, `objectTypeExtension` and `directiveDefinition`. The top-level `document` refers to them, and each one can be read on its own. Recursive constructs, such as selection sets inside fields, are `lazy val`s for the same reason explained in [recursive syntaxes](how-to-recursive-syntaxes.md).

## Where to Go Next

- Read the module's [tests](https://github.com/zio/zio-parser/tree/master/zio-parser-caliban/src/test/scala/zio/parser/caliban) for more query examples, including invalid ones.
- Compare with [Example: a JSON Syntax](example-json.md), which builds a small grammar from scratch.
