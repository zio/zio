package zio.test

import scala.language.implicitConversions

final case class SmartAssertion[A](result: () => Either[String, A], error: String) {
  def run: A =
    throw new Error("This must only be called within `assertTrue`")
}

object SmartAssertion {
  def cond(predicate: => Boolean, error: String): SmartAssertion[Boolean] =
    SmartAssertion(() => Right(predicate), error)

  def fromEither[A](result: => Either[String, A]): SmartAssertion[A] =
    SmartAssertion(() => result, "")

  implicit def smartAssertion2Value[A](smartAssertion: SmartAssertion[A]): A =
    throw new Error("This must only be called within `assertTrue`")
}
