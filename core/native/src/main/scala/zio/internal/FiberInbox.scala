package zio.internal

import java.util.concurrent.ConcurrentLinkedQueue

private[zio] final class FiberInbox {
  private val inbox = new ConcurrentLinkedQueue[FiberMessage]()

  def poll(): FiberMessage = inbox.poll()

  def offer(message: FiberMessage): Unit = inbox.offer(message)

  def isEmpty: Boolean = inbox.isEmpty

  def offerAll(messages: FiberMessage*): Unit = messages.foreach(offer)
}
