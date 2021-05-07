package zio.test

import zio.test.FailureRenderer.FailureMessage.{Line, Message}
import zio.test.FailureRenderer.{blue, magenta, red}

import scala.language.implicitConversions

object MessageDesc {

  val result: MessageDesc[Any]                                   = Result(_.toString)
  def result[A](render: A => String): MessageDesc[A]             = Result(render)
  def choice(success: String, failure: String): MessageDesc[Any] = Choice(success, failure)
  def text(string: String): MessageDesc[Any]                     = choice(string, string)
  def value(value: Any): MessageDesc[Any]                        = Value(value)

  val is: MessageDesc[Any]    = choice("is", "is not")
  val does: MessageDesc[Any]  = choice("does", "does not")
  val did: MessageDesc[Any]   = choice("did", "did not")
  val was: MessageDesc[Any]   = choice("was", "was not")
  val valid: MessageDesc[Any] = choice("Valid", "Invalid")

  implicit def messageDesc2Render[A](messageDesc: MessageDesc[A]): (A, Boolean) => Message = messageDesc.render

  private final case class Result[-A](render: A => String)                       extends MessageDesc[A]
  private final case class Choice[-A](success: String, failure: String)          extends MessageDesc[A]
  private final case class Value(value: Any)                                     extends MessageDesc[Any]
  private final case class Combine[-A](lhs: MessageDesc[A], rhs: MessageDesc[A]) extends MessageDesc[A]
}

sealed trait MessageDesc[-A] { self =>
  def +[A1 <: A](that: MessageDesc[A1]): MessageDesc[A1] = MessageDesc.Combine(self, that)
  def +(that: String): MessageDesc[A]                    = MessageDesc.Combine(self, MessageDesc.text(that))

  def render(a: A, isSuccess: Boolean): Message = renderLine(a, isSuccess).toMessage

  private def renderLine(a: A, isSuccess: Boolean): Line =
    self match {
      case MessageDesc.Result(f) => blue(f(a)).toLine
      case MessageDesc.Choice(success, failure) =>
        if (isSuccess) magenta(success).toLine
        else red(failure).toLine
      case MessageDesc.Value(value) => blue(value.toString).toLine
      case MessageDesc.Combine(lhs, rhs) =>
        lhs.renderLine(a, isSuccess) ++ Line.fromString(" ") ++ rhs.renderLine(a, isSuccess)
    }

}
