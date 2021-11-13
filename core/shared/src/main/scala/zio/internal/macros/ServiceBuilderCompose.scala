package zio.internal.macros

import zio.internal.macros.ServiceBuilderCompose.{ComposeH, ComposeV, Empty, Value}

sealed abstract class ServiceBuilderCompose[+A] extends Product with Serializable { self =>
  def >>>[A1 >: A](that: ServiceBuilderCompose[A1]): ServiceBuilderCompose[A1] =
    if (self eq Empty) that else if (that eq Empty) self else ComposeV(self, that)

  def ++[A1 >: A](that: ServiceBuilderCompose[A1]): ServiceBuilderCompose[A1] =
    if (self eq Empty) that else if (that eq Empty) self else ComposeH(self, that)

  def fold[B](z: B, value: A => B, composeH: (B, B) => B, composeV: (B, B) => B): B = self match {
    case Empty         => z
    case Value(value0) => value(value0)
    case ComposeH(left, right) =>
      composeH(left.fold(z, value, composeH, composeV), right.fold(z, value, composeH, composeV))
    case ComposeV(left, right) =>
      composeV(left.fold(z, value, composeH, composeV), right.fold(z, value, composeH, composeV))
  }

  def toSet[A1 >: A]: Set[A1] = fold[Set[A1]](Set.empty[A1], Set(_), _ ++ _, _ ++ _)
}

object ServiceBuilderCompose {
  def succeed[A](value: A): ServiceBuilderCompose[A] = Value(value)
  def empty: ServiceBuilderCompose[Nothing]          = Empty

  case object Empty                    extends ServiceBuilderCompose[Nothing]
  final case class Value[+A](value: A) extends ServiceBuilderCompose[A]
  final case class ComposeH[+A](left: ServiceBuilderCompose[A], right: ServiceBuilderCompose[A])
      extends ServiceBuilderCompose[A]
  final case class ComposeV[+A](left: ServiceBuilderCompose[A], right: ServiceBuilderCompose[A])
      extends ServiceBuilderCompose[A]

  implicit final class ServiceBuilderComposeIterableOps[A](private val self: Iterable[ServiceBuilderCompose[A]])
      extends AnyVal {
    def combineHorizontally: ServiceBuilderCompose[A] = self.foldLeft[ServiceBuilderCompose[A]](Empty)(_ ++ _)
  }
}
