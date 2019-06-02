package zio.interop

import _root_.monix.eval
import _root_.monix.execution.Scheduler
import zio.{ IO, Task, UIO }

object monix {
  implicit class IOObjOps(private val obj: IO.type) extends AnyVal {
    def fromTask[A](task: eval.Task[A])(implicit scheduler: Scheduler): Task[A] =
      Task.fromFuture(_ => task.runToFuture)

    def fromCoeval[A](coeval: eval.Coeval[A]): Task[A] =
      Task.fromTry(coeval.runTry())
  }

  implicit class TaskOps[A](private val io: Task[A]) extends AnyVal {
    def toTask: UIO[eval.Task[A]] =
      io.fold(eval.Task.raiseError, eval.Task.now)

    def toCoeval: UIO[eval.Coeval[A]] =
      io.fold(eval.Coeval.raiseError, eval.Coeval.now)
  }
}
