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

import zio.{ Fiber, ZIO }

object ZIOSyntax {
  final class EagerCreationSyntax[A](val a: A) extends AnyVal {
    def fail[R]: ZIO[R, A, Nothing]                            = ZIO.fail(a)
    def require[R, AA]: ZIO[R, A, Option[AA]] => ZIO[R, A, AA] = ZIO.require(a)
    def succeed[R, E]: ZIO[R, E, A]                            = ZIO.succeed(a)
  }

  final class LazyCreationSyntax[A](val a: () => A) extends AnyVal {
    def effectTotal[R, E]: ZIO[R, E, A] = ZIO.effectTotal(a())
    def effect[R]: ZIO[R, Throwable, A] = ZIO.effect(a())
    @deprecated("use effect", "1.0.0")
    def sync[R]: ZIO[R, Throwable, A] = effect
  }

  final class IterableSyntax[R, E, A](val ios: Iterable[ZIO[R, E, A]]) extends AnyVal {
    def collectAll: ZIO[R, E, Iterable[A]]                 = ZIO.collectAll(ios)
    def collectAllPar: ZIO[R, E, Iterable[A]]              = ZIO.collectAllPar(ios)
    def forkAll: ZIO[R, Nothing, Fiber[E, Iterable[A]]]    = ZIO.forkAll(ios)
    def mergeAll[B](zero: B)(f: (B, A) => B): ZIO[R, E, B] = ZIO.mergeAll(ios)(zero)(f)
  }

  final class Tuple2Syntax[R, E, A, B](val ios2: (ZIO[R, E, A], ZIO[R, E, B])) extends AnyVal {
    def map2[C](f: (A, B) => C): ZIO[R, E, C] = ios2._1.flatMap(a => ios2._2.map(f(a, _)))
  }

  final class Tuple3Syntax[R, E, A, B, C](val ios3: (ZIO[R, E, A], ZIO[R, E, B], ZIO[R, E, C])) extends AnyVal {
    def map3[D](f: (A, B, C) => D): ZIO[R, E, D] =
      for {
        a <- ios3._1
        b <- ios3._2
        c <- ios3._3
      } yield f(a, b, c)
  }

  final class Tuple4Syntax[R, E, A, B, C, D](
    val ios4: (ZIO[R, E, A], ZIO[R, E, B], ZIO[R, E, C], ZIO[R, E, D])
  ) extends AnyVal {
    def map4[F](f: (A, B, C, D) => F): ZIO[R, E, F] =
      for {
        a <- ios4._1
        b <- ios4._2
        c <- ios4._3
        d <- ios4._4
      } yield f(a, b, c, d)
  }
}
