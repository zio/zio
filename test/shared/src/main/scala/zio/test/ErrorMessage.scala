package zio.test

import zio.test.render.LogLine.Message
import zio.test.render._

import scala.io.AnsiColor

object ErrorMessage {

  def throwable(throwable: Throwable): ErrorMessage = ThrowableM(throwable)

  def choice(success: String, failure: String): ErrorMessage = Choice(success, failure)
  def text(string: String): ErrorMessage                     = choice(string, string)
  def value(value: Any): ErrorMessage                        = Value(value)

  val equals: ErrorMessage = choice("was equal to", "was not equal to")
  val is: ErrorMessage     = choice("is", "is not")
  val does: ErrorMessage   = choice("does", "does not")
  val did: ErrorMessage    = choice("did", "did not")
  val was: ErrorMessage    = choice("was", "was not")
  val valid: ErrorMessage  = choice("Valid", "Invalid")

  private final case class Value(value: Any)                                               extends ErrorMessage
  private final case class ThrowableM(throwable: Throwable)                                extends ErrorMessage
  private final case class Choice(success: String, failure: String)                        extends ErrorMessage
  private final case class Combine(lhs: ErrorMessage, rhs: ErrorMessage, spacing: Int = 1) extends ErrorMessage
}

sealed trait ErrorMessage { self =>
  def +(that: String): ErrorMessage        = ErrorMessage.Combine(self, ErrorMessage.text(that))
  def +(that: ErrorMessage): ErrorMessage  = ErrorMessage.Combine(self, that)
  def +/(that: ErrorMessage): ErrorMessage = ErrorMessage.Combine(self, that, 0)

  private[test] def render(isSuccess: Boolean): Message =
    self match {
      case ErrorMessage.Choice(success, failure) =>
        (if (isSuccess) fr(success).ansi(AnsiColor.MAGENTA) else error(failure)).toLine.toMessage

      case ErrorMessage.Value(value) => primary(value.toString).bold.toLine.toMessage

      case ErrorMessage.Combine(lhs, rhs, spacing) => {
        (lhs.render(isSuccess) :+ (sp * spacing)) ++ rhs.render(isSuccess)
      }

      case ErrorMessage.ThrowableM(throwable) =>
        val stacktrace = throwable.getStackTrace.toIndexedSeq
          .takeWhile(!_.getClassName.startsWith("zio.test.Arrow$"))
          .map(s => LogLine.Line.fromString(s.toString))

        Message((error("ERROR:") + sp + bold(throwable.toString)) +: stacktrace)
    }

}

private[zio] object PrettyPrint {
  def apply(any: Any): String = any match {
    case array: Array[_] => array.mkString("Array(", ", ", ")")
    case other           => other.toString
  }
}
