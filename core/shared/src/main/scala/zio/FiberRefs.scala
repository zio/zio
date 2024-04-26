/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio

import zio.FiberRefs.{FiberRefStack, FiberRefStackEntry}
import zio.internal.SpecializationHelpers.SpecializeInt
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.tailrec

/**
 * `FiberRefs` is a data type that represents a collection of `FiberRef` values.
 * This allows safely propagating `FiberRef` values across fiber boundaries, for
 * example between an asynchronous producer and consumer.
 */
final class FiberRefs private (
  private[zio] val fiberRefLocals: Map[FiberRef[_], FiberRefStack[_]]
) { self =>

  /**
   * Returns a new fiber refs with the specified ref deleted from it.
   */
  def delete(fiberRef: FiberRef[_]): FiberRefs = {
    val newMap = fiberRefLocals - fiberRef
    if (newMap eq fiberRefLocals) self
    else FiberRefs(newMap)
  }

  /**
   * Returns a set of each `FiberRef` in this collection.
   */
  def fiberRefs: Set[FiberRef[_]] =
    fiberRefLocals.keySet

  /**
   * Boolean flag which indicates whether the FiberRefs map contains an entry
   * that will cause the map to be changed when [[forkAs]] is called.
   *
   * This way we can avoid calling `fiberRefLocals.transform` on every
   * invocation of [[forkAs]] when we already know that the map will not be
   * transformed
   */
  private var needsTransformWhenForked: Boolean = true

  /**
   * Forks this collection of fiber refs as the specified child fiber id. This
   * will potentially modify the value of the fiber refs, as determined by the
   * individual fiber refs that make up the collection.
   */
  def forkAs(childId: FiberId.Runtime): FiberRefs =
    if (needsTransformWhenForked) {
      var modified = false
      val childMap = fiberRefLocals.transform { case (fiberRef, fiberRefStack) =>
        val oldValue = fiberRefStack.value.asInstanceOf[fiberRef.Value]
        val newValue = fiberRef.patch(fiberRef.fork)(oldValue)
        if (oldValue == newValue) fiberRefStack
        else {
          modified = true
          fiberRefStack.put(childId, newValue)
        }
      }

      if (modified) FiberRefs(childMap)
      else {
        needsTransformWhenForked = false
        self
      }
    } else self

  /**
   * Gets the value of the specified `FiberRef` in this collection of `FiberRef`
   * values if it exists or `None` otherwise.
   */
  def get[A](fiberRef: FiberRef[A]): Option[A] =
    Option(getOrNull(fiberRef))

  /**
   * Gets the value of the specified `FiberRef` in this collection of `FiberRef`
   * values if it exists or the `initial` value of the `FiberRef` otherwise.
   */
  def getOrDefault[A](fiberRef: FiberRef[A]): A =
    getOrNull(fiberRef) match {
      case null => fiberRef.initial
      case v    => v
    }

  private[zio] def getOrNull[A](fiberRef: FiberRef[A]): A = {
    val out = fiberRefLocals.getOrElse(fiberRef, null)
    if (out eq null) null else out.value
  }.asInstanceOf[A]

  /**
   * Joins this collection of fiber refs to the specified collection, as the
   * specified fiber id. This will perform diffing and merging to ensure
   * preservation of maximum information from both child and parent refs.
   */
  def joinAs(fiberId: FiberId.Runtime)(that: FiberRefs): FiberRefs = {
    @tailrec
    def findAncestor(initialFiberValue: Any)(
      parentStack: List[FiberRefStackEntry[?]],
      parentDepth: Int,
      childStack: List[FiberRefStackEntry[?]],
      childDepth: Int
    ): Any =
      parentStack match {
        case Nil => initialFiberValue // case (Nil, _)
        case parentHead :: parentAncestors =>
          childStack match {
            case Nil => initialFiberValue // case (_, Nil)
            case childHead :: childAncestors => // case (head0 :: tail0, head1 :: tail1)
              if (parentHead.fiberId == childHead.fiberId)
                if (childHead.version > parentHead.version) parentHead.value else childHead.value
              else if (childDepth > parentDepth)
                findAncestor(initialFiberValue)(parentStack, parentDepth, childAncestors, childDepth - 1)
              else if (childDepth < parentDepth)
                findAncestor(initialFiberValue)(parentAncestors, parentDepth - 1, childStack, childDepth)
              else
                findAncestor(initialFiberValue)(parentAncestors, parentDepth - 1, childAncestors, childDepth - 1)
          }
      }

    val parentFiberRefs = self.fiberRefLocals
    val childFiberRefs  = that.fiberRefLocals

    val newFiberRefLocals = childFiberRefs.foldLeft(parentFiberRefs) { case (parentFiberRefs, (ref, childStack)) =>
      if (childStack.fiberId == fiberId) parentFiberRefs
      else {
        val childFiberRef          = ref.asInstanceOf[FiberRef[Any]]
        val childFiberInitialValue = childFiberRef.initial
        val childStackCurrentValue = childStack.value

        parentFiberRefs.getOrElse(childFiberRef, null) match {
          case null =>
            val emptyChildStack = childStackCurrentValue == childFiberInitialValue
            if (emptyChildStack) parentFiberRefs
            else
              parentFiberRefs.updated(
                childFiberRef,
                FiberRefStack.init(fiberId, childFiberRef.join(childFiberInitialValue, childStackCurrentValue))
              )
          case parentStack =>
            val ancestor =
              findAncestor(childFiberInitialValue)(
                parentStack.stack(),
                parentStack.depth,
                childStack.stack(),
                childStack.depth
              )
            val patch    = childFiberRef.diff(ancestor, childStackCurrentValue)
            val oldValue = parentStack.value
            val newValue = childFiberRef.join(oldValue, childFiberRef.patch(patch)(oldValue))

            if (oldValue == newValue) parentFiberRefs
            else {
              val newStack =
                if (parentStack.fiberId == fiberId) parentStack.updateValue(newValue)
                else parentStack.put(fiberId, newValue)

              parentFiberRefs.updated(childFiberRef, newStack)
            }
        }
      }
    }

    if (self.fiberRefLocals eq newFiberRefLocals) self
    else FiberRefs(newFiberRefLocals)
  }

  def setAll(implicit trace: Trace): UIO[Unit] =
    ZIO.foreachDiscard(fiberRefs) { fiberRef =>
      fiberRef.asInstanceOf[FiberRef[Any]].set(getOrDefault(fiberRef))
    }

  override def toString: String = fiberRefLocals.mkString("FiberRefLocals(", ",", ")")

  def updatedAs[@specialized(SpecializeInt) A](
    fiberId: FiberId.Runtime
  )(fiberRef: FiberRef[A], value: A): FiberRefs = {
    val oldStack = fiberRefLocals.getOrElse(fiberRef, null).asInstanceOf[FiberRefStack[A]]

    val newStack =
      if (oldStack eq null) FiberRefStack.init(fiberId, value)
      else {
        if (oldStack.fiberId == fiberId) {
          if (oldStack.value == value) oldStack else oldStack.updateValue(value)
        } else if (oldStack.value == value) oldStack
        else oldStack.put(fiberId, value)
      }

    if (oldStack eq newStack) self
    else FiberRefs(fiberRefLocals.updated(fiberRef, newStack))
  }

}

