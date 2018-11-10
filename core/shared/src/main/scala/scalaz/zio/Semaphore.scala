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

  final def withPermit[E, A](task: IO[E, A]): IO[E, A] =
    IO.bracket0[E, AcquireTasks, A](prepare(1L))(cleanup) { _._1 *> task }

  /**
   * Ported from @mpilquist work in cats-effects (https://github.com/typelevel/cats-effect/pull/403)
   */
  final def acquireN(n: Long): IO[Nothing, Unit] =
    assertNonNegative(n) *> IO.bracket0[Nothing, AcquireTasks, Unit](prepare(n))(cleanup)(_._1)

  /**
   * Ported from @mpilquist work in cats-effects (https://github.com/typelevel/cats-effect/pull/403)
   */
  final private def prepare(n: Long): IO[Nothing, AcquireTasks] = {
    def restore(p: Promise[Nothing, Unit], n: Long): IO[Nothing, Unit] =
      IO.flatten(state.modify {
        case Left(q) =>
          q.find(_._1 == p).fold(releaseN(n) -> Left(q))(x => releaseN(n - x._2) -> Left(q.filter(_._1 != p)))
        case Right(m) => IO.unit -> Right(m + n)
      })

    if (n == 0)
      IO.now((IO.unit, IO.unit))
    else
      Promise.make[Nothing, Unit].flatMap { p =>
        state.modify {
          case Right(m) if m >= n => (IO.unit, releaseN(n)) -> Right(m - n)
          case Right(m)           => (p.get, restore(p, n)) -> Left(IQueue(p -> (n - m)))
          case Left(q)            => (p.get, restore(p, n)) -> Left(q.enqueue(p -> n))
        }
      }
  }

  final private def cleanup[E, A](ops: AcquireTasks, res: ExitResult[E, A]): IO[Nothing, Unit] =
    res match {
      case ExitResult.Failed(_) => ops._2
      case _                    => IO.unit
    }

  final def releaseN(toRelease: Long): IO[Nothing, Unit] = {
    @tailrec def loop(
      n: Long,
      io: IO[Nothing, Boolean],
      st: Option[(Entry, IQueue[Entry])]
    ): (IO[Nothing, Boolean], State) = (n, st) match {
      case (_, None)                          => io -> Right(n)
      case (_, Some(((p2, n2), q))) if n < n2 => io -> Left(q :+ (p2 -> (n2 - n)))
      case (_, Some(((p2, n2), q)))           => loop(n - n2, io *> p2.complete(()), q.dequeueOption)
    }

    val acquire: (Promise[Nothing, Unit], State) => (IO[Nothing, Boolean], State) = {
      case (p, Right(n))             => p.complete(()) -> Right(n + toRelease)
      case (p, Left(q)) if q.isEmpty => p.complete(()) -> Right(toRelease)
      case (p, Left(q))              => loop(toRelease, p.complete(()), q.dequeueOption)
    }

    val release: (Boolean, Promise[Nothing, Unit]) => IO[Nothing, Unit] = (_, _) => IO.unit

    assertNonNegative(toRelease) *> Promise.bracket[Nothing, State, Unit, Boolean](state)(acquire)(release)
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

  type AcquireTasks = (IO[Nothing, Unit], IO[Nothing, Unit])

  type Entry = (Promise[Nothing, Unit], Long)

  type State = Either[IQueue[Entry], Long]

  def assertNonNegative(n: Long): IO[Nothing, Unit] =
    if (n < 0) IO.terminate(new NegativeArgument(s"Unexpected negative value `$n` passed to acquireN or releaseN."))
    else IO.unit

  class NegativeArgument(message: String) extends IllegalArgumentException(message)
}
