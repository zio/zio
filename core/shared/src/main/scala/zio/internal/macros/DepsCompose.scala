package zio.internal.macros

import zio.internal.macros.DepsCompose.{ComposeH, ComposeV, Empty, Value}

sealed abstract class DepsCompose[+A] extends Product with Serializable { self =>
  def >>>[A1 >: A](that: DepsCompose[A1]): DepsCompose[A1] =
    if (self eq Empty) that else if (that eq Empty) self else ComposeV(self, that)

  def ++[A1 >: A](that: DepsCompose[A1]): DepsCompose[A1] =
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

object DepsCompose {
  def succeed[A](value: A): DepsCompose[A] = Value(value)
  def empty: DepsCompose[Nothing]          = Empty

  case object Empty                                                          extends DepsCompose[Nothing]
  final case class Value[+A](value: A)                                       extends DepsCompose[A]
  final case class ComposeH[+A](left: DepsCompose[A], right: DepsCompose[A]) extends DepsCompose[A]
  final case class ComposeV[+A](left: DepsCompose[A], right: DepsCompose[A]) extends DepsCompose[A]

  implicit final class DepsComposeIterableOps[A](private val self: Iterable[DepsCompose[A]]) extends AnyVal {
    def combineHorizontally: DepsCompose[A] = self.foldLeft[DepsCompose[A]](Empty)(_ ++ _)
  }
}
