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

import java.util.{AbstractSet, Set}

/**
 * A `ZScope` represents the scope of a fiber lifetime. The scope of a fiber can
 * be retrieved using [[ZIO.descriptor]], and when forking fibers, you can
 * specify a custom scope to fork them on by using the [[ZIO#forkIn]].
 */
class ZScope(private[zio] val weakSet: Set[FiberContext[_, _]]) {
  private[zio] def unsafeAdd(fiber: FiberContext[_, _]): Unit = weakSet.add(fiber)
}
object ZScope {

  /**
   * The global scope. Anything forked onto the global scope is not supervised,
   * and will only terminate on its own accord (never from interruption of a
   * parent fiber, because there is no parent fiber).
   */
  val global = new ZScope(new AbstractSet[FiberContext[_, _]] {
    override def add(v: FiberContext[_, _]): Boolean       = false
    def iterator(): java.util.Iterator[FiberContext[_, _]] = java.util.Collections.emptyIterator[FiberContext[_, _]]()
    def size(): Int                                        = 0
  })

  private[zio] def unsafeMake(): ZScope =
    new ZScope(Platform.newWeakSet[FiberContext[_, _]]())
}
