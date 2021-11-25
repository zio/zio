package zio.test.mock.module

import com.github.ghik.silencer.silent
import zio.{Tag, URIO, ZIO}

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
    def polyInput[I: Tag](v: I): String
    def polyError[E <: Throwable: Tag](v: String): String
    def polyOutput[A: Tag](v: String): A
    def polyInputError[I: Tag, E <: Throwable: Tag](v: I): String
    def polyInputOutput[I: Tag, A: Tag](v: I): A
    def polyErrorOutput[E <: Throwable: Tag, A: Tag](v: String): A
    def polyInputErrorOutput[I: Tag, E <: Throwable: Tag, A: Tag](v: I): A
    def polyMixed[A: Tag]: (A, String)
    def polyBounded[A <: AnyVal: Tag]: A
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

  def zeroParams: URIO[ImpureModule.Service, String] = ZIO.service[ImpureModule.Service].map(_.zeroParams)
  def zeroParamsWithParens(): URIO[ImpureModule.Service, String] =
    ZIO.service[ImpureModule.Service].map(_.zeroParamsWithParens())
  def singleParam(a: Int): URIO[ImpureModule.Service, String] = ZIO.service[ImpureModule.Service].map(_.singleParam(a))
  def manyParams(a: Int, b: String, c: Long): URIO[ImpureModule.Service, String] =
    ZIO.service[ImpureModule.Service].map(_.manyParams(a, b, c))
  def manyParamLists(a: Int)(b: String)(c: Long): URIO[ImpureModule.Service, String] =
    ZIO.service[ImpureModule.Service].map(_.manyParamLists(a)(b)(c))
  def command: URIO[ImpureModule.Service, Unit] = ZIO.service[ImpureModule.Service].map(_.command)
  def parameterizedCommand(a: Int): URIO[ImpureModule.Service, Unit] =
    ZIO.service[ImpureModule.Service].map(_.parameterizedCommand(a))
  def overloaded(n: Int): URIO[ImpureModule.Service, String]  = ZIO.service[ImpureModule.Service].map(_.overloaded(n))
  def overloaded(n: Long): URIO[ImpureModule.Service, String] = ZIO.service[ImpureModule.Service].map(_.overloaded(n))
  def polyInput[I: Tag](v: I): URIO[ImpureModule.Service, String] =
    ZIO.service[ImpureModule.Service].map(_.polyInput(v))
  def polyError[E <: Throwable: Tag](v: String): URIO[ImpureModule.Service, String] =
    ZIO.service[ImpureModule.Service].map(_.polyError(v))
  def polyOutput[A: Tag](v: String): URIO[ImpureModule.Service, A] =
    ZIO.service[ImpureModule.Service].map(_.polyOutput(v))
  def polyInputError[I: Tag, E <: Throwable: Tag](v: I): URIO[ImpureModule.Service, String] =
    ZIO.service[ImpureModule.Service].map(_.polyInputError[I, E](v))
  def polyInputOutput[I: Tag, A: Tag](v: I): URIO[ImpureModule.Service, A] =
    ZIO.service[ImpureModule.Service].map(_.polyInputOutput[I, A](v))
  def polyErrorOutput[E <: Throwable: Tag, A: Tag](v: String): URIO[ImpureModule.Service, A] =
    ZIO.service[ImpureModule.Service].map(_.polyErrorOutput[E, A](v))
  def polyInputErrorOutput[I: Tag, E <: Throwable: Tag, A: Tag](v: I): URIO[ImpureModule.Service, A] =
    ZIO.service[ImpureModule.Service].map(_.polyInputErrorOutput[I, E, A](v))
  def polyMixed[A: Tag]: URIO[ImpureModule.Service, (A, String)] = ZIO.service[ImpureModule.Service].map(_.polyMixed[A])
  def polyBounded[A <: AnyVal: Tag]: URIO[ImpureModule.Service, A] =
    ZIO.service[ImpureModule.Service].map(_.polyBounded[A])
  def varargs(a: Int, b: String*): URIO[ImpureModule.Service, String] =
    ZIO.service[ImpureModule.Service].map(_.varargs(a, b: _*))
  def curriedVarargs(a: Int, b: String*)(c: Long, d: Char*): URIO[ImpureModule.Service, String] =
    ZIO.service[ImpureModule.Service].map(_.curriedVarargs(a, b: _*)(c, d: _*))
  def byName(a: => Int): URIO[ImpureModule.Service, String] = ZIO.service[ImpureModule.Service].map(_.byName(a))
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
  ): URIO[ImpureModule.Service, String] =
    ZIO.service[ImpureModule.Service].map(_.maxParams(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v))
}
