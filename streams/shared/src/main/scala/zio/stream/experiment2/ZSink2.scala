package zio.stream.experiment2

import zio.{ Cause, Chunk, Has, IO, Ref, Tag, URManaged, ZIO, ZManaged, ZRef }

abstract class ZSink2[-R, +E, -I, +O](val process: ZSink2.Process[R, E, I, O]) {
  self =>

  def >>:[R1 <: R, E1 >: E, I1 <: I](upstream: ZStream2[R1, E1, I1]): ZIO[R1, E1, O] =
    aggregate(upstream)

  def >>:[R1 <: R, E1 >: E, I1, II <: I](upstream: ZTransducer2[R1, E1, I1, II]): ZSink2[R1, E1, I1, O] =
    prepend(upstream)

  def aggregate[R1 <: R, E1 >: E, I1 <: I](upstream: ZStream2[R1, E1, I1]): ZIO[R1, E1, O] =
    upstream.process.zip(self.process).use {
      case (pull, (push, read)) =>
        pull.flatMap(push).forever.catchAllCause(Cause.sequenceCauseOption(_).fold[ZIO[R1, E1, O]](read)(ZIO.halt(_)))
    }

  def chunked: ZSink2[R, E, Chunk[I], O] =
    ZSink2(self.process.map {
      case (push, read) => (ZIO.foreach(_)(push), read)
    })

  def prepend[R1 <: R, E1 >: E, I1, II <: I](upstream: ZTransducer2[R1, E1, I1, II]): ZSink2[R1, E1, I1, O] =
    ZSink2(upstream.process.zipWith(self.process) {
      case (pipe, (push, read)) => (pipe(_) >>= push, read)
    })
}

object ZSink2 {

  type Process[-R, +E, -I, +O] = URManaged[R, (I => Pull[E, Any], IO[E, O])]

  def access[R]: AccessPartiallyApplied[R] =
    new AccessPartiallyApplied[R]()

  def apply[R, E, I, O](process: Process[R, E, I, O]): ZSink2[R, E, I, O] =
    new ZSink2(process) {}

  def fold[S, I](init: S)(push: (S, I) => S): ZSink2[Any, Nothing, I, S] =
    new ZSink2[Any, Nothing, I, S](Process.fold(init)(push)) {

      override def chunked: ZSink2[Any, Nothing, Chunk[I], S] =
        ZSink2.fold(init)((s, is: Chunk[I]) => is.foldLeft(s)(push))
    }

  def service[A]: ServicePartiallyApplied[A] =
    new ServicePartiallyApplied[A]()

  def sum[N](implicit N: Numeric[N]): ZSink2[Any, Nothing, N, N] =
    fold(N.zero)(N.plus)

  final class AccessPartiallyApplied[R](val dummy: Boolean = true) extends AnyVal {

    def apply[E, I, O](push: R => I => Pull[E, Any])(read: R => IO[E, O]): ZSink2[R, E, I, O] =
      ZSink2(ZManaged.access[R](r => (push(r), read(r))))
  }

  final class ServicePartiallyApplied[A](val dummy: Boolean = true) extends AnyVal {

    def apply[E, I, O](push: A => I => Pull[E, Any])(read: A => IO[E, O])(implicit A: Tag[A]): ZSink2[Has[A], E, I, O] =
      ZSink2(ZManaged.service[A].map(a => (push(a), read(a))))
  }

  object Process {

    def fold[S, I](init: S)(push: (S, I) => S): Process[Any, Nothing, I, S] =
      stateful(init)(ref => i => ref.update(push(_, i)))

    def stateful[S, E, I](init: S)(push: Ref[S] => I => Pull[E, Any]): Process[Any, E, I, S] =
      ZRef.makeManaged(init).map(ref => (push(ref), ref.get))
  }
}
