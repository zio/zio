package scalaz.zio.internal.impls

import java.util
import java.util.concurrent._

import scala.concurrent.ExecutionContext
import scalaz.zio.Exit.Cause
import scalaz.zio.{ FiberFailure, IO }
import scalaz.zio.internal.{ Env => IEnv, ExecutionMetrics, Executor, NamedThreadFactory }

object Env {

  /**
   * Creates a new environment from an `ExecutionContext`.
   */
  final def fromExecutionContext(ec: ExecutionContext): IEnv =
    new IEnv {
      val executor = Executor.fromExecutionContext(1000)(ec)

      def nonFatal(t: Throwable): Boolean =
        !t.isInstanceOf[VirtualMachineError]

      def reportFailure(cause: Cause[_]): IO[Nothing, _] =
        IO.sync(ec.reportFailure(FiberFailure(cause)))

      def newWeakHashMap[A, B](): util.Map[A, B] =
        new util.WeakHashMap[A, B]()
    }

  /**
   * Creates a new default environment.
   */
  final def newDefaultEnv(reportFailure0: Cause[_] => IO[Nothing, _]): IEnv =
    new IEnv {
      val executor = newexecutor()

      def nonFatal(t: Throwable): Boolean =
        !t.isInstanceOf[VirtualMachineError]

      def reportFailure(cause: Cause[_]): IO[Nothing, _] =
        reportFailure0(cause)

      def newWeakHashMap[A, B](): util.Map[A, B] =
        new util.WeakHashMap[A, B]()
    }

  /**
   * Creates a new default executor of the specified type.
   */
  final def newexecutor(): Executor =
    fromThreadPoolExecutor(_ => 1024) {
      val corePoolSize  = Runtime.getRuntime.availableProcessors() * 2
      val maxPoolSize   = corePoolSize
      val keepAliveTime = 1000L
      val timeUnit      = TimeUnit.MILLISECONDS
      val workQueue     = new LinkedBlockingQueue[Runnable]()
      val threadFactory = new NamedThreadFactory("zio-default-async", true)

      val threadPool = new ThreadPoolExecutor(
        corePoolSize,
        maxPoolSize,
        keepAliveTime,
        timeUnit,
        workQueue,
        threadFactory
      )
      threadPool.allowCoreThreadTimeOut(true)

      threadPool
    }

  /**
   * Constructs an `Executor` from a Java `ThreadPoolExecutor`.
   */
  final def fromThreadPoolExecutor(yieldOpCount0: ExecutionMetrics => Int)(
    es: ThreadPoolExecutor
  ): Executor =
    new Executor {
      private[this] def metrics0 = new ExecutionMetrics {
        def concurrency: Int = es.getMaximumPoolSize()

        def capacity: Int = {
          val queue = es.getQueue()

          val remaining = queue.remainingCapacity()

          if (remaining == Int.MaxValue) remaining
          else remaining + queue.size
        }

        def size: Int = es.getQueue().size

        def workersCount: Int = es.getPoolSize()

        def enqueuedCount: Long = es.getTaskCount()

        def dequeuedCount: Long = enqueuedCount - size.toLong
      }

      def metrics = Some(metrics0)

      def yieldOpCount = yieldOpCount0(metrics0)

      def submit(runnable: Runnable): Boolean =
        try {
          es.execute(runnable)

          true
        } catch {
          case _: RejectedExecutionException => false
        }

      def here = false

      def shutdown(): Unit = { val _ = es.shutdown() }
    }
}
