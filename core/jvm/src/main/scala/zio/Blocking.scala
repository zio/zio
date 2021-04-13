/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

import zio.internal.tracing.ZIOFn
import zio.internal.{Executor, NamedThreadFactory}

import java.io.IOException
import java.util.concurrent._

/**
 * The `Blocking` module provides access to a thread pool that can be used for performing
 * blocking operations, such as thread sleeps, synchronous socket/file reads, and so forth.
 * The contract is that the thread pool will accept unlimited tasks (up to the available memory)
 * and continuously create new threads as necessary.
 */
trait Blocking extends Serializable {

  /**
   * Locks the specified effect to the blocking thread pool.
   */
  def blocking[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    zio.lock(blockingExecutor)

  /**
   * Retrieves the executor for all blocking tasks.
   */
  def blockingExecutor: Executor

  /**
   * Imports a synchronous effect that does blocking IO into a pure value.
   */
  def effectBlocking[A](effect: => A): Task[A] =
    blocking(ZIO.effect(effect))

  /**
   * Imports a synchronous effect that does blocking IO into a pure value,
   * with a custom cancel effect.
   *
   * If the returned `ZIO` is interrupted, the blocked thread running the
   * synchronous effect will be interrupted via the cancel effect.
   */
  def effectBlockingCancelable[A](effect: => A)(cancel: UIO[Unit]): Task[A] =
    blocking(ZIO.effect(effect)).fork.flatMap(_.join).onInterrupt(cancel)

  /**
   * Imports a synchronous effect that does blocking IO into a pure value.
   *
   * If the returned `ZIO` is interrupted, the blocked thread running the
   * synchronous effect will be interrupted via `Thread.interrupt`.
   *
   * Note that this adds significant overhead. For performance sensitive
   * applications consider using `effectBlocking` or
   * `effectBlockingCancel`.
   */
  def effectBlockingInterrupt[A](effect: => A): Task[A] =
    // Reference user's lambda for the tracer
    ZIOFn.recordTrace(() => effect) {
      ZIO.effectSuspendTotal {
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
          ZIO.effectTotal {
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

        blocking(
          ZIO.uninterruptibleMask(restore =>
            for {
              fiber <- ZIO.effectSuspend {
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
                         } finally {
                           withMutex { thread.set(None); end.set(()) }
                         }
                       }.forkDaemon
              a <- restore(fiber.join.refailWithTrace).ensuring(interruptThread)
            } yield a
          )
        )
      }
    }

  /**
   * Imports a synchronous effect that does blocking IO into a pure value,
   * refining the error type to `[[java.io.IOException]]`.
   */
  def effectBlockingIO[A](effect: => A): IO[IOException, A] =
    effectBlocking(effect).refineToOrDie[IOException]
}

object Blocking extends Serializable {

  // Layer Definitions

  val any: ZLayer[Has[Blocking], Nothing, Has[Blocking]] =
    ZLayer.service[Blocking]

  val live: Layer[Nothing, Has[Blocking]] =
    ZLayer.succeed(BlockingLive)

  object BlockingLive extends Blocking {
    override val blockingExecutor: Executor = internal.blockingExecutor0
  }

  private[zio] object internal {
    private[zio] val blockingExecutor0 =
      Executor.fromThreadPoolExecutor(_ => Int.MaxValue) {
        val corePoolSize  = 0
        val maxPoolSize   = 1000
        val keepAliveTime = 60000L
        val timeUnit      = TimeUnit.MILLISECONDS
        val workQueue     = new SynchronousQueue[Runnable]()
        val threadFactory = new NamedThreadFactory("zio-default-blocking", true)

        val threadPool = new ThreadPoolExecutor(
          corePoolSize,
          maxPoolSize,
          keepAliveTime,
          timeUnit,
          workQueue,
          threadFactory
        )

        threadPool
      }
  }

  // Accessor Methods

  /**
   * Locks the specified effect to the blocking thread pool.
   */
  def blocking[R <: Has[Blocking], E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.accessM(_.get.blocking(zio))

  /**
   * Retrieves the executor for all blocking tasks.
   */
  def blockingExecutor: URIO[Has[Blocking], Executor] =
    ZIO.access(_.get.blockingExecutor)

  /**
   * Retrieves the executor for all blocking tasks.
   */
  def effectBlocking[A](effect: => A): RIO[Has[Blocking], A] =
    ZIO.serviceWith(_.effectBlocking(effect))

  /**
   * Imports a synchronous effect that does blocking IO into a pure value, with
   * a custom cancel effect.
   *
   * If the returned `ZIO` is interrupted, the blocked thread running the
   * synchronous effect will be interrupted via the cancel effect.
   */
  def effectBlockingCancelable[A](effect: => A)(cancel: UIO[Unit]): RIO[Has[Blocking], A] =
    ZIO.serviceWith(_.effectBlockingCancelable(effect)(cancel))

  /**
   * Imports a synchronous effect that does blocking IO into a pure value.
   *
   * If the returned `ZIO` is interrupted, the blocked thread running the
   * synchronous effect will be interrupted via `Thread.interrupt`.
   *
   * Note that this adds significant overhead. For performance sensitive
   * applications consider using `effectBlocking` or `effectBlockingCancel`.
   */
  def effectBlockingInterrupt[A](effect: => A): RIO[Has[Blocking], A] =
    ZIO.serviceWith(_.effectBlockingInterrupt(effect))

  /**
   * Imports a synchronous effect that does blocking IO into a pure value,
   * refining the error type to `[[java.io.IOException]]`.
   */
  def effectBlockingIO[A](effect: => A): ZIO[Has[Blocking], IOException, A] =
    ZIO.serviceWith(_.effectBlockingIO(effect))
}
