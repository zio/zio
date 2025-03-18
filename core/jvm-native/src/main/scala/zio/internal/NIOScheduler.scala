package zio.internal

import zio.{Executor, Unsafe}

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.LockSupport

private final class NIOScheduler extends Executor {
  import NIOScheduler.poolSize
  import NIOScheduler.Worker

  private[this] val workers = Array.ofDim[NIOScheduler.Worker](poolSize)
  private[this] val cache   = new ConcurrentLinkedQueue[NIOScheduler.Worker]()

  (0 until poolSize).foreach { workerId =>
    val worker = makeWorker()
    worker.setName(workerId)
    worker.setDaemon(true)
    workers(workerId) = worker
  }
  workers.foreach(_.start())

  // The NIOScheduler cannot detect blocking on its own.
  // Therefore, a supervisor is required to handle it.
  private[this] val supervisor: NIOScheduler.Supervisor = makeSupervisor()
  supervisor.setName("NIOScheduler-Supervisor")
  supervisor.setDaemon(true)
  supervisor.start()

  def metrics(implicit unsafe: Unsafe): Option[ExecutionMetrics] = {
    val metrics = new ExecutionMetrics {
      def capacity: Int =
        Int.MaxValue
      def concurrency: Int =
        poolSize
      def dequeuedCount: Long = {
        var dequeued = 0L
        var i        = 0
        while (i != poolSize) {
          val worker = workers(i)
          dequeued += worker.opCount
          i += 1
        }
        dequeued
      }
      def enqueuedCount: Long = {
        var enqueued = 0L
        var i        = 0
        while (i != poolSize) {
          val worker = workers(i)
          enqueued += worker.opCount
          enqueued += worker.localQueue.size()
          i += 1
        }
        enqueued
      }
      def size: Int = {
        var i    = 0
        var size = 0
        while (i != poolSize) {
          val worker = workers(i)
          size += worker.localQueue.size()
          i += 1
        }
        size
      }
      def workersCount: Int = poolSize
    }
    Some(metrics)
  }

  /**
   * Submits an effect for execution.
   */
  def submit(runnable: Runnable)(implicit unsafe: Unsafe): Boolean = {
    val worker = leastLoadedWorker()
    if (worker.localQueue.offer(runnable)) {
      if (!worker.active) {
        worker.active = true
        LockSupport.unpark(worker)
      }
    }

    true
  }

  private def leastLoadedWorker(): Worker = {
    var bestWorker: Worker        = null
    var leastLoadedWorker: Worker = null
    var minLoad                   = Int.MaxValue
    var minOverallLoad            = Int.MaxValue

    workers.foreach { worker =>
      val queueSize = worker.localQueue.size()

      if (worker.active && queueSize <= 128 && queueSize < minLoad) {
        minLoad = queueSize
        bestWorker = worker
      }

      if (queueSize < minOverallLoad) {
        minOverallLoad = queueSize
        leastLoadedWorker = worker
      }
    }

    if (bestWorker != null) bestWorker else leastLoadedWorker
  }

  private[this] def makeWorker(): NIOScheduler.Worker =
    new NIOScheduler.Worker {
      self =>
      override def run(): Unit = {
        var currentOpCount     = 0L
        var runnable: Runnable = null
        var currentBlocking    = false

        while (!isInterrupted) {
          currentBlocking = blocking

          runnable = localQueue.poll(null)

          if (runnable eq null) {
            active = false
            if (currentBlocking) {
              cache.offer(self)
            }

            while (localQueue.size() == 0 && !isInterrupted) {
              LockSupport.park()
            }
          } else {
            currentRunnable = runnable
            runnable.run()
            runnable = null
            currentRunnable = null
            currentOpCount += 1
            opCount = currentOpCount
          }
        }
      }

      def markAsBlocking(): Unit = synchronized {
        if (blocking) ()
        else {
          blocking = true
          val idx = workers.indexOf(self)
          if (idx >= 0) {
            val runnables = self.localQueue.pollUpTo(512)
            val worker    = cache.poll()
            if (worker eq null) {
              val worker = makeWorker()
              worker.setName(idx)
              worker.setDaemon(true)
              worker.localQueue.offerAll(runnables)
              workers(idx) = worker
              worker.start()
            } else {
              worker.setName(idx)
              worker.localQueue.offerAll(runnables)
              workers(idx) = worker
              worker.blocking = false
              worker.active = true
              LockSupport.unpark(worker)
            }
          }
        }
      }
    }

  private[this] def makeSupervisor(): NIOScheduler.Supervisor =
    new NIOScheduler.Supervisor {
      override def run(): Unit = {
        val previousOpCounts = Array.fill(poolSize)(-1L)
        while (!isInterrupted) {
          var workerId = 0
          while (workerId < poolSize) {
            val currentWorker = workers(workerId)
            if (currentWorker.active) {
              val currentOpCount  = currentWorker.opCount
              val previousOpCount = previousOpCounts(workerId)
              if (currentOpCount == previousOpCount) {
                previousOpCounts(workerId) = -1L
                currentWorker.markAsBlocking()
              } else {
                previousOpCounts(workerId) = currentOpCount
              }
            } else {
              if (currentWorker.localQueue.size() > 0) {
                currentWorker.active = true
                LockSupport.unpark(currentWorker)
              }
              previousOpCounts(workerId) = -1L
            }
            workerId += 1
          }
          val deadline = java.lang.System.currentTimeMillis() + 100
          var loop     = true
          while (loop) {
            LockSupport.parkUntil(deadline)
            loop = java.lang.System.currentTimeMillis() < deadline
          }
        }
      }
    }
}

private object NIOScheduler {
  private val poolSize = java.lang.Runtime.getRuntime.availableProcessors

  def markCurrentWorkerAsBlocking(): Unit = {
    val worker = workerOrNull()
    if (worker ne null) {
      worker.markAsBlocking()
    } else {
      ()
    }
  }

  /**
   * If the current thread is a [[NIOScheduler.Worker]] then it is returned,
   * otherwise returns null
   */
  private def workerOrNull(): NIOScheduler.Worker =
    Thread.currentThread() match {
      case w: NIOScheduler.Worker => w
      case _                      => null
    }

  /**
   * A `Supervisor` is a `Thread` that is responsible for monitoring workers and
   * shifting tasks from workers that are blocking to new workers.
   */
  private sealed abstract class Supervisor extends Thread

  /**
   * A `Worker` is a `Thread` that is responsible for executing actions
   * submitted to the scheduler.
   */
  private sealed abstract class Worker extends Thread {

    /**
     * Whether this worker is currently active.
     */
    @volatile
    var active: Boolean =
      true

    /**
     * The current task being executed by this worker.
     */
    @volatile
    var currentRunnable: Runnable =
      null

    /**
     * The local work queue for this worker.
     */
    val localQueue: RingBufferPow2[Runnable] =
      RingBufferPow2[Runnable](512)

    /**
     * The number of tasks that have been executed by this worker.
     */
    @volatile
    var opCount: Long =
      0L

    final def setName(i: Int): Unit =
      setName(s"NIOScheduler-Worker-$i")

    def markAsBlocking(): Unit

    /**
     * Whether this worker is currently blocking.
     */
    @volatile
    var blocking: Boolean =
      false
  }
}
