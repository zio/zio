package zio

trait ManagedAspect[-R, +E] {
  def apply[R1 <: R, E1 >: E, A](managed: ZManaged[R1, E1, A]): ZManaged[R1, E1, A]
}
