package zio.test.diff

private[diff] case class Chunk[T](position: Int, elements: Vector[T]) {

  def size: Int = elements.size

  /**
   * the position just after the end of the chunk
   */
  def endPosition: Int = position + size

  override def toString: String = s"Chunk($position, $elements, $size)"
}
