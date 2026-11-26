package zio

import scala.reflect.ClassTag

/**
 * The operations of a collection that holds at least one element. `CC` is the
 * non-empty collection type, which operations that keep at least one element
 * return, and `EC` is the possibly empty collection type, which operations that
 * can drop elements return. [[NonEmptyChunk]] implements it with
 * `NonEmptyChunk` as `CC` and `Chunk` as `EC`.
 */
trait NonEmptyOps[+A, CC[+_], EC[+_]] {

  /**
   * Returns the results of `pf` for the elements where it is defined, in a
   * possibly empty collection.
   */
  def collect[B](pf: PartialFunction[A, B]): EC[B]

  /**
   * Returns `true` when `p` holds for at least one element.
   */
  def exists(p: A => Boolean): Boolean

  /**
   * Returns the elements that satisfy `p`, in a possibly empty collection.
   */
  def filter(p: A => Boolean): EC[A]

  /**
   * Returns the elements that do not satisfy `p`, in a possibly empty
   * collection.
   */
  def filterNot(p: A => Boolean): EC[A]

  /**
   * Returns the first element that satisfies `p`, or `None` when no element
   * does.
   */
  def find(p: A => Boolean): Option[A]

  /**
   * Combines the elements from left to right with `op`, starting from `z`.
   */
  def foldLeft[B](z: B)(op: (B, A) => B): B

  /**
   * Returns `true` when `p` holds for every element.
   */
  def forall(p: A => Boolean): Boolean

  /**
   * Splits the elements into non-empty groups of `size` elements, in order. The
   * last group holds the remaining elements and can be smaller.
   */
  def grouped(size: Int): Iterator[CC[A]]

  /**
   * Returns the first element.
   */
  def head: A

  /**
   * Returns every element except the last, in a possibly empty collection.
   */
  def init: EC[A]

  /**
   * Returns an iterator over the elements.
   */
  def iterator: Iterator[A]

  /**
   * Returns the last element.
   */
  def last: A

  /**
   * Returns a non-empty collection with `f` applied to each element.
   */
  def map[B](f: A => B): CC[B]

  /**
   * Combines the elements with `op`. A collection with one element returns that
   * element without calling `op`.
   */
  def reduce[B >: A](op: (B, B) => B): B

  /**
   * Returns the number of elements. The default implementation counts the
   * elements of `toIterable`.
   */
  def size: Int = toIterable.size

  /**
   * Returns every element except the first, in a possibly empty collection.
   */
  def tail: EC[A]

  /**
   * Returns an array of the elements. The default implementation builds it from
   * `toIterable`.
   */
  def toArray[B >: A: ClassTag]: Array[B] = toIterable.toArray

  /**
   * Returns the elements as an `Iterable`. The default implementation builds a
   * `List` from `iterator`.
   */
  def toIterable: Iterable[A] = iterator.toList

  /**
   * Returns the elements as a `List`.
   */
  def toList: List[A] = toIterable.toList

  /**
   * Pairs the elements with the elements of `that` by position, keeping as many
   * pairs as the shorter collection has elements. The `Zippable` instance
   * combines each pair.
   */
  def zip[B](that: CC[B])(implicit zippable: Zippable[A, B]): CC[zippable.Out]

  /**
   * Pairs each element with its index, starting from 0.
   */
  def zipWithIndex: CC[(A, Int)]
}
