// Demo script to show the ZScheduler optimization for Issue #9878
// This demonstrates reduced unpark operations in the hotpath

import zio._
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

/**
 * Performance demonstration for ZScheduler unpark optimization
 * 
 * Before: maybeUnparkWorker called on every submit/submitAndYield
 * After: maybeUnparkWorkerThrottled reduces unnecessary unpark operations
 * 
 * The optimization:
 * 1. Tracks the last unpark time to avoid redundant calls within 1 microsecond
 * 2. Uses heuristics to determine when unparking is truly necessary:
 *    - Low worker utilization (currentActive < poolSize / 2)
 *    - Work waiting in global queue
 *    - Sufficient time elapsed since last unpark
 * 
 * This reduces the frequency of expensive LockSupport.unpark() calls in the hotpath
 * while maintaining fairness and responsiveness.
 */
object ZSchedulerOptimizationDemo extends ZIOAppDefault {
  
  // Simple counter to track unpark operations
  val unparkCounter = new AtomicLong(0)
  
  def mockUnparkOperation(): Unit = {
    unparkCounter.incrementAndGet()
    // Simulate the cost of LockSupport.unpark
    LockSupport.parkNanos(100) // 100ns delay to simulate unpark cost
  }
  
  // Simulate the old behavior - unpark on every submission
  def simulateOldBehavior(submissions: Int): Long = {
    val startTime = System.nanoTime()
    (0 until submissions).foreach { _ =>
      // Every submission triggers an unpark
      mockUnparkOperation()
    }
    System.nanoTime() - startTime
  }
  
  // Simulate the new optimized behavior - throttled unparks
  def simulateOptimizedBehavior(submissions: Int): Long = {
    val startTime = System.nanoTime()
    var lastUnpark = 0L
    val throttleNanos = 1000L // 1 microsecond throttle
    
    (0 until submissions).foreach { _ =>
      val now = System.nanoTime()
      // Only unpark if enough time has passed (throttling)
      if ((now - lastUnpark) > throttleNanos) {
        mockUnparkOperation()
        lastUnpark = now
      }
    }
    System.nanoTime() - startTime
  }
  
  def run = for {
    _ <- Console.printLine("ZScheduler Optimization Demo - Issue #9878")
    _ <- Console.printLine("Comparing unpark frequencies in submit() hotpath")
    _ <- Console.printLine()
    
    submissions = 100000
    
    // Reset counter and test old behavior
    _ <- ZIO.succeed(unparkCounter.set(0))
    oldTime <- ZIO.succeed(simulateOldBehavior(submissions))
    oldUnparks = unparkCounter.get()
    
    // Reset counter and test optimized behavior  
    _ <- ZIO.succeed(unparkCounter.set(0))
    newTime <- ZIO.succeed(simulateOptimizedBehavior(submissions))
    newUnparks = unparkCounter.get()
    
    _ <- Console.printLine(s"Results for $submissions submissions:")
    _ <- Console.printLine(s"Old behavior: $oldUnparks unpark operations, ${oldTime / 1000000}ms")
    _ <- Console.printLine(s"Optimized:    $newUnparks unpark operations, ${newTime / 1000000}ms")
    _ <- Console.printLine()
    
    unparkReduction = ((oldUnparks - newUnparks).toDouble / oldUnparks * 100).round
    timeReduction = ((oldTime - newTime).toDouble / oldTime * 100).round
    
    _ <- Console.printLine(s"Improvement:")
    _ <- Console.printLine(s"- Unpark operations reduced by: ${unparkReduction}%")
    _ <- Console.printLine(s"- Execution time reduced by: ${timeReduction}%")
    _ <- Console.printLine()
    _ <- Console.printLine("This demonstrates how throttling reduces expensive")
    _ <- Console.printLine("LockSupport.unpark() calls in the submit() hotpath")
    
  } yield ()
}