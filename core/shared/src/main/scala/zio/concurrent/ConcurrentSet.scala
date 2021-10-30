package zio.concurrent

import zio.UIO

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

final class ConcurrentSet[A] private (private val underlying: ConcurrentHashMap.KeySetView[A, java.lang.Boolean])
    extends AnyVal {
  def add(x: A): UIO[Boolean] =
    UIO(underlying.add(x))

  def addAll(xs: Iterable[A]): UIO[Boolean] =
    UIO(underlying.addAll(xs.asJavaCollection))

  def remove(x: A): UIO[Boolean] =
    UIO(underlying.remove(x))

  def clear: UIO[Unit] =
    UIO(underlying.clear())

  def contains(x: A): UIO[Boolean] =
    UIO(underlying.contains(x))

  def containsAll(xs: Iterable[A]): UIO[Boolean] =
    UIO(underlying.containsAll(xs.asJavaCollection))

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
        keySetView.addAll(xs.asJavaCollection)
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
