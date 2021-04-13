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

import scala.scalajs.js
import scala.scalajs.js.{Promise => JSPromise}

private[zio] trait TaskPlatformSpecific { self: Task.type =>

  /**
   * Imports a Scala.js promise into a `Task`.
   */
  def fromPromiseJS[A](promise: => JSPromise[A]): Task[A] =
    self.effectAsync { callback =>
      promise.`then`[Unit](
        a => callback(UIO.succeedNow(a)),
        js.defined { (e: Any) =>
          callback(IO.fail(e match {
            case t: Throwable => t
            case _            => js.JavaScriptException(e)
          }))
        }
      )
    }
}
