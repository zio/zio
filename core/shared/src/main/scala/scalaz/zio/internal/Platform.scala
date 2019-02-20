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

package scalaz.zio.internal

import java.util.{ Map => JMap }

import scalaz.zio.Exit.Cause

/**
 * A `Platform` provides the minimum capabilities necessary to bootstrap
 * execution of `ZIO` tasks.
 */
trait Platform { self =>

  /**
   * Retrieves the default executor.
   */
  def executor: Executor

  def withExecutor(e: Executor): Platform =
    new Platform.Proxy(self) {
      override def executor = e
    }

  /**
   * Determines if a throwable is non-fatal or not.
   */
  def nonFatal(t: Throwable): Boolean

  def withNonFatal(f: Throwable => Boolean): Platform =
    new Platform.Proxy(self) {
      override def nonFatal(t: Throwable): Boolean = f(t)
    }

  /**
   * Reports the specified failure.
   */
  def reportFailure(cause: Cause[_]): Unit

  def withReportFailure(f: Cause[_] => Unit): Platform =
    new Platform.Proxy(self) {
      override def reportFailure(cause: Cause[_]): Unit = f(cause)
    }

  /**
   * Create a new java.util.WeakHashMap if supported by the platform,
   * otherwise any implementation of Map.
   */
  def newWeakHashMap[A, B](): JMap[A, B]
}
object Platform {
  class Proxy(self: Platform) extends Platform {
    def executor: Executor                   = self.executor
    def nonFatal(t: Throwable): Boolean      = self.nonFatal(t)
    def reportFailure(cause: Cause[_]): Unit = self.reportFailure(cause)
    def newWeakHashMap[A, B](): JMap[A, B]   = self.newWeakHashMap[A, B]()
  }
}
