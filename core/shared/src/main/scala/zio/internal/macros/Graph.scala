package zio.internal.macros

import zio.internal.macros.LayerLike._

final case class Graph[A: LayerLike](nodes: List[Node[A]]) {

  def buildComplete(outputs: List[String]): Either[::[GraphError[A]], A] =
    traverse(outputs) { output =>
      getNodeWithOutput(output, error = GraphError.MissingTopLevelDependency(output))
    }
      .flatMap(traverse(_)(node => buildNode(node, Set(node))))
      .map(_.distinct.combineHorizontally)

  def map[B: LayerLike](f: A => B): Graph[B] =
    Graph(nodes.map(_.map(f)))

  private def getNodeWithOutput[E](output: String, error: E): Either[::[E], Node[A]] =
    nodes.find(_.outputs.contains(output)).toRight(::(error, Nil))

  private def getDependencies[E](node: Node[A]): Either[::[GraphError[A]], List[Node[A]]] =
    traverse(node.inputs) { input =>
      getNodeWithOutput(input, error = GraphError.MissingDependency(node, input))
    }
      .map(_.distinct)

  private def buildNode(node: Node[A], seen: Set[Node[A]]): Either[::[GraphError[A]], A] =
    getDependencies(node).flatMap {
      traverse(_) { out =>
        for {
          _    <- assertNonCircularDependency(node, seen, out)
          tree <- buildNode(out, seen + out)
        } yield tree
      }.map {
        case Nil      => node.value
        case children => children.distinct.combineHorizontally >>> node.value
      }
    }

  private def assertNonCircularDependency(
    node: Node[A],
    seen: Set[Node[A]],
    dependency: Node[A]
  ): Either[::[GraphError.CircularDependency[A]], Unit] =
    if (seen(dependency))
      Left(::(GraphError.CircularDependency(node, dependency, seen.size), Nil))
    else
      Right(())

  private def traverse[B, C](
    list: List[B]
  )(f: B => Either[::[GraphError[A]], C]): Either[::[GraphError[A]], List[C]] =
    list.foldRight[Either[::[GraphError[A]], List[C]]](Right(List.empty)) { (a, b) =>
      (f(a), b) match {
        case (Left(::(e, es)), Left(e1s)) => Left(::(e, es ++ e1s))
        case (Left(es), _)                => Left(es)
        case (_, Left(es))                => Left(es)
        case (Right(a), Right(b))         => Right(a +: b)
      }
    }
}
