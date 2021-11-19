package zio

trait IsNotIntersectionVersionSpecific {
  implicit def materialize[A]: IsNotIntersection[A] =
    macro zio.internal.macros.InternalMacros.materializeIsNotIntersection[A]
}
