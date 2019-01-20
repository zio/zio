// Copyright (C) 2017-2019 John A. De Goes. All rights reserved.
package scalaz.zio

import java.io.IOException

package object console {

  /**
   * Prints text to the console.
   */
  final def putStr(line: String): ZIO[Console, Nothing, Unit] =
    ZIO.readM(_.console putStr line)

  /**
   * Prints a line of text to the console, including a newline character.
   */
  final def putStrLn(line: String): ZIO[Console, Nothing, Unit] =
    ZIO.readM(_.console putStrLn line)

  /**
   * Retrieves a line of input from the console.
   */
  final val getStrLn: ZIO[Console, IOException, String] =
    ZIO.readM(_.console.getStrLn)
}
