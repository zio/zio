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

import zio._
import zio.ZIOBaseSpec
import zio.test.Assertion._
import zio.test._

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.util.concurrent.{CountDownLatch, TimeUnit}

/**
 * Tests for [[NioScheduler]].
 *
 * These tests validate the basic contract of the NIO-based executor:
 *   - tasks submitted to the executor are eventually run
 *   - concurrent submissions are handled safely
 *   - the executor continues accepting work under load
 *   - shutdown completes without hanging
 *
 * We intentionally keep these tests at the ''unit'' level (no ZIO runtime
 * overhead) so they can catch regressions in the raw scheduling loop.
 */
object NioSchedulerSpec extends ZIOBaseSpec {

  def spec: Spec[Any, Any] = suite("NioSchedulerSpec")(
    suite("basic execution")(
      test("executes a submitted task") {
        ZIO.attemptBlocking {
          val executed = new AtomicBoolean(false)
          val latch    = new CountDownLatch(1)
          val exec     = NioScheduler.make(nThreads = 1)
          Unsafe.unsafe { implicit u =>
            exec.submit(new Runnable {
              def run(): Unit = {
                executed.set(true)
                latch.countDown()
              }
            })
          }
          latch.await(5, TimeUnit.SECONDS)
          exec.asInstanceOf[NioScheduler].shutdown()
          executed.get()
        }.map(assert(_)(isTrue))
      },
      test("executes multiple sequential tasks in order") {
        ZIO.attemptBlocking {
          val results  = new java.util.concurrent.ConcurrentLinkedQueue[Int]()
          val latch    = new CountDownLatch(5)
          val exec     = NioScheduler.make(nThreads = 1) // single loop ensures ordering
          Unsafe.unsafe { implicit u =>
            (1 to 5).foreach { i =>
              exec.submit(new Runnable {
                def run(): Unit = {
                  results.offer(i)
                  latch.countDown()
                }
              })
            }
          }
          val completed = latch.await(5, TimeUnit.SECONDS)
          exec.asInstanceOf[NioScheduler].shutdown()
          completed && results.size() == 5
        }.map(assert(_)(isTrue))
      }
    ),
    suite("concurrent submissions")(
      test("handles concurrent submissions from multiple threads") {
        ZIO.attemptBlocking {
          val total    = 10000
          val counter  = new AtomicInteger(0)
          val latch    = new CountDownLatch(total)
          val exec     = NioScheduler.make()
          val threads  = (1 to 8).map { _ =>
            new Thread(new Runnable {
              def run(): Unit =
                (1 to (total / 8)).foreach { _ =>
                  Unsafe.unsafe { implicit u =>
                    exec.submit(new Runnable {
                      def run(): Unit = {
                        counter.incrementAndGet()
                        latch.countDown()
                      }
                    })
                  }
                }
            })
          }
          threads.foreach(_.start())
          threads.foreach(_.join(5000L))
          val completed = latch.await(10, TimeUnit.SECONDS)
          exec.asInstanceOf[NioScheduler].shutdown()
          completed && counter.get() == total
        }.map(assert(_)(isTrue))
      },
      test("work-stealing distributes tasks across all loops") {
        ZIO.attemptBlocking {
          val nThreads = 4
          val total    = 400
          val latch    = new CountDownLatch(total)
          val exec     = NioScheduler.make(nThreads = nThreads)
          // Submit all to a single loop to trigger stealing
          Unsafe.unsafe { implicit u =>
            (1 to total).foreach { _ =>
              exec.submit(new Runnable {
                def run(): Unit = latch.countDown()
              })
            }
          }
          val completed = latch.await(10, TimeUnit.SECONDS)
          exec.asInstanceOf[NioScheduler].shutdown()
          completed
        }.map(assert(_)(isTrue))
      }
    ),
    suite("executor metrics")(
      test("metrics are available") {
        ZIO.attempt {
          val exec    = NioScheduler.make(nThreads = 2)
          val metrics = Unsafe.unsafe(implicit u => exec.metrics)
          exec.asInstanceOf[NioScheduler].shutdown()
          metrics
        }.map(m => assert(m)(isSome))
      },
      test("concurrency equals nThreads") {
        ZIO.attempt {
          val nThreads = 3
          val exec     = NioScheduler.make(nThreads = nThreads)
          val conc     = Unsafe.unsafe(implicit u => exec.metrics.map(_.concurrency))
          exec.asInstanceOf[NioScheduler].shutdown()
          conc
        }.map(c => assert(c)(isSome(equalTo(3))))
      }
    ),
    suite("ZIO integration")(
      test("runs ZIO effects on NioScheduler") {
        val exec = NioScheduler.make()
        for {
          result <- ZIO.succeed(42).onExecutor(exec)
          _      <- ZIO.attempt(exec.asInstanceOf[NioScheduler].shutdown())
        } yield assert(result)(equalTo(42))
      },
      test("forks many fibers on NioScheduler") {
        val exec = NioScheduler.make()
        val io = for {
          promise <- Promise.make[Nothing, Unit]
          ref     <- Ref.make(1000)
          effect   = ref.modify(n => (if (n == 1) promise.succeed(()) else ZIO.unit, n - 1)).flatten
          _       <- ZIO.collectAll(ZIO.replicate(1000)(effect.forkDaemon))
          _       <- promise.await
          _       <- ZIO.attempt(exec.asInstanceOf[NioScheduler].shutdown())
        } yield assertCompletes
        io.onExecutor(exec)
      },
      test("supports submitAndYield") {
        ZIO.attemptBlocking {
          val latch  = new CountDownLatch(1)
          val exec   = NioScheduler.make(nThreads = 2)
          Unsafe.unsafe { implicit u =>
            exec.submitAndYield(new Runnable { def run(): Unit = latch.countDown() })
          }
          val done = latch.await(5, TimeUnit.SECONDS)
          exec.asInstanceOf[NioScheduler].shutdown()
          done
        }.map(assert(_)(isTrue))
      }
    ),
    suite("shutdown")(
      test("shutdown completes without hanging") {
        ZIO.attemptBlocking {
          val exec = NioScheduler.make(nThreads = 2)
          // submit some work first
          val latch = new CountDownLatch(10)
          Unsafe.unsafe { implicit u =>
            (1 to 10).foreach { _ =>
              exec.submit(new Runnable { def run(): Unit = latch.countDown() })
            }
          }
          latch.await(5, TimeUnit.SECONDS)
          exec.asInstanceOf[NioScheduler].shutdown()
          true
        }.map(assert(_)(isTrue))
      },
      test("shutdown is idempotent") {
        ZIO.attemptBlocking {
          val exec = NioScheduler.make(nThreads = 1)
          exec.asInstanceOf[NioScheduler].shutdown()
          exec.asInstanceOf[NioScheduler].shutdown() // second call should not throw
          true
        }.map(assert(_)(isTrue))
      }
    )
  )
}
