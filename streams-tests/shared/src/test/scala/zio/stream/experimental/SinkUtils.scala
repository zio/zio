package zio.stream.experimental

import zio.test.Assertion.equalTo
import zio.test.{ assert, Assertion, TestResult }
import zio.{ IO, ZIO }

object SinkUtils {

  def findSink[A](a: A): ZSink[Any, Unit, A, A] =
    ZSink.fold[A, Option[A]](None)(_.isEmpty)((_, v) => if (a == v) Some(a) else None).mapM {
      case Some(v) => IO.succeed(v)
      case None    => IO.fail(())
    }

  def sinkRaceLaw[E, A](
    stream: ZStream[Any, Nothing, A],
    s1: ZSink[Any, Unit, A, A],
    s2: ZSink[Any, Unit, A, A]
  ): ZIO[Any, Nothing, TestResult] =
    for {
      r1 <- stream.run(s1).either
      r2 <- stream.run(s2).either
      r  <- stream.run(s1.raceBoth(s2)).either
    } yield {
      r match {
        case Left(_) => assert(r1)(Assertion.isLeft) && assert(r2)(Assertion.isLeft)
        case Right(v) => {
          v match {
            case Left(w)  => assert(Right(w))(equalTo(r1))
            case Right(w) => assert(Right(w))(equalTo(r2))
          }
        }
      }
    }

}
