/*
 * Copyright 2021-2022 John A. De Goes and the ZIO Contributors
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

trait FunctionToLayerSyntax {
  implicit final class Function0ToLayerOps[A: ServiceTag](self: () => A) {

    /**
     * Converts this function to a Layer.
     *
     * {{{
     * case class FooLive() extends Foo
     *
     * val live: ULayer[Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[A1 >: A: ServiceTag](implicit trace: ZTraceElement): URLayer[Any, A1] =
      UIO(self()).toLayer
  }

  implicit final class Function1ToLayerOps[A: ServiceTag, B: ServiceTag](
    self: A => B
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config) extends Foo
     *
     * val live: URLayer[Config, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[B1 >: B: ServiceTag](implicit trace: ZTraceElement): URLayer[A, B1] =
      ZIO.serviceWith[A](self).toLayer
  }

  implicit final class Function2ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag
  ](
    self: (A, B) => C
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[C1 >: C: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[A with B, C1] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
      } yield self(a, b)
    }.toLayer
  }

  implicit final class Function3ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag
  ](self: (A, B, C) => D) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[D1 >: D: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[A with B with C, D1] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
      } yield self(a, b, c)
    }.toLayer
  }

  implicit final class Function4ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag,
    E: ServiceTag
  ](
    self: (A, B, C, D) => E
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[E1 >: E: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[A with B with C with D, E1] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
      } yield self(a, b, c, d)
    }.toLayer
  }

  implicit final class Function5ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag,
    E: ServiceTag,
    F: ServiceTag
  ](
    self: (A, B, C, D, E) => F
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[F1 >: F: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[A with B with C with D with E, F1] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
      } yield self(a, b, c, d, e)
    }.toLayer
  }

  implicit final class Function6ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag,
    E: ServiceTag,
    F: ServiceTag,
    G: ServiceTag
  ](
    self: (A, B, C, D, E, F) => G
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[G1 >: G: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[A with B with C with D with E with F, G1] = {
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

  implicit final class Function7ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag,
    E: ServiceTag,
    F: ServiceTag,
    G: ServiceTag,
    H: ServiceTag
  ](
    self: (A, B, C, D, E, F, G) => H
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[H1 >: H: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[A with B with C with D with E with F with G, H1] = {
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

  implicit final class Function8ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag,
    E: ServiceTag,
    F: ServiceTag,
    G: ServiceTag,
    H: ServiceTag,
    I: ServiceTag
  ](
    self: (A, B, C, D, E, F, G, H) => I
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[I1 >: I: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[A with B with C with D with E with F with G with H, I1] = {
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

  implicit final class Function9ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag,
    E: ServiceTag,
    F: ServiceTag,
    G: ServiceTag,
    H: ServiceTag,
    I: ServiceTag,
    J: ServiceTag
  ](
    self: (A, B, C, D, E, F, G, H, I) => J
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[J1 >: J: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[A with B with C with D with E with F with G with H with I, J1] = {
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

  implicit final class Function10ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag,
    E: ServiceTag,
    F: ServiceTag,
    G: ServiceTag,
    H: ServiceTag,
    I: ServiceTag,
    J: ServiceTag,
    K: ServiceTag
  ](
    self: (A, B, C, D, E, F, G, H, I, J) => K
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[K1 >: K: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[A with B with C with D with E with F with G with H with I with J, K1] = {
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

  implicit final class Function11ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag,
    E: ServiceTag,
    F: ServiceTag,
    G: ServiceTag,
    H: ServiceTag,
    I: ServiceTag,
    J: ServiceTag,
    K: ServiceTag,
    L: ServiceTag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K) => L
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[L1 >: L: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[A with B with C with D with E with F with G with H with I with J with K, L1] = {
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

  implicit final class Function12ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag,
    E: ServiceTag,
    F: ServiceTag,
    G: ServiceTag,
    H: ServiceTag,
    I: ServiceTag,
    J: ServiceTag,
    K: ServiceTag,
    L: ServiceTag,
    M: ServiceTag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L) => M
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[M1 >: M: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[A with B with C with D with E with F with G with H with I with J with K with L, M1] = {
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
  implicit final class Function13ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag,
    E: ServiceTag,
    F: ServiceTag,
    G: ServiceTag,
    H: ServiceTag,
    I: ServiceTag,
    J: ServiceTag,
    K: ServiceTag,
    L: ServiceTag,
    M: ServiceTag,
    N: ServiceTag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M) => N
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[N1 >: N: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[A with B with C with D with E with F with G with H with I with J with K with L with M, N1] = {
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

  implicit final class Function14ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag,
    E: ServiceTag,
    F: ServiceTag,
    G: ServiceTag,
    H: ServiceTag,
    I: ServiceTag,
    J: ServiceTag,
    K: ServiceTag,
    L: ServiceTag,
    M: ServiceTag,
    N: ServiceTag,
    O: ServiceTag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) => O
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[O1 >: O: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N,
      O1
    ] = {
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

  implicit final class Function15ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag,
    E: ServiceTag,
    F: ServiceTag,
    G: ServiceTag,
    H: ServiceTag,
    I: ServiceTag,
    J: ServiceTag,
    K: ServiceTag,
    L: ServiceTag,
    M: ServiceTag,
    N: ServiceTag,
    O: ServiceTag,
    P: ServiceTag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => P
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[P1 >: P: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N with O,
      P1
    ] = {
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

  implicit final class Function16ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag,
    E: ServiceTag,
    F: ServiceTag,
    G: ServiceTag,
    H: ServiceTag,
    I: ServiceTag,
    J: ServiceTag,
    K: ServiceTag,
    L: ServiceTag,
    M: ServiceTag,
    N: ServiceTag,
    O: ServiceTag,
    P: ServiceTag,
    Q: ServiceTag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) => Q
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[Q1 >: Q: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N with O with P,
      Q1
    ] = {
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

  implicit final class Function17ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag,
    E: ServiceTag,
    F: ServiceTag,
    G: ServiceTag,
    H: ServiceTag,
    I: ServiceTag,
    J: ServiceTag,
    K: ServiceTag,
    L: ServiceTag,
    M: ServiceTag,
    N: ServiceTag,
    O: ServiceTag,
    P: ServiceTag,
    Q: ServiceTag,
    R: ServiceTag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) => R
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[R1 >: R: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N with O with P with Q,
      R1
    ] = {
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

  implicit final class Function18ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag,
    E: ServiceTag,
    F: ServiceTag,
    G: ServiceTag,
    H: ServiceTag,
    I: ServiceTag,
    J: ServiceTag,
    K: ServiceTag,
    L: ServiceTag,
    M: ServiceTag,
    N: ServiceTag,
    O: ServiceTag,
    P: ServiceTag,
    Q: ServiceTag,
    R: ServiceTag,
    S: ServiceTag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) => S
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[S1 >: S: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N with O with P with Q with R,
      S1
    ] = {
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

  implicit final class Function19ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag,
    E: ServiceTag,
    F: ServiceTag,
    G: ServiceTag,
    H: ServiceTag,
    I: ServiceTag,
    J: ServiceTag,
    K: ServiceTag,
    L: ServiceTag,
    M: ServiceTag,
    N: ServiceTag,
    O: ServiceTag,
    P: ServiceTag,
    Q: ServiceTag,
    R: ServiceTag,
    S: ServiceTag,
    T: ServiceTag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) => T
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[T1 >: T: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N with O with P with Q with R with S,
      T1
    ] = {
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

  implicit final class Function20ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag,
    E: ServiceTag,
    F: ServiceTag,
    G: ServiceTag,
    H: ServiceTag,
    I: ServiceTag,
    J: ServiceTag,
    K: ServiceTag,
    L: ServiceTag,
    M: ServiceTag,
    N: ServiceTag,
    O: ServiceTag,
    P: ServiceTag,
    Q: ServiceTag,
    R: ServiceTag,
    S: ServiceTag,
    T: ServiceTag,
    U: ServiceTag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) => U
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[U1 >: U: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N with O with P with Q with R with S with T,
      U1
    ] = {
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

  implicit final class Function21ToLayerOps[
    A: ServiceTag,
    B: ServiceTag,
    C: ServiceTag,
    D: ServiceTag,
    E: ServiceTag,
    F: ServiceTag,
    G: ServiceTag,
    H: ServiceTag,
    I: ServiceTag,
    J: ServiceTag,
    K: ServiceTag,
    L: ServiceTag,
    M: ServiceTag,
    N: ServiceTag,
    O: ServiceTag,
    P: ServiceTag,
    Q: ServiceTag,
    R: ServiceTag,
    S: ServiceTag,
    T: ServiceTag,
    U: ServiceTag,
    V: ServiceTag
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) => V
  ) {

    /**
     * Converts this function to a Layer that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URLayer[Config with Repo, Foo] =
     *   FooLive.toLayer
     * }}}
     */
    def toLayer[V1 >: V: ServiceTag](implicit
      trace: ZTraceElement
    ): URLayer[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N with O with P with Q with R with S with T with U,
      V1
    ] = {
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
