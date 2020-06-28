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

private[zio] trait ZManagedPlatformSpecific {

  def readFile(path: Path): ZManaged[Blocking, IOException, ZInputStream] =
    readFile(path.toString())

  def readFile(path: String): ZManaged[Blocking, IOException, ZInputStream] =
    ZManaged.make(
      effectBlocking(ZInputStream.fromInputStream(new io.FileInputStream(path)))
        .refineToOrDie[IOException]
    )(_.close())

  def readURL(url: URL): ZManaged[Blocking, IOException, ZInputStream] =
    ZManaged.make(
      effectBlocking(ZInputStream.fromInputStream(url.openStream()))
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
      effectBlocking(ZOutputStream.fromOutputStream(new io.FileOutputStream(path)))
        .refineToOrDie[IOException]
    )(_.close())

  def writeFile(path: Path): ZManaged[Blocking, IOException, ZOutputStream] =
    writeFile(path.toString())

}
