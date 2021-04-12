/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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

package zio.stm

import zio.UIO

object TRef {

  /**
   * See [[ZTRef.make]].
   */
  def make[A](a: => A): USTM[TRef[A]] =
    ZTRef.make(a)

  /**
   * See [[ZTRef.makeCommit]].
   */
  def makeCommit[A](a: => A): UIO[TRef[A]] =
    ZTRef.makeCommit(a)
}
