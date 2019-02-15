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

package scalaz.zio

import scalaz.zio.internal.impls.Env

trait CommonRTS {
  type Environment

  val Environment: Environment

  lazy val env =
    Env.newDefaultEnv {
      case cause if cause.interrupted => IO.unit // do not log interruptions
      case cause                      => IO.sync(println(cause.toString))
    }

  /**
   * Awaits for the result of the fiber to be computed.
   * In Javascript, this operation will not, in general, succeed because it is not possible to block for the result.
   * However, it may succeed in some cases if the IO is purely synchronous.
   */
  final def unsafeRun[E, A](io: ZIO[Environment, E, A]): A =
    env.unsafeRun(io.provide(Environment))

  /**
   * Awaits for the result of the fiber to be computed.
   * In Javascript, this operation will not, in general, succeed because it is not possible to block for the result.
   * However, it may succeed in some cases if the IO is purely synchronous.
   */
  final def unsafeRunSync[E, A](io: ZIO[Environment, E, A]): Exit[E, A] =
    env.unsafeRunSync(io.provide(Environment))

  /**
   * Runs the `io` asynchronously.
   */
  final def unsafeRunAsync[E, A](io: ZIO[Environment, E, A])(k: Exit[E, A] => Unit): Unit =
    env.unsafeRunAsync(io.provide(Environment), k)

  final def shutdown(): Unit = env.shutdown()
}
