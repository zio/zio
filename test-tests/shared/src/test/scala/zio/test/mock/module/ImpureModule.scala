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

import zio.{ Tagged, ZIO }

/**
 * Example of impure module used for testing ZIO Mock framework.
 */
object ImpureModule {

  trait Service {

    def zeroParams: String
    def zeroParamsWithParens(): String
    def singleParam(a: Int): String
    def manyParams(a: Int, b: String, c: Long): String
    def manyParamLists(a: Int)(b: String)(c: Long): String
    @silent("side-effecting nullary methods")
    def command: Unit
    def parameterizedCommand(a: Int): Unit
    def overloaded(n: Int): String
    def overloaded(n: Long): String
    def polyInput[I: Tagged](v: I): String
    def polyError[E <: Throwable: Tagged](v: String): String
    def polyOutput[A: Tagged](v: String): A
    def polyInputError[I: Tagged, E <: Throwable: Tagged](v: I): String
    def polyInputOutput[I: Tagged, A: Tagged](v: I): A
    def polyErrorOutput[E <: Throwable: Tagged, A: Tagged](v: String): A
    def polyInputErrorOutput[I: Tagged, E <: Throwable: Tagged, A: Tagged](v: I): A
    def polyMixed[A: Tagged]: (A, String)
    def polyBounded[A <: AnyVal: Tagged]: A
    def varargs(a: Int, b: String*): String
    def curriedVarargs(a: Int, b: String*)(c: Long, d: Char*): String
    def byName(a: => Int): String
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
    ): String
  }

  def zeroParams                                              = ZIO.access[ImpureModule](_.get.zeroParams)
  def zeroParamsWithParens()                                  = ZIO.access[ImpureModule](_.get.zeroParamsWithParens())
  def singleParam(a: Int)                                     = ZIO.access[ImpureModule](_.get.singleParam(a))
  def manyParams(a: Int, b: String, c: Long)                  = ZIO.access[ImpureModule](_.get.manyParams(a, b, c))
  def manyParamLists(a: Int)(b: String)(c: Long)              = ZIO.access[ImpureModule](_.get.manyParamLists(a)(b)(c))
  def command                                                 = ZIO.access[ImpureModule](_.get.command)
  def parameterizedCommand(a: Int)                            = ZIO.access[ImpureModule](_.get.parameterizedCommand(a))
  def overloaded(n: Int)                                      = ZIO.access[ImpureModule](_.get.overloaded(n))
  def overloaded(n: Long)                                     = ZIO.access[ImpureModule](_.get.overloaded(n))
  def polyInput[I: Tagged](v: I)                              = ZIO.access[ImpureModule](_.get.polyInput(v))
  def polyError[E <: Throwable: Tagged](v: String)            = ZIO.access[ImpureModule](_.get.polyError(v))
  def polyOutput[A: Tagged](v: String)                        = ZIO.access[ImpureModule](_.get.polyOutput(v))
  def polyInputError[I: Tagged, E <: Throwable: Tagged](v: I) = ZIO.access[ImpureModule](_.get.polyInputError[I, E](v))
  def polyInputOutput[I: Tagged, A: Tagged](v: I)             = ZIO.access[ImpureModule](_.get.polyInputOutput[I, A](v))
  def polyErrorOutput[E <: Throwable: Tagged, A: Tagged](v: String) =
    ZIO.access[ImpureModule](_.get.polyErrorOutput[E, A](v))
  def polyInputErrorOutput[I: Tagged, E <: Throwable: Tagged, A: Tagged](v: I) =
    ZIO.access[ImpureModule](_.get.polyInputErrorOutput[I, E, A](v))
  def polyMixed[A: Tagged]             = ZIO.access[ImpureModule](_.get.polyMixed[A])
  def polyBounded[A <: AnyVal: Tagged] = ZIO.access[ImpureModule](_.get.polyBounded[A])
  def varargs(a: Int, b: String*)      = ZIO.access[ImpureModule](_.get.varargs(a, b: _*))
  def curriedVarargs(a: Int, b: String*)(c: Long, d: Char*) =
    ZIO.access[ImpureModule](_.get.curriedVarargs(a, b: _*)(c, d: _*))
  def byName(a: => Int) = ZIO.access[ImpureModule](_.get.byName(a))
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
  ) = ZIO.access[ImpureModule](_.get.maxParams(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v))
}
