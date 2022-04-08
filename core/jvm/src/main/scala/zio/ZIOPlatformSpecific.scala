/*
 * Copyright 2017-2022 John A. De Goes and the ZIO Contributors
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

package zio

import zio.interop.javaz
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.io
import java.io.IOException
import java.net.{URI, URL}
import java.nio.channels.CompletionHandler
import java.nio.file.Path
import java.util.concurrent.{CompletableFuture, CompletionStage, Future}

private[zio] trait ZIOPlatformSpecific[-R, +E, +A] { self: ZIO[R, E, A] =>
  def toCompletableFuture[A1 >: A](implicit
    ev: E IsSubtypeOfError Throwable,
    trace: ZTraceElement
  ): URIO[R, CompletableFuture[A1]] =
    toCompletableFutureWith(ev)

  def toCompletableFutureWith[A1 >: A](f: E => Throwable)(implicit
    trace: ZTraceElement
  ): URIO[R, CompletableFuture[A1]] =
    self.mapError(f).fold(javaz.CompletableFuture_.failedFuture, CompletableFuture.completedFuture[A1])
}

private[zio] trait ZIOCompanionPlatformSpecific {

  /**
   * Imports a synchronous effect that does blocking IO into a pure value.
   *
   * If the returned `ZIO` is interrupted, the blocked thread running the
   * synchronous effect will be interrupted via `Thread.interrupt`.
   *
   * Note that this adds significant overhead. For performance sensitive
   * applications consider using `attemptBlocking` or
   * `attemptBlockingCancelable`.
   */
  def attemptBlockingInterrupt[A](effect: => A)(implicit trace: ZTraceElement): Task[A] =
    ZIO.suspendSucceed {
      import java.util.concurrent.atomic.AtomicReference
      import java.util.concurrent.locks.ReentrantLock

      import zio.internal.OneShot

      val lock   = new ReentrantLock()
      val thread = new AtomicReference[Option[Thread]](None)
      val begin  = OneShot.make[Unit]
      val end    = OneShot.make[Unit]

      def withMutex[B](b: => B): B =
        try {
          lock.lock(); b
        } finally lock.unlock()

      val interruptThread: UIO[Unit] =
        ZIO.succeed {
          begin.get()

          var looping = true
          var n       = 0L
          val base    = 2L
          while (looping) {
            withMutex(thread.get match {
              case None         => looping = false; ()
              case Some(thread) => thread.interrupt()
            })

            if (looping) {
              n += 1
              Thread.sleep(math.min(50, base * n))
            }
          }

          end.get()
        }

      ZIO.blocking(
        ZIO.uninterruptibleMask(restore =>
          for {
            fiber <- ZIO.suspend {
                       val current = Some(Thread.currentThread)

                       withMutex(thread.set(current))

                       begin.set(())

                       try {
                         val a = effect

                         ZIO.succeedNow(a)
                       } catch {
                         case _: InterruptedException =>
                           Thread.interrupted // Clear interrupt status
                           ZIO.interrupt
                         case t: Throwable =>
                           ZIO.fail(t)
                       } finally withMutex { thread.set(None); end.set(()) }
                     }.forkDaemon
            a <- restore(fiber.join).ensuring(interruptThread)
          } yield a
        )
      )
    }

  def asyncWithCompletionHandler[T](op: CompletionHandler[T, Any] => Any)(implicit trace: ZTraceElement): Task[T] =
    javaz.asyncWithCompletionHandler(op)

  def fromCompletionStage[A](cs: => CompletionStage[A])(implicit trace: ZTraceElement): Task[A] =
    javaz.fromCompletionStage(cs)

  /**
   * Alias for `formCompletionStage` for a concrete implementation of
   * CompletionStage
   */
  def fromCompletableFuture[A](cs: => CompletableFuture[A])(implicit trace: ZTraceElement): Task[A] =
    fromCompletionStage(cs)

  /**
   * WARNING: this uses the blocking Future#get, consider using
   * `fromCompletionStage`
   */
  def fromFutureJava[A](future: => Future[A])(implicit trace: ZTraceElement): Task[A] = javaz.fromFutureJava(future)

  def readFile(path: Path)(implicit trace: ZTraceElement): ZIO[Scope, IOException, ZInputStream] =
    readFile(path.toString())

  def readFile(path: String)(implicit trace: ZTraceElement): ZIO[Scope, IOException, ZInputStream] =
    ZIO
      .acquireRelease(
        ZIO.attemptBlockingIO {
          val fis = new io.FileInputStream(path)
          (fis, ZInputStream.fromInputStream(fis))
        }
      )(tuple => ZIO.attemptBlocking(tuple._1.close()).orDie)
      .map(_._2)

  def readURL(url: URL)(implicit trace: ZTraceElement): ZIO[Scope, IOException, ZInputStream] =
    ZIO
      .acquireRelease(
        ZIO.attemptBlockingIO {
          val fis = url.openStream()
          (fis, ZInputStream.fromInputStream(fis))
        }
      )(tuple => ZIO.attemptBlocking(tuple._1.close()).orDie)
      .map(_._2)

  def readURL(url: String)(implicit trace: ZTraceElement): ZIO[Scope, IOException, ZInputStream] =
    ZIO.succeed(new URL(url)).flatMap(readURL)

  def readURI(uri: URI)(implicit trace: ZTraceElement): ZIO[Scope, IOException, ZInputStream] =
    for {
      isAbsolute <- ZIO.attemptBlockingIO(uri.isAbsolute())
      is         <- if (isAbsolute) readURL(uri.toURL()) else readFile(uri.toString())
    } yield is

  def writeFile(path: String)(implicit trace: ZTraceElement): ZIO[Scope, IOException, ZOutputStream] =
    ZIO
      .acquireRelease(
        ZIO.attemptBlockingIO {
          val fos = new io.FileOutputStream(path)
          (fos, ZOutputStream.fromOutputStream(fos))
        }
      )(tuple => ZIO.attemptBlocking(tuple._1.close()).orDie)
      .map(_._2)

  def writeFile(path: Path)(implicit trace: ZTraceElement): ZIO[Scope, IOException, ZOutputStream] =
    writeFile(path.toString())

}
