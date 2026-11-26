/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

import java.io.{EOFException, IOException, PrintStream}
import scala.io.StdIn
import scala.{Console => SConsole}

/**
 * A service for writing text to standard output and standard error and for
 * reading lines from standard input. Every operation can fail with
 * `java.io.IOException`. The accessors in the `Console` object use the console
 * of the current workflow, which is [[Console.ConsoleLive]] unless it is
 * replaced, for example with `ZIO.withConsole` or, in ZIO Test, with
 * `TestConsole`, which feeds input and records output.
 *
 * {{{
 * import java.io.IOException
 *
 * val greet: IO[IOException, Unit] =
 *   for {
 *     _    <- Console.printLine("What is your name?")
 *     name <- Console.readLine
 *     _    <- Console.printLine("Hello, " + name + "!")
 *   } yield ()
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/services/console/ Console]]
 */
trait Console extends Serializable { self =>

  /**
   * Writes the `toString` of `line` to standard output without adding a line
   * separator.
   */
  def print(line: => Any)(implicit trace: Trace): IO[IOException, Unit]

  /**
   * Writes the `toString` of `line` to standard error without adding a line
   * separator.
   */
  def printError(line: => Any)(implicit trace: Trace): IO[IOException, Unit]

  /**
   * Writes the `toString` of `line` and a line separator to standard output.
   */
  def printLine(line: => Any)(implicit trace: Trace): IO[IOException, Unit]

  /**
   * Writes the `toString` of `line` and a line separator to standard error.
   */
  def printLineError(line: => Any)(implicit trace: Trace): IO[IOException, Unit]

  /**
   * Reads one line from standard input, without its line terminator.
   * `ConsoleLive` and `TestConsole` fail with `java.io.EOFException` ("There is
   * no more input left to read") when no input is left.
   */
  def readLine(implicit trace: Trace): IO[IOException, String]

  /**
   * Writes `prompt` with `print`, without a line separator, and then reads a
   * line with `readLine`.
   */
  def readLine(prompt: String)(implicit trace: Trace): IO[IOException, String] =
    print(prompt) *> readLine

  /**
   * Synchronous versions of the console operations, for code outside ZIO. Each
   * method returns its result directly instead of an effect, needs an implicit
   * `Unsafe` value, and throws where the effect would fail.
   */
  trait UnsafeAPI extends Serializable {

    /** Writes `line` to standard output without adding a line separator. */
    def print(line: Any)(implicit unsafe: Unsafe): Unit

    /** Writes `line` to standard error without adding a line separator. */
    def printError(line: Any)(implicit unsafe: Unsafe): Unit

    /** Writes `line` and a line separator to standard output. */
    def printLine(line: Any)(implicit unsafe: Unsafe): Unit

    /** Writes `line` and a line separator to standard error. */
    def printLineError(line: Any)(implicit unsafe: Unsafe): Unit

    /** Reads one line from standard input, without its line terminator. */
    def readLine()(implicit unsafe: Unsafe): String
  }

