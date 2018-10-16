// Copyright (C) 2017-2018 Łukasz Biały, Paul Chiusano, Michael Pilquist,
// Oleg Pyzhcov, Fabio Labella, Alexandru Nedelcu, Pavel Chlupacek. All rights reserved.

package scalaz.zio

import internals._

import scala.annotation.tailrec
import scala.collection.immutable.{ Queue => IQueue }

final class Semaphore private (private val state: Ref[State]) {

  final def count: IO[Nothing, Long] = state.get.map(count_)

  final def available: IO[Nothing, Long] = state.get.map {
    case Left(_)  => 0
    case Right(n) => n
  }

  final def acquire: IO[Nothing, Unit] = acquireN(1)

  final def release: IO[Nothing, Unit] = releaseN(1)

  final def withPermit[E, A](task: IO[E, A]): IO[E, A] =
    IO.bracket[E, Unit, A](acquire)(_ => release)(_ => task)

  final def acquireN(requested: Long): IO[Nothing, Unit] = {
    val acquire: (Promise[Nothing, Unit], State) => (IO[Nothing, Boolean], State) = {
      case (p, Right(n)) if n >= requested => p.complete(()) -> Right(n - requested)
      case (p, Right(n))                   => IO.now(false)  -> Left(IQueue(p -> (requested - n)))
      case (p, Left(q))                    => IO.now(false)  -> Left(q.enqueue(p -> requested))
    }

    val release: (Boolean, Promise[Nothing, Unit]) => IO[Nothing, Unit] = {
      case (_, p) =>
        p.poll.void <> state.update {
          case Left(q) => Left(q.filterNot(_._1 == p))
          case x       => x
        }.void
    }

    assertNonNegative(requested) *> Promise.bracket[Nothing, State, Unit, Boolean](state)(acquire)(release)
  }

  final def releaseN(toRelease: Long): IO[Nothing, Unit] = {
    @tailrec def loop(
      n: Long,
      io: IO[Nothing, Boolean],
      st: Option[(Entry, IQueue[Entry])]
    ): (IO[Nothing, Boolean], State) = (n, st) match {
      case (n, None)                          => io -> Right(n)
      case (n, Some(((p2, n2), q))) if n < n2 => io -> Left(q :+ (p2 -> (n2 - n)))
      case (n, Some(((p2, n2), q)))           => loop(n - n2, io *> p2.complete(()), q.dequeueOption)
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

object Semaphore {
  def apply(permits: Long): IO[Nothing, Semaphore] = Ref[State](Right(permits)).map(new Semaphore(_))
}

private object internals {

  type Entry = (Promise[Nothing, Unit], Long)

  type State = Either[IQueue[Entry], Long]

  def assertNonNegative(n: Long): IO[Nothing, Unit] =
    if (n < 0) IO.terminate(new NegativeArgument(s"Unexpected negative value `$n` passed to acquireN or releaseN."))
    else IO.unit

  class NegativeArgument(message: String) extends IllegalArgumentException(message)
}
