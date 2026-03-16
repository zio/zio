package zio.internal.concurrent

import zio.test._
import zio.test.Assertion._

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.DurationInt

object SchedulerSpec extends ZIOSpecDefault {
  
  def spec = suite("SchedulerSpec")(
    
    test("concurrent task execution without race conditions") {
      val scheduler = Scheduler(4)
      val counter = new AtomicInteger(0)
      val tasks = (1 to 100).map { _ =>
        () => counter.incrementAndGet()
      }
      
      tasks.foreach(task => scheduler.schedule(() => task()))
      
      // Wait for all tasks to complete
      TestClock.adjust(1.second)
      
      val stats = scheduler.shutdown()
      assertTrue(counter.get() == 100)
    },
    
    test("scheduler handles high contention correctly") {
      val scheduler = Scheduler(8)
      val sharedList = scala.collection.mutable.ListBuffer[Int]()
      val lock = new Object()
      
      val tasks = (1 to 200).map { i =>
        () => {
          lock.synchronized {
            sharedList += i
          }
        }
      }
      
      tasks.foreach(task => scheduler.schedule(() => task()))
      
      // Wait for completion
      TestClock.adjust(2.seconds)
      
      scheduler.shutdown()
      assertTrue(sharedList.length == 200)
    },
    
    test("tasks are executed exactly once") {
      val scheduler = Scheduler(2)
      val executionCounts = Array.fill(50)(new AtomicInteger(0))
      
      val tasks = (0 until 50).map { i =>
        () => executionCounts(i).incrementAndGet()
      }
      
      tasks.foreach(task => scheduler.schedule(() => task()))
      
      TestClock.adjust(1.second)
      
      val results = executionCounts.map(_.get())
      scheduler.shutdown()
      assertTrue(results.forall(_ == 1))
    },
    
    test("scheduler stats are accurate") {
      val scheduler = Scheduler(2)
      val latch = new java.util.concurrent.CountDownLatch(10)
      
      val tasks = (1 to 10).map { _ =>
        () => latch.countDown()
      }
      
      tasks.foreach(task => scheduler.schedule(() => task()))
      
      // Wait for all tasks to complete
      latch.await()
      
      val stats = scheduler.getStats()
      scheduler.shutdown()
      
      assertTrue(
        stats.scheduledTasks == 10,
        stats.completedTasks == 10,
        stats.pendingTasks >= 0
      )
    },
    
    test("graceful shutdown stops all workers") {
      val scheduler = Scheduler(4)
      var executed = false
      
      scheduler.schedule(() => executed = true)
      
      // Allow some time for execution
      Thread.sleep(100)
      
      scheduler.shutdown()
      
      assertTrue(executed)
    }
  )
}