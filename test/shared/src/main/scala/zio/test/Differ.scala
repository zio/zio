package zio.test

import zio.test.FailureRenderer.FailureMessage.{Fragment, Message}
import zio.test.FailureRenderer.{blue, red}

trait OptionalImplicit[A] {
  def value: Option[A]
}

object OptionalImplicit extends LowPriOptionalImplicit {
  def apply[A: OptionalImplicit]: Option[A] = implicitly[OptionalImplicit[A]].value

  implicit def some[A](implicit instance: A): OptionalImplicit[A] = new OptionalImplicit[A] {
    val value: Option[A] = Some(instance)
  }
}

trait LowPriOptionalImplicit {
  implicit def none[A]: OptionalImplicit[A] = new OptionalImplicit[A] {
    val value: Option[A] = None
  }
}

trait Diff[A, B] {
  def diff(lhs: A, rhs: B): Message
}

object Diff {
  implicit def stringDiff: Diff[String, String] = (lhs: String, rhs: String) =>
    (blue(lhs) + red(" != ") + blue(rhs)).toMessage
}
