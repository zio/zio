package zio.internal

import java.util.concurrent.{SynchronousQueue, ThreadPoolExecutor, TimeUnit}

object Blocking {

  val blockingExecutor: Executor =
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
