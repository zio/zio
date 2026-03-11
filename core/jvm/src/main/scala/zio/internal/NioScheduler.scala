/*
 * Copyright 2024 John A. De Goes and the ZIO Contributors
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

import zio.{Executor, Unsafe}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.nio.channels.Selector
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * A `NioScheduler` is an `Executor` that uses a pool of event loops backed by
 * Java NIO `Selector`s for cooperative task scheduling.
 *
 * Unlike [[ZScheduler]] which uses `LockSupport.park`/`unpark` and may block
 * OS threads waiting for work, the `NioScheduler` uses
 * `java.nio.channels.Selector` as a lightweight blocking primitive. Each event
 * loop thread calls `selector.select(timeoutMs)` when idle, and
 * `selector.wakeup()` is used instead of `LockSupport.unpark` to signal new
 * work. This keeps the scheduler fully non-blocking with respect to I/O-related
 * waiting and enables a clean integration point for future NIO channel
 * registration.
 *
 * The scheduling strategy is inspired by the "Least-Loaded" (LL) algorithm
 * described in https://nurmohammed840.github.io/posts/announcing-nio/: tasks
 * are dispatched to the event loop with the fewest queued items, reducing
 * starvation. When an event loop runs out of local work it attempts to steal
 * tasks from busier siblings before going idle.
 *
 * ==Design==
 *   - `NioScheduler` – public entry-point; owns a fixed pool of `NioEventLoop`
 *     threads and implements `zio.Executor`.
 *   - `NioEventLoop` – a daemon thread that drains its local
 *     `ConcurrentLinkedDeque`, attempts work-stealing when idle, then sleeps
 *     on `selector.select(1)` until woken by `selector.wakeup()`.
 *
 * ==Usage==
 * {{{
 *   val nioExecutor: zio.Executor = Executor.makeNio()
 *   val runtime = Runtime.default
 *   val result  = Unsafe.unsafe { implicit u =>
 *     runtime.unsafe.runToFuture(myEffect.onExecutor(nioExecutor))
 *   }
 * }}}
 *
 * @param nThreads
 *   Number of event-loop threads. Defaults to the number of available
 *   processors.
 *
 * @note
 *   This implementation does not yet perform NIO channel registration; the
 *   `Selector` is used purely as an interruptible sleep mechanism. Future work
 *   can attach `SelectableChannel`s to the per-loop selectors for zero-copy I/O
 *   multiplexing.
 */
private[zio] final class NioScheduler(nThreads: Int = java.lang.Runtime.getRuntime.availableProcessors())
    extends Executor {

  // Declare as a var so we can close over `loops` via a lambda that is only
  // called *after* the array is fully initialised (at work-steal time, not
  // during construction).
  private[this] var loops: Array[NioScheduler.EventLoop] = null
  loops = Array.tabulate(nThreads)(i => new NioScheduler.EventLoop(i, nThreads, () => loops))
  private[this] val counter = new AtomicInteger(0)

  // Start threads only after the `loops` array is fully populated so that the
  // work-stealing closure sees a complete array.
  loops.foreach(_.start())

  // -------------------------------------------------------------------------
  // Executor API
  // -------------------------------------------------------------------------

  override def metrics(implicit unsafe: Unsafe): Option[ExecutionMetrics] = {
    val snap = new ExecutionMetrics {
      def concurrency: Int = nThreads
      def capacity: Int    = Int.MaxValue

      def size: Int = {
        var total = 0
        loops.foreach(l => total += l.queueSize)
        total
      }

      def workersCount: Int = nThreads

      def enqueuedCount: Long = {
        var total = 0L
        loops.foreach(l => total += l.totalEnqueued)
        total
      }

      def dequeuedCount: Long = {
        var total = 0L
        loops.foreach(l => total += l.totalDequeued)
        total
      }
    }
    Some(snap)
  }

  override def submit(runnable: Runnable)(implicit unsafe: Unsafe): Boolean = {
    dispatch(runnable)
    true
  }

  override def submitAndYield(runnable: Runnable)(implicit unsafe: Unsafe): Boolean = {
    dispatch(runnable)
    true
  }

  // -------------------------------------------------------------------------
  // Internal helpers
  // -------------------------------------------------------------------------

  /** Dispatch to the least-loaded event loop (ties broken by round-robin). */
  private def dispatch(runnable: Runnable): Unit = {
    var bestLoop  = loops(0)
    var bestSize  = Int.MaxValue
    var i         = 0
    val n         = nThreads
    val offset    = (counter.getAndIncrement() & Int.MaxValue) % n
    while (i < n) {
      val loop = loops((i + offset) % n)
      val sz   = loop.queueSize
      if (sz < bestSize) {
        bestSize = sz
        bestLoop = loop
      }
      i += 1
    }
    bestLoop.submit(runnable)
  }

  /** Gracefully shut down all event loops. */
  def shutdown(): Unit = loops.foreach(_.shutdown())
}

