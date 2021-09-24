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

trait FunctionToLayerOps {
  implicit final class Function0ToLayerSyntax[A: Tag](self: () => A) {

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
    def toLayer[A1 >: A: Tag]: URLayer[Any, Has[A1]] =
      UIO(self()).toLayer
  }

  implicit final class Function1ToLayerSyntax[A: Tag, B: Tag](self: A => B) {

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
    def toLayer[B1 >: B: Tag]: URLayer[Has[A], Has[B1]] =
      ZIO.service[A].map(self).toLayer
  }

  implicit final class Function2ToLayerSyntax[A: Tag, B: Tag, C: Tag](self: (A, B) => C) {

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
    def toLayer[C1 >: C: Tag]: URLayer[Has[A] with Has[B], Has[C1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
      } yield self(a, b)
    }.toLayer
  }

  implicit final class Function3ToLayerSyntax[A: Tag, B: Tag, C: Tag, D: Tag](self: (A, B, C) => D) {

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
    def toLayer[D1 >: D: Tag]: URLayer[Has[A] with Has[B] with Has[C], Has[D1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
      } yield self(a, b, c)
    }.toLayer
  }

  implicit final class Function4ToLayerSyntax[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag](self: (A, B, C, D) => E) {

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
    def toLayer[E1 >: E: Tag]: URLayer[Has[A] with Has[B] with Has[C] with Has[D], Has[E1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
      } yield self(a, b, c, d)
    }.toLayer
  }

  implicit final class Function5ToLayerSyntax[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag](
    self: (A, B, C, D, E) => F
  ) {

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
    def toLayer[F1 >: F: Tag]: URLayer[Has[A] with Has[B] with Has[C] with Has[D] with Has[E], Has[F1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
      } yield self(a, b, c, d, e)
    }.toLayer
  }

  implicit final class Function6ToLayerSyntax[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag, G: Tag](
    self: (A, B, C, D, E, F) => G
  ) {

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
    def toLayer[G1 >: G: Tag]: URLayer[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F], Has[G1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
      } yield self(a, b, c, d, e, f)
    }.toLayer
  }

  implicit final class Function7ToLayerSyntax[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag, G: Tag, H: Tag](
    self: (A, B, C, D, E, F, G) => H
  ) {

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
    def toLayer[H1 >: H: Tag]
      : URLayer[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G], Has[H1]] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
        g <- ZIO.service[G]
      } yield self(a, b, c, d, e, f, g)
    }.toLayer
  }

  implicit final class Function8ToLayerSyntax[A: Tag, B: Tag, C: Tag, D: Tag, E: Tag, F: Tag, G: Tag, H: Tag, I: Tag](
    self: (A, B, C, D, E, F, G, H) => I
  ) {

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
    def toLayer[I1 >: I: Tag]
      : URLayer[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[G] with Has[H], Has[I1]] = {
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
    }.toLayer
  }

  implicit final class Function9ToLayerSyntax[
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
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[J1 >: J: Tag]: URLayer[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
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
    }.toLayer
  }

  implicit final class Function10ToLayerSyntax[
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
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[K1 >: K: Tag]: URLayer[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
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
    }.toLayer
  }

  implicit final class Function11ToLayerSyntax[
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
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[L1 >: L: Tag]: URLayer[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
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
    }.toLayer
  }

  implicit final class Function12ToLayerSyntax[
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
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[M1 >: M: Tag]: URLayer[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
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
    }.toLayer
  }
  implicit final class Function13ToLayerSyntax[
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
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[N1 >: N: Tag]: URLayer[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
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
    }.toLayer
  }

  implicit final class Function14ToLayerSyntax[
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
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[O1 >: O: Tag]: URLayer[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
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
    }.toLayer
  }

  implicit final class Function15ToLayerSyntax[
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
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[P1 >: P: Tag]: URLayer[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
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
    }.toLayer
  }

  implicit final class Function16ToLayerSyntax[
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
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[Q1 >: Q: Tag]: URLayer[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
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
    }.toLayer
  }

  implicit final class Function17ToLayerSyntax[
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
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[R1 >: R: Tag]: URLayer[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
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
    }.toLayer
  }

  implicit final class Function18ToLayerSyntax[
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
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[S1 >: S: Tag]: URLayer[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
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
    }.toLayer
  }

  implicit final class Function19ToLayerSyntax[
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
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[T1 >: T: Tag]: URLayer[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
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
    }.toLayer
  }

  implicit final class Function20ToLayerSyntax[
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
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[U1 >: U: Tag]: URLayer[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
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
    }.toLayer
  }

  implicit final class Function21ToLayerSyntax[
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
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Has[Config] with Has[Repo], Has[Foo]] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[V1 >: V: Tag]: URLayer[Has[A] with Has[B] with Has[C] with Has[D] with Has[E] with Has[F] with Has[
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
    }.toLayer
  }

}
