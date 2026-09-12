package zio.internal.macros

import zio.internal.macros.LayerTree._

import scala.collection.mutable

final case class Graph[Key, A](
  nodes: List[Node[Key, A]],
  keyEquals: (Key, Key) => Boolean,
  unknownLayerFactory: Key => Option[Node[Key, A]]
) {

  def buildComplete(outputs: List[Key]): Either[::[GraphError[Key, A]], LayerTree[A]] =
    forEach(outputs) { output =>
      getNodeWithOutput[GraphError[Key, A]](output, error = GraphError.MissingTopLevelDependency(output))
        .flatMap(node => buildNode(node, Set(node)))
    }
      .map(_.distinct.combineHorizontally)

  def buildNodes(nodes: List[Node[Key, A]]): Either[::[GraphError[Key, A]], LayerTree[A]] =
    forEach(nodes)(buildNode).map(_.combineHorizontally)

  private def buildNode(node: Node[Key, A]): Either[::[GraphError[Key, A]], LayerTree[A]] =
    forEach(node.inputs) { output =>
      getNodeWithOutput[GraphError[Key, A]](output, error = GraphError.missingTransitiveDependency(node, output))
        .flatMap(node => buildNode(node, Set(node)))
    }
      .map(_.distinct.combineHorizontally)
      .map(_ >>> LayerTree.succeed(node.value))

  def map[B](f: A => B): Graph[Key, B] =
    Graph(nodes.map(_.map(f)), keyEquals, unknownLayerFactory(_).map(_.map(f)))

  private val nodeWithOutputCache = new java.util.HashMap[Key, Option[Node[Key, A]]]

  private def getNodeWithOutput[E](output: Key, error: => E): Either[::[E], Node[Key, A]] =
    nodeWithOutputCache.computeIfAbsent(output, findNodeWithOutput).toRight(::(error, Nil))

  private def findNodeWithOutput(output: Key): Option[Node[Key, A]] =
    nodes.find(_.outputs.exists(keyEquals(_, output))).orElse(unknownLayerFactory(output))

  /**
   * Assigns each node a stable id the first time it is built, so that a node
   * reachable by many distinct paths through the dependency DAG contributes a
   * single `Shared` sub-tree plus a `Ref` at every other use site, rather than
   * a fresh copy of its whole sub-graph per path.
   *
   * Without this the emitted `LayerTree` has one leaf per distinct path through
   * the graph, which is exponential in the number of layers.
   */
  // Keyed by identity, not by `Node`'s structural equality. Two distinct nodes
  // can compare equal when their types and layer expressions look alike (an
  // abstract type member such as `spec.Environment` is one way this happens),
  // and sharing them would substitute one layer for another at runtime.
  // `nodes` holds one instance per provided layer, so identity is the right
  // notion here.
  private val treeCache  = new java.util.IdentityHashMap[Node[Key, A], Integer]
  private var nextTreeId = 0

  /**
   * Definitions of the shared sub-graphs, in the order they were built: a
   * sub-graph is always preceded by every sub-graph it references.
   *
   * These are kept here rather than inline in the tree because the tree is
   * still de-duplicated and recombined after it is built (see `distinct` below
   * and `buildComplete`/`buildNodes`), which can drop a `Shared` node while
   * leaving `Ref`s to it elsewhere. Holding the definitions separately means a
   * `Ref` can never outlive its definition.
   */
  private val sharedDefs = mutable.ListBuffer.empty[(Int, LayerTree[A])]

  def sharedSubTrees: List[(Int, LayerTree[A])] = sharedDefs.toList

  private def buildNode(
    node: Node[Key, A],
    seen: Set[Node[Key, A]]
  ): Either[::[GraphError[Key, A]], LayerTree[A]] =
    treeCache.get(node) match {
      case null =>
        forEach(node.inputs) { input =>
          for {
            out    <- getNodeWithOutput(input, error = GraphError.missingTransitiveDependency(node, input))
            _      <- assertNonCircularDependency(node, seen, out)
            result <- buildNode(out, seen + out)
          } yield result
        }.map { children =>
          val body = children.distinct.combineHorizontally >>> LayerTree.succeed(node.value)
          // A node with no inputs contributes a single leaf, so sharing it buys
          // nothing and only risks conflating nodes that must stay distinct:
          // `getNodeWithOutput` matches by subtyping, so one node can be
          // returned for several different requested types (an abstract type
          // member such as a spec's `Environment` is one way this arises).
          if (node.inputs.isEmpty) body
          else {
            val id = nextTreeId
            nextTreeId += 1
            treeCache.put(node, id)
            sharedDefs += (id -> body)
            LayerTree.Shared(id, body)
          }
        }
      case id => Right(LayerTree.Ref(id.intValue))
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
