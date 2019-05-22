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

package scalaz.zio.interop

import com.twitter.util.{ Future, FutureCancelledException, Return, Throw }
import scalaz.zio.{ Task, UIO }

package object twitter {
  implicit class TaskObjOps(private val obj: Task.type) extends AnyVal {
    final def fromTwitterFuture[A](future: Task[Future[A]]): Task[A] =
      future.flatMap { f =>
        Task.effectAsyncInterrupt { cb =>
          f.respond {
            case Return(a) => cb(Task.succeed(a))
            case Throw(e)  => cb(Task.fail(e))
          }

          Left(UIO(f.raise(new FutureCancelledException)))
        }
      }
  }
}
