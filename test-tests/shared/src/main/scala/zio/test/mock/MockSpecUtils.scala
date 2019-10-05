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

package zio.test.mock

import zio.{ UIO, ZIO }

object MockSpecUtils {

  val intTuple22 = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)

  trait Module {
    val module: Module.Service[Any]
  }

  object Module {
    trait Service[R] {
      val static: ZIO[R, Nothing, String]
      def zeroParams: ZIO[R, Nothing, String]
      def zeroParamsWithParens(): ZIO[R, Nothing, String]
      def singleParam(a: Int): ZIO[R, Nothing, String]
      def manyParams(a: Int, b: String, c: Long): ZIO[R, Nothing, String]
      def manyParamLists(a: Int)(b: String)(c: Long): ZIO[R, Nothing, String]
      def withFailure(a: Int): ZIO[R, Unit, String]
      def command(a: Int): ZIO[R, Nothing, Unit]
      def never(a: Int): ZIO[R, Unit, Nothing]
      def overloaded(n: Int): ZIO[R, Nothing, String]
      def overloaded(n: Long): ZIO[R, Nothing, String]
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
      ): ZIO[R, Nothing, String]
    }
    object Service {
      object static               extends Method[Unit, String]
      object zeroParams           extends Method[Unit, String]
      object zeroParamsWithParens extends Method[Unit, String]
      object singleParam          extends Method[Int, String]
      object manyParams           extends Method[(Int, String, Long), String]
      object manyParamLists       extends Method[(Int, String, Long), String]
      object withFailure          extends Method[Int, String]
      object command              extends Method[Int, Unit]
      object never                extends Method[Int, Nothing]
      object overloaded {
        object _0 extends Method[Int, String]
        object _1 extends Method[Long, String]
      }
      object maxParams
          extends Method[
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
    }

    object > extends Service[Module] {
      val static = ZIO.accessM { env: Module =>
        env.module.static
      }
      def zeroParams                                 = ZIO.accessM(_.module.zeroParams)
      def zeroParamsWithParens()                     = ZIO.accessM(_.module.zeroParamsWithParens())
      def singleParam(a: Int)                        = ZIO.accessM(_.module.singleParam(a))
      def manyParams(a: Int, b: String, c: Long)     = ZIO.accessM(_.module.manyParams(a, b, c))
      def manyParamLists(a: Int)(b: String)(c: Long) = ZIO.accessM(_.module.manyParamLists(a)(b)(c))
      def withFailure(a: Int)                        = ZIO.accessM(_.module.withFailure(a))
      def command(a: Int)                            = ZIO.accessM(_.module.command(a))
      def never(a: Int)                              = ZIO.accessM(_.module.never(a))
      def overloaded(n: Int)                         = ZIO.accessM(_.module.overloaded(n))
      def overloaded(n: Long)                        = ZIO.accessM(_.module.overloaded(n))
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
      ) = ZIO.accessM(_.module.maxParams(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v))
    }

    implicit val mockable: Mockable[Module] = (mock: Mock) =>
      new Module {
        val module = new Service[Any] {
          val static: UIO[String]                                     = mock(Service.static)
          def zeroParams: UIO[String]                                 = mock(Service.zeroParams)
          def zeroParamsWithParens(): UIO[String]                     = mock(Service.zeroParamsWithParens)
          def singleParam(a: Int): UIO[String]                        = mock(Service.singleParam, a)
          def manyParams(a: Int, b: String, c: Long): UIO[String]     = mock(Service.manyParams, (a, b, c))
          def manyParamLists(a: Int)(b: String)(c: Long): UIO[String] = mock(Service.manyParamLists, a, b, c)
          def withFailure(a: Int): UIO[String]                        = mock(Service.withFailure, a)
          def command(a: Int): UIO[Unit]                              = mock(Service.command, a)
          def never(a: Int): UIO[Nothing]                             = mock(Service.never, a)
          def overloaded(n: Int): UIO[String]                         = mock(Service.overloaded._0, n)
          def overloaded(n: Long): UIO[String]                        = mock(Service.overloaded._1, n)
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
          ): UIO[String] = mock(Service.maxParams, (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v))
        }
      }
  }
}
