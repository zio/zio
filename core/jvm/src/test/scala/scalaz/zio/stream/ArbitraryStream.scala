package scalaz.zio.stream

import org.scalacheck.{Arbitrary, Gen}
import scalaz.zio.IO

import scala.reflect.ClassTag

object ArbitraryStream {

  implicit def arbStream[T: ClassTag](implicit a: Arbitrary[T]): Arbitrary[Stream[String, T]] =
    Arbitrary {
      val failingStream: Gen[Stream[String, T]] =
        for {
          it <- Arbitrary.arbitrary[List[T]]
          n <- Gen.choose(0, it.size)
        } yield Stream.unfoldM((n, it)) {
          case (_, Nil) | (0, _)=>
            IO.fail("fail-case")
          case (n, head :: rest) => IO.now(Some((head, (n - 1, rest))))
        }

      val succeedingStream: Gen[Stream[String, T]] =
        for (it <- Arbitrary.arbitrary[Iterable[T]]) yield Stream.fromIterable(it)

      Gen.oneOf(failingStream, succeedingStream)
    }
}
