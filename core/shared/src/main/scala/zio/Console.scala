/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

import java.io.{EOFException, IOException, PrintStream}
import scala.io.StdIn
import scala.{Console => SConsole}

trait Console extends Serializable {
  def print(line: String): UIO[Unit]

  def printError(line: String): UIO[Unit]

  def printLine(line: String): UIO[Unit]

  def printLineError(line: String): UIO[Unit]

  def readLine: IO[IOException, String]

  @deprecated("use `print`", "2.0.0")
  def putStr(line: String): UIO[Unit] = print(line)

  @deprecated("use `printError`", "2.0.0")
  def putStrErr(line: String): UIO[Unit] = printError(line)

  @deprecated("use `printLine`", "2.0.0")
  def putStrLn(line: String): UIO[Unit] = printLine(line)

  @deprecated("use `printLineError`", "2.0.0")
  def putStrLnErr(line: String): UIO[Unit] = printLineError(line)

  @deprecated("use `readLine`", "2.0.0")
  def getStrLn: IO[IOException, String] = readLine
}

object Console extends Serializable {

  // Layer Definitions

  val any: ZLayer[Has[Console], Nothing, Has[Console]] =
    ZLayer.requires[Has[Console]]

  val live: Layer[Nothing, Has[Console]] =
    ZLayer.succeed(ConsoleLive)

  object ConsoleLive extends Console {

    def print(line: String): UIO[Unit] = putStr(SConsole.out)(line)

    def printError(line: String): UIO[Unit] = putStr(SConsole.err)(line)

    def printLine(line: String): UIO[Unit] = putStrLn(SConsole.out)(line)

    def printLineError(line: String): UIO[Unit] = putStrLn(SConsole.err)(line)

    def readLine: IO[IOException, String] =
      IO.effect {
        val line = StdIn.readLine()

        if (line ne null) line
        else throw new EOFException("There is no more input left to read")
      }.refineToOrDie[IOException]

    private def putStr(stream: PrintStream)(line: String): UIO[Unit] =
      IO.effectTotal(SConsole.withOut(stream)(SConsole.print(line)))

    private def putStrLn(stream: PrintStream)(line: String): UIO[Unit] =
      IO.effectTotal(SConsole.withOut(stream)(SConsole.println(line)))
  }

  // Accessor Methods

  /**
   * Prints text to the console.
   */
  def print(line: => String): URIO[Has[Console], Unit] =
    ZIO.serviceWith(_.print(line))

  /**
   * Prints text to the standard error console.
   */
  def printError(line: => String): URIO[Has[Console], Unit] =
    ZIO.serviceWith(_.printError(line))

  /**
   * Prints a line of text to the console, including a newline character.
   */
  def printLine(line: => String): URIO[Has[Console], Unit] =
    ZIO.serviceWith(_.printLine(line))

  /**
   * Prints a line of text to the standard error console, including a newline character.
   */
  def printLineError(line: => String): URIO[Has[Console], Unit] =
    ZIO.serviceWith(_.printLineError(line))

  /**
   * Retrieves a line of input from the console.
   * Fails with an [[java.io.EOFException]] when the underlying [[java.io.Reader]]
   * returns null.
   */
  val readLine: ZIO[Has[Console], IOException, String] =
    ZIO.accessM(_.get.readLine)

  /**
   * Prints text to the console.
   */
  @deprecated("use `print`", "2.0.0")
  def putStr(line: => String): URIO[Has[Console], Unit] =
    print(line)

  /**
   * Prints text to the standard error console.
   */
  @deprecated("use `printError`", "2.0.0")
  def putStrErr(line: => String): URIO[Has[Console], Unit] =
    printError(line)

  /**
   * Prints a line of text to the console, including a newline character.
   */
  @deprecated("use `printLine`", "2.0.0")
  def putStrLn(line: => String): URIO[Has[Console], Unit] =
    printLine(line)

  /**
   * Prints a line of text to the standard error console, including a newline character.
   */
  @deprecated("use `printLineError`", "2.0.0")
  def putStrLnErr(line: => String): URIO[Has[Console], Unit] =
    printLineError(line)

  /**
   * Retrieves a line of input from the console.
   * Fails with an [[java.io.EOFException]] when the underlying [[java.io.Reader]]
   * returns null.
   */
  @deprecated("use `readLine`", "2.0.0")
  val getStrLn: ZIO[Has[Console], IOException, String] =
    readLine
}
