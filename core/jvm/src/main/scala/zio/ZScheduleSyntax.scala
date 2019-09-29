/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

final class ZScheduleSyntax[A, B](private val sched: ZSchedule[DefaultRuntime#Environment, A, B]) extends AnyVal {
  type Env = DefaultRuntime#Environment

  /**
   * Applies random jitter in the range (0, 1) to all sleeps executed by the schedule.
   */
  final def jittered: ZSchedule[Env, A, B] =
    jittered(0.0, 1.0)

  /**
   * Applies random jitter to all sleeps executed by the schedule.
   */
  final def jittered(min: Double, max: Double): ZSchedule[Env, A, B] =
    sched.jittered_[Env](min, max) { (old, clock0) =>
      new zio.clock.Clock with zio.console.Console with zio.system.System with zio.random.Random
      with blocking.Blocking {
        val clock    = clock0.clock
        val console  = old.console
        val system   = old.system
        val random   = old.random
        val blocking = old.blocking
      }
    }
}
