package zio.stream.encoding1

import zio._

abstract class ZSink1[-R, +E, -I, +O] {
  self =>

  def process[EE >: E]: ZSink1.Process[R, EE, I, O]

  def >>:[R1 <: R, E1 >: E, I1 <: I](upstream: ZStream1[R1, E1, I1]): ZIO[R1, E1, O] =
    aggregate(upstream)

  def >>:[R1 <: R, E1 >: E, II, I1 <: I](upstream: ZTransducer1[R1, E1, II, I1]): ZSink1[R1, E1, II, O] =
    prepend(upstream)

  def aggregate[R1 <: R, E1 >: E, I1 <: I](upstream: ZStream1[R1, E1, I1]): ZIO[R1, E1, O] =
    upstream.process.zip(self.process[E1]).use {
      case (pull, (push, read)) =>
        push(pull).forever.catchAllCause[R1, E1, O](Cause.sequenceCauseOption(_).fold(read)(ZIO.halt(_)))
    }

  def chunked: ZSink1[R, E, Chunk[I], O] =
    new ZSink1[R, E, Chunk[I], O] {

      def process[EE >: E]: ZSink1.Process[R, EE, Chunk[I], O] =
        self.process[EE].map {
          case (push, read) => (_.flatMap(ZIO.foreach(_)(i => push(Pull.emit(i)))), read)
        }
    }

  def prepend[R1 <: R, E1 >: E, II, I1 <: I](upstream: ZTransducer1[R1, E1, II, I1]): ZSink1[R1, E1, II, O] =
    new ZSink1[R1, E1, II, O] {

      def process[EE >: E1]: ZSink1.Process[R1, EE, II, O] =
        upstream.process[EE].zipWith(self.process[EE]) {
          case (pipe, (push, read)) => (pipe andThen push, read)
        }
    }
}

object ZSink1 {

  type Process[-R, E, -I, +O] = URManaged[R, (Pull[E, I] => Pull[E, Any], IO[E, O])]

  def access[R] =
    new AccessPartiallyApplied[R]()

  def collect[I]: ZSink1[Any, Nothing, I, Chunk[I]] =
    new ZSink1[Any, Nothing, I, Chunk[I]] {

      def process[EE >: Nothing]: Process[Any, EE, I, Chunk[I]] =
        Process.stateful(ChunkBuilder.make[I]())(
          (ref, pull: Pull[EE, I]) => pull.flatMap(i => ref.update(_ += i)),
          _.getAndSet(ChunkBuilder.make[I]()).map(_.result())
        )

      override def chunked: ZSink1[Any, Nothing, Chunk[I], Chunk[I]] =
        new ZSink1[Any, Nothing, Chunk[I], Chunk[I]] {

          def process[EE >: Nothing]: Process[Any, EE, Chunk[I], Chunk[I]] =
            Process.stateful(ChunkBuilder.make[I]())(
              (ref, pull: Pull[EE, Chunk[I]]) => pull.flatMap(i => ref.update(_ ++= i)),
              _.getAndSet(ChunkBuilder.make[I]()).map(_.result())
            )
        }

    }

  val drain: ZSink1[Any, Nothing, Any, Unit] =
    new ZSink1[Any, Nothing, Any, Unit] {

      def process[EE >: Nothing]: Process[Any, EE, Any, Unit] =
        ZManaged.succeedNow((identity, ZIO.unit))

      override def chunked: ZSink1[Any, Nothing, Chunk[Any], Unit] =
        ZSink1.drain
    }

  def fold[S, I](init: S)(push: (S, I) => S): ZSink1[Any, Nothing, I, S] =
    new ZSink1[Any, Nothing, I, S] {

      def process[EE >: Nothing]: Process[Any, EE, I, S] =
        Process.stateful(init)((ref, pull: Pull[EE, I]) => pull.flatMap(i => ref.update(push(_, i))), _.get)

      override def chunked: ZSink1[Any, Nothing, Chunk[I], S] =
        ZSink1.fold(init)((s, is: Chunk[I]) => is.foldLeft(s)(push))
    }

  def foreach[R, E, I](f: I => ZIO[R, E, Any]): ZSink1[R, E, I, Unit] =
    access[R]((r, i: I) => f(i).provide(r).asSomeError)(_ => ZIO.unit)

  def service[A] =
    new ServicePartiallyApplied[A]()

  def sum[N](implicit N: Numeric[N]): ZSink1[Any, Nothing, N, N] =
    fold(N.zero)(N.plus)

  final class AccessPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {

    def apply[E, I, O](push: (R, I) => Pull[E, Any])(read: R => IO[E, O]): ZSink1[R, E, I, O] =
      new ZSink1[R, E, I, O] {
        def process[EE >: E]: Process[R, EE, I, O] =
          ZManaged.access[R](r => (_.flatMap(push(r, _)), read(r)))
      }
  }

  final class ServicePartiallyApplied[A](private val dummy: Boolean = true) extends AnyVal {

    def apply[E, I, O](push: (A, I) => Pull[E, O])(read: A => IO[E, O])(implicit a: Tag[A]): ZSink1[Has[A], E, I, O] =
      new ZSink1[Has[A], E, I, O] {
        def process[EE >: E]: Process[Has[A], EE, I, O] =
          ZManaged.service[A].map(a => (_.flatMap(push(a, _)), read(a)))
      }
  }

  object Process {

    def stateful[S, E, I, O](
      init: S
    )(push: (Ref[S], Pull[E, I]) => Pull[E, Any], read: Ref[S] => IO[E, O]): Process[Any, E, I, O] =
      ZRef.makeManaged(init).map(ref => (push(ref, _), read(ref)))
  }
}
