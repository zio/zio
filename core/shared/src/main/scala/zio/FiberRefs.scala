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

import zio.internal.SpecializationHelpers.SpecializeInt
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.tailrec

/**
 * `FiberRefs` is a data type that represents a collection of `FiberRef` values.
 * This allows safely propagating `FiberRef` values across fiber boundaries, for
 * example between an asynchronous producer and consumer.
 */
final class FiberRefs private (
  private[zio] val fiberRefLocals: Map[FiberRef[_], FiberRefs.Value]
) { self =>
  import zio.FiberRefs.{StackEntry, Value}

  /**
   * Returns a new fiber refs with the specified ref deleted from it.
   */
  def delete(fiberRef: FiberRef[_]): FiberRefs =
    if (!fiberRefLocals.contains(fiberRef)) self
    else FiberRefs(fiberRefLocals - fiberRef)

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
  @volatile private var needsTransformWhenForked: Boolean = true

  /**
   * Forks this collection of fiber refs as the specified child fiber id. This
   * will potentially modify the value of the fiber refs, as determined by the
   * individual fiber refs that make up the collection.
   */
  def forkAs(childId: FiberId.Runtime): FiberRefs =
    if (needsTransformWhenForked) {
      val childFiberRefLocals =
        fiberRefLocals.transform { case (fiberRef, entry @ Value(stack, depth)) =>
          val oldValue = stack.head.value.asInstanceOf[fiberRef.Value]
          val newValue = fiberRef.patch(fiberRef.fork)(oldValue)
          if (oldValue == newValue) entry
          else Value(::(StackEntry(childId, newValue, 0), stack), depth + 1)
        }
      if (fiberRefLocals eq childFiberRefLocals) {
        needsTransformWhenForked = false
        self
      } else {
        FiberRefs(childFiberRefLocals)
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
    if (out eq null) null else out.stack.head.value
  }.asInstanceOf[A]

  /**
   * Joins this collection of fiber refs to the specified collection, as the
   * specified fiber id. This will perform diffing and merging to ensure
   * preservation of maximum information from both child and parent refs.
   */
  def joinAs(fiberId: FiberId.Runtime)(that: FiberRefs): FiberRefs = {
    val parentFiberRefs = self.fiberRefLocals
    val childFiberRefs  = that.fiberRefLocals

    val fiberRefLocals0 = childFiberRefs.foldLeft(parentFiberRefs) {
      case (parentFiberRefs, (fiberRef, Value(childStack, childDepth))) =>
        val ref        = fiberRef.asInstanceOf[FiberRef[Any]]
        val childValue = childStack.head.value

        if (childStack.head.id == fiberId) {
          parentFiberRefs
        } else {

          parentFiberRefs
            .get(ref)
            .fold {
              if (childValue == ref.initial) parentFiberRefs
              else
                parentFiberRefs.updated(
                  ref,
                  Value(::(StackEntry(fiberId, ref.join(ref.initial, childValue), 0), List.empty), 1)
                )
            } { case Value(parentStack, parentDepth) =>
              @tailrec
              def findAncestor(
                parentStack: List[StackEntry[?]],
                parenthDepth: Int,
                childStack: List[StackEntry[?]],
                childDepth: Int
              ): Any =
                (parentStack, childStack) match {
                  case (
                        StackEntry(parentFiberId, parentValue, parentVersion) :: parentAncestors,
                        StackEntry(childFiberId, childValue, childVersion) :: childAncestors
                      ) =>
                    if (parentFiberId == childFiberId)
                      if (childVersion > parentVersion) parentValue else childValue
                    else if (childDepth > parentDepth)
                      findAncestor(parentStack, parentDepth, childAncestors, childDepth - 1)
                    else if (childDepth < parentDepth)
                      findAncestor(parentAncestors, parentDepth - 1, childStack, childDepth)
                    else
                      findAncestor(parentAncestors, parentDepth - 1, childAncestors, childDepth - 1)
                  case _ =>
                    (ref.initial)
                }

              val ancestor = findAncestor(parentStack, parentDepth, childStack, childDepth)

              val patch = ref.diff(ancestor, childValue)

              val oldValue = parentStack.head.value
              val newValue = ref.join(oldValue, ref.patch(patch)(oldValue))

              if (oldValue == newValue) parentFiberRefs
              else {
                val newEntry = parentStack match {
                  case StackEntry(parentFiberId, _, parentVersion) :: tail =>
                    if (parentFiberId == fiberId)
                      Value(::(StackEntry(parentFiberId, newValue, parentVersion + 1), tail), parentDepth)
                    else
                      Value(::(StackEntry(fiberId, newValue, 0), parentStack), parentDepth + 1)
                }
                parentFiberRefs.updated(ref, newEntry)
              }
            }
        }
    }

    if (self.fiberRefLocals eq fiberRefLocals0) self
    else FiberRefs(fiberRefLocals0)
  }

  def setAll(implicit trace: Trace): UIO[Unit] =
    ZIO.foreachDiscard(fiberRefs) { fiberRef =>
      fiberRef.asInstanceOf[FiberRef[Any]].set(getOrDefault(fiberRef))
    }

  override final def toString(): String = fiberRefLocals.mkString("FiberRefLocals(", ",", ")")

  def updatedAs[@specialized(SpecializeInt) A](
    fiberId: FiberId.Runtime
  )(fiberRef: FiberRef[A], value: A): FiberRefs = {
    val oldEntry = fiberRefLocals.getOrElse(fiberRef, null)

    val oldStack = if (oldEntry ne null) oldEntry.stack.asInstanceOf[::[StackEntry[A]]] else null
    val oldDepth = if (oldEntry ne null) oldEntry.depth else 0
    val newEntry =
      if (oldEntry eq null) Value(::(StackEntry(fiberId, value, 0), List.empty), 1)
      else if (oldStack.head.id == fiberId) {
        val oldValue = oldStack.head.value
        if (oldValue == value) oldEntry
        else
          Value(
            ::(StackEntry(fiberId, value, oldStack.head.version + 1), oldStack.tail),
            oldEntry.depth
          )
      } else if (oldStack.head.value == value)
        oldEntry
      else
        Value(::(StackEntry(fiberId, value, 0), oldStack), oldDepth + 1)

    if (oldEntry eq newEntry) self
    else FiberRefs(fiberRefLocals.updated(fiberRef, newEntry))
  }

}

object FiberRefs {
  private[zio] case class StackEntry[@specialized(SpecializeInt) A](
    id: FiberId.Runtime,
    value: A,
    version: Int
  )
  private[zio] case class Value(stack: ::[StackEntry[?]], depth: Int)

  /**
   * The empty collection of `FiberRef` values.
   */
  val empty: FiberRefs =
    FiberRefs(Map.empty)

  private[zio] def apply(fiberRefLocals: Map[FiberRef[_], Value]): FiberRefs =
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
    def apply(fiberId: FiberId.Runtime, fiberRefs: FiberRefs): FiberRefs = {

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
    def combine(that: Patch): Patch =
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
        case ((fiberRefs, patch), (fiberRef, Value(StackEntry(_, newValue, _) :: _, _))) =>
          fiberRefs.getOrNull(fiberRef) match {
            case null =>
              fiberRefs.delete(fiberRef) -> patch.combine(
                Add(fiberRef.asInstanceOf[FiberRef[fiberRef.Value]], newValue.asInstanceOf[fiberRef.Value])
              )
            case oldValue =>
              if (oldValue == newValue)
                fiberRefs.delete(fiberRef) -> patch
              else {
                fiberRefs.delete(fiberRef) -> patch.combine(
                  Update(
                    fiberRef.asInstanceOf[FiberRef.WithPatch[fiberRef.Value, fiberRef.Patch]],
                    fiberRef.diff(oldValue.asInstanceOf[fiberRef.Value], newValue.asInstanceOf[fiberRef.Value])
                  )
                )
              }
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
