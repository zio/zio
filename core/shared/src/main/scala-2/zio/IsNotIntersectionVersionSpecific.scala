package zio

trait IsNotIntersectionVersionSpecific {
  implicit def materialize[A]: IsNotIntersection[A] =
    macro zio.internal.macros.InternalMacros.materializeIsNotIntersection[A]
}

trait ServiceTagVersionSpecific {
  implicit def materialize[A]: ServiceTag[A] =
    macro zio.internal.macros.InternalMacros.materializeServiceTag[A]
}
