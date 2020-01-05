/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

import java.io.IOException
import java.io.EOFException

import zio.console._
import zio._

/**
 * `TestConsole` provides a testable interface for programs interacting with
 * the console by modeling input and output as reading from and writing to
 * input and output buffers maintained by `TestConsole` and backed by a `Ref`.
 *
 * All calls to `putStr` and `putStrLn` using the `TestConsole` will write the
 * string to the output buffer and all calls to `getStrLn` will take a string
 * from the input buffer. No actual printing or reading from the console will
 * occur. `TestConsole` has several methods to access and manipulate the
 * content of these buffers including `feedLines` to feed strings to the input
 * buffer that will then be returned by calls to `getStrLn`, `output` to get
 * the content of the output buffer from calls to `putStr` and `putStrLn`, and
 * `clearInput` and `clearOutput` to clear the respective buffers.
 *
 * Together, these functions make it easy to test programs interacting with the
 * console.
 *
 * {{{
 * import zio.console._
 * import zio.test.environment.TestConsole
 * import zio.ZIO
 *
 * val sayHello = for {
 *   name <- getStrLn
 *   _    <- putStrLn("Hello, " + name + "!")
 * } yield ()
 *
 * for {
 *   _ <- TestConsole.feedLines("John", "Jane", "Sally")
 *   _ <- ZIO.collectAll(List.fill(3)(sayHello))
 *   result <- TestConsole.output
 * } yield result == Vector("Hello, John!\n", "Hello, Jane!\n", "Hello, Sally!\n")
 * }}}
 */
object TestConsole extends Serializable {

  trait Service extends Console.Service with Restorable {
    def feedLines(lines: String*): UIO[Unit]
    def output: UIO[Vector[String]]
    def clearInput: UIO[Unit]
    def clearOutput: UIO[Unit]
  }

  case class Test(consoleState: Ref[TestConsole.Data]) extends TestConsole.Service {

    /**
     * Clears the contents of the input buffer.
     */
    val clearInput: UIO[Unit] =
      consoleState.update(data => data.copy(input = List.empty)).unit

    /**
     * Clears the contents of the output buffer.
     */
    val clearOutput: UIO[Unit] =
      consoleState.update(data => data.copy(output = Vector.empty)).unit

    /**
     * Writes the specified sequence of strings to the input buffer. The
     * first string in the sequence will be the first to be taken. These
     * strings will be taken before any strings that were previously in the
     * input buffer.
     */
    def feedLines(lines: String*): UIO[Unit] =
      consoleState.update(data => data.copy(input = lines.toList ::: data.input)).unit

    /**
     * Takes the first value from the input buffer, if one exists, or else
     * fails with an `EOFException`.
     */
    val getStrLn: ZIO[Any, IOException, String] = {
      for {
        input <- consoleState.get.flatMap(
                  d =>
                    IO.fromOption(d.input.headOption)
                      .mapError(_ => new EOFException("There is no more input left to read"))
                )
        _ <- consoleState.update { data =>
              Data(data.input.tail, data.output)
            }
      } yield input
    }

    /**
     * Returns the contents of the output buffer. The first value written to
     * the output buffer will be the first in the sequence.
     */
    val output: UIO[Vector[String]] =
      consoleState.get.map(_.output)

    /**
     * Writes the specified string to the output buffer.
     */
    override def putStr(line: String): UIO[Unit] =
      consoleState.update { data =>
        Data(data.input, data.output :+ line)
      }.unit

    /**
     * Writes the specified string to the output buffer followed by a newline
     * character.
     */
    override def putStrLn(line: String): ZIO[Any, Nothing, Unit] =
      consoleState.update { data =>
        Data(data.input, data.output :+ s"$line\n")
      }.unit

    /**
     * Saves the `TestConsole`'s current state in an effect which, when run, will restore the `TestConsole`
     * state to the saved state
     */
    val save: UIO[UIO[Unit]] =
      for {
        cState <- consoleState.get
      } yield consoleState.set(cState)
  }

  /**
   * Constructs a new `Test` object that implements the `TestConsole`
   * interface. This can be useful for mixing in with implementations of other
   * interfaces.
   */
  def makeTest(data: Data): ZLayer[Has.Any, Nothing, TestConsole] =
    ZLayer.fromEffect(Ref.make(data).map(ref => Has(Test(ref))))

  /**
   * Accesses a `TestConsole` instance in the environment and writes the
   * specified sequence of strings to the input buffer.
   */
  def feedLines(lines: String*): ZIO[TestConsole, Nothing, Unit] =
    ZIO.accessM(_.get.feedLines(lines: _*))

  /**
   * Accesses a `TestConsole` instance in the environment and returns the
   * contents of the output buffer.
   */
  val output: ZIO[TestConsole, Nothing, Vector[String]] =
    ZIO.accessM(_.get.output)

  /**
   * Accesses a `TestConsole` instance in the environment and clears the input
   * buffer.
   */
  val clearInput: ZIO[TestConsole, Nothing, Unit] =
    ZIO.accessM(_.get.clearInput)

  /**
   * Accesses a `TestConsole` instance in the environment and clears the output
   * buffer.
   */
  val clearOutput: ZIO[TestConsole, Nothing, Unit] =
    ZIO.accessM(_.get.clearOutput)

  /**
   * The default initial state of the `TestConsole` with input and output
   * buffers both empty.
   */
  val DefaultData: Data = Data(Nil, Vector())

  /**
   * Accesses a `TestConsole` instance in the environment and saves the console state in an effect which, when run,
   * will restore the `TestConsole` to the saved state
   */
  val save: ZIO[TestConsole, Nothing, UIO[Unit]] = ZIO.accessM[TestConsole](_.get.save)

  /**
   * The state of the `TestConsole`.
   */
  case class Data(input: List[String] = List.empty, output: Vector[String] = Vector.empty)
}
