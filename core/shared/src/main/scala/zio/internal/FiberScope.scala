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
  private[zio] def unsafeAdd(runtimeConfig: RuntimeConfig, child: FiberContext[_, _])(implicit
    trace: ZTraceElement
  ): Boolean

  /**
   * Adds the specified child fiber to the scope, returning `true` if the scope
   * is still open, and `false` if it has been closed already.
   */
  private[zio] def unsafeAdd(enableFiberRoots: Boolean, child: zio2.RuntimeFiber[_, _])(implicit
    trace: ZTraceElement
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

    private[zio] def unsafeAdd(runtimeConfig: RuntimeConfig, child: FiberContext[_, _])(implicit
      trace: ZTraceElement
    ): Boolean = {
      if (runtimeConfig.flags.isEnabled(RuntimeConfigFlag.EnableFiberRoots)) {
        val childRef = Fiber._roots.add(child)

        child.unsafeOnDone(_ => childRef.clear())
      }

      true
    }

    private[zio] def unsafeAdd(enableFiberRoots: Boolean, child: zio2.RuntimeFiber[_, _])(implicit
      trace: ZTraceElement
    ): Boolean = {
      if (enableFiberRoots) {
        // FIXME: Add the child to the roots when the types are fixed!!!
        // val childRef = Fiber._roots.add(child)
        // child.unsafeOnDone(_ => childRef.clear())
      }

      true
    }
  }

  private final class Local(val fiberId: FiberId, parentRef: WeakReference[AnyRef]) extends FiberScope {
    private[zio] def unsafeAdd(runtimeConfig: RuntimeConfig, child: FiberContext[_, _])(implicit
      trace: ZTraceElement
    ): Boolean = {
      val parent = parentRef.get()

      (parent ne null) && parent.asInstanceOf[FiberContext[_, _]].unsafeAddChild(child)
    }

    private[zio] def unsafeAdd(enableFiberRoots: Boolean, child: zio2.RuntimeFiber[_, _])(implicit
      trace: ZTraceElement
    ): Boolean = {
      val parent = parentRef.get()

      // FIXME: Only return true if the parent is not yet finished
      (parent ne null) && { parent.asInstanceOf[zio2.RuntimeFiber[_, _]].unsafeAddChild(child); true }
    }
  }

  // FIXME: Turn type of `fiber` to `RuntimeFiber`
  private[zio] def unsafeMake(fiber: { def fiberId : FiberId }): FiberScope =
    new Local(fiber.fiberId, new WeakReference(fiber))
}
