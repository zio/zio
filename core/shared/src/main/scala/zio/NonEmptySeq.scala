package zio

/**
 * The operations of an ordered collection that holds at least one element: the
 * [[NonEmptyOps]] operations, plus operations that keep at least one element
 * and so return the non-empty collection type `CC`. [[NonEmptyChunk]]
 * implements it.
 */
trait NonEmptySeq[+A, CC[+_], EC[+_]] extends NonEmptyOps[A, CC, EC] {

  /**
   * Returns a collection with `elem` added at the end.
   */
  def appended[B >: A](elem: B): CC[B]

  /**
   * Returns the result of `pf` for the first element where it is defined, or
   * `None` when it is defined for no element.
   */
  def collectFirst[B](pf: PartialFunction[A, B]): Option[B]

  /**
   * Returns the elements without duplicates, keeping the first occurrence of
   * each in its original position.
   */
  def distinct: CC[A]

  /**
   * Returns a collection with `elem` added at the beginning.
   */
  def prepended[B >: A](elem: B): CC[B]

  /**
   * Returns the elements in reverse order.
   */
  def reverse: CC[A]

  /**
   * Returns the elements sorted by the key that `f` computes, compared with
   * `ord`.
   */
  def sortBy[B](f: A => B)(implicit ord: Ordering[B]): CC[A]

  /**
   * Returns the elements sorted with `ord`.
   */
  def sorted[B >: A](implicit ord: Ordering[B]): CC[B]
}
