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
import zio.{ Has, Tagged, URLayer, ZLayer }

/**
 * Example module used for testing ZIO Mock framework.
 */
object ImpureModuleMock extends Mock[ImpureModule] {

  object ZeroParams           extends Method[Unit, Throwable, String]
  object ZeroParamsWithParens extends Method[Unit, Throwable, String]
  object SingleParam          extends Method[Int, Throwable, String]
  object ManyParams           extends Method[(Int, String, Long), Throwable, String]
  object ManyParamLists       extends Method[(Int, String, Long), Throwable, String]
  object Command              extends Method[Unit, Throwable, Unit]
  object ParameterizedCommand extends Method[Int, Throwable, Unit]
  object PolyInput            extends Poly.Method.Input[Throwable, String]
  object PolyError            extends Poly.Method.Error[String, String]
  object PolyOutput           extends Poly.Method.Output[String, Throwable]
  object PolyInputError       extends Poly.Method.InputError[String]
  object PolyInputOutput      extends Poly.Method.InputOutput[Throwable]
  object PolyErrorOutput      extends Poly.Method.ErrorOutput[String]
  object PolyInputErrorOutput extends Poly.Method.InputErrorOutput
  object PolyMixed            extends Poly.Method.Output[Unit, Throwable]
  object PolyBounded          extends Poly.Method.Output[Unit, Throwable]
  object Varargs              extends Method[(Int, Seq[String]), Throwable, String]
  object CurriedVarargs       extends Method[(Int, Seq[String], Long, Seq[Char]), Throwable, String]

  object Overloaded {
    object _0 extends Method[Int, Throwable, String]
    object _1 extends Method[Long, Throwable, String]
  }

  object MaxParams extends Method[T22[Int], Throwable, String]

  val compose: URLayer[Has[Proxy], ImpureModule] =
    ZLayer.fromServiceM { proxy =>
      withRuntime.map { rts =>
        new ImpureModule.Service {
          def zeroParams: String                                 = rts.unsafeRunTask(proxy(ZeroParams))
          def zeroParamsWithParens(): String                     = rts.unsafeRunTask(proxy(ZeroParamsWithParens))
          def singleParam(a: Int): String                        = rts.unsafeRunTask(proxy(SingleParam, a))
          def manyParams(a: Int, b: String, c: Long): String     = rts.unsafeRunTask(proxy(ManyParams, (a, b, c)))
          def manyParamLists(a: Int)(b: String)(c: Long): String = rts.unsafeRunTask(proxy(ManyParamLists, a, b, c))
          @silent("side-effecting nullary methods")
          def command: Unit                                        = rts.unsafeRunTask(proxy(Command))
          def parameterizedCommand(a: Int): Unit                   = rts.unsafeRunTask(proxy(ParameterizedCommand, a))
          def overloaded(n: Int): String                           = rts.unsafeRunTask(proxy(Overloaded._0, n))
          def overloaded(n: Long): String                          = rts.unsafeRunTask(proxy(Overloaded._1, n))
          def polyInput[I: Tagged](v: I): String                   = rts.unsafeRunTask(proxy(PolyInput.of[I], v))
          def polyError[E <: Throwable: Tagged](v: String): String = rts.unsafeRunTask(proxy(PolyError.of[E], v))
          def polyOutput[A: Tagged](v: String): A                  = rts.unsafeRunTask(proxy(PolyOutput.of[A], v))
          def polyInputError[I: Tagged, E <: Throwable: Tagged](v: I): String =
            rts.unsafeRunTask(proxy(PolyInputError.of[I, E], v))
          def polyInputOutput[I: Tagged, A: Tagged](v: I): A = rts.unsafeRunTask(proxy(PolyInputOutput.of[I, A], v))
          def polyErrorOutput[E <: Throwable: Tagged, A: Tagged](v: String): A =
            rts.unsafeRunTask(proxy(PolyErrorOutput.of[E, A], v))
          def polyInputErrorOutput[I: Tagged, E <: Throwable: Tagged, A: Tagged](v: I): A =
            rts.unsafeRunTask(proxy(PolyInputErrorOutput.of[I, E, A], v))
          def polyMixed[A: Tagged]: (A, String)   = rts.unsafeRunTask(proxy(PolyMixed.of[(A, String)]))
          def polyBounded[A <: AnyVal: Tagged]: A = rts.unsafeRunTask(proxy(PolyBounded.of[A]))
          def varargs(a: Int, b: String*): String = rts.unsafeRunTask(proxy(Varargs, (a, b)))
          def curriedVarargs(a: Int, b: String*)(c: Long, d: Char*): String =
            rts.unsafeRunTask(proxy(CurriedVarargs, (a, b, c, d)))
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
          ): String =
            rts.unsafeRunTask(proxy(MaxParams, (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v)))
        }
      }
    }
}
