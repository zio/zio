package zio

trait ServiceTagVersionSpecific {
  implicit def materialize[A]: ServiceTag[A] =
    macro zio.internal.macros.InternalMacros.materializeServiceTag[A]
}
