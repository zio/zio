package zio.internal.macros

import zio.internal.macros.LayerTree._

final case class Graph[Key, A](
  nodes: List[Node[Key, A]],
  keyEquals: (Key, Key) => Boolean,
  environment: Key => Node[Key, A],
  envKeys: List[Key]
) {

  // Map assigning to each type the times that it must be built
  // -1 designs a `Key` from the environment
  private var neededKeys: Map[Key, Int] = Map.empty
  // Dependencies to pass to next iteration of buildComplete
  private var dependencies: List[Key]    = Nil
  private var envDependencies: List[Key] = Nil

  private var usedEnvKeys: Set[Key] = Set.empty
  def usedRemainders(): Set[A]      = usedEnvKeys.map(environment(_)).map(_.value)
  private var topLevel              = true

  def buildNodes(
    outputs: List[Key],
    sideEffectNodes: List[Node[Key, A]]
  ): Either[::[GraphError[Key, A]], LayerTree[A]] = for {
    _           <- mkNeededKeys(outputs ++ sideEffectNodes.flatMap(_.inputs), true)
    sideEffects <- forEach(sideEffectNodes)(buildNode).map(_.combineHorizontally)
    rightTree   <- build(outputs).map(_._1)
    leftTree    <- buildComplete(constructDeps())
  } yield leftTree >>> (rightTree ++ sideEffects)

  private def buildComplete(outputs: List[Key]): Either[::[GraphError[Key, A]], LayerTree[A]] =
    if (!outputs.isEmpty)
      for {
        _         <- Right(restartKeys())
        _         <- mkNeededKeys(outputs)
        rightTree <- build(outputs).map(_._1)
        leftTree  <- buildComplete(constructDeps())
      } yield leftTree >>> rightTree
    else Right(LayerTree.empty)

  private def constructDeps(): List[Key] =
    if (dependencies.isEmpty) dependencies
    else distinctKeys(dependencies) ++ distinctKeys(envDependencies)

  /**
   * Restarts variables for next iteration of buildComplete
   */
  private def restartKeys(): Unit = {
    neededKeys = Map.empty
    dependencies = Nil
    envDependencies = Nil
  }

  private def distinctKeys(keys: List[Key]): List[Key] = {
    var distinct: List[Key] = List.empty
    for (k <- keys) {
      if (!distinct.exists(k2 => keyEquals(k, k2) || keyEquals(k2, k))) distinct = k :: distinct
    }
    distinct.reverse
  }

  /**
   * Initializes neededKeys
   */
  def mkNeededKeys(
    outputs: List[Key],
    topLevel: Boolean = false,
    seen: Set[Node[Key, A]] = Set.empty,
    parent: Option[Node[Key, A]] = None
  ): Either[::[GraphError[Key, A]], Unit] = {
    var created: List[Key]          = List.empty
    var (envOutputs, normalOutputs) = outputs.partition(isEnv(_))

    envOutputs.map(addEnv(_))

    forEach(normalOutputs) { output =>
      if (created.exists(k => keyEquals(k, output))) {
        if (neededKeys.get(output).isEmpty) throw new Throwable("This can't happen.")
        Right(())
      } else {
        for {
          node <- getNodeWithOutput[GraphError[Key, A]](
                    output,
                    error =
                      if (topLevel || parent.isEmpty) Some(GraphError.MissingTopLevelDependency(output))
                      else Some(GraphError.missingTransitiveDependency(parent.get, output))
                  )
          nodeOutputs = node.outputs.map(out => findKey(out, outputs))
          _          <- Right(nodeOutputs.map(addKey(_)))
          _          <- Right { created = nodeOutputs ++ created }
          _ <- parent match {
                 case Some(p) => assertNonCircularDependency(p, seen, node)
                 case None    => Right(())
               }
          _ <- mkNeededKeys(node.inputs, false, seen + node, Some(node))
        } yield ()
      }
    }.map(_ => ())
  }

  private def findKey(key: Key, keys: List[Key]): Key =
    keys.find(k => keyEquals(key, k)).getOrElse(key)

  private def addEnv(key: Key): Unit = {
    usedEnvKeys = usedEnvKeys + key
    neededKeys.get(key) match {
      case Some(_) => ()
      case None    => neededKeys = neededKeys + (key -> -1)
    }
  }
  private def addKey(key: Key): Unit =
    neededKeys.get(key) match {
      case Some(-1) => ()
      case Some(n)  => neededKeys = neededKeys + (key -> (n + 1))
      case None     => neededKeys = neededKeys + (key -> 1)
    }

  private def buildNode(node: Node[Key, A]): Either[::[GraphError[Key, A]], LayerTree[A]] =
    build(node.inputs).map { case (deps, allEnv) =>
      if (allEnv) LayerTree.succeed(node.value)
      else deps >>> LayerTree.succeed(node.value)
    }

  private def tapKey(key: Key): Option[Int] =
    neededKeys.get(key) match {
      case Some(n) => Some(n)
      case None    => neededKeys.find(pair => keyEquals(pair._1, key) || keyEquals(key, pair._1)).map(_._2)
    }

  /**
   * Builds a layer containing only types that appears once. Types appearing
   * more than once are replaced with ZLayer.environment[_] and left for the
   * next iteration of buildComplete to create.
   */
  private def build(outputs: List[Key]): Either[::[GraphError[Key, A]], (LayerTree[A], Boolean)] =
    forEach(outputs) { output =>
      if (isEnv(output)) {
        envDependencies = output :: envDependencies
        Right((LayerTree.succeed(environment(output).value), true))
      } else
        tapKey(output) match {
          case None => throw new Throwable(s"This can't happen")
          case Some(1) =>
            getNodeWithOutput[GraphError[Key, A]](output).flatMap(node => buildNode(node).map(tree => (tree, false)))
          case Some(n) => {
            dependencies = output :: dependencies
            Right((LayerTree.succeed(environment(output).value), true))
          }
        }
    }.map { deps =>
      (deps.map(_._1).distinct.combineHorizontally, deps.forall(_._2))
    }

  def map[B](f: A => B): Graph[Key, B] =
    Graph(nodes.map(_.map(f)), keyEquals, key => environment(key).map(f), envKeys)

  private def getNodeWithOutput[E](output: Key, error: Option[E] = None): Either[::[E], Node[Key, A]] =
    nodes
      .find(_.outputs.exists(keyEquals(_, output)))
      .toRight(error.map(e => ::(e, Nil)).getOrElse(throw new Throwable("This can't happen")))

  private def isEnv(key: Key): Boolean =
    envKeys.exists(env => keyEquals(key, env) || keyEquals(env, key))

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
