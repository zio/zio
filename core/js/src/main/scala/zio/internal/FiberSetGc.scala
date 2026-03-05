package zio.internal

import zio.Duration

private object FiberSetGc {
  def start[A <: AnyRef](set: FiberSet[A], every: Duration): Unit = ()
}
