// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

import scala.concurrent.duration.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent._
import scalaz.zio.CommonRTS.FiberContext

/**
 * This trait provides a high-performance implementation of a runtime system for
 * the `IO` monad on the JVM.
 */
trait RTS extends CommonRTS {
  import RTS._

  /**
   * Effectfully interprets an `IO`, blocking if necessary to obtain the result.
   */
  final def unsafeRunSync[E, A](io: IO[E, A]): ExitResult[E, A] = {
    val context = new FiberContext[E, A](this, defaultHandler)
    context.evaluate(io)
    context.runSync
  }

  final def unsafeShutdownAndWait(timeout: Duration): Unit = {
    scheduledExecutor.shutdown()
    scheduledExecutor.awaitTermination(timeout.toMillis, TimeUnit.MILLISECONDS)
    threadPool.shutdown()
    threadPool.awaitTermination(timeout.toMillis, TimeUnit.MILLISECONDS)
    ()
  }

  /**
   * The main thread pool used for executing fibers.
   */
  val threadPool = newDefaultThreadPool()

  lazy val scheduledExecutor = newDefaultScheduledExecutor()

  final def submit[A](block: => A): Unit = {
    threadPool.submit(new Runnable {
      def run: Unit = { block; () }
    })

    ()
  }

  final def schedule[E, A](block: => A, duration: Duration): Async[E, Unit] =
    if (duration == Duration.Zero) {
      submit(block)

      Async.later[E, Unit]
    } else {
      val future = scheduledExecutor.schedule(new Runnable {
        def run: Unit = submit(block)
      }, duration.toNanos, TimeUnit.NANOSECONDS)

      Async.maybeLater { () =>
        future.cancel(true); ()
      }
    }

  /** Utility function to avoid catching truly fatal exceptions. Do not allocate
   * memory here since this would defeat the point of checking for OOME.
   */
  protected def nonFatal(t: Throwable): Boolean =
    !t.isInstanceOf[VirtualMachineError]

  def getMap[A]: java.util.Map[A, java.lang.Boolean] =
    new java.util.WeakHashMap[A, java.lang.Boolean]()

}

private object RTS {

  sealed abstract class RaceState extends Serializable with Product
  object RaceState extends Serializable {
    case object Started     extends RaceState
    case object FirstFailed extends RaceState
    case object Finished    extends RaceState
  }

  @inline
  final def nextInstr[E](value: Any, stack: Stack): IO[E, Any] =
    if (!stack.isEmpty) stack.pop()(value).asInstanceOf[IO[E, Any]] else null

  final class Stack() {
    type Cont = Any => IO[_, Any]

    private[this] var array   = new Array[AnyRef](13)
    private[this] var size    = 0
    private[this] var nesting = 0

    def isEmpty: Boolean = size == 0

    def push(a: Cont): Unit =
      if (size == 13) {
        array = Array(array, a, null, null, null, null, null, null, null, null, null, null, null)
        size = 2
        nesting += 1
      } else {
        array(size) = a
        size += 1
      }

    def pop(): Cont = {
      val idx = size - 1
      var a   = array(idx)
      if (idx == 0 && nesting > 0) {
        array = a.asInstanceOf[Array[AnyRef]]
        a = array(12)
        array(12) = null // GC
        size = 12
        nesting -= 1
      } else {
        array(idx) = null // GC
        size = idx
      }
      a.asInstanceOf[Cont]
    }
  }

  final def newDefaultThreadPool(): ExecutorService = {
    val corePoolSize    = 0
    val maximumPoolSize = Int.MaxValue
    val keepAliveTime   = 60000L
    val timeUnit        = TimeUnit.MILLISECONDS
    val workQueue       = new SynchronousQueue[Runnable]()
    val threadFactory   = new NamedThreadFactory("zio", true)

    new ThreadPoolExecutor(
      corePoolSize,
      maximumPoolSize,
      keepAliveTime,
      timeUnit,
      workQueue,
      threadFactory
    )
  }

  final def newDefaultScheduledExecutor(): ScheduledExecutorService =
    Executors.newScheduledThreadPool(1, new NamedThreadFactory("zio-timer", true))

  final class NamedThreadFactory(name: String, daemon: Boolean) extends ThreadFactory {

    private val parentGroup =
      Option(System.getSecurityManager).fold(Thread.currentThread().getThreadGroup)(_.getThreadGroup)

    private val threadGroup = new ThreadGroup(parentGroup, name)
    private val threadCount = new AtomicInteger(1)
    private val threadHash  = Integer.toUnsignedString(this.hashCode())

    override def newThread(r: Runnable): Thread = {
      val newThreadNumber = threadCount.getAndIncrement()

      val thread = new Thread(threadGroup, r)
      thread.setName(s"$name-$newThreadNumber-$threadHash")
      thread.setDaemon(daemon)

      thread
    }

  }
}
