package scalaz.zio.interop

import java.util.concurrent.{ CompletableFuture, CompletionStage, Future }

import scalaz.zio.{ ExitResult, Fiber, IO }

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
      if (future.isDone) {
        IO.syncException(future.get())
      } else {
        IO.unyielding(IO.syncException(future.get()))
      }

    def fromFutureJavaIO[A, E >: Exception](futureIo: IO[E, Future[A]]): IO[E, A] =
      futureIo.flatMap(unsafeFutureJavaToIO)

    def fromFutureJava[A](future: () => Future[A]): IO[Exception, A] =
      IO.suspend {
        unsafeFutureJavaToIO(future())
      }
  }

  implicit class FiberObjOps(private val fiberObj: Fiber.type) extends AnyVal {

    def fromFutureJava[A](_ftr: () => Future[A]): Fiber[Throwable, A] = {

      lazy val ftr = _ftr()

      new Fiber[Throwable, A] {

        def observe: IO[Nothing, ExitResult[Throwable, A]] =
          IO.fromFutureJava(() => ftr).redeemPure(ExitResult.checked, ExitResult.succeeded)

        def poll: IO[Nothing, Option[ExitResult[Throwable, A]]] =
          IO.suspend {
            if (ftr.isDone) {
              IO.syncException(ftr.get())
                .redeemPure(ExitResult.checked, ExitResult.succeeded)
                .map(Some(_))
            } else {
              IO.now(None)
            }
          }

        def interrupt: IO[Nothing, ExitResult[Throwable, A]] =
          join.redeemPure(ExitResult.checked, ExitResult.succeeded)
      }
    }
  }

  /**
   * CompletableFuture#failedFuture(Throwable) available only since Java 9
   */
  object CompletableFuture_ {
    def failedFuture[A](e: Throwable): CompletableFuture[A] = {
      val f = new CompletableFuture[A]
      f.completeExceptionally(e)
      f
    }
  }

  implicit class IOThrowableOps[A](private val io: IO[Throwable, A]) extends AnyVal {
    def toCompletableFuture: IO[Nothing, CompletableFuture[A]] =
      io.redeemPure(CompletableFuture_.failedFuture, CompletableFuture.completedFuture[A])
  }

  implicit class IOOps[E, A](private val io: IO[E, A]) extends AnyVal {
    def toCompletableFutureE(f: E => Throwable): IO[Nothing, CompletableFuture[A]] =
      io.leftMap(f).toCompletableFuture
  }

}
