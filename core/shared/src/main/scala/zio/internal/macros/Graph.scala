package zio.internal.macros

import zio.internal.macros.LayerCompose._

final case class Graph[A](nodes: List[Node[A]]) {

  def buildComplete(outputs: List[String]): Either[::[GraphError[A]], LayerCompose[A]] =
    forEach(outputs) { output =>
      getNodeWithOutput(output, error = GraphError.MissingTopLevelDependency(output))
        .flatMap(node => buildNode(node, Set(node)))
    }
      .map(_.distinct.combineHorizontally)

  def map[B](f: A => B): Graph[B] =
    Graph(nodes.map(_.map(f)))

  private def getNodeWithOutput[E](output: String, error: E): Either[::[E], Node[A]] =
    nodes.find(_.outputs.contains(output)).toRight(::(error, Nil))

  private def getDependencies[E](node: Node[A]): Either[::[GraphError[A]], List[Node[A]]] =
    forEach(node.inputs) { input =>
      getNodeWithOutput(input, error = GraphError.MissingDependency(node, input))
    }
      .map(_.distinct)

  private def buildNode(node: Node[A], seen: Set[Node[A]]): Either[::[GraphError[A]], LayerCompose[A]] =
    getDependencies(node).flatMap {
      forEach(_) { out =>
        assertNonCircularDependency(node, seen, out).flatMap(_ => buildNode(out, seen + out))
      }.map {
        _.distinct.combineHorizontally >>> LayerCompose.succeed(node.value)
      }
    }

  private def assertNonCircularDependency(
    node: Node[A],
    seen: Set[Node[A]],
    dependency: Node[A]
  ): Either[::[GraphError[A]], Unit] =
    if (seen(dependency))
      Left(::(GraphError.CircularDependency(node, dependency, seen.size), Nil))
    else
      Right(())

  private def forEach[B, C](
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
