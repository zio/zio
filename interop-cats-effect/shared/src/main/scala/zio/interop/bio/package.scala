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
package interop

import cats.Monad
import com.github.ghik.silencer.silent
import zio.interop.bio.{ Bracket2, Concurrent2, Errorful2, RunAsync2, RunSync2 }

package object bio extends SyntaxInstances0 {

  implicit private[interop] final class FaSyntax[F[+_, +_], E, A](private val fa: F[E, A]) extends AnyVal {

    @inline def map[B](f: A => B)(implicit m: Monad[F[E, ?]]): F[E, B] =
      (m map fa)(f)

    @inline def flatMap[B, EE >: E](f: A => F[EE, B])(implicit m: Monad[F[EE, ?]]): F[EE, B] =
      (m flatMap fa)(f)

    @inline def tap[EE >: E, B](f: A => F[EE, B])(implicit m: Monad[F[EE, ?]]): F[EE, A] =
      flatMap(a => m.map(f(a))(_ => a))

    @inline def >>=[B, EE >: E](f: A => F[EE, B])(implicit m: Monad[F[EE, ?]]): F[EE, B] =
      flatMap(f)

    @inline def *>[B, EE >: E](fb: F[EE, B])(implicit m: Monad[F[EE, ?]]): F[EE, B] =
      flatMap(_ => fb)

    @inline def <*[B, EE >: E](fb: F[EE, B])(implicit m: Monad[F[EE, ?]]): F[EE, A] =
      flatMap(a => fb map (_ => a))

    @silent @inline def widenBoth[EE, AA](implicit ev1: A <:< AA, ev2: E <:< EE): F[EE, AA] =
      fa.asInstanceOf[F[EE, AA]]
  }

  implicit private[interop] final class FFaSyntax[F[+_, +_], E, EE >: E, A](private val ffa: F[E, F[EE, A]])
      extends AnyVal {

    def flatten[B](implicit m: Monad[F[EE, ?]]): F[EE, A] =
      m flatten ffa
  }
}

private[interop] sealed abstract class SyntaxInstances0 extends SyntaxInstances1 {
  @inline implicit def runAsync2ImpliesMonad[F[+_, +_], E](implicit ev: RunAsync2[F]): Monad[F[E, ?]] = ev.monad
}

private[interop] sealed abstract class SyntaxInstances1 extends SyntaxInstances2 {
  @inline implicit def runSync2ImpliesMonad[F[+_, +_], E](implicit ev: RunSync2[F]): Monad[F[E, ?]] = ev.monad
}

private[interop] sealed abstract class SyntaxInstances2 extends SyntaxInstances3 {
  @inline implicit def concurrent2ImpliesMonad[F[+_, +_], E](implicit ev: Concurrent2[F]): Monad[F[E, ?]] = ev.monad
}

private[interop] sealed abstract class SyntaxInstances3 extends SyntaxInstances4 {
  @inline implicit def bracket2ImpliesMonad[F[+_, +_], E](implicit ev: Bracket2[F]): Monad[F[E, ?]] = ev.monad
}

private[interop] sealed abstract class SyntaxInstances4 {
  @inline implicit def errorful2ImpliesMonad[F[+_, +_], E](implicit ev: Errorful2[F]): Monad[F[E, ?]] = ev.monad
}
