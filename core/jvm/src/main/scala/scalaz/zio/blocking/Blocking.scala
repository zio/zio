package scalaz.zio.blocking

import java.util.concurrent._

import scalaz.zio.ZIO
import scalaz.zio.internal.{ Executor, NamedThreadFactory }

/**
 * The `Blocking` module provides access to a thread pool that can be used for performing
 * blocking operations, such as thread sleeps, synchronous socket/file reads, and so forth.
 * The contract is that the thread pool will accept unlimited tasks (up to the available)
 * memory, and continuously create new threads as necessary.
 */
trait Blocking extends Serializable {
  def blocking: Blocking.Interface[Any]
}
object Blocking extends Serializable {
  trait Interface[R] extends Serializable {

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
      ZIO.flatten(ZIO.sync {
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
          ZIO.sync(withMutex(thread.get match {
            case None         => ()
            case Some(thread) => thread.interrupt()
          }))

        val awaitInterruption: ZIO[Any, Nothing, Unit] = ZIO.sync(barrier.get())

        for {
          a <- (for {
                fiber <- blocking(ZIO.sync[Either[Throwable, A]] {
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
    object blocking extends Interface[Any] {
      private[this] val blockingExecutor0 =
        scalaz.zio.internal.impls.Env.fromThreadPoolExecutor(null, _ => Int.MaxValue) {
          val corePoolSize  = 0
          val maxPoolSize   = Int.MaxValue
          val keepAliveTime = 1000L
          val timeUnit      = TimeUnit.MILLISECONDS
          val workQueue     = new SynchronousQueue[Runnable]()
          val threadFactory = new NamedThreadFactory("zio-default-unyielding", true)

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
