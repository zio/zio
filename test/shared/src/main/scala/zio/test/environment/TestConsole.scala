/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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

package zio.test.environment

import zio.{Console, FiberRef, Has, IO, Ref, UIO, URIO, ZIO, ZLayer}

import java.io.{EOFException, IOException}

/**
 * `TestConsole` provides a testable interface for programs interacting with
 * the console by modeling input and output as reading from and writing to
 * input and output buffers maintained by `TestConsole` and backed by a
 * `Ref`.
 *
 * All calls to `print` and `printLine` using the `TestConsole` will write
 * the string to the output buffer and all calls to `readLine` will take a
 * string from the input buffer. To facilitate debugging, by default output
 * will also be rendered to standard output. You can enable or disable this
 * for a scope using `debug`, `silent`, or the corresponding test aspects.
 *
 * `TestConsole` has several methods to access and manipulate the content of
 * these buffers including `feedLines` to feed strings to the input  buffer
 * that will then be returned by calls to `readLine`, `output` to get the
 * content of the output buffer from calls to `print` and `printLine`, and
 * `clearInput` and `clearOutput` to clear the respective buffers.
 *
 * Together, these functions make it easy to test programs interacting with
 * the console.
 *
 * {{{
 * import zio.Console._
 * import zio.test.environment.TestConsole
 * import zio.ZIO
 *
 * val sayHello = for {
 *   name <- readLine
 *   _    <- printLine("Hello, " + name + "!")
 * } yield ()
 *
 * for {
 *   _ <- TestConsole.feedLines("John", "Jane", "Sally")
 *   _ <- ZIO.collectAll(List.fill(3)(sayHello))
 *   result <- TestConsole.output
 * } yield result == Vector("Hello, John!\n", "Hello, Jane!\n", "Hello, Sally!\n")
 * }}}
 */
trait TestConsole extends Restorable {
  def clearInput: UIO[Unit]
  def clearOutput: UIO[Unit]
  def debug[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A]
  def feedLines(lines: String*): UIO[Unit]
  def output: UIO[Vector[String]]
  def outputErr: UIO[Vector[String]]
  def silent[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A]
}

object TestConsole extends Serializable {

