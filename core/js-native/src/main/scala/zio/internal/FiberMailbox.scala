/*
 * Copyright 2022-2026 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Scala.js and Scala Native keep the historical concurrent queue. The JVM
 * implementation is a specialized single-consumer mailbox; this one only has to
 * match its observable behavior on a single thread and under the runtime's
 * existing `running` flag protocol.
 */
private[zio] final class FiberMailbox {
  private val queue = new ConcurrentLinkedQueue[FiberMessage]()

  def add(message: FiberMessage): Unit = {
    queue.add(message)
    ()
  }

  def poll(): FiberMessage =
    queue.poll()

  def pollRelaxed(): FiberMessage =
    queue.poll()

  def isEmpty(): Boolean =
    queue.isEmpty()
}
