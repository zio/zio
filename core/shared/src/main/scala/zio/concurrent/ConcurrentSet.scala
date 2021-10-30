package zio.concurrent

import zio.{UIO, ZIO}

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate
import collection.JavaConverters._
import scala.collection.mutable

final class ConcurrentSet[A] private (private val underlying: ConcurrentHashMap.KeySetView[A, java.lang.Boolean])
    extends AnyVal {

  def add(x: A): UIO[Boolean] =
    UIO(underlying.add(x))

  def addAll(xs: Iterable[A]): UIO[Boolean] =
    UIO(underlying.addAll(xs.asJavaCollection))

  def collectFirst[B](pf: PartialFunction[A, B]): UIO[Option[B]] =
    UIO(underlying.asScala.toSet.collectFirst(pf))

  def exists(p: A => Boolean): UIO[Boolean] =
    UIO(underlying.asScala.toSet.exists(p))

  def fold[R, E, S](zero: S)(f: (S, A) => S): UIO[S] =
    UIO(underlying.asScala.toSet.foldLeft(zero)(f))

  def foldZIO[R, E, S](zero: S)(f: (S, A) => ZIO[R, E, S]): ZIO[R, E, S] =
    ZIO.foldLeft(underlying.asScala.toSet)(zero)(f)

  def foreach[R, E, B](f: A => ZIO[R, E, B]): ZIO[R, E, Set[B]] =
    ZIO.foreach(underlying.asScala.toSet)(f)

  def foreach_[R, E](f: A => ZIO[R, E, Any]): ZIO[R, E, Unit] =
    ZIO.foreach_(underlying.asScala.toSet)(f)

  def forall(p: A => Boolean): UIO[Boolean] =
    UIO(underlying.asScala.toSet.forall(p))

  def find[B](p: A => Boolean): UIO[Option[A]] =
    UIO(underlying.asScala.toSet.find(p))

  def remove(x: A): UIO[Boolean] =
    UIO(underlying.remove(x))

  def removeAll(xs: Iterable[A]): UIO[Boolean] =
    UIO(underlying.removeAll(xs.asJavaCollection))

  def removeIf(p: A => Boolean): UIO[Boolean] =
    UIO(underlying.removeIf((t: A) => !p(t)))

  def retainAll(xs: Iterable[A]): UIO[Boolean] =
    UIO(underlying.retainAll(xs.asJavaCollection))

  def retainIf(p: A => Boolean): UIO[Boolean] =
    UIO(underlying.removeIf((t: A) => p(t)))

  def clear: UIO[Unit] =
    UIO(underlying.clear())

  def contains(x: A): UIO[Boolean] =
    UIO(underlying.contains(x))

  def containsAll(xs: Iterable[A]): UIO[Boolean] =
    UIO(xs.forall(x => underlying.contains(x)))

  def size: UIO[Int] =
    UIO(underlying.size())

  def isEmpty: UIO[Boolean] =
    UIO(underlying.isEmpty)

  def toSet: UIO[Set[A]] =
    UIO(underlying.asScala.toSet)

  def transform(f: A => A): UIO[Unit] = UIO {
    val set = underlying.asScala.toSet
    underlying.removeAll(set.asJavaCollection)
    underlying.addAll(set.map(f).asJavaCollection)
  }
}

object ConcurrentSet {

  def empty[A]: UIO[ConcurrentSet[A]] =
    UIO {
      val keySetView = ConcurrentHashMap.newKeySet[A]()
      new ConcurrentSet(keySetView)
    }

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

  def make[A](as: A*): UIO[ConcurrentSet[A]] =
    UIO {
      val keySetView = ConcurrentHashMap.newKeySet[A]()
      as.foreach(x => keySetView.add(x))
      new ConcurrentSet(keySetView)
    }
}
