package zio.stream.experiment1

import zio._

abstract class ZSink1[-R, -EI, +EO, -I, +O] {

  def process: ZSink1.Process[R, EI, EO, I, O]

  def >>:[R1 <: R, E1 <: EI, I1](upstream: ZTransducer1[R1, E1, E1, I1, I]): ZSink1[R1, E1, EO, I1, O] =
    transduce(upstream)

  def transduce[R1 <: R, E1 <: EI, I1](upstream: ZTransducer1[R1, E1, E1, I1, I]): ZSink1[R1, E1, EO, I1, O] =
    ZSink1(upstream.process.zipWith(process) {
      case (pipe, (push, read)) => (pipe andThen push, read)
    })
}

object ZSink1 {

  type Process[-R, -EI, +EO, -I, +O] = URManaged[R, (Pull[EI, I] => Pull[EO, Any], IO[EO, O])]

  def apply[R, EI, EO, I, O](p: Process[R, EI, EO, I, O]): ZSink1[R, EI, EO, I, O] =
    new ZSink1[R, EI, EO, I, O] {
      def process: Process[R, EI, EO, I, O] = p
    }

  def drain[E]: Chunked[Any, E, Any, Unit] =
    new Chunked[Any, E, Any, Unit](ZManaged.succeedNow((_ => ZIO.unit, ZIO.unit))) {

      override def chunked: Chunked[Any, E, Chunk[Any], Unit] =
        ZSink1.drain
    }

  def fold[S, E, I](init: S)(push: (S, I) => S): Chunked[Any, E, I, S] =
    new Chunked[Any, E, I, S](Process.fold(init)(push)) {

      override def chunked: Chunked[Any, E, Chunk[I], S] =
        ZSink1.fold(init)((s, is: Chunk[I]) => is.foldLeft(s)(push))
    }

  def make[R, E, I, O](process: Process[R, E, E, I, O]): Chunked[R, E, I, O] =
    new Chunked(process)

  def sum[E, N](implicit N: Numeric[N]): Chunked[Any, E, N, N] =
    fold(N.zero)(N.plus)

  sealed class Chunked[-R, E, -I, +O](val process: Process[R, E, E, I, O]) extends ZSink1[R, E, E, I, O] {

    def chunked: Chunked[R, E, Chunk[I], O] =
      ZSink1.make(process.map {
        case (push, read) => (_.flatMap(ZIO.foreach_(_)(i => push(Pull.emit(i)))), read)
      })
  }

  object Process {

    def fold[S, E, I](init: S)(push: (S, I) => S): Process[Any, E, E, I, S] =
      ZRef.makeManaged(init).map(ref => (_.flatMap(i => ref.update(push(_, i))), ref.get))
  }
}
