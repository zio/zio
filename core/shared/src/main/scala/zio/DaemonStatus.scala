/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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
 * The `DaemonStatus` of a fiber determines whether or not it is currently in
 * daemon mode. The status can change over time in different regions.
 */
sealed abstract class DaemonStatus extends Serializable with Product {
  final def isDaemon: Boolean    = this match { case DaemonStatus.Daemon => true; case _ => false }
  final def isNonDaemon: Boolean = !isDaemon

  private[zio] final def toBoolean: Boolean = isDaemon
}
object DaemonStatus {
  def daemon: DaemonStatus    = Daemon
  def nonDaemon: DaemonStatus = NonDaemon

  /**
   * Indicates forked children of the fiber will be marked as daemons.
   */
  case object Daemon extends DaemonStatus

  /**
   * Indicates forked children of the fiber will not be marked as daemons.
   */
  case object NonDaemon extends DaemonStatus

  private[zio] def fromBoolean(b: Boolean): DaemonStatus = if (b) Daemon else NonDaemon
}
