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
  private[zio] val fiberRefLocals: Map[FiberRef[_], FiberRefs.FiberRefStack]
) { self =>
  import zio.FiberRefs.{FiberRefStack, FiberRefStackEntry}

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

    @tailrec
    def findAncestor(initialValue: Any)(
      parentStack: List[FiberRefStackEntry[?]],
      parentDepth: Int,
      childStack: List[FiberRefStackEntry[?]],
      childDepth: Int
    ): Any =
      parentStack match {
        case Nil => initialValue // case (Nil, _)
        case FiberRefStackEntry(parentFiberId, parentValue, parentVersion) :: parentAncestors =>
          childStack match {
            case Nil => initialValue // case (_, Nil)
            case FiberRefStackEntry(
                  childFiberId,
                  childValue,
                  childVersion
                ) :: childAncestors => // case (head0 :: tail0, head1 :: tail1)
              if (parentFiberId == childFiberId)
                if (childVersion > parentVersion) parentValue else childValue
              else if (childDepth > parentDepth)
                findAncestor(initialValue)(parentStack, parentDepth, childAncestors, childDepth - 1)
              else if (childDepth < parentDepth)
                findAncestor(initialValue)(parentAncestors, parentDepth - 1, childStack, childDepth)
              else
                findAncestor(initialValue)(parentAncestors, parentDepth - 1, childAncestors, childDepth - 1)
          }
      }

    val fiberRefLocals0 = childFiberRefs.foldLeft(parentFiberRefs) {
      case (parentFiberRefs, (childFiberRef, childStack)) =>
        if (childStack.id == fiberId) parentFiberRefs
        else {
          val ref               = childFiberRef.asInstanceOf[FiberRef[Any]]
          val childInitialValue = ref.initial
          val childValue        = childStack.value

          parentFiberRefs.getOrElse(ref, null) match {
            case null =>
              if (childValue == childInitialValue) parentFiberRefs
              else
                parentFiberRefs.updated(
                  ref,
                  FiberRefStack.init(fiberId, ref.join(childInitialValue, childValue))
                )
            case parentStack =>
              val ancestor =
                findAncestor(childInitialValue)(
                  parentStack.stack,
                  parentStack.depth,
                  childStack.stack,
                  childStack.depth
                )
              val patch    = ref.diff(ancestor, childValue)
              val oldValue = parentStack.value
              val newValue = ref.join(oldValue, ref.patch(patch)(oldValue))

              if (oldValue == newValue) parentFiberRefs
              else {
                val newEntry =
                  if (parentStack.id == fiberId) parentStack.updateValue(newValue)
                  else parentStack.put(fiberId, newValue)

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
    val oldStack = fiberRefLocals.getOrElse(fiberRef, null)

    val newStack =
      if (oldStack eq null) FiberRefStack.init(fiberId, value)
      else {
        if (oldStack.id == fiberId) {
          if (oldStack.value == value) oldStack else oldStack.updateValue(value)
        } else if (oldStack.value == value) oldStack
        else oldStack.put(fiberId, value)
      }

    if (oldStack eq newStack) self
    else FiberRefs(fiberRefLocals.updated(fiberRef, newStack))
  }

}

object FiberRefs {
  private[zio] final case class FiberRefStackEntry[@specialized(SpecializeInt) A] private[FiberRefs] (
    id: FiberId.Runtime,
    value: A,
    version: Int
  )
  private[zio] object FiberRefStackEntry {
    @inline def make[A](id: FiberId.Runtime, value: A): FiberRefStackEntry[A] = FiberRefStackEntry(id, value, 0)
  }

  private[zio] final case class FiberRefStack private (stack: ::[FiberRefStackEntry[?]], depth: Int) {
    @inline private def head: FiberRefStackEntry[?]       = stack.head
    @inline private def tail: List[FiberRefStackEntry[?]] = stack.tail

    @inline def id: FiberId.Runtime = stack.head.id
    @inline def value: Any          = stack.head.value
    @inline def version: Int        = stack.head.version

    /**
     * Update the value of the head entry
     *
     * @param value
     * @return
     */
    @inline def updateValue(value: Any): FiberRefStack =
      FiberRefStack(
        head = head.copy(value = value, version = version + 1),
        tail = tail,
        depth = depth
      )

    /**
     * Add a new entry on top of the Stack
     *
     * @param fiberId
     * @param value
     * @return
     */
    @inline def put(fiberId: FiberId.Runtime, value: Any): FiberRefStack =
      FiberRefStack(
        head = FiberRefStackEntry.make(fiberId, value),
        tail = stack,
        depth = depth + 1
      )
  }
  private[zio] object FiberRefStack {
    @inline def init[A](id: FiberId.Runtime, value: A): FiberRefStack =
      FiberRefStack(::(FiberRefStackEntry.make(id, value), List.empty), 1)

    @inline def apply(head: FiberRefStackEntry[?], tail: List[FiberRefStackEntry[?]], depth: Int): FiberRefStack =
      FiberRefStack(::(head, tail), depth)
  }

  /**
   * The empty collection of `FiberRef` values.
   */
  val empty: FiberRefs =
    FiberRefs(Map.empty)

  private[zio] def apply(fiberRefLocals: Map[FiberRef[_], FiberRefStack]): FiberRefs =
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
        case ((fiberRefs, patch), (fiberRef, fiberRefStack)) =>
          type V = fiberRef.Value
          val newValue0 = fiberRefStack.value.asInstanceOf[V]

          fiberRefs.getOrNull(fiberRef) match {
            case null =>
              fiberRefs -> patch.combine(Add(fiberRef, newValue0))
            case oldValue =>
              val patch0 =
                if (oldValue == newValue0) patch
                else {
                  patch.combine(
                    Update(
                      fiberRef.asInstanceOf[FiberRef.WithPatch[V, fiberRef.Patch]],
                      fiberRef.diff(oldValue.asInstanceOf[V], newValue0)
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
