package scalaz.zio.interop

import monix.eval
import monix.execution.Scheduler
import scalaz.zio.IO

object monixio {
  implicit class IOObjOps(private val obj: IO.type) extends AnyVal {
    def fromTask[A](task: eval.Task[A])(implicit scheduler: Scheduler): Task[A] =
      Task.fromFuture(scheduler)(Task(task.runToFuture))

    def fromCoeval[A](coeval: eval.Coeval[A]): Task[A] =
      IO.fromTry(coeval.runTry())
  }

  implicit class IOThrowableOps[A](private val io: Task[A]) extends AnyVal {
    def toTask: IO[Nothing, eval.Task[A]] =
      io.fold(eval.Task.raiseError, eval.Task.now)

    def toCoeval: IO[Nothing, eval.Coeval[A]] =
      io.fold(eval.Coeval.raiseError, eval.Coeval.now)
  }
}
