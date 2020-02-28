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
 * Dictates the supervision mode when a child fiber is forked from a parent
 * fiber. There are three possible supervision modes: `Disown`, `Interrupt`,
 * and `InterruptFork`, which determine what the parent fiber will do with the
 * child fiber when the parent fiber exits.
 */
sealed trait SuperviseMode extends Serializable with Product
object SuperviseMode {

  /**
   * The child fiber will be disowned when the parent fiber exits.
   */
  case object Disown extends SuperviseMode

  /**
   * The child fiber will be interrupted when the parent fiber exits.
   */
  case object Interrupt extends SuperviseMode

  /**
   * The child fiber will be interrupted when the parent fiber exits, but in
   * the background, without blocking the parent fiber.
   */
  case object InterruptFork extends SuperviseMode
}
