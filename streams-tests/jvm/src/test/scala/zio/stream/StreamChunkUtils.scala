package zio.stream

import scala.annotation.tailrec
import scala.reflect.ClassTag

import zio.random.Random
import zio.test.{ Gen, Sized }
import zio.{ Chunk, IO }

trait StreamChunkUtils extends StreamUtils {
  def streamChunkGen[R <: Random with Sized, A: ClassTag](as: Gen[R, Chunk[A]]): Gen[R, StreamChunk[String, A]] =
    Gen.oneOf(
      Gen.small(failingStreamGen(as, _)).map(StreamChunk(_)),
      Gen.small(pureStreamGen(as, _)).map(StreamChunk(_)),
      Gen.small(failingStreamEffectGen(as, _)).map(StreamEffectChunk(_)),
      Gen.small(pureStreamEffectGen(as, _)).map(StreamEffectChunk(_))
    )

  def pureStreamChunkGen[R <: Random with Sized, A: ClassTag](as: Gen[R, Chunk[A]]): Gen[R, StreamChunk[Nothing, A]] =
    Gen.oneOf(
      Gen.small(pureStreamGen(as, _)).map(StreamChunk(_)),
      Gen.small(pureStreamEffectGen(as, _)).map(StreamEffectChunk(_))
    )
}

object StreamChunkUtils extends StreamChunkUtils {
  def slurp[E, A](s: StreamChunk[E, A]): IO[E, Seq[A]] =
    s.chunks
      .fold(Chunk.empty: Chunk[A])(_ ++ _)

  def foldLazyList[S, T](list: List[T], zero: S)(cont: S => Boolean)(f: (S, T) => S): S = {
    @tailrec
    def loop(xs: List[T], state: S): S = xs match {
      case head :: tail if cont(state) => loop(tail, f(state, head))
      case _                           => state
    }
    loop(list, zero)
  }
}
