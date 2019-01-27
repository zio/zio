package scalaz.zio.stream

import org.scalacheck.{ Arbitrary, Gen }
import scalaz.zio.IO

import scala.reflect.ClassTag

object ArbitraryStream {

  implicit def arbStream[T: ClassTag: Arbitrary]: Arbitrary[Stream[Any, String, T]] =
    Arbitrary {
      val failingStream: Gen[Stream[Any, String, T]] = genFailingStream

      val succeedingStream: Gen[Stream[Any, String, T]] = genPureStream

      Gen.oneOf(failingStream, succeedingStream)
    }

  def genPureStream[T: ClassTag: Arbitrary]: Gen[StreamPure[Any, T]] =
    Arbitrary.arbitrary[Iterable[T]].map(StreamPure.fromIterable)

  def genSucceededStream[T: ClassTag: Arbitrary]: Gen[Stream[Any, Nothing, T]] =
    Arbitrary.arbitrary[List[T]].map { xs =>
      Stream.unfoldM[Any, List[T], Nothing, T](xs) {
        case head :: tail => IO.succeed(Some(head -> tail))
        case _            => IO.succeed(None)
      }
    }

  def genFailingStream[T: ClassTag: Arbitrary]: Gen[Stream[Any, String, T]] =
    for {
      it <- Arbitrary.arbitrary[List[T]]
      n  <- Gen.choose(0, it.size)
    } yield
      Stream.unfoldM((n, it)) {
        case (_, Nil) | (0, _) =>
          IO.fail("fail-case")
        case (n, head :: rest) => IO.succeed(Some((head, (n - 1, rest))))
      }
}
