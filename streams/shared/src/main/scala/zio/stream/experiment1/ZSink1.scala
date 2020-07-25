package zio.stream.experiment1

import zio._

abstract class ZSink1[-R, +EO, -I, +O] {
  self =>

  def process[E >: EO]: ZSink1.Process[R, E, I, O]

  def >>:[R1 <: R, E1 >: EO, I1 <: I](upstream: ZStream1[R1, E1, I1]): ZIO[R1, E1, O] =
    aggregate(upstream)

  def >>:[R1 <: R, E1 >: EO, II, I1 <: I](upstream: ZTransducer1[R1, E1, II, I1]): ZSink1[R1, E1, II, O] =
    prepend(upstream)

  def aggregate[R1 <: R, E1 >: EO, I1 <: I](upstream: ZStream1[R1, E1, I1]): ZIO[R1, E1, O] =
    upstream.process.zip(self.process[E1]).use {
      case (pull, (push, read)) =>
        push(pull).forever.catchAllCause[R1, E1, O](Cause.sequenceCauseOption(_).fold(read)(ZIO.halt(_)))
    }

  def chunked: ZSink1[R, EO, Chunk[I], O] =
    new ZSink1[R, EO, Chunk[I], O] {

      def process[E >: EO]: ZSink1.Process[R, E, Chunk[I], O] =
        self.process[E].map {
          case (push, read) => (_.flatMap(ZIO.foreach(_)(i => push(Pull.emit(i)))), read)
        }
    }

  def prepend[R1 <: R, E1 >: EO, II, I1 <: I](upstream: ZTransducer1[R1, E1, II, I1]): ZSink1[R1, E1, II, O] =
    new ZSink1[R1, E1, II, O] {

      def process[E >: E1]: ZSink1.Process[R1, E, II, O] =
        upstream.process[E].zipWith(self.process[E]) {
          case (pipe, (push, read)) => (pipe andThen push, read)
        }
    }
}

object ZSink1 {

  type Process[-R, E, -I, +O] = URManaged[R, (Pull[E, I] => Pull[E, Any], IO[E, O])]

  def access[R, I] =
    new AccessPartiallyApplied[R, I]()

  val drain: ZSink1[Any, Nothing, Any, Unit] =
    new ZSink1[Any, Nothing, Any, Unit] {

      def process[E >: Nothing]: Process[Any, E, Any, Unit] =
        ZManaged.succeedNow((_ => ZIO.unit, ZIO.unit))

      override def chunked: ZSink1[Any, Nothing, Chunk[Any], Unit] =
        ZSink1.drain
    }

  def fold[S, I](init: S)(push: (S, I) => S): ZSink1[Any, Nothing, I, S] =
    new ZSink1[Any, Nothing, I, S] {

      def process[E >: Nothing]: Process[Any, E, I, S] =
        Process.stateful(init)((ref, pull) => pull.flatMap(i => ref.update(push(_, i))))

      override def chunked: ZSink1[Any, Nothing, Chunk[I], S] =
        ZSink1.fold(init)((s, is: Chunk[I]) => is.foldLeft(s)(push))
    }

  def service[A, I] =
    new ServicePartiallyApplied[A, I]()

  def sum[N](implicit N: Numeric[N]): ZSink1[Any, Nothing, N, N] =
    fold(N.zero)(N.plus)

  final class AccessPartiallyApplied[R, I](private val dummy: Boolean = true) extends AnyVal {

    def apply[EO, O](push: (R, I) => Pull[EO, Any])(read: R => IO[EO, O]): ZSink1[R, EO, I, O] =
      new ZSink1[R, EO, I, O] {
        def process[E >: EO]: Process[R, E, I, O] =
          ZManaged.access[R](r => (_.flatMap(push(r, _)), read(r)))
      }
  }

  final class ServicePartiallyApplied[A, I](private val dummy: Boolean = true) extends AnyVal {

    def apply[EO, O](push: (A, I) => Pull[EO, O])(read: A => IO[EO, O])(implicit a: Tag[A]): ZSink1[Has[A], EO, I, O] =
      new ZSink1[Has[A], EO, I, O] {
        def process[E >: EO]: Process[Has[A], E, I, O] =
          ZManaged.service[A].map(a => (_.flatMap(push(a, _)), read(a)))
      }
  }

  object Process {

    def stateful[S, E, I](init: S)(push: (Ref[S], Pull[E, I]) => Pull[E, Any]): Process[Any, E, I, S] =
      ZRef.makeManaged(init).map(ref => (push(ref, _), ref.get))
  }
}
