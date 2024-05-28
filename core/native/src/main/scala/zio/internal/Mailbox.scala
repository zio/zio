package zio.internal

import zio.stacktracer.TracingImplicits.disableAutoTrace

final class Mailbox[A] extends Serializable {

  private[this] var read  = new Node(null)
  private[this] var write = read

  def add(data: A): Unit = {
    val next = new Node(data.asInstanceOf[AnyRef])
    write.next = next
  }

  def isEmpty(): Boolean =
    null == read.next

  def nonEmpty(): Boolean =
    null != read.next

  def poll(): A = {
    val next = read.next;

    if (null == next)
      return null.asInstanceOf[A]

    val data = next.data
    next.data = null
    read = next
    data.asInstanceOf[A]
  }
}

private class Node(var data: AnyRef) {
  var next: Node = _
}