object FiberRefs {
  private[FiberRefs] final case class FiberRefStackEntry[@specialized(SpecializeInt) A](
    fiberId: FiberId.Runtime,
    value: A,
    version: Int
  )

  /**
   * The 'head' of the stack is inlined in the `FiberRefStack` data structure:
   *
   * Instead of having
   * {{{
   *   FiberRefStack(
   *     head: FiberRefStackEntry[?],
   *     tail: List[FiberRefStackEntry[?],
   *     depth: Int
   *   )
   * }}}
   * we inline the content of the `head: FiberRefStackEntry` data structure
   * directly into the `FiberRefStack` to avoid to have to instantiate this
   * `FiberRefStackEntry` as much as possible.
   */
  private[zio] final case class FiberRefStack[@specialized(SpecializeInt) A] private[FiberRefs] (
    headFiberId: FiberId.Runtime, // should be `private val` but https://github.com/scala/bug/issues/12988. Prefer usage of `#fiberId` when outside of the `FiberRefStack`
    headValue: A,                 // should be `private val` but https://github.com/scala/bug/issues/12988. Prefer usage of `#value` when version of the `FiberRefStack`
    headVersion: Int,             // should be `private val` but https://github.com/scala/bug/issues/12988. Prefer usage of `#version` when version of the `FiberRefStack`
    tail: List[FiberRefStackEntry[A]],
    depth: Int
  ) {
    // nicer names for these variables when used outside of the FiberRefStack
    @inline def fiberId: FiberId.Runtime = headFiberId
    @inline def value: A                 = headValue
    @inline def version: Int             = headVersion

    /**
     * Added `()` to the signature to indicate that it's not a delegating
     * function (unlike [[fiberId]] for example). It does some allocations
     * therefor it must be used with care.
     */
    @inline def stack(): ::[FiberRefStackEntry[A]] = ::(FiberRefStackEntry(headFiberId, headValue, headVersion), tail)

    /**
     * Update the value of the head entry
     */
    @inline def updateValue(newValue: A): FiberRefStack[A] =
      this.copy(headValue = newValue, headVersion = headVersion + 1)

    /**
     * Add a new entry on top of the Stack
     */
    @inline def put(fiberId: FiberId.Runtime, value: A): FiberRefStack[A] =
      FiberRefStack(
        headFiberId = fiberId,
        headValue = value,
        headVersion = 0,
        tail = stack(),
        depth = depth + 1
      )
  }
  private[zio] object FiberRefStack {
    @inline def init[@specialized(SpecializeInt) A](fiberId: FiberId.Runtime, value: A): FiberRefStack[A] =
      FiberRefStack(headFiberId = fiberId, headValue = value, headVersion = 0, tail = List.empty, depth = 1)
  }

