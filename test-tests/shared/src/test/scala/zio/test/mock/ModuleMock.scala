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

import zio.{ Has, IO, UIO, ZLayer }

/**
 * Example module used for testing ZIO Mock framework.
 */
object ModuleMock {

  sealed trait Tag[I, A] extends Method[Module, I, A] {
    val mock = ModuleMock.mock
  }

  case object Static               extends Tag[Unit, String]
  case object ZeroParams           extends Tag[Unit, String]
  case object ZeroParamsWithParens extends Tag[Unit, String]
  case object SingleParam          extends Tag[Int, String]
  case object ManyParams           extends Tag[(Int, String, Long), String]
  case object ManyParamLists       extends Tag[(Int, String, Long), String]
  case object Command              extends Tag[Int, Unit]
  case object Looped               extends Tag[Int, Nothing]
  object Overloaded {
    case object _0 extends Tag[Int, String]
    case object _1 extends Tag[Long, String]
  }

  case object MaxParams extends Tag[T22[Int], String]

  private[test] lazy val mock: ZLayer[Has[Mock], Nothing, Module] =
    ZLayer.fromService(mock =>
      new Module.Service {
        val static: IO[String, String]                                     = mock(ModuleMock.Static)
        def zeroParams: IO[String, String]                                 = mock(ModuleMock.ZeroParams)
        def zeroParamsWithParens(): IO[String, String]                     = mock(ModuleMock.ZeroParamsWithParens)
        def singleParam(a: Int): IO[String, String]                        = mock(ModuleMock.SingleParam, a)
        def manyParams(a: Int, b: String, c: Long): IO[String, String]     = mock(ModuleMock.ManyParams, (a, b, c))
        def manyParamLists(a: Int)(b: String)(c: Long): IO[String, String] = mock(ModuleMock.ManyParamLists, a, b, c)
        def command(a: Int): IO[Unit, Unit]                                = mock(ModuleMock.Command, a)
        def looped(a: Int): UIO[Nothing]                                   = mock(ModuleMock.Looped, a)
        def overloaded(n: Int): IO[String, String]                         = mock(ModuleMock.Overloaded._0, n)
        def overloaded(n: Long): IO[String, String]                        = mock(ModuleMock.Overloaded._1, n)
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
          mock(ModuleMock.MaxParams, (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v))
      }
    )
}
