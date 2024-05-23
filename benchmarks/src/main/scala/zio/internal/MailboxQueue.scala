package zio.internal

sealed trait MailboxQueue[A] {
  def add(data: A): Unit
  def poll(): A
}

object MailboxQueue {

  def apply[A](name: String): MailboxQueue[A] =
    name match {
      case "ConcurrentLinkedQueue" =>
        new MailboxQueue[A] {
          private val q          = new java.util.concurrent.ConcurrentLinkedQueue[A]()
          def add(data: A): Unit = q.add(data)
          def poll(): A          = q.poll()
        }
      case "Mailbox" =>
        new MailboxQueue[A] {
          private val q          = new Mailbox[A]()
          def add(data: A): Unit = q.add(data)
          def poll(): A          = q.poll()
        }
      case "MpscLinkedQueue" =>
        new MailboxQueue[A] {
          private val q          = new org.jctools.queues.MpscLinkedQueue[A]()
          def add(data: A): Unit = q.add(data)
          def poll(): A          = q.poll()
        }
      case _ =>
        throw new IllegalArgumentException(s"unsupported mailbox: $name")
    }
}
