package scalaz.zio

import scalaz.zio.internal.Executor

package object blocking extends Blocking.Interface[Blocking] {
  def blockingExecutor: ZIO[Blocking, Nothing, Executor] =
    ZIO.readM(_.blocking.blockingExecutor)
}
