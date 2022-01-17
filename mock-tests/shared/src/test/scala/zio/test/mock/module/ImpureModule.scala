package zio.mock.module

import com.github.ghik.silencer.silent
import zio.{Tag, URIO, ZIO}

/**
 * Example of impure module used for testing ZIO Mock framework.
 */
trait ImpureModule {
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

object ImpureModule {
  def zeroParams: URIO[ImpureModule, String] = ZIO.service[ImpureModule].map(_.zeroParams)
  def zeroParamsWithParens(): URIO[ImpureModule, String] =
    ZIO.service[ImpureModule].map(_.zeroParamsWithParens())
  def singleParam(a: Int): URIO[ImpureModule, String] = ZIO.service[ImpureModule].map(_.singleParam(a))
  def manyParams(a: Int, b: String, c: Long): URIO[ImpureModule, String] =
    ZIO.service[ImpureModule].map(_.manyParams(a, b, c))
  def manyParamLists(a: Int)(b: String)(c: Long): URIO[ImpureModule, String] =
    ZIO.service[ImpureModule].map(_.manyParamLists(a)(b)(c))
  def command: URIO[ImpureModule, Unit] = ZIO.service[ImpureModule].map(_.command)
  def parameterizedCommand(a: Int): URIO[ImpureModule, Unit] =
    ZIO.service[ImpureModule].map(_.parameterizedCommand(a))
  def overloaded(n: Int): URIO[ImpureModule, String]  = ZIO.service[ImpureModule].map(_.overloaded(n))
  def overloaded(n: Long): URIO[ImpureModule, String] = ZIO.service[ImpureModule].map(_.overloaded(n))
  def polyInput[I: Tag](v: I): URIO[ImpureModule, String] =
    ZIO.service[ImpureModule].map(_.polyInput(v))
  def polyError[E <: Throwable: Tag](v: String): URIO[ImpureModule, String] =
    ZIO.service[ImpureModule].map(_.polyError(v))
  def polyOutput[A: Tag](v: String): URIO[ImpureModule, A] =
    ZIO.service[ImpureModule].map(_.polyOutput(v))
  def polyInputError[I: Tag, E <: Throwable: Tag](v: I): URIO[ImpureModule, String] =
    ZIO.service[ImpureModule].map(_.polyInputError[I, E](v))
  def polyInputOutput[I: Tag, A: Tag](v: I): URIO[ImpureModule, A] =
    ZIO.service[ImpureModule].map(_.polyInputOutput[I, A](v))
  def polyErrorOutput[E <: Throwable: Tag, A: Tag](v: String): URIO[ImpureModule, A] =
    ZIO.service[ImpureModule].map(_.polyErrorOutput[E, A](v))
  def polyInputErrorOutput[I: Tag, E <: Throwable: Tag, A: Tag](v: I): URIO[ImpureModule, A] =
    ZIO.service[ImpureModule].map(_.polyInputErrorOutput[I, E, A](v))
  def polyMixed[A: Tag]: URIO[ImpureModule, (A, String)] = ZIO.service[ImpureModule].map(_.polyMixed[A])
  def polyBounded[A <: AnyVal: Tag]: URIO[ImpureModule, A] =
    ZIO.service[ImpureModule].map(_.polyBounded[A])
  def varargs(a: Int, b: String*): URIO[ImpureModule, String] =
    ZIO.service[ImpureModule].map(_.varargs(a, b: _*))
  def curriedVarargs(a: Int, b: String*)(c: Long, d: Char*): URIO[ImpureModule, String] =
    ZIO.service[ImpureModule].map(_.curriedVarargs(a, b: _*)(c, d: _*))
  def byName(a: => Int): URIO[ImpureModule, String] = ZIO.service[ImpureModule].map(_.byName(a))
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
  ): URIO[ImpureModule, String] =
    ZIO.service[ImpureModule].map(_.maxParams(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v))
}
