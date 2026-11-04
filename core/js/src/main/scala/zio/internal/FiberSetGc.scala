package zio.internal

import zio.Duration

private object FiberSetGc {
  def start(set: FiberSet, every: Duration): Unit = ()
}
