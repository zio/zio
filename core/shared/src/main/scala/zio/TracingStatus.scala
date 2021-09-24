/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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

/**
 * Whether ZIO Tracing is enabled for the current fiber in the current region.
 */
sealed abstract class TracingStatus extends Serializable with Product {
  final def isTraced: Boolean   = this match { case TracingStatus.Traced => true; case _ => false }
  final def isUntraced: Boolean = !isTraced

  private[zio] final def toBoolean: Boolean = isTraced
}
object TracingStatus {
  def traced: TracingStatus   = Traced
  def untraced: TracingStatus = Untraced

  case object Traced   extends TracingStatus
  case object Untraced extends TracingStatus

  private[zio] def fromBoolean(b: Boolean): TracingStatus = if (b) Traced else Untraced
}
