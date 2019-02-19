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

package scalaz.zio.console

import java.io.{ IOException, PrintStream, Reader }

import scalaz.zio.{ IO, UIO, ZIO }

import scala.io.StdIn
import scala.{ Console => SConsole }

trait Console extends Serializable {
  val console: Console.Service[Any]
}
object Console extends Serializable {
  trait Service[R] {
    def putStr(line: String): ZIO[R, Nothing, Unit]

    def putStr(stream: PrintStream)(line: String): ZIO[R, Nothing, Unit]

    def putStrLn(line: String): ZIO[R, Nothing, Unit]

    def putStrLn(stream: PrintStream)(line: String): ZIO[R, Nothing, Unit]

    val getStrLn: ZIO[R, IOException, String]

    def getStrLn(reader: Reader): ZIO[R, IOException, String]
  }
  trait Live extends Console {
    val console: Service[Any] = new Service[Any] {

      /**
       * Prints text to the console.
       */
      final def putStr(line: String): UIO[Unit] =
        putStr(SConsole.out)(line)

      final def putStr(stream: PrintStream)(line: String): UIO[Unit] =
        IO.defer(SConsole.withOut(stream) {
          SConsole.print(line)
        })

      /**
       * Prints a line of text to the console, including a newline character.
       */
      final def putStrLn(line: String): ZIO[Any, Nothing, Unit] =
        putStrLn(SConsole.out)(line)

      final def putStrLn(stream: PrintStream)(line: String): UIO[Unit] =
        IO.defer(SConsole.withOut(stream) {
          SConsole.println(line)
        })

      /**
       * Retrieves a line of input from the console.
       */
      final val getStrLn: ZIO[Any, IOException, String] =
        getStrLn(SConsole.in)

      final def getStrLn(reader: Reader): IO[IOException, String] =
        IO.sync(SConsole.withIn(reader) {
            StdIn.readLine()
          })
          .keepSome {
            case e: IOException => e
          }

    }
  }
  object Live extends Live
}
