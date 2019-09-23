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

package zio

import scala.language.implicitConversions

package object syntax {
  import zio.syntax.ZIOSyntax._

  implicit final def zioEagerCreationSyntax[A](a: A): EagerCreationSyntax[A]  = new EagerCreationSyntax[A](a)
  implicit final def zioLazyCreationSyntax[A](a: => A): LazyCreationSyntax[A] = new LazyCreationSyntax[A](() => a)
  implicit final def zioIterableSyntax[R, E, A](zios: Iterable[ZIO[R, E, A]]): IterableSyntax[R, E, A] =
    new IterableSyntax[R, E, A](zios)

  implicit final def zioTuple2Syntax[R, E, A, B](zios: (ZIO[R, E, A], ZIO[R, E, B])): Tuple2Syntax[R, E, A, B] =
    new Tuple2Syntax(zios)
  implicit final def zioTuple3Syntax[R, E, A, B, C](
    zios: (ZIO[R, E, A], ZIO[R, E, B], ZIO[R, E, C])
  ): Tuple3Syntax[R, E, A, B, C] =
    new Tuple3Syntax(zios)
  implicit final def zioTuple4Syntax[R, E, A, B, C, D](
    zios: (ZIO[R, E, A], ZIO[R, E, B], ZIO[R, E, C], ZIO[R, E, D])
  ): Tuple4Syntax[R, E, A, B, C, D] =
    new Tuple4Syntax(zios)
}
