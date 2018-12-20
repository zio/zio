package scalaz.zio.interop

import scalaz.zio.{ Exit, FiberFailure, IO, RTS }
import scala.concurrent.{ Future, Promise }
import scala.util.{ Failure, Success }

import scala.language.implicitConversions

protected[interop] object FuturePlatformSpecific {
  class RTSOps(private val rts: RTS) extends AnyVal {
    def unsafeToFuture[E <: Throwable, A](io: IO[E, A]): Future[A] = {
      val p = Promise[A]()

      rts.unsafeRunAsync(io) {
        case Exit.Success(v) =>
          p.complete(Success(v))
          ()
        case Exit.Failure(Exit.Cause.Checked(e)) =>
          p.complete(Failure(e))
          ()
        case Exit.Failure(c) =>
          p.complete(Failure(FiberFailure(c)))
          ()
      }

      p.future
    }
  }
}

protected[interop] trait FuturePlatformSpecific {
  implicit def FutureRTSOps(rts: RTS): FuturePlatformSpecific.RTSOps =
    new FuturePlatformSpecific.RTSOps(rts)
}
