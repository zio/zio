package zio.internal

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.{SynchronousQueue, ThreadPoolExecutor, TimeUnit}

object Blocking {

  val blockingExecutor: zio.Executor =
    zio.Executor.fromThreadPoolExecutor {
      val corePoolSize  = 0
      val maxPoolSize   = Int.MaxValue
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
