package zio

import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec

final class InMemoryCache[E, K, V] private (private val ref: AtomicReference[Map[K, Promise[E, V]]])
    extends AnyVal
    with Serializable {

  def clear: UIO[Unit] =
    IO.effectTotal(ref.set(Map.empty))

  def get(k: K): UIO[Option[IO[E, V]]] =
    IO.effectTotal(ref.get.get(k).map(_.await))

  def set(k: K, value: IO[E, V]): UIO[Unit] =
    for {
      p <- Promise.make[E, V]
      _ <- p.complete(value).fork
      _ <- IO.effectTotal {
            @tailrec def rec(current: Map[K, Promise[E, V]]): Unit = {
              val updated = current.updated(k, p)
              if (!ref.compareAndSet(current, updated)) rec(ref.get)
            }
            rec(ref.get)
          }
    } yield ()

  def getOrUpdate(k: K)(f: K => IO[E, V]): IO[E, V] =
    for {
      map <- IO.effectTotal(ref.get)
      value <- map.get(k) match {
                case Some(p) => p.await
                case None =>
                  Promise.make[E, V].flatMap { p =>
                    if (ref.compareAndSet(map, map.updated(k, p))) {
                      f(k).to(p).fork *> p.await
                    } else getOrUpdate(k)(f) // someone interfered with our update, so try again
                  }
              }
    } yield value
}

object InMemoryCache extends Serializable {

  final def make[E, K, V]: UIO[InMemoryCache[E, K, V]] =
    IO.effectTotal(new InMemoryCache[E, K, V](new AtomicReference(Map.empty)))
}
