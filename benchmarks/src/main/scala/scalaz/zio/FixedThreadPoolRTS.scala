package scalaz.zio
import java.util.concurrent.{ ExecutorService, LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit }
import scalaz.zio.RTS.NamedThreadFactory

class FixedThreadPoolRTS(threadPoolSize: Int) extends RTS {

  override val threadPool: ExecutorService = {
    val keepAliveTime = 1000L
    val timeUnit      = TimeUnit.MILLISECONDS
    val workQueue     = new LinkedBlockingQueue[Runnable]()
    val threadFactory = new NamedThreadFactory("zio", true)

    val threadPool = new ThreadPoolExecutor(
      threadPoolSize,
      threadPoolSize,
      keepAliveTime,
      timeUnit,
      workQueue,
      threadFactory
    )
    threadPool.allowCoreThreadTimeOut(true)

    threadPool
  }
}
