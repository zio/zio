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

import java.io.IOException

import zio.blocking.{ Blocking, _ }

trait ZInputStream {
  def readN(n: Int): ZIO[Blocking, Option[IOException], Chunk[Byte]]
  def skip(n: Long): ZIO[Blocking, IOException, Long]
  def readAll(bufferSize: Int): ZIO[Blocking, Option[IOException], Chunk[Byte]]
  def close(): ZIO[Blocking, Nothing, Unit]
}

object ZInputStream {

  def fromInputStream(is: java.io.InputStream): ZInputStream =
    new ZInputStream {

      def readN(n: Int): ZIO[Blocking, Option[IOException], Chunk[Byte]] =
        (for {
          bc <- effectBlocking {
                 val b: Array[Byte] = new Array[Byte](n)
                 val code           = is.read(b)
                 (b, code)
               }.refineToOrDie[IOException]
          r <- bc match {
                case (buf, rcode) =>
                  rcode match {
                    case -1 => ZIO.fail(None)
                    case _  => ZIO.succeed(Chunk.fromArray(buf))
                  }
              }
        } yield r).mapError {
          case e: IOException => Some(e)
        }

      def skip(n: Long): ZIO[Blocking, IOException, Long] =
        effectBlocking(is.skip(n)).refineToOrDie[IOException]

      def readAll(bufferSize: Int): ZIO[Blocking, Option[IOException], Chunk[Byte]] =
        (for {
          bdr <- effectBlocking {
                  val buffer = new java.io.ByteArrayOutputStream();
                  val data   = new Array[Byte](bufferSize);
                  val nRead  = is.read(data, 0, data.length)
                  (buffer, data, nRead)
                }.refineToOrDie[IOException]
          r <- bdr match {
                case (buf, data, rcode) =>
                  rcode match {
                    case -1 => ZIO.fail(None)
                    case _ =>
                      effectBlocking {
                        var nRead = rcode
                        while (nRead != -1) {
                          buf.write(data, 0, nRead);
                          nRead = is.read(data, 0, data.length)
                        }
                        buf.flush()
                        Chunk.fromArray(buf.toByteArray())
                      }
                  }
              }
        } yield r).mapError {
          case e: IOException => Some(e)
        }

      def close(): ZIO[Blocking, Nothing, Unit] =
        effectBlocking(is.close()).orDie
    }
}
