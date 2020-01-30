/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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

package zio.test

/**
 * A `Label[L]` represents a label with a value of type `L` and a string
 * rendering.
 */
final case class Label[+L](value: L, render: String) { self =>

  /**
   * Transforms the value of this label with the specified function.
   */
  def map[L1](f: L => L1): Label[L1] =
    self.copy(value = f(value))

  /**
   * Transforms the string rendering of this label with the specified
   * function.
   */
  def mapRender(f: String => String): Label[L] =
    self.copy(render = f(render))
}

object Label {

  /**
   * Constructs a new label from the specified string.
   */
  def fromString(string: String): Label[Unit] =
    Label((), string)
}
