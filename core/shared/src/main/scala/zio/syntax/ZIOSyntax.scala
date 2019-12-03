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

package zio.syntax

import zio.ZIO

object ZIOSyntax {

  final class EagerCreationSyntax[A](val a: A) extends AnyVal {
    def fail[R]: ZIO[R, A, Nothing]                            = ZIO.fail(a)
    def require[R, AA]: ZIO[R, A, Option[AA]] => ZIO[R, A, AA] = ZIO.require(a)
    def succeed[R, E]: ZIO[R, E, A]                            = ZIO.succeed(a)
  }

  final class LazyCreationSyntax[A](val a: () => A) extends AnyVal {
    def effectTotal[R, E]: ZIO[R, E, A] = ZIO.effectTotal(a())
    def effect[R]: ZIO[R, Throwable, A] = ZIO.effect(a())
  }
}
