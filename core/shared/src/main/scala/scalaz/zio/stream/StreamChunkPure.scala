package scalaz.zio.stream

import scalaz.zio._

private[stream] trait StreamChunkPure[-R, @specialized +A] extends StreamChunk[R, Nothing, A] { self =>
  val chunks: StreamPure[R, Chunk[A]]

  def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
    chunks.foldPureLazy(s)(cont) { (s, as) =>
      as.foldLeftLazy(s)(cont)(f)
    }

  override def foldLeft[A1 >: A, S](s: S)(f: (S, A1) => S): IO[Nothing, S] =
    IO.succeed(foldPureLazy(s)(_ => true)(f))

  override def map[@specialized B](f: A => B): StreamChunkPure[R, B] =
    StreamChunkPure(chunks.map(_.map(f)))

  override def filter(pred: A => Boolean): StreamChunkPure[R, A] =
    StreamChunkPure(chunks.map(_.filter(pred)))

  override def mapConcat[B](f: A => Chunk[B]): StreamChunkPure[R, B] =
    StreamChunkPure(chunks.map(_.flatMap(f)))
}

object StreamChunkPure {
  def apply[R, A](chunkStream: StreamPure[R, Chunk[A]]): StreamChunkPure[R, A] =
    new StreamChunkPure[R, A] {
      val chunks = chunkStream
    }
}
