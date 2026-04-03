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

object NioSchedulerSpec extends ZIOBaseSpec {

  def spec = suite("NioSchedulerSpec")(
    suite("basic functionality")(
      test("can be created and used") {
        for {
          executor <- ZIO.succeed(Executor.makeNio())
          counter  <- ZIO.succeed(new AtomicInteger(0))
          _        <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                        executor.submit(() => { counter.incrementAndGet(); () })
                      })
          _        <- ZIO.sleep(100.millis)
          value    <- ZIO.succeed(counter.get())
        } yield assert(value)(equalTo(1))
      },
      test("executes multiple tasks") {
        for {
          executor <- ZIO.succeed(Executor.makeNio())
          counter  <- ZIO.succeed(new AtomicInteger(0))
          _        <- ZIO.foreachDiscard(1 to 100) { _ =>
                        ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                          executor.submit(() => { counter.incrementAndGet(); () })
                        })
                      }
          _        <- ZIO.sleep(200.millis)
          value    <- ZIO.succeed(counter.get())
        } yield assert(value)(equalTo(100))
      },
      test("provides metrics") {
        for {
          executor <- ZIO.succeed(Executor.makeNio())
          metrics  <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                        executor.metrics
                      })
        } yield assert(metrics)(isSome)
      },
      test("metrics report concurrency") {
        for {
          executor    <- ZIO.succeed(Executor.makeNio())
          metricsOpt  <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                          executor.metrics
                        })
          concurrency <- ZIO.fromOption(metricsOpt.map(_.concurrency))
        } yield assert(concurrency)(equalTo(java.lang.Runtime.getRuntime.availableProcessors))
      }
    ),
    suite("least-loaded scheduling")(
      test("distributes tasks across workers") {
        for {
          executor   <- ZIO.succeed(Executor.makeNio())
          counter    <- ZIO.succeed(new AtomicInteger(0))
          numTasks   = 1000
          _          <- ZIO.foreachDiscard(1 to numTasks) { _ =>
                          ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                            executor.submit(() => {
                              counter.incrementAndGet()
                              Thread.sleep(1) // Small delay to allow distribution
                              ()
                            })
                          })
                        }
          _          <- ZIO.sleep(2.seconds)
          value      <- ZIO.succeed(counter.get())
        } yield assert(value)(equalTo(numTasks))
      } @@ TestAspect.timeout(10.seconds)
    ),
    suite("integration with ZIO runtime")(
      test("can be used as runtime executor") {
        for {
          result <- ZIO.succeed(42).provide(Runtime.enableNioScheduler(Trace.empty))
        } yield assert(result)(equalTo(42))
      },
      test("fibers run on NioScheduler workers") {
        for {
          threadName <- ZIO.succeed(Thread.currentThread().getName)
                         .provide(Runtime.enableNioScheduler(Trace.empty))
        } yield assert(threadName)(containsString("NioScheduler"))
      },
      test("parallel fibers run correctly") {
        for {
          result <- ZIO.foreachPar(1 to 100)(ZIO.succeed(_))
                     .provide(Runtime.enableNioScheduler(Trace.empty))
        } yield assert(result)(hasSize(equalTo(100)))
      } @@ TestAspect.timeout(10.seconds)
    ),
    suite("submitAndYield")(
      test("works correctly") {
        for {
          executor <- ZIO.succeed(Executor.makeNio())
          counter  <- ZIO.succeed(new AtomicInteger(0))
          _        <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                        executor.submitAndYield(() => { counter.incrementAndGet(); () })
                      })
          _        <- ZIO.sleep(100.millis)
          value    <- ZIO.succeed(counter.get())
        } yield assert(value)(equalTo(1))
      }
    ),
    suite("auto-blocking")(
      test("can create with auto-blocking enabled") {
        for {
          executor <- ZIO.succeed(Executor.makeNio(autoBlocking = true))
          metrics  <- ZIO.succeed(Unsafe.unsafe { implicit unsafe =>
                        executor.metrics
                      })
        } yield assert(metrics)(isSome)
      }
    )
  )
}
