package zio.test.sbt

/**
 * A `Render` is a structured description of a String including values
 * representing ANSI escape codes for colorizing console output. This is used
 * internally by the test framework to ensure that colored strings are
 * displayed appropriately by the `SBTTestLogger`.
 */
private[sbt] sealed trait Render {
  import Render._

  /**
   * Returns whether this `Render` represents an ANSI escape code for colorized
   * output.
   */
  final def isColor: Boolean = this match {
    case Blue   => true
    case Cyan   => true
    case Green  => true
    case Red    => true
    case Yellow => true
    case _      => false
  }

  /**
   * Returns whether this `Render` represents a newline character.
   */
  final def isNewLine: Boolean = this match {
    case NewLine => true
    case _       => false
  }

  /**
   * Returns whether this `Render` represents resetting ANSI styles.
   */
  final def isReset: Boolean = this match {
    case Reset => true
    case _     => false
  }

  final override def toString: String = this match {
    case Blue          => Console.BLUE
    case Cyan          => Console.CYAN
    case Green         => Console.GREEN
    case Literal(char) => char.toString
    case NewLine       => "\n"
    case Red           => Console.RED
    case Reset         => Console.RESET
    case Yellow        => Console.YELLOW
  }
}

private[sbt] object Render {
  case object Blue                     extends Render
  case object Cyan                     extends Render
  case object Green                    extends Render
  final case class Literal(char: Char) extends Render
  case object NewLine                  extends Render
  case object Red                      extends Render
  case object Reset                    extends Render
  case object Yellow                   extends Render

  /**
   * Parses the ANSI escape code for blue.
   */
  final val blue: Parser[Unit, Render] =
    Parser { input =>
      if (input.slice(5) == Console.BLUE) Right((input.advanceBy(5), Blue))
      else Left(())
    }

  /**
   * Inserts the last ANSI escape code for colorized output seen after each
   * newline character in the specified list of `Render` values.
   */
  final def colored(renders: List[Render]): List[Render] = {
    def loop(color: Option[Render], renders: List[Render], acc: List[Render]): List[Render] =
      renders match {
        case Nil                     => acc.reverse
        case (h :: t) if h.isNewLine => loop(color, t, color.fold(h :: acc)(_ :: h :: acc))
        case (h :: t) if h.isColor   => loop(Some(h), t, h :: acc)
        case (h :: t) if h.isReset   => loop(None, t, h :: acc)
        case (h :: t)                => loop(color, t, h :: acc)
      }
    loop(None, renders, List.empty)
  }

  /**
   * Parses the ANSI escape code for cyan.
   */
  final val cyan: Parser[Unit, Render] =
    Parser { input =>
      if (input.slice(5) == Console.CYAN) Right((input.advanceBy(5), Cyan))
      else Left(())
    }

  /**
   * Parses the ANSI escape code for green.
   */
  final val green: Parser[Unit, Render] =
    Parser { input =>
      if (input.slice(5) == Console.GREEN) Right((input.advanceBy(5), Green))
      else Left(())
    }

  /**
   * Parses a literal character.
   */
  final val literal: Parser[Unit, Render] =
    Parser { input =>
      if (!input.isComplete) Right((input.advanceBy(1), Literal(input.extract)))
      else Left(())
    }

  /**
   * Parses a newline character.
   */
  final val newLine: Parser[Unit, Render] =
    Parser { input =>
      if (input.slice(1) == "\n") Right((input.advanceBy(1), NewLine))
      else Left(())
    }

  /**
   * Parses the ANSI escape code for red.
   */
  final val red: Parser[Unit, Render] =
    Parser { input =>
      if (input.slice(5) == Console.RED) Right((input.advanceBy(5), Red))
      else Left(())
    }

  /**
   * Parses the ANSI escape code for resetting ANSI styles.
   */
  final val reset: Parser[Unit, Render] =
    Parser { input =>
      if (input.slice(4) == Console.RESET) Right((input.advanceBy(4), Reset))
      else Left(())
    }

  /**
   * Parses the ANSI escape code for yellow.
   */
  final val yellow: Parser[Unit, Render] =
    Parser { input =>
      if (input.slice(5) == Console.YELLOW) Right((input.advanceBy(5), Yellow))
      else Left(())
    }

  /**
   * Parses a String into a `Render`.
   */
  final val render: Parser[Unit, List[Render]] =
    (blue <> cyan <> green <> newLine <> red <> reset <> yellow <> literal).repeat
}
