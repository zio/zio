package zio.internal

import java.util.concurrent.ConcurrentLinkedQueue
import zio.stacktracer.TracingImplicits.disableAutoTrace

/** Simple ConcurrentLinkedQueue wrapper for Scala.js (single-threaded). */
private[zio] object UnboundedMpmcQueue {
  def apply[A <: AnyRef](chunkSize: Int): UnboundedMpmcQueue[A] =
    new UnboundedMpmcQueue[A]
}

private[zio] final class UnboundedMpmcQueue[A <: AnyRef] private[internal] () {
  private[this] val queue = new ConcurrentLinkedQueue[A]()

  def size(): Int       = queue.size()
  def offer(a: A): Unit = { queue.offer(a); () }
  def poll(): A         = queue.poll()
  def peek(): A         = queue.peek()
}
