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

import zio.stm.ZSTM

package object internal {

  /**
   * Returns an effect that models success with the specified value.
   */
  def ZIOSucceedNow[A](a: A): UIO[A] =
    ZIO.succeedNow(a)

  /**
   * Lifts an eager, pure value into a Managed.
   */
  def ZManagedSucceedNow[A](r: A): ZManaged[Any, Nothing, A] =
    ZManaged.succeedNow(r)

  /**
   * Returns an `STM` effect that succeeds with the specified value.
   */
  def ZSTMSucceedNow[A](a: A): ZSTM[Any, Nothing, A] =
    ZSTM.succeedNow(a)
}
