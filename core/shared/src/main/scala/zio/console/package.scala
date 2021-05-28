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

package object console {
  type Console = Has[Console.Service]

  object Console extends Serializable {
    trait Service extends Serializable {
      def putStr(line: String): IO[IOException, Unit]

      def putStrErr(line: String): IO[IOException, Unit]

      def putStrLn(line: String): IO[IOException, Unit]

      def putStrLnErr(line: String): IO[IOException, Unit]

      def getStrLn: IO[IOException, String]
    }

    object Service {
      private def putStr(stream: PrintStream)(line: String): IO[IOException, Unit] =
        IO.effect(SConsole.withOut(stream)(SConsole.print(line))).refineToOrDie[IOException]

      private def putStrLn(stream: PrintStream)(line: String): IO[IOException, Unit] =
        IO.effect(SConsole.withOut(stream)(SConsole.println(line))).refineToOrDie[IOException]

      val live: Service = new Service {
        def putStr(line: String): IO[IOException, Unit] = Service.putStr(SConsole.out)(line)

        def putStrErr(line: String): IO[IOException, Unit] = Service.putStr(SConsole.err)(line)

        def putStrLnErr(line: String): IO[IOException, Unit] = Service.putStrLn(SConsole.err)(line)

        def putStrLn(line: String): IO[IOException, Unit] = Service.putStrLn(SConsole.out)(line)

        val getStrLn: IO[IOException, String] =
          IO.effect {
            val line = StdIn.readLine()

            if (line ne null) line
            else throw new EOFException("There is no more input left to read")
          }.refineToOrDie[IOException]
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
  def putStr(line: => String): ZIO[Console, IOException, Unit] =
    ZIO.accessM(_.get putStr line)

  /**
   * Prints text to the standard error console.
   */
  def putStrErr(line: => String): ZIO[Console, IOException, Unit] =
    ZIO.accessM(_.get putStrErr line)

  /**
   * Prints a line of text to the console, including a newline character.
   */
  def putStrLn(line: => String): ZIO[Console, IOException, Unit] =
    ZIO.accessM(_.get putStrLn line)

  /**
   * Prints a line of text to the standard error console, including a newline character.
   */
  def putStrLnErr(line: => String): ZIO[Console, IOException, Unit] =
    ZIO.accessM(_.get putStrLnErr line)

  /**
   * Retrieves a line of input from the console.
   * Fails with an [[java.io.EOFException]] when the underlying [[java.io.Reader]]
   * returns null.
   */
  val getStrLn: ZIO[Console, IOException, String] =
    ZIO.accessM(_.get.getStrLn)
}