  /**
   * The empty collection of `FiberRef` values.
   */
  val empty: FiberRefs =
    FiberRefs(Map.empty)

  private[zio] def apply(fiberRefLocals: Map[FiberRef[_], FiberRefStack[_]]): FiberRefs =
    new FiberRefs(fiberRefLocals)

  /**
   * A `Patch` captures the changes in `FiberRef` values made by a single fiber
   * as a value. This allows fibers to apply the changes made by a workflow
   * without inheriting all the `FiberRef` values of the fiber that executed the
   * workflow.
   */
  sealed trait Patch extends Function2[FiberId.Runtime, FiberRefs, FiberRefs] { self =>
    import Patch._

    /**
     * Applies the changes described by this patch to the specified collection
     * of `FiberRef` values.
     */
    final def apply(fiberId: FiberId.Runtime, fiberRefs: FiberRefs): FiberRefs = {

      @tailrec
      def loop(fiberRefs: FiberRefs, patches: List[Patch]): FiberRefs =
        patches match {
          case Add(fiberRef, value) :: patches =>
            loop(fiberRefs.updatedAs(fiberId)(fiberRef, value), patches)
          case AndThen(first, second) :: patches =>
            loop(fiberRefs, first :: second :: patches)
          case Empty :: patches =>
            loop(fiberRefs, patches)
          case Remove(fiberRef) :: patches =>
            loop(fiberRefs.delete(fiberRef), patches)
          case Update(fiberRef, patch) :: patches =>
            loop(
              fiberRefs.updatedAs(fiberId)(fiberRef, fiberRef.patch(patch)(fiberRefs.getOrDefault(fiberRef))),
              patches
            )
          case Nil =>
            fiberRefs
        }

      loop(fiberRefs, List(self))
    }

    /**
     * Combines this patch and the specified patch to create a new patch that
     * describes applying the changes from this patch and the specified patch
     * sequentially.
     */
    final def combine(that: Patch): Patch =
      AndThen(self, that)
  }

  object Patch {

    /**
     * The empty patch that describes no changes to `FiberRef` values.
     */
    val empty: Patch =
      Patch.Empty

    /**
     * Constructs a patch that describes the changes between the specified
     * collections of `FiberRef`
     */
    def diff(oldValue: FiberRefs, newValue: FiberRefs): Patch = {
      val (removed, patch) = newValue.fiberRefLocals.foldLeft[(FiberRefs, Patch)](oldValue -> empty) {
        case ((fiberRefs, patch), (fiberRef, fiberRefStack)) =>
          type V = fiberRef.Value
          val newValue0 = fiberRefStack.value.asInstanceOf[V]

          fiberRefs.getOrNull(fiberRef) match {
            case null =>
              fiberRefs -> patch.combine(Add(fiberRef.asInstanceOf[FiberRef[V]], newValue.asInstanceOf[V]))
            case oldValue0 =>
              val patch0 =
                if (oldValue0 == newValue0) patch
                else {
                  patch.combine(
                    Update(
                      fiberRef.asInstanceOf[FiberRef.WithPatch[V, fiberRef.Patch]],
                      fiberRef.diff(oldValue0.asInstanceOf[V], newValue0)
                    )
                  )
                }

              fiberRefs.delete(fiberRef) -> patch0
          }
      }
      removed.fiberRefLocals.foldLeft(patch) { case (patch, (fiberRef, _)) => patch.combine(Remove(fiberRef)) }
    }

    private final case class Add[Value0](fiberRef: FiberRef[Value0], value: Value0) extends Patch
    private final case class AndThen(first: Patch, second: Patch)                   extends Patch
    private case object Empty                                                       extends Patch
    private final case class Remove[Value0](fiberRef: FiberRef[Value0])             extends Patch
    private final case class Update[Value0, Patch0](fiberRef: FiberRef.WithPatch[Value0, Patch0], patch: Patch0)
        extends Patch
  }
}
