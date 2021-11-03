package zio.test

final class CustomAssertion[A, B] private[test] (val run: A => Either[String, B])

object CustomAssertion {
  def cond[A](p: A => Boolean, error: String): CustomAssertion[A, A] =
    make[A] { a =>
      if (p(a)) Left(error)
      else Right(a)
    }

  def make[A]: MakePartiallyApplied[A] =
    new MakePartiallyApplied[A]

  final class MakePartiallyApplied[A] {
    def apply[B](run: A => Either[String, B]): CustomAssertion[A, B] =
      new CustomAssertion[A, B](run)
  }

  def fromPartialFunction[A](message: String): FromPartialFunctionPartiallyApplied[A] =
    new FromPartialFunctionPartiallyApplied[A](message)

  final class FromPartialFunctionPartiallyApplied[A](message: String) {
    def apply[B](run: PartialFunction[A, B]): CustomAssertion[A, B] = {
      val run0 = run
      new CustomAssertion[A, B](a =>
        if (run0.isDefinedAt(a)) Right(run0(a))
        else Left(message)
      )
    }
  }
}

final case class SmartAssertion[+A]()

private case class SmartAssertionExtensionError() extends Throwable {
  override def getMessage: String =
    s"This method can only be called inside of `assertTrue`"
}
