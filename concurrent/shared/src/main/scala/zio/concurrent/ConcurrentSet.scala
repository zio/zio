package zio.concurrent

import com.github.ghik.silencer.silent
import zio.UIO

import java.util.concurrent.ConcurrentHashMap
import java.util.function.{Consumer, Predicate}
import scala.collection.JavaConverters._

/**
 * A `ConcurrentSet` is a Set wrapper over
 * `java.util.concurrent.ConcurrentHashMap`.
 */
final class ConcurrentSet[A] private (private val underlying: ConcurrentHashMap.KeySetView[A, java.lang.Boolean])
    extends AnyVal {

  /**
   * Adds a new value.
   */
  def add(x: A): UIO[Boolean] =
    UIO(underlying.add(x))

  /**
   * Adds all new values.
   */
  def addAll(xs: Iterable[A]): UIO[Boolean] =
    UIO(underlying.addAll(xs.asJavaCollection): @silent("JavaConverters"))

  /**
   * Removes all elements.
   */
  def clear: UIO[Unit] =
    UIO(underlying.clear())

  /**
   * Finds the first element of a set for which the partial function is defined
   * and applies the function to it.
   */
  def collectFirst[B](pf: PartialFunction[A, B]): UIO[Option[B]] =
    UIO {
      var result = Option.empty[B]
      underlying.forEach {
        makeConsumer { (a: A) =>
          if (result.isEmpty && pf.isDefinedAt(a)) {
            result = Some(pf(a))
          }
        }
      }
      result
    }

  /**
   * Tests whether if the element is in the set.
   */
  def contains(x: A): UIO[Boolean] =
    UIO(underlying.contains(x))

  /**
   * Tests if the elements in the collection are a subset of the set.
   */
  def containsAll(xs: Iterable[A]): UIO[Boolean] =
    UIO(xs.forall(x => underlying.contains(x)))

  /**
   * Tests whether a given predicate holds true for at least one element in the
   * set.
   */
  def exists(p: A => Boolean): UIO[Boolean] =
    UIO {
      var result = false
      underlying.forEach {
        makeConsumer { (a: A) =>
          if (!result && p(a))
            result = true
        }
      }
      result
    }

  /**
   * Retrieves the elements in which predicate is satisfied.
   */
  def find[B](p: A => Boolean): UIO[Option[A]] =
    UIO {
      var result = Option.empty[A]
      underlying.forEach {
        makeConsumer { (a: A) =>
          if (result.isEmpty && p(a))
            result = Some(a)
        }
      }
      result
    }

  /**
   * Folds the elements of a set using the given binary operator.
   */
  def fold[R, E, S](zero: S)(f: (S, A) => S): UIO[S] =
    UIO {
      var result: S = zero
      underlying.forEach {
        makeConsumer { (a: A) =>
          result = f(result, a)
        }
      }
      result
    }

  /**
   * Tests whether a predicate is satisfied by all elements of a set.
   */
  def forall(p: A => Boolean): UIO[Boolean] =
    UIO {
      var result = true
      underlying.forEach {
        makeConsumer { (a: A) =>
          if (result && !p(a))
            result = false
        }
      }
      result
    }

  /**
   * True if there are no elements in the set.
   */
  def isEmpty: UIO[Boolean] =
    UIO(underlying.isEmpty)

  /**
   * Removes the entry for the given value if it is mapped to an existing
   * element.
   */
  def remove(x: A): UIO[Boolean] =
    UIO(underlying.remove(x))

  /**
   * Removes all the entries for the given values if they are mapped to an
   * existing element.
   */
  def removeAll(xs: Iterable[A]): UIO[Boolean] =
    UIO(underlying.removeAll(xs.asJavaCollection): @silent("JavaConverters"))

  /**
   * Removes all elements which satisfy the given predicate.
   */
  def removeIf(p: A => Boolean): UIO[Boolean] =
    UIO(underlying.removeIf(makePredicate(a => !p(a))))

  /**
   * Retain all the entries for the given values if they are mapped to an
   * existing element.
   */
  def retainAll(xs: Iterable[A]): UIO[Boolean] =
    UIO(underlying.retainAll(xs.asJavaCollection): @silent("JavaConverters"))

  /**
   * Removes all elements which do not satisfy the given predicate.
   */
  def retainIf(p: A => Boolean): UIO[Boolean] =
    UIO(underlying.removeIf(makePredicate(p)))

  /**
   * Number of elements in the set.
   */
  def size: UIO[Int] =
    UIO(underlying.size())

  /**
   * Create a concurrent set from a set.
   */
  def toSet: UIO[Set[A]] =
    UIO(underlying.asScala.toSet: @silent("JavaConverters"))

  /**
   * Create a concurrent set from a collection.
   */
  @silent("JavaConverters")
  def transform(f: A => A): UIO[Unit] =
    UIO {
      val set = underlying.asScala.toSet
      underlying.removeAll(set.asJavaCollection)
      underlying.addAll(set.map(f).asJavaCollection)
      ()
    }

  private def makeConsumer(f: A => Unit): Consumer[A] =
    new Consumer[A] {
      override def accept(t: A): Unit = f(t)
    }

  private def makePredicate(f: A => Boolean): Predicate[A] =
    new Predicate[A] {
      def test(a: A): Boolean = f(a)
    }
}

object ConcurrentSet {

  /**
   * Makes an empty ConcurrentSet
   */
  def empty[A]: UIO[ConcurrentSet[A]] =
    UIO {
      val keySetView = ConcurrentHashMap.newKeySet[A]()
      new ConcurrentSet(keySetView)
    }

  /**
   * Makes an empty ConcurrentSet with initial capacity
   */
  def empty[A](initialCapacity: Int): UIO[ConcurrentSet[A]] =
    UIO {
      val keySetView = ConcurrentHashMap.newKeySet[A](initialCapacity)
      new ConcurrentSet(keySetView)
    }

  /**
   * Makes a new `ConcurrentSet` initialized with provided collection.
   */
  def fromIterable[A](as: Iterable[A]): UIO[ConcurrentSet[A]] =
    UIO {
      val keySetView = ConcurrentHashMap.newKeySet[A]()
      as.foreach(x => keySetView.add(x))
      new ConcurrentSet(keySetView)
    }

  /**
   * Makes a new ConcurrentSet initialized with the provided elements
   */
  def make[A](as: A*): UIO[ConcurrentSet[A]] =
    UIO {
      val keySetView = ConcurrentHashMap.newKeySet[A]()
      as.foreach(x => keySetView.add(x))
      new ConcurrentSet(keySetView)
    }
}
