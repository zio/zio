package zio.internal

sealed trait MailboxWrapper[A] {
  def add(data: A): Unit
  def poll(): A
}

object MailboxWrapper {

  def apply[A](name: String): MailboxWrapper[A] =
    name match {
      case "ConcurrentLinkedQueue" =>
        new MailboxWrapper[A] {
          private val q          = new java.util.concurrent.ConcurrentLinkedQueue[A]()
          def add(data: A): Unit = q.add(data)
          def poll(): A          = q.poll()
        }
      case "Mailbox" =>
        new MailboxWrapper[A] {
          private val q          = new Mailbox[A]()
          def add(data: A): Unit = q.add(data)
          def poll(): A          = q.poll()
        }
      case "MpscLinkedQueue" =>
        new MailboxWrapper[A] {
          private val q          = new org.jctools.queues.MpscLinkedQueue[A]()
          def add(data: A): Unit = q.add(data)
          def poll(): A          = q.poll()
        }
      case _ =>
        throw new IllegalArgumentException(s"unsupported mailbox: $name")
    }
}
