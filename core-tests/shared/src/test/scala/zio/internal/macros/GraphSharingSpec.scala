package zio.internal.macros

import zio.ZIOBaseSpec
import zio.test._

/**
 * Regression tests for https://github.com/zio/zio/issues/11053.
 *
 * `Graph.buildNode` used to expand the dependency DAG into a tree, emitting one
 * copy of a node's whole sub-graph for every distinct path that reached it. For
 * a densely connected set of layers that is exponential in the number of
 * layers: a ~100 layer application produced a `LayerTree` with tens of
 * thousands of nodes, each of which becomes a real `ZipWithPar` (two forked
 * fibers) at runtime. On the happy path `MemoMap` hid the cost, but when a
 * layer failed the resulting interrupt `Cause`s were combined pairwise across
 * the whole structure and exhausted the heap.
 *
 * These tests pin the structural property that prevents it: the emitted tree is
 * linear in the size of the graph, not in the number of paths through it.
 */
object GraphSharingSpec extends ZIOBaseSpec {

  private def node(value: String, inputs: List[String], outputs: List[String]): Node[String, String] =
    Node(inputs, outputs, value)

  /**
   * Counts constructors up to `limit`, then stops. Bounding the walk matters:
   * if sharing regresses, the tree really does contain hundreds of millions of
   * nodes, and an unbounded traversal would hang (or stack overflow) instead of
   * failing the assertion.
   */
  private def sizeUpTo(tree: LayerTree[String], limit: Int): Int = {
    var count = 0
    def loop(t: LayerTree[String]): Unit =
      if (count <= limit) t match {
        case LayerTree.Empty            => ()
        case LayerTree.Value(_)         => count += 1
        case LayerTree.ComposeH(l, r)   => count += 1; loop(l); loop(r)
        case LayerTree.ComposeV(l, r)   => count += 1; loop(l); loop(r)
        case LayerTree.Shared(_, t0)    => loop(t0)
        case LayerTree.Ref(_)           => ()
        case LayerTree.Defs(defs, body) =>
          // Each definition is emitted once, so they all count toward the size.
          defs.foreach { case (_, t0) => loop(t0) }
          loop(body)
      }
    loop(tree)
    count
  }

  /**
   * A "diamond chain" of `n` layers where layer `i` depends on `i - 1` and `i -
   * 2`. The number of distinct paths from the root to the leaves grows like the
   * Fibonacci sequence, so the fully expanded tree is exponential in `n` while
   * the graph itself has only `n` nodes.
   */
  private def diamondChain(n: Int): Graph[String, String] = {
    val nodes = (0 until n).toList.map { i =>
      val inputs = List(i - 1, i - 2).filter(_ >= 0).map(j => s"T$j")
      node(s"layer$i", inputs, List(s"T$i"))
    }
    Graph(nodes, (a: String, b: String) => a == b, (_: String) => None)
  }

  def spec = suite("GraphSharingSpec")(
    test("shares sub-graphs reachable by multiple paths") {
      val graph = diamondChain(4)
      graph.buildComplete(List("T3")) match {
        case Left(errors) => assertNever(s"failed to build: $errors")
        case Right(tree)  =>
          // T3 depends on T2 and T1, and T2 also depends on T1, so T1 is
          // reachable by two paths. It must be emitted once, as a `Shared`
          // sub-tree that the second path reaches through a `Ref`.
          //
          // T0 is deliberately excluded: it has no inputs, and leaf nodes are
          // not shared (a leaf is a single `Value`, so a `Ref` to it saves
          // nothing, and sharing it risks conflating nodes that must stay
          // distinct). It is therefore expected to appear more than once.
          //
          // Note this counts the *shared* representation. `fold` deliberately
          // inlines `Ref`s, so folding would legitimately see T1 twice.
          def emitted(t: LayerTree[String]): List[String] = t match {
            case LayerTree.Empty          => Nil
            case LayerTree.Value(v)       => List(v)
            case LayerTree.ComposeH(l, r) => emitted(l) ++ emitted(r)
            case LayerTree.ComposeV(l, r) => emitted(l) ++ emitted(r)
            case LayerTree.Shared(_, t0)  => emitted(t0)
            case LayerTree.Ref(_)         => Nil
            case LayerTree.Defs(defs, body) =>
              defs.flatMap { case (_, t0) => emitted(t0) } ++ emitted(body)
          }

          val values = emitted(tree)
          assertTrue(
            values.count(_ == "layer1") == 1,
            values.count(_ == "layer2") == 1,
            values.count(_ == "layer3") == 1
          )
      }
    },
    test("tree size stays linear in the number of layers") {
      // A 26-layer diamond chain has 317,810 distinct root-to-leaf paths. With
      // sharing the emitted tree is a couple of hundred nodes.
      //
      // `n` is deliberately modest: the old algorithm's cost is in *building*
      // the tree, not walking it, so a larger chain would make a regression
      // hang here rather than fail. At 26 the unshared tree is still built in
      // well under a second, and the assertion below fails decisively.
      val n = 26
      // Generous: the shared tree is ~200 nodes, the unshared one 317,810. Any
      // value between the two separates them, so the exact bound is not
      // load-bearing. It only has to be far from both.
      val limit = 2000
      val graph = diamondChain(n)
      graph.buildComplete(List(s"T${n - 1}")) match {
        case Left(errors) => assertNever(s"failed to build: $errors")
        case Right(tree) =>
          val size = sizeUpTo(tree, limit)
          assertTrue(size < limit)
      }
    },
    test("expanding fold observes the same layers as the shared tree") {
      // `fold` inlines `Ref`s so that rendering and unused-layer warnings see
      // the logical graph. It must agree with the shared representation on
      // which layers are present, regardless of `Shared`/`Ref` ordering.
      val n     = 12
      val graph = diamondChain(n)
      graph.buildComplete(List(s"T${n - 1}")) match {
        case Left(errors) => assertNever(s"failed to build: $errors")
        case Right(tree) =>
          val expected = (0 until n).map(i => s"layer$i").toSet
          assertTrue(tree.toSet == expected, tree.toList.toSet == expected)
      }
    }
  )
}
