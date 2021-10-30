/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
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

package zio.profiling

import scala.reflect.ClassTag

private final class VolatileArray[A: ClassTag](val length: Int) {

  private[this] val array = new Array[A](length)

  @volatile
  private[this] var marker = 0;

  def apply(i: Int): A           = { marker; array(i) }
  def update(i: Int, x: A): Unit = { array(i) = x; marker = 0 }
  def toList: List[A]            = { marker; array.toList }
}
