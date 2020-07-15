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

import java.io.{ EOFException, IOException, PrintStream, Reader }

import scala.io.StdIn
import scala.{ Console => SConsole }

package object console {
  type Console = Has[Console.Service]

  object Console extends Serializable {
    trait Service extends Serializable {
      def putStr(line: String): UIO[Unit]

      def putStrErr(line: String): UIO[Unit]

      def putStrLn(line: String): UIO[Unit]

      def putStrLnErr(line: String): UIO[Unit]

      def getStrLn: IO[IOException, String]
    }

    object Service {
      val live: Service = new Service {

        /**
         * Prints text to the console.
         */
        final def putStr(line: String): UIO[Unit] =
          putStr(SConsole.out)(line)

        /**
         * Prints text to the standard error console.
         */
        override def putStrErr(line: String): UIO[Unit] =
          putStrLn(SConsole.err)(line)

        final def putStr(stream: PrintStream)(line: String): UIO[Unit] =
          IO.effectTotal(SConsole.withOut(stream) {
            SConsole.print(line)
          })

        /**
         * Prints a line of text to the standard error console, including a newline character.
         */
        override def putStrLnErr(line: String): UIO[Unit] =
          putStrLn(SConsole.err)(line)

        /**
         * Prints a line of text to the console, including a newline character.
         */
        final def putStrLn(line: String): UIO[Unit] =
          putStrLn(SConsole.out)(line)

        final def putStrLn(stream: PrintStream)(line: String): UIO[Unit] =
          IO.effectTotal(SConsole.withOut(stream) {
            SConsole.println(line)
          })

        /**
         * Retrieves a line of input from the console.
         */
        final val getStrLn: IO[IOException, String] =
          getStrLn(SConsole.in)

        /**
         * Retrieves a line of input from the console.
         * Fails with an [[java.io.EOFException]] when the underlying [[java.io.Reader]]
         * returns null.
         */
        final def getStrLn(reader: Reader): IO[IOException, String] =
          IO.effect(SConsole.withIn(reader) {
              val line = StdIn.readLine()
              if (line == null) {
                throw new EOFException("There is no more input left to read")
              } else line
            })
            .refineToOrDie[IOException]

      }
    }

    val any: ZLayer[Console, Nothing, Console] =
      ZLayer.requires[Console]

    val live: Layer[Nothing, Console] =
      ZLayer.succeed(Service.live)
  }

  /**
   * Prints text to the console.
   */
  def putStr(line: => String): URIO[Console, Unit] =
    ZIO.accessM(_.get putStr line)

  /**
   * Prints text to the standard error console.
   */
  def putStrErr(line: => String): URIO[Console, Unit] =
    ZIO.accessM(_.get putStrErr line)

  /**
   * Prints a line of text to the console, including a newline character.
   */
  def putStrLn(line: => String): URIO[Console, Unit] =
    ZIO.accessM(_.get putStrLn line)

  /**
   * Prints a line of text to the standard error console, including a newline character.
   */
  def putStrLnErr(line: => String): URIO[Console, Unit] =
    ZIO.accessM(_.get putStrLnErr line)

  /**
   * Retrieves a line of input from the console.
   */
  val getStrLn: ZIO[Console, IOException, String] =
    ZIO.accessM(_.get.getStrLn)
}
