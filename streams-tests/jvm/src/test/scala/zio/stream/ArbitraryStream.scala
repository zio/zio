package zio.stream

import org.scalacheck.{ Arbitrary, Gen }
import zio.IO

import scala.reflect.ClassTag

object ArbitraryStream {

  implicit def arbStream[T: ClassTag: Arbitrary]: Arbitrary[Stream[String, T]] =
    Arbitrary {
      val failingStream: Gen[Stream[String, T]] = genFailingStream

      val succeedingStream: Gen[Stream[String, T]] = genPureStream

      val failingStreamEffect: Gen[StreamEffect[Any, String, T]] = genFailingStreamEffect

      val succeedingStreamEffect: Gen[StreamEffect[Any, String, T]] = genPureStreamEffect

      Gen.oneOf(failingStream, succeedingStream, failingStreamEffect, succeedingStreamEffect)
    }

  def genPureStream[T: ClassTag: Arbitrary]: Gen[Stream[Nothing, T]] =
    Arbitrary.arbitrary[Iterable[T]].map(Stream.fromIterable)

  def genFailingStream[T: ClassTag: Arbitrary]: Gen[Stream[String, T]] =
    for {
      it <- Arbitrary.arbitrary[List[T]]
      n  <- Gen.choose(0, it.size)
    } yield ZStream.unfoldM((n, it)) {
      case (_, Nil) | (0, _) =>
        IO.fail("fail-case")
      case (n, head :: rest) => IO.succeed(Some((head, (n - 1, rest))))
    }

  def genPureStreamEffect[T: ClassTag: Arbitrary]: Gen[StreamEffect[Any, Nothing, T]] =
    Arbitrary.arbitrary[Iterable[T]].map(StreamEffect.fromIterable)

  def genFailingStreamEffect[T: ClassTag: Arbitrary]: Gen[StreamEffect[Any, String, T]] =
    for {
      it <- Arbitrary.arbitrary[List[T]]
      n  <- Gen.choose(0, it.size)
    } yield StreamEffect.unfold((n, it)) {
      case (_, Nil) | (0, _) => None
      case (n, head :: rest) => Some((head, (n - 1, rest)))
    }
}
