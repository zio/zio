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

import cats.{Applicative, Bifunctor}

abstract class Guaranteed2[F[+ _, + _]] extends Bifunctor[F] {

  def applicative[E]: Applicative[F[E, ?]]

  def guarantee[E, A](fa: F[E, A], f: F[Nothing, Unit]): F[E, A]
}

object Guaranteed2 {

  @inline def apply[F[+ _, + _]: Guaranteed2]: Guaranteed2[F] = implicitly
}
