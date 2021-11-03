package zio.test

final case class SmartAssertion[+A]()

private case class SmartAssertionExtensionError() extends Throwable {
  override def getMessage: String =
    s"This method can only be called inside of `assertTrue`"
}
