// Copyright (C) 2017 John A. De Goes. All rights reserved.
package scalaz.zio

import java.io.IOException

package object console {

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
  final def getStrLn: IO[IOException, String] =
    IO.syncCatch(scala.io.StdIn.readLine()) {
      case e: IOException => e
    }

}
