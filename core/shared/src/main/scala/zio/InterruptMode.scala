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

sealed trait InterruptMode extends Serializable with Product { self =>
  import zio.InterruptMode._

  final def fork: Boolean = self match {
    case Await => false
    case Fork  => true
  }

  final def await: Boolean = !fork
}
object InterruptMode {

  /**
   * Indicates an attempt to interrupt the fiber will resume immediately,
   * forking the interruption in a separate fiber.
   */
  case object Fork extends InterruptMode

  /**
   * Indicates an attempt to interrupt the fiber will await until the fiber
   * has been successfully interrupted.
   */
  case object Await extends InterruptMode
}
