/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

package zio.mock

import zio.{Console, IO, URLayer, ZIO, ZTraceElement}
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.io.IOException

object MockConsole extends Mock[Console] {

  object Print          extends Effect[Any, IOException, Unit]
  object PrintError     extends Effect[Any, IOException, Unit]
  object PrintLine      extends Effect[Any, IOException, Unit]
  object PrintLineError extends Effect[Any, IOException, Unit]
  object ReadLine       extends Effect[Unit, IOException, String]

  val compose: URLayer[Proxy, Console] = {
    implicit val trace = Tracer.newTrace
    ZIO
      .service[Proxy]
      .map(proxy =>
        new Console {
          def print(line: => Any)(implicit trace: ZTraceElement): IO[IOException, Unit]      = proxy(Print, line)
          def printError(line: => Any)(implicit trace: ZTraceElement): IO[IOException, Unit] = proxy(PrintError, line)
          def printLine(line: => Any)(implicit trace: ZTraceElement): IO[IOException, Unit]  = proxy(PrintLine, line)
          def printLineError(line: => Any)(implicit trace: ZTraceElement): IO[IOException, Unit] =
            proxy(PrintLineError, line)
          def readLine(implicit trace: ZTraceElement): IO[IOException, String] = proxy(ReadLine)
        }
      )
      .toLayer
  }
}
