package zio.internal.macros

import zio.internal.macros.LayerTree._

final case class Graph[Key, A](
  nodes: List[Node[Key, A]],
  keyEquals: (Key, Key) => Boolean,
  environment: Key => Node[Key, A]
) {

  // Map assigning to each type the times that it must be built
  private var neededKeys: Map[Key, Int] = Map.empty
  // Dependencies to pass to next iteration of buildComplete
  private var dependencies: List[Key] = Nil

  def buildNodes(outputs: List[Key], nodes: List[Node[Key, A]]): Either[::[GraphError[Key, A]], LayerTree[A]] = for {
    _           <- neededKeys((outputs ++ nodes.flatMap(_.inputs)).distinct)
    sideEffects <- forEach(nodes)(buildNode).map(_.combineHorizontally)
    rightTree   <- build(outputs)
    leftTree    <- buildComplete(dependencies.distinct)
  } yield leftTree >>> (rightTree ++ sideEffects)

  private def buildComplete(outputs: List[Key]): Either[::[GraphError[Key, A]], LayerTree[A]] =
    if (!outputs.isEmpty)
      for {
        _         <- Right(restartKeys())
        _         <- neededKeys(outputs)
        rightTree <- build(outputs)
        leftTree  <- buildComplete(dependencies.distinct)
      } yield leftTree >>> rightTree
    else Right(LayerTree.empty)

  /**
   * Restarts variables for next iteration of buildComplete
   */
  private def restartKeys(): Unit = {
    neededKeys = Map.empty
    dependencies = Nil
  }

  /**
   * Initializes neededKeys
   */
  private def neededKeys(
    outputs: List[Key],
    seen: Set[Node[Key, A]] = Set.empty,
    parent: Option[Node[Key, A]] = None
  ): Either[::[GraphError[Key, A]], Unit] =
    forEach(outputs) { output =>
      for {
        node <- parent match {
                  case Some(p) =>
                    getNodeWithOutput[GraphError[Key, A]](
                      output,
                      error = GraphError.missingTransitiveDependency(p, output)
                    )
                  case None =>
                    getNodeWithOutput[GraphError[Key, A]](output, error = GraphError.MissingTopLevelDependency(output))
                }
        _ <- Right(addKey(output))
        _ <- parent match {
               case Some(p) => assertNonCircularDependency(p, seen, node)
               case None    => Right(())
             }
        _ <- neededKeys(node.inputs, seen + node, Some(node))
      } yield ()
    }.map(_ => ())

  private def addKey(key: Key): Unit =
    neededKeys.get(key) match {
      case Some(n) => neededKeys = neededKeys + (key -> (n + 1))
      case None    => neededKeys = neededKeys + (key -> 1)
    }

  /**
   * Builds a layer containing only types that appears once. Types appearing
   * more than once are replaced with ZLayer.environment[_] and left for the
   * next iteration of buildComplete to create.
   */
  private def build(outputs: List[Key]): Either[::[GraphError[Key, A]], LayerTree[A]] =
    forEach(outputs) { output =>
      neededKeys.get(output) match {
        case None => Right(LayerTree.empty)
        case Some(1) =>
          getNodeWithOutput[GraphError[Key, A]](output, error = GraphError.MissingTopLevelDependency(output))
            .flatMap(node => buildNode(node, Set(node)))
        case Some(n) => {
          dependencies = output :: dependencies
          Right(LayerTree.succeed(environment(output).value))
        }
      }
    }
      .map(_.distinct.combineHorizontally)

  private def buildNode(node: Node[Key, A]): Either[::[GraphError[Key, A]], LayerTree[A]] =
    forEach(node.inputs) { output =>
      neededKeys.get(output) match {
        case None => Right(LayerTree.empty)
        case Some(1) =>
          getNodeWithOutput[GraphError[Key, A]](output, error = GraphError.MissingTopLevelDependency(output))
            .flatMap(node => buildNode(node, Set(node)))
        case Some(n) => {
          dependencies = output :: dependencies
          Right(LayerTree.empty)
        }
      }
    }
      .map(_.distinct.combineHorizontally)
      .map(_ >>> LayerTree.succeed(node.value))

  def map[B](f: A => B): Graph[Key, B] =
    Graph(nodes.map(_.map(f)), keyEquals, key => environment(key).map(f))

  private def getNodeWithOutput[E](output: Key, error: E): Either[::[E], Node[Key, A]] =
    nodes.find(_.outputs.exists(keyEquals(_, output))).toRight(::(error, Nil))

  private def buildNode(
    node: Node[Key, A],
    seen: Set[Node[Key, A]]
  ): Either[::[GraphError[Key, A]], LayerTree[A]] =
    forEach(node.inputs) { input =>
      for {
        out <- getNodeWithOutput(input, error = GraphError.missingTransitiveDependency(node, input))
        _   <- assertNonCircularDependency(node, seen, out)
        result <- neededKeys.get(input) match {
                    case None    => Left(::(GraphError.missingTransitiveDependency(node, input), Nil))
                    case Some(1) => buildNode(out, seen + out)
                    case Some(n) => {
                      dependencies = input :: dependencies
                      Right(LayerTree.empty)
                    }
                  }
      } yield result
    }.map {
      _.distinct.combineHorizontally >>> LayerTree.succeed(node.value)
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
