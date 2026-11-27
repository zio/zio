package zio.internal.macros

import zio.internal.macros.LayerTree.{ComposeH, ComposeV, Defs, Empty, Ref, Shared, Value}
import scala.collection.mutable

sealed abstract class LayerTree[+A] extends Product with Serializable { self =>

  def >>>[A1 >: A](that: LayerTree[A1]): LayerTree[A1] =
    if (self eq Empty) that else if (that eq Empty) self else ComposeV(self, that)

  def ++[A1 >: A](that: LayerTree[A1]): LayerTree[A1] =
    if (self eq Empty) that else if (that eq Empty) self else ComposeH(self, that)

  /**
   * Folds over the tree, transparently expanding any sharing introduced by
   * [[Shared]] / [[Ref]]. A `Ref` is folded as though the subtree it points at
   * were inlined at that position, so consumers that care about the logical
   * shape of the graph (rendering, unused-layer warnings) observe exactly the
   * same result whether or not sharing is present.
   */
  def fold[B](z: B, value: A => B, composeH: (B, B) => B, composeV: (B, B) => B): B = {
    // Index every shared sub-tree up front so that a `Ref` folds correctly
    // regardless of whether it appears before or after its `Shared` definition.
    val shared = mutable.LongMap.empty[LayerTree[A]]

    def index(tree: LayerTree[A]): Unit = tree match {
      case ComposeH(left, right) => index(left); index(right)
      case ComposeV(left, right) => index(left); index(right)
      case Shared(id, tree0)     => shared.update(id.toLong, tree0); index(tree0)
      case Defs(defs, body) =>
        defs.foreach { case (id, tree0) => shared.update(id.toLong, tree0) }
        defs.foreach { case (_, tree0) => index(tree0) }
        index(body)
      case _ => ()
    }

    index(self)

    def loop(tree: LayerTree[A]): B = tree match {
      case Empty         => z
      case Value(value0) => value(value0)
      case ComposeH(left, right) =>
        composeH(loop(left), loop(right))
      case ComposeV(left, right) =>
        composeV(loop(left), loop(right))
      case Shared(_, tree0) =>
        loop(tree0)
      case Defs(_, body) =>
        loop(body)
      case Ref(id) =>
        shared.get(id.toLong) match {
          case Some(tree0) => loop(tree0)
          case None        => z
        }
    }

    loop(self)
  }

  /**
   * Folds over the tree without expanding sharing: each [[Shared]] subtree is
   * visited exactly once and every [[Ref]] to it is folded via `ref`. This is
   * what code generation uses, so that a shared sub-graph is emitted as a
   * single `lazy val` and referenced by name everywhere else.
   */
  def foldShared[B](
    z: B,
    value: A => B,
    composeH: (B, B) => B,
    composeV: (B, B) => B,
    shared: (Int, B) => B,
    ref: Int => B
  ): B = {
    def loop(tree: LayerTree[A]): B = tree match {
      case Empty         => z
      case Value(value0) => value(value0)
      case ComposeH(left, right) =>
        composeH(loop(left), loop(right))
      case ComposeV(left, right) =>
        composeV(loop(left), loop(right))
      case Defs(_, body) => loop(body)
      case Shared(id, _) => ref(id)
      case Ref(id)       => ref(id)
    }

    // `sharedDefs` lists the sub-graphs in the order they were built, so each
    // one's body only refers to sub-graphs already folded. Every `Ref` in the
    // tree therefore resolves to a name that has been bound by the time it is
    // reached.
    sharedDefs.foreach { case (id, body) => shared(id, loop(body)) }

    loop(self)
  }

  /**
   * The definitions of the shared sub-graphs, in the order they must be bound.
   * Carried at the root of the tree rather than inline, because sibling
   * de-duplication can drop a `Shared` node while leaving `Ref`s to it
   * elsewhere.
   */
  def sharedDefs: List[(Int, LayerTree[A])] = self match {
    case Defs(defs, _) => defs
    case _             => Nil
  }

  def map[B](f: A => B): LayerTree[B] = self match {
    case Empty                 => Empty
    case Value(value0)         => Value(f(value0))
    case ComposeH(left, right) => ComposeH(left.map(f), right.map(f))
    case ComposeV(left, right) => ComposeV(left.map(f), right.map(f))
    case Shared(id, tree)      => Shared(id, tree.map(f))
    case Ref(id)               => Ref(id)
    case Defs(defs, body) =>
      Defs(defs.map { case (id, tree) => id -> tree.map(f) }, body.map(f))
  }

  def toSet[A1 >: A]: Set[A1] = fold[Set[A1]](Set.empty[A1], Set(_), _ ++ _, _ ++ _)

  def toList: List[A] =
    fold[mutable.LinkedHashSet[A]](mutable.LinkedHashSet.empty[A], mutable.LinkedHashSet(_), _ ++ _, _ ++ _).toList
}

object LayerTree {
  def succeed[A](value: A): LayerTree[A] = Value(value)
  def empty: LayerTree[Nothing]          = Empty

  /**
   * Attaches the definitions of the shared sub-graphs referenced by `tree`.
   * `defs` must be ordered so that each definition only refers to earlier ones.
   */
  def withSharedDefs[A](tree: LayerTree[A], defs: List[(Int, LayerTree[A])]): LayerTree[A] =
    if (defs.isEmpty) tree else Defs(defs, tree)

  case object Empty                                                      extends LayerTree[Nothing]
  final case class Value[+A](value: A)                                   extends LayerTree[A]
  final case class ComposeH[+A](left: LayerTree[A], right: LayerTree[A]) extends LayerTree[A]
  final case class ComposeV[+A](left: LayerTree[A], right: LayerTree[A]) extends LayerTree[A]

  /**
   * Marks `tree` as a shared sub-graph, identified by `id`. It appears exactly
   * once in the overall tree; every other use site is a [[Ref]] carrying the
   * same `id`.
   */
  final case class Shared[+A](id: Int, tree: LayerTree[A]) extends LayerTree[A]

  /**
   * Carries the definitions of every shared sub-graph alongside the tree that
   * uses them. See [[LayerTree.sharedDefs]].
   */
  final case class Defs[+A](defs: List[(Int, LayerTree[A])], body: LayerTree[A]) extends LayerTree[A]

  /**
   * A reference to the [[Shared]] sub-graph with this `id`.
   */
  final case class Ref(id: Int) extends LayerTree[Nothing]

  implicit final class LayerComposeIterableOps[A](private val self: Iterable[LayerTree[A]]) extends AnyVal {
    def combineHorizontally: LayerTree[A] = self.foldLeft[LayerTree[A]](Empty)(_ ++ _)
  }
}
