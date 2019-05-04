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

import cats.Monad

package object bio {

  implicit def errorful2ImpliesMonad[F[+ _, + _], E](implicit ev: Errorful2[F]): Monad[F[E, ?]] = ev.monad

  implicit final class MonadOps[F[+ _, + _], E, A](private val fa: F[E, A]) extends AnyVal {

    def map[B](f: A => B)(implicit m: Monad[F[E, ?]]): F[E, B] =
      (m map fa)(f)

    def flatMap[B, EE >: E](f: A => F[EE, B])(implicit m: Monad[F[EE, ?]]): F[EE, B] =
      (m flatMap fa)(f)

    def >>=[B, EE >: E](f: A => F[EE, B])(implicit m: Monad[F[EE, ?]]): F[EE, B] =
      flatMap(f)

    def >>[B, EE >: E](fb: F[EE, B])(implicit m: Monad[F[EE, ?]]): F[EE, B] =
      flatMap(_ => fb)
  }
}
