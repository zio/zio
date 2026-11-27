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

package zio.test

import zio.{UIO, Trace}
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A test service whose state can be saved and later restored. `TestClock`,
 * `TestConsole`, `TestRandom` and `TestSystem` are restorable, and
 * `TestAspect.restore` uses `save` to put a service back in its starting state
 * after each run of a test.
 *
 * @see
 *   [[https://zio.dev/reference/test/aspects/restoring-state-of-test-services/ Restoring State of Test Services]]
 */
trait Restorable extends Serializable {

  /**
   * Saves the current state of the service and returns an effect that, when
   * run, restores the service to that state.
   */
  def save(implicit trace: Trace): UIO[UIO[Unit]]
}
