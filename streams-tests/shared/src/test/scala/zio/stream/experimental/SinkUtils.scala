package zio.stream.experimental

import zio.test.Assertion.equalTo
import zio.test.{Assertion, TestResult, assert}
import zio.{IO, UIO}

object SinkUtils {

  def findSink[A](a: A): ZSink[Any, Nothing, A, Unit, A, A] =
    ZSink
      .fold[Nothing, A, Option[A]](None)(_.isEmpty)((_, v) => if (a == v) Some(a) else None)
      .mapZIO {
        case Some(v) => IO.succeedNow(v)
        case None    => IO.fail(())
      }

  def sinkRaceLaw[E, A, L](
    stream: ZStream[Any, Nothing, A],
    s1: ZSink[Any, Nothing, A, E, L, A],
    s2: ZSink[Any, Nothing, A, E, L, A]
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
}
