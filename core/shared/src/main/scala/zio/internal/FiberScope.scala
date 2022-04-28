/*
 * Copyright 2020-2022 John A. De Goes and the ZIO Contributors
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
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.lang.ref.WeakReference

/**
 * A `FiberScope` represents the scope of a fiber lifetime.
 */
private[zio] sealed trait FiberScope {
  def fiberId: FiberId

  /**
   * Adds the specified child fiber to the scope, returning `true` if the scope
   * is still open, and `false` if it has been closed already.
   */
  private[zio] def unsafeAdd(enableFiberRoots: Boolean, child: FiberContext[_, _])(implicit
    trace: Trace
  ): Boolean
}

private[zio] object FiberScope {

  /**
   * The global scope. Anything forked onto the global scope is not supervised,
   * and will only terminate on its own accord (never from interruption of a
   * parent fiber, because there is no parent fiber).
   */
  object global extends FiberScope {
    def fiberId: FiberId = FiberId.None

    private[zio] def unsafeAdd(enableFiberRoots: Boolean, child: FiberContext[_, _])(implicit
      trace: Trace
    ): Boolean = {
      if (enableFiberRoots) {
        val childRef = Fiber._roots.add(child)

        child.unsafeOnDone((_, _) => childRef.clear())
      }

      true
    }
  }

  private final class Local(val fiberId: FiberId, parentRef: WeakReference[FiberContext[_, _]]) extends FiberScope {
    private[zio] def unsafeAdd(enableFiberRoots: Boolean, child: FiberContext[_, _])(implicit
      trace: Trace
    ): Boolean = {
      val parent = parentRef.get()

      (parent ne null) && parent.unsafeAddChild(child)
    }
  }

  private[zio] def unsafeMake(fiber: FiberContext[_, _]): FiberScope =
    new Local(fiber.fiberId, new WeakReference(fiber))
}
