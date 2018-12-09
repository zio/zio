// Copyright (C) 2018 - 2019 John A. De Goes. All rights reserved.
package scalaz.zio.internal

import java.util.concurrent._
import scala.concurrent.ExecutionContext

/**
 * An executor is responsible for executing actions. Each action is guaranteed
 * to begin execution on a fresh stack frame.
 */
trait Executor {

  /**
   * Execution metrics.
   */
  def metrics: ExecutionMetrics

  /**
   * The role the executor is optimized for.
   */
  def role: Executor.Role

  /**
   * The number of operations a fiber should run before yielding.
   */
  def yieldOpCount: Int

  /**
   * Submits a task for execution.
   */
  def submit(runnable: Runnable): Boolean

  /**
   * Initiates shutdown of the executor.
   */
  def shutdown(): Unit

  /**
   * Views this `Executor` as a Scala `ExecutionContext`.
   */
  lazy val asEC: ExecutionContext =
    new ExecutionContext {
      override def execute(r: Runnable): Unit =
        if (!submit(r)) throw new RejectedExecutionException("Rejected: " + r.toString)

      override def reportFailure(cause: Throwable): Unit =
        cause.printStackTrace
    }
}

object Executor extends Serializable {
  sealed abstract class Role extends Product with Serializable

  /**
   * An executor optimized for synchronous tasks, which yield
   * to the runtime infrequently or never.
   */
  final case object Unyielding extends Role

  /**
   * An executor optimized for asynchronous tasks, which yield
   * frequently to the runtime.
   */
  final case object Yielding extends Role

  /**
   * Creates a new default executor of the specified type.
   */
  final def newDefaultExecutor(role: Role): Executor = role match {
    case Unyielding =>
      fromThreadPoolExecutor(role, _ => Int.MaxValue) {
        val corePoolSize  = Int.MaxValue
        val maxPoolSize   = Int.MaxValue
        val keepAliveTime = 1000L
        val timeUnit      = TimeUnit.MILLISECONDS
        val workQueue     = new SynchronousQueue[Runnable]()
        val threadFactory = new NamedThreadFactory("zio-default-unyielding", true)

        val threadPool = new ThreadPoolExecutor(
          corePoolSize,
          maxPoolSize,
          keepAliveTime,
          timeUnit,
          workQueue,
          threadFactory
        )
        threadPool.allowCoreThreadTimeOut(true)

        threadPool
      }

    case Yielding =>
      fromThreadPoolExecutor(role, _ => Int.1024) {
        val corePoolSize  = Runtime.getRuntime.availableProcessors() * 2
        val maxPoolSize   = corePoolSize
        val keepAliveTime = 1000L
        val timeUnit      = TimeUnit.MILLISECONDS
        val workQueue     = new LinkedBlockingQueue[Runnable]()
        val threadFactory = new NamedThreadFactory("zio-default-yielding", true)

        val threadPool = new ThreadPoolExecutor(
          corePoolSize,
          maxPoolSize,
          keepAliveTime,
          timeUnit,
          workQueue,
          threadFactory
        )
        threadPool.allowCoreThreadTimeOut(true)

        threadPool
      }
  }

  /**
   * Creates an `Executor` from a Scala `ExecutionContext`. You must manually
   * supply the concurrency level, since `ExecutionContext` cannot
   * provide this detail.
   */
  final def fromExecutionContext(role0: Role, yieldOpCount0: ExecutrionMetrics => Int, concurrency0: Int)(ec: ExecutionContext): Executor =
    new Executor {
      import java.util.concurrent.atomic.AtomicLong

      val _enqueuedCount = new AtomicLong()
      val _dequeuedCount = new AtomicLong()

      def role = role0

      val metrics = new ExecutionMetrics {
        def concurrency: Int = concurrency0

        def capacity: Int = Int.MaxValue

        def size: Int = (enqueuedCount - dequeuedCount).toInt

        def enqueuedCount: Long = _enqueuedCount.get

        def dequeuedCount: Long = _dequeuedCount.get
      }

      def yieldOpCount = yieldOpCount0(metrics)

      def submit(runnable: Runnable): Boolean =
        try {
          ec.execute(new Runnable() {
            def run: Unit =
              try runnable.run()
              finally {
                val _ = _dequeuedCount.incrementAndGet()
              }
          })

          val _ = _enqueuedCount.incrementAndGet()

          true
        } catch {
          case _: RejectedExecutionException =>
            false
        }

      def shutdown(): Unit = ()
    }

  /**
   * Constructs an `Executor` from a Java `ThreadPoolExecutor`.
   */
  final def fromThreadPoolExecutor(role0: Role, yieldOpCount0: ExecutionMetrics => Int)(es: ThreadPoolExecutor): Executor =
    new Executor {
      def role = role0

      val metrics = new ExecutionMetrics {
        def concurrency: Int = es.getMaximumPoolSize()

        def capacity: Int = {
          val queue = es.getQueue()

          val remaining = queue.remainingCapacity()

          if (remaining == Int.MaxValue) remaining
          else remaining + queue.size
        }

        def size: Int = es.getQueue().size

        def enqueuedCount: Long = es.getTaskCount()

        def dequeuedCount: Long = enqueuedCount - size.toLong
      }

      def yieldOpCount = yieldOpCount0(metrics)

      def submit(runnable: Runnable): Boolean =
        try {
          es.execute(runnable)

          true
        } catch {
          case _: RejectedExecutionException => false
        }

      def shutdown(): Unit = { val _ = es.shutdown() }
    }
}
