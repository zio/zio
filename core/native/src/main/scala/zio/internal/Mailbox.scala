package zio.internal

final class Mailbox[A] extends Serializable {

  @transient private var read  = new Mailbox.Node[A](null.asInstanceOf[A])
  @transient private var write = read

  def add(data: A): Unit = {
    val next = new Mailbox.Node(data)
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
    next.data = null.asInstanceOf[A]
    read = next
    data.asInstanceOf[A]
  }
}

object Mailbox {

  private[Mailbox] class Node[A](var data: A) {
    var next: Node[A] = _
  }
}
