package zio.internal.macros

trait LayerLike[A] {
  def composeH(lhs: A, rhs: A): A
  def composeV(lhs: A, rhs: A): A
}

object LayerLike {
  def apply[A: LayerLike]: LayerLike[A] = implicitly[LayerLike[A]]

  implicit final class LayerLikeOps[A: LayerLike](val self: A) {
    def >>>(that: A): A = LayerLike[A].composeV(self, that)
  }

  implicit final class LayerLikeIterableOps[A: LayerLike](val self: Iterable[A]) {
    def combineHorizontally: A = self.reduce(LayerLike[A].composeH)
  }
}
