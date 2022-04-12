package zio

trait ZIOAppVersionSpecific {

  type Environment

  /**
   * This implicit conversion macro will ensure that the provided ZIO effect
   * does not require more than the provided environment.
   *
   * If it is missing requirements, it will report a descriptive error message.
   * Otherwise, the effect will be returned unmodified.
   */
  implicit def validateEnv[R, E, A](zio: ZIO[R, E, A]): ZIO[Environment with ZIOAppArgs with Scope, E, A] =
    macro internal.macros.LayerMacros.validate[Environment with ZIOAppArgs with Scope, R]

}
