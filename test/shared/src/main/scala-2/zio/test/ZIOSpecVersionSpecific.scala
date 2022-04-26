package zio.test

trait ZIOSpecVersionSpecific[R] {
  // SCALA 2

  def suiteAll(name: String)(spec: Any): Spec[Nothing, Nothing] =
    macro SmartSpecMacros.suiteImpl
}
