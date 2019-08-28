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

import zio.{ URIO, ZIO }
import zio.duration.Duration

/**
 * A `TestReporter[L]` is capable of reporting test results annotated with
 * labels `L`.
 */
trait TestReporter[-L] {
  def apply[E, S](duration: Duration, executedSpec: ExecutedSpec[L, E, S]): URIO[TestLogger, Unit]
}

object TestReporter {

  /**
   * TestReporter that does nothing
   */
  def silent[L]: TestReporter[L] = new TestReporter[L] {
    def apply[E, S](duration: Duration, executedSpec: ExecutedSpec[L, E, S]): URIO[TestLogger, Unit] =
      ZIO.succeed(())
  }
}
