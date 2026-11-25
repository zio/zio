# How to Traverse Collections with ForEach

> Run a function that can fail over every element of a collection and get one combined result, using ForEach.

In this guide, we parse a list of text values into numbers, where each parse can fail, and get a single result for the whole list. By the end, you will have used `forEach` with `Option` and `Validation`, turned a list of results inside out with `flip`, numbered elements with `mapAccum`, and given your own tree type a `ForEach` instance so it gets the same operators.

We start with `List`, then move to other collections, and finish with a custom data type.

## The Problem

Mapping a list with a function that can fail gives a list of results, not a result of a list. Turning it inside out by hand means a fold that threads the failure through, and it must be rewritten for each pairing of collection and result type:

```scala
def parseAll(inputs: List[String]): Option[List[Int]] =
  inputs.foldRight(Option(List.empty[Int])) { (input, acc) =>
    for {
      rest   <- acc
      number <- input.toIntOption
    } yield number :: rest
  }
```

This is specific to `List` and `Option`. Swapping `Option` for a type that collects all errors, or `List` for a `Map`, means writing it again, and the `for` comprehension still stops at the first failure.

In this guide, we replace it with `forEach`, which works for any collection that has a `ForEach` instance and any result type that can combine independent results.

## Prerequisites

We need the `zio-prelude` dependency:

```scala
libraryDependencies += "dev.zio" %% "zio-prelude" % "1.0.0-RC49"
```

`ForEach[F]` describes a collection type `F`, such as `List`, `Chunk`, `Map` or `Option`. Importing the package adds the operators to values of those types. We also define the two parsers used throughout the guide, one that returns an `Option` and one that returns a `Validation` with an error message:

```scala
import zio.prelude._

def parseOption(text: String): Option[Int] =
  text.toIntOption

def parseValidation(text: String): Validation[String, Int] =
  Validation.fromOptionWith(s"not a number: $text")(text.toIntOption)
```

## Traversing with Option

`ForEach#forEach` applies a function to every element and combines the results. With an `Option` result, we get `Some` of the whole collection if every element succeeded, and `None` otherwise:

```scala
List("1", "2", "3").forEach(parseOption)
// res2: Option[List[Int]] = Some(value = List(1, 2, 3))

List("1", "x", "3").forEach(parseOption)
// res3: Option[List[Int]] = None
```

## Traversing with Validation

With a `Validation` result, `ForEach#forEach` keeps the errors of every failing element instead of stopping at the first. This is the version to use for input from users:

```scala
List("1", "2", "3").forEach(parseValidation)
// res4: Validation[String, List[Int]] = Success(
//   log = IndexedSeq(),
//   value = List(1, 2, 3)
// )

List("1", "x", "y").forEach(parseValidation)
// res5: Validation[String, List[Int]] = Failure(
//   log = IndexedSeq(),
//   errors = NonEmptyChunk(not a number: x, not a number: y)
// )
```

Notice that both bad inputs are reported. The same call works on other collections. A `Map` traverses its values and keeps its keys, and an `Option` traverses its single value:

```scala
Map("a" -> "1", "b" -> "2").forEach(parseOption)
// res6: Option[Map[String, Int]] = Some(value = Map("a" -> 1, "b" -> 2))

Option("1").forEach(parseValidation)
// res7: Validation[String, Option[Int]] = Success(
//   log = IndexedSeq(),
//   value = Some(value = 1)
// )
```

## Turning a Collection of Results Inside Out

When we already have a collection of results, `ForEach#flip` swaps the two layers, so a `List[Option[Int]]` becomes an `Option[List[Int]]`. It is `forEach` with the identity function:

```scala
List(Option(1), Option(2)).flip
// res8: Option[List[Int]] = Some(value = List(1, 2))

List(Option(1), None).flip
// res9: Option[List[Int]] = None
```

## Traversing for Effects Only

