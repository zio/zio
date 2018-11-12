package scalaz.zio.internal
import java.util.concurrent.{ RejectedExecutionException, RejectedExecutionHandler, ThreadPoolExecutor }

object RejectedExecutionHandlers {

  val saneSafeRejectedExecutionHandler: RejectedExecutionHandler = SaneSafeRejectedExecutionHandler

  /**
   * A special [[RejectedExecutionHandler]] implementation which will always try to run
   * the submitted [[Runnable]]. When the underling [[java.util.concurrent.Executor]] was
   * shutdown,it will try to run the submitted [[Runnable]] in the current submitting thread
   * and throw a [[RejectedExecutionException]].
   * */
  private final object SaneSafeRejectedExecutionHandler extends RejectedExecutionHandler {
    override def rejectedExecution(runnable: Runnable, executor: ThreadPoolExecutor): Unit = {
      def safeRun(runnable: Runnable): Unit =
        if (runnable eq null) {
          throw new NullPointerException("parameter runnable should not be null.")
        } else {
          runnable.run()
        }
      if (executor.isShutdown) {
        safeRun(runnable)
        throw new RejectedExecutionException(
          s"executor:[$executor] is shutdown, poolSize:[${executor.getPoolSize}]], completedTaskSize:[${executor.getCompletedTaskCount}], queueSize:[${executor.getQueue.size()}]"
        )
      } else {
        safeRun(runnable)
      }
    }
  }
}
