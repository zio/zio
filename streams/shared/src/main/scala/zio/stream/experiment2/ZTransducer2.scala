package zio.stream.experiment2

import zio._

abstract class ZTransducer2[-R, +E, -I, +O](val process: ZTransducer2.Process[R, E, I, O]) {
  self =>

  def >>:[R1 <: R, E1 >: E, I1, II <: I](upstream: ZTransducer2[R1, E1, I1, II]): ZTransducer2[R1, E1, I1, O] =
    prepend(upstream)

  def >>:[R1 <: R, E1 >: E, I1 <: I](upstream: ZStream2[R1, E1, I1]): ZStream2[R1, E1, O] =
    transduce(upstream)

  def chunked: ZTransducer2[R, E, Chunk[I], Chunk[O]] =
    ZTransducer2(self.process.map(pipe => ZIO.foreach(_)(pipe)))

  def prepend[R1 <: R, E1 >: E, I1, II <: I](upstream: ZTransducer2[R1, E1, I1, II]): ZTransducer2[R1, E1, I1, O] =
    ZTransducer2(upstream.process.zipWith(self.process)((pipe1, pipe2) => pipe1(_) >>= pipe2))

  def transduce[R1 <: R, E1 >: E, I1 <: I](upstream: ZStream2[R1, E1, I1]): ZStream2[R1, E1, O] =
    ZStream2(upstream.process.zipWith(self.process)(_ >>= _))
}

object ZTransducer2 {

  type Process[-R, +E, -I, +O] = URManaged[R, I => Pull[E, O]]

  def access[R]: AccessPartiallyApplied[R] =
    new AccessPartiallyApplied[R]()

  def apply[R, E, I, O](process: Process[R, E, I, O]): ZTransducer2[R, E, I, O] =
    new ZTransducer2(process) {}

  def identity[I]: ZTransducer2[Any, Nothing, I, I] =
    new ZTransducer2[Any, Nothing, I, I](ZManaged.succeedNow(Pull.emit)) {

      override def chunked: ZTransducer2[Any, Nothing, Chunk[I], Chunk[I]] =
        ZTransducer2.identity[Chunk[I]]
    }

  def map[I, O](f: I => O): ZTransducer2[Any, Nothing, I, O] =
    new ZTransducer2[Any, Nothing, I, O](ZManaged.succeedNow(i => Pull.emit(f(i)))) {

      override def chunked: ZTransducer2[Any, Nothing, Chunk[I], Chunk[O]] =
        ZTransducer2.map[Chunk[I], Chunk[O]](_.map(f))
    }

  def service[A]: ServicePartiallyApplied[A] =
    new ServicePartiallyApplied[A]()

  def take[I](n: Long): ZTransducer2[Any, Nothing, I, I] =
    new ZTransducer2[Any, Nothing, I, I](
      Process.stateful(n)(ref => i => ref.modify(s => if (s > 0) (Pull.emit(i), s - 1) else (Pull.end, s)).flatten)
    ) {

      override def chunked: ZTransducer2[Any, Nothing, Chunk[I], Chunk[I]] =
        ZTransducer2(
          Process.stateful(n)(ref =>
            is =>
              ref
                .modify(s =>
                  if (s > 0) if (s > is.length) (Pull.emit(is), s - is.length) else (Pull.emit(is.take(s.toInt)), 0)
                  else (Pull.end, s)
                )
                .flatten
          )
        )
    }

  def takeUntil[I](p: I => Boolean): ZTransducer2[Any, Nothing, I, I] =
    new ZTransducer2[Any, Nothing, I, I](
      Process.stateful(false)(ref => i => ref.modify(s => (if (s) Pull.end else Pull.emit(i), p(i))).flatten)
    ) {

      override def chunked: ZTransducer2[Any, Nothing, Chunk[I], Chunk[I]] =
        ZTransducer2(
          Process.stateful(false)(ref =>
            is =>
              ref
                .modify(
                  if (_) (Pull.end, true)
                  else
                    is.indexWhere(p) match {
                      case -1 => (Pull.emit(is), false)
                      case i  => (Pull.emit(is.take(i + 1)), true)
                    }
                )
                .flatten
          )
        )
    }

  def takeWhile[I](p: I => Boolean): ZTransducer2[Any, Nothing, I, I] =
    new ZTransducer2[Any, Nothing, I, I](
      Process.stateless(i => if (p(i)) Pull.emit(i) else Pull.end)
    ) {

      override def chunked: ZTransducer2[Any, Nothing, Chunk[I], Chunk[I]] =
        ZTransducer2(
          Process.stateful(true)(ref =>
            is =>
              ref
                .modify(
                  if (_) is.indexWhere(!p(_)) match {
                    case -1 => (Pull.emit(is), true)
                    case i  => (Pull.emit(is.take(i)), false)
                  }
                  else (Pull.end, false)
                )
                .flatten
          )
        )
    }

  final implicit class AccessPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {

    def apply[E, I, O](pipe: R => I => Pull[E, O]): ZTransducer2[R, E, I, O] =
      ZTransducer2(ZManaged.access[R](pipe))
  }

  final implicit class ServicePartiallyApplied[A](private val dummy: Boolean = true) extends AnyVal {

    def apply[E, I, O](pipe: A => I => Pull[E, O])(implicit A: Tag[A]): ZTransducer2[Has[A], E, I, O] =
      ZTransducer2(ZManaged.service[A].map(pipe))
  }

  object Process {

    def stateless[E, I, O](pipe: I => Pull[E, O]): Process[Any, E, I, O] =
      ZManaged.succeedNow(pipe)

    def stateful[S, E, I, O](init: S)(pipe: Ref[S] => I => Pull[E, O]): Process[Any, E, I, O] =
      ZRef.makeManaged(init).map(pipe)
  }
}
