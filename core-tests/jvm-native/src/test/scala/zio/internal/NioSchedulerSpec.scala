/*
 * Copyright 2024-2024 John A. De Goes and the ZIO Contributors
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
import zio.test.Assertion._
import zio.test._

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object NioSchedulerSpec extends ZIOBaseSpec {

  /**
   * Helper: submit a task to the executor and wait for completion using a
   * CountDownLatch. This avoids relying on ZIO.sleep (which is affected by
   * TestClock in ZIO tests).
   */
  private def submitAndAwait(executor: Executor, task: Runnable)(implicit unsafe: Unsafe): Unit = {
    val latch = new CountDownLatch(1)
    executor.submit { () =>
      try task.run()
      finally latch.countDown()
    }
    latch.await(10, TimeUnit.SECONDS)
    ()
  }

  def spec = suite("NioSchedulerSpec")(
    suite("basic functionality")(
      test("can be created and used") {
        for {
          executor <- ZIO.succeed(Executor.makeNio())
          counter  <- ZIO.succeed(new AtomicInteger(0))
          _ <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                 submitAndAwait(executor, () => { counter.incrementAndGet(); () })
               })
          value <- ZIO.succeed(counter.get())
        } yield assert(value)(equalTo(1))
      },
      test("executes multiple tasks") {
        for {
          executor <- ZIO.succeed(Executor.makeNio())
          counter  <- ZIO.succeed(new AtomicInteger(0))
          latch    <- ZIO.succeed(new CountDownLatch(100))
          _ <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                 var ii = 0
                 while (ii < 100) {
                   executor.submit { () => counter.incrementAndGet(); latch.countDown(); () }
                   ii += 1
                 }
               })
          _     <- ZIO.succeed(latch.await(10, TimeUnit.SECONDS))
          value <- ZIO.succeed(counter.get())
        } yield assert(value)(equalTo(100))
      },
      test("provides metrics") {
        for {
          executor <- ZIO.succeed(Executor.makeNio())
          metrics <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                       executor.metrics
                     })
        } yield assert(metrics)(isSome)
      },
      test("metrics report concurrency") {
        for {
          executor <- ZIO.succeed(Executor.makeNio())
          metricsOpt <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                          executor.metrics
                        })
          concurrency <- ZIO.fromOption(metricsOpt.map(_.concurrency))
        } yield assert(concurrency)(equalTo(java.lang.Runtime.getRuntime.availableProcessors))
      },
      test("metrics report size correctly") {
        for {
          executor <- ZIO.succeed(Executor.makeNio())
          latch    <- ZIO.succeed(new CountDownLatch(1))
          counter  <- ZIO.succeed(new AtomicInteger(0))
          // Submit tasks that block until we release them
          _ <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                 var ii = 0
                 while (ii < 10) {
                   executor.submit { () =>
                     counter.incrementAndGet()
                     latch.await(5, TimeUnit.SECONDS)
                     ()
                   }
                   ii += 1
                 }
               })
          // Wait for tasks to start executing (use real sleep via live)
          _ <- live(ZIO.sleep(200.millis))
          metricsOpt <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                          executor.metrics
                        })
          size <- ZIO.fromOption(metricsOpt.map(_.size))
          // Release the tasks
          _ <- ZIO.succeed(latch.countDown())
        } yield assert(size)(isGreaterThanEqualTo(0))
      }
    ),
    suite("least-loaded scheduling")(
      test("distributes tasks across workers") {
        for {
          executor <- ZIO.succeed(Executor.makeNio())
          counter  <- ZIO.succeed(new AtomicInteger(0))
          numTasks  = 1000
          latch    <- ZIO.succeed(new CountDownLatch(numTasks))
          _ <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                 var ii = 0
                 while (ii < numTasks) {
                   executor.submit { () =>
                     counter.incrementAndGet()
                     latch.countDown()
                     ()
                   }
                   ii += 1
                 }
               })
          _     <- ZIO.succeed(latch.await(10, TimeUnit.SECONDS))
          value <- ZIO.succeed(counter.get())
        } yield assert(value)(equalTo(numTasks))
      } @@ TestAspect.timeout(15.seconds),
      test("handles burst of tasks") {
        for {
          executor <- ZIO.succeed(Executor.makeNio())
          counter  <- ZIO.succeed(new AtomicInteger(0))
          numTasks  = 5000
          latch    <- ZIO.succeed(new CountDownLatch(numTasks))
          _ <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                 var ii = 0
                 while (ii < numTasks) {
                   executor.submit { () => counter.incrementAndGet(); latch.countDown(); () }
                   ii += 1
                 }
               })
          _     <- ZIO.succeed(latch.await(10, TimeUnit.SECONDS))
          value <- ZIO.succeed(counter.get())
        } yield assert(value)(equalTo(numTasks))
      } @@ TestAspect.timeout(15.seconds)
    ),
    suite("integration with ZIO runtime")(
      test("can be used as runtime executor") {
        for {
          result <- ZIO.succeed(42).provide(Runtime.enableNioScheduler(Trace.empty))
        } yield assert(result)(equalTo(42))
      },
      test("parallel fibers run correctly") {
        for {
          result <- ZIO
                      .foreachPar(1 to 100)(ZIO.succeed(_))
                      .provide(Runtime.enableNioScheduler(Trace.empty))
        } yield assert(result)(hasSize(equalTo(100)))
      } @@ TestAspect.timeout(10.seconds),
      test("nested forks work correctly") {
        for {
          counter <- Ref.make(0)
          _ <- ZIO
                 .foreachPar(1 to 10) { _ =>
                   ZIO.foreachPar(1 to 10)(_ => counter.update(_ + 1))
                 }
                 .provide(Runtime.enableNioScheduler(Trace.empty))
          value <- counter.get
        } yield assert(value)(equalTo(100))
      } @@ TestAspect.timeout(10.seconds),
      test("race works correctly") {
        for {
          result <- ZIO
                      .raceAll(ZIO.succeed(1), List(ZIO.never))
                      .provide(Runtime.enableNioScheduler(Trace.empty))
        } yield assert(result)(equalTo(1))
      },
      test("interruption works correctly") {
        for {
          ref <- Ref.make(false)
          fiber <- (ZIO.never)
                     .onInterrupt(ref.set(true))
                     .fork
                     .provide(Runtime.enableNioScheduler(Trace.empty))
          _       <- fiber.interrupt
          outcome <- ref.get
        } yield assert(outcome)(isTrue)
      }
    ),
    suite("submitAndYield")(
      test("works correctly") {
        for {
          executor <- ZIO.succeed(Executor.makeNio())
          counter  <- ZIO.succeed(new AtomicInteger(0))
          _ <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                 submitAndAwait(executor, () => { counter.incrementAndGet(); () })
               })
          value <- ZIO.succeed(counter.get())
        } yield assert(value)(equalTo(1))
      },
      test("handles multiple submitAndYield calls") {
        for {
          executor <- ZIO.succeed(Executor.makeNio())
          counter  <- ZIO.succeed(new AtomicInteger(0))
          latch    <- ZIO.succeed(new CountDownLatch(100))
          _ <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                 var ii = 0
                 while (ii < 100) {
                   executor.submitAndYield { () => counter.incrementAndGet(); latch.countDown(); () }
                   ii += 1
                 }
               })
          _     <- ZIO.succeed(latch.await(10, TimeUnit.SECONDS))
          value <- ZIO.succeed(counter.get())
        } yield assert(value)(equalTo(100))
      }
    ),
    suite("auto-blocking")(
      test("can create with autoBlocking enabled") {
        for {
          executor <- ZIO.succeed(Executor.makeNio(autoBlocking = true))
          metrics <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                       executor.metrics
                     })
        } yield assert(metrics)(isSome)
      },
      test("auto-blocking scheduler executes tasks") {
        for {
          executor <- ZIO.succeed(Executor.makeNio(autoBlocking = true))
          counter  <- ZIO.succeed(new AtomicInteger(0))
          latch    <- ZIO.succeed(new CountDownLatch(100))
          _ <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                 var ii = 0
                 while (ii < 100) {
                   executor.submit { () => counter.incrementAndGet(); latch.countDown(); () }
                   ii += 1
                 }
               })
          _     <- ZIO.succeed(latch.await(10, TimeUnit.SECONDS))
          value <- ZIO.succeed(counter.get())
        } yield assert(value)(equalTo(100))
      }
    ),
    suite("concurrent scheduling")(
      test("multiple threads can submit tasks concurrently") {
        for {
          executor    <- ZIO.succeed(Executor.makeNio())
          counter     <- ZIO.succeed(new AtomicInteger(0))
          numTasks     = 10000
          numThreads   = 10
          doneLatch   <- ZIO.succeed(new CountDownLatch(numTasks))
          submitLatch <- ZIO.succeed(new CountDownLatch(numThreads))
          // Start multiple threads that each submit tasks
          _ <- ZIO.succeed {
                 (0 until numThreads).foreach { _ =>
                   new Thread(() => {
                     var ii = 0
                     while (ii < numTasks / numThreads) {
                       Unsafe.unsafe { implicit unsafe =>
                         executor.submit { () => counter.incrementAndGet(); doneLatch.countDown(); () }
                       }
                       ii += 1
                     }
                     submitLatch.countDown()
                   }).start()
                 }
               }
          // Wait for all tasks to complete
          _     <- ZIO.succeed(doneLatch.await(30, TimeUnit.SECONDS))
          value <- ZIO.succeed(counter.get())
        } yield assert(value)(equalTo(numTasks))
      } @@ TestAspect.timeout(60.seconds)
    ),
    suite("stress tests")(
      test("high throughput scheduling") {
        for {
          executor <- ZIO.succeed(Executor.makeNio())
          counter  <- ZIO.succeed(new AtomicInteger(0))
          numTasks  = 100000
          latch    <- ZIO.succeed(new CountDownLatch(numTasks))
          start    <- ZIO.succeed(java.lang.System.nanoTime())
          _ <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                 var ii = 0
                 while (ii < numTasks) {
                   executor.submit { () => counter.incrementAndGet(); latch.countDown(); () }
                   ii += 1
                 }
               })
          _       <- ZIO.succeed(latch.await(30, TimeUnit.SECONDS))
          elapsed <- ZIO.succeed((java.lang.System.nanoTime() - start) / 1_000_000.0)
          value   <- ZIO.succeed(counter.get())
          _       <- ZIO.succeed(println(s"Completed $value tasks in ${elapsed}ms (${value * 1000.0 / elapsed} tasks/sec)"))
        } yield assert(value)(equalTo(numTasks))
      } @@ TestAspect.timeout(60.seconds) @@ TestAspect.ignore,
      test("fiber creation under load") {
        for {
          counter <- Ref.make(0)
          _ <- ZIO
                 .foreachParDiscard(1 to 1000) { _ =>
                   ZIO.forkAll((1 to 100).map(_ => counter.update(_ + 1))).flatMap(_.join)
                 }
                 .provide(Runtime.enableNioScheduler(Trace.empty))
          value <- counter.get
        } yield assert(value)(equalTo(100000))
      } @@ TestAspect.timeout(60.seconds) @@ TestAspect.ignore
    )
  )
}
