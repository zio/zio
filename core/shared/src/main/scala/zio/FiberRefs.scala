/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

/**
 * `FiberRefs` is a data type that represents a collection of `FiberRef`
 * values. This allows safely propagating `FiberRef` values across fiber
 * boundaries, for example between an asynchronous producer and consumer.
 */
final class FiberRefs private (private val fiberRefLocals: Map[FiberRef.Runtime[_], Any]) { self =>

  /**
   * Sets the value of each `FiberRef` for the fiber running this effect to
   * the value in this collection of `FiberRef` values.
   */
  def setAll: UIO[Unit] =
    ZIO.foreachDiscard(fiberRefLocals) { case (fiberRef, value) =>
      fiberRef.asInstanceOf[FiberRef.Runtime[Any]].set(value)
    }
}

object FiberRefs {

  private[zio] def apply(fiberRefLocals: Map[FiberRef.Runtime[_], Any]): FiberRefs =
    new FiberRefs(fiberRefLocals)
}
