package zio.test.sbt

import scala.annotation.tailrec

import zio.test.sbt.Parser._

/**
 * A Parser[E, A] can be run to parse a String, potentially failing with an `E`
 * or succeeding with an `A` and a leftover String.
 */
private[sbt] final case class Parser[+E, +A](run: ParseState => Either[E, (ParseState, A)]) { self =>

  /**
   * A symbolic alias for `orElse`.
   */
  final def <>[E1, A1 >: A](that: => Parser[E1, A1]): Parser[E1, A1] =
    self.orElse(that)

  /**
   * Runs this parser and returns its result if it succeeds or else runs that
   * parser.
   */
  final def orElse[E1, A1 >: A](that: => Parser[E1, A1]): Parser[E1, A1] =
    Parser { s =>
      self.run(s) match {
        case Left(_)       => that.run(s)
        case Right(result) => Right(result)
      }
    }

  /**
   * Runs this parser until the string has been completely parsed or the parser
   * fails.
   */
  final def repeat: Parser[E, List[A]] = {
    @tailrec
    def loop(s: ParseState, acc: List[A]): Either[E, (ParseState, List[A])] =
      if (s.isComplete) Right((s, acc.reverse))
      else
        self.run(s) match {
          case Left(e)        => Left(e)
          case Right((s1, a)) => loop(s1, a :: acc)
        }
    Parser(loop(_, List.empty))
  }

  final def run(s: String): Either[E, (ParseState, A)] =
    run(ParseState(s, 0))

}

object Parser {

  final case class ParseState(string: String, index: Int) { self =>
    final def advanceBy(n: Int): ParseState =
      self.copy(index = index + n)
    final def extract: Char =
      string(index)
    final def isComplete: Boolean =
      index >= string.length
    final def slice(n: Int): String =
      string.slice(index, index + n)
  }
}
