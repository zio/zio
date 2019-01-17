package scalaz
package zio
package interop
package console

import _root_.cats.{ Show => CatsShow }

import zio.{ console => c }

object cats {

  /**
   * Prints the string representation of an object to the console.
   */
  def putStr[A](a: A)(implicit ev: CatsShow[A]): IO[Nothing, Unit] =
    c.putStr(ev.show(a))

  /**
   * Prints the string representation of an object to the console, including a newline character.
   */
  def putStrLn[A](a: A)(implicit ev: CatsShow[A]): IO[Nothing, Unit] =
    c.putStrLn(ev.show(a))
}
