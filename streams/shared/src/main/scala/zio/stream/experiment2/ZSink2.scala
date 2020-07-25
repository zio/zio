package zio.stream.experiment2

import zio.{ Cause, Chunk, IO, URManaged, ZIO }

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

  def apply[R, E, I, O](process: Process[R, E, I, O]): ZSink2[R, E, I, O] =
    new ZSink2(process) {}
}
