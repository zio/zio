package zio.internal.macros

import zio.internal.macros.ProviderCompose.{ComposeH, ComposeV, Empty, Value}

sealed abstract class ProviderCompose[+A] extends Product with Serializable { self =>
  def >>>[A1 >: A](that: ProviderCompose[A1]): ProviderCompose[A1] =
    if (self eq Empty) that else if (that eq Empty) self else ComposeV(self, that)

  def ++[A1 >: A](that: ProviderCompose[A1]): ProviderCompose[A1] =
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

object ProviderCompose {
  def succeed[A](value: A): ProviderCompose[A] = Value(value)
  def empty: ProviderCompose[Nothing]          = Empty

  case object Empty                                                                  extends ProviderCompose[Nothing]
  final case class Value[+A](value: A)                                               extends ProviderCompose[A]
  final case class ComposeH[+A](left: ProviderCompose[A], right: ProviderCompose[A]) extends ProviderCompose[A]
  final case class ComposeV[+A](left: ProviderCompose[A], right: ProviderCompose[A]) extends ProviderCompose[A]

  implicit final class ProviderComposeIterableOps[A](private val self: Iterable[ProviderCompose[A]]) extends AnyVal {
    def combineHorizontally: ProviderCompose[A] = self.foldLeft[ProviderCompose[A]](Empty)(_ ++ _)
  }
}
