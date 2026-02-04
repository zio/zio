/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.internal

import org.openjdk.jmh.annotations._
import java.util.concurrent.{TimeUnit, ConcurrentLinkedQueue}
import zio.internal.FiberMessage

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
class FiberMailboxBenchmark {

  // Baseline: Old Implementation (Generic Queue)
  val oldQueue = new ConcurrentLinkedQueue[AnyRef]()

  // New Implementation (Specialized Mailbox)
  val newMailbox = new FiberMailbox()

  // Static message to avoid allocation during test setup
  val msg = FiberMessage.resumeUnit

  @Benchmark
  def baseline_ConcurrentLinkedQueue(): AnyRef = {
    // Simulate typical fiber flow: Add 1, Poll 1
    oldQueue.offer(msg)
    oldQueue.poll()
  }

  @Benchmark
  def specialized_FiberMailbox(): AnyRef = {
    // Simulate typical fiber flow: Add 1, Poll 1
    newMailbox.offer(msg)
    newMailbox.poll()
  }
}
