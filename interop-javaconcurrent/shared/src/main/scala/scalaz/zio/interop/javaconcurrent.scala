package scalaz.zio.interop

import java.util.concurrent.{ CompletableFuture, CompletionException, CompletionStage, Future }

import scalaz.zio.{ Exit, Fiber, IO }

import scala.concurrent.ExecutionException

object javaconcurrent {

  implicit class IOObjJavaconcurrentOps(private val ioObj: IO.type) extends AnyVal {

    private def unsafeCompletionStageToIO[A](cs: CompletionStage[A]): IO[Throwable, A] =
      IO.async { cb =>
        val _ = cs.handle[Unit] { (v: A, t: Throwable) =>
          if (v != null) {
            cb(IO.succeed(v))
          } else {
            val io = t match {
              case e: CompletionException =>
                IO.fail(e.getCause)
              case t: Throwable =>
                IO.fail(t)
            }
            cb(io)
          }
        }
      }

    def fromCompletionStage[A, E >: Throwable](csIo: IO[E, CompletionStage[A]]): IO[E, A] =
      csIo.flatMap(unsafeCompletionStageToIO)

    def fromCompletionStage[A](cs: () => CompletionStage[A]): IO[Throwable, A] =
      IO.suspend {
        unsafeCompletionStageToIO(cs())
      }

    private def unsafeFutureJavaToIO[A](future: Future[A]): IO[Throwable, A] = {
      def unwrap[B](f: Future[B]): IO[Throwable, B] =
        IO.flatten {
          IO.sync {
            try {
              val result = f.get()
              IO.succeed(result)
            } catch {
              case e: ExecutionException =>
                IO.fail(e.getCause)
              case _: InterruptedException =>
                IO.interrupt
              case t: Throwable => // CancellationException
                IO.fail(t)
            }
          }
        }

      if (future.isDone) {
        unwrap(future)
      } else {
        IO.unyielding(unwrap(future))
      }
    }

    def fromFutureJavaIO[A, E >: Throwable](futureIo: IO[E, Future[A]]): IO[E, A] =
      futureIo.flatMap(unsafeFutureJavaToIO)

    def fromFutureJava[A](future: () => Future[A]): IO[Throwable, A] =
      IO.suspend {
        unsafeFutureJavaToIO(future())
      }
  }

  implicit class FiberObjOps(private val fiberObj: Fiber.type) extends AnyVal {

    def fromFutureJava[A](_ftr: () => Future[A]): Fiber[Throwable, A] = {

      lazy val ftr = _ftr()

      new Fiber[Throwable, A] {

        def await: IO[Nothing, Exit[Throwable, A]] =
          IO.fromFutureJava(() => ftr).redeemPure(Exit.checked, Exit.succeed)

        def poll: IO[Nothing, Option[Exit[Throwable, A]]] =
          IO.suspend {
            if (ftr.isDone) {
              IO.syncException(ftr.get())
                .redeemPure(Exit.checked, Exit.succeed)
                .map(Some(_))
            } else {
              IO.succeed(None)
            }
          }

        def interrupt: IO[Nothing, Exit[Throwable, A]] =
          join.redeemPure(Exit.checked, Exit.succeed)
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
