package zio.stream

import zio.test.Assertion.equalTo
import zio.test.{Assertion, TestResult, assert}
import zio.{UIO, ZIO}

object SinkUtils {

  def findSink[A](a: A): ZSink[Any, Unit, A, A, A] =
    ZSink
      .fold[A, Option[A]](None)(_.isEmpty)((_, v) => if (a == v) Some(a) else None)
      .mapZIO {
        case Some(v) => ZIO.succeedNow(v)
        case None    => ZIO.fail(())
      }

  def sinkRaceLaw[E, A, L](
    stream: ZStream[Any, Nothing, A],
    s1: ZSink[Any, E, A, L, A],
    s2: ZSink[Any, E, A, L, A]
  ): UIO[TestResult] =
    for {
      r1 <- stream.run(s1).either
      r2 <- stream.run(s2).either
      r  <- stream.run(s1.raceBoth(s2)).either
    } yield {
      r match {
        case Left(_) => assert(r1)(Assertion.isLeft) || assert(r2)(Assertion.isLeft)
        case Right(v) => {
          v match {
            case Left(w)  => assert(Right(w))(equalTo(r1))
            case Right(w) => assert(Right(w))(equalTo(r2))
          }
        }
      }
    }

  def zipParLaw[A, B, C, L, E](
    s: ZStream[Any, Nothing, A],
    sink1: ZSink[Any, E, A, A, B],
    sink2: ZSink[Any, E, A, A, C]
  ): UIO[TestResult] =
    for {
      zb  <- s.run(sink1).either
      zc  <- s.run(sink2).either
      zbc <- s.run(sink1.zipPar(sink2)).either
    } yield {
      zbc match {
        case Left(e)       => assert(zb)(equalTo(Left(e))) || assert(zc)(equalTo(Left(e)))
        case Right((b, c)) => assert(zb)(equalTo(Right(b))) && assert(zc)(equalTo(Right(c)))
      }
    }
}
