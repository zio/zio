/*
 * Copyright 2020-2021 John A. De Goes and the ZIO Contributors
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

package zio

import zio.internal.{FiberContext, Platform, Sync}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.lang.ref.WeakReference

/**
 * A `ZScope` represents the scope of a fiber lifetime. The scope of a fiber can
 * be retrieved using [[ZIO.descriptor]], and when forking fibers, you can
 * specify a custom scope to fork them on by using the [[ZIO#forkIn]].
 */
sealed trait ZScope {
  def fiberId: FiberId
  private[zio] def unsafeAdd(child: FiberContext[_, _])(implicit trace: ZTraceElement): Boolean
}
object ZScope {

  /**
   * The global scope. Anything forked onto the global scope is not supervised,
   * and will only terminate on its own accord (never from interruption of a
   * parent fiber, because there is no parent fiber).
   */
  object global extends ZScope {
    def fiberId: FiberId = FiberId.None

    private[zio] def unsafeAdd(child: FiberContext[_, _])(implicit trace: ZTraceElement): Boolean = true
  }

  final class Local(val fiberId: FiberId, parentRef: WeakReference[FiberContext[_, _]]) extends ZScope {
    private[zio] def unsafeAdd(child: FiberContext[_, _])(implicit trace: ZTraceElement): Boolean = {
      val parent = parentRef.get()

      if (parent ne null) {
        parent.unsafeAddChild(child)
        true
      } else false
    }
  }

  private[zio] def unsafeMake(fiber: FiberContext[_, _]): ZScope = new Local(fiber.fiberId, new WeakReference(fiber))
}
