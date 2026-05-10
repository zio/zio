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

import zio.Fiber

import java.lang.ref.WeakReference
import scala.annotation.tailrec

private[zio] abstract class FiberSet {
  def add[A](fiber: Fiber.Runtime[A, _]): Unit
  def remove[A](fiber: Fiber.Runtime[A, _]): Unit
  def fibers: Iterable[Fiber.Runtime[_, _]]
}

private[zio] object FiberSet {

  private val SIZE_ESTIMATE = 1024

  def make(): FiberSet = {
    val map = Platform.newConcurrentSet[WeakReference[Fiber.Runtime[_, _]]](SIZE_ESTIMATE)(Unsafe.unsafe)
    new FiberSetImpl(map)
  }

  private final class FiberSetImpl(
    private val map: JSet[WeakReference[Fiber.Runtime[_, _]]]
  ) extends FiberSet {

    def add[A](fiber: Fiber.Runtime[A, _]): Unit = {
      val ref = new WeakReference(fiber)
      if (!map.add(ref)) {
        map.remove(ref)
        map.add(ref)
      }
    }

    def remove[A](fiber: Fiber.Runtime[A, _]): Unit = {
      val ref = new WeakReference(fiber)
      map.remove(ref)
    }

    def fibers: Iterable[Fiber.Runtime[_, _]] =
      new Iterable[Fiber.Runtime[_, _]] {
        def iterator: Iterator[Fiber.Runtime[_, _]] = {
          val mapIterator = map.iterator()
          new Iterator[Fiber.Runtime[_, _]] {
            private var _next: Fiber.Runtime[_, _] = prefetchOrNull()

            @tailrec
            private def prefetchOrNull(): Fiber.Runtime[_, _] = {
              if (!mapIterator.hasNext) {
                null.asInstanceOf[Fiber.Runtime[_, _]]
              } else {
                val ref = mapIterator.next()
                val fiber = ref.get
                if (fiber eq null) {
                  mapIterator.remove()
                  prefetchOrNull()
                } else if (!fiber.isAlive()) {
                  mapIterator.remove()
                  prefetchOrNull()
                } else {
                  fiber
                }
              }
            }

            def hasNext: Boolean = _next ne null

            def next(): Fiber.Runtime[_, _] = {
              val result = _next
              if (result eq null) {
                throw new NoSuchElementException("No more fibers in FiberSet")
              }
              _next = prefetchOrNull()
              result
            }
          }
        }
      }
  }

  type JSet[A] = java.util.Set[A]

}
