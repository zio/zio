/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace

trait FunctionToDepsOps {
  implicit final class Function0ToDepsSyntax[A: Tag](self: () => A) {

    /**
     * Converts this function to a Deps.
     *
     * {{{
     * case class FooLive() extends Foo
     *
     * val live: UDeps[Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[A1 >: A: Tag](implicit trace: ZTraceElement): URDeps[Any, Has[A1]] =
      UIO(self()).toDeps

    /**
     * Converts this function to a Layer.
     *
     * {{{
     * case class FooLive() extends Foo
     *
     * val live: ULayer[Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[A1 >: A: Tag](implicit trace: ZTraceElement): URDeps[Any, Has[A1]] =
      toDeps
  }

  implicit final class Function1ToDepsSyntax[A: Tag, B: Tag](self: A => B) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config) extends Foo
     *
     * val live: URDeps[Has[Config], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[B1 >: B: Tag](implicit trace: ZTraceElement): URDeps[Has[A], Has[B1]] =
      ZIO.service[A].map(self).toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config) extends Foo
     *
     * val live: URLayer[Has[Config], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[B1 >: B: Tag](implicit trace: ZTraceElement): URDeps[Has[A], Has[B1]] =
      toDeps
  }

  implicit final class Function2ToDepsSyntax[A: Tag, B: Tag, C: Tag](self: (A, B) => C) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[C1 >: C: Tag](implicit trace: ZTraceElement): URDeps[Has[A] with Has[B], Has[C1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
      } yield self(a, b)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[C1 >: C: Tag](implicit trace: ZTraceElement): URDeps[Has[A] with Has[B], Has[C1]] =
      toDeps
  }

  implicit final class Function3ToDepsSyntax[A: Tag, B: Tag, C: Tag, D: Tag](self: (A, B, C) => D) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[D1 >: D: Tag](implicit trace: ZTraceElement): URDeps[Has[A] with Has[B] with Has[C], Has[D1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
      } yield self(a, b, c)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[D1 >: D: Tag](implicit trace: ZTraceElement): URDeps[Has[A] with Has[B] with Has[C], Has[D1]] =
      toDeps
  }

  implicit final class Function4ToDepsSyntax[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag](self: (A, B, C, D) => E) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[E1 >: E: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D], Has[E1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
      } yield self(a, b, c, d)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[E1 >: E: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D], Has[E1]] =
      toDeps
  }

  implicit final class Function5ToDepsSyntax[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag](
    self: (A, B, C, D, E) => F
  ) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[F1 >: F: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E], Has[F1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
      } yield self(a, b, c, d, e)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[F1 >: F: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E], Has[F1]] =
      toDeps
  }

  implicit final class Function6ToDepsSyntax[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag, G: Tag](
    self: (A, B, C, D, E, F) => G
  ) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[G1 >: G: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F], Has[G1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
      } yield self(a, b, c, d, e, f)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[G1 >: G: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F], Has[G1]] =
      toDeps
  }

  implicit final class Function7ToDepsSyntax[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag, G: Tag, H: Tag](
    self: (A, B, C, D, E, F, G) => H
  ) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[H1 >: H: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G], Has[H1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
        g <- ZIO.service[G]
      } yield self(a, b, c, d, e, f, g)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[H1 >: H: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G], Has[H1]] =
      toDeps
  }

  implicit final class Function8ToDepsSyntax[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag, G: Tag, H: Tag, I: Tag](
    self: (A, B, C, D, E, F, G, H) => I
  ) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[I1 >: I: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] with Has[H], Has[I1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
        g <- ZIO.service[G]
        h <- ZIO.service[H]
      } yield self(a, b, c, d, e, f, g, h)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[I1 >: I: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] with Has[H], Has[I1]] =
      toDeps
  }

  implicit final class Function9ToDepsSyntax[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag
  ](
    self: (A, B, C, D, E, F, G, H, I) => J
  ) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[J1 >: J: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I], Has[J1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
        g <- ZIO.service[G]
        h <- ZIO.service[H]
        i <- ZIO.service[I]
      } yield self(a, b, c, d, e, f, g, h, i)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[J1 >: J: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I], Has[J1]] =
      toDeps
  }

  implicit final class Function10ToDepsSyntax[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag
  ](
    self: (A, B, C, D, E, F, G, H, I, J) => K
  ) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[K1 >: K: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J], Has[K1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
        g <- ZIO.service[G]
        h <- ZIO.service[H]
        i <- ZIO.service[I]
        j <- ZIO.service[J]
      } yield self(a, b, c, d, e, f, g, h, i, j)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[K1 >: K: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J], Has[K1]] =
      toDeps
  }

  implicit final class Function11ToDepsSyntax[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K) => L
  ) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[L1 >: L: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K], Has[L1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
        g <- ZIO.service[G]
        h <- ZIO.service[H]
        i <- ZIO.service[I]
        j <- ZIO.service[J]
        k <- ZIO.service[K]
      } yield self(a, b, c, d, e, f, g, h, i, j, k)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[L1 >: L: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K], Has[L1]] =
      toDeps
  }

  implicit final class Function12ToDepsSyntax[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L) => M
  ) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[M1 >: M: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L], Has[M1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
        g <- ZIO.service[G]
        h <- ZIO.service[H]
        i <- ZIO.service[I]
        j <- ZIO.service[J]
        k <- ZIO.service[K]
        l <- ZIO.service[L]
      } yield self(a, b, c, d, e, f, g, h, i, j, k, l)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[M1 >: M: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L], Has[M1]] =
      toDeps
  }
  implicit final class Function13ToDepsSyntax[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag,
    N: Tag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M) => N
  ) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[N1 >: N: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M], Has[N1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
        g <- ZIO.service[G]
        h <- ZIO.service[H]
        i <- ZIO.service[I]
        j <- ZIO.service[J]
        k <- ZIO.service[K]
        l <- ZIO.service[L]
        m <- ZIO.service[M]
      } yield self(a, b, c, d, e, f, g, h, i, j, k, l, m)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[N1 >: N: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M], Has[N1]] =
      toDeps
  }

  implicit final class Function14ToDepsSyntax[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag,
    N: Tag,
    O: Tag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) => O
  ) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[O1 >: O: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N], Has[O1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
        g <- ZIO.service[G]
        h <- ZIO.service[H]
        i <- ZIO.service[I]
        j <- ZIO.service[J]
        k <- ZIO.service[K]
        l <- ZIO.service[L]
        m <- ZIO.service[M]
        n <- ZIO.service[N]
      } yield self(a, b, c, d, e, f, g, h, i, j, k, l, m, n)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[O1 >: O: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N], Has[O1]] =
      toDeps
  }

  implicit final class Function15ToDepsSyntax[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag,
    N: Tag,
    O: Tag,
    P: Tag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => P
  ) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[P1 >: P: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O], Has[P1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
        g <- ZIO.service[G]
        h <- ZIO.service[H]
        i <- ZIO.service[I]
        j <- ZIO.service[J]
        k <- ZIO.service[K]
        l <- ZIO.service[L]
        m <- ZIO.service[M]
        n <- ZIO.service[N]
        o <- ZIO.service[O]
      } yield self(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[P1 >: P: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O], Has[P1]] =
      toDeps
  }

  implicit final class Function16ToDepsSyntax[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag,
    N: Tag,
    O: Tag,
    P: Tag,
    Q: Tag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) => Q
  ) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[Q1 >: Q: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[P], Has[
      Q1
    ]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
        g <- ZIO.service[G]
        h <- ZIO.service[H]
        i <- ZIO.service[I]
        j <- ZIO.service[J]
        k <- ZIO.service[K]
        l <- ZIO.service[L]
        m <- ZIO.service[M]
        n <- ZIO.service[N]
        o <- ZIO.service[O]
        p <- ZIO.service[P]
      } yield self(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[Q1 >: Q: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[P], Has[
      Q1
    ]] =
      toDeps
  }

  implicit final class Function17ToDepsSyntax[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag,
    N: Tag,
    O: Tag,
    P: Tag,
    Q: Tag,
    R: Tag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) => R
  ) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[R1 >: R: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[
      P
    ] with Has[Q], Has[
      R1
    ]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
        g <- ZIO.service[G]
        h <- ZIO.service[H]
        i <- ZIO.service[I]
        j <- ZIO.service[J]
        k <- ZIO.service[K]
        l <- ZIO.service[L]
        m <- ZIO.service[M]
        n <- ZIO.service[N]
        o <- ZIO.service[O]
        p <- ZIO.service[P]
        q <- ZIO.service[Q]
      } yield self(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[R1 >: R: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[
      P
    ] with Has[Q], Has[
      R1
    ]] =
      toDeps
  }

  implicit final class Function18ToDepsSyntax[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag,
    N: Tag,
    O: Tag,
    P: Tag,
    Q: Tag,
    R: Tag,
    S: Tag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) => S
  ) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[S1 >: S: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[
      P
    ] with Has[Q] with Has[R], Has[
      S1
    ]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
        g <- ZIO.service[G]
        h <- ZIO.service[H]
        i <- ZIO.service[I]
        j <- ZIO.service[J]
        k <- ZIO.service[K]
        l <- ZIO.service[L]
        m <- ZIO.service[M]
        n <- ZIO.service[N]
        o <- ZIO.service[O]
        p <- ZIO.service[P]
        q <- ZIO.service[Q]
        r <- ZIO.service[R]
      } yield self(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[S1 >: S: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[
      P
    ] with Has[Q] with Has[R], Has[
      S1
    ]] =
      toDeps
  }

  implicit final class Function19ToDepsSyntax[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag,
    N: Tag,
    O: Tag,
    P: Tag,
    Q: Tag,
    R: Tag,
    S: Tag,
    T: Tag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) => T
  ) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[T1 >: T: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[
      P
    ] with Has[Q] with Has[R] with Has[S], Has[
      T1
    ]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
        g <- ZIO.service[G]
        h <- ZIO.service[H]
        i <- ZIO.service[I]
        j <- ZIO.service[J]
        k <- ZIO.service[K]
        l <- ZIO.service[L]
        m <- ZIO.service[M]
        n <- ZIO.service[N]
        o <- ZIO.service[O]
        p <- ZIO.service[P]
        q <- ZIO.service[Q]
        r <- ZIO.service[R]
        s <- ZIO.service[S]
      } yield self(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[T1 >: T: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[
      P
    ] with Has[Q] with Has[R] with Has[S], Has[
      T1
    ]] =
      toDeps
  }

  implicit final class Function20ToDepsSyntax[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag,
    N: Tag,
    O: Tag,
    P: Tag,
    Q: Tag,
    R: Tag,
    S: Tag,
    T: Tag,
    U: Tag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) => U
  ) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[U1 >: U: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[
      P
    ] with Has[Q] with Has[R] with Has[S] with Has[T], Has[
      U1
    ]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
        g <- ZIO.service[G]
        h <- ZIO.service[H]
        i <- ZIO.service[I]
        j <- ZIO.service[J]
        k <- ZIO.service[K]
        l <- ZIO.service[L]
        m <- ZIO.service[M]
        n <- ZIO.service[N]
        o <- ZIO.service[O]
        p <- ZIO.service[P]
        q <- ZIO.service[Q]
        r <- ZIO.service[R]
        s <- ZIO.service[S]
        t <- ZIO.service[T]
      } yield self(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[U1 >: U: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[
      P
    ] with Has[Q] with Has[R] with Has[S] with Has[T], Has[
      U1
    ]] =
      toDeps
  }

  implicit final class Function21ToDepsSyntax[
    A: Tag,
    B: Tag,
    C: Tag,
    D: Tag,
    E: Tag,
    F: Tag,
    G: Tag,
    H: Tag,
    I: Tag,
    J: Tag,
    K: Tag,
    L: Tag,
    M: Tag,
    N: Tag,
    O: Tag,
    P: Tag,
    Q: Tag,
    R: Tag,
    S: Tag,
    T: Tag,
    U: Tag,
    V: Tag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) => V
  ) {

    /**
     * Converts this function to a Deps that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URDeps[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toDeps
     * }}}
     */
    def toDeps[V1 >: V: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[
      P
    ] with Has[Q] with Has[R] with Has[S] with Has[T] with Has[U], Has[
      V1
    ]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
        g <- ZIO.service[G]
        h <- ZIO.service[H]
        i <- ZIO.service[I]
        j <- ZIO.service[J]
        k <- ZIO.service[K]
        l <- ZIO.service[L]
        m <- ZIO.service[M]
        n <- ZIO.service[N]
        o <- ZIO.service[O]
        p <- ZIO.service[P]
        q <- ZIO.service[Q]
        r <- ZIO.service[R]
        s <- ZIO.service[S]
        t <- ZIO.service[T]
        u <- ZIO.service[U]
      } yield self(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u)
    }.toDeps

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    @deprecated("use toDeps", "2.0.0")
    def toLayer[V1 >: V: Tag](implicit
      trace: ZTraceElement
    ): URDeps[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
      G
    ] with Has[H] with Has[I] with Has[J] with Has[K] with Has[L] with Has[M] with Has[N] with Has[O] with Has[
      P
    ] with Has[Q] with Has[R] with Has[S] with Has[T] with Has[U], Has[
      V1
    ]] =
      toDeps
  }

}
