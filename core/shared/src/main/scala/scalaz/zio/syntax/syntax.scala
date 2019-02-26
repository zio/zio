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

import scalaz.zio.syntax.IOSyntax._
import scala.language.implicitConversions

package object syntax {
  implicit final def ioEagerSyntax[A](a: A): IOCreationEagerSyntax[A]                        = new IOCreationEagerSyntax[A](a)
  implicit final def ioLazySyntax[A](a: => A): IOCreationLazySyntax[A]                       = new IOCreationLazySyntax[A](() => a)
  implicit final def ioIterableSyntax[E, A](ios: Iterable[IO[E, A]]): IOIterableSyntax[E, A] = new IOIterableSyntax(ios)
  implicit final def ioTuple2Syntax[E, A, B](ios: (IO[E, A], IO[E, B])): IOTuple2[E, A, B]   = new IOTuple2(ios)
  implicit final def ioTuple3Syntax[E, A, B, C](ios: (IO[E, A], IO[E, B], IO[E, C])): IOTuple3[E, A, B, C] =
    new IOTuple3(ios)
  implicit final def ioTuple4Syntax[E, A, B, C, D](
    ios: (IO[E, A], IO[E, B], IO[E, C], IO[E, D])
  ): IOTuple4[E, A, B, C, D] =
    new IOTuple4(ios)
}
