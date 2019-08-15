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

object hash {

  /**
   * Creates a hashfunction by mapping each element to an int and mapping every
   * int to a random long value. This is for example useful for creating a hashfunction
   * from an incrementing index.
   *
   * The values will be determinstically mappend, but can be controlled
   * by providing a seed.
   *
   * All negative values will have the same hash as 0.
   */
  def fromIndex[A](f: A => Int, seed: Int): HashFunction[A] =
    IndexHash(f, seed)

  def crc32: HashFunction[Seq[Byte]] = { bytes =>
    import java.util.zip.CRC32
    val checksum = new CRC32
    checksum.update(bytes.toArray)
    checksum.getValue
  }

}
