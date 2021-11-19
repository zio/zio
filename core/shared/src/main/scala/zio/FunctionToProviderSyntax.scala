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

trait FunctionToProviderSyntax {
  implicit final class Function0ToProviderOps[A: Tag: IsNotIntersection](self: () => A) {

    /**
     * Converts this function to a Provider.
     *
     * {{{
     * case class FooLive() extends Foo
     *
     * val live: UProvider[Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[A1 >: A: Tag: IsNotIntersection](implicit trace: ZTraceElement): URProvider[Any, A1] =
      UIO(self()).toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[A1 >: A: Tag: IsNotIntersection](implicit trace: ZTraceElement): URProvider[Any, A1] =
      toProvider
  }

  implicit final class Function1ToProviderOps[A: Tag: IsNotIntersection, B: Tag: IsNotIntersection](
    self: A => B
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config) extends Foo
     *
     * val live: URProvider[Config, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[B1 >: B: Tag: IsNotIntersection](implicit trace: ZTraceElement): URProvider[A, B1] =
      ZIO.serviceWith[A](self).toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[B1 >: B: Tag: IsNotIntersection](implicit trace: ZTraceElement): URProvider[A, B1] =
      toProvider
  }

  implicit final class Function2ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection
  ](
    self: (A, B) => C
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[C1 >: C: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B, C1] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
      } yield self(a, b)
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[C1 >: C: Tag: IsNotIntersection](implicit trace: ZTraceElement): URProvider[A with B, C1] =
      toProvider
  }

  implicit final class Function3ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection
  ](self: (A, B, C) => D) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[D1 >: D: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C, D1] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
      } yield self(a, b, c)
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[D1 >: D: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C, D1] =
      toProvider
  }

  implicit final class Function4ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection
  ](
    self: (A, B, C, D) => E
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[E1 >: E: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D, E1] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
      } yield self(a, b, c, d)
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[E1 >: E: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D, E1] =
      toProvider
  }

  implicit final class Function5ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection,
    F: Tag: IsNotIntersection
  ](
    self: (A, B, C, D, E) => F
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[F1 >: F: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D with E, F1] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
      } yield self(a, b, c, d, e)
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[F1 >: F: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D with E, F1] =
      toProvider
  }

  implicit final class Function6ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection,
    F: Tag: IsNotIntersection,
    G: Tag: IsNotIntersection
  ](
    self: (A, B, C, D, E, F) => G
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[G1 >: G: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D with E with F, G1] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
      } yield self(a, b, c, d, e, f)
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[G1 >: G: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D with E with F, G1] =
      toProvider
  }

  implicit final class Function7ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection,
    F: Tag: IsNotIntersection,
    G: Tag: IsNotIntersection,
    H: Tag: IsNotIntersection
  ](
    self: (A, B, C, D, E, F, G) => H
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[H1 >: H: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D with E with F with G, H1] = {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
        c <- ZIO.service[C]
        d <- ZIO.service[D]
        e <- ZIO.service[E]
        f <- ZIO.service[F]
        g <- ZIO.service[G]
      } yield self(a, b, c, d, e, f, g)
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[H1 >: H: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D with E with F with G, H1] =
      toProvider
  }

  implicit final class Function8ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection,
    F: Tag: IsNotIntersection,
    G: Tag: IsNotIntersection,
    H: Tag: IsNotIntersection,
    I: Tag: IsNotIntersection
  ](
    self: (A, B, C, D, E, F, G, H) => I
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[I1 >: I: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D with E with F with G with H, I1] = {
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
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[I1 >: I: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D with E with F with G with H, I1] =
      toProvider
  }

  implicit final class Function9ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection,
    F: Tag: IsNotIntersection,
    G: Tag: IsNotIntersection,
    H: Tag: IsNotIntersection,
    I: Tag: IsNotIntersection,
    J: Tag: IsNotIntersection
  ](
    self: (A, B, C, D, E, F, G, H, I) => J
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[J1 >: J: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D with E with F with G with H with I, J1] = {
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
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[J1 >: J: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D with E with F with G with H with I, J1] =
      toProvider
  }

  implicit final class Function10ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection,
    F: Tag: IsNotIntersection,
    G: Tag: IsNotIntersection,
    H: Tag: IsNotIntersection,
    I: Tag: IsNotIntersection,
    J: Tag: IsNotIntersection,
    K: Tag: IsNotIntersection
  ](
    self: (A, B, C, D, E, F, G, H, I, J) => K
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[K1 >: K: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D with E with F with G with H with I with J, K1] = {
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
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[K1 >: K: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D with E with F with G with H with I with J, K1] =
      toProvider
  }

  implicit final class Function11ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection,
    F: Tag: IsNotIntersection,
    G: Tag: IsNotIntersection,
    H: Tag: IsNotIntersection,
    I: Tag: IsNotIntersection,
    J: Tag: IsNotIntersection,
    K: Tag: IsNotIntersection,
    L: Tag: IsNotIntersection
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K) => L
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[L1 >: L: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D with E with F with G with H with I with J with K, L1] = {
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
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[L1 >: L: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D with E with F with G with H with I with J with K, L1] =
      toProvider
  }

  implicit final class Function12ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection,
    F: Tag: IsNotIntersection,
    G: Tag: IsNotIntersection,
    H: Tag: IsNotIntersection,
    I: Tag: IsNotIntersection,
    J: Tag: IsNotIntersection,
    K: Tag: IsNotIntersection,
    L: Tag: IsNotIntersection,
    M: Tag: IsNotIntersection
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L) => M
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[M1 >: M: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D with E with F with G with H with I with J with K with L, M1] = {
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
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[M1 >: M: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D with E with F with G with H with I with J with K with L, M1] =
      toProvider
  }
  implicit final class Function13ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection,
    F: Tag: IsNotIntersection,
    G: Tag: IsNotIntersection,
    H: Tag: IsNotIntersection,
    I: Tag: IsNotIntersection,
    J: Tag: IsNotIntersection,
    K: Tag: IsNotIntersection,
    L: Tag: IsNotIntersection,
    M: Tag: IsNotIntersection,
    N: Tag: IsNotIntersection
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M) => N
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[N1 >: N: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D with E with F with G with H with I with J with K with L with M, N1] = {
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
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[N1 >: N: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[A with B with C with D with E with F with G with H with I with J with K with L with M, N1] =
      toProvider
  }

  implicit final class Function14ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection,
    F: Tag: IsNotIntersection,
    G: Tag: IsNotIntersection,
    H: Tag: IsNotIntersection,
    I: Tag: IsNotIntersection,
    J: Tag: IsNotIntersection,
    K: Tag: IsNotIntersection,
    L: Tag: IsNotIntersection,
    M: Tag: IsNotIntersection,
    N: Tag: IsNotIntersection,
    O: Tag: IsNotIntersection
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) => O
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[O1 >: O: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[
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
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[O1 >: O: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N,
      O1
    ] =
      toProvider
  }

  implicit final class Function15ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection,
    F: Tag: IsNotIntersection,
    G: Tag: IsNotIntersection,
    H: Tag: IsNotIntersection,
    I: Tag: IsNotIntersection,
    J: Tag: IsNotIntersection,
    K: Tag: IsNotIntersection,
    L: Tag: IsNotIntersection,
    M: Tag: IsNotIntersection,
    N: Tag: IsNotIntersection,
    O: Tag: IsNotIntersection,
    P: Tag: IsNotIntersection
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => P
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[P1 >: P: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[
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
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[P1 >: P: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N with O,
      P1
    ] =
      toProvider
  }

  implicit final class Function16ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection,
    F: Tag: IsNotIntersection,
    G: Tag: IsNotIntersection,
    H: Tag: IsNotIntersection,
    I: Tag: IsNotIntersection,
    J: Tag: IsNotIntersection,
    K: Tag: IsNotIntersection,
    L: Tag: IsNotIntersection,
    M: Tag: IsNotIntersection,
    N: Tag: IsNotIntersection,
    O: Tag: IsNotIntersection,
    P: Tag: IsNotIntersection,
    Q: Tag: IsNotIntersection
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) => Q
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[Q1 >: Q: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[
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
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[Q1 >: Q: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N with O with P,
      Q1
    ] =
      toProvider
  }

  implicit final class Function17ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection,
    F: Tag: IsNotIntersection,
    G: Tag: IsNotIntersection,
    H: Tag: IsNotIntersection,
    I: Tag: IsNotIntersection,
    J: Tag: IsNotIntersection,
    K: Tag: IsNotIntersection,
    L: Tag: IsNotIntersection,
    M: Tag: IsNotIntersection,
    N: Tag: IsNotIntersection,
    O: Tag: IsNotIntersection,
    P: Tag: IsNotIntersection,
    Q: Tag: IsNotIntersection,
    R: Tag: IsNotIntersection
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) => R
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[R1 >: R: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[
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
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[R1 >: R: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N with O with P with Q,
      R1
    ] =
      toProvider
  }

  implicit final class Function18ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection,
    F: Tag: IsNotIntersection,
    G: Tag: IsNotIntersection,
    H: Tag: IsNotIntersection,
    I: Tag: IsNotIntersection,
    J: Tag: IsNotIntersection,
    K: Tag: IsNotIntersection,
    L: Tag: IsNotIntersection,
    M: Tag: IsNotIntersection,
    N: Tag: IsNotIntersection,
    O: Tag: IsNotIntersection,
    P: Tag: IsNotIntersection,
    Q: Tag: IsNotIntersection,
    R: Tag: IsNotIntersection,
    S: Tag: IsNotIntersection
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) => S
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[S1 >: S: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[
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
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[S1 >: S: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N with O with P with Q with R,
      S1
    ] =
      toProvider
  }

  implicit final class Function19ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection,
    F: Tag: IsNotIntersection,
    G: Tag: IsNotIntersection,
    H: Tag: IsNotIntersection,
    I: Tag: IsNotIntersection,
    J: Tag: IsNotIntersection,
    K: Tag: IsNotIntersection,
    L: Tag: IsNotIntersection,
    M: Tag: IsNotIntersection,
    N: Tag: IsNotIntersection,
    O: Tag: IsNotIntersection,
    P: Tag: IsNotIntersection,
    Q: Tag: IsNotIntersection,
    R: Tag: IsNotIntersection,
    S: Tag: IsNotIntersection,
    T: Tag: IsNotIntersection
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) => T
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[T1 >: T: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[
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
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[T1 >: T: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N with O with P with Q with R with S,
      T1
    ] =
      toProvider
  }

  implicit final class Function20ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection,
    F: Tag: IsNotIntersection,
    G: Tag: IsNotIntersection,
    H: Tag: IsNotIntersection,
    I: Tag: IsNotIntersection,
    J: Tag: IsNotIntersection,
    K: Tag: IsNotIntersection,
    L: Tag: IsNotIntersection,
    M: Tag: IsNotIntersection,
    N: Tag: IsNotIntersection,
    O: Tag: IsNotIntersection,
    P: Tag: IsNotIntersection,
    Q: Tag: IsNotIntersection,
    R: Tag: IsNotIntersection,
    S: Tag: IsNotIntersection,
    T: Tag: IsNotIntersection,
    U: Tag: IsNotIntersection
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) => U
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[U1 >: U: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[
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
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[U1 >: U: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N with O with P with Q with R with S with T,
      U1
    ] =
      toProvider
  }

  implicit final class Function21ToProviderOps[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection,
    E: Tag: IsNotIntersection,
    F: Tag: IsNotIntersection,
    G: Tag: IsNotIntersection,
    H: Tag: IsNotIntersection,
    I: Tag: IsNotIntersection,
    J: Tag: IsNotIntersection,
    K: Tag: IsNotIntersection,
    L: Tag: IsNotIntersection,
    M: Tag: IsNotIntersection,
    N: Tag: IsNotIntersection,
    O: Tag: IsNotIntersection,
    P: Tag: IsNotIntersection,
    Q: Tag: IsNotIntersection,
    R: Tag: IsNotIntersection,
    S: Tag: IsNotIntersection,
    T: Tag: IsNotIntersection,
    U: Tag: IsNotIntersection,
    V: Tag: IsNotIntersection
  ](
    self: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) => V
  ) {

    /**
     * Converts this function to a Provider that depends upon its inputs.
     *
     * {{{
     * case class FooLive(config: Config, repo: Repo) extends Foo
     *
     * val live: URProvider[Config with Repo, Foo] =
     *   FooLive.toProvider
     * }}}
     */
    def toProvider[V1 >: V: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[
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
    }.toProvider

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
    @deprecated("use toProvider", "2.0.0")
    def toLayer[V1 >: V: Tag: IsNotIntersection](implicit
      trace: ZTraceElement
    ): URProvider[
      A with B with C with D with E with F with G with H with I with J with K with L with M with N with O with P with Q with R with S with T with U,
      V1
    ] =
      toProvider
  }

}
