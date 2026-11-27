/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

package zio.test

import zio._
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.io.{EOFException, IOException}

/**
 * `TestConsole` provides a testable interface for programs interacting with the
 * console by modeling input and output as reading from and writing to input and
 * output buffers maintained by `TestConsole` and backed by a `Ref`.
 *
 * All calls to `print` and `printLine` using the `TestConsole` will write the
 * string to the output buffer and all calls to `readLine` will take a string
 * from the input buffer; `printError` and `printLineError` write to a separate
 * error buffer. To facilitate debugging, by default output will also be written
 * to the live console (standard output, or standard error for `printError` and
 * `printLineError`). You can enable or disable this for a scope using `debug`,
 * `silent`, or the corresponding test aspects.
 *
 * `TestConsole` has several methods to access and manipulate the content of
 * these buffers including `feedLines` to feed strings to the input buffer that
 * will then be returned by calls to `readLine`, `output` to get the content of
 * the output buffer from calls to `print` and `printLine`, and `clearInput` and
 * `clearOutput` to clear the respective buffers.
 *
 * Together, these functions let a test feed input to a program that interacts
 * with the console and check what it printed.
 *
 * {{{
 * import zio.Console._
 * import zio.test.TestConsole
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
 *
 * @see
 *   [[https://zio.dev/reference/test/services/console/ TestConsole]]
 * @see
 *   [[https://zio.dev/reference/test/aspects/restoring-state-of-test-services/ Restoring State of Test Services]]
 */
trait TestConsole extends Console with Restorable {

  /** Clears the contents of the input buffer. */
  def clearInput(implicit trace: Trace): UIO[Unit]

  /** Clears the contents of the output buffer; the error buffer is kept. */
  def clearOutput(implicit trace: Trace): UIO[Unit]

  /** Clears the contents of the error output buffer. */
  def clearOutputErr(implicit trace: Trace): UIO[Unit]

  /**
   * Runs `zio` with the `TestConsole` in debug mode, so that console output is
   * written to the live console as well as to the buffers.
   */
  def debug[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]

  /**
   * Writes `lines` to the front of the input buffer, so that `readLine` returns
   * them in order before any lines that were already there.
   */
  def feedLines(lines: String*)(implicit trace: Trace): UIO[Unit]

  /**
   * Returns the contents of the output buffer, written by `print` and
   * `printLine`, oldest first.
   */
  def output(implicit trace: Trace): UIO[Vector[String]]

  /**
   * Returns the contents of the error output buffer, written by `printError`
   * and `printLineError`, oldest first.
   */
  def outputErr(implicit trace: Trace): UIO[Vector[String]]

  /**
   * Runs `zio` with the `TestConsole` in silent mode, so that console output is
   * written only to the buffers and not to the live console.
   */
  def silent[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]
}

/**
 * The `TestConsole` service: its `Test` implementation, the layers that build
 * it, and accessors that run a `TestConsole` method on the current
 * `TestConsole`.
 */
object TestConsole extends Serializable {

  /** The `Tag` of `TestConsole`, used to find it in a `ZEnvironment`. */
  implicit val tag: Tag[TestConsole] = Tag(EnvironmentTag.tagFromTagMacro[TestConsole])

  private[test] object unsafe {
    def make(data: Data, debug: Boolean, live: Live, annotations: Annotations)(implicit unsafe: Unsafe): Test =
      Test(Ref.unsafe.make(data), live, annotations, FiberRef.unsafe.make(debug))
  }

