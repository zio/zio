package zio.stream

import zio.test.{ Gen, Sized }
import zio.random.Random
import zio._

trait StreamUtils extends ChunkUtils {
  def streamGen[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, Stream[String, A]] =
    Gen.oneOf(impureStreamGen(a), pureStreamGen(a))

  def pureStreamGen[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, Stream[Nothing, A]] =
    for {
      n  <- Gen.int(0, 20)
      xs <- Gen.listOfN(n)(a).map(Stream.fromIterable)
    } yield xs

  def impureStreamGen[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, Stream[String, A]] =
    for {
      n  <- Gen.int(1, 20)
      i  <- Gen.int(0, n - 1)
      it <- Gen.listOfN(n)(a)
    } yield ZStream.unfoldM((i, it)) {
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
