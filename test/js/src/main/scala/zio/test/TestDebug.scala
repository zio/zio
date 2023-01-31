package zio.test

import zio.{Ref, ZIO}

// TODO Implement this with appropriate JS filesystem APIs after JVM version is finalized
private[test] object TestDebug {
  def printDebug(executionEvent: ExecutionEvent, lock: Ref.Synchronized[Unit]) =
    executionEvent match {
      case t: ExecutionEvent.TestStarted =>
        write(t.fullyQualifiedName, s"${t.labels.mkString(" - ")} STARTED\n", true, lock)

      case t: ExecutionEvent.Test =>
        removeLine(t.fullyQualifiedName, t.labels.mkString(" - ") + " STARTED", lock)

      case _ => ZIO.unit
    }

  private def write(content: => String, append: Boolean, lock: Ref.Synchronized[Unit]): ZIO[Any, Nothing, Unit] =
    ZIO.unit

  private def removeLine(searchString: String) =
    ZIO.unit

}
