package zio.internal

import java.util.concurrent.{ LinkedBlockingQueue, RejectedExecutionException, ThreadPoolExecutor, TimeUnit }

private[internal] abstract class DefaultExecutors {
  final def makeDefault(yieldOpCount: Int): Executor =
    fromThreadPoolExecutor(_ => yieldOpCount) {
      val corePoolSize  = 1
      val maxPoolSize   = corePoolSize
      val keepAliveTime = 60000L
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
    }
}