  /**
   * Returns the synchronous API of this console. This default runs each effect
   * of the console on `Runtime.default` and throws a `zio.FiberFailure` when
   * the effect fails or dies; `ConsoleLive` overrides it to use the standard
   * streams directly.
   */
  def unsafe: UnsafeAPI =
    new UnsafeAPI {
      def print(line: Any)(implicit unsafe: Unsafe): Unit =
        Runtime.default.unsafe.run(self.print(line)(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def printError(line: Any)(implicit unsafe: Unsafe): Unit =
        Runtime.default.unsafe.run(self.printError(line)(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def printLine(line: Any)(implicit unsafe: Unsafe): Unit =
        Runtime.default.unsafe.run(self.printLine(line)(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def printLineError(line: Any)(implicit unsafe: Unsafe): Unit =
        Runtime.default.unsafe.run(self.printLineError(line)(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def readLine()(implicit unsafe: Unsafe): String =
        Runtime.default.unsafe.run(self.readLine(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()
    }
}

/**
 * Accessors for the `Console` service of the current workflow, and the default
 * implementation [[Console.ConsoleLive]].
 *
 * @see
 *   [[https://zio.dev/reference/services/console/ Console]]
 */
object Console extends Serializable {

  /** The `Tag` that identifies the `Console` service in a ZIO environment. */
  implicit val tag: Tag[Console] = Tag(EnvironmentTag.tagFromTagMacro[Console])

  /**
   * The default `Console`. It writes through `scala.Console.out` and
   * `scala.Console.err` and reads with `scala.io.StdIn.readLine()`, running
   * each operation on ZIO's blocking executor (on Scala.js, the default
   * executor).
   *
   * An `IOException` thrown while printing (for example by the `toString` of
   * the value) fails the effect, and any other exception makes it die. The
   * standard streams are `java.io.PrintStream`s, which record write errors in
   * `checkError()` instead of throwing, so a write to a closed standard output
   * succeeds.
   */
  object ConsoleLive extends Console {

    /**
     * Writes `line` (its `toString`, or `"null"` for `null`) to
     * `scala.Console.out` without a line separator.
     */
    def print(line: => Any)(implicit trace: Trace): IO[IOException, Unit] =
      ZIO.attemptBlockingIO(unsafe.print(line)(Unsafe.unsafe))

    /**
     * Writes `line` (its `toString`, or `"null"` for `null`) to
     * `scala.Console.err` without a line separator.
     */
    def printError(line: => Any)(implicit trace: Trace): IO[IOException, Unit] =
      ZIO.attemptBlockingIO(unsafe.printError(line)(Unsafe.unsafe))

    /**
     * Writes `line` (its `toString`, or `"null"` for `null`) and a line
     * separator to `scala.Console.out`.
     */
    def printLine(line: => Any)(implicit trace: Trace): IO[IOException, Unit] =
      ZIO.attemptBlockingIO(unsafe.printLine(line)(Unsafe.unsafe))

    /**
     * Writes `line` (its `toString`, or `"null"` for `null`) and a line
     * separator to `scala.Console.err`.
     */
    def printLineError(line: => Any)(implicit trace: Trace): IO[IOException, Unit] =
      ZIO.attemptBlockingIO(unsafe.printLineError(line)(Unsafe.unsafe))

    /**
     * Reads one line with `scala.io.StdIn.readLine()` on ZIO's blocking
     * executor (on Scala.js, the default executor). Fails with
     * `java.io.EOFException` ("There is no more input left to read") at the end
     * of standard input; an exception that is not an `IOException` makes the
     * effect die.
     */
    def readLine(implicit trace: Trace): IO[IOException, String] =
      ZIO.attemptBlockingInterrupt(unsafe.readLine()(Unsafe.unsafe)).refineToOrDie[IOException]

    /**
     * Uses the standard streams directly, without running an effect.
     * `readLine()` throws `java.io.EOFException` at the end of standard input.
     */
    override val unsafe: UnsafeAPI =
      new UnsafeAPI {
        override def print(line: Any)(implicit unsafe: Unsafe): Unit =
          print(SConsole.out)(line)

        override def printError(line: Any)(implicit unsafe: Unsafe): Unit =
          print(SConsole.err)(line)

        override def printLine(line: Any)(implicit unsafe: Unsafe): Unit =
          printLine(SConsole.out)(line)

        override def printLineError(line: Any)(implicit unsafe: Unsafe): Unit =
          printLine(SConsole.err)(line)

        override def readLine()(implicit unsafe: Unsafe): String = {
          val line = StdIn.readLine()

          if (line ne null) line
          else throw new EOFException("There is no more input left to read")
        }

        private def print(stream: => PrintStream)(line: => Any): Unit =
          SConsole.withOut(stream)(SConsole.print(line))

        private def printLine(stream: => PrintStream)(line: => Any): Unit =
          SConsole.withOut(stream)(SConsole.println(line))
      }
  }

  /**
   * Writes `line` to standard output without adding a line separator, using the
   * `Console` of the current workflow.
   */
  def print(line: => Any)(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.consoleWith(_.print(line))

  /**
   * Writes `line` to standard error without adding a line separator, using the
   * `Console` of the current workflow.
   */
  def printError(line: => Any)(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.consoleWith(_.printError(line))

  /**
   * Writes `line` and a line separator to standard output, using the `Console`
   * of the current workflow.
   *
   * {{{
   * val hello: IO[java.io.IOException, Unit] = Console.printLine("Hello, ZIO")
   * }}}
   */
  def printLine(line: => Any)(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.consoleWith(_.printLine(line))

  /**
   * Writes `line` and a line separator to standard error, using the `Console`
   * of the current workflow.
   */
  def printLineError(line: => Any)(implicit trace: Trace): IO[IOException, Unit] =
    ZIO.consoleWith(_.printLineError(line))

  /**
   * Reads one line from standard input, without its line terminator, using the
   * `Console` of the current workflow. With `ConsoleLive` and `TestConsole` it
   * fails with `java.io.EOFException` when no input is left.
   */
  def readLine(implicit trace: Trace): IO[IOException, String] =
    ZIO.consoleWith(_.readLine)

  /**
   * Writes `prompt` to standard output without a line separator and then reads
   * one line of input, using the `Console` of the current workflow. With
   * `ConsoleLive` and `TestConsole` it fails with `java.io.EOFException` when
   * no input is left.
   *
   * {{{
   * val name: IO[java.io.IOException, String] = Console.readLine("Name: ")
   * }}}
   */
  def readLine(prompt: String)(implicit trace: Trace): IO[IOException, String] =
    ZIO.consoleWith(_.readLine(prompt))
}
