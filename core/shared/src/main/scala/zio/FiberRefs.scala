/*
 * Copyright 2017-2022 John A. De Goes and the ZIO Contributors
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
  private[zio] val fiberRefLocals: Map[FiberRef[_], ::[(FiberId.Runtime, Any)]]
) { self =>

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
    val childFiberRefLocals: Map[FiberRef[_], ::[(FiberId.Runtime, Any)]] =
      fiberRefLocals.transform { case (fiberRef, stack) =>
        ::(childId -> fiberRef.patch(fiberRef.fork)(stack.head._2.asInstanceOf[fiberRef.Value]), stack)
      }

    FiberRefs(childFiberRefLocals)
  }

  /**
   * Gets the value of the specified `FiberRef` in this collection of `FiberRef`
   * values if it exists or `None` otherwise.
   */
  def get[A](fiberRef: FiberRef[A]): Option[A] =
    fiberRefLocals.get(fiberRef).map(_.head._2.asInstanceOf[A])

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
  def joinAs(fiberId: FiberId)(that: FiberRefs): FiberRefs = {
    val parentFiberRefs = self.fiberRefLocals
    val childFiberRefs  = that.fiberRefLocals

    val fiberRefLocals = childFiberRefs.foldLeft(parentFiberRefs) { case (parentFiberRefs, (fiberRef, childStack)) =>
      val ref         = fiberRef.asInstanceOf[FiberRef[Any]]
      val parentStack = parentFiberRefs.get(ref).getOrElse(List.empty)

      def combine[A](
        fiberRef: FiberRef[A]
      )(parentStack: List[(FiberId.Runtime, A)], childStack: ::[(FiberId.Runtime, A)]): List[A] = {

        @tailrec
        def loop[A](
          parentStack: List[(FiberId.Runtime, A)],
          childStack: List[(FiberId.Runtime, A)],
          lastParentValue: A,
          lastChildValue: A
        ): List[A] =
          (parentStack, childStack) match {
            case ((parentId, parentValue) :: parentStack, (childId, childValue) :: childStack) =>
              if (parentId == childId)
                loop(parentStack, childStack, parentValue, childValue)
              else if (parentId.id < childId.id)
                lastParentValue :: lastChildValue :: childValue :: childStack.map(_._2)
              else
                lastChildValue :: childValue :: childStack.map(_._2)
            case _ =>
              lastChildValue :: childStack.map(_._2)
          }

        loop(parentStack.reverse, childStack.reverse, fiberRef.initial, fiberRef.initial)
      }

      val values = combine(ref)(parentStack, childStack)

      val patches =
        values.tail
          .foldLeft(values.head -> List.empty[ref.Patch]) { case ((oldValue, patches), newValue) =>
            (newValue, ref.diff(oldValue, newValue) :: patches)
          }
          ._2
          .reverse

      if (patches.isEmpty) parentFiberRefs
      else {
        val patch = patches.reduce(ref.combine)
        val newStack = parentStack match {
          case (fiberId, oldValue) :: tail => Some(::((fiberId, ref.patch(patch)(oldValue)), tail))
          case Nil                         => None
        }

        newStack match {
          case Some(newStack) => parentFiberRefs + (ref -> newStack)
          case None           => parentFiberRefs
        }

      }
    }

    FiberRefs(fiberRefLocals)
  }

  /**
   * Sets the value of each `FiberRef` for the fiber running this effect to the
   * value in this collection of `FiberRef` values.
   */
  def setAll(implicit trace: ZTraceElement): UIO[Unit] =
    ZIO.foreachDiscard(fiberRefs) { fiberRef =>
      fiberRef.asInstanceOf[FiberRef[Any]].set(getOrDefault(fiberRef))
    }

  def updatedAs[A](fiberId: FiberId.Runtime)(fiberRef: FiberRef[A], value: A): FiberRefs = {
    val oldStack = fiberRefLocals.get(fiberRef).getOrElse(List.empty)
    val newStack =
      if (oldStack.isEmpty) ::((fiberId, value.asInstanceOf[Any]), Nil)
      else ::((fiberId, value.asInstanceOf[Any]), oldStack.tail)
    FiberRefs(fiberRefLocals.updated(fiberRef, newStack))
  }
}

object FiberRefs {
  val empty = FiberRefs(Map())

  private[zio] def apply(
    fiberRefLocals: Map[FiberRef[_], ::[(FiberId.Runtime, Any)]]
  ): FiberRefs =
    new FiberRefs(fiberRefLocals)
}
