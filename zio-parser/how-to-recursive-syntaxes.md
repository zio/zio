# How to Parse Nested Data

> Many formats nest: a list can contain lists, a directory can contain directories, an expression can contain expressions. This guide shows how to describe such data with one recursive syntax that both parses and prints.

The goal is a syntax for a tree of numbers, written as nested brackets. For example, `[1,[2,3],[]]` is a node with three children: the leaf `1`, a node with two leaves, and an empty node.

## The Problem

A parser that calls itself directly, without a pause, never finishes building: the definition of the syntax would refer to itself before its value exists. Languages handle this with functions and lazy values. Writing the parser and the printer as two separate recursive functions doubles the work, and the two can drift apart.

ZIO Parser lets you write the recursive definition once, as a `lazy val`, and both directions follow from it.

## Prerequisites

The examples use these imports and the data types below:

```scala
import zio.Chunk
import zio.parser.*

sealed trait Tree
object Tree {
  final case class Leaf(value: Int)         extends Tree
  final case class Node(children: Chunk[Tree]) extends Tree
}
```

## Parsing a Leaf

A leaf is a single number. We parse the digits and convert them to an `Int`. The conversion can fail, so we use `transformEither`, which reports a failure instead of throwing an exception:

```scala
val leaf: Syntax[String, Char, Char, Tree.Leaf] =
  Syntax.digit.repeat.string.transformEither[String, Tree.Leaf](
    digits => digits.toIntOption.map(Tree.Leaf(_)).toRight("number out of range"),
    leaf => Right(leaf.value.toString)
  )
```

The leaf does not refer to the tree, so it does not need `lazy`.

## Parsing a Node

A node is a pair of brackets around a list of trees separated by commas. The list refers back to `tree`, so both syntaxes are `lazy val`s:

```scala
lazy val tree: Syntax[String, Char, Char, Tree] =
  (leaf.widen[Tree] | node.widen[Tree]) ?? "tree"

lazy val node: Syntax[String, Char, Char, Tree.Node] =
  (Syntax.char('[') ~> tree.repeatWithSep0(Syntax.char(',')) <~ Syntax.char(']'))
    .transform[Tree.Node](Tree.Node(_), _.children)
```

The code above:

- `tree` is a leaf or a node. `widen` lets each alternative produce its own subtype and be used as `Tree`
- `??` gives the whole tree a name, so error messages can say where a failure happened
- `node` matches the brackets, and in between a comma-separated list of `tree`
- `transform` converts the list to a `Tree.Node`, and back when printing

Because `tree` is a `lazy val`, the reference inside `node` is evaluated only when the parser runs, not when the definitions are created. Without `lazy`, Scala would try to build `tree` while building `node`, which needs `tree` first.

## Parsing and Printing Nested Input

We parse a nested list, then print a tree built by hand:

```scala
tree.parseString("[1,[2,3],[]]")
// res0: Either[StringParserError[String], Tree] = Right(
//   Node(
//     IndexedSeq(Leaf(1), Node(IndexedSeq(Leaf(2), Leaf(3))), Node(IndexedSeq()))
//   )
// )
```

```scala
tree.printString(Tree.Node(Chunk(Tree.Leaf(1), Tree.Node(Chunk(Tree.Leaf(2))))))
// res1: Either[String, String] = Right("[1,[2]]")
```

The printer writes the brackets and commas back in the same places the parser expects them, because both directions come from the same definition.

:::caution
Deeply nested input can overflow the JVM stack with the default parser implementation. If your data can be nested that deeply, pass `ParserImplementation.StackSafe` to `parseString`, as explained in the [Syntax reference](syntax.md#choosing-the-parser-implementation).
:::
