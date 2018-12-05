package scalaz.zio
import java.util.concurrent.{ ExecutorService, LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit }
import scalaz.zio.RTS.NamedThreadFactory

class CustomRTS(threadPoolSize: Int) extends RTS {

  override val threadPool: ExecutorService = {
    val corePoolSize  = threadPoolSize
    val keepAliveTime = 1000L
    val timeUnit      = TimeUnit.MILLISECONDS
    val workQueue     = new LinkedBlockingQueue[Runnable]()
    val threadFactory = new NamedThreadFactory("zio", true)

    val threadPool = new ThreadPoolExecutor(
      corePoolSize,
      corePoolSize,
      keepAliveTime,
      timeUnit,
      workQueue,
      threadFactory
    )
    threadPool.allowCoreThreadTimeOut(true)

    threadPool
  }
}
