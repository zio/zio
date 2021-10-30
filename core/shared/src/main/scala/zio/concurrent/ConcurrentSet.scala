package zio.concurrent

import zio.UIO

import java.util.concurrent.ConcurrentHashMap

final class ConcurrentSet[A] private (private val underlying: ConcurrentHashMap.KeySetView[A, java.lang.Boolean])
    extends AnyVal {

  def add(x: A): UIO[Boolean] =
    UIO(underlying.add(x))

  def addAll(xs: Iterable[A]): UIO[Boolean] =
    UIO {
      var added = false
      xs.foreach(x => added = underlying.add(x))
      added
    }

  def remove(x: A): UIO[Boolean] =
    UIO(underlying.remove(x))

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
}

object ConcurrentSet {

  def make[A](xs: A*): UIO[ConcurrentSet[A]] =
    UIO.effectSuspendTotal {
      UIO {
        val keySetView = ConcurrentHashMap.newKeySet[A]()
        xs.foreach(x => keySetView.add(x))
        new ConcurrentSet(keySetView)
      }
    }

  def make[A](size: Int): UIO[ConcurrentSet[A]] =
    UIO.effectSuspendTotal {
      UIO {
        val keySetView = ConcurrentHashMap.newKeySet[A](size)
        new ConcurrentSet(keySetView)
      }
    }
}
