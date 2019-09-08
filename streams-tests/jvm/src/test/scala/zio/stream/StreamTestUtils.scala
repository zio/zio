package zio.stream

import zio.test.{ Gen, Sized }
import zio.random.Random
import zio._

trait StreamUtils extends ChunkUtils {
  def streamGen[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, Stream[String, A]] =
    Gen.oneOf(impureStreamGen(a), pureStreamGen(a))

  def pureStreamGen[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, Stream[Nothing, A]] =
    Gen.listOf(a).map(Stream.fromIterable)

  def impureStreamGen[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, Stream[String, A]] =
    for {
      it <- Gen.listOf(a)
      n  <- Gen.int(0, it.size - 1)
    } yield ZStream.unfoldM((n, it)) {
      case (_, Nil) | (0, _) =>
        IO.fail("fail-case")
      case (n, head :: rest) => IO.succeed(Some((head, (n - 1, rest))))
    }
}

object StreamUtils extends StreamUtils with GenUtils {
  val streamOfBytes   = streamGen(Gen.anyByte)
  val streamOfInts    = streamGen(Gen.anyInt)
  val streamOfStrings = streamGen(Gen.anyString)

  val listOfInts = Gen.listOf(Gen.anyInt)

  val pureStreamOfInts    = pureStreamGen(Gen.anyInt)
  val pureStreamOfStrings = pureStreamGen(Gen.anyString)
}
