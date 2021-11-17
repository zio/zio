package zio

trait EnvironmentTagVersionSpecific {
  implicit def materialize[A]: EnvironmentTag[A] =
    macro zio.internal.macros.InternalMacros.materializeEnvironmentTag[A]
}
