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

import com.github.ghik.silencer.silent
import zio.test.mock.{ Mock, Proxy }
import zio.{ Has, IO, Tag, UIO, URLayer, ZLayer }

/**
 * Example pure module used for testing ZIO Mock framework.
 */
object PureModuleMock extends Mock[PureModule] {

  object Static               extends Effect[Unit, String, String]
  object ZeroParams           extends Effect[Unit, String, String]
  object ZeroParamsWithParens extends Effect[Unit, String, String]
  object SingleParam          extends Effect[Int, String, String]
  object ManyParams           extends Effect[(Int, String, Long), String, String]
  object ManyParamLists       extends Effect[(Int, String, Long), String, String]
  object Command              extends Effect[Unit, Unit, Unit]
  object ParameterizedCommand extends Effect[Int, Unit, Unit]
  object Looped               extends Effect[Int, Nothing, Nothing]
  object PolyInput            extends Poly.Effect.Input[String, String]
  object PolyError            extends Poly.Effect.Error[String, String]
  object PolyOutput           extends Poly.Effect.Output[String, String]
  object PolyInputError       extends Poly.Effect.InputError[String]
  object PolyInputOutput      extends Poly.Effect.InputOutput[String]
  object PolyErrorOutput      extends Poly.Effect.ErrorOutput[String]
  object PolyInputErrorOutput extends Poly.Effect.InputErrorOutput
  object PolyMixed            extends Poly.Effect.Output[Unit, String]
  object PolyBounded          extends Poly.Effect.Output[Unit, String]
  object Varargs              extends Effect[(Int, Seq[String]), String, String]
  object CurriedVarargs       extends Effect[(Int, Seq[String], Long, Seq[Char]), String, String]
  object ByName               extends Effect[Int, String, String]

  object Overloaded {
    object _0 extends Effect[Int, String, String]
    object _1 extends Effect[Long, String, String]
  }

  object MaxParams extends Effect[T22[Int], String, String]

  @silent("is never used")
  val compose: URLayer[Has[Proxy], PureModule] =
    ZLayer.fromServiceM { proxy =>
      withRuntime.map { rts =>
        new PureModule.Service {
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
          def polyInput[I: Tag](v: I): IO[String, String]                    = proxy(PolyInput.of[I], v)
          def polyError[E: Tag](v: String): IO[E, String]                    = proxy(PolyError.of[E], v)
          def polyOutput[A: Tag](v: String): IO[String, A]                   = proxy(PolyOutput.of[A], v)
          def polyInputError[I: Tag, E: Tag](v: I): IO[E, String]            = proxy(PolyInputError.of[I, E], v)
          def polyInputOutput[I: Tag, A: Tag](v: I): IO[String, A]           = proxy(PolyInputOutput.of[I, A], v)
          def polyErrorOutput[E: Tag, A: Tag](v: String): IO[E, A]           = proxy(PolyErrorOutput.of[E, A], v)
          def polyInputErrorOutput[I: Tag, E: Tag, A: Tag](v: I): IO[E, A] =
            proxy(PolyInputErrorOutput.of[I, E, A], v)
          def polyMixed[A: Tag]: IO[String, (A, String)]      = proxy(PolyMixed.of[(A, String)])
          def polyBounded[A <: AnyVal: Tag]: IO[String, A]    = proxy(PolyBounded.of[A])
          def varargs(a: Int, b: String*): IO[String, String] = proxy(Varargs, (a, b))
          def curriedVarargs(a: Int, b: String*)(c: Long, d: Char*): IO[String, String] =
            proxy(CurriedVarargs, (a, b, c, d))
          def byName(a: => Int): IO[String, String] = proxy(ByName, a)
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
      }
    }
}
