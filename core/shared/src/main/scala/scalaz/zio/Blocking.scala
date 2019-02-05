package scalaz.zio

import scalaz.zio.internal.Executor

/**
 * The `Blocking` module provides access to a thread pool that can be used for performing
 * blocking operations, such as thread sleeps, synchronous socket/file reads, and so forth.
 * The contract is that the thread pool will accept unlimited tasks (up to the available)
 * memory, and continuously create new threads as necessary.
 */
trait Blocking {
  def blocking: Blocking.Interface[Any]
}
object Blocking {
  trait Interface[R] {

    /**
     * Retrieves the executor for all blocking tasks.
     */
    def executor: ZIO[R, Nothing, Executor]

    /**
     * Locks the specified task to the blocking thread pool.
     */
    final def blocking[R1 <: R, E, A](zio: ZIO[R1, E, A]): ZIO[R1, E, A] =
      executor.flatMap(exec => zio.lock(exec))
  }
}
