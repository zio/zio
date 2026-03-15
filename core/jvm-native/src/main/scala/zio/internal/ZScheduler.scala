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
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import scala.annotation.tailrec

/**
 * A work-stealing scheduler for the JVM and Native platforms.
 */
private[zio] final class ZScheduler(implicit unsafe: Unsafe) extends Scheduler {

  import ZScheduler._

  private[this] val poolSize = Runtime.getRuntime.availableProcessors()
  
  private[this] val workers = new Array[Worker](poolSize)
  
  private[this] val globalQueue = new ConcurrentLinkedQueue[Runnable]()
  
  private[this] val idleWorkers = new ConcurrentLinkedQueue[Worker]()
  
  private[this] val activeWorkers = new AtomicInteger(0)
  
  private[this] var shutdownFlag = false
  
  // Initialize workers
  for (i <- 0 until poolSize) {
    workers(i) = new Worker(i)
    workers(i).start()
  }

  override def schedule(task: Runnable): Unit = {
    if (shutdownFlag) return
    
    // Try to submit to an idle worker first
    val worker = idleWorkers.poll()
    if (worker ne null) {
      worker.enqueue(task)
      worker.unpark()
    } else {
      // Submit to global queue
      globalQueue.offer(task)
      
      // Try to wake up an idle worker or activate a new one
      val idleWorker = idleWorkers.poll()
      if (idleWorker ne null) {
        idleWorker.unpark()
      } else if (activeWorkers.get() < poolSize) {
        // Try to activate a worker to process global queue
        activateWorker()
      }
    }
  }
  
  private def activateWorker(): Unit = {
    val currentActive = activeWorkers.get()
    if (currentActive < poolSize) {
      if (activeWorkers.compareAndSet(currentActive, currentActive + 1)) {
        // Find an inactive worker and wake it
        for (i <- 0 until poolSize) {
          val worker = workers(i)
          if (!worker.isActive) {
            worker.unpark()
            return
          }
        }
        // No inactive worker found, decrement back
        activeWorkers.decrementAndGet()
      }
    }
  }

  override def shutdown(): Unit = {
    shutdownFlag = true
    for (i <- 0 until poolSize) {
      workers(i).unpark()
    }
  }

  private class Worker(val id: Int) extends Thread(s"ZScheduler-Worker-$id") {
    
    private[this] val localQueue = new ConcurrentLinkedQueue[Runnable]()
    
    private[this] var active = false
    
    private[this] var parked = false
    
    @volatile private[this] var _parked = false
    
    def isActive: Boolean = active
    
    def enqueue(task: Runnable): Unit = {
      localQueue.offer(task)
    }
    
    def unpark(): Unit = {
      if (_parked) {
        _parked = false
        LockSupport.unpark(this)
      }
    }
    
    private def park(): Unit = {
      _parked = true
      // Check for work one more time before parking
      if (localQueue.isEmpty && globalQueue.isEmpty) {
        // Add to idle queue before parking
        idleWorkers.offer(this)
        active = false
        activeWorkers.decrementAndGet()
        
        // Double-check to avoid missing work
        if (localQueue.isEmpty && globalQueue.isEmpty) {
          LockSupport.park()
        }
        
        // Woke up, remove from idle queue if still there
        idleWorkers.remove(this)
        active = true
        activeWorkers.incrementAndGet()
      }
      _parked = false
    }
    
    override def run(): Unit = {
      active = true
      activeWorkers.incrementAndGet()
      
      while (!shutdownFlag) {
        // Try to get work from local queue
        var task = localQueue.poll()
        
        // If no local work, try global queue
        if (task eq null) {
          task = globalQueue.poll()
        }
        
        // If still no work, try work stealing
        if (task eq null) {
          task = stealWork()
        }
        
        if (task ne null) {
          try {
            task.run()
          } catch {
            case t: Throwable =>
              // Log and continue
              t.printStackTrace()
          }
        } else {
          // No work available, park with backoff
          park()
        }
      }
    }
    
    private def stealWork(): Runnable = {
      // Try to steal from other workers
      for (i <- 0 until poolSize) {
        if (i != id) {
          val stolen = workers(i).localQueue.poll()
          if (stolen ne null) {
            return stolen
          }
        }
      }
      null
    }
  }
}

private[zio] object ZScheduler {
  private val ParkTimeoutNanos = 1000L // 1 microsecond minimum park
}
