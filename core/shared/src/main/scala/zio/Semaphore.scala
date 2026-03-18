package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import scala.annotation.tailrec
import scala.collection.immutable.{Queue => ScalaQueue}

sealed trait Semaphore extends Serializable {
  def available(implicit trace: Trace): UIO[Long]
  def awaiting(implicit trace: Trace): UIO[Long] = ZIO.succeed(0L)

  final def tryWithPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
    tryWithPermits(1L)(zio)

  def tryWithPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]]

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

        override def awaiting(implicit trace: Trace): UIO[Long] =
          ref.get.map {
            case Left(q) => q.size.toLong
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

        def tryWithPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
          ZIO.uninterruptibleMask { restore =>
            tryReserve(n).flatMap {
              case Some(res) => restore(zio).asSome.ensuring(res.release)
              case None      => ZIO.none
            }
          }

        case class Reservation(acquire: UIO[Unit], release: UIO[Any])

        def tryReserve(n: Long)(implicit trace: Trace): UIO[Option[Reservation]] =
          if (n < 0) ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
          else if (n == 0L) ZIO.succeed(Some(Reservation(ZIO.unit, ZIO.unit)))
          else
            ref.modify {
              case Right(p) if p >= n => (Some(Reservation(ZIO.unit, releaseN(n))), Right(p - n))
              case other => (None, other)
            }

        def reserve(n: Long)(implicit trace: Trace): UIO[Reservation] =
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

        def restore(promise: Promise[Nothing, Unit], n: Long)(implicit trace: Trace): UIO[Any] =
          ref.modify {
            case Left(q) =>
              q.find(_._1 == promise) match {
                case Some((_, pending)) => (releaseN(n - pending), Left(q.filter(_._1 != promise)))
                case None               => (releaseN(n), Left(q))
              }
            case Right(p) => (ZIO.unit, Right(p + n))
          }.flatten

        def releaseN(n: Long)(implicit trace: Trace): UIO[Any] = {
          @tailrec
          def loop(
            n: Long,
            state: Either[ScalaQueue[(Promise[Nothing, Unit], Long)], Long],
            acc: UIO[Any]
          ): (UIO[Any], Either[ScalaQueue[(Promise[Nothing, Unit], Long)], Long]) =
            state match {
              case Right(p) => (acc, Right(p + n))
              case Left(q) =>
                q.dequeueOption match {
                  case None => (acc, Right(n))
                  case Some(((prom, req), rest)) =>
                    if (n >= req) loop(n - req, Left(rest), acc *> prom.succeedUnit)
                    else (acc, Left((prom -> (req - n)) +: rest))
                }
            }
          ref.modify(loop(n, _, ZIO.unit)).flatten
        }
      }
  }
}