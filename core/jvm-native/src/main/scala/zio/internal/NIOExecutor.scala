/*
 * Copyright 2021-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.internal

import zio._
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, LongAdder}
import java.util.concurrent.{
  LinkedBlockingQueue,
  PriorityBlockingQueue,
  RejectedExecutionException,
  ThreadLocalRandom,
  TimeUnit
}
import java.util.{ArrayDeque, Deque}

private[internal] object TraceLogger {
  private var enabled: Boolean     = false
  def setEnabled(b: Boolean): Unit = enabled = b
  def log(msg: => String): Unit    = if (enabled) println(s"[NIO-SCHED] ${Thread.currentThread().getName} | $msg")
}

/**
 * A `NIOExecutor` is an `Executor` that runs ZIO applications using a
 * multi-threaded, work-sharing, least-loaded scheduling algorithm. Inspired by
 * NIO Rust experimental scheduler by Nur Mohammed.
 * [[https://nurmohammed840.github.io/posts/announcing-nio/]]
 */
final class NIOExecutor(val config: NIOExecutor.NIOExecutorConfig) extends Executor {
  import NIOExecutor._

  private val shutdownFlag: AtomicBoolean                      = new AtomicBoolean(false)
  private val timerQueue: PriorityBlockingQueue[ScheduledTask] = new PriorityBlockingQueue[ScheduledTask]()
  private val workerStates: Array[WorkerState]                 = Array.fill(config.nThreads)(new WorkerState())
  private val workers: Array[NIOWorkerThread]                  = new Array[NIOWorkerThread](config.nThreads)
  private val nextBatchIndex: AtomicInteger                    = new AtomicInteger(0)
  private val _enqueuedCount: LongAdder                        = new LongAdder()

  private val timerThread: TimerThread = new TimerThread(timerQueue, workerStates.map(_.taskQueue))

  /**
   * Initialization code that launches the necessary threads as specified in the
   * configuration provided.
   */
  {
    TraceLogger.setEnabled(config.trace)
    TraceLogger.log(s"Initializing NIOExecutor with ${config.nThreads} workers.")
    for (i <- 0 until config.nThreads) {
      val workerThread = new NIOWorkerThread(i, workerStates(i))

      workers(i) = workerThread
      workerThread.start()
    }
    timerThread.start()
  }

  /**
   * Submits a new task to the executor. The method implements two paths: a "hot
   * path" for fibers yielded by a worker, and a "cold path" for new tasks.
   */
  override def submit(runnable: Runnable)(implicit unsafe: Unsafe): Boolean = {
    TraceLogger.log("Submit called.")
    if (shutdownFlag.get()) {
      TraceLogger.log("Rejected submission: executor is shutdown.")
      throw new RejectedExecutionException("NIOExecutor has been shut down")
    }

    val currentThread = Thread.currentThread()

    if (currentThread.isInstanceOf[NIOWorkerThread]) {

      /**
       * HOT PATH: The task is being submitted by one of the executor's own
       * worker threads as ZIO fibers yield. The task is placed in the worker's
       * local `deferQueue` for high cache locality, ensuring it's likely to be
       * picked up immediately by the same worker.
       */
      val worker = currentThread.asInstanceOf[NIOWorkerThread]
      TraceLogger.log(s"HOT PATH: Submitting yielded fiber to local deferQueue of ${worker.getName}.")
      worker.deferQueue.addLast(runnable)
      worker.workerState.enqueuedCount.increment()
      _enqueuedCount.increment()
      return true
    }

    /**
     * COLD PATH: The task is submitted from an external thread. We must find
     * the "least loaded" worker to maintain balanced queues.
     *
     * To avoid contention on a global lock, it inspects a random batch of
     * workers and picks the one with the smallest queue size. This provides
     * good-enough load balancing at a much lower cost.
     */
    TraceLogger.log("COLD PATH: Submitting new task. Finding best worker.")
    val batchStartIndex = (nextBatchIndex.getAndIncrement() * config.batchSize) % config.nThreads
    var minQueueSize    = Int.MaxValue
    var bestWorker      = 0

    var i = 0
    while (i < config.batchSize) {
      val workerIndex = (batchStartIndex + i) % config.nThreads
      val size        = workerStates(workerIndex).queueSize.get()
      if (size < minQueueSize) {
        minQueueSize = size
        bestWorker = workerIndex
      }
      i += 1
    }

    val selectedWorker = workerStates(bestWorker)
    TraceLogger.log(s"COLD PATH: Selected worker ${bestWorker} with queue size ${minQueueSize}.")
    selectedWorker.taskQueue.offer(runnable)
    selectedWorker.queueSize.incrementAndGet()
    _enqueuedCount.increment()
    true
  }

