package zio.stm

import zio._

/**
 * A transactional queue that can only be enqueued.
 */
trait TEnqueue[-A] extends Serializable {

  /**
   * The maximum capacity of the queue.
   */
  def capacity: Int

  /**
   * Checks whether the queue is shut down.
   */
  def isShutdown: USTM[Boolean]

  /**
   * Offers a value to the queue, returning whether the value was offered to the
   * queue.
   */
  def offer(a: A): ZSTM[Any, Nothing, Boolean]

  /**
   * Offers all of the specified values to the queue, returning whether they
   * were offered to the queue.
   */
  def offerAll(as: Iterable[A]): ZSTM[Any, Nothing, Boolean]

  /**
   * Shuts down the queue.
   */
  def shutdown: USTM[Unit]

  /**
   * The current number of values in the queue.
   */
  def size: USTM[Int]
}
