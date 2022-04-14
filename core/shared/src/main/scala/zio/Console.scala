/*
 * Copyright 2017-2022 John A. De Goes and the ZIO Contributors
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

import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.io.{EOFException, IOException, PrintStream}
import scala.io.StdIn
import scala.{Console => SConsole}

trait Console extends Serializable {
  def print(line: => Any)(implicit trace: ZTraceElement): IO[IOException, Unit]

  def printError(line: => Any)(implicit trace: ZTraceElement): IO[IOException, Unit]

  def printLine(line: => Any)(implicit trace: ZTraceElement): IO[IOException, Unit]

  def printLineError(line: => Any)(implicit trace: ZTraceElement): IO[IOException, Unit]

  def readLine(implicit trace: ZTraceElement): IO[IOException, String]

  private[zio] def unsafePrint(line: Any): Unit =
    Runtime.default.unsafeRun(print(line)(ZTraceElement.empty))(ZTraceElement.empty)

  private[zio] def unsafePrintError(line: Any): Unit =
    Runtime.default.unsafeRun(printError(line)(ZTraceElement.empty))(ZTraceElement.empty)

  private[zio] def unsafePrintLine(line: Any): Unit =
    Runtime.default.unsafeRun(printLine(line)(ZTraceElement.empty))(ZTraceElement.empty)

  private[zio] def unsafePrintLineError(line: Any): Unit =
    Runtime.default.unsafeRun(printLineError(line)(ZTraceElement.empty))(ZTraceElement.empty)

  private[zio] def unsafeReadLine(): String =
    Runtime.default.unsafeRun(readLine(ZTraceElement.empty))(ZTraceElement.empty)
}

object Console extends Serializable {

  val any: ZLayer[Console, Nothing, Console] =
    ZLayer.service[Console](Tag[Console], Tracer.newTrace)

  val live: Layer[Nothing, Console] =
    ZLayer.succeed[Console](ConsoleLive)(Tag[Console], Tracer.newTrace)

  object ConsoleLive extends Console {

    def print(line: => Any)(implicit trace: ZTraceElement): IO[IOException, Unit] =
      ZIO.attemptBlockingIO(unsafePrint(line))

    def printError(line: => Any)(implicit trace: ZTraceElement): IO[IOException, Unit] =
      ZIO.attemptBlockingIO(unsafePrintError(line))

    def printLine(line: => Any)(implicit trace: ZTraceElement): IO[IOException, Unit] =
      ZIO.attemptBlockingIO(unsafePrintLine(line))

    def printLineError(line: => Any)(implicit trace: ZTraceElement): IO[IOException, Unit] =
      ZIO.attemptBlockingIO(unsafePrintLineError(line))

    def readLine(implicit trace: ZTraceElement): IO[IOException, String] =
      ZIO.attemptBlockingInterrupt(unsafeReadLine()).refineToOrDie[IOException]

    override private[zio] def unsafePrint(line: Any): Unit =
      print(SConsole.out)(line)

    override private[zio] def unsafePrintError(line: Any): Unit =
      print(SConsole.err)(line)

    override private[zio] def unsafePrintLine(line: Any): Unit =
      printLine(SConsole.out)(line)

    override private[zio] def unsafePrintLineError(line: Any): Unit =
      printLine(SConsole.err)(line)

    override private[zio] def unsafeReadLine(): String = {
      val line = StdIn.readLine()

      if (line ne null) line
      else throw new EOFException("There is no more input left to read")
    }

    private def print(stream: => PrintStream)(line: => Any): Unit =
      SConsole.withOut(stream)(SConsole.print(line))

    private def printLine(stream: => PrintStream)(line: => Any): Unit =
      SConsole.withOut(stream)(SConsole.println(line))
  }

  /**
   * Prints text to the console.
   */
  def print(line: => Any)(implicit trace: ZTraceElement): IO[IOException, Unit] =
    ZIO.consoleWith(_.print(line))

  /**
   * Prints text to the standard error console.
   */
  def printError(line: => Any)(implicit trace: ZTraceElement): IO[IOException, Unit] =
    ZIO.consoleWith(_.printError(line))

  /**
   * Prints a line of text to the console, including a newline character.
   */
  def printLine(line: => Any)(implicit trace: ZTraceElement): IO[IOException, Unit] =
    ZIO.consoleWith(_.printLine(line))

  /**
   * Prints a line of text to the standard error console, including a newline
   * character.
   */
  def printLineError(line: => Any)(implicit trace: ZTraceElement): IO[IOException, Unit] =
    ZIO.consoleWith(_.printLineError(line))

  /**
   * Retrieves a line of input from the console. Fails with an
   * [[java.io.EOFException]] when the underlying [[java.io.Reader]] returns
   * null.
   */
  def readLine(implicit trace: ZTraceElement): IO[IOException, String] =
    ZIO.consoleWith(_.readLine)
}
