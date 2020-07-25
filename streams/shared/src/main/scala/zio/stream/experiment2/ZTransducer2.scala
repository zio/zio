package zio.stream.experiment2

import zio.{ Chunk, URManaged, ZIO }

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
    ZStream2(upstream.process.zipWith(self.process)(_.flatMap(_)))
}

object ZTransducer2 {

  type Process[-R, +E, -I, +O] = URManaged[R, I => Pull[E, O]]

  def apply[R, E, I, O](process: Process[R, E, I, O]): ZTransducer2[R, E, I, O] =
    new ZTransducer2(process) {}
}
