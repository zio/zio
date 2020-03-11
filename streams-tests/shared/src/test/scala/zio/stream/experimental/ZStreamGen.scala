package zio.stream.experimental

import zio._
import zio.random.Random
import zio.stream.ChunkUtils._
import zio.test.{ Gen, Sized }

object ZStreamGen {
  def streamGen[R <: Random, A](a: Gen[R, A], max: Int): Gen[R with Sized, ZStream[Any, String, A]] =
    Gen.oneOf(failingStreamGen(a, max), pureStreamGen(a, max))

  def pureStreamGen[R <: Random, A](a: Gen[R, A], max: Int): Gen[R with Sized, ZStream[Any, Nothing, A]] =
    max match {
      case 0 => Gen.const(ZStream.empty)
      case n =>
        Gen.oneOf(
          Gen.const(ZStream.empty),
          Gen.int(1, n).flatMap(Gen.listOfN(_)(a)).map(ZStream.fromIterable(_))
        )
    }

  def failingStreamGen[R <: Random, A](a: Gen[R, A], max: Int): Gen[R with Sized, ZStream[Any, String, A]] =
    max match {
      case 0 => Gen.const(ZStream.fromEffect(IO.fail("fail-case")))
      case _ =>
        Gen
          .int(1, max)
          .flatMap(n =>
            for {
              i  <- Gen.int(0, n - 1)
              it <- Gen.listOfN(n)(a)
            } yield ZStream.unfoldM((i, it)) {
              case (_, Nil) | (0, _) => IO.fail("fail-case")
              case (n, head :: rest) => IO.succeedNow(Some((head, (n - 1, rest))))
            }
          )
    }

  def dropUntil[A](as: List[A])(f: A => Boolean): List[A] =
    as.dropWhile(!f(_)).drop(1)

  def takeUntil[A](as: List[A])(f: A => Boolean): List[A] =
    as.takeWhile(!f(_)) ++ as.dropWhile(!f(_)).take(1)

  def nPulls[R, E, A](pull: ZIO[R, Option[E], A], n: Int): ZIO[R, Nothing, List[Either[Option[E], A]]] =
    ZIO.foreach(1 to n)(_ => pull.either)

  val streamOfBytes = Gen.small(streamGen(Gen.anyByte, _))
  val streamOfInts  = Gen.small(streamGen(intGen, _))

  val listOfInts = Gen.listOf(intGen)

  val pureStreamOfBytes = Gen.small(pureStreamGen(Gen.anyByte, _))
  val pureStreamOfInts  = Gen.small(pureStreamGen(intGen, _))
}
