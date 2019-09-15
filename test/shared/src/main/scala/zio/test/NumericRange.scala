package zio.test

case class NumericRange[A: Numeric](base: A, offset: A) {
  def isInRange(reference: A): Boolean = {
    val referenceType = implicitly[Numeric[A]]
    val max           = referenceType.plus(base, offset)
    val min           = referenceType.minus(base, offset)

    referenceType.gteq(reference, min) && referenceType.lteq(reference, max)
  }
}
