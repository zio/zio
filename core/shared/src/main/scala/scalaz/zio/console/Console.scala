// Copyright (C) 2017-2019 John A. De Goes. All rights reserved.
package scalaz.zio.console

import java.io.{ IOException, PrintStream, Reader }

import scalaz.zio.{ IO, UIO, ZIO }

import scala.io.StdIn
import scala.{ Console => SConsole }

trait Console extends Serializable {
  val console: Console.Interface[Any]
}
object Console extends Serializable {
  trait Interface[R] {
    def putStr(line: String): ZIO[R, Nothing, Unit]

    def putStr(stream: PrintStream)(line: String): ZIO[R, Nothing, Unit]

    def putStrLn(line: String): ZIO[R, Nothing, Unit]

    def putStrLn(stream: PrintStream)(line: String): ZIO[R, Nothing, Unit]

    val getStrLn: ZIO[R, IOException, String]

    def getStrLn(reader: Reader): ZIO[R, IOException, String]
  }
  trait Live extends Console {
    object console extends Interface[Any] {

      /**
       * Prints text to the console.
       */
      final def putStr(line: String): UIO[Unit] =
        putStr(SConsole.out)(line)

      final def putStr(stream: PrintStream)(line: String): UIO[Unit] =
        IO.sync(SConsole.withOut(stream) {
          SConsole.print(line)
        })

      /**
       * Prints a line of text to the console, including a newline character.
       */
      final def putStrLn(line: String): ZIO[Any, Nothing, Unit] =
        putStrLn(SConsole.out)(line)

      final def putStrLn(stream: PrintStream)(line: String): UIO[Unit] =
        IO.sync(SConsole.withOut(stream) {
          SConsole.println(line)
        })

      /**
       * Retrieves a line of input from the console.
       */
      final val getStrLn: ZIO[Any, IOException, String] =
        getStrLn(SConsole.in)

      final def getStrLn(reader: Reader): IO[IOException, String] =
        IO.syncCatch(SConsole.withIn(reader) {
          StdIn.readLine()
        }) {
          case e: IOException => e
        }

    }
  }
  object Live extends Live
}
