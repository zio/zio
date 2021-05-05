package zio.test

import zio.test.FailureRenderer.FailureMessage.{Line, Message}
import zio.test.FailureRenderer.{blue, green, red}

import scala.language.implicitConversions

private object MessageDesc {
  val result: MessageDesc                                   = Result
  def choice(success: String, failure: String): MessageDesc = Choice(success, failure)
  def text(string: String): MessageDesc                     = choice(string, string)
  def value[A](value: A): MessageDesc                       = Value(value)

  val is: MessageDesc   = choice("is", "is not")
  val does: MessageDesc = choice("does", "does not")
  val did: MessageDesc  = choice("did", "did not")

  implicit def messageDesc2Render[A](messageDesc: MessageDesc): (A, Boolean) => Message = messageDesc.render

  private final case object Result                                     extends MessageDesc
  private final case class Choice(success: String, failure: String)    extends MessageDesc
  private final case class Value[A](value: A)                          extends MessageDesc
  private final case class Combine(lhs: MessageDesc, rhs: MessageDesc) extends MessageDesc
}

private sealed trait MessageDesc { self =>
  def +(that: MessageDesc): MessageDesc = MessageDesc.Combine(self, that)
  def +(that: String): MessageDesc      = MessageDesc.Combine(self, MessageDesc.text(that))

  def render[A](a: A, isSuccess: Boolean): Message = renderLine(a, isSuccess).toMessage

  private def renderLine[A](a: A, isSuccess: Boolean): Line =
    self match {
      case MessageDesc.Result => blue(a.toString).toLine
      case MessageDesc.Choice(success, failure) =>
        if (isSuccess) green(success).toLine
        else red(failure).toLine
      case MessageDesc.Value(value) => blue(value.toString).toLine
      case MessageDesc.Combine(lhs, rhs) =>
        lhs.renderLine(a, isSuccess) ++ Line.fromString(" ") ++ rhs.renderLine(a, isSuccess)
    }

}
