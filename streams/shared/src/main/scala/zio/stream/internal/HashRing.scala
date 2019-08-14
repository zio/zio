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

import HashRing.{ HashFunction, NodeHashFunction }

/**
 * Consistent hash ring.
 */
final case class HashRing[T, A](
  ring: SortedMap[Long, T],
  elementHash: HashFunction[A],
  nodeHash: NodeHashFunction[T],
  replicas: Int = 3
) { self =>

  def isEmpty: Boolean =
    ring.isEmpty

  def nodeForKey(element: A): Option[T] = {
    val h = elementHash(element)
    ring.get(h) orElse nextClockwise(h)
  }

  def nodeReplicas(node: T): List[(T, Int)] =
    (1 to replicas).map((node, _)).toList

  def removeNode(node: T): HashRing[T, A] = {
    val newRing = nodeReplicas(node).foldLeft(ring) {
      case (ring, node) =>
        ring - nodeHash(node)
    }
    self.copy(ring = newRing)
  }

  def addNode(node: T): HashRing[T, A] =
    addNodes(node)

  def addNodes(nodes: T*): HashRing[T, A] = {
    val newRing = nodes.flatMap(nodeReplicas).foldLeft(ring) {
      case (ring, node) =>
        ring + (nodeHash(node) -> node._1)
    }
    self.copy(ring = newRing)
  }

  private[this] def nextClockwise(key: Long): Option[T] = {
    val node = ring.rangeImpl(Some(key), None).headOption orElse ring.headOption
    node.map(_._2)
  }
}

object HashRing {

  type HashFunction[A]     = A => Long
  type NodeHashFunction[T] = HashFunction[(T, Int)]

  def empty[T, A](
    elementHash0: HashFunction[A],
    nodeHash0: NodeHashFunction[T],
    replicas: Int
  ): HashRing[T, A] =
    HashRing[T, A](TreeMap.empty, elementHash0, nodeHash0, replicas)

  def fromNodes[T, A](
    nodes: List[T],
    elementHash0: HashFunction[A],
    nodeHash0: NodeHashFunction[T],
    replicas: Int
  ): HashRing[T, A] =
    HashRing.empty(elementHash0, nodeHash0, replicas).addNodes(nodes: _*)

  def crc32: HashFunction[Seq[Byte]] = { bytes =>
      import java.util.zip.CRC32
      val checksum = new CRC32
      checksum.update(bytes.toArray)
      checksum.getValue
  }
}
