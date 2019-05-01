package scalaz.zio.internal.tracing

/**
 * Marks a ZIO utility function that wraps a user-supplied lambda.
 *
 * Allows access to the underlying lambda so that stacktraces can
 * point to supplied user's code, instead of ZIO internals.
 * */
private[zio] abstract class ZIOFn[-A, +B] extends Function[A, B] {
  def underlying: AnyRef
}
