package scalaz.zio.interop

import monix.eval.{ Task => MTask }
import monix.execution.Scheduler
import scalaz.zio.IO

object monixio {
  implicit class IOObjOps(private val obj: IO.type) extends AnyVal {
    def fromTask[A](task: MTask[A])(implicit scheduler: Scheduler): Task[A] =
      Task.fromFuture(Task(task.runToFuture))(scheduler)
  }

  implicit class IOThrowableOps[A](private val io: Task[A]) extends AnyVal {
    def toTask: IO[Nothing, MTask[A]] =
      io.redeemPure(MTask.raiseError, MTask.now)
  }
}
