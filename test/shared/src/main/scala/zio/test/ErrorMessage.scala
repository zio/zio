package zio.test

import zio.test.ConsoleUtils._

object ErrorMessage {

  def throwable(throwable: Throwable): ErrorMessage = ThrowableM(throwable)

  def choice(success: String, failure: String): ErrorMessage = Choice(success, failure)
  def text(string: String): ErrorMessage                     = choice(string, string)
  def value(value: Any): ErrorMessage                        = Value(value)

  val equals: ErrorMessage = choice("is equal to", "is not equal to")
  val is: ErrorMessage     = choice("is", "is not")
  val does: ErrorMessage   = choice("does", "does not")
  val did: ErrorMessage    = choice("did", "did not")
  val was: ErrorMessage    = choice("was", "was not")
  val valid: ErrorMessage  = choice("Valid", "Invalid")

  private final case class Value(value: Any)                             extends ErrorMessage
  private final case class ThrowableM(throwable: Throwable)              extends ErrorMessage
  private final case class Choice(success: String, failure: String)      extends ErrorMessage
  private final case class Combine(lhs: ErrorMessage, rhs: ErrorMessage) extends ErrorMessage
}

sealed trait ErrorMessage { self =>
  def +(that: String): ErrorMessage       = ErrorMessage.Combine(self, ErrorMessage.text(that))
  def +(that: ErrorMessage): ErrorMessage = ErrorMessage.Combine(self, that)

  private[test] def render(isSuccess: Boolean): String =
    self match {
      case ErrorMessage.Choice(success, failure) =>
        if (isSuccess) magenta(success) else red(failure)

      case ErrorMessage.Value(value) => blue(value.toString)

      case ErrorMessage.Combine(lhs, rhs) =>
        lhs.render(isSuccess) + " " + rhs.render(isSuccess)

      case ErrorMessage.ThrowableM(throwable) =>
        (red("ERROR: ") + bold(throwable.toString)) + "\n" +
          throwable.getStackTrace.toIndexedSeq
            .takeWhile(!_.getClassName.startsWith("zio.test.Arrow$"))
            .map(dim("› ") + _)
            .mkString("\n")

    }
}
