package zio.concurrent

import com.github.ghik.silencer.silent
import zio.{UIO, ZIO}

import java.util.concurrent.ConcurrentHashMap
import java.util.function.{Consumer, Predicate}
import scala.collection.JavaConverters._

final class ConcurrentSet[A] private (private val underlying: ConcurrentHashMap.KeySetView[A, java.lang.Boolean])
    extends AnyVal {

  def add(x: A): UIO[Boolean] =
    ZIO.succeed(underlying.add(x))

  def addAll(xs: Iterable[A]): UIO[Boolean] =
    ZIO.succeed(underlying.addAll(xs.asJavaCollection): @silent("JavaConverters"))

  def collectFirst[B](pf: PartialFunction[A, B]): UIO[Option[B]] =
    ZIO.succeed {
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

  def exists(p: A => Boolean): UIO[Boolean] =
    ZIO.succeed {
      var result = false
      underlying.forEach {
        makeConsumer { (a: A) =>
          if (!result && p(a))
            result = true
        }
      }
      result
    }

  def fold[R, E, S](zero: S)(f: (S, A) => S): UIO[S] =
    ZIO.succeed {
      var result: S = zero
      underlying.forEach {
        makeConsumer { (a: A) =>
          result = f(result, a)
        }
      }
      result
    }

  def forall(p: A => Boolean): UIO[Boolean] =
    ZIO.succeed {
      var result = true
      underlying.forEach {
        makeConsumer { (a: A) =>
          if (result && !p(a))
            result = false
        }
      }
      result
    }

  def find[B](p: A => Boolean): UIO[Option[A]] =
    ZIO.succeed {
      var result = Option.empty[A]
      underlying.forEach {
        makeConsumer { (a: A) =>
          if (result.isEmpty && p(a))
            result = Some(a)
        }
      }
      result
    }

  def remove(x: A): UIO[Boolean] =
    ZIO.succeed(underlying.remove(x))

  def removeAll(xs: Iterable[A]): UIO[Boolean] =
    ZIO.succeed(underlying.removeAll(xs.asJavaCollection): @silent("JavaConverters"))

  def removeIf(p: A => Boolean): UIO[Boolean] =
    ZIO.succeed(underlying.removeIf(makePredicate(a => !p(a))))

  def retainAll(xs: Iterable[A]): UIO[Boolean] =
    ZIO.succeed(underlying.retainAll(xs.asJavaCollection): @silent("JavaConverters"))

  def retainIf(p: A => Boolean): UIO[Boolean] =
    ZIO.succeed(underlying.removeIf(makePredicate(p)))

  def clear: UIO[Unit] =
    ZIO.succeed(underlying.clear())

  def contains(x: A): UIO[Boolean] =
    ZIO.succeed(underlying.contains(x))

  def containsAll(xs: Iterable[A]): UIO[Boolean] =
    ZIO.succeed(xs.forall(x => underlying.contains(x)))

  def size: UIO[Int] =
    ZIO.succeed(underlying.size())

  def isEmpty: UIO[Boolean] =
    ZIO.succeed(underlying.isEmpty)

  def toSet: UIO[Set[A]] =
    ZIO.succeed(underlying.asScala.toSet: @silent("JavaConverters"))

  @silent("JavaConverters")
  def transform(f: A => A): UIO[Unit] =
    ZIO.succeed {
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

  def empty[A]: UIO[ConcurrentSet[A]] =
    ZIO.succeed {
      val keySetView = ConcurrentHashMap.newKeySet[A]()
      new ConcurrentSet(keySetView)
    }

  def empty[A](initialCapacity: Int): UIO[ConcurrentSet[A]] =
    ZIO.succeed {
      val keySetView = ConcurrentHashMap.newKeySet[A](initialCapacity)
      new ConcurrentSet(keySetView)
    }

  /**
   * Makes a new `ConcurrentSet` initialized with provided collection.
   */
  def fromIterable[A](as: Iterable[A]): UIO[ConcurrentSet[A]] =
    ZIO.succeed {
      val keySetView = ConcurrentHashMap.newKeySet[A]()
      as.foreach(x => keySetView.add(x))
      new ConcurrentSet(keySetView)
    }

  def make[A](as: A*): UIO[ConcurrentSet[A]] =
    ZIO.succeed {
      val keySetView = ConcurrentHashMap.newKeySet[A]()
      as.foreach(x => keySetView.add(x))
      new ConcurrentSet(keySetView)
    }
}
