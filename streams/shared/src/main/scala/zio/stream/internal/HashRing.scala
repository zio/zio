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
package zio.stream.internal

import scala.collection.SortedMap
import scala.collection.immutable.TreeMap

/**
 * Consistent hash ring.
 */
trait HashRing[T, A] { self =>
  import HashRing.HashFunction

  def ring: SortedMap[Long, T]
  def nodeHash: HashFunction[T]
  def elementHash: HashFunction[A]

  def isEmpty: Boolean =
    ring.isEmpty

  def nodeForKey(element: A): Option[T] = {
    val h = elementHash(element)
    ring.get(h) orElse nextClockwise(h)
  }

  def removeNode(node: T): HashRing[T, A] = {
    val h = nodeHash(node)
    if (!ring.contains(h)) self
    else {
      new HashRing[T, A] {
        val ring = self.ring - h
        val nodeHash = self.nodeHash
        val elementHash = self.elementHash
      }
    }
  }

  def addNode(node: T): HashRing[T, A] =
    addNodes(node)

  def addNodes(nodes: T*): HashRing[T, A] = {
    new HashRing[T, A] {
      val ring = self.ring ++ nodes.map(node => (self.nodeHash(node) -> node))
      val nodeHash = self.nodeHash
      val elementHash = self.elementHash
    }
  }

  private[this] def nextClockwise(key: Long): Option[T] = {
    val node = ring.rangeImpl(Some(key), None).headOption orElse ring.headOption
    node.map(_._2)
  }
}

object HashRing {

  type HashFunction[A] = A => Long

  def make[T, A](nodes: List[T], elementHash0: HashFunction[A], nodeHash0: HashFunction[T]): HashRing[T, A] = {
    new HashRing[T, A] {
      val ring = TreeMap(nodes.map(n => (nodeHash0(n) -> n)): _*)
      val nodeHash = nodeHash0
      val elementHash = elementHash0
    }
  }

}
