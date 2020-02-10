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

package zio.interop

import scala.concurrent.ExecutionException

import _root_.java.nio.channels.CompletionHandler
import _root_.java.util.concurrent.{ CompletableFuture, CompletionException, CompletionStage, Future }

import zio.Fiber.Status
import zio._
import zio.blocking.{ blocking, Blocking }

object javaz {
  def withCompletionHandler[T](op: CompletionHandler[T, Any] => Unit): Task[T] =
    Task.effectSuspendTotalWith[T] { (p, _) =>
      Task.effectAsync { k =>
        val handler = new CompletionHandler[T, Any] {
          def completed(result: T, u: Any): Unit = k(Task.succeed(result))

          def failed(t: Throwable, u: Any): Unit = t match {
            case e if !p.fatal(e) => k(Task.fail(e))
            case _                => k(Task.die(t))
          }
        }

        try {
          op(handler)
        } catch {
          case e if !p.fatal(e) => k(Task.fail(e))
        }
      }
    }

  private def catchFromGet(isFatal: Throwable => Boolean): PartialFunction[Throwable, Task[Nothing]] = {
    case e: CompletionException =>
      Task.fail(e.getCause)
    case e: ExecutionException =>
      Task.fail(e.getCause)
    case _: InterruptedException =>
      Task.interrupt
    case e if !isFatal(e) =>
      Task.fail(e)
  }

  private def unwrapDone[A](isFatal: Throwable => Boolean)(f: Future[A]): Task[A] =
    try {
      Task.succeedNow(f.get())
    } catch catchFromGet(isFatal)

  def fromCompletionStage[A](csUio: UIO[CompletionStage[A]]): Task[A] =
    csUio.flatMap { cs =>
      Task.effectSuspendTotalWith { (p, _) =>
        val cf = cs.toCompletableFuture
        if (cf.isDone) {
          unwrapDone(p.fatal)(cf)
        } else {
          Task.effectAsync { cb =>
            val _ = cs.handle[Unit] { (v: A, t: Throwable) =>
              val io = Option(t).fold[Task[A]](Task.succeed(v)) { t =>
                catchFromGet(p.fatal).lift(t).getOrElse(Task.die(t))
              }
              cb(io)
            }
          }
        }
      }
    }

  /** WARNING: this uses the blocking Future#get, consider using `fromCompletionStage` */
  def fromFutureJava[A](futureUio: UIO[Future[A]]): RIO[Blocking, A] =
    futureUio.flatMap { future =>
      RIO.effectSuspendTotalWith { (p, _) =>
        if (future.isDone) {
          unwrapDone(p.fatal)(future)
        } else {
          blocking(Task.effectSuspend(unwrapDone(p.fatal)(future)))
        }
      }
    }

  implicit class CompletionStageJavaconcurrentOps[A](private val csUio: UIO[CompletionStage[A]]) extends AnyVal {
    def toZio: Task[A] = ZIO.fromCompletionStage(csUio)
  }

  implicit class FutureJavaconcurrentOps[A](private val futureUio: UIO[Future[A]]) extends AnyVal {

    /** WARNING: this uses the blocking Future#get, consider using `CompletionStage` */
    def toZio: RIO[Blocking, A] = ZIO.fromFutureJava(futureUio)
  }

  implicit class ZioObjJavaconcurrentOps(private val zioObj: ZIO.type) extends AnyVal {
    def withCompletionHandler[T](op: CompletionHandler[T, Any] => Unit): Task[T] =
      javaz.withCompletionHandler(op)

    def fromCompletionStage[A](csUio: UIO[CompletionStage[A]]): Task[A] = javaz.fromCompletionStage(csUio)

    /** WARNING: this uses the blocking Future#get, consider using `fromCompletionStage` */
    def fromFutureJava[A](futureUio: UIO[Future[A]]): RIO[Blocking, A] = javaz.fromFutureJava(futureUio)
  }

  implicit class FiberObjOps(private val fiberObj: Fiber.type) extends AnyVal {
    def fromCompletionStage[A](thunk: => CompletionStage[A]): Fiber[Throwable, A] = {
      lazy val cs: CompletionStage[A] = thunk

      new Fiber.Synthetic[Throwable, A] {
        override def await: UIO[Exit[Throwable, A]] = ZIO.fromCompletionStage(UIO.effectTotal(cs)).run

        override def poll: UIO[Option[Exit[Throwable, A]]] =
          UIO.effectSuspendTotal {
            val cf = cs.toCompletableFuture
            if (cf.isDone) {
              Task
                .effectSuspendWith((p, _) => unwrapDone(p.fatal)(cf))
                .fold(Exit.fail, Exit.succeed)
                .map(Some(_))
            } else {
              UIO.succeedNow(None)
            }
          }

        final def children: UIO[Iterable[Fiber[Any, Any]]] = UIO(Nil)

        final def getRef[A](ref: FiberRef[A]): UIO[A] = UIO(ref.initial)

        final def interruptAs(id: Fiber.Id): UIO[Exit[Throwable, A]] = join.fold(Exit.fail, Exit.succeed)

        final def inheritRefs: UIO[Unit] = IO.unit

        final def status: UIO[Fiber.Status] = UIO {
          // TODO: Avoid toCompletableFuture?
          if (thunk.toCompletableFuture.isDone) Status.Done else Status.Running
        }
      }
    }

    /** WARNING: this uses the blocking Future#get, consider using `fromCompletionStage` */
    def fromFutureJava[A](thunk: => Future[A]): Fiber[Throwable, A] = {
      lazy val ftr: Future[A] = thunk

      new Fiber.Synthetic[Throwable, A] {
        def await: UIO[Exit[Throwable, A]] =
          Blocking.live.value.use(ZIO.fromFutureJava(UIO.effectTotal(ftr)).provide(_).run)

        def poll: UIO[Option[Exit[Throwable, A]]] =
          UIO.effectSuspendTotal {
            if (ftr.isDone) {
              Task
                .effectSuspendWith((p, _) => unwrapDone(p.fatal)(ftr))
                .fold(Exit.fail, Exit.succeed)
                .map(Some(_))
            } else {
              UIO.succeed(None)
            }
          }

        def children: UIO[Iterable[Fiber[Any, Any]]] = UIO(Nil)

        def getRef[A](ref: FiberRef[A]): UIO[A] = UIO(ref.initial)

        def interruptAs(id: Fiber.Id): UIO[Exit[Throwable, A]] = join.fold(Exit.fail, Exit.succeed)

        def inheritRefs: UIO[Unit] = UIO.unit

        def status: UIO[Fiber.Status] = UIO {
          if (thunk.isDone) Status.Done else Status.Running
        }
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

  implicit class TaskCompletableFutureOps[A](private val io: Task[A]) extends AnyVal {
    def toCompletableFuture: UIO[CompletableFuture[A]] =
      io.fold(CompletableFuture_.failedFuture, CompletableFuture.completedFuture[A])
  }

  implicit class IOCompletableFutureOps[E, A](private val io: IO[E, A]) extends AnyVal {
    def toCompletableFutureWith(f: E => Throwable): UIO[CompletableFuture[A]] =
      io.mapError(f).toCompletableFuture
  }
}
