/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scalaz.zio.interop

import java.util.concurrent.{ CompletableFuture, CompletionException, CompletionStage, Future }

import scalaz.zio._
import scalaz.zio.blocking.{ blocking, Blocking }

import scala.concurrent.ExecutionException

object javaconcurrent {

  implicit class IOObjJavaconcurrentOps(private val ioObj: IO.type) extends AnyVal {

    private def unsafeCompletionStageToIO[A](cs: CompletionStage[A]): Task[A] =
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

    def fromCompletionStage[A](cs: () => CompletionStage[A]): Task[A] =
      IO.suspend {
        unsafeCompletionStageToIO(cs())
      }

    private def unsafeFutureJavaToIO[A](future: Future[A]): Task[A] = {
      def unwrap[B](f: Future[B]): Task[B] =
        IO.flatten {
          IO.defer {
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
        blocking(unwrap(future)).provide(Blocking.Live)
      }
    }

    def fromFutureJava[A, E >: Throwable](futureIo: IO[E, Future[A]]): IO[E, A] =
      futureIo.flatMap(unsafeFutureJavaToIO)

    def fromFutureJava[A](future: () => Future[A]): Task[A] =
      IO.suspend {
        unsafeFutureJavaToIO(future())
      }
  }

  implicit class FiberObjOps(private val fiberObj: Fiber.type) extends AnyVal {

    def fromFutureJava[A](_ftr: () => Future[A]): Fiber[Throwable, A] = {

      lazy val ftr = _ftr()

      new Fiber[Throwable, A] {

        def await: UIO[Exit[Throwable, A]] =
          IO.fromFutureJava(() => ftr).fold(Exit.fail, Exit.succeed)

        def poll: UIO[Option[Exit[Throwable, A]]] =
          IO.suspend {
            if (ftr.isDone) {
              IO.sync(ftr.get())
                .keepSome(JustExceptions)
                .fold(Exit.fail, Exit.succeed)
                .map(Some(_))
            } else {
              IO.succeed(None)
            }
          }

        def interrupt: UIO[Exit[Throwable, A]] =
          join.fold(Exit.fail, Exit.succeed)
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

  implicit class IOThrowableOps[A](private val io: Task[A]) extends AnyVal {
    def toCompletableFuture: UIO[CompletableFuture[A]] =
      io.fold(CompletableFuture_.failedFuture, CompletableFuture.completedFuture[A])
  }

  implicit class IOOps[E, A](private val io: IO[E, A]) extends AnyVal {
    def toCompletableFutureE(f: E => Throwable): UIO[CompletableFuture[A]] =
      io.mapError(f).toCompletableFuture
  }

}
