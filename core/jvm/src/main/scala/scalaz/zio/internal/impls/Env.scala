package scalaz.zio.internal.impls

import java.util
import java.util.concurrent._

import scalaz.zio.Exit.Cause
import scalaz.zio.IO
import scalaz.zio.internal.Executor.{ Role, Unyielding, Yielding }
import scalaz.zio.internal.{ Env, ExecutionMetrics, Executor, NamedThreadFactory, Scheduler }

object Env {

  /**
   * Creates a new default environment.
   */
  final def newDefaultEnv(reportFailure0: Cause[_] => IO[Nothing, _]): Env =
    new Env {
      val sync  = newDefaultExecutor(Executor.Unyielding)
      val async = newDefaultExecutor(Executor.Yielding)

      def executor(tpe: Executor.Role): Executor = tpe match {
        case Executor.Unyielding => sync
        case Executor.Yielding   => async
      }

      val scheduler: Scheduler =
        Scheduler.fromScheduledExecutorService(
          Executors.newScheduledThreadPool(1, new NamedThreadFactory("zio-timer", true))
        )

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
  final def newDefaultExecutor(role: Role): Executor = role match {
    case Unyielding =>
      fromThreadPoolExecutor(role, _ => Int.MaxValue) {
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

    case Yielding =>
      fromThreadPoolExecutor(role, _ => 1024) {
        val corePoolSize  = Runtime.getRuntime.availableProcessors() * 2
        val maxPoolSize   = corePoolSize
        val keepAliveTime = 1000L
        val timeUnit      = TimeUnit.MILLISECONDS
        val workQueue     = new LinkedBlockingQueue[Runnable]()
        val threadFactory = new NamedThreadFactory("zio-default-yielding", true)

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
  }

  /**
   * Constructs an `Executor` from a Java `ThreadPoolExecutor`.
   */
  final def fromThreadPoolExecutor(role0: Role, yieldOpCount0: ExecutionMetrics => Int)(
    es: ThreadPoolExecutor
  ): Executor =
    new Executor {
      def role = role0

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
