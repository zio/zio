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
import zio.test._
import zio.test.Assertion._

object NioSchedulerSpec extends ZIOSpecDefault {

  def spec = suite("NioSchedulerSpec")(
    test("NioScheduler should be creatable") {
      val executor = Executor.makeNio(false)
      assertTrue(executor ne null)
    },
    test("NioScheduler should execute simple tasks") {
      for {
        ref <- Ref.make(0)
        executor = Executor.makeNio(false)
        _ <- ZIO.attempt {
               implicit val unsafe: Unsafe = Unsafe.unsafe
               executor.submit(() => ref.set(42))
             }.orDie
        _ <- ZIO.sleep(100.millis)
        value <- ref.get
      } yield assertTrue(value == 42)
    },
    test("NioScheduler should report metrics") {
      val executor = Executor.makeNio(false)
      val metrics = executor.metrics(Unsafe.unsafe)
      assertTrue(
        metrics.isDefined,
        metrics.get.concurrency == Runtime.getRuntime.availableProcessors(),
        metrics.get.capacity == Int.MaxValue
      )
    },
    test("NioScheduler should execute concurrent tasks") {
      for {
        ref <- Ref.make(0)
        executor = Executor.makeNio(false)
        _ <- ZIO.foreachParDiscard(1 to 100) { i =>
               ZIO.attempt {
                 implicit val unsafe: Unsafe = Unsafe.unsafe
                 executor.submit(() => ref.update(_ + 1))
               }.orDie
             }
        _ <- ZIO.sleep(500.millis)
        value <- ref.get
      } yield assertTrue(value == 100)
    },
    test("NioScheduler submitAndYield should work") {
      for {
        ref <- Ref.make(0)
        executor = Executor.makeNio(false)
        _ <- ZIO.attempt {
               implicit val unsafe: Unsafe = Unsafe.unsafe
               executor.submitAndYield(() => ref.set(100))
             }.orDie
        _ <- ZIO.sleep(100.millis)
        value <- ref.get
      } yield assertTrue(value == 100)
    },
    test("NioScheduler should handle task scheduling without excessive blocking") {
      for {
        ref <- Ref.make(0)
        executor = Executor.makeNio(false)
        _ <- ZIO.foreachDiscard(1 to 1000) { i =>
               ZIO.attempt {
                 implicit val unsafe: Unsafe = Unsafe.unsafe
                 executor.submit(() => ref.update(_ + 1))
               }.orDie
             }
        _ <- ZIO.sleep(1.second)
        value <- ref.get
      } yield assertTrue(value == 1000)
    }
  )
}
