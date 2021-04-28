package zio.internal.macros

import zio.internal.macros.LayerCompose._

final case class Graph[Key, A](nodes: List[Node[Key, A]], keyEquals: (Key, Key) => Boolean) {
  def buildComplete(outputs: List[Key]): Either[::[GraphError[Key, A]], LayerCompose[A]] =
    forEach(outputs) { output =>
      getNodeWithOutput[GraphError[Key, A]](output, error = GraphError.MissingTopLevelDependency(output))
        .flatMap(node => buildNode(node, Set(node)))
    }
      .map(_.distinct.combineHorizontally)

  def map[B](f: A => B): Graph[Key, B] =
    Graph(nodes.map(_.map(f)), keyEquals)

  private def getNodeWithOutput[E](output: Key, error: E): Either[::[E], Node[Key, A]] =
    nodes.find(_.outputs.exists(keyEquals(_, output))).toRight(::(error, Nil))

  private def getDependencies[E](node: Node[Key, A]): Either[::[GraphError[Key, A]], List[Node[Key, A]]] =
    forEach(node.inputs) { input =>
      getNodeWithOutput(input, error = GraphError.MissingTransitiveDependency(node, input))
    }
      .map(_.distinct)

  private def buildNode(node: Node[Key, A], seen: Set[Node[Key, A]]): Either[::[GraphError[Key, A]], LayerCompose[A]] =
    getDependencies(node).flatMap {
      forEach(_) { out =>
        assertNonCircularDependency(node, seen, out).flatMap(_ => buildNode(out, seen + out))
      }.map {
        _.distinct.combineHorizontally >>> LayerCompose.succeed(node.value)
      }
    }

  private def assertNonCircularDependency(
    node: Node[Key, A],
    seen: Set[Node[Key, A]],
    dependency: Node[Key, A]
  ): Either[::[GraphError[Key, A]], Unit] =
    if (seen(dependency))
      Left(::(GraphError.CircularDependency(node, dependency, seen.size), Nil))
    else
      Right(())

  private def forEach[B, C](
    list: List[B]
  )(f: B => Either[::[GraphError[Key, A]], C]): Either[::[GraphError[Key, A]], List[C]] =
    list.foldRight[Either[::[GraphError[Key, A]], List[C]]](Right(List.empty)) { (a, b) =>
      (f(a), b) match {
        case (Left(::(e, es)), Left(e1s)) => Left(::(e, es ++ e1s))
        case (Left(es), _)                => Left(es)
        case (_, Left(es))                => Left(es)
        case (Right(a), Right(b))         => Right(a +: b)
      }
    }
}
