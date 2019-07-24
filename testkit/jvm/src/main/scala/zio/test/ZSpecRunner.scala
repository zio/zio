/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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

package zio.test

import zio.UIO

trait ZSpecRunner {
  def apply[R, E, L](spec: ZSpec[R, E, L]): UIO[ZSpec[R, E, (L, TestResult)]]
}

object ZSpecRunner {

  /**
   * Runs tests in parallel, up to the specified limit.
   */
  def parallel(n: Int): ZSpecRunner = new ZSpecRunner {
    val _ = n

    def apply[R, E, L](spec: ZSpec[R, E, L]): UIO[ZSpec[R, E, (L, TestResult)]] = ???
  }

  /**
   * Runs tests sequentially.
   */
  val sequential: ZSpecRunner = new ZSpecRunner {
    def apply[R, E, L](spec: ZSpec[R, E, L]): UIO[ZSpec[R, E, (L, TestResult)]] = ???
  }
}
