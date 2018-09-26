package scalaz.zio.stream

import scalaz.zio._

private[stream] trait StreamChunkPure[@specialized +A] extends StreamChunk[Nothing, A] { self =>
  import Stream.Step

  val chunks: StreamPure[Chunk[A]]

  def foldPure[A1 >: A, S](s: S)(f: (S, A1) => Step[S]): Step[S] =
    chunks.foldPure(s) { (s, as) =>
      def loop(s: S, i: Int): Step[S] =
        if (i >= as.length) Step.cont(s)
        else
          f(s, as(i)) match {
            case s: Step.Cont[S] => loop(s.extract, i + 1)
            case s               => s
          }

      loop(s, 0)
    }

  def foldPureLazy[A1 >: A, S](s: S)(cont: S => Boolean)(f: (S, A1) => S): S =
    chunks.foldPureLazy(s)(cont) { (s, as) =>
      as.foldLeftLazy(s)(cont)(f)
    }

  override def foldLeft[A1 >: A, S](s: S)(f: (S, A1) => S): IO[Nothing, S] =
    IO.now(foldPureLazy(s)(_ => true)(f))

  override def map[@specialized B](f: A => B): StreamChunkPure[B] =
    StreamChunkPure(chunks.map(_.map(f)))

  override def filter(pred: A => Boolean): StreamChunkPure[A] =
    StreamChunkPure(chunks.map(_.filter(pred)))

  override def mapConcat[B](f: A => Chunk[B]): StreamChunkPure[B] =
    StreamChunkPure(chunks.map(_.flatMap(f)))
}

object StreamChunkPure {
  def apply[A](chunkStream: StreamPure[Chunk[A]]): StreamChunkPure[A] =
    new StreamChunkPure[A] {
      val chunks = chunkStream
    }
}
