package zio

trait ZManagedAspect[+LowerR, -UpperR, +LowerE, -UpperE, +LowerA, -UpperA] {
  def apply[R >: LowerR <: UpperR, E >: LowerE <: UpperE, A >: LowerA <: UpperA](
    managed: ZManaged[R, E, A]
  ): ZManaged[R, E, A]
}