  /**
   * Schedules a task for future execution. The task is added to a concurrent
   * priority queue and will be executed on the TimerThread after the specified
   * duration.
   */
  def schedule(task: Runnable, duration: Duration): () => Boolean = {
    TraceLogger.log(s"Scheduling a task to run in ${duration.toNanos}ns.")
    val scheduledTask = ScheduledTask(java.lang.System.nanoTime() + duration.toNanos, task)
    timerQueue.put(scheduledTask)
    () => {
      TraceLogger.log("Cancelling scheduled task.")
      timerQueue.remove(scheduledTask)
    }
  }

  /**
   * Initiates a non-blocking shutdown of the executor. It sends an interrupt
   * signal to all worker threads and the timer thread. Because the threads are
   * daemons, the JVM will not wait for them to terminate, preventing deadlocks
   * during application shutdown.
   */
  def shutdown(): Unit =
    if (shutdownFlag.compareAndSet(false, true)) {
      TraceLogger.log("Shutdown initiated. Interrupting daemon threads.")
      timerThread.interrupt()
      workers.foreach(_.interrupt())
    }

  override def metrics(implicit unsafe: Unsafe): Option[ExecutionMetrics] = Some(
    new ExecutionMetrics {
      override def concurrency: Int = config.nThreads
      override def capacity: Int    = Int.MaxValue
      override def size: Int = {
        var totalSize = timerQueue.size()
        workerStates.foreach(state => totalSize += state.queueSize.get())
        workers.foreach(worker => if (worker != null) totalSize += worker.deferQueue.size())
        totalSize
      }
      override def enqueuedCount: Long = _enqueuedCount.sum()
      override def dequeuedCount: Long = {
        var dequeues = 0L
        workerStates.foreach(state => dequeues += state.dequeuedCount.sum())
        dequeues
      }
      override def workersCount: Int = config.nThreads
    }
  )
}

object NIOExecutor {

  case class NIOExecutorConfig(nThreads: Int, batchSize: Int, trace: Boolean)

  /**
   * Class that allows configurability of the NIOExecutor class, supports the
   * parameters:
   *   - nThreads : integer that indicates the number of threads to use for
   *     executing the work, defaults to number of processors
   *   - batchSize : integer that indicates the number of workers to sample for
   *     evaluation of current load, default 4
   *   - trace : boolean that indicates if trace logging has to be enabled,
   *     default false
   */
  object NIOExecutorConfig {
    val default: NIOExecutorConfig = NIOExecutorConfig(
      nThreads = java.lang.Runtime.getRuntime.availableProcessors(),
      batchSize = 4,
      trace = false
    )
    val config: Config[NIOExecutorConfig] =
      (Config.int("nThreads") zip Config.int("batchSize") zip Config.boolean("trace")).map { p =>
        NIOExecutorConfig(p._1, p._2, p._3)
      }
        .nested("zio", "nioexecutor")
        .withDefault(default)
  }

  /**
   * Class that describes the current state of the executor worker.
   *   - taskQueue : list of the runnables that may be executed next, not
   *     started yet
   *   - queueSize : cached size of the taskQueue, used for metrics
   *   - enqueuedCount : cached size of tasks enqueued, used for metrics
   *   - dequeuedCount : cached size of tasks dequeued, used for metrics
   */
  private class WorkerState {
    val taskQueue: LinkedBlockingQueue[Runnable] = new LinkedBlockingQueue[Runnable]()
    val queueSize: AtomicInteger                 = new AtomicInteger(0)
    val enqueuedCount: LongAdder                 = new LongAdder()
    val dequeuedCount: LongAdder                 = new LongAdder()
  }

