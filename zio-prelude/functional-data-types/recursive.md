# Recursive

> `Recursive[Case, Annotations]` represents a recursive data structure, such as an expression tree or a list, without writing the recursion by hand. The `Case` type parameter is a type constructor that describes one layer of the structure, and `Recursive` supplies the nesting.

For example, a small expression language has literals and additions. A layer of that language refers to its sub-expressions through a type parameter, and `Recursive` plugs the layers together. The benefit is that operators such as `fold` can process the whole structure without knowing its shape in advance.

We define the layer type, with a case for each kind of node. The type parameter `A` stands for the sub-expressions:

```scala
import zio._
import zio.prelude._
import zio.prelude.recursive._

sealed trait ExprF[+A]
final case class Lit(value: Int) extends ExprF[Nothing]
final case class Add[A](left: A, right: A) extends ExprF[A]
```

`Recursive` needs to know how to visit the sub-expressions of a layer. The operations that only rebuild or collapse the structure (`Recursive#fold`, `Recursive#transformUp`, `Recursive.unfold` and similar) require a `Covariant` instance for the layer type, and the operations that traverse it effectfully or in a fixed order (`Recursive#foldDown`, `Recursive#foldM` and similar) require a `ForEach` instance. `ForEach` extends `Covariant`, so a single `ForEach` instance for the layer type serves both:

```scala
implicit val exprForEach: ForEach[ExprF] =
  new ForEach[ExprF] {
    def forEach[G[+_]: IdentityBoth: Covariant, A, B](fa: ExprF[A])(f: A => G[B]): G[ExprF[B]] =
      fa match {
        case Lit(value)       => (Lit(value): ExprF[B]).succeed[G]
        case Add(left, right) => f(left).zipWith(f(right))(Add(_, _))
      }
  }
```

We build an expression tree by wrapping each layer in `Recursive`. The second type parameter of `Recursive` holds annotations, which are values stored in a `ZEnvironment` next to each layer. We do not need any here, so the annotations type is `Any` and we use an empty `ZEnvironment`:

```scala
def lit(value: Int): Recursive[ExprF, Any] =
  Recursive(Lit(value), ZEnvironment.empty)

def add(left: Recursive[ExprF, Any], right: Recursive[ExprF, Any]): Recursive[ExprF, Any] =
  Recursive(Add(left, right), ZEnvironment.empty)

val expression: Recursive[ExprF, Any] =
  add(lit(1), add(lit(2), lit(3)))
```

## Folding a Recursive Structure

`fold` processes the structure from the bottom up. It takes a function that receives one layer whose sub-expressions have already been folded, and returns a result for that layer. To evaluate the expression, each literal becomes its value and each addition becomes the sum of its two sides:

```scala
expression.fold[Int] {
  case Lit(value)       => value
  case Add(left, right) => left + right
}
// res1: Int = 6
```

A `Folder` wraps the same function as a value that can be reused. `Folder[ExprF, Int]` takes a layer of `Int` results and returns an `Int`:

```scala
val evaluate: Folder[ExprF, Int] =
  Folder[ExprF, Int] {
    case Lit(value)       => value
    case Add(left, right) => left + right
  }
// evaluate: Folder[ExprF, Int] = <function1>

expression.fold(evaluate.apply)
// res2: Int = 6
```

`Folder` has a `Folder#zip` method that runs two folders in one pass and returns both results as a pair, and an `Invariant` instance so that `Folder#invmap` can change the result type through an equivalence.

### Folding Effectfully

When producing a result for a layer can fail or needs an effect, `Recursive#foldM` threads an effect through the fold. The effect type needs `AssociativeFlatten`, `Covariant` and `IdentityBoth` instances, and the layer type needs `ForEach`. To fail the evaluation when a literal is negative, we fold into `Option`:

```scala
expression.foldM[Option, Int] {
  case Lit(value) => if (value < 0) None else Some(value)
  case Add(left, right) => Some(left + right)
}
// res3: Option[Int] = Some(value = 6)

add(lit(-1), lit(2)).foldM[Option, Int] {
  case Lit(value) => if (value < 0) None else Some(value)
  case Add(left, right) => Some(left + right)
}
// res4: Option[Int] = None
```

The same pattern is available specialised to common effect types as `Recursive#foldZIO`, `Recursive#foldSTM`, `Recursive#foldPure` and `Recursive#foldValidation`.

### Other Folds

`Recursive#foldRecursive` is like `fold`, but each sub-result is paired with the sub-expression it came from. `Recursive#foldDown` visits every node from the root downwards and threads an accumulator through. To count the nodes of the expression:

```scala
expression.foldDown(0)((count, _) => count + 1)
// res5: Int = 5
```

`Recursive#foldUp` threads an accumulator in a similar way, and `Recursive#foldDownSome` and `Recursive#foldUpSome` accept a partial function and leave the accumulator unchanged where it is not defined.

## Transforming a Recursive Structure

`Recursive#transformUp` and `Recursive#transformDown` rebuild the structure by applying a function to every node, bottom up or top down. The function receives a whole `Recursive` value and returns one, and `Recursive#transformUpSome` and `Recursive#transformDownSome` take a partial function. To negate every literal and then evaluate the result:

```scala
expression
  .transformUp[Any] { node =>
    node.caseValue match {
      case Lit(value) => lit(-value)
      case _          => node
    }
  }
  .fold(evaluate.apply)
// res6: Int = -6
```

## Unfolding

`Unfolder` is the reverse of `Folder`. It takes a seed value and produces one layer, whose sub-expressions are seeds for the next step. `Recursive.unfold` repeats that step until the unfolder produces a layer with no sub-expressions. To build an expression tree whose shape follows the Fibonacci recursion, we define an unfolder and unfold from the seed `4`:

```scala
val fibonacci: Unfolder[ExprF, Int] =
  Unfolder[ExprF, Int](n => if (n <= 1) Lit(1) else Add(n - 1, n - 2))
// fibonacci: Unfolder[ExprF, Int] = <function1>

val tree = Recursive.unfold[ExprF, Int](4)(fibonacci)
// tree: Recursive[ExprF, Any] = Recursive(
//   caseValue = Add(
//     left = Recursive(
//       caseValue = Add(
//         left = Recursive(
//           caseValue = Add(
//             left = Recursive(
//               caseValue = Lit(value = 1),
//               annotations = ZEnvironment()
//             ),
//             right = Recursive(
//               caseValue = Lit(value = 1),
//               annotations = ZEnvironment()
//             )
//           ),
//           annotations = ZEnvironment()
//         ),
//         right = Recursive(
//           caseValue = Lit(value = 1),
//           annotations = ZEnvironment()
//         )
//       ),
//       annotations = ZEnvironment()
//     ),
//     right = Recursive(
//       caseValue = Add(
//         left = Recursive(
//           caseValue = Lit(value = 1),
//           annotations = ZEnvironment()
//         ),
//         right = Recursive(
//           caseValue = Lit(value = 1),
//           annotations = ZEnvironment()
//         )
//       ),
//       annotations = ZEnvironment()
//     )
//   ),
//   annotations = ZEnvironment()
// )

tree.fold(evaluate.apply)
// res7: Int = 5
```

`Unfolder#zip` combines two unfolders into one over pairs of seeds and requires an `AssociativeBoth` instance for the layer type, and like `Folder` it has an `Invariant` instance. `Recursive.unfoldRecursive` is a variant in which the step can also return an existing `Recursive` value in place of a seed, which is then reused as is.
