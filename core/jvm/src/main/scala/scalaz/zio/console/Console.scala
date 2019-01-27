// Copyright (C) 2017-2019 John A. De Goes. All rights reserved.
package scalaz.zio.console

import scalaz.zio.{ IO, ZIO }
import java.io.IOException

trait Console extends Serializable {
  val console: Console.Interface[Any]
}
object Console extends Serializable {
  trait Interface[R] {
    def putStr(line: String): ZIO[R, Nothing, Unit]

    def putStrLn(line: String): ZIO[R, Nothing, Unit]

    val getStrLn: ZIO[R, IOException, String]
  }
  trait Live extends Console {
    object console extends Interface[Any] {

      /**
       * Prints text to the console.
       */
      final def putStr(line: String): IO[Nothing, Unit] =
        IO.sync(scala.Console.print(line))

      /**
       * Prints a line of text to the console, including a newline character.
       */
      final def putStrLn(line: String): IO[Nothing, Unit] =
        IO.sync(scala.Console.println(line))

      /**
       * Retrieves a line of input from the console.
       */
      final val getStrLn: IO[IOException, String] =
        IO.syncCatch(scala.io.StdIn.readLine()) {
          case e: IOException => e
        }
    }
  }
  object Live extends Live
}
