package scalaz.zio.interop

import java.util.concurrent.{ CompletionStage, Future }

import scalaz.zio.IO

object javaconcurrent {

  implicit class IOObjJavaconcurrentOps(private val ioObj: IO.type) extends AnyVal {

    private def unsafeCompletionStageToIO[A](cs: CompletionStage[A]): IO[Throwable, A] =
      IO.async { cb =>
        val _ = cs.handle[Unit] { (v: A, t: Throwable) =>
          if (v != null) {
            cb(IO.now(v))
          } else {
            cb(IO.fail(t))
          }
        }
      }

    def fromCompletionStage[A, E >: Throwable](csIo: IO[E, CompletionStage[A]]): IO[E, A] =
      csIo.flatMap(unsafeCompletionStageToIO)

    def fromCompletionStage[A](cs: () => CompletionStage[A]): IO[Throwable, A] =
      IO.suspend {
        unsafeCompletionStageToIO(cs())
      }

    private def unsafeFutureJavaToIO[A](future: Future[A]): IO[Exception, A] =
      IO.syncException(future.get())

    def fromFutureJava[A, E >: Exception](futureIo: IO[E, Future[A]]): IO[E, A] =
      futureIo.flatMap(unsafeFutureJavaToIO)

    def fromFutureJava[A](future: () => Future[A]): IO[Exception, A] =
      IO.suspend {
        unsafeFutureJavaToIO(future())
      }
  }
}
