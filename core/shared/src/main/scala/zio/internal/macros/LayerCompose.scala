package zio.internal.macros

import zio.internal.macros.LayerCompose.{ComposeH, ComposeV, Empty, Value}

sealed trait LayerCompose[+A] { self =>
  def >>>[A1 >: A](that: LayerCompose[A1]): LayerCompose[A1] =
    if (self eq Empty) that else if (that eq Empty) self else ComposeV(self, that)

  def ++[A1 >: A](that: LayerCompose[A1]): LayerCompose[A1] =
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

object LayerCompose {
  def succeed[A](value: A): LayerCompose[A] = Value(value)
  def empty: LayerCompose[Nothing]          = Empty

  case object Empty                                                            extends LayerCompose[Nothing]
  final case class Value[+A](value: A)                                         extends LayerCompose[A]
  final case class ComposeH[+A](left: LayerCompose[A], right: LayerCompose[A]) extends LayerCompose[A]
  final case class ComposeV[+A](left: LayerCompose[A], right: LayerCompose[A]) extends LayerCompose[A]

  implicit final class LayerComposeIterableOps[A](private val self: Iterable[LayerCompose[A]]) extends AnyVal {
    def combineHorizontally: LayerCompose[A] = self.foldLeft[LayerCompose[A]](Empty)(_ ++ _)
  }
}
