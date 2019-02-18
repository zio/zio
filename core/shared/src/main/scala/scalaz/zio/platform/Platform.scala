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

package scalaz.zio.platform

import java.util.{ Map => JMap }

import scalaz.zio.Exit.Cause
import scalaz.zio.internal.Executor

/**
 * A `Platform` provides the minimum capabilities necessary to bootstrap
 * execution of `ZIO` tasks.
 */
trait Platform {
  val platform: Platform.Service
}
object Platform {
  trait Service {

    /**
     * Retrieves the default executor.
     */
    def executor: Executor

    /**
     * Determines if a throwable is non-fatal or not.
     */
    def nonFatal(t: Throwable): Boolean

    /**
     * Reports the specified failure.
     */
    def reportFailure(cause: Cause[_]): Unit

    /**
     * Create a new java.util.WeakHashMap if supported by the platform,
     * otherwise any implementation of Map.
     */
    def newWeakHashMap[A, B](): JMap[A, B]
  }

  def apply(service: Platform.Service): Platform = new Platform {
    val platform = service
  }
}
