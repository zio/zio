package zio.internal.concurrent

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong}
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

/**
 * A thread-safe scheduler implementation that fixes concurrency issues
 * related to race conditions in task scheduling and execution.
 */
class Scheduler private (
  val threadCount: Int,
  val threadFactory: Option[(Runnable) => Thread]
) {
  
  private[this] val tasks = new ConcurrentLinkedQueue[Runnable]()
  private[this] val workers = new ConcurrentHashMap[Int, Worker]()
  private[this] val running = new AtomicBoolean(true)
  private[this] val scheduledTasks = new AtomicLong(0)
  private[this] val completedTasks = new AtomicLong(0)
  
  // Initialize worker threads
  for (i <- 0 until threadCount) {
    val worker = new Worker(i)
    workers.put(i, worker)
    val thread = threadFactory.map(_(worker)).getOrElse(new Thread(worker, s"zio-scheduler-worker-$i"))
    thread.setDaemon(true)
    thread.start()
  }
  
  /**
   * Schedule a task for execution
   */
def schedule(task: Runnable): Unit = {
    if (running.get()) {
      tasks.offer(task)
      scheduledTasks.incrementAndGet()
      // Wake up a waiting worker if needed
      notifyWorkers()
    }
  }
  
  /**
   * Shutdown the scheduler gracefully
   */
def shutdown(): Unit = {
    running.set(false)
    // Interrupt all worker threads
    workers.values().forEach(_.interrupt())
  }
  
  /**
   * Get current statistics about the scheduler
   */
def getStats(): SchedulerStats = {
    SchedulerStats(
      scheduledTasks.get(),
      completedTasks.get(),
      tasks.size(),
      workers.size()
    )
  }
  
  private def notifyWorkers(): Unit = {
    // In a real implementation, this might signal condition variables
    // For now, we rely on the polling mechanism in Worker
  }
  
  private class Worker(id: Int) extends Runnable {
    private[this] val isInterrupted = new AtomicBoolean(false)
    
    override def run(): Unit = {
      while (running.get() && !isInterrupted.get()) {
        try {
          val task = pollTask()
          if (task != null) {
            try {
              task.run()
              completedTasks.incrementAndGet()
            } catch {
              case _: InterruptedException =>
                isInterrupted.set(true)
                Thread.currentThread().interrupt()
              case t: Throwable =>
                // Log error but continue processing
                System.err.println(s"Error executing task in worker $id: ${t.getMessage}")
            }
          } else {
            // No task available, briefly sleep to avoid busy waiting
            Thread.sleep(1)
          }
        } catch {
          case _: InterruptedException =>
            isInterrupted.set(true)
            Thread.currentThread().interrupt()
        }
      }
    }
    
    @tailrec
    private def pollTask(): Runnable = {
      val task = tasks.poll()
      if (task == null && running.get()) {
        // Brief pause before checking again
        Thread.sleep(1)
        pollTask()
      } else {
        task
      }
    }
    
    def interrupt(): Unit = {
      isInterrupted.set(true)
      Thread.currentThread().interrupt()
    }
  }
}

object Scheduler {
  
  def apply(threadCount: Int = Runtime.getRuntime().availableProcessors()): Scheduler = {
    new Scheduler(threadCount, None)
  }
  
  def apply(threadCount: Int, threadFactory: (Runnable) => Thread): Scheduler = {
    new Scheduler(threadCount, Some(threadFactory))
  }
}

case class SchedulerStats(
  scheduledTasks: Long,
  completedTasks: Long,
  pendingTasks: Int,
  workerCount: Int
)