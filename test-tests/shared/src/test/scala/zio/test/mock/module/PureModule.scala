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

import zio.{ IO, Tagged, ZIO }

import scala.reflect.ClassTag

/**
 * Example module used for testing ZIO Mock framework.
 */
object PureModule {

  // Workaround for izumi.reflect.Tag any-kindedness (probably?)  causing `Any` to be inferred on dotty
  final class NotAnyKind[A](private val dummy: Boolean = false) extends AnyVal
  object NotAnyKind {
    // for some reason a ClassTag search is required, an arbitrary type like Set[A] doesn't fix inference
    implicit def notAnyKind[A](implicit maybeClassTag: ClassTag[A] = null): NotAnyKind[A] =
      new NotAnyKind[A]
  }

  trait Service {

    val static: IO[String, String]
    def zeroParams: IO[String, String]
    def zeroParamsWithParens(): IO[String, String]
    def singleParam(a: Int): IO[String, String]
    def manyParams(a: Int, b: String, c: Long): IO[String, String]
    def manyParamLists(a: Int)(b: String)(c: Long): IO[String, String]
    def command: IO[Unit, Unit]
    def parameterizedCommand(a: Int): IO[Unit, Unit]
    def looped(a: Int): IO[Nothing, Nothing]
    def overloaded(n: Int): IO[String, String]
    def overloaded(n: Long): IO[String, String]
    def polyInput[I: Tagged](v: I): IO[String, String]
    def polyError[E: Tagged](v: String): IO[E, String]
    def polyOutput[A: Tagged](v: String): IO[String, A]
    def polyInputError[I: Tagged, E: Tagged](v: I): IO[E, String]
    def polyInputOutput[I: Tagged, A: Tagged](v: I): IO[String, A]
    def polyErrorOutput[E: Tagged, A: Tagged](v: String): IO[E, A]
    def polyInputErrorOutput[I: Tagged, E: Tagged, A: Tagged](v: I): IO[E, A]
    def polyMixed[A: Tagged]: IO[String, (A, String)]
    def polyBounded[A <: AnyVal: Tagged]: IO[String, A]
    def varargs(a: Int, b: String*): IO[String, String]
    def curriedVarargs(a: Int, b: String*)(c: Long, d: Char*): IO[String, String]
    def byName(a: => Int): IO[String, String]
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

  val static: ZIO[PureModule, String, String]                 = ZIO.accessM[PureModule](_.get.static)
  def zeroParams: ZIO[PureModule, String, String]             = ZIO.accessM[PureModule](_.get.zeroParams)
  def zeroParamsWithParens(): ZIO[PureModule, String, String] = ZIO.accessM[PureModule](_.get.zeroParamsWithParens())
  def singleParam(a: Int): ZIO[PureModule, String, String]    = ZIO.accessM[PureModule](_.get.singleParam(a))
  def manyParams(a: Int, b: String, c: Long): ZIO[PureModule, String, String] =
    ZIO.accessM[PureModule](_.get.manyParams(a, b, c))
  def manyParamLists(a: Int)(b: String)(c: Long): ZIO[PureModule, String, String] =
    ZIO.accessM[PureModule](_.get.manyParamLists(a)(b)(c))
  def command: ZIO[PureModule, Unit, Unit]                      = ZIO.accessM[PureModule](_.get.command)
  def parameterizedCommand(a: Int): ZIO[PureModule, Unit, Unit] = ZIO.accessM[PureModule](_.get.parameterizedCommand(a))
  def looped(a: Int): ZIO[PureModule, Nothing, Nothing]         = ZIO.accessM[PureModule](_.get.looped(a))
  def overloaded(n: Int): ZIO[PureModule, String, String]       = ZIO.accessM[PureModule](_.get.overloaded(n))
  def overloaded(n: Long): ZIO[PureModule, String, String]      = ZIO.accessM[PureModule](_.get.overloaded(n))
  def polyInput[I: NotAnyKind: Tagged](v: I): ZIO[PureModule, String, String] =
    ZIO.accessM[PureModule](_.get.polyInput[I](v))
  def polyError[E: NotAnyKind: Tagged](v: String): ZIO[PureModule, E, String] =
    ZIO.accessM[PureModule](_.get.polyError[E](v))
  def polyOutput[A: NotAnyKind: Tagged](v: String): ZIO[PureModule, String, A] =
    ZIO.accessM[PureModule](_.get.polyOutput[A](v))
  def polyInputError[I: NotAnyKind: Tagged, E: NotAnyKind: Tagged](v: I): ZIO[PureModule, E, String] =
    ZIO.accessM[PureModule](_.get.polyInputError[I, E](v))
  def polyInputOutput[I: NotAnyKind: Tagged, A: NotAnyKind: Tagged](v: I): ZIO[PureModule, String, A] =
    ZIO.accessM[PureModule](_.get.polyInputOutput[I, A](v))
  def polyErrorOutput[E: NotAnyKind: Tagged, A: NotAnyKind: Tagged](v: String): ZIO[PureModule, E, A] =
    ZIO.accessM[PureModule](_.get.polyErrorOutput[E, A](v))
  def polyInputErrorOutput[I: NotAnyKind: Tagged, E: NotAnyKind: Tagged, A: NotAnyKind: Tagged](
    v: I
  ): ZIO[PureModule, E, A] =
    ZIO.accessM[PureModule](_.get.polyInputErrorOutput[I, E, A](v))
  def polyMixed[A: NotAnyKind: Tagged]: ZIO[PureModule, String, (A, String)] =
    ZIO.accessM[PureModule](_.get.polyMixed[A])
  def polyBounded[A <: AnyVal: NotAnyKind: Tagged]: ZIO[PureModule, String, A] =
    ZIO.accessM[PureModule](_.get.polyBounded[A])
  def varargs(a: Int, b: String*): ZIO[PureModule, String, String] = ZIO.accessM[PureModule](_.get.varargs(a, b: _*))
  def curriedVarargs(a: Int, b: String*)(c: Long, d: Char*): ZIO[PureModule, String, String] =
    ZIO.accessM[PureModule](_.get.curriedVarargs(a, b: _*)(c, d: _*))
  def byName(a: => Int): ZIO[PureModule, String, String] = ZIO.accessM[PureModule](_.get.byName(a))
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
  ): ZIO[PureModule, String, String] =
    ZIO.accessM[PureModule](_.get.maxParams(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v))
}
