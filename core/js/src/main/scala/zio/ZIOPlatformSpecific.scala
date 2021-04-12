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

private[zio] trait ZIOPlatformSpecific[-R, +E, +A] { self: ZIO[R, E, A] =>

  /**
   * Converts the current `ZIO` to a Scala.js promise.
   */
  def toPromiseJS(implicit ev: E <:< Throwable): URIO[R, JSPromise[A]] =
    toPromiseJSWith(ev)

  /**
   * Converts the current `ZIO` to a Scala.js promise and maps the
   * error type with `f`.
   */
  def toPromiseJSWith(f: E => Throwable): URIO[R, JSPromise[A]] =
    self.foldCause(c => JSPromise.reject(c.squashWith(f)), JSPromise.resolve[A](_))
}

private[zio] trait ZIOCompanionPlatformSpecific { self: ZIO.type =>

  /**
   * Imports a Scala.js promise into a `ZIO`.
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
