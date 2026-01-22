/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
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

import zio.Duration
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.lang.ref.ReferenceQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Background daemon thread that periodically polls the ReferenceQueue to clean
 * up garbage collected entries from a FiberSet.
 */
private final class FiberSetCleaner[A <: AnyRef] private (
  fiberSet: FiberSet[A],
  refQueue: ReferenceQueue[A],
  every: Duration
) extends Thread {
  setDaemon(true)

  override def run(): Unit = {
    val millis = every.toMillis

    while (true) {
      try {
        Thread.sleep(millis)
        fiberSet.pollRefQueue()
      } catch {
        case _: InterruptedException =>
          // Thread interrupted, exit gracefully
          return
      }
    }
  }
}

private object FiberSetCleaner {
  private val counter = new AtomicInteger(0)

  def start[A <: AnyRef](
    fiberSet: FiberSet[A],
    refQueue: ReferenceQueue[A],
    every: Duration
  ): Unit = {
    val thread = new FiberSetCleaner(fiberSet, refQueue, every)
    thread.setName(s"zio.internal.FiberSet.Cleaner-${counter.getAndIncrement()}")
    thread.start()
  }
}
