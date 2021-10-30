package zio.concurrent

import zio.UIO

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

final class KeySetView[A](val inner: ConcurrentHashMap.KeySetView[A, java.lang.Boolean]) extends AnyVal

final class ConcurrentSet[A] private (keySetView: KeySetView[A]) {
  def add(x: A): UIO[Boolean] =
    UIO(keySetView.inner.add(x))

  def addAll(xs: Iterable[A]): UIO[Boolean] =
    UIO(keySetView.inner.addAll(xs.asJavaCollection))

  def remove(x: A): UIO[Boolean] =
    UIO(keySetView.inner.remove(x))

  def clear: UIO[Unit] =
    UIO(keySetView.inner.clear())

  def contains(x: A): UIO[Boolean] =
    UIO(keySetView.inner.contains(x))

  def containsAll(xs: Iterable[A]): UIO[Boolean] =
    UIO(keySetView.inner.containsAll(xs.asJavaCollection))
}

object ConcurrentSet {

  def make[A](xs: A*): UIO[ConcurrentSet[A]] =
    UIO.effectSuspendTotal {
      UIO {
        val keySetView = new KeySetView(ConcurrentHashMap.newKeySet[A]())
        keySetView.inner.addAll(xs.asJavaCollection)
        new ConcurrentSet(keySetView)
      }
    }

  def make[A](size: Int): UIO[ConcurrentSet[A]] =
    UIO.effectSuspendTotal {
      UIO {
        val keySetView = new KeySetView(ConcurrentHashMap.newKeySet[A](size))
        new ConcurrentSet(keySetView)
      }
    }
}
