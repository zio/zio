// Copyright (C) 2017 John A. De Goes. All rights reserved.
package scalaz.zio

package object console {

  /**
   * Prints text to the console.
   */
  def putStr(line: String): IO[Nothing, Unit] =
    IO.async[Nothing, Unit](_(ExitResult.Completed(println(line))))

  /**
   * Prints a line of text to the console, including a newline character.
   */
  def putStrLn(line: String): IO[Nothing, Unit] =
    IO.async[Nothing, Unit](_(ExitResult.Completed(println(line))))

}
