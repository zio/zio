/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
 * All rights reserved.
 */

package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import scala.annotation.tailrec
import scala.collection.immutable.{Queue => ScalaQueue}

sealed trait Semaphore extends Serializable {
  def available(implicit trace: Trace): UIO[Long]
  def awaiting(implicit trace: Trace): UIO[Long]
  def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]
  def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit]
  def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]
  def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit]
}

object Semaphore {
  def make(permits: => Long)(implicit trace: Trace): UIO[Semaphore] =
    ZIO.succeed(unsafe.make(permits)(Unsafe.unsafe))

  object unsafe {
    def make(permits: Long)(implicit unsafe: Unsafe): Semaphore =
      new Semaphore {
        val ref = Ref.unsafe.make[Either[ScalaQueue[(Promise[Nothing, Unit], Long)], Long]](Right(permits))

        def available(implicit trace: Trace): UIO[Long] =
          ref.get.map {
            case Left(_)  => 0L
            case Right(p) => p
          }

        def awaiting(implicit trace: Trace): UIO[Long] =
          ref.get.map {
            case Left(q)  => q.size.toLong
            case Right(_) => 0L
          }

        def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
          withPermits(1L)(zio)

        def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
          withPermitsScoped(1L)

        def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
          ZIO.uninterruptibleMask { restore =>
            reserve(n).flatMap { res =>
              restore(res.acquire *> zio).ensuring(res.release)
            }
          }

        def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
          ZIO.uninterruptibleMask { _ =>
            reserve(n).flatMap { res =>
              ZIO.acquireRelease(res.acquire)(_ => res.release)
            }
          }

        private case class Reservation(acquire: UIO[Unit], release: UIO[Any])

        private def reserve(n: Long)(implicit trace: Trace): UIO[Reservation] =
          if (n < 0) ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
          else if (n == 0L) ZIO.succeed(Reservation(ZIO.unit, ZIO.unit))
          else
            ref.modify {
              case Right(p) if p >= n => (Reservation(ZIO.unit, releaseN(n)), Right(p - n))
              case state =>
                val promise = Promise.unsafe.make[Nothing, Unit](FiberId.None)
                val newState = state match {
                  case Right(p) => Left(ScalaQueue(promise -> (n - p)))
                  case Left(q)  => Left(q.enqueue(promise -> n))
                }
                (Reservation(promise.await, restore(promise, n)), newState)
            }

        private def restore(promise: Promise[Nothing, Unit], n: Long)(implicit trace: Trace): UIO[Any] =
          ref.modify {
            case Left(q) =>
              val filtered = q.filter(_._1 != promise)
              if (filtered.size == q.size) (releaseN(n), Left(q))
              else {
                val pending = q.find(_._1 == promise).get._2
                (releaseN(n - pending), Left(filtered))
              }
            case Right(p) => (ZIO.unit, Right(p + n))
          }.flatten

        private def releaseN(n: Long)(implicit trace: Trace): UIO[Any] =
          ref.modify {
            case Right(p) => (ZIO.unit, Right(p + n))
            case Left(q) =>
              val (acc, newState) = loop(n, q, ZIO.unit)
              (acc, newState)
          }.flatten

        @tailrec
        private def loop(
          n: Long,
          q: ScalaQueue[(Promise[Nothing, Unit], Long)],
          acc: UIO[Any]
        )(implicit trace: Trace): (UIO[Any], Either[ScalaQueue[(Promise[Nothing, Unit], Long)], Long]) =
          if (q.isEmpty) (acc, Right(n))
          else {
            val ((prom, req), rest) = q.dequeue
            if (n >= req) loop(n - req, rest, acc *> prom.succeedUnit)
            else (acc, Left((prom -> (req - n)) +: rest))
          }
      }
  }
}