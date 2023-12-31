/*
 * Copyright 2017-2023 John A. De Goes and the ZIO Contributors
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

import java.io.{FileInputStream, FileOutputStream, IOException}
import java.net.{URI, URL}
import java.nio.file.Path

private[zio] trait ZIOPlatformSpecific[-R, +E, +A]

private[zio] trait ZIOCompanionPlatformSpecific {

  /**
   * Imports a synchronous effect that does blocking IO into a pure value.
   *
   * If the returned `ZIO` is interrupted, the blocked thread running the
   * synchronous effect will be interrupted via `Thread.interrupt`.
   *
   * Note that this adds significant overhead. For performance sensitive
   * applications consider using `attemptBlocking` or
   * `attemptBlockingCancelable`.
   */
  def attemptBlockingInterrupt[A](effect: => A)(implicit trace: Trace): Task[A] =
    ZIO.attemptBlocking(effect)

  def readFile(path: => Path)(implicit trace: Trace, d: DummyImplicit): ZIO[Any, IOException, String] =
    readFile(path.toString)

  def readFile(name: => String)(implicit trace: Trace): ZIO[Any, IOException, String] =
    ZIO.acquireReleaseWith(ZIO.attemptBlockingIO(scala.io.Source.fromFile(name)))(s =>
      ZIO.attemptBlocking(s.close()).orDie
    ) { s =>
      ZIO.attemptBlockingIO(s.mkString)
    }

  def readFileInputStream(
    path: => Path
  )(implicit trace: Trace, d: DummyImplicit): ZIO[Scope, IOException, ZInputStream] =
    readFileInputStream(path.toString)

  def readFileInputStream(
    name: => String
  )(implicit trace: Trace): ZIO[Scope, IOException, ZInputStream] =
    ZIO
      .acquireRelease(
        ZIO.attemptBlockingIO {
          val fis = new FileInputStream(name)
          (fis, ZInputStream.fromInputStream(fis))
        }
      )(tuple => ZIO.attemptBlocking(tuple._1.close()).orDie)
      .map(_._2)

  def readURLInputStream(
    url: => URL
  )(implicit trace: Trace, d: DummyImplicit): ZIO[Scope, IOException, ZInputStream] =
    ZIO
      .acquireRelease(
        ZIO.attemptBlockingIO {
          val fis = url.openStream()
          (fis, ZInputStream.fromInputStream(fis))
        }
      )(tuple => ZIO.attemptBlocking(tuple._1.close()).orDie)
      .map(_._2)

  def readURLInputStream(
    url: => String
  )(implicit trace: Trace): ZIO[Scope, IOException, ZInputStream] =
    ZIO.succeed(new URL(url)).flatMap(readURLInputStream(_))

  def readURIInputStream(uri: => URI)(implicit trace: Trace): ZIO[Scope, IOException, ZInputStream] =
    for {
      uri        <- ZIO.succeed(uri)
      isAbsolute <- ZIO.attemptBlockingIO(uri.isAbsolute)
      is         <- if (isAbsolute) readURLInputStream(uri.toURL) else readFileInputStream(uri.toString)
    } yield is

  def writeFile(path: => String, content: => String)(implicit trace: Trace): ZIO[Any, IOException, Unit] =
    ZIO.acquireReleaseWith(ZIO.attemptBlockingIO(new java.io.FileWriter(path)))(f =>
      ZIO.attemptBlocking(f.close()).orDie
    ) { f =>
      ZIO.attemptBlockingIO(f.write(content))
    }

  def writeFile(path: => Path, content: => String)(implicit
    trace: Trace,
    d: DummyImplicit
  ): ZIO[Any, IOException, Unit] =
    writeFile(path.toString, content)

  def writeFileOutputStream(
    path: => String
  )(implicit trace: Trace): ZIO[Scope, IOException, ZOutputStream] =
    ZIO
      .acquireRelease(
        ZIO.attemptBlockingIO {
          val fos = new FileOutputStream(path)
          (fos, ZOutputStream.fromOutputStream(fos))
        }
      )(tuple => ZIO.attemptBlocking(tuple._1.close()).orDie)
      .map(_._2)

  def writeFileOutputStream(
    path: => Path
  )(implicit trace: Trace, d: DummyImplicit): ZIO[Scope, IOException, ZOutputStream] =
    writeFileOutputStream(path.toString)
}
