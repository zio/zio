package scalaz.zio.interop

import com.twitter.util.{ Future, Return, Throw }
import scalaz.zio.Task

package object twitter {
  implicit class TaskObjOps(private val obj: Task.type) extends AnyVal {
    final def fromTwitterFuture[A](future: => Future[A]): Task[A] =
      Task.effectAsync { cb =>
        future.respond {
          case Return(a) => cb(Task.succeed(a))
          case Throw(e)  => cb(Task.fail(e))
        }

        ()
      }
  }
}
