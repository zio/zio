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

package zio.test

import scala.collection.mutable.Map

private[test] final case class ConcurrentHashMap[K, V] private (private val map: Map[K, V]) {
  def foldLeft[B](z: B)(f: (B, (K, V)) => B): B =
    map.foldLeft(z)(f)
  def getOrElseUpdate(key: K, op: => V): V =
    map.getOrElseUpdate(key, op)
}

private[test] object ConcurrentHashMap {
  def empty[K, V]: ConcurrentHashMap[K, V] =
    new ConcurrentHashMap[K, V](Map.empty[K, V])
}
