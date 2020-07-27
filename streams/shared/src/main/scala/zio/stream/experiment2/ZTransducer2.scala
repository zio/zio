package zio.stream.experiment2

import zio.{ Chunk, ChunkBuilder, Has, Ref, Tag, URManaged, ZIO, ZManaged, ZRef }

abstract class ZTransducer2[-R, +E, -I, +O](val process: ZTransducer2.Process[R, E, I, O]) {
  self =>

  def >>:[R1 <: R, E1 >: E, I1, II <: I](upstream: ZTransducer2[R1, E1, I1, II]): ZTransducer2[R1, E1, I1, O] =
    prepend(upstream)

  def >>:[R1 <: R, E1 >: E, I1 <: I](upstream: ZStream2[R1, E1, I1]): ZStream2[R1, E1, O] =
    transduce(upstream)

  def chunked: ZTransducer2[R, E, Chunk[I], Chunk[O]] =
    ZTransducer2(self.process.map(push => ZIO.foreach(_)(push)))

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

  def foldWhile[S, I, O](init: S)(p: S => Boolean)(pipe: (S, I) => (O, S)): ZTransducer2[Any, Nothing, I, O] =
    new ZTransducer2[Any, Nothing, I, O](
      Process.stateful(init)(ref => i => ref.get.flatMap(s => if (p(s)) ref.modify(pipe(_, i)) else Pull.end))
    ) {

      override def chunked: ZTransducer2[Any, Nothing, Chunk[I], Chunk[O]] =
        ZTransducer2.foldWhile(init)(p) { (s, is: Chunk[I]) =>
          val b = ChunkBuilder.make[O]()
          var z = s
          var i = 0
          while (i < is.length) {
            val os = pipe(z, is(i))
            b += os._1
            z = os._2
            if (p(z)) i += 1 else i = is.length
          }
          b.result() -> z
        }
    }

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
            i =>
              ref
                .modify(s =>
                  if (s > 0) if (s > i.length) (Pull.emit(i), s - i.length) else (Pull.emit(i.take(s.toInt)), 0)
                  else (Pull.end, s)
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

    def stateful[S, E, I, O](init: S)(pipe: Ref[S] => I => Pull[E, O]): Process[Any, E, I, O] =
      ZRef.makeManaged(init).map(pipe)
  }
}
