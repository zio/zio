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

package scalaz.zio.blocking

import java.util.concurrent._

import scalaz.zio.ZIO
import scalaz.zio.internal.{ Executor, NamedThreadFactory }
import scalaz.zio.internal.PlatformLive

/**
 * The `Blocking` module provides access to a thread pool that can be used for performing
 * blocking operations, such as thread sleeps, synchronous socket/file reads, and so forth.
 * The contract is that the thread pool will accept unlimited tasks (up to the available)
 * memory, and continuously create new threads as necessary.
 */
trait Blocking extends Serializable {
  val blocking: Blocking.Service[Any]
}
object Blocking extends Serializable {
  trait Service[R] extends Serializable {

    /**
     * Retrieves the executor for all blocking tasks.
     */
    def blockingExecutor: ZIO[R, Nothing, Executor]

    /**
     * Locks the specified task to the blocking thread pool.
     */
    def blocking[R1 <: R, E, A](zio: ZIO[R1, E, A]): ZIO[R1, E, A] =
      blockingExecutor.flatMap(exec => zio.lock(exec))

    /**
     * Imports a synchronous effect that does blocking IO into a pure value.
     *
     * If the returned `IO` is interrupted, the blocked thread running the synchronous effect
     * will be interrupted via `Thread.interrupt`.
     */
    def interruptible[A](effect: => A): ZIO[R, Throwable, A] =
      ZIO.flatten(ZIO.defer {
        import java.util.concurrent.locks.ReentrantLock
        import java.util.concurrent.atomic.AtomicReference
        import scalaz.zio.internal.OneShot

        val lock    = new ReentrantLock()
        val thread  = new AtomicReference[Option[Thread]](None)
        val barrier = OneShot.make[Unit]

        def withMutex[B](b: => B): B =
          try {
            lock.lock(); b
          } finally lock.unlock()

        val interruptThread: ZIO[Any, Nothing, Unit] =
          ZIO.defer(withMutex(thread.get match {
            case None         => ()
            case Some(thread) => thread.interrupt()
          }))

        val awaitInterruption: ZIO[Any, Nothing, Unit] = ZIO.defer(barrier.get())

        for {
          a <- (for {
                fiber <- blocking(ZIO.defer[Either[Throwable, A]] {
                          val current = Some(Thread.currentThread)

                          withMutex(thread.set(current))

                          try Right(effect)
                          catch {
                            case t: Throwable =>
                              Thread.interrupted // Clear interrupt status
                              Left(t)
                          } finally {
                            withMutex { thread.set(None); barrier.set(()) }
                          }
                        }).fork
                a <- fiber.join.absolve
              } yield a).ensuring(interruptThread *> awaitInterruption)
        } yield a
      })
  }

  trait Live extends Blocking {
    val blocking: Service[Any] = new Service[Any] {
      private[this] val blockingExecutor0 =
        PlatformLive.fromThreadPoolExecutor(_ => Int.MaxValue) {
          val corePoolSize  = 0
          val maxPoolSize   = Int.MaxValue
          val keepAliveTime = 1000L
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

      val blockingExecutor: ZIO[Any, Nothing, Executor] = ZIO.succeed(blockingExecutor0)
    }
  }
  object Live extends Live
}
