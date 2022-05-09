/*
 * Copyright 2022 John A. De Goes and the ZIO Contributors
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

import zio.Chunk

final class GrowableArray[A <: AnyRef](hint: Int) {
  import java.lang.System

  private var stack = new Array[AnyRef](hint)
  private var size  = 0

  def length: Int = size

  def apply(index: Int): A = stack(index).asInstanceOf[A] // No error checking for performance

  def ensureCapacity(elements: Int): Unit = {
    val newSize = size + elements

    if (stack eq null) {
      stack = new Array[AnyRef](newSize)
    } else if (newSize > stack.length) {
      val newStack = new Array[AnyRef](newSize)
      System.arraycopy(stack, 0, newStack, 0, size)
      stack = newStack
    }
  }

  def +=(a: A): Unit = {
    ensureCapacity(1)

    stack(size) = a
    size += 1
  }

  def ++=(as: Array[A]): Unit = {
    ensureCapacity(as.length)

    System.arraycopy(as, 0, stack, size, as.length)

    size += as.length
  }

  def toChunk: Chunk[A] = {
    ensureCapacity(0)

    val chunk = Chunk.fromArray(stack.asInstanceOf[Array[A]]).take(size)

    stack = null

    chunk
  }
}
