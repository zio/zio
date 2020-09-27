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

package zio.internal.stacktracer

sealed abstract class ZTraceElement extends Product with Serializable {
  def prettyPrint: String
}

object ZTraceElement {

  final case class NoLocation(error: String) extends ZTraceElement {
    final def prettyPrint: String = s"<couldn't get location, error: $error>"
  }

  final case class SourceLocation(file: String, clazz: String, method: String, line: Int) extends ZTraceElement {
    final def toStackTraceElement: StackTraceElement = new StackTraceElement(clazz, method, file, line)
    final def prettyPrint: String                    = toStackTraceElement.toString
  }

}
