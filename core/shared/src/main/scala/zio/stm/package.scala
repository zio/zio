/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

package object stm extends EitherCompat {

  /**
   * A transaction that needs an environment `R` and can fail with a
   * `Throwable`.
   */
  type RSTM[-R, +A] = ZSTM[R, Throwable, A]

  /**
   * A transaction that needs an environment `R` and cannot fail with a typed
   * error.
   */
  type URSTM[-R, +A] = ZSTM[R, Nothing, A]

  /**
   * A transaction that needs no environment and can fail with an `E`.
   */
  type STM[+E, +A] = ZSTM[Any, E, A]

  /**
   * A transaction that needs no environment and cannot fail with a typed error.
   */
  type USTM[+A] = ZSTM[Any, Nothing, A]

  /**
   * A transaction that needs no environment and can fail with a `Throwable`.
   */
  type TaskSTM[+A] = ZSTM[Any, Throwable, A]
}
