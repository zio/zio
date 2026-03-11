/*
 * Copyright 2026 John A. De Goes and the ZIO Contributors
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
import zio._

import java.util.concurrent.TimeUnit

@State(org.openjdk.jmh.annotations.Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
class FiberSetBenchmark {
  var fiberSet: FiberSet                  = _
  var dummyFiber: Fiber.Runtime[Any, Any] = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    fiberSet = new FiberSet()

    // Create a real FiberRuntime instance using ZIO's internal constructor
    val fiberId = FiberId.Runtime(1, 123L, Trace.empty)
    dummyFiber = FiberRuntime(fiberId, FiberRefs.empty, RuntimeFlags.default)
  }

  @Benchmark
  @Threads(8)
  def addConcurrent(): Unit =
    fiberSet.add(dummyFiber)

  @Benchmark
  @Threads(8)
  def addAndRemoveConcurrent(): Unit = {
    fiberSet.add(dummyFiber)
    fiberSet.remove(dummyFiber)
  }
}
