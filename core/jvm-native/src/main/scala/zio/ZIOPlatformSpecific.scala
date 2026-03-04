/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

import zio.internal.OneShot
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.io
import java.io.IOException
import java.net.{URI, URL}
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import scala.runtime.BoxedUnit

private[zio] trait ZIOPlatformSpecific[-R, +E, +A] { self: ZIO[R, E, A] =>
  def toCompletableFuture[A1 >: A](implicit
    ev: E IsSubtypeOfError Throwable,
    trace: Trace
  ): URIO[R, CompletableFuture[A1]] =
    toCompletableFutureWith(ev)

  def toCompletableFutureWith[A1 >: A](f: E => Throwable)(implicit
    trace: Trace
  ): URIO[R, CompletableFuture[A1]] =
    ZIO.uninterruptibleMask { restore =>
      for {
        future <- ZIO.succeed(new CompletableFuture[A1])
        _ <- restore(self)
               .foldCause(
                 cause => future.completeExceptionally(cause.squashTraceWith(f)),
                 a => future.complete(a)
               )
               .fork
      } yield future
    }
}

private[zio] trait ZIOCompanionPlatformSpecific extends ZIOPlatformSpecificJVM {

  /**
   * Imports a synchronous effect that does blocking IO into a pure value.
   *
   * If the returned `ZIO` is interrupted, the blocked thread running the
   * synchronous effect will be interrupted via `Thread.interrupt`.
   *
   * `Thread.interrupt` will be called continuously every 50 milliseconds, until
   * the target thread is unwound. This is done in attempt to guarantee thread
   * interruption in presence of misbehaving underlying code, but is done at
   * risk of possible resource leaks if interrupts aren't handled properly.
   *
   * Note that this adds significant overhead. For performance sensitive
   * applications consider using `attemptBlocking` or
   * `attemptBlockingCancelable`.
   *
   * @see
   *   [[attemptBlockingInterruptOnce]] for a version that uses
   *   `Thread.interrupt` only once to avoid resource leaks.
   */
  def attemptBlockingInterrupt[A](effect: => A)(implicit trace: Trace): Task[A] =
    attemptBlockingInterruptImpl(once = false, effect)

  /**
   * Imports a synchronous effect that does blocking IO into a pure value.
   *
   * If the returned `ZIO` is interrupted, the blocked thread running the
   * synchronous effect will be interrupted via `Thread.interrupt`.
   *
   * `Thread.interrupt` will be called only once on the target thread. If
   * swallowed by misbehaving code, the thread will still linger on, but if the
   * underlying code handles interrupts well, this would allow it to perform all
   * necessary cleanups.
   *
   * Note that this adds significant overhead. For performance sensitive
   * applications consider using `attemptBlocking` or
   * `attemptBlockingCancelable`.
   *
   * @see
   *   [[attemptBlockingInterrupt]] for a version that calls `Thread.interrupt`
   *   continuously to attempt to rule out target thread lingering
   */
  def attemptBlockingInterruptOnce[A](effect: => A)(implicit trace: Trace): Task[A] =
    attemptBlockingInterruptImpl(once = true, effect)

  @inline private[this] def attemptBlockingInterruptImpl[A](once: Boolean, effect: => A)(implicit
    trace: Trace
  ): Task[A] =
    ZIO.uninterruptibleMask { restore =>
      val threadState =
        if (once) new ThreadInterruptionStrategy.Once()(trace, Unsafe)
        else new ThreadInterruptionStrategy.Continuously()

      for {
        fiber <- ZIO.blocking {
                   threadState.signalBegin(Thread.currentThread)

                   try {
                     Exit.succeed(effect)
                   } catch {
                     case _: InterruptedException =>
                       ZIO.interrupt
                     case t if nonFatal(t) =>
                       ZIO.fail(t)
                   } finally {
                     threadState.signalEnd()

                     Thread.interrupted() // Clear interrupt status
                   }
                 }.forkDaemon
        a <- restore(fiber.await).exitWith {
               case Exit.Success(exit)       => exit
               case f: Exit.Failure[Nothing] => threadState.interruptThread() *> f
             }
      } yield a
    }

  private[this] sealed abstract class ThreadInterruptionStrategy {
    def signalBegin(thread: Thread): Unit
    def signalEnd(): Unit
    def interruptThread(): UIO[Unit]
  }

  private[this] object ThreadInterruptionStrategy {
    final class Once()(implicit trace: Trace, unsafe: Unsafe) extends ThreadInterruptionStrategy {
      private val begin: Promise[Nothing, Thread] = Promise.unsafe.make(FiberId.None)
      private val end: Promise[Nothing, Unit]     = Promise.unsafe.make(FiberId.None)

      override def signalBegin(thread: Thread): Unit = begin.unsafe.done(Exit.Success(thread))
      override def signalEnd(): Unit                 = end.unsafe.succeed(())
      override def interruptThread(): UIO[Unit] = ZIO.suspendSucceed {
        (begin.unsafe.poll match {
          case Some(Exit.Success(thread)) =>
            thread.interrupt()
            ZIO.unit
          case None =>
            begin.await.flatMap(thread => ZIO.succeed(thread.interrupt()))
        }) *> end.await
      }
    }

    final class Continuously()(implicit trace: Trace) extends ThreadInterruptionStrategy {
      private val begin: OneShot[Thread] = OneShot.make[Thread]
      private val end: OneShot[Object]   = OneShot.make[Object]

      override def signalBegin(thread: Thread): Unit = begin.set(thread)
      override def signalEnd(): Unit                 = end.set(BoxedUnit.UNIT)

      /**
       * Interrupts the thread running the blocking effect using thread
       * interruption.
       *
       * This effect is run in the blocking threadpool because begin.get() and
       * end.tryGet() hard-block the thread.
       */
      override def interruptThread(): UIO[Unit] =
        ZIO.succeedBlocking {
          val thread  = begin.get()
          var n       = 0L
          var looping = !end.isSet
          while (looping) {
            end.lock()
            try {
              // `end` cannot be set while we're here, so we can safely interrupt
              if (!end.isSet) thread.interrupt()
            } finally {
              end.unlock()
            }

            n += 1
            looping = end.tryGet(math.min(50, 2L * n)) eq null
          }
        }
    }
  }

  def readFile(path: => Path)(implicit trace: Trace, d: DummyImplicit): ZIO[Any, IOException, String] =
    readFile(path.toString)

  def readFile(name: => String)(implicit trace: Trace): ZIO[Any, IOException, String] =
    ZIO.acquireReleaseWith(ZIO.attemptBlockingIO(scala.io.Source.fromFile(name)))(s =>
      ZIO.attemptBlocking(s.close()).orDie
    ) { s =>
      ZIO.attemptBlockingIO(s.mkString)
    }

  def readFileInputStream(
    path: => Path
  )(implicit trace: Trace, d: DummyImplicit): ZIO[Scope, IOException, ZInputStream] =
    readFileInputStream(path.toString)

  def readFileInputStream(
    name: => String
  )(implicit trace: Trace): ZIO[Scope, IOException, ZInputStream] =
    ZIO
      .acquireRelease(
        ZIO.attemptBlockingIO {
          val fis = new io.FileInputStream(name)
          (fis, ZInputStream.fromInputStream(fis))
        }
      )(tuple => ZIO.attemptBlocking(tuple._1.close()).orDie)
      .map(_._2)

  def readURLInputStream(
    url: => URL
  )(implicit trace: Trace, d: DummyImplicit): ZIO[Scope, IOException, ZInputStream] =
    ZIO
      .acquireRelease(
        ZIO.attemptBlockingIO {
          val fis = url.openStream()
          (fis, ZInputStream.fromInputStream(fis))
        }
      )(tuple => ZIO.attemptBlocking(tuple._1.close()).orDie)
      .map(_._2)

  def readURLInputStream(
    url: => String
  )(implicit trace: Trace): ZIO[Scope, IOException, ZInputStream] =
    ZIO.succeed(new URL(url)).flatMap(readURLInputStream(_))

  def readURIInputStream(uri: => URI)(implicit trace: Trace): ZIO[Scope, IOException, ZInputStream] =
    for {
      uri        <- ZIO.succeed(uri)
      isAbsolute <- ZIO.attemptBlockingIO(uri.isAbsolute)
      is         <- if (isAbsolute) readURLInputStream(uri.toURL) else readFileInputStream(uri.toString)
    } yield is

  def writeFile(path: => String, content: => String)(implicit trace: Trace): ZIO[Any, IOException, Unit] =
    ZIO.acquireReleaseWith(ZIO.attemptBlockingIO(new java.io.FileWriter(path)))(f =>
      ZIO.attemptBlocking(f.close()).orDie
    ) { f =>
      ZIO.attemptBlockingIO(f.write(content))
    }

  def writeFile(path: => Path, content: => String)(implicit
    trace: Trace,
    d: DummyImplicit
  ): ZIO[Any, IOException, Unit] =
    writeFile(path.toString, content)

  def writeFileOutputStream(
    path: => String
  )(implicit trace: Trace): ZIO[Scope, IOException, ZOutputStream] =
    ZIO
      .acquireRelease(
        ZIO.attemptBlockingIO {
          val fos = new io.FileOutputStream(path)
          (fos, ZOutputStream.fromOutputStream(fos))
        }
      )(tuple => ZIO.attemptBlocking(tuple._1.close()).orDie)
      .map(_._2)

  def writeFileOutputStream(
    path: => Path
  )(implicit trace: Trace, d: DummyImplicit): ZIO[Scope, IOException, ZOutputStream] =
    writeFileOutputStream(path.toString)

}
