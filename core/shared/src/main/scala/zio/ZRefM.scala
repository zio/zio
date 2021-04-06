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

object ZRefM {

  /**
   * Creates a new `RefM` and a `Dequeue` that will emit every change to the
   * `RefM`.
   */
  def dequeueRef[A](a: A): UIO[(RefM[A], Dequeue[A])] =
    ZRef.ZRefM.dequeueRef(a)

  /**
   * Creates a new `ZRefM` with the specified value.
   */
  def make[A](a: A): UIO[RefM[A]] =
    ZRef.ZRefM.make(a)

  /**
   * Creates a new `ZRefM` with the specified value in the context of a
   * `Managed.`
   */
  def makeManaged[A](a: A): UManaged[RefM[A]] =
    ZRef.ZRefM.makeManaged(a)
}
