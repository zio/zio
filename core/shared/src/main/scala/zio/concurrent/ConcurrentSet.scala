package zio.concurrent

import zio.UIO

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

final class ConcurrentSet[A] private (underlying: ConcurrentHashMap.KeySetView[A, java.lang.Boolean]) {
  def add(x: A): UIO[Boolean] =
    UIO(underlying.add(x))

  def addAll(xs: Iterable[A]): UIO[Boolean] =
    UIO(underlying.addAll(xs.asJavaCollection))

  def clear: UIO[Unit] =
    UIO(underlying.clear())

  def contains(x: A): UIO[Boolean] =
    UIO(underlying.contains(x))

  def containsAll(xs: Iterable[A]): UIO[Boolean] =
    UIO(underlying.containsAll(xs.asJavaCollection))
}

object ConcurrentSet {

  def make[A](xs: A*): UIO[ConcurrentSet[A]] =
    UIO(ConcurrentHashMap.newKeySet[A]()).flatMap { kvs =>
      UIO.effectSuspendTotal {
        UIO {
          kvs.addAll(xs.asJavaCollection)
          new ConcurrentSet(kvs)
        }
      }
    }

  def make[A](size: Int): UIO[ConcurrentSet[A]] =
    UIO(ConcurrentHashMap.newKeySet[A](size)).map(new ConcurrentSet(_))
}
