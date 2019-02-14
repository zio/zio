// Copyright (C) 2017 John A. De Goes. All rights reserved.
package scalaz.zio

import java.io.{ IOException, PrintStream, Reader }

package object console extends Console.Interface[Console] {
  final val consoleService: ZIO[Console, Nothing, Console.Interface[Any]] =
    ZIO.read(_.console)

  /**
   * Prints text to the console.
   */
  final def putStr(line: String): ZIO[Console, Nothing, Unit] =
    ZIO.readM(_.console putStr line)

  final def putStr(stream: PrintStream)(line: String): ZIO[Console, Nothing, Unit] =
    ZIO.readM(_.console.putStr(stream)(line))

  /**
   * Prints a line of text to the console, including a newline character.
   */
  final def putStrLn(line: String): ZIO[Console, Nothing, Unit] =
    ZIO.readM(_.console putStrLn line)

  final def putStrLn(stream: PrintStream)(line: String): ZIO[Console, Nothing, Unit] =
    ZIO.readM(_.console.putStrLn(stream)(line))

  /**
   * Retrieves a line of input from the console.
   */
  final val getStrLn: ZIO[Console, IOException, String] =
    ZIO.readM(_.console.getStrLn)

  final def getStrLn(reader: Reader): ZIO[Console, IOException, String] =
    ZIO.readM(_.console getStrLn reader)

}
