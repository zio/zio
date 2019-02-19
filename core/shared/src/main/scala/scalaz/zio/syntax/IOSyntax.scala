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

package scalaz.zio.syntax

import scalaz.zio.{ Fiber, IO, Task, UIO }

object IOSyntax {
  final class IOCreationLazySyntax[A](val a: () => A) extends AnyVal {
    def succeedLazy: UIO[A] = IO.succeedLazy(a())
    def defer: UIO[A]       = IO.defer(a())
    def sync: Task[A]       = Task.sync(a())
  }

  final class IOCreationEagerSyntax[A](val a: A) extends AnyVal {
    def succeed: UIO[A]                             = IO.succeed(a)
    def fail: IO[A, Nothing]                        = IO.fail(a)
    def require[AA]: IO[A, Option[AA]] => IO[A, AA] = IO.require(a)
  }

  final class IOIterableSyntax[E, A](val ios: Iterable[IO[E, A]]) extends AnyVal {
    def mergeAll[B](zero: B)(f: (B, A) => B): IO[E, B] = IO.mergeAll(ios)(zero)(f)
    def collectAllPar: IO[E, List[A]]                  = IO.collectAllPar(ios)
    def forkAll: UIO[Fiber[E, List[A]]]                = IO.forkAll(ios)
    def collectAll: IO[E, List[A]]                     = IO.collectAll(ios)
  }

  final class IOTuple2[E, A, B](val ios2: (IO[E, A], IO[E, B])) extends AnyVal {
    def map2[C](f: (A, B) => C): IO[E, C] = ios2._1.flatMap(a => ios2._2.map(f(a, _)))
  }

  final class IOTuple3[E, A, B, C](val ios3: (IO[E, A], IO[E, B], IO[E, C])) extends AnyVal {
    def map3[D](f: (A, B, C) => D): IO[E, D] =
      for {
        a <- ios3._1
        b <- ios3._2
        c <- ios3._3
      } yield f(a, b, c)
  }

  final class IOTuple4[E, A, B, C, D](val ios4: (IO[E, A], IO[E, B], IO[E, C], IO[E, D])) extends AnyVal {
    def map4[F](f: (A, B, C, D) => F): IO[E, F] =
      for {
        a <- ios4._1
        b <- ios4._2
        c <- ios4._3
        d <- ios4._4
      } yield f(a, b, c, d)
  }
}
