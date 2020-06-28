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

import java.io
import java.io.IOException
import java.net.{ URI, URL }
import java.nio.file.Path

import zio.blocking.{ Blocking, _ }

private[zio] trait ZInputStream {
  def readN(n: Int): ZIO[Blocking, IOException, Option[Chunk[Byte]]]
  def skip(n: Long): ZIO[Blocking, IOException, Long]
  def readAll: ZIO[Blocking, IOException, Option[Chunk[Byte]]]
  def close(): ZIO[Blocking, Nothing, Unit]
}

private[zio] trait ZOutputStream {
  def write(chunk: Chunk[Byte]): ZIO[Blocking, IOException, Unit]
  def close(): ZIO[Blocking, Nothing, Unit]
}

/**
 * A functional wrapper over a java.io.InputStream.
 */
private[zio] case class InputStream private (private val is: java.io.InputStream) extends ZInputStream {
  def readN(n: Int): ZIO[Blocking, IOException, Option[Chunk[Byte]]] =
    effectBlocking {
      val available = is.available()
      available match {
        case 0 => None
        case _ =>
          val b: Array[Byte] = new Array[Byte](n)
          is.read(b)
          Some(Chunk.fromArray(b))
      }
    }.refineToOrDie[IOException]

  def skip(n: Long): ZIO[Blocking, IOException, Long] =
    effectBlocking(is.skip(n)).refineToOrDie[IOException]

  def readAll: ZIO[Blocking, IOException, Option[Chunk[Byte]]] =
    effectBlocking(is.available())
      .flatMap(available =>
        effectBlocking {
          available match {
            case 0 => None
            case _ => {
              val buffer = new java.io.ByteArrayOutputStream();
              val data   = new Array[Byte](4096);
              var nRead  = is.read(data, 0, data.length)
              while (nRead != -1) {
                buffer.write(data, 0, nRead);
                nRead = is.read(data, 0, data.length)
              }
              buffer.flush()
              Some(Chunk.fromArray(buffer.toByteArray()))
            }
          }
        }
      )
      .refineToOrDie[IOException]

  def close(): ZIO[Blocking, Nothing, Unit] =
    effectBlocking(is.close()).orDie
}

/**
 * A functional wrapper over a java.io.OutputStream.
 */
private[zio] case class OutputStream private (private val os: java.io.OutputStream) extends ZOutputStream {
  def write(chunk: Chunk[Byte]): ZIO[Blocking, IOException, Unit] =
    effectBlocking {
      os.write(chunk.toArray)
      os.flush()
    }.refineToOrDie[IOException]

  def close(): ZIO[Blocking, Nothing, Unit] =
    effectBlocking(os.close()).orDie
}

private[zio] trait ZManagedPlatformSpecific {

  def readFile(path: Path): ZManaged[Blocking, IOException, ZInputStream] =
    readFile(path.toString())

  def readFile(path: String): ZManaged[Blocking, IOException, ZInputStream] =
    ZManaged.make(
      effectBlocking(InputStream(new io.FileInputStream(path)))
        .refineToOrDie[IOException]
    )(_.close())

  def readURL(url: URL): ZManaged[Blocking, IOException, ZInputStream] =
    ZManaged.make(
      effectBlocking(InputStream(url.openStream()))
        .refineToOrDie[IOException]
    )(_.close())

  def readURL(url: String): ZManaged[Blocking, IOException, ZInputStream] =
    ZManaged.fromEffect(ZIO.effect(new URL(url))).orDie.flatMap(readURL)

  def readURI(uri: URI): ZManaged[Blocking, IOException, ZInputStream] =
    for {
      isAbsolute <- ZManaged.fromEffect(effectBlocking(uri.isAbsolute()).refineToOrDie[IOException])
      is         <- if (isAbsolute) readURL(uri.toURL()) else readFile(uri.toString())
    } yield is

  def writeFile(path: String): ZManaged[Blocking, IOException, ZOutputStream] =
    ZManaged.make(
      effectBlocking(OutputStream(new io.FileOutputStream(path)))
        .refineToOrDie[IOException]
    )(_.close())

  def writeFile(path: Path): ZManaged[Blocking, IOException, ZOutputStream] =
    writeFile(path.toString())

}
