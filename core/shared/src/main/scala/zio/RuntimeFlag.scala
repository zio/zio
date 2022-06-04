/*
 * Copyright 2021-2022 John A. De Goes and the ZIO Contributors
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

sealed trait RuntimeFlag {
  def index: Int

  def mask: Int

  def notMask: Int
}

object RuntimeFlag {
  lazy val all: Set[RuntimeFlag] =
    Set(Interruption, CurrentFiber, OpLog, OpSupervision, RuntimeMetrics, FiberRoots)

  case object Interruption extends RuntimeFlag {
    final val index   = 0
    final val mask    = 1 << index
    final val notMask = ~mask
  }
  case object CurrentFiber extends RuntimeFlag {
    final val index   = 1
    final val mask    = 1 << index
    final val notMask = ~mask
  }
  case object OpLog extends RuntimeFlag {
    final val index   = 2
    final val mask    = 1 << index
    final val notMask = ~mask
  }
  case object OpSupervision extends RuntimeFlag {
    final val index   = 3
    final val mask    = 1 << index
    final val notMask = ~mask
  }
  case object RuntimeMetrics extends RuntimeFlag {
    final val index   = 4
    final val mask    = 1 << index
    final val notMask = ~mask
  }
  case object FiberRoots extends RuntimeFlag {
    final val index   = 5
    final val mask    = 1 << index
    final val notMask = ~mask
  }
}
