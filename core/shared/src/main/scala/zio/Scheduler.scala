package zio

import java.io.IOException
import java.nio.channels.{SelectableChannel, Selector, SelectionKey}
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, ScheduledFuture, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable

/**
 * A scheduler that uses Java NIO for efficient non-blocking timer management.
 */
trait Scheduler {
  def schedule(task: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture[_]
  def shutdown(): Unit
}

object Scheduler {
  
  /**
   * Creates a new NIO-based scheduler.
   */
  def nio(): Scheduler = new NIOScheduler()
  
  /**
   * Creates a default scheduler based on java.util.concurrent.ScheduledThreadPoolExecutor.
   */
  def default(): Scheduler = new DefaultScheduler()
}

/**
 * NIO-based scheduler implementation using Selector for efficient timer management.
 */
class NIOScheduler extends Scheduler {
  private val selector: Selector = Selector.open()
  private val tasks: ConcurrentLinkedQueue[ScheduledTask] = new ConcurrentLinkedQueue[ScheduledTask]()
  private val running: AtomicBoolean = new AtomicBoolean(true)
  private val scheduledTasks: ConcurrentHashMap[ScheduledFuture[_], Boolean] = new ConcurrentHashMap[ScheduledFuture[_], Boolean]()
  
  private val workerThread: Thread = new Thread(new Runnable {
    def run(): Unit = {
      while (running.get()) {
        try {
          processTasks()
          selector.select(10)
        } catch {
          case _: IOException => 
            // Selector error, continue
        }
      }
    }
  })
  
  workerThread.setDaemon(true)
  workerThread.start()
  
  private def processTasks(): Unit = {
    var task = tasks.poll()
    while (task != null) {
      try {
        task.run()
      } catch {
        case _: Exception => 
          // Task execution error, continue
      }
      task = tasks.poll()
    }
  }
  
  def schedule(task: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture[_] = {
    val scheduledTask = new ScheduledTask(task, unit.toMillis(delay))
    tasks.offer(scheduledTask)
    selector.wakeup()
    scheduledTask.getFuture()
  }
  
  def shutdown(): Unit = {
    running.set(false)
    selector.wakeup()
    workerThread.join(1000)
    selector.close()
  }
  
  private class ScheduledTask(task: Runnable, delayMs: Long) {
    private val future: NIOScheduledFuture[_] = new NIOScheduledFuture[AnyRef](null, delayMs, this)
    
    def run(): Unit = {
      if (!future.isCancelled) {
        task.run()
      }
    }
    
    def getFuture(): ScheduledFuture[_] = future
  }
  
  private class NIOScheduledFuture[T](result: T, delayMs: Long, task: ScheduledTask) extends ScheduledFuture[T] {
    @volatile private var cancelled: Boolean = false
    @volatile private var done: Boolean = false
    
    def cancel(mayInterruptIfRunning: Boolean): Boolean = {
      cancelled = true
      true
    }
    
    def isCancelled: Boolean = cancelled
    
    def isDone: Boolean = done
    
    def get(): T = result
    
    def get(timeout: Long, unit: TimeUnit): T = result
    
    def getDelay(unit: TimeUnit): Long = unit.convert(delayMs, TimeUnit.MILLISECONDS)
    
    def compareTo(other: ScheduledFuture[_]): Int = {
      val thisDelay = getDelay(TimeUnit.MILLISECONDS)
      val otherDelay = other.getDelay(TimeUnit.MILLISECONDS)
      thisDelay.compareTo(otherDelay)
    }
  }
}

/**
 * Default scheduler implementation using ScheduledThreadPoolExecutor.
 */
class DefaultScheduler extends Scheduler {
  private val executor = new java.util.concurrent.ScheduledThreadPoolExecutor(
    Runtime.getRuntime.availableProcessors(),
    new java.util.concurrent.ThreadFactory {
      private val counter = new java.util.concurrent.atomic.AtomicInteger(0)
      def newThread(r: Runnable): Thread = {
        val t = new Thread(r, s"zio-scheduler-${counter.incrementAndGet()}")
        t.setDaemon(true)
        t
      }
    }
  )
  
  executor.setRemoveOnCancelPolicy(true)
  
  def schedule(task: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture[_] = {
    executor.schedule(task, delay, unit)
  }
  
  def shutdown(): Unit = {
    executor.shutdown()
    executor.awaitTermination(5, TimeUnit.SECONDS)
  }
}
