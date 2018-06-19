package scalaz.zio
package interop

import scala.concurrent.Future

object future {

  implicit class IOObjOps(private val ioObj: IO.type) extends AnyVal {
    def fromFuture[A](ftr: () => Future[A]): IO[Throwable, A] = {
      val _ = ftr() // avoid warning
      ???
    }
  }

  implicit class IOThrowableOps[A](private val io: IO[Throwable, A]) extends AnyVal {
    def toFuture: IO[Void, Future[A]] = ???
  }

  implicit class IOOps[E, A](private val io: IO[E, A]) extends AnyVal {
    def toFuture(f: E => Throwable): IO[Void, Future[A]] = io.leftMap(f).toFuture
  }

}

object SampleUsage {
  import future._

  val ec = scala.concurrent.ExecutionContext.Implicits.global

  def unsafePerformIO[E, A](io: IO[E, A]): A = {
    val _ = io // avoid warning
    ???
  }

  def du: Future[Int] = Future(42)(ec)

  val duHast: IO[Throwable, Int] = IO.fromFuture(du _)

  val duHastMich: IO[Void, Future[Int]] = duHast.toFuture

  val duHastMichGefragt: Future[Int] = unsafePerformIO(duHastMich)

}
