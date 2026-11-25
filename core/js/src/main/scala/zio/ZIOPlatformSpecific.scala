/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

import scala.scalajs.js
import scala.scalajs.js.{Function1, Promise => JSPromise, Thenable, |}

private[zio] trait ZIOPlatformSpecific[-R, +E, +A] { self: ZIO[R, E, A] =>

  /**
   * Converts the current `ZIO` to a Scala.js promise. The returned effect runs
   * this effect to the end and then succeeds with a promise that is already
   * resolved with its value, or rejected with `squash` of its cause.
   */
  def toPromiseJS(implicit ev: E IsSubtypeOfError Throwable, trace: Trace): URIO[R, JSPromise[A]] =
    toPromiseJSWith(ev)

  /**
   * Converts the current `ZIO` to a Scala.js promise and maps the error type
   * with `f`. Like `toPromiseJS`, it runs this effect to the end before it
   * returns the settled promise.
   */
  def toPromiseJSWith(f: E => Throwable)(implicit trace: Trace): URIO[R, JSPromise[A]] =
    self.foldCause(c => JSPromise.reject(c.squashWith(f)), JSPromise.resolve[A](_))
}

private[zio] trait ZIOCompanionPlatformSpecific { self: ZIO.type =>

  /**
   * Imports a synchronous effect that does blocking IO into a pure value.
   *
   * If the returned `ZIO` is interrupted, the blocked thread running the
   * synchronous effect will be interrupted via `Thread.interrupt`.
   *
   * Note that this adds significant overhead. For performance sensitive
   * applications consider using `attemptBlocking` or
   * `attemptBlockingCancelable`.
   *
   * On Scala.js it is the same as `attemptBlocking`, and no thread is
   * interrupted.
   */
  def attemptBlockingInterrupt[A](effect: => A)(implicit trace: Trace): Task[A] =
    ZIO.attemptBlocking(effect)

  /**
   * Imports a Scala.js promise into a `ZIO`. The effect succeeds with the
   * promise's value; a rejection with a `Throwable` fails with it, and a
   * rejection with any other value fails with a `js.JavaScriptException` that
   * wraps the value.
   */
  def fromPromiseJS[A](promise: => JSPromise[A])(implicit trace: Trace): Task[A] =
    self.async { callback =>
      val onFulfilled: Function1[A, Unit | Thenable[Unit]] = new scala.Function1[A, Unit | Thenable[Unit]] {
        def apply(a: A): Unit | Thenable[Unit] = callback(ZIO.succeed(a))
      }
      val onRejected: Function1[Any, Unit | Thenable[Unit]] = new scala.Function1[Any, Unit | Thenable[Unit]] {
        def apply(e: Any): Unit | Thenable[Unit] =
          callback(ZIO.fail(e match {
            case t: Throwable => t
            case _            => js.JavaScriptException(e)
          }))
      }
      promise.`then`[Unit](onFulfilled, js.defined(onRejected))
    }

  /**
   * Writes `content` to the file at the given path, on the blocking executor,
   * replacing what the file held and creating it when it does not exist. On
   * the JVM and Scala Native it encodes the text with the platform's default
   * charset and fails with an `IOException` when the file cannot be written.
   * On Scala.js it needs Node.js: it writes the file with `fs.writeFileSync`
   * as UTF-8, and fails with the error that Node.js throws.
   */
  def writeFile(file: => String, content: => String)(implicit trace: Trace): ZIO[Any, Throwable, Unit] = {
    import scalajs.js.Dynamic.{global => g}
    val fs = g.require("fs")
    ZIO.attemptBlocking(fs.writeFileSync(file, content))
  }

  /**
   * Reads the whole file at the given path into a `String`, on the blocking
   * executor. On the JVM and Scala Native it decodes the bytes with the
   * platform's default charset and fails with an `IOException` when the file
   * cannot be read. On Scala.js it needs Node.js: it reads the file with
   * `fs.readFileSync`, decodes it as UTF-8, and fails with the error that
   * Node.js throws.
   */
  def readFile(file: => String)(implicit trace: Trace): ZIO[Any, Throwable, String] = {
    import scalajs.js.Dynamic.{global => g}
    val fs = g.require("fs")
    ZIO.attemptBlocking(fs.readFileSync(file).toString)
  }

}
