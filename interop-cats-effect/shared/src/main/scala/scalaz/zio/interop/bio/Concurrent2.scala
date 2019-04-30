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
package interop
package bio

import scala.concurrent.ExecutionContext

abstract class Concurrent2[F[+ _, + _]] extends Temporal2[F] {

  def start[E, A](fa: F[E, A]): F[Nothing, Fiber2[F, E, A]]

  def uninterruptible[E, A](fa: F[E, A]): F[E, A]

  def yieldTo[E, A](fa: F[E, A]): F[E, A]

  def evalOn[E, A](fa: F[E, A], ec: ExecutionContext): F[E, A]
}

object Concurrent2 {

  @inline def apply[F[+ _, + _]: Concurrent2]: Concurrent2[F] = implicitly
}
