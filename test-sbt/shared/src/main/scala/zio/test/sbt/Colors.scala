package zio.test.sbt

import scala.annotation.tailrec

/**
 * ANSI color helpers for the text that the ZIO Test sbt integration prints.
 */
object Colors {

  /**
   * Returns `str` preceded by the ANSI code `code` and followed by the ANSI
   * reset code.
   */
  def colored(code: String)(str: String): String = s"$code$str${Console.RESET}"

  /** Colors a string red. */
  val red: String => String = colored(Console.RED)

  /** Colors a string green. */
  val green: String => String = colored(Console.GREEN)

  /** Colors a string cyan. */
  val cyan: String => String = colored(Console.CYAN)

  /** Colors a string blue. */
  val blue: String => String = colored(Console.BLUE)

  /** Colors a string yellow. */
  val yellow: String => String = colored(Console.YELLOW)

  /** Returns `str` preceded by the ANSI reset code. */
  def reset(str: String): String = s"${Console.RESET}$str"

  /**
   * Inserts the ANSI code of the current color at the beginning of each line of
   * `s`, so that each line of colored text keeps its color when the lines are
   * printed one by one. The current color is the last red, green, cyan, blue or
   * yellow code before the line break that no reset code has ended.
   */
  def coloredLines(s: String): String = {
    @tailrec
    def loop(s: String, i: Int, color: Option[String]): String =
      if (i >= s.length) s
      else {
        val s1 = s.slice(i, i + 5)
        val isColor = s1 == Console.BLUE ||
          s1 == Console.CYAN ||
          s1 == Console.GREEN ||
          s1 == Console.RED ||
          s1 == Console.YELLOW
        if (isColor)
          loop(s, i + 5, Some(s1))
        else if (s.slice(i, i + 4) == Console.RESET)
          loop(s, i + 4, None)
        else if (s.slice(i, i + 1) == "\n" && color.isDefined)
          loop(s.patch(i + 1, color.get, 0), i + 6, color)
        else loop(s, i + 1, color)

      }
    loop(s, 0, None)
  }
}
