// Copyright (C) 2018 - 2019 John A. De Goes. All rights reserved.
package scalaz.zio.internal

import scala.concurrent.duration._
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger

trait Scheduler {
  import Scheduler.CancelToken

  def schedule(executor: Executor, task: Runnable, duration: Duration): CancelToken

  /**
   * The number of tasks scheduled.
   */
  def size: Int

  /**
   * Initiates shutdown of the scheduler.
   */
  def shutdown(): Unit
}

object Scheduler {
  type CancelToken = () => Boolean

  /**
   * Creates a new default scheduler.
   */
  final def newDefaultScheduler(): Scheduler =
    fromScheduledExecutorService(Executors.newScheduledThreadPool(1, new NamedThreadFactory("zio-timer", true)))

  /**
   * Creates a new `Scheduler` from a Java `ScheduledExecutorService`.
   */
  final def fromScheduledExecutorService(service: ScheduledExecutorService): Scheduler =
    new Scheduler {
      val ConstFalse = () => false

      val _size = new AtomicInteger()

      override def schedule(executor: Executor, task: Runnable, duration: Duration): CancelToken =
        if (duration == Duration.Zero) {
          executor.submit(task)

          ConstFalse
        } else {
          _size.incrementAndGet

          val future = service.schedule(new Runnable {
            def run: Unit =
              try {
                val _ = executor.submit(task)
              } finally {
                val _ = _size.decrementAndGet
              }
          }, duration.toNanos, TimeUnit.NANOSECONDS)

          () => {
            val canceled = future.cancel(true)

            if (canceled) _size.decrementAndGet

            canceled
          }
        }

      override def size: Int = _size.get

      override def shutdown(): Unit = service.shutdown()
    }
}
