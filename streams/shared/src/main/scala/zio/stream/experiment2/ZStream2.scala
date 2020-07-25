package zio.stream.experiment2

import zio.{ URManaged, ZIO }

abstract class ZStream2[-R, +E, +I](val process: ZStream2.Process[R, E, I]) {
  self =>

  def run[R1 <: R, E1 >: E, I1 >: I, O](downstream: ZSink2[R1, E1, I1, O]): ZIO[R1, E1, O] =
    downstream.aggregate(self)
}

object ZStream2 {

  type Process[-R, +E, +I] = URManaged[R, Pull[E, I]]

  def apply[R, E, I](process: Process[R, E, I]): ZStream2[R, E, I] =
    new ZStream2(process) {}
}
