package zio.stream

import zio.test.Assertion.equalTo
import zio.test.{ assert, Assertion, TestResult }
import zio.{ IO, UIO }

object SinkUtils {

  def findSink[A](a: A): ZSink[Any, Unit, A, A, A] =
    ZSink.fold[A, Option[A]](None)(_.isEmpty)((_, v) => if (a == v) Some(a) else None).mapM {
      case Some(v) => IO.succeedNow(v)
      case None    => IO.fail(())
    }

  def sinkRaceLaw[E, A, B, C, L](
    stream: ZStream[Any, Nothing, A],
    s1: ZSink[Any, E, A, L, B],
    s2: ZSink[Any, E, A, L, C]
  ): UIO[TestResult] =
    for {
      x        <- stream.run(s1.either.exposeLeftover)
      (zb, l1) = x
      y        <- stream.run(s2.either.exposeLeftover)
      (zc, l2) = y
      z        <- stream.run(s1.raceBoth(s2).either.exposeLeftover)
      (zbc, l) = z
    } yield {
      val valueCheck = zbc match {
        case Left(_) => assert(zb)(Assertion.isLeft) && assert(zc)(Assertion.isLeft)
        case Right(v) => {
          v match {
            case Left(w)  => assert(Right(w))(equalTo(zb))
            case Right(w) => assert(Right(w))(equalTo(zc))
          }
        }
      }
      val leftoverCheck = assert(l1)(Assertion.endsWith(l)) || assert(l2)(Assertion.endsWith(l))
      valueCheck && leftoverCheck
    }

  def zipParLaw[A, B, C, L, E](
    s: ZStream[Any, Nothing, A],
    sink1: ZSink[Any, E, A, L, B],
    sink2: ZSink[Any, E, A, L, C]
  ): UIO[TestResult] =
    for {
      x        <- s.run(sink1.either.exposeLeftover)
      (zb, l1) = x
      y        <- s.run(sink2.either.exposeLeftover)
      (zc, l2) = y
      z        <- s.run(sink1.zipPar(sink2).either.exposeLeftover)
      (zbc, l) = z
    } yield {
      val valueCheck = zbc match {
        case Left(e)       => assert(zb)(equalTo(Left(e))) || assert(zc)(equalTo(Left(e)))
        case Right((b, c)) => assert(zb)(equalTo(Right(b))) && assert(zc)(equalTo(Right(c)))
      }
      val leftoverCheck = assert(l)(equalTo(l1)) || assert(l)(equalTo(l2))
      valueCheck && leftoverCheck
    }

}
