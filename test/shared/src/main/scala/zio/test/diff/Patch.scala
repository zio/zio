package zio.test.diff

private[diff] case class Patch[T](deltas: Vector[Delta[T]] = Vector.empty) {
  def addDelta(delta: Delta[T]): Patch[T] = copy(deltas = deltas :+ delta)

  def deltasSorted: Vector[Delta[T]] =
    deltas.sortBy(_.original.position)
}
