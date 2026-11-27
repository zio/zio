package zio.test

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.render.LogLine.{Fragment, Message}
import zio.test.render._

import scala.io.AnsiColor

/**
 * Constructors of [[ErrorMessage]] values.
 */
object ErrorMessage {

  /**
   * A message that reads `success` when the step succeeded and `failure` when
   * it failed.
   */
  def choice(success: String, failure: String): ErrorMessage = Choice(success, failure)
  /**
   * A message that reads `string` whether the step succeeded or failed.
   */
  def custom(string: String): ErrorMessage                   = Custom(string)
  /**
   * A message that shows `value` formatted with `PrettyPrint`.
   */
  def pretty(value: Any): ErrorMessage                       = Value(PrettyPrint(value))
  /**
   * A message that reads `string` whether the step succeeded or failed; the
   * same as `choice(string, string)`.
   */
  def text(string: String): ErrorMessage                     = choice(string, string)
  /**
   * A message that shows `throwable` and its stack trace.
   */
  def throwable(throwable: Throwable): ErrorMessage          = ThrowableZIO(throwable)
  /**
   * A message that shows `value` with its `toString`.
   */
  def value(value: Any): ErrorMessage                        = Value(value)

  /**
   * Reads "did" when the step succeeded and "did not" when it failed.
   */
  val did: ErrorMessage    = choice("did", "did not")
  /**
   * Reads "was equal to" when the step succeeded and "was not equal to" when it
   * failed.
   */
  val equals: ErrorMessage = choice("was equal to", "was not equal to")
  /**
   * Reads "was" when the step succeeded and "was not" when it failed.
   */
  val was: ErrorMessage    = choice("was", "was not")
  /**
   * Reads "had" when the step succeeded and "did not have" when it failed.
   */
  val had: ErrorMessage    = choice("had", "did not have")

  private final case class Choice(success: String, failure: String)                        extends ErrorMessage
  private final case class Combine(lhs: ErrorMessage, rhs: ErrorMessage, spacing: Int = 1) extends ErrorMessage
  private final case class CombineMessage(lhs: ErrorMessage, rhs: ErrorMessage)            extends ErrorMessage
  private final case class Custom(string: String)                                          extends ErrorMessage
  private final case class ThrowableZIO(throwable: Throwable)                              extends ErrorMessage
  private final case class Value(value: Any)                                               extends ErrorMessage
}

/**
 * The message that a step of an assertion reports, built from text, values and
 * words that depend on whether the step succeeded. Combine parts with `+` and
 * `++`.
 */
sealed trait ErrorMessage { self =>
  /**
   * Appends `that` as text to this message.
   */
  def +(that: String): ErrorMessage        = ErrorMessage.Combine(self, ErrorMessage.text(that))
  /**
   * Appends `that` to this message.
   */
  def +(that: ErrorMessage): ErrorMessage  = ErrorMessage.Combine(self, that)
  /**
   * Appends `that` to this message as a separate part. Unlike `+`, the two
   * parts are not joined into one.
   */
  def ++(that: ErrorMessage): ErrorMessage = ErrorMessage.CombineMessage(self, that)

  private[test] def render(isSuccess: Boolean): Message =
    self match {
      case ErrorMessage.Custom(custom) =>
        Message(custom.split("\n").toIndexedSeq.map(Fragment(_).toLine))

      case ErrorMessage.Choice(success, failure) =>
        (if (isSuccess) fr(success).ansi(AnsiColor.MAGENTA) else error(failure)).toLine.toMessage

      case ErrorMessage.Value(value) =>
        Message(value.toString.split("\n").toIndexedSeq.map(primary(_).bold.toLine))

      case ErrorMessage.Combine(lhs, rhs, spacing) =>
        (lhs.render(isSuccess) + (sp * spacing)) +++ rhs.render(isSuccess)

      case ErrorMessage.CombineMessage(lhs, rhs) =>
        lhs.render(isSuccess) ++ rhs.render(isSuccess)

      case ErrorMessage.ThrowableZIO(throwable) =>
        val stacktrace = throwable.getStackTrace.toIndexedSeq
          .takeWhile(!_.getClassName.startsWith("zio.test.Arrow$"))
          .map(s => LogLine.Line.fromString(s.toString))

        Message((error("ERROR:") + sp + bold(throwable.toString)) +: stacktrace)
    }

}
