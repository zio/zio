/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

package zio.test.mock

trait Method[+A, -B] {

  /**
   * Render method fully qualified name.
   */
  override def toString: String = {
    val fragments = getClass.getName.replaceAll("\\$", ".").split("\\.")
    fragments.toList.splitAt(fragments.size - 3) match {
      case (namespace, module :: service :: method :: Nil) =>
        val capability = s"${method.head.toLower}${method.tail}"
        s"""${namespace.mkString(".")}.$module.$service.${capability}"""
      case _ => fragments.mkString(".")
    }
  }
}