  private case class ScheduledTask(time: Long, task: Runnable) extends Comparable[ScheduledTask] {
    override def compareTo(o: ScheduledTask): Int = time.compareTo(o.time)
  }

  /**
   * A dedicated daemon thread that manages all scheduled tasks. It waits for
   * the next available task in a priority queue. Once a task is ready to be
   * executed, it is submitted to a randomly selected worker thread's main
   * queue.
   */
  private class TimerThread(
    timerQueue: PriorityBlockingQueue[ScheduledTask],
    mainQueues: Array[LinkedBlockingQueue[Runnable]]
  ) extends Thread {
    setName("NIOExecutor-TimerThread")
    setDaemon(true)
    override def run(): Unit =
      try {
        TraceLogger.log("TimerThread run loop started.")
        while (true) {
          val task = timerQueue.take()
          TraceLogger.log("TimerThread took a scheduled task.")
          if (task != null) {
            val idx = ThreadLocalRandom.current().nextInt(mainQueues.length)
            TraceLogger.log(s"TimerThread submitting task to worker $idx.")
            mainQueues(idx).offer(task.task)
          }
        }
      } catch { case _: InterruptedException => TraceLogger.log("TimerThread interrupted, exiting.") }
  }

  /**
   * A single worker thread. It has a local, LIFO queue named `deferQueue` for
   * cache-friendly execution of yielded fibers, and a global FIFO queue named
   * `taskQueue` for new work.
   */
  private class NIOWorkerThread(val id: Int, val workerState: WorkerState) extends Thread {
    setName(s"NIOExecutor-Worker-$id")
    setDaemon(true)

    /**
     * This is a local, non-concurrent queue for tasks that were yielded by this
     * worker. Placing them here ensures they are picked up again by the same
     * thread, maximizing cache locality.
     */
    val deferQueue: Deque[Runnable] = new ArrayDeque[Runnable]()

    override def run(): Unit =
      try {
        TraceLogger.log("Worker run loop started.")
        while (true) {
          val task = findTask()
          if (task ne null) runTask(task)
        }
      } catch { case _: InterruptedException => TraceLogger.log("Worker interrupted, exiting.") }

    private def runTask(task: Runnable): Unit = {
      TraceLogger.log("Worker found task, preparing to run.")
      try task.run()
      catch {
        case _: InterruptedException =>
          TraceLogger.log("Task was interrupted during run.")
          Thread.currentThread().interrupt()
        case t: Throwable =>
          TraceLogger.log(s"Task threw an unhandled exception: ${t.getClass.getName}")
          t.printStackTrace()
      }
      workerState.dequeuedCount.increment()
    }

    /*
     * Finds next task to execute. The local `deferQueue` is checked first so that
     * recently yielded work can finish first. If empty, it polls its main work-sharing queue.
     * If that is also empty, it will block and wait for a new task to arrive in
     * the main queue.
     */
    private def findTask(): Runnable = {
      var task = deferQueue.poll()
      if (task ne null) {
        TraceLogger.log("Found task in local deferQueue.")
        return task
      }

      task = workerState.taskQueue.poll()
      if (task ne null) {
        workerState.queueSize.decrementAndGet()
        TraceLogger.log("Found task in main taskQueue (non-blocking).")
        return task
      }

      TraceLogger.log("No tasks found, blocking on main taskQueue.")
      val takenTask = workerState.taskQueue.take()
      TraceLogger.log("Woke up with a task from main taskQueue (blocking).")
      takenTask
    }
  }

  val live: ZLayer[Any, Config.Error, NIOExecutor] = {
    implicit val trace = Tracer.newTrace
    ZLayer.scoped {
      for {
        config  <- ZIO.config[NIOExecutorConfig](NIOExecutorConfig.config)
        _       <- ZIO.logTrace(s"Constructing NIOExecutor with config: $config")
        executor = new NIOExecutor(config)
        _       <- ZIO.addFinalizer(ZIO.succeedBlocking(executor.shutdown()))
      } yield executor
    }
  }
}
