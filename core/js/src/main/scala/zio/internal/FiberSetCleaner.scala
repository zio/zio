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

/**
 * No-op cleaner for Scala.js since ReferenceQueue is not available.
 */
private object FiberSetCleaner {
  def start[A <: AnyRef](
    fiberSet: FiberSet[A],
    refQueue: ReferenceQueue[A],
    every: Duration
  ): Unit = ()
}
