package zio.mock.module

import zio.{IO, Tag, URIO, ZIO}

import scala.reflect.ClassTag

/**
 * Example module used for testing ZIO Mock framework.
 */
trait PureModule {

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
  def polyInput[I: Tag](v: I): IO[String, String]
  def polyError[E: Tag](v: String): IO[E, String]
  def polyOutput[A: Tag](v: String): IO[String, A]
  def polyInputError[I: Tag, E: Tag](v: I): IO[E, String]
  def polyInputOutput[I: Tag, A: Tag](v: I): IO[String, A]
  def polyErrorOutput[E: Tag, A: Tag](v: String): IO[E, A]
  def polyInputErrorOutput[I: Tag, E: Tag, A: Tag](v: I): IO[E, A]
  def polyMixed[A: Tag]: IO[String, (A, String)]
  def polyBounded[A <: AnyVal: Tag]: IO[String, A]
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

object PureModule {

  // Workaround for izumi.reflect.Tag any-kindness (probably?)  causing `Any` to be inferred on dotty
  final class NotAnyKind[A](private val dummy: Boolean = false) extends AnyVal
  object NotAnyKind {
    // for some reason a ClassTag search is required, an arbitrary type like Set[A] doesn't fix inference
    implicit def notAnyKind[A](implicit maybeClassTag: ClassTag[A] = null): NotAnyKind[A] =
      new NotAnyKind[A]
  }

  val static: ZIO[PureModule, String, String]     = ZIO.serviceWithZIO[PureModule](_.static)
  def zeroParams: ZIO[PureModule, String, String] = ZIO.serviceWithZIO[PureModule](_.zeroParams)
  def zeroParamsWithParens(): ZIO[PureModule, String, String] =
    ZIO.serviceWithZIO[PureModule](_.zeroParamsWithParens())
  def singleParam(a: Int): ZIO[PureModule, String, String] =
    ZIO.serviceWithZIO[PureModule](_.singleParam(a))
  def manyParams(a: Int, b: String, c: Long): ZIO[PureModule, String, String] =
    ZIO.serviceWithZIO[PureModule](_.manyParams(a, b, c))
  def manyParamLists(a: Int)(b: String)(c: Long): ZIO[PureModule, String, String] =
    ZIO.serviceWithZIO[PureModule](_.manyParamLists(a)(b)(c))
  def command: ZIO[PureModule, Unit, Unit] = ZIO.serviceWithZIO[PureModule](_.command)
  def parameterizedCommand(a: Int): ZIO[PureModule, Unit, Unit] =
    ZIO.serviceWithZIO[PureModule](_.parameterizedCommand(a))
  def looped(a: Int): URIO[PureModule, Nothing] = ZIO.serviceWithZIO[PureModule](_.looped(a))
  def overloaded(n: Int): ZIO[PureModule, String, String] =
    ZIO.serviceWithZIO[PureModule](_.overloaded(n))
  def overloaded(n: Long): ZIO[PureModule, String, String] =
    ZIO.serviceWithZIO[PureModule](_.overloaded(n))
  def polyInput[I: NotAnyKind: Tag](v: I): ZIO[PureModule, String, String] =
    ZIO.serviceWithZIO[PureModule](_.polyInput[I](v))
  def polyError[E: NotAnyKind: Tag](v: String): ZIO[PureModule, E, String] =
    ZIO.serviceWithZIO[PureModule](_.polyError[E](v))
  def polyOutput[A: NotAnyKind: Tag](v: String): ZIO[PureModule, String, A] =
    ZIO.serviceWithZIO[PureModule](_.polyOutput[A](v))
  def polyInputError[I: NotAnyKind: Tag, E: NotAnyKind: Tag](v: I): ZIO[PureModule, E, String] =
    ZIO.serviceWithZIO[PureModule](_.polyInputError[I, E](v))
  def polyInputOutput[I: NotAnyKind: Tag, A: NotAnyKind: Tag](v: I): ZIO[PureModule, String, A] =
    ZIO.serviceWithZIO[PureModule](_.polyInputOutput[I, A](v))
  def polyErrorOutput[E: NotAnyKind: Tag, A: NotAnyKind: Tag](v: String): ZIO[PureModule, E, A] =
    ZIO.serviceWithZIO[PureModule](_.polyErrorOutput[E, A](v))
  def polyInputErrorOutput[I: NotAnyKind: Tag, E: NotAnyKind: Tag, A: NotAnyKind: Tag](
    v: I
  ): ZIO[PureModule, E, A] =
    ZIO.serviceWithZIO[PureModule](_.polyInputErrorOutput[I, E, A](v))
  def polyMixed[A: NotAnyKind: Tag]: ZIO[PureModule, String, (A, String)] =
    ZIO.serviceWithZIO[PureModule](_.polyMixed[A])
  def polyBounded[A <: AnyVal: NotAnyKind: Tag]: ZIO[PureModule, String, A] =
    ZIO.serviceWithZIO[PureModule](_.polyBounded[A])
  def varargs(a: Int, b: String*): ZIO[PureModule, String, String] =
    ZIO.serviceWithZIO[PureModule](_.varargs(a, b: _*))
  def curriedVarargs(a: Int, b: String*)(c: Long, d: Char*): ZIO[PureModule, String, String] =
    ZIO.serviceWithZIO[PureModule](_.curriedVarargs(a, b: _*)(c, d: _*))
  def byName(a: => Int): ZIO[PureModule, String, String] = ZIO.serviceWithZIO[PureModule](_.byName(a))
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
    ZIO.serviceWithZIO[PureModule](
      _.maxParams(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v)
    )
}
