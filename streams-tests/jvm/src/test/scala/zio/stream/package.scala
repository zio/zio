package zio

import scala.reflect.ClassTag
import zio.Chunk
import zio.test.{ Gen, Sized }
import zio.random.Random

package object stream {
  def chunkGen[A: ClassTag](a: Gen[Random, A]): Gen[Random with Sized, Chunk[A]] =
    Gen.oneOf(
      Gen.const(Chunk.empty),
      a.map(Chunk.succeed),
      Gen.listOf(a).map(seqT => Chunk.fromArray(seqT.toArray)),
      for {
        arr  <- chunkGen(a)
        left <- Gen.int(0, arr.length)
      } yield arr.take(left),
      for {
        left  <- chunkGen(a)
        right <- chunkGen(a)
      } yield left ++ right
    )

  def streamGen[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, Stream[String, A]] =
    Gen.oneOf(genFailingStream(a), genPureStream(a))

  def genPureStream[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, Stream[Nothing, A]] =
    Gen.listOf(a).map(Stream.fromIterable)

  def genSucceededStream[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, Stream[Nothing, A]] =
    Gen.listOf(a).map(Stream.fromIterable)

  def genFailingStream[R <: Random, A](a: Gen[R, A]): Gen[R with Sized, Stream[String, A]] =
    for {
      it <- Gen.listOf(a)
      n  <- Gen.int(0, it.size)
    } yield ZStream.unfoldM((n, it)) {
      case (_, Nil) | (0, _) =>
        IO.fail("fail-case")
      case (n, head :: rest) => IO.succeed(Some((head, (n - 1, rest))))
    }

  def stepGen[R <: Random, S, A: ClassTag](genS: Gen[R, S], genA: Gen[R, A]): Gen[R with Sized, ZSink.Step[S, A]] =
    Gen.oneOf(
      genS.map(ZSink.Step.more(_)),
      (genS zip chunkGen(genA)).map(tp => ZSink.Step.done(tp._1, tp._2))
    )

  def streamChunkGen[R <: Random, A: ClassTag](a: Gen[R, A]): Gen[R with Sized, StreamChunk[String, A]] =
    Gen.oneOf(
      genFailingStream(chunkGen(a)).map(StreamChunk(_)),
      genPureStream(chunkGen(a)).map(StreamChunk(_)),
      genSucceededStream(chunkGen(a)).map(StreamChunk(_))
    )

  def succeededStreamChunkGen[R <: Random, A: ClassTag](a: Gen[R, A]): Gen[R with Sized, StreamChunk[Nothing, A]] =
    Gen.oneOf(
      genPureStream(chunkGen(a)).map(StreamChunk(_)),
      genSucceededStream(chunkGen(a)).map(StreamChunk(_))
    )

}
