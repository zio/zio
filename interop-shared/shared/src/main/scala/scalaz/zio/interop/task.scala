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

import scalaz.zio.Task

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object Util {
  type Par[A] = Par.T[Throwable, A]

  final def fromFuture[E, A](ec: ExecutionContext)(io: Task[Future[A]]): Task[A] =
    io.either.flatMap { tf =>
      tf.fold(
        t => Task.fail(t),
        f =>
          f.value.fold(
            Task.effectAsync { (cb: Task[A] => Unit) =>
              f.onComplete {
                case Success(a) => cb(Task.succeed(a))
                case Failure(t) => cb(Task.fail(t))
              }(ec)
            }
          )(Task.fromTry(_))
      )
    }
}
