package scalaz.zio.stream

import org.scalacheck.{ Arbitrary, Gen }
import scalaz.zio.IO

import scala.reflect.ClassTag

object ArbitraryStream {

  implicit def arbStream[T: ClassTag: Arbitrary]: Arbitrary[Stream[String, T]] =
    Arbitrary {
      val failingStream: Gen[Stream[String, T]] = genFailingStream

      val succeedingStream: Gen[Stream[String, T]] = genPureStream

      Gen.oneOf(failingStream, succeedingStream)
    }

  def genPureStream[T: ClassTag: Arbitrary]: Gen[StreamPure[T]] =
    Arbitrary.arbitrary[Iterable[T]].map(StreamPure.fromIterable)

  def genSucceededStream[T: ClassTag: Arbitrary]: Gen[Stream[Nothing, T]] =
    Arbitrary.arbitrary[List[T]].map { xs =>
      Stream.unfoldM[List[T], Nothing, T](xs) {
        case head :: tail => IO.now(Some(head -> tail))
        case _            => IO.now(None)
      }
    }

  def genFailingStream[T: ClassTag: Arbitrary]: Gen[Stream[String, T]] =
    for {
      it <- Arbitrary.arbitrary[List[T]]
      n  <- Gen.choose(0, it.size)
    } yield
      Stream.unfoldM((n, it)) {
        case (_, Nil) | (0, _) =>
          IO.fail("fail-case")
        case (n, head :: rest) => IO.now(Some((head, (n - 1, rest))))
      }
}
