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
import java.nio.channels.CompletionHandler

private[zio] trait TaskPlatformSpecific {


  def asyncWithCompletionHandler[T](op: CompletionHandler[T, Any] => Any)(implicit trace: Trace): Task[T] =
    ZIO.isFatalWith[Any, Throwable, T] { isFatal =>
      ZIO.async { k =>
        val handler = new CompletionHandler[T, Any] {
          def completed(result: T, u: Any): Unit = k(ZIO.succeed(result))

          def failed(t: Throwable, u: Any): Unit = t match {
            case e if !isFatal(e) => k(ZIO.fail(e))
            case _                => k(ZIO.die(t))
          }
        }

        try {
          op(handler)
        } catch {
          case e if !isFatal(e) => k(ZIO.fail(e))
        }
      }
    }

}
