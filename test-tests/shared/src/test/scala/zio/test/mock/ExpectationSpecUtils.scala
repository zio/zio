/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

package zio.test.mock

import zio.duration.Duration
import zio.test.environment.Live
import zio.test.{ assertM, testM, Assertion }
import zio.{ Has, IO, UIO, ZIO, ZLayer }

object ExpectationSpecUtils {

  private[mock] def testSpec[E, A](name: String)(
    mock: ZLayer.NoDeps[Nothing, Has[Module]],
    app: ZIO[Has[Module], E, A],
    check: Assertion[A]
  ) = testM(name) {
    val result = mock.build.use[Any, E, A](app.provide _)
    assertM(result)(check)
  }

  private[mock] def testSpecTimeboxed[E, A](name: String)(duration: Duration)(
    mock: ZLayer.NoDeps[Nothing, Has[Module]],
    app: ZIO[Has[Module], E, A],
    check: Assertion[Option[A]]
  ) = testM(name) {
    val result =
      Live.live {
        mock.build
          .use(app.provide _)
          .timeout(duration)
      }

    assertM(result)(check)
  }

  private[mock] def testSpecDied[E, A](name: String)(
    mock: ZLayer.NoDeps[Nothing, Has[Module]],
    app: ZIO[Has[Module], E, A],
    check: Assertion[Throwable]
  ) = testM(name) {
    val result =
      mock.build
        .use(app.provide _)
        .orElse(ZIO.unit)
        .absorb
        .flip

    assertM(result)(check)
  }

  val intTuple22 = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)

  trait Module {
    val static: IO[String, String]
    def zeroParams: IO[String, String]
    def zeroParamsWithParens(): IO[String, String]
    def singleParam(a: Int): IO[String, String]
    def manyParams(a: Int, b: String, c: Long): IO[String, String]
    def manyParamLists(a: Int)(b: String)(c: Long): IO[String, String]
    def command(a: Int): IO[Unit, Unit]
    def looped(a: Int): IO[Nothing, Nothing]
    def overloaded(n: Int): IO[String, String]
    def overloaded(n: Long): IO[String, String]
    def maxParams(
      a: Int,
      b: Int,
      c: Int,
      d: Int,
      e: Int,
      f: Int,
      g: Int,
      h: Int,
      i: Int,
      j: Int,
      k: Int,
      l: Int,
      m: Int,
      n: Int,
      o: Int,
      p: Int,
      q: Int,
      r: Int,
      s: Int,
      t: Int,
      u: Int,
      v: Int
    ): IO[String, String]
  }

  object Module {

    object static               extends Method[Module, Unit, String]
    object zeroParams           extends Method[Module, Unit, String]
    object zeroParamsWithParens extends Method[Module, Unit, String]
    object singleParam          extends Method[Module, Int, String]
    object manyParams           extends Method[Module, (Int, String, Long), String]
    object manyParamLists       extends Method[Module, (Int, String, Long), String]
    object command              extends Method[Module, Int, Unit]
    object looped               extends Method[Module, Int, Nothing]
    object overloaded {
      object _0 extends Method[Module, Int, String]
      object _1 extends Method[Module, Long, String]
    }
    object maxParams
        extends Method[
          Module,
          (
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int,
            Int
          ),
          String
        ]

    object > {
      val static                                     = ZIO.accessM[Has[Module]](_.get.static)
      def zeroParams                                 = ZIO.accessM[Has[Module]](_.get.zeroParams)
      def zeroParamsWithParens()                     = ZIO.accessM[Has[Module]](_.get.zeroParamsWithParens())
      def singleParam(a: Int)                        = ZIO.accessM[Has[Module]](_.get.singleParam(a))
      def manyParams(a: Int, b: String, c: Long)     = ZIO.accessM[Has[Module]](_.get.manyParams(a, b, c))
      def manyParamLists(a: Int)(b: String)(c: Long) = ZIO.accessM[Has[Module]](_.get.manyParamLists(a)(b)(c))
      def command(a: Int)                            = ZIO.accessM[Has[Module]](_.get.command(a))
      def looped(a: Int)                             = ZIO.accessM[Has[Module]](_.get.looped(a))
      def overloaded(n: Int)                         = ZIO.accessM[Has[Module]](_.get.overloaded(n))
      def overloaded(n: Long)                        = ZIO.accessM[Has[Module]](_.get.overloaded(n))
      def maxParams(
        a: Int,
        b: Int,
        c: Int,
        d: Int,
        e: Int,
        f: Int,
        g: Int,
        h: Int,
        i: Int,
        j: Int,
        k: Int,
        l: Int,
        m: Int,
        n: Int,
        o: Int,
        p: Int,
        q: Int,
        r: Int,
        s: Int,
        t: Int,
        u: Int,
        v: Int
      ) = ZIO.accessM[Has[Module]](_.get.maxParams(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v))
    }

    implicit val mockableModule: Mockable[Module] = (mock: Mock) =>
      Has(new Module {
        val static: IO[String, String]                                     = mock(Module.static)
        def zeroParams: IO[String, String]                                 = mock(Module.zeroParams)
        def zeroParamsWithParens(): IO[String, String]                     = mock(Module.zeroParamsWithParens)
        def singleParam(a: Int): IO[String, String]                        = mock(Module.singleParam, a)
        def manyParams(a: Int, b: String, c: Long): IO[String, String]     = mock(Module.manyParams, (a, b, c))
        def manyParamLists(a: Int)(b: String)(c: Long): IO[String, String] = mock(Module.manyParamLists, a, b, c)
        def command(a: Int): IO[Unit, Unit]                                = mock(Module.command, a)
        def looped(a: Int): UIO[Nothing]                                   = mock(Module.looped, a)
        def overloaded(n: Int): IO[String, String]                         = mock(Module.overloaded._0, n)
        def overloaded(n: Long): IO[String, String]                        = mock(Module.overloaded._1, n)
        def maxParams(
          a: Int,
          b: Int,
          c: Int,
          d: Int,
          e: Int,
          f: Int,
          g: Int,
          h: Int,
          i: Int,
          j: Int,
          k: Int,
          l: Int,
          m: Int,
          n: Int,
          o: Int,
          p: Int,
          q: Int,
          r: Int,
          s: Int,
          t: Int,
          u: Int,
          v: Int
        ): IO[String, String] =
          mock(Module.maxParams, (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v))
      })
  }
}
