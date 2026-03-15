package zio.internal

import java.util.concurrent.{ConcurrentLinkedQueue, ThreadPoolExecutor}
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

/**
 * ZScheduler is a work-stealing scheduler for ZIO runtimes.
 * It attempts to minimize context switching and park/unpark cycles.
 */
private[zio] final class ZScheduler(
  corePoolSize: Int,
  maxPoolSize: Int,
  keepAliveTime: Long,
  timeUnit: java.util.concurrent.TimeUnit
) {

  private val workers = new java.util.concurrent.CopyOnWriteArrayList[Worker]()
  private val globalQueue = new ConcurrentLinkedQueue[Runnable]()
  private val state = new AtomicInteger(0)

  private val PARK_TIMEOUT_NANOS = timeUnit.toNanos(keepAliveTime)

  private def parkWorker(): Unit = {
    LockSupport.parkNanos(PARK_TIMEOUT_NANOS)
  }

  private def unparkWorker(worker: Worker): Unit = {
    LockSupport.unpark(worker.thread)
  }

  private def shouldUnpark(): Boolean = {
    !globalQueue.isEmpty || workers.stream().anyMatch(w => w.hasTasks)
  }

  private def tryUnpark(): Unit = {
    if (shouldUnpark()) {
      workers.forEach { worker =>
        if (!worker.isActive) {
          unparkWorker(worker)
        }
      }
    }
  }

  def execute(runnable: Runnable): Unit = {
    globalQueue.add(runnable)
    tryUnpark()
  }

  private class Worker extends Thread {
    var isActive = false
    private val localQueue = new java.util.ArrayDeque[Runnable]()

    def hasTasks: Boolean = !localQueue.isEmpty

    override def run(): Unit = {
      isActive = true
      try {
        while (true) {
          val task = localQueue.poll()
          if (task == null) {
            val stolen = globalQueue.poll()
            if (stolen != null) {
              stolen.run()
            } else {
              isActive = false
              parkWorker()
              isActive = true
            }
          } else {
            task.run()
          }
        }
      } finally {
        isActive = false
      }
    }
  }
}
