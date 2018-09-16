package scalaz.zio.lockfree

trait LockFreeQueue[A] {
  val capacity: Int

  def offer(a: A): Boolean

  def poll(): Option[A]

  def relaxedSize(): Int

  def enqueuedCount(): Long

  def dequeuedCount(): Long

  def isEmpty(): Boolean

  def isFull(): Boolean
}
