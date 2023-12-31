/*
 * Copyright 2017-2023 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.tailrec

/**
 * `FiberRefs` is a data type that represents a collection of `FiberRef` values.
 * This allows safely propagating `FiberRef` values across fiber boundaries, for
 * example between an asynchronous producer and consumer.
 */
final class FiberRefs private (
  private[zio] val fiberRefLocals: Map[FiberRef[_], (::[(FiberId.Runtime, Any, Int)], Int)]
) { self =>

  /**
   * Returns a new fiber refs with the specified ref deleted from it.
   */
  def delete(fiberRef: FiberRef[_]): FiberRefs =
    FiberRefs(fiberRefLocals - fiberRef)

  /**
   * Returns a set of each `FiberRef` in this collection.
   */
  def fiberRefs: Set[FiberRef[_]] =
    fiberRefLocals.keySet

  /**
   * Forks this collection of fiber refs as the specified child fiber id. This
   * will potentially modify the value of the fiber refs, as determined by the
   * individual fiber refs that make up the collection.
   */
  def forkAs(childId: FiberId.Runtime): FiberRefs = {
    val childFiberRefLocals: Map[FiberRef[_], (::[(FiberId.Runtime, Any, Int)], Int)] =
      fiberRefLocals.transform { case (fiberRef, (stack, depth)) =>
        val oldValue = stack.head._2.asInstanceOf[fiberRef.Value]
        val newValue = fiberRef.patch(fiberRef.fork)(oldValue)
        if (oldValue == newValue) (stack, depth) else (::((childId, newValue, 0), stack), depth + 1)
      }

    FiberRefs(childFiberRefLocals)
  }

  /**
   * Gets the value of the specified `FiberRef` in this collection of `FiberRef`
   * values if it exists or `None` otherwise.
   */
  def get[A](fiberRef: FiberRef[A]): Option[A] =
    fiberRefLocals.get(fiberRef).map(_._1.head._2.asInstanceOf[A])

  /**
   * Gets the value of the specified `FiberRef` in this collection of `FiberRef`
   * values if it exists or the `initial` value of the `FiberRef` otherwise.
   */
  def getOrDefault[A](fiberRef: FiberRef[A]): A =
    get(fiberRef).getOrElse(fiberRef.initial)

  /**
   * Joins this collection of fiber refs to the specified collection, as the
   * specified fiber id. This will perform diffing and merging to ensure
   * preservation of maximum information from both child and parent refs.
   */
  def joinAs(fiberId: FiberId.Runtime)(that: FiberRefs): FiberRefs = {
    val parentFiberRefs = self.fiberRefLocals
    val childFiberRefs  = that.fiberRefLocals

    val fiberRefLocals = childFiberRefs.foldLeft(parentFiberRefs) {
      case (parentFiberRefs, (fiberRef, (childStack, childDepth))) =>
        val ref        = fiberRef.asInstanceOf[FiberRef[Any]]
        val childValue = childStack.head._2

        if (childStack.head._1 == fiberId) {
          parentFiberRefs
        } else {

          parentFiberRefs
            .get(ref)
            .fold {
              if (childValue == ref.initial) parentFiberRefs
              else parentFiberRefs.updated(ref, (::((fiberId, ref.join(ref.initial, childValue), 0), Nil), 1))
            } { case (parentStack, parentDepth) =>
              @tailrec
              def findAncestor(
                parentStack: List[(FiberId.Runtime, Any, Int)],
                parenthDepth: Int,
                childStack: List[(FiberId.Runtime, Any, Int)],
                childDepth: Int
              ): Any =
                (parentStack, childStack) match {
                  case (
                        (parentFiberId, parentValue, parentVersion) :: parentAncestors,
                        (childFiberId, childValue, childVersion) :: childAncestors
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

              val oldValue = parentStack.head._2
              val newValue = ref.join(oldValue, ref.patch(patch)(oldValue))

              if (oldValue == newValue) parentFiberRefs
              else {
                val (newStack, newDepth) = parentStack match {
                  case (parentFiberId, _, parentVersion) :: tail =>
                    if (parentFiberId == fiberId) (::((parentFiberId, newValue, parentVersion + 1), tail), parentDepth)
                    else (::((fiberId, newValue, 0), parentStack), parentDepth + 1)
                }
                parentFiberRefs.updated(ref, (newStack, newDepth))
              }
            }
        }
    }

    FiberRefs(fiberRefLocals)
  }

  def setAll(implicit trace: Trace): UIO[Unit] =
    ZIO.foreachDiscard(fiberRefs) { fiberRef =>
      fiberRef.asInstanceOf[FiberRef[Any]].set(getOrDefault(fiberRef))
    }

  override final def toString(): String = fiberRefLocals.mkString("FiberRefLocals(", ",", ")")

  def updatedAs[A](fiberId: FiberId.Runtime)(fiberRef: FiberRef[A], value: A): FiberRefs = {
    val (oldStack, oldDepth) = fiberRefLocals.get(fiberRef).getOrElse((List.empty, 0))
    val (newStack, newDepth) =
      if (oldStack.isEmpty) (::((fiberId, value.asInstanceOf[Any], 0), Nil), 1)
      else if (oldStack.head._1 == fiberId)
        (::((fiberId, value.asInstanceOf[Any], oldStack.head._3 + 1), oldStack.tail), oldDepth)
      else if (oldStack.head._2 == value) (::(oldStack.head, oldStack.tail), oldDepth)
      else (::((fiberId, value, 0), oldStack), oldDepth + 1)

    FiberRefs(fiberRefLocals.updated(fiberRef, (newStack, newDepth)))
  }
}

object FiberRefs {

  /**
   * The empty collection of `FiberRef` values.
   */
  val empty: FiberRefs =
    FiberRefs(Map.empty)

  private[zio] def apply(fiberRefLocals: Map[FiberRef[_], (::[(FiberId.Runtime, Any, Int)], Int)]): FiberRefs =
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
        case ((fiberRefs, patch), (fiberRef, ((_, newValue, _) :: _, _))) =>
          fiberRefs.get(fiberRef) match {
            case Some(oldValue) =>
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
            case _ =>
              fiberRefs.delete(fiberRef) -> patch.combine(
                Add(fiberRef.asInstanceOf[FiberRef[fiberRef.Value]], newValue.asInstanceOf[fiberRef.Value])
              )
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
