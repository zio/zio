/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

object Queue {

  /**
   * @see See [[zio.ZQueue.bounded]]
   */
  def bounded[A](requestedCapacity: Int): UIO[Queue[A]] = ZQueue.bounded(requestedCapacity)

  /**
   * @see See [[zio.ZQueue.dropping]]
   */
  def dropping[A](requestedCapacity: Int): UIO[Queue[A]] = ZQueue.dropping(requestedCapacity)

  /**
   * @see See [[zio.ZQueue.sliding]]
   */
  def sliding[A](requestedCapacity: Int): UIO[Queue[A]] = ZQueue.sliding(requestedCapacity)

  /**
   * @see See [[zio.ZQueue.unbounded]]
   */
  def unbounded[A]: UIO[Queue[A]] = ZQueue.unbounded

}
