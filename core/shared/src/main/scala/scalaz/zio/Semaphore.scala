// Copyright (C) 2017-2018 Łukasz Biały, Paul Chiusano, Michael Pilquist,
// Oleg Pyzhcov, Fabio Labella, Alexandru Nedelcu, Pavel Chlupacek. All rights reserved.

package scalaz.zio

import internals._

import scala.annotation.tailrec
import scala.collection.immutable.{ Queue => IQueue }

final class Semaphore private (private val state: Ref[State]) extends Serializable {

  final def count: IO[Nothing, Long] = state.get.map(count_)

  final def available: IO[Nothing, Long] = state.get.map {
    case Left(_)  => 0
    case Right(n) => n
  }

  final def acquire: IO[Nothing, Unit] = acquireN(1)

  final def release: IO[Nothing, Unit] = releaseN(1)

  final def withPermit[E, A](task: IO[E, A]): IO[E, A] = prepare(1L).bracket(_.release)(_.awaitAcquire *> task)

  /**
   * Ported from @mpilquist work in cats-effects (https://github.com/typelevel/cats-effect/pull/403)
   */
  final def acquireN(n: Long): IO[Nothing, Unit] =
    assertNonNegative(n) *> IO.bracket0[Nothing, Acquisition, Unit](prepare(n))(cleanup)(_.awaitAcquire)

  /**
   * Ported from @mpilquist work in cats-effects (https://github.com/typelevel/cats-effect/pull/403)
   */
  final private def prepare(n: Long): IO[Nothing, Acquisition] = {
    def restore(p: Promise[Nothing, Unit], n: Long): IO[Nothing, Unit] =
      IO.flatten(state.modify {
        case Left(q) =>
          q.find(_._1 == p).fold(releaseN(n) -> Left(q))(x => releaseN(n - x._2) -> Left(q.filter(_._1 != p)))
        case Right(m) => IO.unit -> Right(m + n)
      })

    if (n == 0)
      IO.now(Acquisition(IO.unit, IO.unit))
    else
      Promise.make[Nothing, Unit].flatMap { p =>
        state.modify {
          case Right(m) if m >= n => Acquisition(IO.unit, releaseN(n)) -> Right(m - n)
          case Right(m)           => Acquisition(p.get, restore(p, n)) -> Left(IQueue(p -> (n - m)))
          case Left(q)            => Acquisition(p.get, restore(p, n)) -> Left(q.enqueue(p -> n))
        }
      }
  }

  final private def cleanup[E, A](ops: Acquisition, res: ExitResult[E, A]): IO[Nothing, Unit] =
    res match {
      case ExitResult.Failed(c) if c.isInterrupted => ops.release
      case _                                       => IO.unit
    }

  final def releaseN(toRelease: Long): IO[Nothing, Unit] = {

    @tailrec def loop(n: Long, state: State, acc: IO[Nothing, Unit]): (IO[Nothing, Unit], State) = state match {
      case Right(m) => acc -> Right(n + m)
      case Left(q) =>
        q.dequeueOption match {
          case None => acc -> Right(n)
          case Some(((p, m), q)) =>
            if (n > m)
              loop(n - m, Left(q), acc <* p.complete(()))
            else if (n == m)
              (acc <* p.complete(())) -> Left(q)
            else
              acc -> Left((p -> (m - n)) +: q)
        }
    }

    IO.flatten(assertNonNegative(toRelease) *> state.modify(loop(toRelease, _, IO.unit))).uninterruptibly

  }

  private final def count_(state: State): Long = state match {
    case Left(q)  => -(q.map(_._2).sum)
    case Right(n) => n
  }

}

object Semaphore extends Serializable {
  def apply(permits: Long): IO[Nothing, Semaphore] = Ref[State](Right(permits)).map(new Semaphore(_))
}

private object internals {

  final case class Acquisition(awaitAcquire: IO[Nothing, Unit], release: IO[Nothing, Unit])

  type Entry = (Promise[Nothing, Unit], Long)

  type State = Either[IQueue[Entry], Long]

  def assertNonNegative(n: Long): IO[Nothing, Unit] =
    if (n < 0) IO.terminate(new NegativeArgument(s"Unexpected negative value `$n` passed to acquireN or releaseN."))
    else IO.unit

  class NegativeArgument(message: String) extends IllegalArgumentException(message)
}