When only the combined outcome matters and not the values, `ForEach#forEach_` discards them and returns `Unit` in the result type. We use it for checks:

```scala
List(1, 2, 3).forEach_(number => if (number > 0) Option(()) else None)
// res10: Option[Unit] = Some(value = ())
```

## Numbering Elements with State

Some traversals need a running value, such as an index or a total. `ForEach#mapAccum` threads a state through the collection without a `var`. It takes the initial state and a function from the state and an element to the next state and a new element, and returns the final state with the new collection:

```scala
List(1, 2, 3).mapAccum(0)((total, number) => (total + number, total))
// res11: (Int, List[Int]) = (6, List(0, 1, 3))
```

Each element is replaced by the running total before it, and `6` is the final total. For simple numbering, `ForEach#zipWithIndex` pairs each element with its index.

## Giving Your Own Type a ForEach Instance

A `ForEach` instance needs one operation, `forEach`. Every other operator, including `map`, comes from it. We define it for a binary tree. For each node, the instance traverses the left subtree, the value and the right subtree, and combines the three results with `zipWith`, which the `IdentityBoth` and `Covariant` instances of the result type provide. A leaf has no elements, so it is lifted into the result type with `succeed`:

```scala
sealed trait Tree[+A]

object Tree {
  case object Leaf                                              extends Tree[Nothing]
  final case class Node[+A](left: Tree[A], value: A, right: Tree[A]) extends Tree[A]

  implicit val treeForEach: ForEach[Tree] =
    new ForEach[Tree] {
      def forEach[G[+_]: IdentityBoth: Covariant, A, B](tree: Tree[A])(f: A => G[B]): G[Tree[B]] =
        tree match {
          case Leaf              => (Leaf: Tree[B]).succeed[G]
          case Node(left, a, right) =>
            forEach(left)(f)
              .zipWith(f(a))((leftB, b) => (leftB, b))
              .zipWith(forEach(right)(f)) { case ((leftB, b), rightB) => Node(leftB, b, rightB): Tree[B] }
        }
    }
}
```

The instance lives in the companion object, so the compiler finds it. We build a small tree and use the operators on it. `Tree#map` comes from `Covariant`, and `forEach` runs with any result type:

```scala
val tree: Tree[String] =
  Tree.Node(Tree.Node(Tree.Leaf, "1", Tree.Leaf), "2", Tree.Node(Tree.Leaf, "3", Tree.Leaf))
// tree: Tree[String] = Node(
//   left = Node(left = Leaf, value = "1", right = Leaf),
//   value = "2",
//   right = Node(left = Leaf, value = "3", right = Leaf)
// )

tree.forEach(parseValidation)
// res12: Validation[String, Tree[Int]] = Success(
//   log = IndexedSeq(),
//   value = Node(
//     left = Node(left = Leaf, value = 1, right = Leaf),
//     value = 2,
//     right = Node(left = Leaf, value = 3, right = Leaf)
//   )
// )

tree.map(_.length)
// res13: Tree[Int] = Node(
//   left = Node(left = Leaf, value = 1, right = Leaf),
//   value = 1,
//   right = Node(left = Leaf, value = 1, right = Leaf)
// )

ForEach[Tree].toList(tree)
// res14: List[String] = List("1", "2", "3")

ForEach[Tree].size(tree)
// res15: Int = 3
```

The tree also gets the folds, searches and the other operators in the `ForEach` trait without more code.

## Going Further

- Combine traversal with `Identity` to fold a collection into one value with `ForEach#foldMap`, as shown in [How to Combine Values with Associative and Identity](./combine-values-with-associative-and-identity.md).
- Read the [ForEach](../functional-abstractions/parameterized-types/foreach.md) reference page for the complete list of operators, and [NonEmptyForEach](../functional-abstractions/parameterized-types/nonemptyforeach.md) for collections that always contain at least one element.
- Test your instance against the laws in [How to Test Instances with Laws](./test-instances-with-laws.md).
