package scalaz.zio.internal.tracing

import scalaz.zio.{ UIO, ZIO }

/**
 * Marks a ZIO utility function that wraps a user-supplied lambda.
 *
 * Allows access to the underlying lambda so that stacktraces can
 * point to supplied user's code, instead of ZIO internals.
 * */
private[zio] abstract class ZIOFn[-A, +B] extends Function[A, B] {
  def underlying: AnyRef
}

private[zio] object ZIOFn {
  def apply[A, B](underlying0: AnyRef)(real: A => B): ZIOFn[A, B] = new ZIOFn[A, B] {
    final val underlying: AnyRef = underlying0
    final def apply(a: A): B     = real(a)
  }

  def recordTrace(lambda: AnyRef): UIO[Unit] = UIO.unit.flatMap(ZIOFn(lambda)(ZIO.succeed))
}
