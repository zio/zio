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

final class BitChunk private (private val bytes: Chunk[Byte]) { self =>

  def apply(n: Int): Boolean =
    (bytes(n >> 3) & (1 << (7 - (n & 7)))) != 0

  val length: Int =
    bytes.length << 3

  def mkString: String =
    mkString(", ")

  def mkString(sep: String): String =
    mkString("BitChunk(", sep, ")")

  def mkString(start: String, sep: String, end: String): String = {
    val builder = new scala.collection.mutable.StringBuilder
    builder.append(start)
    var i = 0
    while (i < length) {
      if (i != 0) {
        builder.append(sep)
      }
      if (apply(i)) builder.append("1") else builder.append("0")
      i += 1
    }
    builder.append(end)
    builder.toString
  }

  def toBinaryString: String =
    mkString("", "", "")

  override def toString: String =
    mkString
}

object BitChunk {

  def fromByteChunk(bytes: Chunk[Byte]): BitChunk =
    new BitChunk(bytes)
}
