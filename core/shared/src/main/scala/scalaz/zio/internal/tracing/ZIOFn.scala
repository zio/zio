package scalaz.zio.internal.tracing

import scalaz.zio.{ UIO, ZIO }

/**
 * Marks a ZIO utility function that wraps a user-supplied lambda.
 *
 * Allows access to the underlying lambda so that stacktraces can
 * point to supplied user's code, instead of ZIO internals.
 *
 * NOTE: it being an `abstract class`, not trait is important as typecasing
 * on class appears to be about 10 times faster in [[scalaz.zio.internal.FiberContext.unwrap]]
 * hot-spot.
 * */
private[zio] abstract class ZIOFn extends Serializable {
  def underlying: AnyRef
}

private[zio] abstract class ZIOFn1[-A, +B] extends ZIOFn with Function[A, B] {
  def underlying: AnyRef
}

private[zio] abstract class ZIOFn2[-A, -B, +C] extends ZIOFn with Function2[A, B, C] {
  def underlying: AnyRef
}

private[zio] object ZIOFn {
  final def apply[A, B](traceAs: AnyRef)(real: A => B): ZIOFn1[A, B] = new ZIOFn1[A, B] {
    final val underlying: AnyRef = traceAs
    final def apply(a: A): B     = real(a)
  }

  @noinline
  final def recordTrace(lambda: AnyRef): UIO[Unit] = UIO.unit.flatMap(ZIOFn(lambda)(ZIO.succeed))
}