private[zio] object NioScheduler {

  /**
   * Creates a new [[NioScheduler]] with the given number of event-loop
   * threads, exposing it as a [[zio.Executor]].
   *
   * {{{
   *   import zio._
   *   import zio.internal.NioScheduler
   *
   *   val nioExecutor: zio.Executor = NioScheduler.make()
   *   val effect: Task[Int] = myZioEffect.onExecutor(nioExecutor)
   * }}}
   *
   * @param nThreads
   *   Number of event-loop threads (default: available processors).
   */
  def make(nThreads: Int = java.lang.Runtime.getRuntime.availableProcessors()): Executor =
    new NioScheduler(nThreads)

  /**
   * A single-threaded event loop backed by a Java NIO `Selector`.
   *
   * The loop body:
   *   1. Drain all tasks in the local deque.
   *   2. Try to steal tasks from sibling loops.
   *   3. If still idle, call `selector.select(1)` (≤1 ms sleep) so new work
   *      wakes it via `selector.wakeup()`.
   */
  private[NioScheduler] final class EventLoop(
    id: Int,
    total: Int,
    siblingsRef: () => Array[EventLoop]
  ) extends Thread(s"zio-nio-$id") {

    setDaemon(true)

    // Not `private[this]` so sibling EventLoops can steal tasks via `queue.pollLast()`
    private[NioScheduler] val queue = new ConcurrentLinkedDeque[Runnable]()
    private[this] val selector      = Selector.open()

    @volatile private[this] var running = true

    // Counters for ExecutionMetrics
    @volatile private[NioScheduler] var totalEnqueued: Long = 0L
    @volatile private[NioScheduler] var totalDequeued: Long = 0L

    def queueSize: Int = queue.size()

    // ------------------------------------------------------------------
    // Public interface used by NioScheduler
    // ------------------------------------------------------------------

    def submit(task: Runnable): Unit = {
      queue.offerLast(task)
      totalEnqueued += 1
      selector.wakeup()
    }

    def shutdown(): Unit = {
      running = false
      selector.wakeup()
    }

    // ------------------------------------------------------------------
    // Event-loop body
    // ------------------------------------------------------------------

    override def run(): Unit = {
      while (running) {
        // 1. Drain local queue
        drainLocal()

        // 2. Work-steal from siblings if still idle
        if (queue.isEmpty) {
          stealWork()
        }

        // 3. Sleep until new work or timeout
        if (queue.isEmpty && running) {
          try selector.select(1L)
          catch { case _: java.io.IOException => () }
        }
      }
      try selector.close()
      catch { case _: java.io.IOException => () }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private def drainLocal(): Unit = {
      var task = queue.pollFirst()
      while (task != null) {
        try task.run()
        catch { case t: Throwable => reportFailure(t) }
        totalDequeued += 1
        task = queue.pollFirst()
      }
    }

    /**
     * Attempt to steal half the tasks from the busiest sibling loop and add
     * them to our own queue. Returns `true` if at least one task was stolen.
     */
    private def stealWork(): Boolean = {
      val siblings = siblingsRef()
      var busiest  = null.asInstanceOf[EventLoop]
      var maxSize  = 0
      var i        = 0
      while (i < total) {
        val sibling = siblings(i)
        if (sibling ne this) {
          val sz = sibling.queueSize
          if (sz > maxSize) {
            maxSize = sz
            busiest = sibling
          }
        }
        i += 1
      }
      if (busiest == null || maxSize == 0) return false

      val toSteal = (maxSize + 1) / 2 // steal approximately half
      var stolen  = 0
      while (stolen < toSteal) {
        val task = busiest.queue.pollLast() // steal from tail (LIFO for stolen tasks)
        if (task == null) return stolen > 0
        queue.offerFirst(task) // prepend so we run them soon
        stolen += 1
      }
      stolen > 0
    }

    private def reportFailure(t: Throwable): Unit =
      // Mirror the ZScheduler approach: swallow Throwable but let fatal errors propagate
      if (!t.isInstanceOf[VirtualMachineError]) ()
      else throw t
  }
}
