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
    IO.bracket[E, Unit, A](acquire)(_ => release)(_ => task)

  final def acquireN(requested: Long): IO[Nothing, Unit] = {
    val acquire: (Promise[Nothing, Unit], State) => (IO[Nothing, IO[Nothing, Unit]], State) = {
      case (p, Right(n)) if n >= requested => p.complete(()) *> IO.now(IO.unit) -> Right(n - requested)
      case (p, Right(n))                   => IO.now(releaseN(n))               -> Left(IQueue(p -> (requested - n)))
      case (p, Left(q))                    => IO.now(IO.unit)                   -> Left(q.enqueue(p -> requested))
    }

    val release: (IO[Nothing, Unit], Promise[Nothing, Unit]) => IO[Nothing, Unit] = {
      case (io, p) =>
        p.poll.redeem(_ => IO.unit, {
          case ExitResult.Terminated(_) => io
          case _                        => IO.unit
        }) *> state.update {
          case Left(q) => Left(q.filterNot(_._1 == p))
          case x       => x
        }.void
    }

    assertNonNegative(requested) *> Promise.bracket[Nothing, State, Unit, IO[Nothing, Unit]](state)(acquire)(release)
  }

  final def releaseN(toRelease: Long): IO[Nothing, Unit] = {
    @tailrec def loop(n: Long, state: State, acc: IO[Nothing, Unit]): (IO[Nothing, Unit], State) = state match {
      case Right(m) => acc -> Right(n + m)
      case Left(q) =>
        q.dequeueOption match {
          case None => IO.unit -> Right(n)
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

  type Entry = (Promise[Nothing, Unit], Long)

  type State = Either[IQueue[Entry], Long]

  def assertNonNegative(n: Long): IO[Nothing, Unit] =
    if (n < 0) IO.terminate(new NegativeArgument(s"Unexpected negative value `$n` passed to acquireN or releaseN."))
    else IO.unit

  class NegativeArgument(message: String) extends IllegalArgumentException(message)
}
