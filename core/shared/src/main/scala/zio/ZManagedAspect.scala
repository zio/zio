package zio

trait ZManagedAspect[+LowerR, -UpperR, +LowerE, -UpperE, +LowerA, -UpperA] { self =>

  def apply[R >: LowerR <: UpperR, E >: LowerE <: UpperE, A >: LowerA <: UpperA](
    managed: ZManaged[R, E, A]
  ): ZManaged[R, E, A]

  def >>>[
    LowerR1 >: LowerR,
    UpperR1 <: UpperR,
    LowerE1 >: LowerE,
    UpperE1 <: UpperE,
    LowerA1 >: LowerA,
    UpperA1 <: UpperA
  ](
    that: ZManagedAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1]
  ): ZManagedAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1] =
    self.andThen(that)

  def andThen[
    LowerR1 >: LowerR,
    UpperR1 <: UpperR,
    LowerE1 >: LowerE,
    UpperE1 <: UpperE,
    LowerA1 >: LowerA,
    UpperA1 <: UpperA
  ](
    that: ZManagedAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1]
  ): ZManagedAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1] =
    new ZManagedAspect[LowerR1, UpperR1, LowerE1, UpperE1, LowerA1, UpperA1] {
      def apply[R >: LowerR1 <: UpperR1, E >: LowerE1 <: UpperE1, A >: LowerA1 <: UpperA1](
        managed: ZManaged[R, E, A]
      ): ZManaged[R, E, A] =
        that(self(managed))
    }
}
