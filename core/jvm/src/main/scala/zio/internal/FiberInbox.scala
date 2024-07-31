package zio.internal

import org.jctools.queues.atomic.unpadded.MpscLinkedAtomicUnpaddedQueue

private[zio] final class FiberInbox extends Serializable {
  private val inbox = new MpscLinkedAtomicUnpaddedQueue[FiberMessage]()

  def poll(): FiberMessage = inbox.poll()

  def offer(message: FiberMessage): Unit = inbox.offer(message)

  def isEmpty: Boolean = inbox.isEmpty

  def offerAll(messages: FiberMessage*): Unit = messages.foreach(offer)
}
