package scalaz.zio.interop

import com.twitter.util.{ Future, FutureCancelledException, Return, Throw }
import scalaz.zio.{ Task, UIO }

package object twitter {
  implicit class TaskObjOps(private val obj: Task.type) extends AnyVal {
    final def fromTwitterFuture[A](future: => Future[A]): Task[A] =
      Task.effectAsyncInterrupt { cb =>
        future.respond {
          case Return(a) => cb(Task.succeed(a))
          case Throw(e)  => cb(Task.fail(e))
        }

        Left(UIO(future.raise(new FutureCancelledException)))
      }
  }
}
