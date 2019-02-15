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

package scalaz.zio.internal.impls

import java.util

import scalaz.zio.Exit.Cause
import scalaz.zio.IO
import scalaz.zio.internal.{ Env => IEnv, Executor }

import scala.concurrent.ExecutionContext.Implicits

object Env {

  /**
   * Creates a new default environment.
   */
  final def newDefaultEnv(reportFailure0: Cause[_] => IO[Nothing, _]): IEnv =
    new IEnv {
      val executor = Executor.fromExecutionContext(1024)(Implicits.global)

      def nonFatal(t: Throwable): Boolean =
        !t.isInstanceOf[VirtualMachineError]

      def reportFailure(cause: Cause[_]): IO[Nothing, _] =
        reportFailure0(cause)

      def newWeakHashMap[A, B](): util.Map[A, B] =
        new util.HashMap[A, B]()
    }
}
