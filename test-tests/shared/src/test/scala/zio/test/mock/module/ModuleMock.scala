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

package zio.test.mock.module

import zio.test.mock.{ Method, Proxy }
import zio.{ Has, IO, Tagged, UIO, URLayer, ZLayer }

/**
 * Example module used for testing ZIO Mock framework.
 */
object ModuleMock {

  case object Static               extends Method[Module, Unit, String, String](compose)
  case object ZeroParams           extends Method[Module, Unit, String, String](compose)
  case object ZeroParamsWithParens extends Method[Module, Unit, String, String](compose)
  case object SingleParam          extends Method[Module, Int, String, String](compose)
  case object ManyParams           extends Method[Module, (Int, String, Long), String, String](compose)
  case object ManyParamLists       extends Method[Module, (Int, String, Long), String, String](compose)
  case object Command              extends Method[Module, Unit, Unit, Unit](compose)
  case object ParameterizedCommand extends Method[Module, Int, Unit, Unit](compose)
  case object Looped               extends Method[Module, Int, Nothing, Nothing](compose)
  case object PolyInput            extends Method.Poly.Input[Module, String, String](compose)
  case object PolyError            extends Method.Poly.Error[Module, String, String](compose)
  case object PolyOutput           extends Method.Poly.Output[Module, String, String](compose)
  case object PolyInputError       extends Method.Poly.InputError[Module, String](compose)
  case object PolyInputOutput      extends Method.Poly.InputOutput[Module, String](compose)
  case object PolyErrorOutput      extends Method.Poly.ErrorOutput[Module, String](compose)
  case object PolyInputErrorOutput extends Method.Poly.InputErrorOutput[Module](compose)
  case object PolyMixed            extends Method.Poly.Output[Module, Unit, String](compose)
  case object PolyBounded          extends Method.Poly.Output[Module, Unit, String](compose)

  object Overloaded {
    case object _0 extends Method[Module, Int, String, String](compose)
    case object _1 extends Method[Module, Long, String, String](compose)
  }

  case object MaxParams extends Method[Module, T22[Int], String, String](compose)

  private[test] lazy val compose: URLayer[Has[Proxy], Module] =
    ZLayer.fromService(proxy =>
      new Module.Service {
        val static: IO[String, String]                                     = proxy(Static)
        def zeroParams: IO[String, String]                                 = proxy(ZeroParams)
        def zeroParamsWithParens(): IO[String, String]                     = proxy(ZeroParamsWithParens)
        def singleParam(a: Int): IO[String, String]                        = proxy(SingleParam, a)
        def manyParams(a: Int, b: String, c: Long): IO[String, String]     = proxy(ManyParams, (a, b, c))
        def manyParamLists(a: Int)(b: String)(c: Long): IO[String, String] = proxy(ManyParamLists, a, b, c)
        def command: IO[Unit, Unit]                                        = proxy(Command)
        def parameterizedCommand(a: Int): IO[Unit, Unit]                   = proxy(ParameterizedCommand, a)
        def looped(a: Int): UIO[Nothing]                                   = proxy(Looped, a)
        def overloaded(n: Int): IO[String, String]                         = proxy(Overloaded._0, n)
        def overloaded(n: Long): IO[String, String]                        = proxy(Overloaded._1, n)
        def polyInput[I: Tagged](v: I): IO[String, String]                 = proxy(PolyInput.of[I], v)
        def polyError[E: Tagged](v: String): IO[E, String]                 = proxy(PolyError.of[E], v)
        def polyOutput[A: Tagged](v: String): IO[String, A]                = proxy(PolyOutput.of[A], v)
        def polyInputError[I: Tagged, E: Tagged](v: I): IO[E, String]      = proxy(PolyInputError.of[I, E], v)
        def polyInputOutput[I: Tagged, A: Tagged](v: I): IO[String, A]     = proxy(PolyInputOutput.of[I, A], v)
        def polyErrorOutput[E: Tagged, A: Tagged](v: String): IO[E, A]     = proxy(PolyErrorOutput.of[E, A], v)
        def polyInputErrorOutput[I: Tagged, E: Tagged, A: Tagged](v: I): IO[E, A] =
          proxy(PolyInputErrorOutput.of[I, E, A], v)
        def polyMixed[A: Tagged]: IO[String, (A, String)]   = proxy(PolyMixed.of[(A, String)])
        def polyBounded[A <: AnyVal: Tagged]: IO[String, A] = proxy(PolyBounded.of[A])
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
          proxy(MaxParams, (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v))
      }
    )
}