  /**
   * The `TestConsole` implementation that the test environment installs. It
   * keeps the input, output and error output buffers in `consoleState`, writes
   * to the live console through `live` while `debugState` is `true`, and
   * records the lines read by `readLine` and written by `printLine` as
   * annotations of the current test.
   */
  case class Test(
    consoleState: Ref.Atomic[TestConsole.Data],
    live: Live,
    annotations: Annotations,
    debugState: FiberRef[Boolean]
  ) extends TestConsole {

    /**
     * Clears the contents of the input buffer.
     */
    def clearInput(implicit trace: Trace): UIO[Unit] =
      consoleState.update(data => data.copy(input = List.empty))

    /**
     * Clears the contents of the output buffer.
     */
    def clearOutput(implicit trace: Trace): UIO[Unit] =
      consoleState.update(data => data.copy(output = Vector.empty))

    /**
     * Clears the contents of the output error buffer.
     */
    def clearOutputErr(implicit trace: Trace): UIO[Unit] =
      consoleState.update(data => data.copy(errOutput = Vector.empty))

    /**
     * Runs the specified effect with the `TestConsole` set to debug mode, so
     * that console output is written to the live console in addition to being
     * written to the output buffers.
     */
    def debug[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      debugState.locally(true)(zio)

    /**
     * Writes the specified sequence of strings to the input buffer. The first
     * string in the sequence will be the first to be taken. These strings will
     * be taken before any strings that were previously in the input buffer.
     */
    def feedLines(lines: String*)(implicit trace: Trace): UIO[Unit] =
      consoleState.update(data => data.copy(input = lines.toList ::: data.input))

    /**
     * Takes the first value from the input buffer, if one exists, or else fails
     * with an `EOFException`.
     */
    def readLine(implicit trace: Trace): IO[IOException, String] =
      ZIO.attempt(unsafe.readLine()(Unsafe.unsafe)).refineToOrDie[IOException].tap { line =>
        annotations.annotate(TestAnnotation.output, Chunk(ConsoleIO.Input(line)))
      }

    /**
     * Returns the contents of the output buffer. The first value written to the
     * output buffer will be the first in the sequence.
     */
    def output(implicit trace: Trace): UIO[Vector[String]] =
      consoleState.get.map(_.output)

    /**
     * Returns the contents of the error output buffer. The first value written
     * to the error output buffer will be the first in the sequence.
     */
    def outputErr(implicit trace: Trace): UIO[Vector[String]] =
      consoleState.get.map(_.errOutput)

    /**
     * Writes the specified string to the output buffer.
     */
    override def print(line: => Any)(implicit trace: Trace): IO[IOException, Unit] =
      ZIO.succeed(unsafe.print(line)(Unsafe.unsafe)) *>
        live.provide(Console.print(line)).whenZIO(debugState.get).unit

    /**
     * Writes the specified string to the error buffer.
     */
    override def printError(line: => Any)(implicit trace: Trace): IO[IOException, Unit] =
      ZIO.succeed(unsafe.printError(line)(Unsafe.unsafe)) *>
        live.provide(Console.printError(line)).whenZIO(debugState.get).unit

    /**
     * Writes the specified string to the output buffer followed by a newline
     * character.
     */
    override def printLine(line: => Any)(implicit trace: Trace): IO[IOException, Unit] =
      annotations.annotate(TestAnnotation.output, Chunk(ConsoleIO.Output(line.toString))) *>
        ZIO.succeed(unsafe.printLine(line)(Unsafe.unsafe)) *>
        live.provide(Console.printLine(line)).whenZIO(debugState.get).unit

    /**
     * Writes the specified string to the error buffer followed by a newline
     * character.
     */
    override def printLineError(line: => Any)(implicit trace: Trace): IO[IOException, Unit] =
      ZIO.succeed(unsafe.printLineError(line)(Unsafe.unsafe)) *>
        live.provide(Console.printLineError(line)).whenZIO(debugState.get).unit

    /**
     * Saves the `TestConsole`'s current state (input, output and error output
     * buffers) in an effect which, when run, will restore the `TestConsole`
     * state to the saved state.
     */
    def save(implicit trace: Trace): UIO[UIO[Unit]] =
      for {
        consoleData <- consoleState.get
      } yield consoleState.set(consoleData)

    /**
     * Runs the specified effect with the `TestConsole` set to silent mode, so
     * that console output is only written to the output buffers and not to the
     * live console.
     */
    def silent[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      debugState.locally(false)(zio)

    override val unsafe: UnsafeAPI =
      new UnsafeAPI {
        override def print(line: Any)(implicit unsafe: Unsafe): Unit =
          consoleState.unsafe.update { data =>
            Data(data.input, data.output :+ line.toString, data.errOutput)
          }

        override def printError(line: Any)(implicit unsafe: Unsafe): Unit =
          consoleState.unsafe.update { data =>
            Data(data.input, data.output, data.errOutput :+ line.toString)
          }

        override def printLine(line: Any)(implicit unsafe: Unsafe): Unit =
          consoleState.unsafe.update { data =>
            Data(data.input, data.output :+ s"$line\n", data.errOutput)
          }

        override def printLineError(line: Any)(implicit unsafe: Unsafe): Unit =
          consoleState.unsafe.update { data =>
            Data(data.input, data.output, data.errOutput :+ s"$line\n")
          }

        override def readLine()(implicit unsafe: Unsafe): String =
          consoleState.unsafe.modify { data =>
            data.input match {
              case head :: tail =>
                head -> Data(tail, data.output, data.errOutput)
              case Nil =>
                throw new EOFException("There is no more input left to read")

            }
          }
      }
  }

  /**
   * Returns a layer that constructs a new `Test` object from `data`, installs
   * it as the `Console` service for the layer's scope, and provides it as the
   * `TestConsole`. With `debug = true` (the default) output is also written to
   * the live console.
   */
  def make(data: Data, debug: Boolean = true)(implicit
    trace: Trace
  ): ZLayer[Live with Annotations, Nothing, TestConsole] =
    ZLayer.scoped {
      ZIO.environmentWithZIO[Live & Annotations] { env =>
        val test = unsafe.make(data, debug, env.get[Live], env.get[Annotations])(Unsafe)
        ZIO.withConsoleScoped(test).as(test)
      }
    }

  /** A layer that passes on the `TestConsole` that its input already holds. */
  val any: ZLayer[TestConsole, Nothing, TestConsole] =
    ZLayer.environment[TestConsole](Tracer.newTrace)

  private[test] val DefaultData: Data =
    Data(Nil, Vector())

  /**
   * A layer built with `make` that starts with empty buffers in debug mode, so
   * output is also written to the live console.
   */
  val debug: ZLayer[Live with Annotations, Nothing, TestConsole] =
    make(DefaultData, true)(Tracer.newTrace)

  /**
   * A layer built with `make` that starts with empty buffers in silent mode, so
   * output is written only to the buffers.
   */
  val silent: ZLayer[Live with Annotations, Nothing, TestConsole] =
    make(Data(Nil, Vector()), false)(Tracer.newTrace)

  /**
   * Accesses the current `TestConsole` and clears the input buffer.
   */
  def clearInput(implicit trace: Trace): UIO[Unit] =
    testConsoleWith(_.clearInput)

  /**
   * Accesses the current `TestConsole` and clears the output buffer.
   */
  def clearOutput(implicit trace: Trace): UIO[Unit] =
    testConsoleWith(_.clearOutput)

  /**
   * Accesses the current `TestConsole` and runs the specified effect with the
   * `TestConsole` set to debug mode, so that console output is written to the
   * live console in addition to being written to the output buffers.
   */
  def debug[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    testConsoleWith(_.debug(zio))

  /**
   * Accesses the current `TestConsole` and writes the specified sequence of
   * strings to the input buffer, ahead of any strings already there.
   *
   * {{{
   * import zio._
   * import zio.test._
   *
   * for {
   *   _      <- TestConsole.feedLines("Alice", "42")
   *   name   <- Console.readLine
   *   age    <- Console.readLine
   *   _      <- Console.printLine(name + " is " + age)
   *   output <- TestConsole.output
   * } yield assertTrue(output == Vector("Alice is 42\n"))
   * }}}
   */
  def feedLines(lines: String*)(implicit trace: Trace): UIO[Unit] =
    testConsoleWith(_.feedLines(lines: _*))

  /**
   * Accesses the current `TestConsole` and returns the contents of the output
   * buffer.
   */
  def output(implicit trace: Trace): UIO[Vector[String]] =
    testConsoleWith(_.output)

  /**
   * Accesses the current `TestConsole` and returns the contents of the error
   * buffer.
   */
  def outputErr(implicit trace: Trace): UIO[Vector[String]] =
    testConsoleWith(_.outputErr)

  /**
   * Accesses the current `TestConsole` and saves the console state in an effect
   * which, when run, will restore the `TestConsole` to the saved state.
   */
  def save(implicit trace: Trace): UIO[UIO[Unit]] =
    testConsoleWith(_.save)

  /**
   * Accesses the current `TestConsole` and runs the specified effect with the
   * `TestConsole` set to silent mode, so that console output is only written to
   * the output buffers and not to the live console.
   */
  def silent[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    testConsoleWith(_.silent(zio))

  /**
   * The state of the `TestConsole`: the lines left to read, the output buffer
   * and the error output buffer.
   */
  final case class Data(
    input: List[String] = List.empty,
    output: Vector[String] = Vector.empty,
    errOutput: Vector[String] = Vector.empty
  )
}
