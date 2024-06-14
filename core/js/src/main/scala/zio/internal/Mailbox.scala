package zio.internal

import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.tailrec

private[internal] final class Mailbox[A] extends Serializable {

  private[this] var read  = new Node(null.asInstanceOf[A])
  private[this] var write = read

  @throws[NullPointerException]("if the element is null")
  def add(data: A): Unit = {
    if (null == data) throw new NullPointerException()
    write.next = new Node(data)
    write = write.next
  }

  def isEmpty(): Boolean =
    null == read.next

  def nonEmpty(): Boolean =
    null != read.next

  @throws[NullPointerException]("if the element is null")
  def prepend(data: A): Unit = {
    if (null == data) throw new NullPointerException()
    read = new Node(null.asInstanceOf[A], new Node(data, read))
  }

  @throws[NullPointerException]("if either element is null")
  def prepend2(data1: A, data2: A): Unit = {
    if (null == data1 || null == data2) throw new NullPointerException()
    read = new Node(null.asInstanceOf[A], new Node(data1, new Node(data2, read)))
  }

  @tailrec def poll(): A = {
    val next = read.next

    if (null == next)
      return null.asInstanceOf[A]

    val data = next.data
    read = next

    if (null != data) {
      next.data = null.asInstanceOf[A]
      return data
    }

    poll()
  }
}

private class Node[A](var data: A, var next: Node[A] = null)
