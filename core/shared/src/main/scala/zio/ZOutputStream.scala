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
 * A sink of bytes whose writes are ZIO effects. On the JVM and Scala Native,
 * `ZIO.writeFileOutputStream` and similar constructors return one, and
 * `ZOutputStream.fromOutputStream` wraps a `java.io.OutputStream`.
 */
sealed trait ZOutputStream {
  /**
   * Writes the bytes of `chunk` to the stream, on the blocking executor. Fails
   * with the `IOException` that the stream throws; other exceptions are
   * defects.
   */
  def write(chunk: Chunk[Byte])(implicit trace: Trace): IO[IOException, Unit]
}

/**
 * Constructors of [[ZOutputStream]] values.
 */
object ZOutputStream {

  /**
   * Wraps the Java output stream `os`. Writes are not flushed, and closing
   * `os` stays the caller's job.
   */
  def fromOutputStream(os: java.io.OutputStream): ZOutputStream = new ZOutputStream {
    def write(chunk: Chunk[Byte])(implicit trace: Trace): IO[IOException, Unit] =
      ZIO.attemptBlockingIO {
        os.write(chunk.toArray)
      }
  }

}
