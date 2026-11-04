package zio.internal.macros

import zio.internal.macros.LayerTree._

final case class Graph[Key, A](
  nodes: List[Node[Key, A]],
  keyEquals: (Key, Key) => Boolean,
  environment: Key => Node[Key, A],
  remainder: RemainderMethod[Key]
) {

  private var neededNodes: List[Node[Key, A]] = List.empty
  private var dependencies: List[Key]         = Nil
  private var envDependencies: List[Key]      = Nil
  private var usedEnvKeys: Set[Key]           = Set.empty

  private val environmentCache    = new java.util.HashMap[Key, Node[Key, A]]
  private val nodeWithOutputCache = new java.util.HashMap[Key, Option[Node[Key, A]]]

  private def getEnvNode(key: Key): Node[Key, A] =
    environmentCache.computeIfAbsent(key, environment(_))

  def usedRemainders(): Set[A] = usedEnvKeys.map(getEnvNode(_).value)

  def buildNodes(
    outputs: List[Key],
    sideEffectNodes: List[Node[Key, A]]
  ): Either[::[GraphError[Key, A]], LayerTree[A]] = for {
    _           <- mkNeededKeys(outputs, topLevel = true)
    _           <- forEach(sideEffectNodes)(node => mkNeededKeys(node.inputs, topLevel = false, Set(node), Some(node)))
    sideEffects <- forEach(sideEffectNodes)(buildNode).map(_.combineHorizontally)
    rightTree   <- build(outputs).map(_._1)
    leftTree    <- buildComplete(constructDeps())
  } yield leftTree >>> (rightTree ++ sideEffects)

  private def buildComplete(outputs: List[Key]): Either[::[GraphError[Key, A]], LayerTree[A]] =
    if (outputs.nonEmpty)
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

  private def restartKeys(): Unit = {
    neededNodes = List.empty
    dependencies = Nil
    envDependencies = Nil
  }

  private def distinctKeys(keys: List[Key]): List[Key] = {
    var distinct: List[Key] = List.empty
    for (k <- keys) {
      if (!distinct.exists(k2 => areEquals(k, k2) && areEquals(k2, k))) distinct = k :: distinct
    }
    distinct.reverse
  }

  def mkNeededKeys(
    outputs: List[Key],
    topLevel: Boolean = false,
    seen: Set[Node[Key, A]] = Set.empty,
    parent: Option[Node[Key, A]] = None
  ): Either[::[GraphError[Key, A]], Unit] = {
    var created: Set[Node[Key, A]]  = Set.empty
    val (envOutputs, normalOutputs) = outputs.partition(isEnv)

    envOutputs.foreach(addEnv)

    forEach(normalOutputs) { output =>
      for {
        node <- getNodeWithOutput[GraphError[Key, A]](
                  output,
                  error =
                    if (topLevel || parent.isEmpty) Some(GraphError.MissingTopLevelDependency(output))
                    else Some(GraphError.missingTransitiveDependency(parent.get, output))
                )
        _ <- if (created.contains(node)) {
               Right(())
             } else {
               for {
                 _ <- Right(addNode(node))
                 _ <- Right { created = created + node }
                 _ <- parent match {
                        case Some(p) => assertNonCircularDependency(p, seen, node)
                        case None    => Right(())
                      }
                 _ <- mkNeededKeys(node.inputs, false, seen + node, Some(node))
               } yield ()
             }
      } yield ()
    }.map(_ => ())
  }

  private def addEnv(key: Key): Unit = {
    val keyFromEnv = remainder match {
      case RemainderMethod.Provided(envKeys) =>
        envKeys
          .find(env => areEquals(env, key))
          .getOrElse(key)
      case RemainderMethod.Inferred =>
        key
    }
    usedEnvKeys = usedEnvKeys + keyFromEnv
  }

  private def addNode(node: Node[Key, A]): Unit =
    neededNodes = node :: neededNodes

  private def buildNode(node: Node[Key, A]): Either[::[GraphError[Key, A]], LayerTree[A]] =
    build(node.inputs).map { case (deps, allEnv) =>
      if (allEnv) LayerTree.succeed(node.value)
      else deps >>> LayerTree.succeed(node.value)
    }

  private def countNode(node: Node[Key, A]): Int =
    neededNodes.count(_ == node)

  private def build(outputs: List[Key]): Either[::[GraphError[Key, A]], (LayerTree[A], Boolean)] =
    forEach(outputs) { output =>
      if (isEnv(output)) {
        envDependencies = output :: envDependencies
        Right((LayerTree.succeed(getEnvNode(output).value), true))
      } else
        getNodeWithOutput[GraphError[Key, A]](output).flatMap { node =>
          countNode(node) match {
            case 0 => throw new Throwable("This can't happen")
            case 1 => buildNode(node).map(tree => (tree, false))
            case _ =>
              dependencies = output :: dependencies
              Right((LayerTree.succeed(getEnvNode(output).value), true))
          }
        }
    }.map { deps =>
      (deps.map(_._1).distinct.combineHorizontally, deps.forall(_._2))
    }

  def map[B](f: A => B): Graph[Key, B] =
    Graph(nodes.map(_.map(f)), keyEquals, key => getEnvNode(key).map(f), remainder)

  private def getNodeWithOutput[E](output: Key, error: Option[E] = None): Either[::[E], Node[Key, A]] =
    findNodeWithOutput(output)
      .toRight(error.map(e => ::(e, Nil)).getOrElse(throw new Throwable("This can't happen")))

  private def findNodeWithOutput(output: Key): Option[Node[Key, A]] =
    nodeWithOutputCache.computeIfAbsent(output, out => nodes.find(_.outputs.exists(areEquals(_, out))))

  private def isEnv(key: Key): Boolean =
    remainder match {
      case RemainderMethod.Provided(envKeys) =>
        envKeys.exists(env => areEquals(env, key)) && findNodeWithOutput(key).isEmpty
      case RemainderMethod.Inferred =>
        findNodeWithOutput(key).isEmpty
    }

  private def areEquals(key1: Key, key2: Key): Boolean =
    keyEquals(key1, key2)

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
