/*
 * Copyright 2020-2024 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.io.IOException

/**
 * A source of bytes whose reads are ZIO effects. On the JVM and Scala Native,
 * `ZIO.readFileInputStream`, `ZIO.readURLInputStream` and similar constructors
 * return one, and `ZInputStream.fromInputStream` wraps a `java.io.InputStream`.
 * The operations run on the blocking executor. `readN` and `readAll` fail with
 * `Some(e)` when the stream throws the `IOException` `e`, and `skip` fails with
 * `e`; other exceptions thrown by the stream are defects.
 */
sealed trait ZInputStream {

  /**
   * Reads up to `n` bytes with one read of the stream and returns them; the
   * chunk can be shorter than `n`. Fails with `None` at the end of the stream.
   * A negative `n` makes the effect die with a `NegativeArraySizeException`.
   */
  def readN(n: Int)(implicit trace: Trace): IO[Option[IOException], Chunk[Byte]]

  /**
   * Skips up to `n` bytes and returns the number of bytes skipped, which is 0
   * at the end of the stream.
   */
  def skip(n: Long)(implicit trace: Trace): IO[IOException, Long]

  /**
   * Reads the stream to its end, `bufferSize` bytes at a time, and returns all
   * the bytes read. Fails with `None` when the stream is already at its end.
   * With a `bufferSize` of 0, each read returns no bytes, so the effect does
   * not complete on a stream that still has data.
   */
  def readAll(bufferSize: Int)(implicit trace: Trace): IO[Option[IOException], Chunk[Byte]]
}

/**
 * Constructors of [[ZInputStream]] values.
 */
object ZInputStream {

  /**
   * Wraps the Java input stream `is`. Closing `is` stays the caller's job.
   */
  def fromInputStream(is: java.io.InputStream): ZInputStream =
    new ZInputStream {

      def readN(n: Int)(implicit trace: Trace): IO[Option[IOException], Chunk[Byte]] =
        ZIO.attemptBlockingIO {
          val b: Array[Byte] = new Array[Byte](n)
          val count          = is.read(b)
          if (count == -1) Exit.failNone else ZIO.succeed(Chunk.fromArray(b).take(count))
        }.mapError { case e: IOException =>
          Some(e)
        }.flatten

      def skip(n: Long)(implicit trace: Trace): IO[IOException, Long] =
        ZIO.attemptBlockingIO(is.skip(n))

      def readAll(bufferSize: Int)(implicit trace: Trace): IO[Option[IOException], Chunk[Byte]] =
        ZIO.attemptBlockingIO {
          val buffer = new java.io.ByteArrayOutputStream();
          val idata  = new Array[Byte](bufferSize);
          var count  = is.read(idata, 0, idata.length)

          if (count == -1) Exit.failNone
          else {
            var countTotalBytes = 0
            var data            = idata
            while (count != -1) {
              countTotalBytes = countTotalBytes + count
              buffer.write(data, 0, count);
              data = new Array[Byte](bufferSize)
              count = is.read(data, 0, data.length)
            }
            ZIO.succeed(Chunk.fromArray(buffer.toByteArray()).take(countTotalBytes))
          }
        }.mapError { case e: IOException =>
          Some(e)
        }.flatten

    }
}
