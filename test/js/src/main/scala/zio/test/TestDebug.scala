package zio.test

import zio.{Ref, ZIO}

// TODO Implement this with appropriate JS filesystem APIs after JVM version is finalized
private[test] object TestDebug {
  def print(executionEvent: ExecutionEvent, lock: TestDebugFileLock) =
    executionEvent match {
      case t: ExecutionEvent.TestStarted =>
        write(t.fullyQualifiedName, s"${t.labels.mkString(" - ")} STARTED\n", true, lock)

      case t: ExecutionEvent.Test[_] =>
        removeLine(t.fullyQualifiedName, t.labels.mkString(" - ") + " STARTED", lock)

      case _ => ZIO.unit
    }

  private def write(
    fullyQualifiedTaskName: String,
    content: => String,
    append: Boolean,
    lock: TestDebugFileLock
  ): ZIO[Any, Nothing, Unit] =
    ZIO.unit

  private def removeLine(fullyQualifiedTaskName: String, searchString: String, lock: TestDebugFileLock) =
    ZIO.unit

  def createDebugFile(fullyQualifiedTaskName: String): ZIO[Any, Nothing, Unit] =
    ZIO.unit

  def deleteIfEmpty(fullyQualifiedTaskName: String): ZIO[Any, Nothing, Unit] = ZIO.unit
}
