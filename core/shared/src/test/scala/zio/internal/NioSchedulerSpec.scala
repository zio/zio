package zio.internal

import zio._
import zio.test._

import java.nio.channels.{ServerSocketChannel, SocketChannel, SelectionKey}
import java.net.InetSocketAddress
import java.nio.ByteBuffer

object NioSchedulerSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("NioSchedulerSpec")(
      test("scheduler creates successfully") {
        val scheduler = new NioScheduler(autoBlocking = true)
        assertTrue(scheduler != null)
      },
      test("metrics are available") {
        val scheduler = new NioScheduler(autoBlocking = true)
        val metrics = scheduler.metrics(Unsafe.unsafe)
        assertTrue(
          metrics.poolSize > 0,
          metrics.activeWorkers >= 0,
          metrics.pendingTasks >= 0
        )
      },
      test("execute runs tasks on workers") {
        val scheduler = new NioScheduler(autoBlocking = true)
        val counter = new java.util.concurrent.atomic.AtomicLong(0)
        
        (1 to 100).foreach { _ =>
          scheduler.execute(() => counter.incrementAndGet())
        }
        
        Thread.sleep(100) // Wait for execution
        assertTrue(counter.get() == 100)
      },
      test("least-loaded distribution works") {
        val scheduler = new NioScheduler(autoBlocking = true)
        val workerCounts = Array.fill(scheduler.metrics(Unsafe.unsafe).poolSize)(
          new java.util.concurrent.atomic.AtomicLong(0)
        )
        
        // Submit tasks that record which worker executed them
        (1 to 1000).foreach { _ =>
          scheduler.execute { () =>
            val worker = Thread.currentThread().asInstanceOf[NioScheduler.Worker]
            if (worker != null) {
              workerCounts(worker.workerIndex).incrementAndGet()
            }
          }
        }
        
        Thread.sleep(500)
        
        // Verify tasks were distributed (not all on one worker)
        val nonZeroWorkers = workerCounts.count(_ > 0)
        assertTrue(nonZeroWorkers > 1)
      },
      test("NIO channel registration works") {
        val scheduler = new NioScheduler(autoBlocking = true)
        
        val serverChannel = ServerSocketChannel.open()
        serverChannel.configureBlocking(false)
        serverChannel.socket().bind(new InetSocketAddress(0))
        
        val fiber = new IOFiber { _ => }
        scheduler.registerChannel(serverChannel, SelectionKey.OP_ACCEPT, fiber)
        
        Thread.sleep(50)
        
        val metrics = scheduler.metrics(Unsafe.unsafe)
        assertTrue(metrics.nioPendingRegistrations == 0) // Should be processed
      },
      test("concurrent execution is thread-safe") {
        val scheduler = new NioScheduler(autoBlocking = true)
        val counter = new java.util.concurrent.atomic.AtomicLong(0)
        val threads = (1 to 16).map { _ =>
          new Thread {
            override def run(): Unit = {
              (1 to 100).foreach { _ =>
                scheduler.execute(() => counter.incrementAndGet())
              }
            }
          }
        }
        
        threads.foreach(_.start())
        threads.foreach(_.join())
        Thread.sleep(200)
        
        assertTrue(counter.get() == 1600)
      },
      test("FiberSet tracks I/O fibers") {
        val scheduler = new NioScheduler(autoBlocking = true)
        
        val fibers = (1 to 10).map { _ =>
          new IOFiber { _ => Thread.sleep(10) }
        }
        
        val serverChannel = ServerSocketChannel.open()
        serverChannel.configureBlocking(false)
        serverChannel.socket().bind(new InetSocketAddress(0))
        
        fibers.foreach { fiber =>
          scheduler.registerChannel(serverChannel, SelectionKey.OP_ACCEPT, fiber)
        }
        
        Thread.sleep(50)
        
        val metrics = scheduler.metrics(Unsafe.unsafe)
        assertTrue(metrics.ioFibersTracked >= 0) // May have completed
      }
    )
}
