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
final class FiberRefs private (private[zio] val fiberRefLocals: Map[FiberRef[_], ::[(FiberId.Runtime, Any)]]) { self =>

  /**
   * Returns a set of each `FiberRef` in this collection.
   */
  def fiberRefs: Set[FiberRef[_]] =
    fiberRefLocals.keySet

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
   * Sets the value of each `FiberRef` for the fiber running this effect to the
   * value in this collection of `FiberRef` values.
   */
  def setAll(implicit trace: Trace): UIO[Unit] =
    ZIO.foreachDiscard(fiberRefs) { fiberRef =>
      fiberRef.asInstanceOf[FiberRef[Any]].set(getOrDefault(fiberRef))
    }

  private[zio] def join(fiberId: FiberId.Runtime)(that: FiberRefs): FiberRefs = {
    val parentFiberRefs = self.fiberRefLocals
    val childFiberRefs  = that.fiberRefLocals

    val fiberRefLocals = childFiberRefs.foldLeft(parentFiberRefs) { case (parentFiberRefs, (fiberRef, childStack)) =>
      val ref        = fiberRef.asInstanceOf[FiberRef[Any]]
      val childValue = childStack.head._2

      if (childStack.head._1 == fiberId) {
        parentFiberRefs
      } else {

        parentFiberRefs
          .get(ref)
          .fold {
            if (childValue == ref.initial) parentFiberRefs
            else parentFiberRefs + (ref -> ::((fiberId, childValue), Nil))
          } { parentStack =>
            def compareFiberId(left: FiberId.Runtime, right: FiberId.Runtime): Int = {
              val compare = left.startTimeSeconds.compare(right.startTimeSeconds)
              if (compare == 0) left.id.compare(right.id) else compare
            }

            @tailrec
            def findAncestor(
              parentStack: List[(FiberId.Runtime, Any)],
              childStack: List[(FiberId.Runtime, Any)],
              childModified: Boolean = false
            ): (Any, Boolean) =
              (parentStack, childStack) match {
                case ((parentFiberId, _) :: parentAncestors, (childFiberId, childValue) :: childAncestors) =>
                  val compare = compareFiberId(parentFiberId, childFiberId)
                  if (compare < 0) findAncestor(parentStack, childAncestors, true)
                  else if (compare > 0) findAncestor(parentAncestors, childStack, childModified)
                  else (childValue, childModified)
                case _ =>
                  (ref.initial, true)
              }

            val (ancestor, wasModified) = findAncestor(parentStack, childStack)

            if (!wasModified) parentFiberRefs
            else {
              val patch = ref.diff(ancestor, childValue)

              val oldValue = parentStack.head._2
              val newValue = ref.patch(patch)(oldValue)

              if (oldValue == newValue) parentFiberRefs
              else {
                val newStack = parentStack match {
                  case (parentFiberId, _) :: tail =>
                    if (parentFiberId == fiberId) ::((parentFiberId, newValue), tail)
                    else ::((fiberId, newValue), parentStack)
                }
                parentFiberRefs + (ref -> newStack)
              }
            }
          }
      }
    }

    FiberRefs(fiberRefLocals)
  }

  private[zio] def update(fiberId: FiberId.Runtime)(fiberRefs: Map[FiberRef[_], Any]): FiberRefs =
    FiberRefs(
      fiberRefs.foldLeft(fiberRefLocals) { case (fiberRefLocals, (fiberRef, newValue)) =>
        fiberRefLocals.get(fiberRef) match {
          case Some(stack @ ((_, oldValue) :: tail)) if oldValue != newValue =>
            fiberRefLocals + (fiberRef -> ::((fiberId, newValue), stack))
          case None => fiberRefLocals + (fiberRef -> ::((fiberId, newValue), Nil))
          case _    => fiberRefLocals
        }
      }
    )
}

object FiberRefs {

  /**
   * The empty collection of `FiberRef` values.
   */
  val empty: FiberRefs =
    FiberRefs(Map.empty)

  private[zio] def apply(fiberRefLocals: Map[FiberRef[_], ::[(FiberId.Runtime, Any)]]): FiberRefs =
    new FiberRefs(fiberRefLocals)
}
