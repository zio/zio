package scalaz.zio.internal.impls

import java.util
import java.util.concurrent.atomic.AtomicInteger

import scalaz.zio.Exit.Cause
import scalaz.zio.IO
import scalaz.zio.duration.Duration
import scalaz.zio.internal.Scheduler.CancelToken
import scalaz.zio.internal.{ Env, Executor, Scheduler }

import scala.concurrent.ExecutionContext.Implicits
import scala.scalajs.js

object Env {

  /**
   * Creates a new default environment.
   */
  final def newDefaultEnv(reportFailure0: Cause[_] => IO[Nothing, _]): Env =
    new Env {
      val executor = Executor.fromExecutionContext(Executor.Yielding, 1024)(Implicits.global)

      def executor(tpe: Executor.Role): Executor =
        executor

      val scheduler = newDefaultScheduler()

      def nonFatal(t: Throwable): Boolean =
        !t.isInstanceOf[VirtualMachineError]

      def reportFailure(cause: Cause[_]): IO[Nothing, _] =
        reportFailure0(cause)

      def newWeakHashMap[A, B](): util.Map[A, B] =
        new util.HashMap[A, B]()
    }

  def newDefaultScheduler() = new Scheduler {

    val ConstFalse = () => false

    val _size = new AtomicInteger()

    override def schedule(task: Runnable, duration: Duration): CancelToken = duration match {
      case Duration.Infinity => ConstFalse
      case Duration.Zero =>
        task.run()

        ConstFalse
      case duration: Duration.Finite =>
        _size.incrementAndGet

        val handle = js.timers.setTimeout(duration.toMillis.toDouble) {
          try {
            task.run()
          } finally {
            val _ = _size.decrementAndGet
          }
        }
        () => {
          js.timers.clearTimeout(handle)
          _size.decrementAndGet
          true
        }
    }

    /**
     * The number of tasks scheduled.
     */
    override def size: Int = _size.get()

    /**
     * Initiates shutdown of the scheduler.
     */
    override def shutdown(): Unit = ()
  }
}