  case class Test(
    consoleState: Ref[TestConsole.Data],
    live: Live,
    debugState: FiberRef[Boolean]
  ) extends Console
      with TestConsole {

    /**
     * Clears the contents of the input buffer.
     */
    val clearInput: UIO[Unit] =
      consoleState.update(data => data.copy(input = List.empty))

    /**
     * Clears the contents of the output buffer.
     */
    val clearOutput: UIO[Unit] =
      consoleState.update(data => data.copy(output = Vector.empty))

    /**
     * Runs the specified effect with the `TestConsole` set to debug mode,
     * so that console output is rendered to standard output in addition to
     * being written to the output buffer.
     */
    def debug[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
      debugState.locally(true)(zio)

    /**
     * Writes the specified sequence of strings to the input buffer. The
     * first string in the sequence will be the first to be taken. These
     * strings will be taken before any strings that were previously in the
     * input buffer.
     */
    def feedLines(lines: String*): UIO[Unit] =
      consoleState.update(data => data.copy(input = lines.toList ::: data.input))

    /**
     * Takes the first value from the input buffer, if one exists, or else
     * fails with an `EOFException`.
     */
    val readLine: IO[IOException, String] = {
      for {
        input <- consoleState.get.flatMap(d =>
                   ZIO
                     .fromOption(d.input.headOption)
                     .orElseFail(new EOFException("There is no more input left to read"))
                 )
        _ <- consoleState.update(data => Data(data.input.tail, data.output, data.errOutput))
      } yield input
    }

    /**
     * Returns the contents of the output buffer. The first value written to
     * the output buffer will be the first in the sequence.
     */
    val output: UIO[Vector[String]] =
      consoleState.get.map(_.output)

    /**
     * Returns the contents of the error output buffer. The first value written to
     * the error output buffer will be the first in the sequence.
     */
    val outputErr: UIO[Vector[String]] =
      consoleState.get.map(_.errOutput)

    /**
     * Writes the specified string to the output buffer.
     */
    override def print(line: String): UIO[Unit] =
      consoleState.update { data =>
        Data(data.input, data.output :+ line, data.errOutput)
      } *> live.provide(Console.print(line)).whenM(debugState.get)

    /**
     * Writes the specified string to the error buffer.
     */
    override def printError(line: String): UIO[Unit] =
      consoleState.update { data =>
        Data(data.input, data.output, data.errOutput :+ line)
      } *> live.provide(Console.printError(line)).whenM(debugState.get)

    /**
     * Writes the specified string to the output buffer followed by a newline
     * character.
     */
    override def printLine(line: String): UIO[Unit] =
      consoleState.update { data =>
        Data(data.input, data.output :+ s"$line\n", data.errOutput)
      } *> live.provide(Console.printLine(line)).whenM(debugState.get)

    /**
     * Writes the specified string to the error buffer followed by a newline
     * character.
     */
    override def printLineError(line: String): UIO[Unit] =
      consoleState.update { data =>
        Data(data.input, data.output, data.errOutput :+ s"$line\n")
      } *> live.provide(Console.printLineError(line)).whenM(debugState.get)

    /**
     * Saves the `TestConsole`'s current state in an effect which, when run,
     * will restore the `TestConsole` state to the saved state.
     */
    val save: UIO[UIO[Unit]] =
      for {
        consoleData <- consoleState.get
      } yield consoleState.set(consoleData)

    /**
     * Runs the specified effect with the `TestConsole` set to silent mode,
     * so that console output is only written to the output buffer and not
     * rendered to standard output.
     */
    def silent[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
      debugState.locally(false)(zio)
  }

  /**
   * Constructs a new `Test` object that implements the `TestConsole`
   * interface. This can be useful for mixing in with implementations of other
   * interfaces.
   */
  def make(data: Data, debug: Boolean = true): ZLayer[Has[Live], Nothing, Has[Console] with Has[TestConsole]] = {
    for {
      live     <- ZIO.service[Live]
      ref      <- Ref.make(data)
      debugRef <- FiberRef.make(debug)
      test      = Test(ref, live, debugRef)
    } yield Has.allOf[Console, TestConsole](test, test)
  }.toLayerMany

  val any: ZLayer[Has[Console] with Has[TestConsole], Nothing, Has[Console] with Has[TestConsole]] =
    ZLayer.requires[Has[Console] with Has[TestConsole]]

  val debug: ZLayer[Has[Live], Nothing, Has[Console] with Has[TestConsole]] =
    make(Data(Nil, Vector()), true)

  val silent: ZLayer[Has[Live], Nothing, Has[Console] with Has[TestConsole]] =
    make(Data(Nil, Vector()), false)

  /**
   * Accesses a `TestConsole` instance in the environment and clears the input
   * buffer.
   */
  val clearInput: URIO[Has[TestConsole], Unit] =
    ZIO.accessM(_.get.clearInput)

  /**
   * Accesses a `TestConsole` instance in the environment and clears the output
   * buffer.
   */
  val clearOutput: URIO[Has[TestConsole], Unit] =
    ZIO.accessM(_.get.clearOutput)

  /**
   * Accesses a `TestConsole` instance in the environment and runs the
   * specified effect with the `TestConsole` set to debug mode, so that
   * console output is rendered to standard output in addition to being
   * written to the output buffer.
   */
  def debug[R <: Has[TestConsole], E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.accessM(_.get.debug(zio))

  /**
   * Accesses a `TestConsole` instance in the environment and writes the
   * specified sequence of strings to the input buffer.
   */
  def feedLines(lines: String*): URIO[Has[TestConsole], Unit] =
    ZIO.accessM(_.get.feedLines(lines: _*))

  /**
   * Accesses a `TestConsole` instance in the environment and returns the
   * contents of the output buffer.
   */
  val output: ZIO[Has[TestConsole], Nothing, Vector[String]] =
    ZIO.accessM(_.get.output)

  /**
   * Accesses a `TestConsole` instance in the environment and returns the
   * contents of the error buffer.
   */
  val outputErr: ZIO[Has[TestConsole], Nothing, Vector[String]] =
    ZIO.accessM(_.get.outputErr)

  /**
   * Accesses a `TestConsole` instance in the environment and saves the
   * console state in an effect which, when run, will restore the
   * `TestConsole` to the saved state.
   */
  val save: ZIO[Has[TestConsole], Nothing, UIO[Unit]] =
    ZIO.accessM(_.get.save)

  /**
   * Accesses a `TestConsole` instance in the environment and runs the
   * specified effect with the `TestConsole` set to silent mode, so that
   * console output is only written to the output buffer and not rendered to
   * standard output.
   */
  def silent[R <: Has[TestConsole], E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.accessM(_.get.silent(zio))

  /**
   * The state of the `TestConsole`.
   */
  final case class Data(
    input: List[String] = List.empty,
    output: Vector[String] = Vector.empty,
    errOutput: Vector[String] = Vector.empty
  )
}
