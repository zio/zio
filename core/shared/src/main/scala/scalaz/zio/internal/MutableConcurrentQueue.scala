package scalaz.zio.internal

object MutableConcurrentQueue {
  import scalaz.zio.internal.impls._

  def bounded[A](desiredCapacity: Int): MutableConcurrentQueue[A] = RingBuffer.build[A](desiredCapacity)
  def unbounded[A]: MutableConcurrentQueue[A]                     = new LinkedQueue[A]
}

/**
 * A MutableConcurrentQueue interface to use under the hood in ZIO.
 *
 * The implementation at minimum:
 * 1. Should be non-blocking and ideally lock-free.
 * 2. Should provide basic metrics such as how many elements were enqueued/dequeued.
 *
 * @note this is declared as `abstract class` since `invokevirtual`
 * is slightly cheaper than `invokeinterface`.
 */
abstract class MutableConcurrentQueue[A] {

  /**
   * The '''maximum''' number of elements that a queue can hold.
   *
   * @note that unbounded queues can still implement this interface
   * with `capaciy = MAX_INT`.
   */
  val capacity: Int

  /**
   * A non-blocking enqueue.
   *
   * @return whether the enqueue was successful or not.
   */
  def offer(a: A): Boolean

  /**
   * A non-blocking dequeue.
   *
   * @return either an element from the queue, or the `default`
   * param.
   *
   * @note that if there's no meaningful default for your type, you
   * can alway use `poll(null)`. Not the best, but reasonable price
   * to pay for lower heap churn from not using [[scala.Option]] here.
   */
  def poll(default: A): A

  /**
   * @return the '''current''' number of elements inside the queue.
   *
   * @note that this method can be non-atomic and return the
   * approximate number in a concurrent setting.
   */
  def size(): Int

  /**
   * @return the number of elements that have ever been added to the
   * queue.
   *
   * @note that [[scala.Long]] is used here, since [[scala.Int]] will be
   * overflowed really quickly for busy queues.
   *
   * @note if you know how much time the queue is alive, you can
   * calculate the rate at which elements are being enqueued.
   */
  def enqueuedCount(): Long

  /**
   * @return the number of elements that have ever been taken from the queue.
   *
   * @note if you know how much time the queue is alive, you can
   * calculate the rate at which elements are being dequeued.
   */
  def dequeuedCount(): Long

  def isEmpty(): Boolean

  def isFull(): Boolean
}
