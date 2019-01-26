// Copyright (C) 2017 John A. De Goes. All rights reserved.
package scalaz.zio

import java.io.{ IOException, PrintStream, Reader }

import scala.Console
import scala.io.StdIn

package object console {

  /**
   * Prints text to the console.
   */
  // $COVERAGE-OFF$ Bootstrap to default stdout
  final def putStr(line: String): IO[Nothing, Unit] =
    putStr(Console.out)(line)
  // $COVERAGE-ON$

  final def putStr(stream: PrintStream)(line: String): IO[Nothing, Unit] =
    IO.sync(Console.withOut(stream) { Console.print(line) })

  /**
   * Prints a line of text to the console, including a newline character.
   */
  // $COVERAGE-OFF$ Bootstrap to default stdout
  final def putStrLn(line: String): IO[Nothing, Unit] =
    putStrLn(Console.out)(line)
  // $COVERAGE-ON$

  final def putStrLn(stream: PrintStream)(line: String): IO[Nothing, Unit] =
    IO.sync(Console.withOut(stream) { Console.println(line) })

  /**
   * Retrieves a line of input from the console.
   */
  // $COVERAGE-OFF$ Bootstrap to default stdin
  final def getStrLn: IO[IOException, String] =
    getStrLn(Console.in)
  // $COVERAGE-ON$

  final def getStrLn(reader: Reader): IO[IOException, String] =
    IO.syncCatch(Console.withIn(reader) { StdIn.readLine() }) {
      case e: IOException => e
    }
}
