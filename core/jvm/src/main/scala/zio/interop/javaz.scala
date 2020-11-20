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

import zio._
import zio.blocking.{ Blocking, blocking }

private[zio] object javaz {
  def effectAsyncWithCompletionHandler[T](op: CompletionHandler[T, Any] => Any): Task[T] =
    Task.effectSuspendTotalWith[T] { (p, _) =>
      Task.effectAsync { k =>
        val handler = new CompletionHandler[T, Any] {
          def completed(result: T, u: Any): Unit = k(Task.succeedNow(result))

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

  def unwrapDone[A](isFatal: Throwable => Boolean)(f: Future[A]): Task[A] =
    try {
      Task.succeedNow(f.get())
    } catch catchFromGet(isFatal)

  def fromCompletionStage[A](thunk: => CompletionStage[A]): Task[A] =
    Task.effect(thunk).flatMap { cs =>
      Task.effectSuspendTotalWith { (p, _) =>
        val cf = cs.toCompletableFuture
        if (cf.isDone) {
          unwrapDone(p.fatal)(cf)
        } else {
          Task.effectAsync { cb =>
            cs.handle[Unit] { (v: A, t: Throwable) =>
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
  def fromFutureJava[A](thunk: => Future[A]): RIO[Blocking, A] =
    RIO.effect(thunk).flatMap { future =>
      RIO.effectSuspendTotalWith { (p, _) =>
        if (future.isDone) {
          unwrapDone(p.fatal)(future)
        } else {
          blocking(Task.effectSuspend(unwrapDone(p.fatal)(future)))
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
}
