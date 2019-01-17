package scalaz
package zio
package interop
package console

import zio.{ console => c }

object scalaz {

  /**
   * Prints the string representation of an object to the console.
   */
  def putStr[A](a: A)(implicit ev: Show[A]): ZIO[c.Console, Nothing, Unit] =
    c.putStr(ev.shows(a))

  /**
   * Prints the string representation of an object to the console, including a newline character.
   */
  def putStrLn[A](a: A)(implicit ev: Show[A]): ZIO[c.Console, Nothing, Unit] =
    c.putStrLn(ev.shows(a))
}
